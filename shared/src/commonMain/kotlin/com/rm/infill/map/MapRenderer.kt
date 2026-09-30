package com.rm.infill.map

import androidx.compose.ui.graphics.ImageBitmap
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.Road
import com.rm.infill.sim.Terrain
import com.rm.infill.sim.Zone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the map in chunks of [CHUNK] by [CHUNK] tiles, each baked into a bitmap
 * at one atlas level, in one look, with the shadows for one sun step.
 *
 * Nothing should ever be seen loading, so chunks are baked before they're
 * needed: the screen and a ring around it, the next sharper level when a zoom
 * is getting close to it, and the whole map at the two coarser levels, so a
 * zoom out has everything ready. A new look or sun step is baked alongside the
 * old one and each level switches over once every chunk on screen has it.
 * When the map changes, the chunks it touches go on showing until their new
 * bitmaps are ready ([changed]).
 *
 * Each frame, [plan] says what's on screen and gets the list of what to bake
 * next, in order. A baker (see [MapView]) takes them with [nextRequest], bakes
 * them wherever [bakeDispatcher] says and hands them back with [store].
 */
internal class MapRenderer(private val map: CityMap, private val atlas: TileAtlas, private val graphics: Graphics) {
    class Request(val key: Long, val cx: Int, val cy: Int, val level: Int, val look: Int, val sun: Sun?, val version: Int)

    private class Entry(val cx: Int, val cy: Int, val level: Int, val image: ImageBitmap, var used: Long, val version: Int)

    private val cache = HashMap<Long, Entry>()
    private var bytes = 0L
    private var frame = 0L

    private val queue = ArrayList<Request>()
    private val queued = HashSet<Long>()
    private var queueAt = 0
    private val baking = HashSet<Long>()

    /** The chunks the screen is waiting on, so baking one of them is worth drawing again for. */
    private val onScreen = HashSet<Long>()

    // What each level is showing, which only changes once all of the screen can.
    private val shownLook = IntArray(LEVELS) { -1 }
    private val shownStep = IntArray(LEVELS)

    /** Goes up whenever a frame leaves something to bake. */
    val requests = MutableStateFlow(0)

    /** True once the first screen has been baked. Until then nothing is drawn. */
    var ready = false
        private set

    private val chunksX = (map.width + CHUNK - 1) / CHUNK
    private val chunksY = (map.height + CHUNK - 1) / CHUNK

    /** How many times each chunk has changed. A bitmap baked at an older count is out of date. */
    private val versions = IntArray(chunksX * chunksY)

    /** The atlas level for a zoom, never sharper than [Graphics.sharpest] allows. */
    fun levelFor(tilePx: Float): Int = THRESHOLDS.indexOfFirst { tilePx >= it }.coerceAtLeast(graphics.sharpest)

    /**
     * The sun step a level is baked with, or -1 for no shadows. The coarsest
     * level has none, since they'd be a pixel or two, and so it never needs
     * baking again as the sun moves.
     */
    private fun stepFor(level: Int, sunStep: Int, sun: Sun): Int =
        if (graphics.shadows && level < LEVELS - 1 && sun.strength > 0f) sunStep else -1

    /** Works out what the screen shows and what to bake next. The screen covers chunks [cx0]..[cx1], [cy0]..[cy1]. */
    fun plan(level: Int, cx0: Int, cy0: Int, cx1: Int, cy1: Int, look: Int, sunStep: Int, sun: Sun, tilePx: Float) {
        frame++
        queue.clear()
        queued.clear()
        onScreen.clear()
        queueAt = 0
        val midX = (cx0 + cx1) / 2
        val midY = (cy0 + cy1) / 2

        // The screen at the look and step it should be in.
        val step = stepFor(level, sunStep, sun)
        var complete = true
        forEachOutward(cx0, cy0, cx1, cy1, midX, midY) { cx, cy ->
            val k = key(cx, cy, level, look, step)
            if (!touch(k, cx, cy)) {
                complete = false
                onScreen += k
                want(k, cx, cy, level, look, step, sun)
            }
        }
        if (complete) {
            shownLook[level] = look
            shownStep[level] = step
            ready = true
        } else if (shownLook[level] >= 0) {
            // Keep what's showing now until the change is ready.
            forEachOutward(cx0, cy0, cx1, cy1, midX, midY) { cx, cy ->
                touch(key(cx, cy, level, shownLook[level], shownStep[level]), cx, cy)
            }
        }

        // A ring around the screen, for panning.
        forEachOutward(cx0 - RING, cy0 - RING, cx1 + RING, cy1 + RING, midX, midY) { cx, cy ->
            val k = key(cx, cy, level, look, step)
            if (!touch(k, cx, cy)) want(k, cx, cy, level, look, step, sun)
        }

        // The sharper level, when a zoom in is getting near it.
        if (level > graphics.sharpest && tilePx >= NEAR_SHARPER * THRESHOLDS[level - 1]) {
            val finer = level - 1
            val s = stepFor(finer, sunStep, sun)
            forEachOutward(cx0, cy0, cx1, cy1, midX, midY) { cx, cy ->
                val k = key(cx, cy, finer, look, s)
                if (!touch(k, cx, cy)) want(k, cx, cy, finer, look, s, sun)
            }
        }

        // The whole map at the coarser levels, for zooming out.
        for (coarse in max(level + 1, max(1, graphics.sharpest)) until LEVELS) {
            val s = stepFor(coarse, sunStep, sun)
            forEachOutward(0, 0, chunksX - 1, chunksY - 1, midX, midY) { cx, cy ->
                val k = key(cx, cy, coarse, look, s)
                if (!touch(k, cx, cy)) want(k, cx, cy, coarse, look, s, sun)
            }
        }

        if (queue.isNotEmpty()) requests.value++
    }

    /** The bitmap to draw for a chunk: what its level is showing, or the nearest thing to it that's baked. */
    fun image(cx: Int, cy: Int, level: Int): ImageBitmap? {
        if (shownLook[level] >= 0) {
            cache[key(cx, cy, level, shownLook[level], shownStep[level])]?.let { return it.image }
        }
        // Coarser levels first, since they're soft rather than too busy, then sharper ones.
        for (l in (level until LEVELS) + (level - 1 downTo 0)) {
            var best: Entry? = null
            for (e in cache.values) {
                if (e.cx == cx && e.cy == cy && e.level == l && (best == null || e.used > best.used)) best = e
            }
            if (best != null) return best.image
        }
        return null
    }

    /** The next chunk to bake, or null when everything the last frame wanted is done. */
    fun nextRequest(): Request? {
        while (queueAt < queue.size) {
            val r = queue[queueAt++]
            if (r.key in baking || fresh(cache[r.key], r.cx, r.cy)) continue
            baking += r.key
            return r
        }
        return null
    }

    /**
     * Keeps a baked chunk. If the map changed while it was baking it's already
     * out of date, but it still stands in until the next one is ready.
     * True if the screen was waiting on it.
     */
    fun store(request: Request, image: ImageBitmap): Boolean {
        baking -= request.key
        val old = cache[request.key]
        if (old != null) {
            if (old.version > request.version) return false
            bytes -= old.image.width.toLong() * old.image.height * 4
        }
        cache[request.key] = Entry(request.cx, request.cy, request.level, image, frame, request.version)
        bytes += image.width.toLong() * image.height * 4
        while (bytes > graphics.cacheBytes && cache.size > 1) {
            val oldest = cache.entries.minBy { it.value.used }
            bytes -= oldest.value.image.width.toLong() * oldest.value.image.height * 4
            cache.remove(oldest.key)
        }
        return request.key in onScreen
    }

    /** Marks the chunks that show tile [x], [y] out of date, including those its sprites and shadows reach. */
    fun changed(x: Int, y: Int) {
        for (cy in (y - SHADOW_MARGIN) / CHUNK..(y + SHADOW_MARGIN + 1) / CHUNK) {
            for (cx in (x - SHADOW_MARGIN) / CHUNK..(x + SHADOW_MARGIN) / CHUNK) {
                if (cx in 0 until chunksX && cy in 0 until chunksY) versions[cy * chunksX + cx]++
            }
        }
    }

    /** Bakes a chunk. Safe on a background thread with [PixelSurface], as long as the map isn't changed meanwhile. */
    fun bake(r: Request): ImageBitmap {
        val level = r.level
        val s = TILE shr level
        val surface = newSurface(atlas, level, CHUNK * s)
        val x0 = r.cx * CHUNK
        val y0 = r.cy * CHUNK
        val x1 = min(x0 + CHUNK, map.width)
        val y1 = min(y0 + CHUNK, map.height)
        val base = r.look * Atlas.PER_LOOK

        // The ground.
        for (ty in y0 until y1) for (tx in x0 until x1) {
            val dx = (tx - x0) * s
            val dy = (ty - y0) * s
            val h = tileHash(tx, ty)
            if (map.terrainAt(tx, ty) == Terrain.WATER) {
                surface.copy(base + Atlas.WATER + h % Atlas.WATER_COUNT, dx, dy)
                shores(surface, base, tx, ty, dx, dy)
            } else {
                surface.copy(base + Atlas.GRASS + h % Atlas.GRASS_COUNT, dx, dy)
                val zone = map.zoneAt(tx, ty)
                if (zone != Zone.NONE) zoneTint(surface, zone, tx, ty, dx, dy, s, level)
                if (map.roadAt(tx, ty) != Road.NONE) surface.blend(base + Atlas.ROAD + roadMask(tx, ty), dx, dy)
            }
        }

        // Shadows, from casters up to a few tiles outside the chunk.
        val sun = r.sun
        if (sun != null) {
            surface.beginShadows(SHADOW_ALPHA * sun.strength)
            val thin = r.look == Atlas.BARE || r.look == Atlas.SNOW
            for (ty in max(0, y0 - SHADOW_MARGIN) until min(map.height, y1 + SHADOW_MARGIN)) {
                for (tx in max(0, x0 - SHADOW_MARGIN) until min(map.width, x1 + SHADOW_MARGIN)) {
                    if (map.terrainAt(tx, ty) != Terrain.TREES) continue
                    shadows(surface, level, treeSprite(tx, ty), (tx - x0) * s, (ty - y0) * s, sun, thin)
                }
            }
            surface.endShadows()
        }

        // Sprites, back rows first. The row below this chunk reaches up into it.
        for (ty in y0 until min(y1 + 1, map.height)) for (tx in x0 until x1) {
            if (map.terrainAt(tx, ty) != Terrain.TREES) continue
            surface.blend(base + treeSprite(tx, ty), (tx - x0) * s, (ty - y0) * s)
        }
        return surface.finish()
    }

    /** Banks along the sides of a water tile that touch land, and in corners where only the diagonal does. */
    private fun shores(surface: BakeSurface, base: Int, tx: Int, ty: Int, dx: Int, dy: Int) {
        val n = land(tx, ty - 1)
        val e = land(tx + 1, ty)
        val s = land(tx, ty + 1)
        val w = land(tx - 1, ty)
        if (n) surface.blend(base + Atlas.SHORE, dx, dy)
        if (e) surface.blend(base + Atlas.SHORE + 1, dx, dy)
        if (s) surface.blend(base + Atlas.SHORE + 2, dx, dy)
        if (w) surface.blend(base + Atlas.SHORE + 3, dx, dy)
        if (!n && !e && land(tx + 1, ty - 1)) surface.blend(base + Atlas.CORNER, dx, dy)
        if (!s && !e && land(tx + 1, ty + 1)) surface.blend(base + Atlas.CORNER + 1, dx, dy)
        if (!s && !w && land(tx - 1, ty + 1)) surface.blend(base + Atlas.CORNER + 2, dx, dy)
        if (!n && !w && land(tx - 1, ty - 1)) surface.blend(base + Atlas.CORNER + 3, dx, dy)
    }

    private fun land(x: Int, y: Int) = map.inside(x, y) && map.terrainAt(x, y) != Terrain.WATER

    /** Which neighbours are road: north 1, east 2, south 4, west 8. */
    private fun roadMask(x: Int, y: Int): Int {
        var m = 0
        if (road(x, y - 1)) m = m or 1
        if (road(x + 1, y)) m = m or 2
        if (road(x, y + 1)) m = m or 4
        if (road(x - 1, y)) m = m or 8
        return m
    }

    private fun road(x: Int, y: Int) = map.inside(x, y) && map.roadAt(x, y) != Road.NONE

    /**
     * A zone is a wash of its colour dotted with it, so it reads as zoned on any
     * ground, with a line along the sides where the zone ends.
     */
    private fun zoneTint(surface: BakeSurface, zone: Byte, tx: Int, ty: Int, dx: Int, dy: Int, s: Int, level: Int) {
        val colour = ZONE_COLOURS[zone.toInt()]
        surface.fill(dx, dy, s, s, ZONE_WASHES[zone.toInt()], ZONE_WASH)
        val spacing = ZONE_DOTS shr level
        val dot = max(1, 2 shr level)
        if (spacing >= 4) {
            for (row in 0 until s / spacing) for (col in 0 until s / spacing) {
                val shift = if (row % 2 == 0) 0 else spacing / 2
                surface.fill(dx + col * spacing + shift + 1, dy + row * spacing + 1, dot, dot, colour, ZONE_DOT)
            }
        }
        val line = max(1, 2 shr level)
        if (!sameZone(tx, ty - 1, zone)) surface.fill(dx, dy, s, line, colour, ZONE_LINE)
        if (!sameZone(tx + 1, ty, zone)) surface.fill(dx + s - line, dy, line, s, colour, ZONE_LINE)
        if (!sameZone(tx, ty + 1, zone)) surface.fill(dx, dy + s - line, s, line, colour, ZONE_LINE)
        if (!sameZone(tx - 1, ty, zone)) surface.fill(dx, dy, line, s, colour, ZONE_LINE)
    }

    private fun sameZone(x: Int, y: Int, zone: Byte) = map.inside(x, y) && map.zoneAt(x, y) == zone

    /** A tree tile among others is a bit of woods, and one on its own is a single tree. */
    private fun treeSprite(tx: Int, ty: Int): Int {
        var around = 0
        if (trees(tx, ty - 1)) around++
        if (trees(tx + 1, ty)) around++
        if (trees(tx, ty + 1)) around++
        if (trees(tx - 1, ty)) around++
        val h = tileHash(tx, ty)
        return if (around >= 2) Atlas.FOREST + h % Atlas.FOREST_COUNT else Atlas.TREE + h % Atlas.TREE_COUNT
    }

    private fun trees(x: Int, y: Int) = map.inside(x, y) && map.terrainAt(x, y) == Terrain.TREES

    /** The shadows of sprite [id]'s casters: the trunk as a thick line and the crown as an oval stretched away from the sun. */
    private fun shadows(surface: BakeSurface, level: Int, id: Int, dx: Int, dy: Int, sun: Sun, thin: Boolean) {
        val scale = 1f / (1 shl level)
        val c = Atlas.casters
        val angle = atan2(sun.shadowY, sun.shadowX)
        val ux = cos(angle)
        val uy = sin(angle)
        // A round crown's shadow lengthens as the sun gets lower.
        val stretch = sqrt(1f + sun.length * sun.length)
        val trunk = max(0.5f, 1.5f * scale)
        for (k in 0 until Atlas.casterCount[id]) {
            val i = (Atlas.casterStart[id] + k) * 4
            val fx = dx + c[i] * scale
            val fy = dy + c[i + 1] * scale
            val height = c[i + 2] * scale
            val radius = c[i + 3] * scale
            val sx = fx + sun.shadowX * height
            val sy = fy + sun.shadowY * height
            surface.shadowLine(fx, fy, sx, sy, trunk)
            // Bare branches let most of the light through, leaving a thin streak.
            surface.shadowOval(sx, sy, ux, uy, radius * stretch, radius * if (thin) 0.4f else 0.85f)
        }
    }

    /** Marks a bitmap as in use. True if it's there and up to date. */
    private fun touch(k: Long, cx: Int, cy: Int): Boolean {
        val e = cache[k] ?: return false
        e.used = frame
        return fresh(e, cx, cy)
    }

    private fun fresh(e: Entry?, cx: Int, cy: Int) = e != null && e.version == versions[cy * chunksX + cx]

    private fun want(k: Long, cx: Int, cy: Int, level: Int, look: Int, step: Int, sun: Sun) {
        if (k in baking || !queued.add(k)) return
        queue += Request(k, cx, cy, level, look, if (step >= 0) sun else null, versions[cy * chunksX + cx])
    }

    /** Every chunk in the range that's on the map, nearest to [midX], [midY] first. */
    private inline fun forEachOutward(x0: Int, y0: Int, x1: Int, y1: Int, midX: Int, midY: Int, action: (Int, Int) -> Unit) {
        val left = max(0, x0)
        val top = max(0, y0)
        val right = min(chunksX - 1, x1)
        val bottom = min(chunksY - 1, y1)
        if (left > right || top > bottom) return
        val reach = max(max(abs(midX - left), abs(right - midX)), max(abs(midY - top), abs(bottom - midY)))
        for (ring in 0..reach) {
            for (cy in midY - ring..midY + ring) {
                if (cy < top || cy > bottom) continue
                val edge = cy == midY - ring || cy == midY + ring
                var cx = midX - ring
                while (cx <= midX + ring) {
                    if (cx in left..right) action(cx, cy)
                    cx += if (edge || ring == 0) 1 else 2 * ring
                }
            }
        }
    }

    private fun key(cx: Int, cy: Int, level: Int, look: Int, step: Int): Long =
        (cx.toLong() shl 40) or (cy.toLong() shl 24) or (level.toLong() shl 16) or (look.toLong() shl 8) or (step + 1).toLong()

    companion object {
        /** Residential, commercial and industrial, as RGB: the edge and dots, and the pale wash over the ground. */
        val ZONE_COLOURS = intArrayOf(0, 0x4CC23A, 0x3C78D7, 0xDCAA28)
        private val ZONE_WASHES = intArrayOf(0, 0xDDF7B8, 0xC4DAFF, 0xFFE9A6)
        private const val ZONE_WASH = 95
        private const val ZONE_LINE = 230
        private const val ZONE_DOT = 255

        /** Dots every this many pixels at 32 px a tile, fewer as the tiles get smaller, none when they'd run together. */
        private const val ZONE_DOTS = 8

        const val CHUNK = 16
        const val TILE = 32
        const val LEVELS = 3

        /** The tile size on screen, in pixels, at which each level takes over. */
        private val THRESHOLDS = floatArrayOf(24f, 12f, 0f)

        /** Start baking the sharper level at this share of the way to its threshold. */
        private const val NEAR_SHARPER = 0.75f
        private const val RING = 1
        private const val SHADOW_MARGIN = 3
        private const val SHADOW_ALPHA = 0.32f

        /** The same scatter of numbers for a tile every time, for picking variants. */
        fun tileHash(x: Int, y: Int): Int {
            var h = x * 73856093 xor y * 19349663
            h = (h xor (h ushr 13)) * 1274126177
            return (h xor (h ushr 16)) and 0x7fffffff
        }
    }
}
