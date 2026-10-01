package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoadsTest {
    private fun city(size: Int = 32) = City(1, size, size, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true }

    private fun City.road(x0: Int, y0: Int, x1: Int, y1: Int, type: RoadType = RoadType.DIRT, acrossFirst: Boolean = true): Plan =
        apply(Action.BuildRoad(Action.roadPath(map, x0, y0, x1, y1, acrossFirst), type))

    private fun City.i(x: Int, y: Int) = map.index(x, y)

    private fun City.move(x0: Int, y0: Int, x1: Int, y1: Int) =
        Traffic.canMove(map, i(x0, y0), i(x1, y1), Heading.of(x1 - x0, y1 - y0).toInt())

    @Test
    fun oneWayRoadsRunTheWayTheyWereDrawn() {
        val c = city()
        assertTrue(c.road(10, 5, 4, 5, RoadType.ONE_WAY_STREET).ok)
        assertEquals(Heading.WEST, c.map.roadHeading[c.i(7, 5)])
        assertTrue(c.move(8, 5, 7, 5))
        assertFalse(c.move(7, 5, 8, 5))
        // Turning off it is fine.
        c.road(7, 5, 7, 9)
        assertTrue(c.move(7, 5, 7, 6))
        assertTrue(c.move(7, 6, 7, 5))
    }

    @Test
    fun twoWideRoadsGetACarriagewayEachWay() {
        val c = city()
        // Drawn east along y 10, so the other carriageway runs west along y 9.
        val plan = c.road(4, 10, 20, 10, RoadType.BOULEVARD)
        assertTrue(plan.ok)
        assertEquals(34, plan.changes.size)
        assertEquals(RoadType.BOULEVARD.price * 34, plan.cost)
        assertEquals(Heading.EAST, c.map.roadHeading[c.i(12, 10)])
        assertEquals(Heading.WEST, c.map.roadHeading[c.i(12, 9)])
        assertFalse(c.move(12, 10, 11, 10))
        assertTrue(c.move(12, 9, 11, 9))
        // No turning across the middle, except where a road crosses and at the ends.
        assertFalse(c.move(12, 10, 12, 9))
        assertTrue(c.move(20, 10, 20, 9))
        assertTrue(c.move(4, 9, 4, 10))
        c.road(15, 4, 15, 16)
        assertTrue(c.move(15, 10, 15, 9))
        assertTrue(c.move(15, 11, 15, 10))
        // The crossing stays a boulevard.
        assertEquals(RoadType.BOULEVARD.id, c.map.road[c.i(15, 10)])
    }

    @Test
    fun aTwoWideRoadStopsAtItsFirstTurn() {
        val c = city()
        val plan = c.plan(Action.BuildRoad(Action.roadPath(c.map, 4, 10, 8, 14, true), RoadType.BOULEVARD))
        assertEquals(10, plan.changes.size)
    }

    @Test
    fun upgradesCostTheDifference() {
        val c = city()
        c.road(4, 5, 13, 5)
        val funds = c.funds
        val plan = c.road(4, 5, 13, 5, RoadType.AVENUE)
        assertEquals(10 * (RoadType.AVENUE.price - RoadType.DIRT.price), plan.cost)
        assertEquals(funds - plan.cost, c.funds)
        assertEquals(RoadType.AVENUE.id, c.map.road[c.i(8, 5)])
        // Doing it again does nothing.
        assertEquals(Problem.NothingToDo, c.plan(Action.BuildRoad(Action.roadPath(c.map, 4, 5, 13, 5, true), RoadType.AVENUE)).problem)
        // A lane drawn across the avenue leaves the crossing alone.
        val lane = c.road(8, 2, 8, 8, RoadType.LANE)
        assertEquals(6, lane.changes.size)
        assertEquals(RoadType.AVENUE.id, c.map.road[c.i(8, 5)])
    }

    @Test
    fun bulldozingTakesTheHeadingToo() {
        val c = city()
        c.road(4, 5, 13, 5, RoadType.ONE_WAY_STREET)
        c.apply(Action.Bulldoze(6, 5, 6, 5))
        assertEquals(Road.NONE, c.map.road[c.i(6, 5)])
        assertEquals(Heading.BOTH, c.map.roadHeading[c.i(6, 5)])
        c.undo()
        assertEquals(Heading.EAST, c.map.roadHeading[c.i(6, 5)])
    }

    @Test
    fun bridgesCostMoreToKeep() {
        val c = city()
        for (y in 0 until 32) c.map.terrain[c.i(16, y)] = Terrain.WATER
        c.road(10, 5, 20, 5)
        repeat(40) { c.tick() }
        assertEquals(Math.round(10 * RoadType.DIRT.upkeep + RoadType.DIRT.upkeep * Balance.BRIDGE_UPKEEP), c.stats.roadUpkeep)
    }

    /** Runs a month of trips from the given tiles and ends it, so the results are in. */
    private fun City.trips(workers: Map<Int, Int>, jobs: Map<Int, Int>, months: Int = 1): Traffic {
        val t = Traffic(map)
        val n = map.size
        repeat(months) {
            val w = IntArray(n).also { a -> workers.forEach { (k, v) -> a[k] = v } }
            val j = IntArray(n).also { a -> jobs.forEach { (k, v) -> a[k] = v } }
            t.newMonth(w, IntArray(n), IntArray(n), j, IntArray(n), it)
            t.sendDay(1, 1)
        }
        t.newMonth(IntArray(n), IntArray(n), IntArray(n), IntArray(n), IntArray(n), 0)
        return t
    }

    @Test
    fun oneWayStreetsSendTrafficTheLongWayRound() {
        val c = city()
        // A one-way street west from the jobs to the homes, and a way round.
        c.road(20, 10, 4, 10, RoadType.ONE_WAY_STREET)
        c.road(4, 10, 4, 16)
        c.road(4, 16, 20, 16)
        c.road(20, 16, 20, 10)
        val t = c.trips(mapOf(c.i(4, 10) to 20), mapOf(c.i(20, 10) to 50))
        assertEquals(20, t.workersPlaced)
        assertEquals(0, t.lastVolume[c.i(12, 10)])
        assertEquals(10, t.lastVolume[c.i(12, 16)]) // averaged with the empty month before
        // 6 down, 16 across, 6 up, and the corners are dirt now.
        assertEquals(28 * RoadType.DIRT.time, t.commute[c.i(4, 10)])
    }

    @Test
    fun busyRoadsSendTrafficAnotherWay() {
        val c = city()
        // Two ways of the same length from the homes to the jobs.
        c.road(4, 10, 20, 10)
        c.road(4, 10, 4, 14)
        c.road(4, 14, 20, 14)
        c.road(20, 14, 20, 10)
        val t = c.trips(mapOf(c.i(4, 12) to 400), mapOf(c.i(20, 12) to 400), months = 4)
        val top = t.lastVolume[c.i(12, 10)]
        val bottom = t.lastVolume[c.i(12, 14)]
        assertTrue(top > 0 && bottom > 0, "top $top, bottom $bottom")
    }

    @Test
    fun jobsNoRoadReachesDontCount() {
        val c = city()
        c.road(4, 10, 10, 10)
        c.road(16, 10, 24, 10)
        val t = c.trips(mapOf(c.i(4, 10) to 20), mapOf(c.i(24, 10) to 50))
        assertEquals(20, t.workersSent)
        assertEquals(0, t.workersPlaced)
        assertEquals(-1, t.commute[c.i(4, 10)])
    }

    @Test
    fun homesCutOffFromWorkAreOutOfWork() {
        val c = city(48)
        c.road(0, 20, 47, 20)
        c.apply(Action.PlaceZone(4, 18, 20, 19, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(28, 21, 40, 22, Zone.INDUSTRIAL))
        repeat(365) { c.tick() }
        val before = c.stats.unemployment
        // Cut the road between the homes and the works, a gap wider than a lot's reach.
        c.apply(Action.Bulldoze(22, 20, 27, 20))
        repeat(62) { c.tick() }
        assertTrue(c.stats.unemployment > before + 30, "unemployment went from $before to ${c.stats.unemployment}")
    }

    @Test
    fun aBetterMainStreetShortensTheCommute() {
        fun town(type: RoadType): City {
            val c = City(7, 64, 64, TerrainOptions(water = 0, trees = 0, river = false))
            c.road(0, 30, 60, 30, type)
            c.apply(Action.PlaceZone(5, 28, 40, 29, Zone.RESIDENTIAL))
            c.apply(Action.PlaceZone(24, 31, 40, 32, Zone.INDUSTRIAL))
            c.apply(Action.PlaceZone(5, 31, 18, 32, Zone.COMMERCIAL))
            repeat(3 * 365) { c.tick() }
            return c
        }
        val dirt = town(RoadType.DIRT)
        val street = town(RoadType.STREET)
        assertTrue(street.stats.commute < dirt.stats.commute, "street ${street.stats.commute} min, dirt ${dirt.stats.commute} min")
    }

    /** A big town's month of trips: a street grid over the whole map, with homes and jobs all along it. */
    @Test
    fun aBigTownsTrafficIsQuick() {
        val c = City(1, 128, 128, TerrainOptions(water = 0, trees = 0, river = false))
        for (k in 0 until 128 step 6) {
            c.road(0, k, 127, k, RoadType.STREET)
            c.road(k, 0, k, 127, RoadType.STREET)
        }
        val n = c.map.size
        val workers = IntArray(n)
        val jobs = IntArray(n)
        val shoppers = IntArray(n)
        val shops = IntArray(n)
        val freight = IntArray(n)
        var people = 0
        for (i in 0 until n) {
            if (c.map.road[i] == Road.NONE) continue
            val x = i % 128
            if (x < 64) {
                workers[i] = 30; shoppers[i] = 16; people += 66
            } else {
                jobs[i] = 34; shops[i] = 40; freight[i] = 3
            }
        }
        val t = Traffic(c.map)
        val took = kotlin.time.measureTime {
            repeat(3) {
                t.newMonth(workers, shoppers, freight, jobs, shops, it)
                for (d in 1..30) t.sendDay(d, 30)
            }
        }
        t.newMonth(IntArray(n), IntArray(n), IntArray(n), IntArray(n), IntArray(n), 0)
        println("three months of trips for about $people people: $took, ${t.workersPlaced} of ${t.workersSent} placed")
        assertTrue(took.inWholeMilliseconds < 3_000)
    }

    @Test
    fun aVersionOneSaveStillLoads() {
        val bytes = javaClass.getResourceAsStream("/saves/v1.infill")!!.readBytes()
        val c = SaveGame.read(bytes)
        assertEquals("Oldbury", c.name)
        assertEquals(270, c.stats.population)
        assertEquals(76, c.buildingCount)
        assertEquals(RoadType.DIRT.id, c.map.road[c.map.index(10, 24)])
        // It carries on, and the first month's trips come in.
        repeat(70) { c.tick() }
        assertTrue(c.stats.commute > 0)
    }
}
