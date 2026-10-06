package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IslandTest {
    @Test
    fun aPortJoinsAnIslandToTheOutside() {
        val c = City(9, 64, 64, TerrainOptions(water = 0, trees = 0, river = false, sea = Sea.ISLAND))
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        assertTrue(c.island)
        assertTrue(c.hasWayIn())
        assertTrue(!c.tradesOut)
        val m = c.map
        // A wharf on the shore, then a street from it into the island's middle, with works beside it.
        val spot = (0 until m.size).firstNotNullOfOrNull { i ->
            val x = i % m.width
            val y = i / m.width
            listOf(BuildingType.WHARF, BuildingType.WHARF_NS).firstOrNull { t -> c.plan(Action.PlaceBuilding(t, x, y)).ok }?.let { Triple(it, x, y) }
        }
        assertNotNull(spot)
        val (t, x, y) = spot
        val road = Action.roadPath(m, x - 1, y, 32, 32, false)
        c.apply(Action.BuildRoad(road, RoadType.STREET))
        c.apply(Action.PlaceZone(30, 33, 34, 36, Zone.INDUSTRIAL))
        assertTrue(c.needsPort())
        assertTrue(c.apply(Action.PlaceBuilding(t, x, y)).ok)
        repeat(40) { c.tick() }
        assertTrue(c.tradesOut, "port ${c.allBuildings.filter { it.type.port }.map { "${it.type} ${it.x},${it.y} ${c.working(it)}" }}")
        assertTrue(!c.needsPort())
    }
}
