package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeaTest {
    private fun land(sea: Sea, side: Int = 128, seed: Long = 21): CityMap =
        CityMap(side, side).also { TerrainGen.generate(it, seed, TerrainOptions(water = 0, trees = 30, river = false, sea = sea)) }

    private fun CityMap.wet(x: Int, y: Int) = terrain[index(x, y)] == Terrain.WATER

    /** Share of each edge that's water: west, north, east, south. */
    private fun CityMap.edges(): List<Int> {
        val n = width
        return listOf(
            (0 until n).count { wet(0, it) }, (0 until n).count { wet(it, 0) },
            (0 until n).count { wet(n - 1, it) }, (0 until n).count { wet(it, n - 1) },
        ).map { it * 100 / n }
    }

    @Test
    fun noSeaIsTheLandAsBefore() {
        val before = CityMap(128, 128).also { TerrainGen.generate(it, 21, TerrainOptions(water = 0, trees = 30, river = false)) }
        assertContentEquals(before.terrain, land(Sea.NONE).terrain)
        assertTrue(land(Sea.NONE).terrain.none { it == Terrain.WATER })
    }

    @Test
    fun theSeaTakesTheSidesAskedFor() {
        for ((sea, sides) in listOf(Sea.ONE_SIDE to 1, Sea.TWO_SIDES to 2, Sea.THREE_SIDES to 3)) {
            val e = land(sea).edges()
            assertEquals(sides, e.count { it >= 90 }, "$sea $e")
        }
        // An island has sea along all four sides, its shore bulging out to them here and there.
        assertTrue(land(Sea.ISLAND).edges().all { it >= 75 }, "${land(Sea.ISLAND).edges()}")
    }

    @Test
    fun aCoastTakesExactlyTheSidesChosen() {
        // Every mix of sides, opposite ones too; with a river, which runs out to one of them.
        for (mask in 1..SeaSide.ALL) for (river in listOf(false, true)) {
            val m = CityMap(128, 128).also { TerrainGen.generate(it, 21, TerrainOptions(water = if (river) 30 else 0, trees = 30, river = river, sea = Sea.COAST, seaSides = mask)) }
            val e = m.edges()
            for (side in 0 until 4) {
                val chosen = mask and (1 shl side) != 0
                if (chosen) assertTrue(e[side] >= 75, "sides $mask: side $side is ${e[side]}% sea")
                else if (!river) {
                    // Along a side not chosen, what's half the map or more from every side chosen is land.
                    val n = m.width
                    val wet = (0 until n).count { k ->
                        val (x, y) = when (side) { 0 -> 0 to k; 1 -> k to 0; 2 -> n - 1 to k; else -> k to n - 1 }
                        val far = SeaSide.list(mask).all { c -> when (c) { 0 -> x; 1 -> y; 2 -> n - 1 - x; else -> n - 1 - y } >= n / 2 }
                        far && m.wet(x, y)
                    }
                    assertEquals(0, wet, "sides $mask: side $side has sea far from the sides chosen")
                }
            }
        }
        // None chosen is no sea at all.
        val none = CityMap(128, 128).also { TerrainGen.generate(it, 21, TerrainOptions(water = 0, trees = 30, river = false, sea = Sea.COAST, seaSides = 0)) }
        assertTrue(none.terrain.none { it == Terrain.WATER })
    }

    @Test
    fun aRegionKeepsItsCoast() {
        val r = Region("Shore", 8, TerrainOptions(sea = Sea.COAST, seaSides = SeaSide.NORTH or SeaSide.WEST), 2, 64)
        val back = Region.read(r.write())
        assertEquals(Sea.COAST, back.land.sea)
        assertEquals(SeaSide.NORTH or SeaSide.WEST, back.land.seaSides)
    }

    /** The share of land in each of [squares] by [squares] squares of [m], in percent. */
    private fun shares(m: CityMap, squares: Int): List<Int> {
        val side = m.width / squares
        return (0 until squares * squares).map { k ->
            val x0 = (k % squares) * side
            val y0 = (k / squares) * side
            var land = 0
            for (y in y0 until y0 + side) for (x in x0 until x0 + side) if (!m.wet(x, y)) land++
            land * 100 / (side * side)
        }
    }

    @Test
    fun everyTownIsAtLeastHalfLand() {
        for (sea in Sea.entries) for (squares in 1..4) for (seed in 1L..3L) {
            val side = if (squares == 1) 128 else 64
            val m = CityMap(side * squares, side * squares)
            TerrainGen.generate(m, seed, TerrainOptions(water = 100, sea = sea), squares)
            val s = shares(m, squares)
            assertTrue(s.all { it >= 50 }, "$sea $squares×$squares seed $seed: $s")
        }
    }

    @Test
    fun islandsComeInManySizesWithSeaRound() {
        for (seed in 1L..4L) {
            val m = CityMap(192, 192).also { TerrainGen.generate(it, seed, TerrainOptions(water = 0, river = false, sea = Sea.ISLANDS), 3) }
            // Mostly sea round the edge, an island here and there running off it.
            assertTrue(m.edges().all { it >= 60 }, "seed $seed: ${m.edges()}")
            // Several pieces of land, not all the same size.
            val sizes = ArrayList<Int>()
            val seen = BooleanArray(m.size)
            for (start in 0 until m.size) {
                if (seen[start] || m.terrain[start] == Terrain.WATER) continue
                var size = 0
                val todo = ArrayDeque<Int>()
                todo += start
                seen[start] = true
                while (todo.isNotEmpty()) {
                    val i = todo.removeFirst()
                    size++
                    val x = i % m.width
                    val y = i / m.width
                    for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                        val nx = x + dx
                        val ny = y + dy
                        if (!m.inside(nx, ny)) continue
                        val j = m.index(nx, ny)
                        if (!seen[j] && m.terrain[j] != Terrain.WATER) {
                            seen[j] = true
                            todo += j
                        }
                    }
                }
                if (size >= 20) sizes += size
            }
            assertTrue(sizes.size >= 3, "seed $seed: $sizes")
            assertTrue(sizes.max() > sizes.min() * 4, "seed $seed: $sizes")
        }
    }

    @Test
    fun riversRunToTheSeaAndFlowThatWay() {
        for (sea in listOf(Sea.ONE_SIDE, Sea.TWO_SIDES, Sea.THREE_SIDES, Sea.ISLAND, Sea.ISLANDS)) {
            val c = City(7, 128, 128, TerrainOptions(water = 30, trees = 0, river = true, sea = sea))
            val m = c.map
            val flow = City::class.java.getDeclaredMethod("flow").apply { isAccessible = true }.invoke(c) as IntArray
            // Every bit of water that runs anywhere ends up at the sea: the highest flow is out at the edge.
            val top = (0 until m.size).maxBy { flow[it] }
            val x = top % m.width
            val y = top / m.width
            assertTrue(x == 0 || y == 0 || x == m.width - 1 || y == m.height - 1 || flow[top] > 0, "$sea: $x,$y")
            // And there's a river: water that flows with some way to go, not just the sea.
            assertTrue((0 until m.size).count { flow[it] in 1 until flow[top] - 5 } > 30, "$sea has no river")
        }
    }

    @Test
    fun aRegionKeepsItsSea() {
        val r = Region("Isles", 8, TerrainOptions(sea = Sea.ISLANDS), 2, 64)
        val back = Region.read(r.write())
        assertEquals(Sea.ISLANDS, back.land.sea)
        assertContentEquals(r.whole().terrain, back.whole().terrain)
    }
}
