package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JusticeTest {
    private fun at(layer: ByteArray, i: Int) = layer[i].toInt() and 0xff

    /** A bare map with a crowd of people on every tile and cheap land. */
    private fun crowd(police: Int): CityMap {
        val m = CityMap(16, 16)
        m.landValue.fill(20)
        m.policeCover.fill(police.toByte())
        return m
    }

    @Test
    fun theftAndViceAddUpToCrimeAndPoliceCutTheftTheMore() {
        val m = crowd(0)
        Effects.crime(m, { 20 }, { 0 }, { true }, unemployment = 10, justice = 100)
        val i = m.index(8, 8)
        val theft = at(m.theft, i)
        val vice = at(m.vice, i)
        assertTrue(theft > 0 && vice > 0)
        assertEquals(theft + vice, at(m.crime, i))

        val policed = crowd(255)
        Effects.crime(policed, { 20 }, { 0 }, { true }, unemployment = 10, justice = 100)
        // Each by a share: theft the bigger one.
        val theftKept = at(policed.theft, i) * 100 / theft
        val viceKept = at(policed.vice, i) * 100 / vice
        assertTrue(theftKept < viceKept && viceKept < 100, "theft kept $theftKept%, vice kept $viceKept%")
    }

    @Test
    fun crimeGrowsWhereArrestsDontStick() {
        val fair = crowd(0)
        Effects.crime(fair, { 20 }, { 0 }, { true }, unemployment = 10, justice = 100)
        val lawless = crowd(0)
        Effects.crime(lawless, { 20 }, { 0 }, { true }, unemployment = 10, justice = 0)
        val i = fair.index(8, 8)
        val expected = at(fair.crime, i) * (100 + Balance.JUSTICE_SLACK) / 100
        assertTrue(kotlin.math.abs(expected - at(lawless.crime, i)) <= 2, "expected $expected, got ${at(lawless.crime, i)}")
    }

    @Test
    fun racketsFeedOnWhatGoesUnpunishedAndDetectivesWearThemDown() {
        val m = crowd(0)
        m.theft.fill(120)
        m.vice.fill(120)
        repeat(6) { Effects.rackets(m, justice = 0, detectives = 0) }
        val grown = at(m.rackets, m.index(8, 8))
        assertTrue(grown > 50, "rackets $grown")
        // Then the town gets its courts and detectives, and the street calms down.
        m.theft.fill(0)
        m.vice.fill(0)
        repeat(12) { Effects.rackets(m, justice = 100, detectives = Balance.DETECTIVES) }
        assertTrue(at(m.rackets, m.index(8, 8)) < grown / 3)
    }

    @Test
    fun racketsReachTheNextStreet() {
        val m = crowd(0)
        m.theft[m.index(8, 8)] = 200.toByte()
        m.vice[m.index(8, 8)] = 200.toByte()
        repeat(3) { Effects.rackets(m, justice = 0, detectives = 0) }
        assertTrue(at(m.rackets, m.index(9, 8)) > 0)
        assertEquals(0, at(m.rackets, m.index(0, 0)))
    }

    /** A poor, crowded town with one police station, in [year]. */
    private fun town(year: Int, jail: Boolean = false, court: Boolean = false, hq: Boolean = false): City {
        val c = City(11, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        val m = c.map
        for (y in listOf(10, 20, 30, 40, 50)) assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 2, y, 60, y, true))).ok)
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 31, 10, 31, 50, true))).ok)
        // A railway out, for newcomers, which leaves the streets as they were.
        assertTrue(c.apply(Action.BuildRail(Action.roadPath(m, 40, 4, 63, 4, true))).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.STATION, 50, 5)).ok)
        for (y in listOf(10, 20, 30, 40)) {
            assertTrue(c.apply(Action.PlaceZone(2, y + 1, 60, y + 3, Zone.RESIDENTIAL, Density.HIGH)).ok)
            assertTrue(c.apply(Action.PlaceZone(2, y + 7, 60, y + 9, Zone.INDUSTRIAL)).ok)
        }
        assertTrue(c.apply(Action.PlaceZone(2, 51, 60, 53, Zone.COMMERCIAL)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 2, 56)).ok)
        // Up the west edge from the station, and along between each row of homes and works.
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 1, 57, 1, 14, false))).ok)
        for (y in listOf(14, 24, 34, 44)) assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 1, y, 60, y, true))).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.POLICE_STATION, 32, 15)).ok)
        if (jail) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.JAIL, 52, 56)).ok)
        if (court) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COURTHOUSE, 47, 56)).ok)
        if (hq) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.POLICE_HQ, 32, 25)).ok)
        return c
    }

    private fun City.run(years: Int) = repeat(years * 365) { tick() }

    @Test
    fun theCourtsHearWhatTheyCanAndTheCellsHoldWhatTheyCan() {
        // Room for all: every arrest sticks.
        assertEquals(100, Justice.month(arrests = 20, canHear = 50, room = 500, prisoners = 0).justice)
        // The courts hear half: half stick.
        val busy = Justice.month(arrests = 40, canHear = 20, room = 500, prisoners = 0)
        assertEquals(20, busy.heard)
        assertEquals(50, busy.justice)
        // Full cells: the newly guilty go free, and next to nothing sticks.
        val full = Justice.month(arrests = 20, canHear = 50, room = 10, prisoners = 10)
        assertEquals(10, full.prisoners)
        assertTrue(full.justice < 20, "justice ${full.justice}")
        // Sentences end: with nobody new, the cells empty a sixth a month.
        assertEquals(60 - 60 / Balance.SENTENCE, Justice.month(0, 0, 100, 60).prisoners)
    }

    @Test
    fun thePoliceMakeArrestsAndTheLockupHearsThem() {
        val c = town(1925)
        var arrests = 0
        repeat(8 * 12) {
            repeat(30) { c.tick() }
            arrests += c.stats.arrests
        }
        val s = c.stats
        assertTrue(s.offences > 0)
        assertTrue(arrests > 0, "no arrests: ${s.offences} offences, ${s.population} people, crime ${(0 until c.map.size).maxOf { c.map.crime[it].toInt() and 0xff }} at most")
        assertTrue(s.cells > 0)
        assertEquals(100, c.justice)
    }

    @Test
    fun racketsComeOnlyAfterTheWar() {
        val early = town(1905).also { it.run(3) }
        assertEquals(0, early.stats.rackets)
    }

    @Test
    fun justiceIsSaved() {
        val c = town(1925).also { it.run(2) }
        val back = SaveGame.read(SaveGame.write(c))
        assertEquals(c.justice, back.justice)
        assertEquals(c.prisoners, back.prisoners)
        assertEquals(c.stats.arrests, back.stats.arrests)
        assertTrue(c.map.theft.contentEquals(back.map.theft))
        assertTrue(c.map.rackets.contentEquals(back.map.rackets))
    }


}
