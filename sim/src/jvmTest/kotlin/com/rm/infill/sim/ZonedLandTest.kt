package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZonedLandTest {
    @Test
    fun aServiceGoesOnZonedLandWhileNothingsBuiltThere() {
        val c = City(3, 32, 32, TerrainOptions(water = 0, trees = 0, river = false))
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        c.everything = true
        c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 10, 31, 10, true), RoadType.STREET))
        assertTrue(c.apply(Action.PlaceZone(4, 11, 12, 16, Zone.RESIDENTIAL)).ok)
        val t = BuildingType.SCHOOL
        assertTrue(c.apply(Action.PlaceBuilding(t, 5, 11)).ok, "zoned, but empty")
        for (y in 11 until 11 + t.height) for (x in 5 until 5 + t.width) assertEquals(Zone.NONE, c.map.zone[c.map.index(x, y)], "the zone goes with it")
        // Undo puts the zone back.
        c.undo()
        assertEquals(Zone.RESIDENTIAL, c.map.zone[c.map.index(5, 11)])
        // A lot with a building on it still stops it.
        c.apply(Action.PlaceBuilding(BuildingType.HOUSE, 9, 12))
        assertEquals(Problem.Blocked, c.plan(Action.PlaceBuilding(t, 8, 11)).problem)
    }
}
