package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TourismTest {
    private val add = City::class.java.getDeclaredMethod("addBuilding", BuildingType::class.java, Int::class.java, Int::class.java, Int::class.java, Int::class.java)
        .apply { isAccessible = true }

    @Test
    fun aHotelTowerTakesGuestsToo() {
        val c = City(41, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true; it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, 1970)
        // A road to the edge for visitors to come in on, and parks to draw them.
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 30, 63, 30, true), RoadType.STREET)).ok)
        assertTrue(c.apply(Action.PlaceParks(10, 32, 25, 40)).ok)
        assertTrue(c.apply(Action.PlaceZone(30, 31, 31, 32, Zone.COMMERCIAL, Density.TOWER)).ok)
        repeat(32) { c.tick() }
        // Then a hotel tower, and the visitors looking for rooms.
        val tower = add.invoke(c, BuildingType.HOTEL_TOWER, 30, 31, 0, 0) as Building
        City::class.java.getDeclaredMethod("tourism").apply { isAccessible = true }.invoke(c)
        assertTrue(c.stats.visitors > 0)
        assertEquals(BuildingType.HOTEL_TOWER.capacity * Balance.ROOMS_PER_JOB, tower.room)
        assertTrue(tower.served > 0, "guests ${tower.served} of ${c.stats.visitors} visitors")
    }
}
