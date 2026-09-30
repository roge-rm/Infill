package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TerrainGenTest {
    private fun make(seed: Long, options: TerrainOptions = TerrainOptions()) =
        CityMap(128, 128).also { TerrainGen.generate(it, seed, options) }

    @Test
    fun sameSeedSameMap() {
        assertEquals(make(1900).hash(), make(1900).hash())
        assertNotEquals(make(1900).hash(), make(1901).hash())
    }

    @Test
    fun sharesAreSensible() {
        for (seed in 1L..20L) {
            val m = make(seed)
            val water = m.terrain.count { it == Terrain.WATER } * 100 / m.size
            val trees = m.terrain.count { it == Terrain.TREES } * 100 / m.size
            assertTrue(water in 3..30, "seed $seed has $water% water")
            assertTrue(trees in 10..40, "seed $seed has $trees% trees")
        }
    }

    @Test
    fun noWaterOrTreesWhenAskedForNone() {
        val m = make(5, TerrainOptions(water = 0, trees = 0, river = false))
        assertTrue(m.terrain.all { it == Terrain.GRASS })
    }

    /** A lone water tile or a one tile spike of water can't be drawn as a shore, so none are left. */
    @Test
    fun noLoneWater() {
        for (seed in 1L..10L) {
            val m = make(seed)
            for (y in 0 until m.height) for (x in 0 until m.width) {
                if (m.terrainAt(x, y) != Terrain.WATER) continue
                var wet = 0
                for ((dx, dy) in listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)) {
                    val v = if (m.inside(x + dx, y + dy)) m.terrainAt(x + dx, y + dy) else Terrain.WATER
                    if (v == Terrain.WATER) wet++
                }
                assertTrue(wet >= 2, "seed $seed: water at $x,$y has $wet water neighbours")
            }
        }
    }

    @Test
    fun noiseStaysInRange() {
        for (i in 0 until 5000) {
            val n = TerrainGen.fractal(i * 7 - 300, i * 13 - 900, 42)
            assertTrue(n in 0..65535)
        }
    }
}
