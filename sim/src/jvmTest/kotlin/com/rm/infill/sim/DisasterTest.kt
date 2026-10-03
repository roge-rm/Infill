package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DisasterTest {
    private fun city(year: Int = 1950, quakes: Boolean = false, seed: Long = 4): City {
        val c = City(seed, 64, 64, TerrainOptions(water = 0, trees = 0, river = false, quakes = quakes)).also { it.everything = true }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 10_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        return c
    }

    private fun City.i(x: Int, y: Int) = map.index(x, y)

    private fun City.road(x0: Int, y0: Int, x1: Int, y1: Int) =
        apply(Action.BuildRoad(Action.roadPath(map, x0, y0, x1, y1, true), RoadType.STREET, pipes = true))

    private fun City.month() = repeat(31) { tick() }

    private fun City.kinds(): Set<EventKind> = HashSet<EventKind>().also { s -> takeEvents { s += it.kind } }

    /** A street grid of homes with mains, powered along a line from a coal station. */
    private fun town(c: City) {
        for (k in 20..44 step 6) {
            c.road(20, k, 44, k)
            c.road(k, 20, k, 44)
        }
        c.apply(Action.PlaceZone(21, 21, 43, 37, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(21, 39, 31, 43, Zone.COMMERCIAL))
        c.apply(Action.PlaceZone(33, 39, 43, 43, Zone.INDUSTRIAL))
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 4, 4))
        c.apply(Action.PlaceBuilding(BuildingType.PUMPING_STATION, 4, 50))
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 6, 6, 20, 20, true)))
    }

    private fun lines(c: City) = (0 until c.map.size).filter { c.map.power[it] != Power.NONE }

    @Test
    fun aGaleBringsDownLinesAndCrewsPutThemBack() {
        val c = city()
        town(c)
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 6, 60, 60, 60, true)))
        c.gale()
        assertTrue(EventKind.Gale in c.kinds())
        val down = lines(c).filter { c.map.out(it, Broken.POWER) }
        assertTrue(down.isNotEmpty(), "nothing came down")
        repeat(Balance.MEND_LINE + 1) { c.tick() }
        assertTrue(down.none { c.map.out(it, Broken.POWER) }, "still down")
    }

    @Test
    fun emergencyFundingMendsFaster() {
        fun days(funding: Int): Int {
            val c = city()
            c.reliefFunding = funding
            c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 0, 10, 63, 10, true)))
            c.gale()
            return lines(c).filter { c.map.out(it, Broken.POWER) }.maxOf { c.map.mendingDays(it) }
        }
        assertTrue(days(200) < days(50), "${days(200)} days at 200%, ${days(50)} at 50%")
    }

    @Test
    fun aNuclearAccidentClearsTheLandAround() {
        val c = city(1975)
        town(c)
        repeat(12) { c.month() }
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.NUCLEAR_PLANT, 46, 30)).ok)
        c.tick()
        val b = c.buildingAt(46, 30)!!
        c.kinds()
        val homes = c.homes.size
        c.nuclearAccident(b)
        assertTrue(EventKind.NuclearAccident in c.kinds())
        assertNull(c.buildingAt(46, 30))
        assertTrue(c.homes.size < homes, "no homes lost")
        assertEquals(1, c.map.brownfield[c.i(44, 31)].toInt())
        assertNull(c.buildingAt(43, 31))
        assertEquals(0, c.map.brownfield[c.i(20, 20)].toInt(), "far away")
        c.month()
        assertTrue(c.stats.disasterCost >= Balance.NUCLEAR_BILL)
    }

    @Test
    fun anEarthquakeDamagesAndBreaks() {
        val c = city(1910)
        town(c)
        repeat(36) { c.month() }
        val before = c.homes.sumOf { it.type.capacity }
        c.quake(32, 32)
        assertTrue(EventKind.Earthquake in c.kinds())
        val after = c.homes.sumOf { it.type.capacity }
        val broken = (0 until c.map.size).count { c.map.broken[it].toInt() != 0 }
        assertTrue(after < before || broken > 0, "nothing happened")
    }

    @Test
    fun earthquakesOnlyWhereTheMapHasThem() {
        assertFalse(City(1, 32, 32).quakes)
        assertTrue(City(1, 32, 32, TerrainOptions(quakes = true)).quakes)
        // They save with the map.
        assertTrue(SaveGame.read(SaveGame.write(City(1, 32, 32, TerrainOptions(quakes = true)))).quakes)
    }

    @Test
    fun anEpidemicTakesTheOldAndTheYoung() {
        var died = 0
        fun run(sick: Boolean): City {
            val c = city(1920)
            c.disasterLevel = 0
            town(c)
            repeat(24) { c.month() }
            if (sick) c.startEpidemic(4, 90)
            died = 0
            repeat(4) {
                c.month()
                died += c.stats.deaths
            }
            return c
        }
        val well = run(false)
        val wellDied = died
        val sick = run(true)
        assertTrue(died > wellDied, "$died died in the epidemic, $wellDied without")
        assertTrue(sick.stats.health < well.stats.health)
        sick.kinds()
        sick.month()
        assertFalse(sick.epidemicNow)
    }

    @Test
    fun theFluComesIn1918() {
        val c = city(1912, seed = 2)
        town(c)
        val seen = HashSet<EventKind>()
        while (c.year < 1919) {
            c.tick()
            c.takeEvents { if (it.kind == EventKind.Epidemic && c.year == 1918) seen += it.kind }
        }
        assertTrue(c.stats.population < 300 || EventKind.Epidemic in seen, "population ${c.stats.population}")
    }

    @Test
    fun withDisastersOffNothingHappens() {
        val c = city(1912, quakes = true)
        c.disasterLevel = 0
        town(c)
        val seen = HashSet<EventKind>()
        repeat(10 * 12) {
            c.month()
            seen += c.kinds()
        }
        // A heat wave still comes, as weather, but does no harm.
        val disasters = setOf(
            EventKind.Gale, EventKind.Blizzard, EventKind.IndustrialAccident, EventKind.NuclearAccident,
            EventKind.Earthquake, EventKind.Epidemic,
        )
        assertTrue(seen.intersect(disasters).isEmpty(), "$seen")
    }

    @Test
    fun snowedInOnlyFeetAndRailsMove() {
        val c = city()
        c.road(4, 10, 40, 10)
        val t = Traffic(c.map)
        val n = c.map.size
        fun send(): Int {
            val w = IntArray(n).also { it[c.i(4, 10)] = 40 }
            val j = IntArray(n).also { it[c.i(40, 10)] = 40 }
            t.newMonth(w, IntArray(n), IntArray(n), j, IntArray(n), 0, carWorkersAt = w)
            t.sendDay(1, 1)
            t.newMonth(IntArray(n), IntArray(n), IntArray(n), IntArray(n), IntArray(n), 0)
            return t.lastVolume[c.i(20, 10)]
        }
        assertTrue(send() > 0)
        val snowed = Traffic(c.map).also { it.snowedIn = true }
        val w = IntArray(n).also { it[c.i(4, 10)] = 40 }
        val j = IntArray(n).also { it[c.i(40, 10)] = 40 }
        snowed.newMonth(w, IntArray(n), IntArray(n), j, IntArray(n), 0, carWorkersAt = w)
        snowed.sendDay(1, 1)
        snowed.newMonth(IntArray(n), IntArray(n), IntArray(n), IntArray(n), IntArray(n), 0)
        assertEquals(0, snowed.lastVolume[c.i(20, 10)])
        // They walk instead.
        assertTrue(snowed.workersPlaced > 0)
    }
}
