package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfficeTest {
    private fun town(year: Int, offices: Boolean = true): City {
        val c = City(9, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.disasterLevel = 0; it.everything = true }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        val m = c.map
        for (k in 10..50 step 6) {
            c.apply(Action.BuildRoad(Action.roadPath(m, 0, k, 63, k, true), RoadType.STREET, pipes = true))
            c.apply(Action.BuildRoad(Action.roadPath(m, k, 0, k, 63, true), RoadType.STREET, pipes = true))
        }
        c.apply(Action.PlaceBuilding(BuildingType.PUMPING_STATION, 0, 0))
        c.apply(Action.PlaceBuilding(BuildingType.SCHOOL, 23, 23))
        c.apply(Action.PlaceZone(11, 11, 49, 27, Zone.RESIDENTIAL, Density.MEDIUM))
        c.apply(Action.PlaceZone(11, 29, 27, 39, Zone.COMMERCIAL, Density.MEDIUM))
        c.apply(Action.PlaceZone(35, 41, 49, 49, Zone.INDUSTRIAL, Density.MEDIUM))
        if (offices) c.apply(Action.PlaceZone(29, 29, 49, 39, Zone.OFFICE, Density.HIGH))
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 1, 52))
        for (k in 10..50 step 6) c.apply(Action.BuildPowerLine(Action.roadPath(m, 2, k + 1, 62, k + 1, true)))
        c.apply(Action.BuildPowerLine(Action.roadPath(m, 2, 51, 2, 11, false)))
        repeat(6 * 12) { repeat(31) { c.tick() } }
        return c
    }

    private fun City.all(): List<Building> = (0 until map.size).mapNotNull { building(map.building[it]) }.distinctBy { it.id }

    @Test
    fun officesGrowOnTheirOwnZone() {
        val c = town(1930)
        val offices = c.all().filter { it.type.zone == Zone.OFFICE }
        assertTrue(offices.isNotEmpty(), "no offices")
        assertTrue(c.stats.officeJobs > 0)
        assertTrue(c.stats.officeIncome > 0)
        // They need no stock and send no freight.
        assertTrue(offices.all { it.local == 0 && it.kind < 0 })
    }

    @Test
    fun theTownWantsMoreOfficeWorkAsTheCenturyGoesOn() {
        val early = town(1905)
        val late = town(1965)
        val share = { c: City -> c.stats.officeJobs * 1000 / maxOf(1, c.stats.population) }
        assertTrue(share(late) > share(early), "1905 ${share(early)}, 1965 ${share(late)} per thousand")
    }

    @Test
    fun officesSurviveASave() {
        val c = town(1930)
        // Allowing everything isn't saved, and the town has offices before their era, so the copy needs it too.
        val loaded = SaveGame.read(SaveGame.write(c)).also { it.disasterLevel = 0; it.everything = true }
        assertEquals(c.stats.officeJobs, loaded.stats.officeJobs)
        repeat(100) { c.tick(); loaded.tick() }
        assertEquals(c.map.hash(), loaded.map.hash())
    }

    @Test
    fun officesComeWithTheStreetcar() {
        val c = City(9, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.disasterLevel = 0 }
        val m = c.map
        c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 63, 30, true)))
        c.apply(Action.PlaceZone(2, 27, 60, 29, Zone.RESIDENTIAL))
        repeat(3) { repeat(31) { c.tick() } }
        // A township has no offices to zone, wants none and isn't told it does.
        assertFalse(c.allowsZone(Zone.OFFICE))
        assertFalse(c.apply(Action.PlaceZone(2, 31, 20, 33, Zone.OFFICE)).ok)
        assertEquals(0, c.stats.officeDemand)
        assertTrue(c.advice.none { it.zone == Zone.OFFICE })
        // With the streetcar they come in.
        City::class.java.getDeclaredField("era").apply { isAccessible = true }.set(c, Era.STREETCAR)
        assertTrue(c.allowsZone(Zone.OFFICE))
        repeat(8) { c.tick() }
        assertTrue(c.stats.officeDemand > 0, "offices wanted once they're in")
    }
}
