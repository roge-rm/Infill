package com.rm.infill.sim

/**
 * How much of a new map is water and woods, from 0 to 100, and whether a river
 * runs across it.
 */
data class TerrainOptions(val water: Int = 30, val trees: Int = 40, val river: Boolean = true, val quakes: Boolean = false)

/**
 * Makes the land for a new city: lakes where a noise field is lowest, a river
 * from one edge to the opposite one, and woods where a second noise field is
 * highest, with a few trees on their own. Integer maths only, so a seed makes
 * the same map everywhere.
 */
object TerrainGen {
    fun generate(map: CityMap, seed: Long, options: TerrainOptions = TerrainOptions()) {
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

        if (options.river && options.water > 0) river(map, rng, 1 + options.water / 30)
        tidy(map)

        // Woods: the highest share of another noise field, then a few trees on their own.
        val woods = IntArray(map.size) { fractal(it % map.width, it / map.width, woodSeed) }
        val woodShare = options.trees.coerceIn(0, 100) * 55 / 100
        val woodLine = percentile(woods, 100 - woodShare)
        for (i in 0 until map.size) {
            if (t[i] != Terrain.GRASS) continue
            if (woodShare > 0 && woods[i] >= woodLine) t[i] = Terrain.TREES
            else if (rng.nextInt(1000) < options.trees / 2) t[i] = Terrain.TREES
        }
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
    private val NEIGHBOURS = arrayOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)
}
