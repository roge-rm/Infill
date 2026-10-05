package com.rm.infill.sim

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * How much of a new map is water and woods, from 0 to 100, whether a river
 * runs across it, whether it has earthquakes, and its [climate], which has
 * more woods or fewer. With [Sea.COAST], [seaSides] says which sides are sea,
 * by [SeaSide] bits.
 */
data class TerrainOptions(
    val water: Int = 30, val trees: Int = 40, val river: Boolean = true, val quakes: Boolean = false, val climate: Climate = Climate.TEMPERATE,
    val sea: Sea = Sea.NONE, val seaSides: Int = 0,
)

/**
 * Where the sea is: none, along one, two or three sides picked by the map
 * number (from before the sides could be chosen), round one island, among
 * several, or along the sides chosen.
 */
enum class Sea { NONE, ONE_SIDE, TWO_SIDES, THREE_SIDES, ISLAND, ISLANDS, COAST }

/** The sides of the map, as bits for [TerrainOptions.seaSides], in the order the sea works in: west, north, east, south. */
object SeaSide {
    const val WEST = 1
    const val NORTH = 2
    const val EAST = 4
    const val SOUTH = 8
    const val ALL = 15

    /** The sides in [mask], as 0 west to 3 south. */
    fun list(mask: Int): List<Int> = (0 until 4).filter { mask and (1 shl it) != 0 }
}

/**
 * Makes the land for a new city: lakes where a noise field is lowest, a river
 * from one edge to the opposite one, and woods where a second noise field is
 * highest, with a few trees on their own. Integer maths only, so a seed makes
 * the same map everywhere.
 */
object TerrainGen {
    /** [squares] is how many town squares the map is across, for a region's land; the sea leaves each at least half land. */
    fun generate(map: CityMap, seed: Long, options: TerrainOptions = TerrainOptions(), squares: Int = 1) {
        val rng = Rng(seed xor TERRAIN_SALT)
        val lakeSeed = rng.nextLong().toInt()
        val woodSeed = rng.nextLong().toInt()
        val t = map.terrain
        t.fill(Terrain.GRASS)

        // Lakes: the lowest share of a noise field.
        val lakes = IntArray(map.size) { fractal(it % map.width, it / map.width, lakeSeed) }
        val lakeShare = options.water.coerceIn(0, 100) * 25 / 100 // percent of the map
        val lakeLine = percentile(lakes, lakeShare)
        for (i in 0 until map.size) if (lakeShare > 0 && lakes[i] < lakeLine) t[i] = Terrain.WATER

        val coast = options.sea == Sea.COAST && options.seaSides and SeaSide.ALL != 0
        val sea = if (options.sea != Sea.NONE && (options.sea != Sea.COAST || coast)) sea(map, seed, options.sea, squares, options.seaSides) else null
        val before = t.copyOf()
        if (options.river && options.water > 0) {
            if (sea != null) riverToSea(map, rng, 1 + options.water / 30, seed, options.sea, squares, sea, options.seaSides)
            else river(map, rng, 1 + options.water / 30)
        }
        tidy(map)
        // Each town at least half land, with or without the sea; the river, as what it made water that wasn't, is kept.
        val river = BooleanArray(map.size) { before[it] != Terrain.WATER && t[it] == Terrain.WATER }
        halfLand(map, squares, sea ?: BooleanArray(map.size), river)

        // Woods: the highest share of another noise field, then a few trees on their own.
        val woods = IntArray(map.size) { fractal(it % map.width, it / map.width, woodSeed) }
        val woodShare = (options.trees.coerceIn(0, 100) * options.climate.trees / 100).coerceIn(0, 100) * 55 / 100
        val woodLine = percentile(woods, 100 - woodShare)
        for (i in 0 until map.size) {
            if (t[i] != Terrain.GRASS) continue
            if (woodShare > 0 && woods[i] >= woodLine) t[i] = Terrain.TREES
            else if (rng.nextInt(1000) < options.trees * options.climate.trees / 200) t[i] = Terrain.TREES
        }
        resources(map, seed)
    }

    /**
     * What's in the ground: good soil over broad stretches, and a few seams of
     * iron ore and of coal, and oil fields. With [awayFrom], for a town made before there were
     * resources, the seams go only where nothing's built or zoned near them.
     * Its own random numbers, so the land is the same as without it.
     */
    fun resources(map: CityMap, seed: Long, awayFrom: ((Int) -> Boolean)? = null) {
        val rng = Rng(seed xor RESOURCE_SALT)
        val soilSeed = rng.nextLong().toInt()
        val r = map.resource
        r.fill(Resource.NONE)
        val soil = IntArray(map.size) { fractal(it % map.width, it / map.width, soilSeed) }
        val soilLine = percentile(soil, 100 - FERTILE_SHARE)
        for (i in 0 until map.size) if (map.terrain[i] != Terrain.WATER && soil[i] >= soilLine) r[i] = Resource.FERTILE
        val seams = maxOf(1, map.size / SEAM_AREA)
        for (kind in listOf(Resource.ORE, Resource.COAL, Resource.OIL)) repeat(seams) {
            // A few tries for a spot on dry land, clear of the town.
            var at = -1
            repeat(40) {
                val i = rng.nextInt(map.size)
                if (at < 0 && map.terrain[i] != Terrain.WATER && (awayFrom == null || !builtNear(map, i, awayFrom))) at = i
            }
            if (at < 0) return@repeat
            val cx = at % map.width
            val cy = at / map.width
            val radius = SEAM_RADIUS + rng.nextInt(3)
            for (y in cy - radius..cy + radius) for (x in cx - radius..cx + radius) {
                if (!map.inside(x, y)) continue
                val i = map.index(x, y)
                val dx = x - cx
                val dy = y - cy
                // A ragged edge.
                if (dx * dx + dy * dy > radius * radius - rng.nextInt(radius * 2 + 1) || map.terrain[i] == Terrain.WATER) continue
                if (awayFrom != null && awayFrom(i)) continue
                r[i] = kind
            }
        }
    }

    private fun builtNear(map: CityMap, i: Int, built: (Int) -> Boolean): Boolean {
        val x = i % map.width
        val y = i / map.width
        for (ty in y - 4..y + 4) for (tx in x - 4..x + 4) if (map.inside(tx, ty) && built(map.index(tx, ty))) return true
        return false
    }

    /** A river that wanders from one edge to the one across from it, [halfWidth] tiles either side of its line. */
    private fun river(map: CityMap, rng: Rng, halfWidth: Int) {
        val across = rng.nextInt(2) == 0 // west to east, or north to south
        val length = if (across) map.width else map.height
        val span = if (across) map.height else map.width
        var pos = span / 4 + rng.nextInt(span / 2)
        var drift = 0
        for (step in 0 until length) {
            // The drift changes a little at a time, so the river bends rather than jitters.
            drift = (drift + rng.nextInt(5) - 2).coerceIn(-6, 6)
            if (step % 3 == 0) pos = (pos + drift / 3).coerceIn(halfWidth + 2, span - halfWidth - 3)
            val width = halfWidth + if (rng.nextInt(8) == 0) 1 else 0
            for (o in -width..width) {
                val x = if (across) step else pos + o
                val y = if (across) pos + o else step
                if (map.inside(x, y)) map.terrain[map.index(x, y)] = Terrain.WATER
            }
        }
    }

    /**
     * The sea, in a map of [squares] by [squares] town squares, measured in
     * squares so every town keeps at least half its land: sides come in a
     * quarter of a square at most, an island is the whole map less a strip of
     * sea round it, and islands are one to each square (four on a town by
     * itself), each with sea round it. The shore is ragged by noise. Which
     * sides goes by the map's number. Returns which tiles it made sea. Its own
     * random numbers, so a map without sea is as it was.
     */
    private fun sea(map: CityMap, seed: Long, sea: Sea, squares: Int, chosen: Int = 0): BooleanArray {
        val rng = Rng(seed xor SEA_SALT)
        val first = rng.nextInt(4)
        val shapeSeed = rng.nextLong().toInt()
        val edgeSeed = rng.nextLong().toInt()
        if (sea == Sea.ISLANDS || sea == Sea.ISLAND) return islands(map, rng, squares, shapeSeed, edgeSeed, one = sea == Sea.ISLAND)
        val w = map.width
        val h = map.height
        // Sides as west, north, east, south: the first, and the ones round from it.
        val sides = when (sea) {
            Sea.ONE_SIDE -> listOf(first)
            Sea.TWO_SIDES -> listOf(first, (first + 1) % 4)
            Sea.THREE_SIDES -> listOf(first, (first + 1) % 4, (first + 2) % 4)
            Sea.ISLAND -> listOf(0, 1, 2, 3)
            Sea.COAST -> SeaSide.list(chosen)
            else -> emptyList()
        }
        // How far the sea comes in, in squares: as far as leaves the square that has it on the most sides half land.
        val depth = when {
            sea == Sea.ISLANDS -> ISLANDS_DEPTH
            squares > 1 -> SIDE_DEPTH
            sea == Sea.ONE_SIDE -> 0.3f
            sea == Sea.TWO_SIDES -> 0.24f
            sea == Sea.THREE_SIDES -> 0.17f
            sea == Sea.COAST -> DEPTHS[sides.size.coerceIn(1, 4) - 1]
            else -> 0.12f
        }
        // Islands: a grid of them, one to a square, or four on a town by itself.
        val cells = if (sea == Sea.ISLANDS) (if (squares == 1) 2 else squares) else 1
        val wet = BooleanArray(map.size)
        for (i in 0 until map.size) {
            val x = i % w
            val y = i / w
            // In squares (or island cells) from the map's top left.
            val u = x / (w - 1f) * (if (sea == Sea.ISLANDS) cells else squares)
            val v = y / (h - 1f) * (if (sea == Sea.ISLANDS) cells else squares)
            val n = if (sea == Sea.ISLANDS) cells.toFloat() else squares.toFloat()
            val d = if (sea == Sea.ISLANDS) {
                // To the nearest edge of this tile's own cell, with its corners rounded.
                val fx = u - floor(u).coerceAtMost(n - 1)
                val fy = v - floor(v).coerceAtMost(n - 1)
                val dx = minOf(fx, 1 - fx)
                val dy = minOf(fy, 1 - fy)
                if (dx < depth && dy < depth) depth - sqrt((depth - dx) * (depth - dx) + (depth - dy) * (depth - dy)) else minOf(dx, dy)
            } else {
                sides.minOf { side ->
                    when (side) {
                        0 -> u
                        1 -> v
                        2 -> n - u
                        else -> n - v
                    }
                }
            }
            // Ragged: a broad wobble and a finer one, a few tiles either way.
            val broad = fractal(x, y, shapeSeed) / 65535f - 0.5f
            val fine = fractal(x * 3, y * 3, edgeSeed) / 65535f - 0.5f
            val shore = depth * (1f + broad * 0.9f + fine * 0.3f)
            if (d < shore) wet[i] = true
        }
        for (i in 0 until map.size) if (wet[i]) map.terrain[i] = Terrain.WATER
        return wet
    }

    /**
     * Islands of all sizes scattered over the map, a big one or two that
     * towns share, middling ones and small ones, each a rough round with a
     * ragged shore. Then any town square under half land gets another island
     * of its own, as big as it's short, until it's made up.
     */
    private fun islands(map: CityMap, rng: Rng, squares: Int, shapeSeed: Int, edgeSeed: Int, one: Boolean = false): BooleanArray {
        val w = map.width
        val side = w / squares.toFloat()
        val n = squares.toFloat()
        val warpX = rng.nextLong().toInt()
        val warpY = rng.nextLong().toInt()
        // Each island: its middle, its long and short radius and which way it lies, in squares.
        class Isle(val x: Float, val y: Float, val r: Float, val narrow: Float, val angle: Float)
        val isles = ArrayList<Isle>()
        fun rand() = rng.nextInt(10_000) / 10_000f
        fun add(x: Float, y: Float, r: Float) {
            isles += Isle(x, y, r, 0.45f + rand() * 0.55f, rand() * PI.toFloat())
        }
        fun place(r: Float) {
            // Kept off the map's edge, mostly, and apart from the others so there's sea between.
            val lo = minOf(r * 0.7f, n / 2)
            repeat(20) {
                val x = lo + rand() * (n - 2 * lo)
                val y = lo + rand() * (n - 2 * lo)
                if (isles.all { o -> sqrt((o.x - x) * (o.x - x) + (o.y - y) * (o.y - y)) > (o.r + r) * 0.75f }) {
                    add(x, y, r)
                    return
                }
            }
        }
        val area = n * n
        if (one) {
            // One big island filling most of the map, lying a little one way or another, and a few islets off it.
            isles += Isle(n / 2 + (rand() - 0.5f) * 0.08f * n, n / 2 + (rand() - 0.5f) * 0.08f * n, 0.43f * n, 0.85f + rand() * 0.15f, rand() * PI.toFloat())
            repeat((area * 0.6f).toInt() + 2) { place(0.04f + rand() * 0.08f) }
        }
        // Sizes from a few large ones, that towns share, down to many small ones.
        val count = if (one) 0 else (area * 2.2f).toInt() + 3
        // On a town by itself or a small region the largest are smaller, so it's still islands rather than one.
        val largest = 0.7f * minOf(1f, n / 2.6f)
        val sizes = List(count) { 0.06f + largest * rand().let { it * it * it } }.sortedDescending()
        for (r in sizes) place(r)
        val land = BooleanArray(map.size)
        // How far the map's pushed about, in squares: more on a bigger region, where the shapes are bigger.
        val warp = 0.3f + 0.1f * n
        fun paint() {
            for (i in 0 until map.size) {
                val x = i % w
                val y = i / w
                // The map pushed about by noise first, so shores bend into bays and run out in arms.
                val u = x / side + (fractal(x / 2, y / 2, warpX) / 65535f - 0.5f) * warp
                val v = y / side + (fractal(x / 2, y / 2, warpY) / 65535f - 0.5f) * warp
                var f = -1f
                for (s in isles) {
                    val dx = u - s.x
                    val dy = v - s.y
                    val reach = s.r * 1.4f
                    if (dx > reach || dx < -reach || dy > reach || dy < -reach) continue
                    // Along the island's length and across it.
                    val c = kotlin.math.cos(s.angle)
                    val sn = kotlin.math.sin(s.angle)
                    val along = (dx * c + dy * sn) / s.r
                    val across = (-dx * sn + dy * c) / (s.r * s.narrow)
                    f = maxOf(f, 1f - sqrt(along * along + across * across))
                }
                val broad = fractal(x, y, shapeSeed) / 65535f - 0.5f
                val fine = fractal(x * 3, y * 3, edgeSeed) / 65535f - 0.5f
                // Open sea along the map's edge, the land shelving off into it.
                // A gentle slope rather than a wall, so the shore curves away rather than running straight along it.
                val edge = minOf(minOf(x, y), minOf(w - 1 - x, map.height - 1 - y)) / (EDGE_SEA * side)
                val off = if (edge < 1f) (1f - edge) * (1f - edge) * 1.6f else 0f
                land[i] = f + broad * 0.5f + fine * 0.2f - off > 0.05f
            }
        }
        paint()
        // Make up any town short of land with more islands, out in its emptiest water.
        repeat(5) {
            var added = false
            for (sy in 0 until squares) for (sx in 0 until squares) {
                val x0 = (sx * side).toInt()
                val y0 = (sy * side).toInt()
                val x1 = ((sx + 1) * side).toInt()
                val y1 = ((sy + 1) * side).toInt()
                var count2 = 0
                for (yy in y0 until y1) for (xx in x0 until x1) if (land[map.index(xx, yy)]) count2++
                val share = count2.toFloat() / ((x1 - x0) * (y1 - y0))
                if (share >= ISLAND_LAND) continue
                // The sea tile in this square furthest from any land, by a coarse look.
                var best = -1f
                var bx = sx + 0.5f
                var by = sy + 0.5f
                val step = maxOf(2, (side / 16).toInt())
                for (yy in y0 until y1 step step) for (xx in x0 until x1 step step) {
                    if (land[map.index(xx, yy)]) continue
                    var near = Float.MAX_VALUE
                    for (oy in y0 until y1 step step) for (ox in x0 until x1 step step) if (land[map.index(ox, oy)]) {
                        val d = ((ox - xx) * (ox - xx) + (oy - yy) * (oy - yy)).toFloat()
                        if (d < near) near = d
                    }
                    if (near > best) {
                        best = near
                        bx = xx / side
                        by = yy / side
                    }
                }
                val r = (sqrt((ISLAND_LAND + 0.06f - share) / PI.toFloat()) * 1.5f).coerceIn(0.1f, 0.42f)
                add(bx.coerceIn(sx + r * 0.6f, sx + 1 - r * 0.6f), by.coerceIn(sy + r * 0.6f, sy + 1 - r * 0.6f), r)
                added = true
            }
            if (!added) return@repeat
            paint()
        }
        val wet = BooleanArray(map.size) { !land[it] }
        for (i in 0 until map.size) if (wet[i]) map.terrain[i] = Terrain.WATER
        return wet
    }

    /**
     * A river out to the sea: from the far side of the land across to a side
     * that's sea, or for an island from its middle out to its shore. The sea's
     * sides are worked out as [sea] did, from the same numbers.
     */
    private fun riverToSea(map: CityMap, rng: Rng, halfWidth: Int, seed: Long, sea: Sea, squares: Int, wet: BooleanArray, chosen: Int = 0) {
        val first = Rng(seed xor SEA_SALT).nextInt(4)
        val w = map.width
        val h = map.height
        // Sides as west, north, east, south; the way out is towards [to], from [from] or from inland.
        val (from, to) = when (sea) {
            Sea.ONE_SIDE -> (first + 2) % 4 to first
            Sea.TWO_SIDES -> (first + 2) % 4 to first
            Sea.THREE_SIDES -> (first + 3) % 4 to (first + 1) % 4
            // Out to a side that's sea, from the side across from it if that's land, or from inland.
            Sea.COAST -> {
                val sides = SeaSide.list(chosen)
                val to = sides.firstOrNull { it == first } ?: sides[first % sides.size]
                (if ((to + 2) % 4 in sides) -1 else (to + 2) % 4) to to
            }
            else -> -1 to first
        }
        // Along the way it goes, a step at a time.
        val ax = when (to) { 0 -> -1; 2 -> 1; else -> 0 }
        val ay = when (to) { 1 -> -1; 3 -> 1; else -> 0 }
        val across = ax != 0
        val length = if (across) w else h
        val span = if (across) h else w
        var pos: Int
        var along: Int
        if (from >= 0) {
            pos = span / 4 + rng.nextInt(span / 2)
            along = if ((across && ax > 0) || (!across && ay > 0)) 0 else length - 1
        } else {
            // From the furthest inland of the island, or of the biggest of the islands.
            val dist = IntArray(map.size) { -1 }
            val queue = IntArray(map.size)
            var head = 0
            var tail = 0
            for (i in 0 until map.size) if (wet[i]) {
                dist[i] = 0
                queue[tail++] = i
            }
            while (head < tail) {
                val i = queue[head++]
                val x = i % w
                val y = i / w
                for ((dx, dy) in NEIGHBOURS) {
                    val nx = x + dx
                    val ny = y + dy
                    if (!map.inside(nx, ny)) continue
                    val j = map.index(nx, ny)
                    if (dist[j] >= 0) continue
                    dist[j] = dist[i] + 1
                    queue[tail++] = j
                }
            }
            val inland = (0 until map.size).maxBy { dist[it] }
            pos = if (across) inland / w else inland % w
            along = if (across) inland % w else inland / w
            // It rises in a lake, a rough round a few tiles across.
            val lx = inland % w
            val ly = inland / w
            val r = 3 + halfWidth * 2 + rng.nextInt(3)
            for (dy in -r - 2..r + 2) for (dx in -r - 2..r + 2) {
                val x = lx + dx
                val y = ly + dy
                if (!map.inside(x, y)) continue
                val ragged = r + (fractal(x * 4, y * 4, seed.toInt() xor 0x1a4e) / 65535f - 0.5f) * 4f
                if (dx * dx + dy * dy <= ragged * ragged) map.terrain[map.index(x, y)] = Terrain.WATER
            }
        }
        var drift = 0
        var out = 0
        while (along in 0 until length) {
            drift = (drift + rng.nextInt(5) - 2).coerceIn(-6, 6)
            if (along % 3 == 0) pos = (pos + drift / 3).coerceIn(halfWidth + 2, span - halfWidth - 3)
            val width = halfWidth + if (rng.nextInt(8) == 0) 1 else 0
            var inSea = true
            for (o in -width..width) {
                val x = if (across) along else pos + o
                val y = if (across) pos + o else along
                if (!map.inside(x, y)) continue
                val i = map.index(x, y)
                if (!wet[i]) inSea = false
                map.terrain[i] = Terrain.WATER
            }
            // A little way out into the sea, and done.
            if (inSea && ++out > 2) break
            along += if (across) ax else ay
        }
    }

    /**
     * Makes sure each of [squares] by [squares] town squares is at least half
     * land: where one isn't, it turns sea back into land, then lakes, nearest
     * the square's middle first. The river stays.
     */
    private fun halfLand(map: CityMap, squares: Int, sea: BooleanArray, river: BooleanArray) {
        val w = map.width
        val side = w / squares
        for (sy in 0 until squares) for (sx in 0 until squares) {
            val x0 = sx * side
            val y0 = sy * side
            val x1 = if (sx == squares - 1) w else x0 + side
            val y1 = if (sy == squares - 1) map.height else y0 + side
            val tiles = (x1 - x0) * (y1 - y0)
            var land = 0
            for (y in y0 until y1) for (x in x0 until x1) if (map.terrain[map.index(x, y)] != Terrain.WATER) land++
            var short = (tiles + 1) / 2 - land
            if (short <= 0) continue
            val cx = (x0 + x1) / 2f
            val cy = (y0 + y1) / 2f
            for (lakes in listOf(false, true)) {
                if (short <= 0) break
                val order = ArrayList<Int>()
                for (y in y0 until y1) for (x in x0 until x1) {
                    val i = map.index(x, y)
                    if (map.terrain[i] == Terrain.WATER && !river[i] && sea[i] != lakes) order += i
                }
                order.sortBy { val dx = it % w - cx; val dy = it / w - cy; dx * dx + dy * dy }
                for (i in order) {
                    if (short <= 0) break
                    map.terrain[i] = Terrain.GRASS
                    short--
                }
            }
        }
    }

    /**
     * Rounds off shores: water with fewer than two water neighbours becomes land,
     * and land with three or more water neighbours becomes water.
     */
    private fun tidy(map: CityMap) {
        val t = map.terrain
        repeat(2) {
            for (y in 0 until map.height) for (x in 0 until map.width) {
                val i = map.index(x, y)
                val wet = waterAround(map, x, y)
                if (t[i] == Terrain.WATER && wet < 2) t[i] = Terrain.GRASS
                else if (t[i] != Terrain.WATER && wet >= 3) t[i] = Terrain.WATER
            }
        }
    }

    /** Water among the four neighbours. Past the edge counts as whatever the tile is. */
    private fun waterAround(map: CityMap, x: Int, y: Int): Int {
        val self = map.terrainAt(x, y)
        var n = 0
        for ((dx, dy) in NEIGHBOURS) {
            val nx = x + dx
            val ny = y + dy
            val v = if (map.inside(nx, ny)) map.terrainAt(nx, ny) else self
            if (v == Terrain.WATER) n++
        }
        return n
    }

    /** The value that [percent] of [values] fall below. */
    private fun percentile(values: IntArray, percent: Int): Int {
        if (percent <= 0) return Int.MIN_VALUE
        if (percent >= 100) return Int.MAX_VALUE
        val sorted = values.copyOf().also { it.sort() }
        return sorted[(sorted.size * percent / 100).coerceIn(0, sorted.size - 1)]
    }

    /** Four octaves of value noise, 0 to 65535. The largest features are about 32 tiles across. */
    fun fractal(x: Int, y: Int, seed: Int): Int {
        var sum = 0
        var weight = 8
        var cell = 32
        var octave = 0
        while (cell >= 4) {
            sum += valueNoise(x, y, cell, seed + octave * 7919) * weight
            weight /= 2
            cell /= 2
            octave++
        }
        return sum / 15
    }

    /** Smoothly blended random values on a grid [cell] tiles apart, 0 to 65535. */
    private fun valueNoise(x: Int, y: Int, cell: Int, seed: Int): Int {
        val cx = x.floorDiv(cell)
        val cy = y.floorDiv(cell)
        val tx = smooth((x - cx * cell) * ONE / cell)
        val ty = smooth((y - cy * cell) * ONE / cell)
        val a = lattice(cx, cy, seed)
        val b = lattice(cx + 1, cy, seed)
        val c = lattice(cx, cy + 1, seed)
        val d = lattice(cx + 1, cy + 1, seed)
        val top = a + (b - a) * tx / ONE
        val bottom = c + (d - c) * tx / ONE
        return top + (bottom - top) * ty / ONE
    }

    /** 3t² - 2t³ with t out of [ONE]. */
    private fun smooth(t: Int): Int = (t * t / ONE) * (3 * ONE - 2 * t) / ONE

    private fun lattice(x: Int, y: Int, seed: Int): Int {
        var h = seed + x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and 0xffff
    }

    private const val ONE = 1024
    private const val TERRAIN_SALT = 0x7e77a1L
    private const val RESOURCE_SALT = 0x5ea3501L
    private const val SEA_SALT = 0x5eaL shl 20

    /**
     * How far in the sea comes, in squares, on a region (a corner square with
     * sea on two sides keeps 0.73 × 0.73 of itself), and round each island.
     */
    private const val SIDE_DEPTH = 0.27f
    /** How far the sea comes in on a coast of one to four sides chosen, in town squares. */
    private val DEPTHS = floatArrayOf(0.3f, 0.24f, 0.17f, 0.12f)
    private const val ISLANDS_DEPTH = 0.13f

    /** How far in from the edge of an island map the land starts to shelve off into the sea, in squares. */
    private const val EDGE_SEA = 0.12f

    /** The share of each town the islands make up to before the last pass. */
    private const val ISLAND_LAND = 0.52f

    /** Percent of the land with good soil; one seam each of ore and coal for this many tiles, and how big. */
    private const val FERTILE_SHARE = 35
    private const val SEAM_AREA = 4096
    private const val SEAM_RADIUS = 3
    private val NEIGHBOURS = arrayOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)
}
