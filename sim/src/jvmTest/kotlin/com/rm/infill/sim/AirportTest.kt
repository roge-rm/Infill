package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AirportTest {
    /** A town along a street to the edge, with an airport of [type] east of it if there's one. */
    private fun town(type: BuildingType?, year: Int = 1980): City {
        val c = City(31, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true; it.disasterLevel = 0; it.needsApply = false }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        val m = c.map
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 63, 30, true), RoadType.STREET)).ok)
        assertTrue(c.apply(Action.PlaceZone(2, 26, 30, 29, Zone.RESIDENTIAL)).ok)
        assertTrue(c.apply(Action.PlaceZone(2, 31, 30, 33, Zone.OFFICE)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 2, 40)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 4, 41, 4, 34, false))).ok)
        if (type != null) assertTrue(c.apply(Action.PlaceBuilding(type, 40, 31)).ok, "$type")
        repeat(14) { repeat(31) { c.tick() } }
        return c
    }

    @Test
    fun anAirportBringsVisitorsAndOfficesAndPaysItsWay() {
        val with = town(BuildingType.AIRPORT)
        val without = town(null)
        assertEquals(2, with.airTier)
        assertTrue(with.stats.visitorsBy[Tourism.AIR] > 0, "visitors by air")
        assertEquals(0, without.stats.visitorsBy[Tourism.AIR])
        assertTrue(with.stats.officeJobs + with.stats.officeJobsComing + with.stats.officeDemand > without.stats.officeJobs + without.stats.officeJobsComing + without.stats.officeDemand, "with ${with.stats.officeJobs}+${with.stats.officeJobsComing}+${with.stats.officeDemand}, without ${without.stats.officeJobs}+${without.stats.officeJobsComing}+${without.stats.officeDemand}")
        assertTrue(with.stats.duesIncome > without.stats.duesIncome, "landing fees")
    }

    @Test
    fun itsNoiseTakesValueOffTheLand() {
        val c = town(BuildingType.INTERNATIONAL_AIRPORT)
        val m = c.map
        // Off the west end of the runway, and well away from it.
        val under = m.index(36, 33)
        val away = m.index(10, 50)
        assertTrue((m.noise[under].toInt() and 0xff) > 0)
        assertEquals(0, m.noise[away].toInt() and 0xff)
        val quiet = town(null).map
        assertTrue((m.landValue[under].toInt() and 0xff) < (quiet.landValue[under].toInt() and 0xff))
    }

    @Test
    fun theBiggerOnesComeLater() {
        val c = City(31, 64, 64, TerrainOptions(water = 0, trees = 0, river = false))
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, 1960)
        City::class.java.getDeclaredField("era").apply { isAccessible = true }.set(c, Era.MOTOR)
        assertTrue(c.allows(BuildingType.AIRFIELD))
        assertTrue(c.allows(BuildingType.AIRPORT))
        assertTrue(!c.allows(BuildingType.INTERNATIONAL_AIRPORT))
    }

    @Test
    fun airportsAreSavedAndOldTownsLoad() {
        val c = town(BuildingType.AIRPORT)
        val back = SaveGame.read(SaveGame.write(c))
        // The switch for what buildings need isn't saved.
        back.needsApply = false
        City::class.java.getDeclaredMethod("updateAirports").apply { isAccessible = true }.invoke(back)
        assertEquals(c.stats.airLoads, back.stats.airLoads)
        assertEquals(c.airTier, back.airTier)
        assertTrue(back.map.noise.contentEquals(c.map.noise))
        val old = SaveGame.read(javaClass.getResourceAsStream("/saves/v24.infill")!!.readBytes())
        assertEquals("Twenty-fourth", old.name)
        val people = old.stats.population
        repeat(70) { old.tick() }
        assertTrue(old.stats.population > people / 2)
        assertTrue(old.map.tunnelled(old.map.index(31, 40)))
        assertEquals(BridgeKind.TRUSS, old.map.bridgeKind(old.map.index(31, 10)))
    }
}
