package com.rm.infill.map

import androidx.compose.ui.graphics.ImageBitmap
import com.rm.infill.sim.BuildingType
import com.rm.infill.sim.Generation
import com.rm.infill.sim.Density
import com.rm.infill.sim.Stop
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.Heading
import com.rm.infill.sim.Junction
import com.rm.infill.sim.Power
import com.rm.infill.sim.Rail
import com.rm.infill.sim.Resource
import com.rm.infill.sim.Road
import com.rm.infill.sim.RoadType
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
        shown.clear()

        // The ground.
        for (ty in y0 until y1) for (tx in x0 until x1) {
            val dx = (tx - x0) * s
            val dy = (ty - y0) * s
            val h = tileHash(tx, ty)
            val i = map.index(tx, ty)
            val grime = map.grimeLevel(i)
            val road = RoadType.of(map.road[i])
            val rail = map.rail[i] != Rail.NONE
            if (map.terrain[i] == Terrain.WATER) {
                surface.copy(base + Atlas.WATER + h % Atlas.WATER_COUNT, dx, dy)
                if (grime > 0) surface.fill(dx, dy, s, s, MURK, MURK_ALPHA[grime])
                val foul = map.foulLevel(i)
                if (foul > 0) surface.fill(dx, dy, s, s, SEWAGE, SEWAGE_ALPHA[foul])
                shores(surface, base, tx, ty, dx, dy)
                if (rail) {
                    val mask = railMask(tx, ty)
                    surface.blend(base + Atlas.TRESTLE + if (mask and 5 != 0 && mask and 10 == 0 || mask == 0) 0 else 1, dx, dy)
                    surface.blend(base + Atlas.TRACK + mask, dx, dy)
                } else if (road != null) {
                    val mask = roadMask(tx, ty)
                    val timber = road == RoadType.DIRT || road == RoadType.GRAVEL || road == RoadType.LANE
                    // One-way bridges run the way the traffic does; the rest the way the road goes on.
                    val heading = map.roadHeading[i].toInt()
                    val northSouth = if (heading != 0) heading % 2 == 1 else mask and 5 != 0 && mask and 10 != 10
                    val deck = (if (timber) 0 else 2) + if (northSouth) 0 else 1
                    surface.blend(base + Atlas.BRIDGE + deck, dx, dy)
                    roadTile(surface, base, road, i, tx, ty, mask, dx, dy, level)
                    surface.blend(base + Atlas.RAILS + deck, dx, dy)
                }
            } else {
                surface.copy(base + Atlas.GRASS + h % Atlas.GRASS_COUNT, dx, dy)
                // Stones showing where there's a seam underneath, until something's built over it.
                val seam = map.resource[i]
                if (seam >= Resource.ORE && map.building[i] == 0 && road == null && !rail) {
                    surface.blend(base + Atlas.SEAM + seam - Resource.ORE, dx, dy)
                }
                if (grime > 0) soot(surface, grime, h, dx, dy, s, level)
                if (map.brownfield[i].toInt() != 0) brownfield(surface, h, dx, dy, s, level)
                val zone = map.zone[i]
                if (zone != Zone.NONE && map.building[i] == 0) zoneTint(surface, zone, map.density[i], tx, ty, dx, dy, s, level)
                if (road != null) roadTile(surface, base, road, i, tx, ty, roadMask(tx, ty), dx, dy, level)
                // What the crossing has: stop signs, lights, a roundabout or an overpass.
                val control = map.control[i]
                when {
                    road == null || control < Junction.STOP -> {}
                    control == Junction.ROUNDABOUT -> surface.blend(base + Atlas.ROUNDABOUT + roadMask(tx, ty), dx, dy)
                    control == Junction.INTERCHANGE -> surface.blend(base + Atlas.JUNCTION + 2, dx, dy)
                    else -> surface.blend(base + Atlas.JUNCTION + control - Junction.STOP, dx, dy)
                }
                if (road != null && map.lane[i].toInt() != 0) lanes(surface, tx, ty, dx, dy, s)
                if (road != null) transitOn(surface, base, i, tx, ty, dx, dy)
                if (map.bank[i].toInt() != 0) embankment(surface, tx, ty, dx, dy, s, r.look == Atlas.SNOW)
                if (rail && road != null) {
                    // A level crossing, drawn over the road the way the track runs.
                    val mask = railMask(tx, ty)
                    surface.blend(base + Atlas.CROSSING + if (mask and 10 != 0 && mask and 5 == 0) 1 else 0, dx, dy)
                } else if (rail) {
                    surface.blend(base + Atlas.TRACK + railMask(tx, ty), dx, dy)
                }
            }
        }

        // Shadows, from casters up to a few tiles outside the chunk.
        val sun = r.sun
        if (sun != null) {
            surface.beginShadows(SHADOW_ALPHA * sun.strength)
            val thin = r.look == Atlas.BARE || r.look == Atlas.SNOW
            for (ty in max(0, y0 - SHADOW_MARGIN) until min(map.height, y1 + SHADOW_MARGIN)) {
                for (tx in max(0, x0 - SHADOW_MARGIN) until min(map.width, x1 + SHADOW_MARGIN)) {
                    val id = spriteAt(tx, ty, anchorOnly = true) ?: continue
                    shadows(surface, level, id, (tx - x0) * s, (ty - y0) * s, sun, thin && id < Atlas.COTTAGE)
                }
            }
            surface.endShadows()
        }

        // Sprites, back rows first, each building from its bottom row so what's in
        // front of it covers it. The rows below this chunk reach up into it.
        for (ty in y0 until min(y1 + SPRITE_ROWS, map.height)) for (tx in max(0, x0 - 1) until x1) {
            val i = map.index(tx, ty)
            val type = map.buildingType[i].toInt()
            if (type != 0) {
                val (ax, ay) = anchor(tx, ty)
                if (ax != tx || ty != bottom(tx, ty)) continue
                surface.blend(base + buildingSprite(ax, ay), (ax - x0) * s, (ay - y0) * s)
                if (map.forSale[i]) surface.blend(base + Atlas.FOR_SALE, (ax - x0) * s, (ay - y0) * s)
                continue
            }
            if (map.terrain[i] == Terrain.TREES) {
                // Trees in the worst of the grime lose their leaves.
                val treeBase = if (map.grimeLevel(i) == 3 && r.look != Atlas.SNOW) Atlas.BARE * Atlas.PER_LOOK else base
                surface.blend(treeBase + treeSprite(tx, ty), (tx - x0) * s, (ty - y0) * s)
            } else {
                if (map.streetTrees[i].toInt() != 0) surface.blend(base + Atlas.STREET_TREES, (tx - x0) * s, (ty - y0) * s)
                if (map.power[i] != Power.NONE) surface.blend(base + lineSprite(tx, ty), (tx - x0) * s, (ty - y0) * s)
            }
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

    /**
     * The look-free sprite that stands on a tile and casts a shadow: a tree, a
     * power pole, or a building from its top left tile. Null if nothing does.
     */
    private fun spriteAt(tx: Int, ty: Int, anchorOnly: Boolean): Int? {
        val i = map.index(tx, ty)
        val type = map.buildingType[i].toInt()
        if (type != 0) {
            val (ax, ay) = anchor(tx, ty)
            if (anchorOnly && (ax != tx || ay != ty)) return null
            return buildingSprite(ax, ay)
        }
        if (map.terrain[i] == Terrain.TREES) return treeSprite(tx, ty)
        if (map.power[i] != Power.NONE) return lineSprite(tx, ty)
        if (map.streetTrees[i].toInt() != 0) return Atlas.STREET_TREES
        return null
    }

    /**
     * The sprite for the building whose top left tile is [x], [y]. When the
     * building next to it on the west or north is the same type and shows the
     * same variant, it takes its next variant instead, so a row of them varies.
     */
    private fun buildingSprite(x: Int, y: Int): Int {
        val type = map.buildingType[map.index(x, y)].toInt() - 1
        val t = BuildingType.entries[type]
        // A building still going up is its site, dug or framed.
        val site = map.site[map.index(x, y)].toInt()
        if (site > 0) return (if (t.large) Atlas.SITE_LARGE else Atlas.SITE_SMALL) + site - 1
        if (t.railway) {
            // The pair of looks for the side the track is on, south or east being the second pair.
            val side = Rail.trackSide(map, t, x, y)
            val far = side == Heading.SOUTH.toInt() || side == Heading.EAST.toInt()
            return BuildingSprites.sprite(type, (if (far) 2 else 0) + (map.buildingVariant[map.index(x, y)].toInt() and 0xff) % 2)
        }
        return BuildingSprites.sprite(type, shownVariant(x, y, type, BuildingSprites.variants(type)))
    }

    /**
     * The variant shown on [x], [y], which follows from those shown to its west
     * and north, so it's worked out from the north-west and remembered for the
     * bake. The same map always gives the same answer, whichever chunk asks.
     */
    private fun shownVariant(x: Int, y: Int, type: Int, n: Int): Int {
        val i = map.index(x, y)
        var v = (map.buildingVariant[i].toInt() and 0xff) % n
        if (n == 1) return v
        shown[i]?.let { return it }
        val west = if (sameType(x - 1, y, type)) shownVariant(x - 1, y, type, n) else -1
        val north = if (sameType(x, y - 1, type)) shownVariant(x, y - 1, type, n) else -1
        repeat(n) { if (v == west || v == north) v = (v + 1) % n }
        shown[i] = v
        return v
    }

    private fun sameType(x: Int, y: Int, type: Int) = map.inside(x, y) && map.buildingType[map.index(x, y)].toInt() - 1 == type

    /** Variants worked out during one bake. */
    private val shown = HashMap<Int, Int>()

    /** The top left tile of the building on [x], [y]. */
    private fun anchor(x: Int, y: Int): Pair<Int, Int> {
        val id = map.building[map.index(x, y)]
        var ax = x
        var ay = y
        while (ax > 0 && map.building[map.index(ax - 1, y)] == id) ax--
        while (ay > 0 && map.building[map.index(ax, ay - 1)] == id) ay--
        return ax to ay
    }

    /** The bottom row of the building on [x], [y]. */
    private fun bottom(x: Int, y: Int): Int {
        val id = map.building[map.index(x, y)]
        var by = y
        while (by < map.height - 1 && map.building[map.index(x, by + 1)] == id) by++
        return by
    }

    /** A pole or a pylon, with its wires to the neighbours that carry the same kind of line. */
    private fun lineSprite(x: Int, y: Int): Int {
        val kind = map.power[map.index(x, y)]
        return (if (kind == Power.HIGH) Atlas.HV_LINE else Atlas.POWER_LINE) + powerMask(x, y, kind)
    }

    /** Which neighbours a line's wires run to: lines of its kind, power stations and substations. */
    private fun powerMask(x: Int, y: Int, kind: Byte): Int {
        var m = 0
        if (carries(x, y - 1, kind)) m = m or 1
        if (carries(x + 1, y, kind)) m = m or 2
        if (carries(x, y + 1, kind)) m = m or 4
        if (carries(x - 1, y, kind)) m = m or 8
        return m
    }

    private fun carries(x: Int, y: Int, kind: Byte): Boolean {
        if (!map.inside(x, y)) return false
        val i = map.index(x, y)
        if (map.building[i] == 0) return map.power[i] == kind
        val type = map.buildingType[i].toInt() - 1
        if (type < 0) return false
        val t = BuildingType.entries[type]
        return Generation.station(t) || t == BuildingType.SUBSTATION
    }

    /** Grime over the ground: a darker wash and, the worse it is, more bare dirt showing. */
    private fun soot(surface: BakeSurface, level: Int, h: Int, dx: Int, dy: Int, s: Int, atlasLevel: Int) {
        surface.fill(dx, dy, s, s, SOOT, SOOT_ALPHA[level])
        val patch = max(1, 4 shr atlasLevel)
        val spots = level * 3
        for (k in 0 until spots) {
            val px = ((h ushr (k * 3)) + k * 7) % (s - patch + 1)
            val py = ((h ushr (k * 2 + 1)) + k * 11) % (s - patch + 1)
            surface.fill(dx + px, dy + py, patch, patch, DIRT, 150)
        }
    }

    /** Fouled works land: dark, bare, rust-stained ground. */
    private fun brownfield(surface: BakeSurface, h: Int, dx: Int, dy: Int, s: Int, atlasLevel: Int) {
        surface.fill(dx, dy, s, s, SOOT, 170)
        val patch = max(1, 6 shr atlasLevel)
        for (k in 0 until 6) {
            val px = ((h ushr (k * 3)) + k * 5) % (s - patch + 1)
            val py = ((h ushr (k * 2 + 1)) + k * 9) % (s - patch + 1)
            surface.fill(dx + px, dy + py, patch, patch, if (k % 2 == 0) RUST else DIRT, 170)
        }
    }

    /**
     * A road tile in its type's art. A boulevard gets half its median on the
     * side it shares with the other carriageway, except where a road crosses,
     * and one-way roads get an arrow every few tiles along straight runs.
     */
    private fun roadTile(surface: BakeSurface, base: Int, type: RoadType, i: Int, tx: Int, ty: Int, mask: Int, dx: Int, dy: Int, level: Int) {
        surface.blend(base + roadArt(type) + mask, dx, dy)
        val heading = map.roadHeading[i].toInt()
        if (heading == 0) return
        if (type.width == 2) {
            // To the left of the way it runs, and the right.
            val left = (heading + 2) % 4 + 1
            val right = Heading.opposite(left)
            val lx = tx + Heading.DX[left]
            val ly = ty + Heading.DY[left]
            val paired = map.inside(lx, ly) && map.roadHeading[map.index(lx, ly)].toInt() == Heading.opposite(heading)
            // No median where a road crosses or where the boulevard ends, since traffic turns there.
            val crossed = road(tx + Heading.DX[right], ty + Heading.DY[right])
            val end = !road(tx + Heading.DX[heading], ty + Heading.DY[heading]) || !road(tx - Heading.DX[heading], ty - Heading.DY[heading])
            if (paired && !crossed && !end) surface.blend(base + Atlas.MEDIAN + left - 1, dx, dy)
        }
        val along = if (heading == Heading.NORTH.toInt() || heading == Heading.SOUTH.toInt()) ty else tx
        val straight = mask == 5 || mask == 10 || (type.width == 2 && (mask == 7 || mask == 13 || mask == 11 || mask == 14))
        if (level < 2 && straight && along % ARROW_EVERY == 0) surface.blend(base + Atlas.ARROW + heading - 1, dx, dy)
    }

    private fun roadArt(road: RoadType): Int = when (road) {
        RoadType.DIRT -> Atlas.ROAD_DIRT
        RoadType.GRAVEL -> Atlas.ROAD_GRAVEL
        RoadType.LANE -> Atlas.ROAD_LANE
        RoadType.STREET, RoadType.ONE_WAY_STREET -> Atlas.ROAD_STREET
        RoadType.AVENUE, RoadType.ONE_WAY_AVENUE, RoadType.BOULEVARD -> Atlas.ROAD_AVENUE
    }

    /**
     * An earth embankment: a grassy ridge along the tile, joined to the next
     * stretch on each side it carries on to, with a pale path along its crest.
     */
    private fun embankment(surface: BakeSurface, tx: Int, ty: Int, dx: Int, dy: Int, s: Int, snow: Boolean) {
        fun bank(x: Int, y: Int) = map.inside(x, y) && map.bank[map.index(x, y)].toInt() != 0
        val lo = s * 7 / 32
        val hi = s * 25 / 32
        val body = if (snow) BANK_SNOW else BANK_GRASS
        val crest = if (snow) BANK_CREST_SNOW else BANK_CREST
        surface.fill(dx + lo, dy + lo, hi - lo, hi - lo, body, 255)
        if (bank(tx, ty - 1)) surface.fill(dx + lo, dy, hi - lo, lo, body, 255)
        if (bank(tx, ty + 1)) surface.fill(dx + lo, dy + hi, hi - lo, s - hi, body, 255)
        if (bank(tx - 1, ty)) surface.fill(dx, dy + lo, lo, hi - lo, body, 255)
        if (bank(tx + 1, ty)) surface.fill(dx + hi, dy + lo, s - hi, hi - lo, body, 255)
        val c0 = s * 15 / 32
        val w = maxOf(1, s * 2 / 32)
        val northSouth = bank(tx, ty - 1) || bank(tx, ty + 1)
        val eastWest = bank(tx - 1, ty) || bank(tx + 1, ty)
        if (northSouth || !eastWest) surface.fill(dx + c0, dy + (if (bank(tx, ty - 1)) 0 else lo), w, (if (bank(tx, ty + 1)) s else hi) - (if (bank(tx, ty - 1)) 0 else lo), crest, 200)
        if (eastWest) surface.fill(dx + (if (bank(tx - 1, ty)) 0 else lo), dy + c0, (if (bank(tx + 1, ty)) s else hi) - (if (bank(tx - 1, ty)) 0 else lo), w, crest, 200)
    }

    /** Which neighbours are track: north 1, east 2, south 4, west 8. */
    private fun railMask(x: Int, y: Int): Int {
        fun track(tx: Int, ty: Int) = map.inside(tx, ty) && map.rail[map.index(tx, ty)] != Rail.NONE
        var m = 0
        if (track(x, y - 1)) m = m or 1
        if (track(x + 1, y)) m = m or 2
        if (track(x, y + 1)) m = m or 4
        if (track(x - 1, y)) m = m or 8
        return m
    }

    /** A bus and tram lane: a red band along each kerb, the way the road runs, left off at crossings. */
    private fun lanes(surface: BakeSurface, tx: Int, ty: Int, dx: Int, dy: Int, s: Int) {
        val mask = roadMask(tx, ty)
        val across = mask and 10 != 0 && mask and 5 == 0
        val down = mask and 5 != 0 && mask and 10 == 0
        if (!across && !down) return
        val a = s * 22 / 100
        val b = s * 66 / 100
        val w = maxOf(1, s * 16 / 100)
        if (across) {
            surface.fill(dx, dy + a, s, w, LANE, LANE_ALPHA)
            surface.fill(dx, dy + b, s, w, LANE, LANE_ALPHA)
        } else {
            surface.fill(dx + a, dy, w, s, LANE, LANE_ALPHA)
            surface.fill(dx + b, dy, w, s, LANE, LANE_ALPHA)
        }
    }

    /** Tram track set in the street, and the stops at its kerb. */
    private fun transitOn(surface: BakeSurface, base: Int, i: Int, tx: Int, ty: Int, dx: Int, dy: Int) {
        if (map.tram[i].toInt() != 0) surface.blend(base + Atlas.TRAMWAY + tramMask(tx, ty), dx, dy)
        if (map.wire[i].toInt() != 0) surface.blend(base + Atlas.TROLLEY_WIRE + layerMask(map.wire, tx, ty), dx, dy)
        val stops = map.stop[i].toInt()
        if (stops and Stop.TRAM != 0) surface.blend(base + Atlas.TRAM_STOP, dx, dy)
        else if (stops and Stop.BUS != 0) surface.blend(base + Atlas.BUS_STOP, dx, dy)
    }

    /** Which neighbours have something in [layer]: north 1, east 2, south 4, west 8. */
    private fun layerMask(layer: ByteArray, x: Int, y: Int): Int {
        fun on(tx: Int, ty: Int) = map.inside(tx, ty) && layer[map.index(tx, ty)].toInt() != 0
        var m = 0
        if (on(x, y - 1)) m = m or 1
        if (on(x + 1, y)) m = m or 2
        if (on(x, y + 1)) m = m or 4
        if (on(x - 1, y)) m = m or 8
        return m
    }

    /** Which neighbours have tram track: north 1, east 2, south 4, west 8. */
    private fun tramMask(x: Int, y: Int): Int {
        fun tram(tx: Int, ty: Int) = map.inside(tx, ty) && map.tram[map.index(tx, ty)].toInt() != 0
        var m = 0
        if (tram(x, y - 1)) m = m or 1
        if (tram(x + 1, y)) m = m or 2
        if (tram(x, y + 1)) m = m or 4
        if (tram(x - 1, y)) m = m or 8
        return m
    }

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
     * ground, with a line along the sides where the zone ends. Low density has
     * sparse dots, high density big ones.
     */
    private fun zoneTint(surface: BakeSurface, zone: Byte, density: Byte, tx: Int, ty: Int, dx: Int, dy: Int, s: Int, level: Int) {
        val colour = ZONE_COLOURS[zone.toInt()]
        surface.fill(dx, dy, s, s, ZONE_WASHES[zone.toInt()], ZONE_WASH)
        val spacing = (if (density == Density.LOW) ZONE_DOTS * 2 else ZONE_DOTS) shr level
        val dot = max(1, (if (density == Density.HIGH) 4 else 2) shr level)
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

    /**
     * The shadows of sprite [id]'s casters, with its first tile's top left at
     * [dx], [dy]. A tree's trunk is a thick line and its crown an oval stretched
     * away from the sun; a building's is its footprint swept along the shadow.
     */
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
            val i = (Atlas.casterStart[id] + k) * 6
            if (c[i] == Atlas.BOX) {
                val left = dx + c[i + 1] * scale
                val top = dy + c[i + 2] * scale
                val right = dx + c[i + 3] * scale
                val bottom = dy + c[i + 4] * scale
                val height = c[i + 5] * scale
                val ox = sun.shadowX * height
                val oy = sun.shadowY * height
                box[0] = left; box[1] = top; box[2] = right; box[3] = top
                box[4] = right; box[5] = bottom; box[6] = left; box[7] = bottom
                for (p in 0 until 4) {
                    box[8 + p * 2] = box[p * 2] + ox
                    box[9 + p * 2] = box[p * 2 + 1] + oy
                }
                surface.shadowPolygon(hull(box))
                continue
            }
            val fx = dx + c[i + 1] * scale
            val fy = dy + c[i + 2] * scale
            val height = c[i + 3] * scale
            val radius = c[i + 4] * scale
            val sx = fx + sun.shadowX * height
            val sy = fy + sun.shadowY * height
            surface.shadowLine(fx, fy, sx, sy, trunk)
            // Bare branches let most of the light through, leaving a thin streak.
            surface.shadowOval(sx, sy, ux, uy, radius * stretch, radius * if (thin) 0.4f else 0.85f)
        }
    }

    private val box = FloatArray(16)

    /** The convex hull of eight points given as x, y pairs, as x, y pairs going round. */
    private fun hull(p: FloatArray): FloatArray {
        val idx = (0 until 8).sortedWith(compareBy({ p[it * 2] }, { p[it * 2 + 1] }))
        fun cross(o: Int, a: Int, b: Int) =
            (p[a * 2] - p[o * 2]) * (p[b * 2 + 1] - p[o * 2 + 1]) - (p[a * 2 + 1] - p[o * 2 + 1]) * (p[b * 2] - p[o * 2])
        val out = IntArray(16)
        var n = 0
        for (i in idx) {
            while (n >= 2 && cross(out[n - 2], out[n - 1], i) <= 0f) n--
            out[n++] = i
        }
        val lower = n + 1
        for (i in idx.reversed().drop(1)) {
            while (n >= lower && cross(out[n - 2], out[n - 1], i) <= 0f) n--
            out[n++] = i
        }
        n--
        return FloatArray(n * 2) { k -> p[out[k / 2] * 2 + k % 2] }
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
        private const val LANE = 0xB8443A
        private const val LANE_ALPHA = 190
        val ZONE_COLOURS = intArrayOf(0, 0x4CC23A, 0x3C78D7, 0xDCAA28, 0xA6703C, 0x9B5CC8)
        private val ZONE_WASHES = intArrayOf(0, 0xDDF7B8, 0xC4DAFF, 0xFFE9A6, 0xEBD7B4, 0xE6D4F2)
        private const val ZONE_WASH = 95
        private const val ZONE_LINE = 230
        private const val ZONE_DOT = 255

        /** Dots every this many pixels at 32 px a tile, fewer as the tiles get smaller, none when they'd run together. */
        private const val ZONE_DOTS = 8

        /** Grime: soot over land, murk over water, and dirt showing through, by grime level. */
        private const val SOOT = 0x3F3830
        private val SOOT_ALPHA = intArrayOf(0, 45, 85, 130)
        private const val MURK = 0x5C5A3C

        /** Embankments: grass, and the path along the top. */
        private const val BANK_GRASS = 0x4E7A34
        private const val BANK_CREST = 0xB8A57A
        private const val BANK_SNOW = 0xDCE4EA
        private const val BANK_CREST_SNOW = 0xB9C3CB

        /** Sewage in the water: a brown-green scum, thicker the fouler it is. */
        private const val SEWAGE = 0x5E5A2A
        private val SEWAGE_ALPHA = intArrayOf(0, 55, 95, 135)
        private val MURK_ALPHA = intArrayOf(0, 45, 85, 125)
        private const val DIRT = 0x6E5E48
        private const val RUST = 0x8A4A2A

        /** How many rows below a chunk have sprites tall enough to reach into it. */
        const val SPRITE_ROWS = 3

        /** One-way roads get an arrow every this many tiles. */
        private const val ARROW_EVERY = 3

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
