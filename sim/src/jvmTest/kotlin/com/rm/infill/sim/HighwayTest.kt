package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HighwayTest {
    private fun city(year: Int = 1960): City {
        val c = City(14, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true; it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        return c
    }

    private fun City.road(x0: Int, y0: Int, x1: Int, y1: Int, t: RoadType) =
        apply(Action.BuildRoad(Action.roadPath(map, x0, y0, x1, y1, y0 == y1), t))

    @Test
    fun nothingGrowsOffAHighway() {
        val c = city()
        val m = c.map
        assertTrue(c.road(0, 30, 63, 30, RoadType.HIGHWAY).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 26, 40, 28, Zone.RESIDENTIAL)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 50, 24)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 49, 25, 5, 25, true))).ok)
        repeat(365) { c.tick() }
        assertEquals(0, c.stats.population, "no homes reached from a highway")
    }

    @Test
    fun aStreetAcrossAHighwayMakesAnInterchange() {
        val c = city()
        val m = c.map
        assertTrue(c.road(0, 30, 63, 30, RoadType.HIGHWAY).ok)
        val funds = c.funds
        val plan = c.road(20, 20, 20, 40, RoadType.STREET)
        assertTrue(plan.ok)
        // Both carriageways are crossed, each an interchange.
        assertTrue(plan.cost >= 2 * Junction.price(Junction.INTERCHANGE))
        assertEquals(funds - plan.cost, c.funds)
        assertEquals(Junction.INTERCHANGE, m.junction[m.index(20, 30)])
        // The crossing stays highway.
        assertEquals(RoadType.HIGHWAY.id, m.road[m.index(20, 30)])
    }

    @Test
    fun aStreetBesideAHighwayDoesntJoinIt() {
        val c = city()
        val m = c.map
        assertTrue(c.road(0, 30, 63, 30, RoadType.HIGHWAY).ok)
        // The carriageways' rows, and a street coming up to the top one and stopping.
        val rows = (25..35).filter { m.road[m.index(40, it)] == RoadType.HIGHWAY.id }
        val top = rows.min()
        assertTrue(c.road(10, top - 1, 10, 20, RoadType.STREET).ok)
        repeat(32) { c.tick() }
        val times = c.travelTimes(m.index(10, 20))
        assertTrue(rows.all { times[m.index(40, it)] < 0 }, "no way on without an interchange")
        // With one where they meet, there is.
        assertTrue(c.apply(Action.SetJunction(intArrayOf(m.index(10, top)), Junction.INTERCHANGE)).ok)
        repeat(32) { c.tick() }
        val joined = c.travelTimes(m.index(10, 20))
        assertTrue(rows.any { joined[m.index(40, it)] > 0 }, "on at the interchange")
    }

    @Test
    fun aHighwayIsQuickerThanAnAvenue() {
        fun across(t: RoadType): Int {
            val c = city()
            val m = c.map
            assertTrue(c.road(0, 30, 63, 30, t).ok)
            repeat(32) { c.tick() }
            return c.travelTimes(m.index(1, 30))[m.index(62, 30)].let { if (it < 0) c.travelTimes(m.index(1, 31))[m.index(62, 31)] else it }
        }
        val highway = across(RoadType.HIGHWAY)
        val avenue = across(RoadType.AVENUE)
        assertTrue(highway in 1 until avenue, "highway $highway, avenue $avenue")
    }

    @Test
    fun aHighwayIsLoudToLiveBeside() {
        fun valueBeside(t: RoadType): Int {
            val c = city()
            assertTrue(c.road(0, 30, 63, 30, t).ok)
            repeat(40) { c.tick() }
            return c.map.landValue[c.map.index(30, 28)].toInt() and 0xff
        }
        assertTrue(valueBeside(RoadType.HIGHWAY) < valueBeside(RoadType.AVENUE))
    }

    @Test
    fun anInterchangeLaysRampsOnItsClearCorners() {
        val c = city()
        val m = c.map
        assertTrue(c.road(0, 30, 63, 30, RoadType.HIGHWAY).ok)
        val rows = (25..35).filter { m.road[m.index(40, it)] == RoadType.HIGHWAY.id }
        assertEquals(2, rows.size)
        assertTrue(c.road(20, 20, 20, 40, RoadType.STREET).ok)
        val ramps = listOf(19 to rows.min() - 1, 21 to rows.min() - 1, 19 to rows.max() + 1, 21 to rows.max() + 1)
        for ((x, y) in ramps) assertEquals(RoadType.RAMP.id, m.road[m.index(x, y)], "ramp at $x, $y")
        // Undo takes the ramps back with the street, and redo lays them again.
        c.undo()
        for ((x, y) in ramps) assertEquals(Road.NONE, m.road[m.index(x, y)])
        c.redo()
        // The highway doesn't stop for a ramp joining it.
        repeat(40) { c.tick() }
        for (y in rows) for (x in 18..22) assertTrue(m.control[m.index(x, y)] == Junction.FREE || m.control[m.index(x, y)] == Junction.INTERCHANGE)
    }

    @Test
    fun trackCrossesADividedRoadOnTheLevel() {
        for (t in listOf(RoadType.BOULEVARD, RoadType.HIGHWAY)) {
            // Track laid across the road.
            val c = city()
            val m = c.map
            assertTrue(c.road(0, 30, 63, 30, t).ok)
            val rows = (25..35).filter { m.road[m.index(40, it)] != Road.NONE }
            assertEquals(2, rows.size, "$t")
            val plan = c.apply(Action.BuildRail(Action.roadPath(m, 20, 20, 20, 40, false)))
            assertTrue(plan.ok && plan.blocked.isEmpty(), "$t: track across")
            for (y in 20..40) assertEquals(Rail.TRACK, m.rail[m.index(20, y)], "$t at $y")
            // And the road laid across the track.
            val d = city()
            assertTrue(d.apply(Action.BuildRail(Action.roadPath(d.map, 20, 20, 20, 40, false))).ok)
            assertTrue(d.road(0, 30, 63, 30, t).ok)
            for (y in rows) assertEquals(t.id, d.map.road[d.map.index(20, y)], "$t over track at $y")
        }
        // But not along a road, or across three lanes of road side by side.
        val c = city()
        val m = c.map
        assertTrue(c.road(0, 30, 63, 30, RoadType.BOULEVARD).ok)
        val below = (29..31).filter { m.road[m.index(40, it)] != Road.NONE }.max() + 1
        assertTrue(c.road(0, below, 63, below, RoadType.STREET).ok)
        assertTrue(c.plan(Action.BuildRail(Action.roadPath(m, 20, 20, 20, 40, false))).blocked.isNotEmpty())
        assertTrue(c.plan(Action.BuildRail(Action.roadPath(m, 5, 30, 15, 30, true))).blocked.isNotEmpty())
    }

    @Test
    fun aTrainHoldsUpAHighwayMoreThanAStreet() {
        fun across(t: RoadType, rail: Boolean): Int {
            val c = city()
            val m = c.map
            assertTrue(c.road(0, 30, 63, 30, t).ok)
            if (rail) assertTrue(c.apply(Action.BuildRail(Action.roadPath(m, 30, 20, 30, 40, false))).ok)
            repeat(32) { c.tick() }
            return c.travelTimes(m.index(1, 30))[m.index(62, 30)].let { if (it < 0) c.travelTimes(m.index(1, 31))[m.index(62, 31)] else it }
        }
        val highway = across(RoadType.HIGHWAY, true) - across(RoadType.HIGHWAY, false)
        val street = across(RoadType.STREET, true) - across(RoadType.STREET, false)
        assertTrue(highway > street && street > 0, "highway $highway, street $street")
    }
}
