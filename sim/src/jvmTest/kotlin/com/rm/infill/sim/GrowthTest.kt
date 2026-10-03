package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime

class GrowthTest {
    /**
     * A town on open grass: a main street from the west edge, homes to its north,
     * shops and industry to its south, and a power station wired along both sides.
     */
    private fun town(seed: Long = 7, power: Boolean = true, connected: Boolean = true): City {
        val c = City(seed, 64, 64, TerrainOptions(water = 0, trees = 0, river = false))
        val m = c.map
        fun road(x0: Int, y0: Int, x1: Int, y1: Int) = assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, x0, y0, x1, y1, true))).ok)
        road(if (connected) 0 else 2, 30, 60, 30)
        assertTrue(c.apply(Action.PlaceZone(5, 28, 40, 29, Zone.RESIDENTIAL)).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 31, 18, 32, Zone.COMMERCIAL)).ok)
        assertTrue(c.apply(Action.PlaceZone(24, 31, 40, 32, Zone.INDUSTRIAL)).ok)
        if (power) {
            assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 50, 25)).ok)
            assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 51, 27, 5, 27, true))).ok)
            assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 51, 27, 51, 33, true))).ok)
            assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 50, 33, 5, 33, true))).ok)
        }
        return c
    }

    private fun City.run(years: Int) = repeat(years * 365) { tick() }

    private fun City.all(): List<Building> =
        (0 until map.size).mapNotNull { building(map.building[it]) }.distinctBy { it.id }

    @Test
    fun theTownGrows() {
        val c = town()
        c.run(10)
        val s = c.stats
        assertTrue(s.population > 150, "population ${s.population}")
        assertTrue(s.shopJobs > 0, "shop jobs ${s.shopJobs}")
        assertTrue(s.industryJobs > 30, "industry jobs ${s.industryJobs}")
        assertTrue(c.all().any { it.type.stage >= 2 }, "nothing grew past its first stage")
        for (b in c.all()) {
            if (b.type.needsPower) assertTrue(c.map.powered[c.map.index(b.x, b.y)], "${b.type} at ${b.x},${b.y} has no power")
        }
    }

    @Test
    fun withoutPowerNothingGetsPastStageOne() {
        val c = town(power = false)
        c.run(5)
        assertTrue(c.stats.population > 0)
        assertTrue(c.all().all { it.type.stage == 1 })
    }

    @Test
    fun aRoadToTheEdgeBringsMoreIndustry() {
        val open = town(connected = true).also { it.run(6) }
        val shut = town(connected = false).also { it.run(6) }
        assertTrue(open.stats.industryJobs > shut.stats.industryJobs, "${open.stats.industryJobs} vs ${shut.stats.industryJobs}")
    }

    @Test
    fun sameSeedSameTown() {
        val a = town(3).also { it.run(8) }
        val b = town(3).also { it.run(8) }
        assertEquals(a.map.hash(), b.map.hash())
        assertEquals(a.funds, b.funds)
        assertEquals(a.stats.population, b.stats.population)
    }

    @Test
    fun theCalendarKeepsLeapYears() {
        val c = City(1, 16, 16, TerrainOptions(water = 0, trees = 0, river = false))
        // 1900 isn't a leap year; 1904 is.
        repeat(365 * 4) { c.tick() }
        assertEquals(1904, c.year)
        assertEquals(0, c.month)
        assertEquals(1, c.day)
        repeat(31 + 28) { c.tick() }
        assertEquals(1, c.month)
        assertEquals(29, c.day)
    }

    @Test
    fun grimeBuildsUpAndWashesAway() {
        val c = town()
        c.run(12)
        val near = c.map.index(32, 31)
        val dirty = c.map.grime[near].toInt() and 0xff
        assertTrue(dirty > 60, "grime by the works is only $dirty")
        assertTrue(c.apply(Action.Bulldoze(24, 31, 40, 32)).ok)
        c.run(2)
        val after2 = c.map.grime[near].toInt() and 0xff
        c.run(20)
        val after22 = c.map.grime[near].toInt() and 0xff
        assertTrue(after2 < dirty && after22 < after2, "grime went $dirty, $after2, $after22")
        assertTrue(after2 > dirty / 2, "grime shouldn't vanish in two years: $dirty to $after2")
    }

    @Test
    fun undoWontGoWhereTheTownHasBuilt() {
        val c = town(power = false)
        c.apply(Action.PlaceZone(5, 34, 20, 36, Zone.RESIDENTIAL))
        // Growth within the month, before the history clears.
        repeat(25) { c.tick() }
        if (c.all().any { it.y in 34..36 }) {
            assertEquals(Problem.TownBuiltThere, c.undo()!!.problem)
            assertTrue(!c.canUndo)
        }
    }

    @Test
    fun zonedLandCarriesPower() {
        val c = City(1, 32, 32, TerrainOptions(water = 0, trees = 0, river = false))
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 20, 2))
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 19, 3, 5, 3, true)))
        c.apply(Action.PlaceZone(5, 4, 15, 8, Zone.RESIDENTIAL))
        c.tick()
        assertTrue(c.map.powered[c.map.index(10, 8)], "the far side of the zone")
        assertTrue(!c.map.powered[c.map.index(10, 12)], "past the zone")
    }

    @Test
    fun theDrawnTypesMatchTheBuildings() {
        val c = town().also { it.run(6) }
        c.apply(Action.Bulldoze(10, 28, 14, 32))
        c.undo()
        for (i in 0 until c.map.size) {
            val b = c.building(c.map.building[i])
            assertEquals(if (b == null) 0 else b.type.ordinal + 1, (c.map.buildingType[i].toInt() and 0xff), "tile $i")
        }
    }

    /** Not a check: prints the town year by year, for tuning Balance. */
    @Test
    fun yearByYear() {
        val c = town()
        println("year  people  workers  shops  industry  demand R/C/I      income  upkeep  funds   crime  value  jobless  commute  busiest")
        repeat(25) {
            c.run(1)
            val s = c.stats
            println(
                "${c.year}  ${s.population.toString().padStart(6)}  ${s.workers.toString().padStart(7)}  " +
                    "${s.shopJobs.toString().padStart(5)}  ${s.industryJobs.toString().padStart(8)}  " +
                    "${s.residentialDemand}/${s.commercialDemand}/${s.industryDemand}".padEnd(16) +
                    "  ${s.income.toString().padStart(6)}  ${s.upkeep.toString().padStart(6)}  ${c.funds.toString().padStart(6)}" +
                    "  ${s.crime.toString().padStart(5)}  ${s.landValue.toString().padStart(5)}" +
                    "  ${s.unemployment.toString().padStart(6)}%  ${s.commute.toString().padStart(4)} min" +
                    "  ${(c.map.congestion.maxOf { it.toInt() and 0xff } * 100 / 128).toString().padStart(6)}%",
            )
        }
    }

    @Test
    fun fiftyYearsIsQuick() {
        val c = City(1900)
        val m = c.map
        c.apply(Action.BuildRoad(Action.roadPath(m, 0, 64, 127, 64, true)))
        c.apply(Action.PlaceZone(10, 60, 120, 63, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(10, 65, 60, 67, Zone.COMMERCIAL))
        c.apply(Action.PlaceZone(64, 65, 120, 67, Zone.INDUSTRIAL))
        val took = measureTime { c.run(50) }
        println("50 years on 128 x 128: $took, population ${c.stats.population}")
        assertTrue(took.inWholeSeconds < 20)
    }
}
