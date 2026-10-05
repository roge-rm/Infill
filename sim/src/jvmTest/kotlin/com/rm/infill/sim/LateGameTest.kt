package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LateGameTest {
    private fun town(year: Int, terrain: TerrainOptions = TerrainOptions(water = 0, trees = 0, river = false)): City {
        val c = City(7, 64, 64, terrain)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        return c
    }

    @Test
    fun officeWorkGoesHomeFrom2020() {
        assertEquals(0, Remote.share(2019))
        assertEquals(30, Remote.share(2020))
        assertEquals(20, Remote.share(2040))
        val c = town(2025)
        c.era = Era.INFILL
        c.stats.population = 20_000
        val offices = c.demandParts().first { it.zone == Zone.OFFICE }
        assertTrue(offices.parts.any { it.source == DemandSource.WORKING_FROM_HOME && it.amount < 0 })
    }

    @Test
    fun anOfficeIsConvertedToHomesOfTheSameSize() {
        val c = town(2025)
        val add = City::class.java.getDeclaredMethod("addBuilding", BuildingType::class.java, Int::class.java, Int::class.java, Int::class.java, Int::class.java)
            .apply { isAccessible = true }
        for (y in 10..11) for (x in 10..11) c.map.zone[c.map.index(x, y)] = Zone.OFFICE
        add.invoke(c, BuildingType.OFFICE_COURT, 10, 10, 0, 0)
        assertEquals(BuildingType.COURT_TENEMENTS, c.homeFor(BuildingType.OFFICE_COURT))
        val before = c.map.hash()
        val plan = c.apply(Action.ConvertToHomes(10, 10, 10, 10))
        assertTrue(plan.ok, "${plan.problem}")
        val home = assertNotNull(c.buildingAt(10, 10))
        assertEquals(BuildingType.COURT_TENEMENTS, home.type)
        assertTrue(home.underway > 0)
        assertEquals(Zone.RESIDENTIAL, c.map.zone[c.map.index(11, 11)])
        c.undo()
        assertEquals(before, c.map.hash())
        assertEquals(BuildingType.OFFICE_COURT, c.buildingAt(10, 10)?.type)
        // Not before the 1970s.
        assertEquals(Problem.NothingToDo, town(1950).apply { for (y in 10..11) for (x in 10..11) map.zone[map.index(x, y)] = Zone.OFFICE; add.invoke(this, BuildingType.OFFICE_COURT, 10, 10, 0, 0) }
            .plan(Action.ConvertToHomes(10, 10, 10, 10)).problem)
    }

    @Test
    fun theRisenSeaFloodsTheShoreUnlessItsBanked() {
        val coast = TerrainOptions(water = 0, trees = 0, river = false, sea = Sea.ONE_SIDE)
        val tides = City::class.java.getDeclaredMethod("tides").apply { isAccessible = true }
        assertEquals(0, town(2025, coast).tideReach())
        val c = town(2080, coast)
        assertTrue(c.tideReach() >= 2, "${c.seaRise()} cm")
        val m = c.map
        // The land tiles beside the sea, every other one banked.
        val shore = (0 until m.size).filter { i ->
            m.terrain[i] != Terrain.WATER && listOf(-1, 1, -m.width, m.width).any { d -> (i + d) in 0 until m.size && m.terrain[i + d] == Terrain.WATER }
        }
        assertTrue(shore.isNotEmpty())
        val banked = shore.filterIndexed { k, _ -> k % 2 == 0 }.toSet()
        for (i in banked) m.bank[i] = 1
        tides.invoke(c)
        assertTrue(shore.filter { it !in banked }.any { (m.flood[it].toInt() and 0xff) >= Balance.FLOODED })
        assertTrue(banked.all { (m.flood[it].toInt() and 0xff) == 0 })
    }

    @Test
    fun legacyGoalsAreMetOnceAndKept() {
        val c = town(2035)
        c.era = Era.FUTURE
        c.stats.population = Balance.LEGACY_PEOPLE
        val month = City::class.java.getDeclaredMethod("legacyMonth").apply { isAccessible = true }
        c.takeEvents { }
        month.invoke(c)
        val told = ArrayList<CityEvent>()
        c.takeEvents { told += it }
        assertEquals(1, c.legacyMet and 1)
        assertTrue(told.any { it.kind == EventKind.LegacyMet && it.count == 0 })
        c.stats.population = 10
        month.invoke(c)
        assertEquals(1, c.legacyMet and 1)
        assertEquals(1, SaveGame.read(SaveGame.write(c)).legacyMet and 1)
    }
}
