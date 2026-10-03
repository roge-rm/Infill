package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WayInTest {
    /** Homes and shops along a street, out to the edge of the map or stopping short of it. */
    private fun town(toEdge: Boolean, island: Boolean = false): City {
        val c = City(5, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.disasterLevel = 0 }
        val m = c.map
        if (island) {
            for (i in 0 until m.size) if (i % m.width < 4 || i % m.width >= 60 || i / m.width < 4 || i / m.width >= 60) m.terrain[i] = Terrain.WATER
        }
        c.apply(Action.BuildRoad(Action.roadPath(m, if (toEdge) 0 else 10, 30, 50, 30, true)))
        c.apply(Action.PlaceZone(12, 27, 48, 29, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(12, 31, 30, 33, Zone.COMMERCIAL))
        repeat(12) { repeat(31) { c.tick() } }
        return c
    }

    @Test
    fun nobodyMovesInWithoutAWayIn() {
        val shut = town(toEdge = false)
        assertEquals(0, shut.stats.population)
        assertTrue(shut.advice.any { it.kind == AdviceKind.NO_WAY_IN })
        val open = town(toEdge = true)
        assertTrue(open.stats.population > 0)
        assertTrue(open.advice.none { it.kind == AdviceKind.NO_WAY_IN })
    }

    @Test
    fun anIslandWithNoLandAtTheEdgeStillGrows() {
        val c = town(toEdge = false, island = true)
        assertTrue(c.stats.population > 0)
    }
}
