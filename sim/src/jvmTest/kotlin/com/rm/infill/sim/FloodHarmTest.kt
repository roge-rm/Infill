package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloodHarmTest {
    /** A city with a river down columns 20 to 22, and money to spend. */
    private fun city(size: Int = 64): City {
        val c = City(5, size, size, TerrainOptions(water = 0, trees = 0, river = false))
        for (y in 0 until size) for (x in 20..22) c.map.terrain[c.map.index(x, y)] = Terrain.WATER
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        return c
    }

    private fun City.i(x: Int, y: Int) = map.index(x, y)

    /** The river well over its banks. */
    private fun City.spate() {
        ground = 100
        river = 95
        overflowRivers()
    }

    /** A riverside town: homes, shops and works on the east bank, grown for a few years. */
    private fun town(): City {
        val c = city()
        val m = c.map
        c.apply(Action.BuildRoad(Action.roadPath(m, 23, 30, 63, 30, true), RoadType.STREET))
        c.apply(Action.BuildRoad(Action.roadPath(m, 26, 10, 26, 50, false), RoadType.STREET))
        c.apply(Action.PlaceZone(23, 10, 25, 29, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(27, 10, 40, 29, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(23, 31, 25, 50, Zone.COMMERCIAL))
        c.apply(Action.PlaceZone(27, 31, 40, 50, Zone.INDUSTRIAL))
        repeat(3 * 365) { c.tick() }
        return c
    }

    @Test
    fun floodedPowerAndPumpingStationsStopUntilItDrains() {
        val c = city()
        val m = c.map
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 23, 10))
        c.apply(Action.BuildPowerLine(Action.roadPath(m, 25, 11, 40, 11, true)))
        c.apply(Action.PlaceBuilding(BuildingType.PUMPING_STATION, 23, 40))
        c.apply(Action.BuildPipe(Action.roadPath(m, 25, 41, 40, 41, true), Pipe.WATER))
        c.apply(Action.PlaceBuilding(BuildingType.POLICE_STATION, 35, 42))
        c.tick()
        assertTrue(m.powered[c.i(40, 11)])
        assertTrue(m.watered[c.i(35, 42)])
        c.spate()
        c.tick()
        assertFalse(m.powered[c.i(40, 11)], "power with the plant under water")
        assertFalse(m.watered[c.i(35, 42)], "water with the pumps under water")
        repeat(15) { c.tick() }
        assertTrue(m.powered[c.i(40, 11)], "power once it's drained")
        assertTrue(m.watered[c.i(35, 42)], "water once it's drained")
    }

    @Test
    fun deepWaterClosesTheRoad() {
        val c = City(1, 32, 32, TerrainOptions(water = 0, trees = 0, river = false))
        c.apply(Action.BuildRoad(Action.roadPath(c.map, 2, 10, 28, 10, true)))
        val n = c.map.size
        fun trips(): Traffic {
            val t = Traffic(c.map)
            t.newMonth(IntArray(n).also { it[c.i(2, 10)] = 20 }, IntArray(n), IntArray(n), IntArray(n).also { it[c.i(28, 10)] = 50 }, IntArray(n), 0)
            t.sendDay(1, 1)
            t.newMonth(IntArray(n), IntArray(n), IntArray(n), IntArray(n), IntArray(n), 0)
            return t
        }
        assertEquals(20, trips().workersPlaced)
        c.map.flood[c.i(15, 10)] = Balance.FLOODED.toByte()
        assertEquals(20, trips().workersPlaced, "shallow water only slows it")
        c.map.flood[c.i(15, 10)] = Balance.FLOOD_DAMAGE.toByte()
        assertEquals(0, trips().workersPlaced, "deep water closes it")
    }

    @Test
    fun buyersRememberFloodsForYears() {
        val wet = city().also { it.spate() }
        val dry = city()
        repeat(40) { wet.tick(); dry.tick() }
        val at = wet.i(23, 30)
        val memory = wet.map.floodMemory[at].toInt() and 0xff
        assertTrue(memory > 0)
        assertTrue((wet.map.landValue[at].toInt() and 0xff) < (dry.map.landValue[at].toInt() and 0xff), "flooded land is worth less")
        repeat(3 * 365) { wet.tick() }
        val later = wet.map.floodMemory[at].toInt() and 0xff
        assertTrue(later in 1 until memory, "the memory fades but slowly: $memory then $later")
    }

    @Test
    fun aFloodLeavesMud() {
        val c = town()
        val at = c.i(23, 30)
        val grime = c.map.grime[at].toInt() and 0xff
        c.spate()
        assertTrue((c.map.grime[at].toInt() and 0xff) > grime, "mud on the street")
    }

    @Test
    fun theCleanUpIsPaidWithTheMonthsUpkeep() {
        val c = town()
        c.spate()
        // To the first of next month.
        val month = c.month
        while (c.month == month) c.tick()
        assertTrue(c.stats.floodCost > 0, "clean-up ${c.stats.floodCost}")
        while (c.month != (month + 2) % 12) c.tick()
        assertEquals(0L, c.stats.floodCost, "and it's paid once")
    }

    @Test
    fun floodwaterOnWellsAndSepticMakesPeopleIll() {
        val c = town()
        c.takeEvents { }
        val before = c.stats.population
        c.spate()
        var sick = false
        c.takeEvents { if (it.kind == EventKind.Sickness) sick = true }
        assertTrue(sick, "no sickness among ${before} people on wells")
    }

    @Test
    fun floodedShopsPayLess() {
        val wet = town()
        val dry = town()
        assertEquals(dry.stats.commercialIncome, wet.stats.commercialIncome)
        wet.spate()
        val month = wet.month
        while (wet.month == month) { wet.tick(); dry.tick() }
        assertTrue(wet.stats.commercialIncome < dry.stats.commercialIncome, "${wet.stats.commercialIncome} flooded, ${dry.stats.commercialIncome} dry")
    }
}
