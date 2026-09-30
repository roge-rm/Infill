package com.rm.infill.map

import androidx.compose.ui.graphics.ImageBitmap
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.Terrain
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
 *
 * Each frame, [plan] says what's on screen and gets the list of what to bake
 * next, in order. A baker (see [MapView]) takes them with [nextRequest], bakes
 * them wherever [bakeDispatcher] says and hands them back with [store].
 */
internal class MapRenderer(private val map: CityMap, private val atlas: TileAtlas, private val graphics: Graphics) {
    class Request(val key: Long, val cx: Int, val cy: Int, val level: Int, val look: Int, val sun: Sun?)

    private class Entry(val cx: Int, val cy: Int, val level: Int, val image: ImageBitmap, var used: Long)

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

    /** Goes up with every change to the map, so a bake that started before one is thrown away. */
    var edits = 0
        private set

    /** True once the first screen has been baked. Until then nothing is drawn. */
    var ready = false
        private set

    private val chunksX = (map.width + CHUNK - 1) / CHUNK
    private val chunksY = (map.height + CHUNK - 1) / CHUNK

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
            if (!touch(k)) {
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
                touch(key(cx, cy, level, shownLook[level], shownStep[level]))
            }
        }

        // A ring around the screen, for panning.
        forEachOutward(cx0 - RING, cy0 - RING, cx1 + RING, cy1 + RING, midX, midY) { cx, cy ->
            val k = key(cx, cy, level, look, step)
            if (!touch(k)) want(k, cx, cy, level, look, step, sun)
        }

        // The sharper level, when a zoom in is getting near it.
        if (level > graphics.sharpest && tilePx >= NEAR_SHARPER * THRESHOLDS[level - 1]) {
            val finer = level - 1
            val s = stepFor(finer, sunStep, sun)
            forEachOutward(cx0, cy0, cx1, cy1, midX, midY) { cx, cy ->
                val k = key(cx, cy, finer, look, s)
                if (!touch(k)) want(k, cx, cy, finer, look, s, sun)
            }
        }

        // The whole map at the coarser levels, for zooming out.
        for (coarse in max(level + 1, max(1, graphics.sharpest)) until LEVELS) {
            val s = stepFor(coarse, sunStep, sun)
            forEachOutward(0, 0, chunksX - 1, chunksY - 1, midX, midY) { cx, cy ->
                val k = key(cx, cy, coarse, look, s)
                if (!touch(k)) want(k, cx, cy, coarse, look, s, sun)
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
            if (r.key in cache || r.key in baking) continue
            baking += r.key
            return r
        }
        return null
    }

    /**
     * Keeps a baked chunk, unless the map changed while it was being baked.
     * True if the screen was waiting on it.
     */
    fun store(request: Request, image: ImageBitmap, editsAtStart: Int): Boolean {
        baking -= request.key
        if (editsAtStart != edits) return false
        cache[request.key] = Entry(request.cx, request.cy, request.level, image, frame)
        bytes += image.width.toLong() * image.height * 4
        while (bytes > graphics.cacheBytes && cache.size > 1) {
            val oldest = cache.entries.minBy { it.value.used }
            bytes -= oldest.value.image.width.toLong() * oldest.value.image.height * 4
            cache.remove(oldest.key)
        }
        return request.key in onScreen
    }

    /** Forgets every bitmap that shows tile [x], [y], including the chunks its sprites and shadows reach. */
    fun invalidate(x: Int, y: Int) {
        edits++
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next().value
            val x0 = e.cx * CHUNK - SHADOW_MARGIN
            val y0 = e.cy * CHUNK - SHADOW_MARGIN
            if (x in x0 until x0 + CHUNK + 2 * SHADOW_MARGIN && y in y0 until y0 + CHUNK + 2 * SHADOW_MARGIN) {
                bytes -= e.image.width.toLong() * e.image.height * 4
                iterator.remove()
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

    private fun touch(k: Long): Boolean {
        val e = cache[k] ?: return false
        e.used = frame
        return true
    }

    private fun want(k: Long, cx: Int, cy: Int, level: Int, look: Int, step: Int, sun: Sun) {
        if (k in baking || !queued.add(k)) return
        queue += Request(k, cx, cy, level, look, if (step >= 0) sun else null)
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
