package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EraTest {
    private fun city(): City {
        val c = City(4, 48, 48, TerrainOptions(water = 0, trees = 0, river = false))
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        return c
    }

    private fun City.setYear(y: Int) = City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(this, y)

    private fun City.newEra() = City::class.java.getDeclaredMethod("newEra").apply { isAccessible = true }.invoke(this)

    private fun City.eraEvents(): List<Era> {
        val out = ArrayList<Era>()
        takeEvents { if (it.kind == EventKind.EraArrived) out += it.era!! }
        return out
    }

    @Test
    fun aNewTownIsATownshipAndCantBuildAvenues() {
        val c = city()
        assertEquals(Era.TOWNSHIP, c.era)
        assertFalse(c.plan(Action.BuildRoad(Action.roadPath(c.map, 2, 10, 20, 10, true), RoadType.AVENUE)).ok)
        assertTrue(c.plan(Action.BuildRoad(Action.roadPath(c.map, 2, 10, 20, 10, true), RoadType.STREET)).ok)
        assertFalse(c.plan(Action.PlaceBuilding(BuildingType.HIGH_SCHOOL, 5, 5)).ok)
    }

    @Test
    fun theStreetcarCityNeedsTheYearAndTheMilestone() {
        val c = city()
        c.stats.population = 2_000
        c.stats.onMains = 30
        c.setYear(1909)
        c.newEra()
        assertEquals(Era.TOWNSHIP, c.era, "too early")
        c.setYear(1912)
        c.stats.onMains = 0
        c.newEra()
        assertEquals(Era.TOWNSHIP, c.era, "no mains and no station")
        c.stats.onMains = 30
        c.newEra()
        assertEquals(Era.STREETCAR, c.era)
        assertEquals(listOf(Era.STREETCAR), c.eraEvents())
        assertTrue(c.plan(Action.BuildRoad(Action.roadPath(c.map, 2, 10, 20, 10, true), RoadType.AVENUE)).ok)
        assertTrue(c.plan(Action.PlaceBuilding(BuildingType.HIGH_SCHOOL, 5, 5)).ok)
    }

    @Test
    fun aSmallTownStaysBehind() {
        val c = city()
        c.stats.population = 900
        c.stats.onMains = 50
        c.setYear(1935)
        c.newEra()
        assertEquals(Era.TOWNSHIP, c.era)
        val goal = c.goals(Era.STREETCAR).first { it.kind == GoalKind.People }
        assertEquals(900, goal.have)
        assertFalse(goal.met)
    }

    @Test
    fun oneEraAtATime() {
        val c = city()
        c.setYear(1975)
        c.stats.population = 30_000
        c.stats.onMains = 90; c.stats.onSewer = 90; c.stats.powered = 90; c.stats.downtown = 3; c.stats.highSchools = 1
        c.newEra()
        assertEquals(Era.STREETCAR, c.era)
        c.newEra()
        c.newEra()
        assertEquals(Era.RENEWAL, c.era)
        c.newEra()
        assertEquals(Era.RENEWAL, c.era, "not 2000 yet")
    }

    @Test
    fun theFutureNeedsATownKeptUpThatMovesPeopleWell() {
        val c = city()
        c.era = Era.INFILL
        c.setYear(2031)
        c.stats.keptUp = 95
        c.stats.greenTrips = 10
        c.newEra()
        assertEquals(Era.INFILL, c.era, "too many cars")
        c.stats.greenTrips = 40
        c.stats.keptUp = 70
        c.newEra()
        assertEquals(Era.INFILL, c.era, "too much worn out")
        c.stats.keptUp = 95
        c.newEra()
        assertEquals(Era.FUTURE, c.era)
    }

    @Test
    fun homesExpectMoreAsTheYearsGoBy() {
        val c = city()
        val amenity = City::class.java.getDeclaredMethod("amenity", Boolean::class.java, Int::class.java, Int::class.java, Int::class.java, Int::class.java).apply { isAccessible = true }
        fun mains(have: Boolean) = amenity.invoke(c, have, Balance.MAINS_APPEAL, Balance.MAINS_FADES, Balance.MAINS_EXPECTED_BY, Balance.MAINS_EXPECTED) as Int
        c.setYear(1905)
        assertEquals(Balance.MAINS_APPEAL, mains(true), "mains water is a draw in 1905")
        assertEquals(0, mains(false), "and no one minds a well")
        c.setYear(1950)
        assertEquals(0, mains(true), "expected by 1950")
        assertEquals(-Balance.MAINS_EXPECTED, mains(false), "and a well counts against a home")
    }

    @Test
    fun theOutsideWorldHasItsUpsAndDowns() {
        assertEquals(100, Economy.market(1900))
        assertTrue(Economy.market(1933) < Economy.market(1928), "the slump")
        assertTrue(Economy.market(1955) > Economy.market(1935), "the post-war boom")
        assertTrue(Economy.market(1985) < Economy.market(1965), "factories closing")
    }

    @Test
    fun theEraSurvivesASaveAndOldSavesEarnTheirs() {
        val c = city()
        c.era = Era.MOTOR
        assertEquals(Era.MOTOR, SaveGame.read(SaveGame.write(c)).era)
        val old = SaveGame.read(javaClass.getResourceAsStream("/saves/v6.infill")!!.readBytes())
        assertEquals("Sixways", old.name)
        assertEquals(Era.TOWNSHIP, old.era)
    }
}
