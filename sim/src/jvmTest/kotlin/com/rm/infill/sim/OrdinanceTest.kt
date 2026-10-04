package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrdinanceTest {
    private fun town(year: Int, era: Era = Era.of(year)): City {
        val c = City(8, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.disasterLevel = 0 }
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        City::class.java.getDeclaredField("era").apply { isAccessible = true }.set(c, era)
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        val m = c.map
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 63, 30, true), RoadType.STREET, pipes = true)).ok)
        assertTrue(c.apply(Action.PlaceZone(4, 27, 60, 29, Zone.RESIDENTIAL, Density.MEDIUM)).ok)
        assertTrue(c.apply(Action.PlaceZone(4, 31, 30, 33, Zone.COMMERCIAL, Density.MEDIUM)).ok)
        assertTrue(c.apply(Action.PlaceZone(32, 31, 60, 33, Zone.INDUSTRIAL, Density.MEDIUM)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 2, 10)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 3, 12, 3, 30, false))).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 3, 30, 63, 30, true))).ok)
        return c
    }

    private fun City.months(n: Int) = repeat(n) { repeat(31) { tick() } }

    @Test
    fun aLawCostsByThePeopleAndSomeBringMoneyIn() {
        val c = town(1950)
        c.months(12)
        assertTrue(c.stats.population > 0 && c.stats.shopJobs > 0)
        c.setOrdinance(Ordinance.PUBLIC_HEALTH_ACT, true)
        c.setOrdinance(Ordinance.LIQUOR_LICENCES, true)
        c.months(1)
        val s = c.stats
        assertEquals(Ordinance.PUBLIC_HEALTH_ACT.cost(s.population) + Ordinance.LIQUOR_LICENCES.cost(s.population), c.ordinanceCost())
        assertTrue(s.ordinanceCost > 0, "charged: ${s.ordinanceCost}")
        assertTrue(s.ordinanceIncome > 0, "licence fees: ${s.ordinanceIncome}")
        assertTrue(s.upkeep >= s.ordinanceCost)
    }

    @Test
    fun aLawWaitsForItsYearAndItsEra() {
        val c = town(1940)
        c.setOrdinance(Ordinance.CLEAN_AIR_ACT, true)
        assertFalse(c.passed(Ordinance.CLEAN_AIR_ACT))
        // The year has come, but not the era.
        val early = town(1972, Era.MOTOR)
        early.setOrdinance(Ordinance.CLEAN_AIR_ACT, true)
        assertFalse(early.passed(Ordinance.CLEAN_AIR_ACT))
        val ready = town(1972, Era.RENEWAL)
        ready.setOrdinance(Ordinance.CLEAN_AIR_ACT, true)
        assertTrue(ready.has(Ordinance.CLEAN_AIR_ACT))
    }

    @Test
    fun prohibitionIsRepealedAndTheLicencesComeBack() {
        val c = town(1932, Era.STREETCAR)
        c.setOrdinance(Ordinance.LIQUOR_LICENCES, true)
        c.setOrdinance(Ordinance.PROHIBITION, true)
        assertTrue(c.has(Ordinance.PROHIBITION))
        // Licences lapse while it's in force.
        assertFalse(c.has(Ordinance.LIQUOR_LICENCES))
        val events = ArrayList<CityEvent>()
        repeat(30) {
            c.months(1)
            c.takeEvents { events += it }
        }
        assertTrue(c.year >= 1934)
        assertFalse(c.passed(Ordinance.PROHIBITION))
        assertTrue(events.any { it.kind == EventKind.OrdinanceEnded && it.count == Ordinance.PROHIBITION.ordinal })
        assertTrue(c.has(Ordinance.LIQUOR_LICENCES))
        // And it can't be passed again.
        c.setOrdinance(Ordinance.PROHIBITION, true)
        assertFalse(c.passed(Ordinance.PROHIBITION))
    }

    @Test
    fun prohibitionCutsViceAndFeedsTheRackets() {
        fun crime(dry: Boolean): Pair<Int, Int> {
            val c = town(1925, Era.STREETCAR)
            c.setOrdinance(Ordinance.PROHIBITION, dry)
            c.months(30)
            val vice = (4..60).sumOf { c.map.vice[c.map.index(it, 28)].toInt() and 0xff }
            val rackets = (0 until c.map.size).sumOf { c.map.rackets[it].toInt() and 0xff }
            return vice to rackets
        }
        val (wetVice, wetRackets) = crime(false)
        val (dryVice, dryRackets) = crime(true)
        assertTrue(dryVice < wetVice, "vice: dry $dryVice, wet $wetVice")
        assertTrue(dryRackets > wetRackets, "rackets: dry $dryRackets, wet $wetRackets")
    }

    @Test
    fun theLawsOnHealthMakePeopleHealthier() {
        fun health(laws: Boolean): Int {
            val c = town(1955)
            if (laws) for (o in listOf(Ordinance.PUBLIC_HEALTH_ACT, Ordinance.FLUORIDATION, Ordinance.SCHOOL_MEALS)) c.setOrdinance(o, true)
            c.months(36)
            return c.stats.health
        }
        assertTrue(health(true) > health(false))
    }

    @Test
    fun cleanAirCutsTheSmoke() {
        fun smoke(law: Boolean): Int {
            val c = town(1975)
            c.setOrdinance(Ordinance.CLEAN_AIR_ACT, law)
            c.months(6)
            return (0..10).sumOf { c.map.pollution[c.map.index(3, 8 + it)].toInt() and 0xff }
        }
        assertTrue(smoke(true) < smoke(false))
    }

    @Test
    fun daylightSavingAndAnEnergyCodeLowerThePeak() {
        fun demand(laws: Boolean): Long {
            val c = town(1985)
            if (laws) {
                c.setOrdinance(Ordinance.DAYLIGHT_SAVING, true)
                c.setOrdinance(Ordinance.ENERGY_CODE, true)
            }
            c.months(12)
            return c.stats.powerDemand
        }
        assertTrue(demand(true) < demand(false))
    }

    @Test
    fun aBottleDepositCutsTheGarbage() {
        fun waste(law: Boolean): Int {
            val c = town(1975)
            c.setOrdinance(Ordinance.BOTTLE_DEPOSIT, law)
            c.months(12)
            return c.stats.waste * 1000 / maxOf(1, c.stats.population)
        }
        assertTrue(waste(true) < waste(false))
    }

    @Test
    fun theLawsInForceAreSaved() {
        val c = town(1960)
        c.setOrdinance(Ordinance.FLUORIDATION, true)
        c.setOrdinance(Ordinance.SPEED_LIMITS, true)
        c.months(2)
        val loaded = SaveGame.read(SaveGame.write(c))
        assertEquals(Ordinance.entries.filter { c.passed(it) }, Ordinance.entries.filter { loaded.passed(it) })
        assertEquals(c.stats.ordinanceCost, loaded.stats.ordinanceCost)
    }
}
