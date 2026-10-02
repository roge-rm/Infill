package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NeedsTest {
    /** A street at y 30 in [year], a hospital on it, and as many of power, mains and the phone as asked for. */
    private fun town(year: Int, power: Boolean = false, water: Boolean = false, phone: Boolean = false, fibre: Boolean = false): City {
        val c = City(37, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true; it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        val m = c.map
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 60, 30, true), RoadType.STREET, pipes = water)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.HOSPITAL, 20, 31)).ok)
        if (power) {
            assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 50, 40)).ok)
            assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 49, 41, 23, 41, true))).ok)
            assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 23, 40, 23, 32, false))).ok)
        }
        if (water) {
            for (y in 0 until 64) m.terrain[m.index(5, y)] = Terrain.WATER
            assertTrue(c.apply(Action.PlaceBuilding(BuildingType.PUMPING_STATION, 6, 26)).ok)
            assertTrue(c.apply(Action.BuildPipe(Action.roadPath(m, 8, 27, 8, 29, false), Pipe.WATER)).ok)
            assertTrue(c.apply(Action.PlaceBuilding(BuildingType.OUTFALL, 6, 32)).ok)
            assertTrue(c.apply(Action.BuildPipe(Action.roadPath(m, 7, 32, 8, 31, true), Pipe.SEWER)).ok)
        }
        if (phone) {
            assertTrue(c.apply(Action.PlaceBuilding(BuildingType.EXCHANGE, 20, 28)).ok)
            assertTrue(c.apply(Action.BuildPhoneLine(Action.roadPath(m, 22, 28, 22, 0, false), fibre = fibre)).ok)
        }
        repeat(2) { repeat(31) { c.tick() } }
        return c
    }

    @Test
    fun aHospitalWithoutWhatItNeedsDoesLess() {
        val bare = town(1950)
        val b = bare.buildingAt(20, 31)!!
        assertEquals(setOf(Need.POWER, Need.WATER, Need.PHONE), bare.unmet(b).map { it.first }.toSet())
        assertEquals(Needs.LEAST, bare.fit(b))
        val served = town(1950, power = true, water = true, phone = true)
        val h = served.buildingAt(20, 31)!!
        assertTrue(served.unmet(h).isEmpty(), "${served.unmet(h)}")
        assertEquals(100, served.fit(h))
        assertTrue(served.condition(h) > bare.condition(b))
    }

    @Test
    fun needsComeInWithTheYears() {
        // Before 1910 a hospital needs nothing.
        val early = town(1905)
        assertTrue(early.unmet(early.buildingAt(20, 31)!!).isEmpty())
        // Broadband from 2000.
        val late = town(2005, power = true, water = true, phone = true)
        assertEquals(listOf(Need.BROADBAND), late.unmet(late.buildingAt(20, 31)!!).map { it.first })
    }

    @Test
    fun anOldBuildingHasToBeRenovatedForANewNeed() {
        val c = town(2005, power = true, water = true, phone = true, fibre = true)
        val b = c.buildingAt(20, 31)!!
        // Built new, it's fitted for broadband.
        assertTrue(c.unmet(b).isEmpty(), "${c.unmet(b)}")
        // Built in 1960, before broadband: the line's there but it isn't fitted.
        b.built = Ageing.monthOf(1960, 0)
        assertEquals(listOf(Need.BROADBAND to true), c.unmet(b))
        assertTrue(c.fit(b) < 100)
        assertTrue(c.renovatable(b))
        assertTrue(c.apply(Action.RenewArea(b.x, b.y, b.x, b.y)).ok)
        assertTrue(c.unmet(b).isEmpty())
        assertFalse(c.renovatable(b))
    }

    @Test
    fun aPortWithoutPowerStopsWorking() {
        val c = City(38, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true; it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, 1975)
        val m = c.map
        for (y in 0 until 64) for (x in 40..42) m.terrain[m.index(x, y)] = Terrain.WATER
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 44, 20, 63, 20, true), RoadType.STREET)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.CONTAINER_PORT_NS, 43, 22)).ok)
        repeat(40) { c.tick() }
        val port = c.buildingAt(43, 22)!!
        assertFalse(c.working(port))
        assertEquals(0, c.seaTier)
        // Shipping in a town that doesn't count what it needs.
        c.needsApply = false
        repeat(40) { c.tick() }
        assertEquals(3, c.seaTier)
    }
}
