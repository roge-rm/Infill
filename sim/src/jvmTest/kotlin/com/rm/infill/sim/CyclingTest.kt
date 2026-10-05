package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CyclingTest {
    /** A long street across the map, in [year]. */
    private fun city(year: Int = 1925): City {
        val c = City(6, 64, 32, TerrainOptions(water = 0, trees = 0, river = false))
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        c.everything = true
        c.needsApply = false
        return c
    }

    private val City.traffic get() = City::class.java.getDeclaredField("traffic").apply { isAccessible = true }.get(this) as Traffic

    /** [workers] going from [from] to [to], none with a car, [cycling] percent of them on bicycles. */
    private fun City.trips(workers: Int, from: Int, to: Int, cycling: Int): Traffic {
        val t = traffic
        val n = map.size
        City::class.java.getDeclaredMethod("updateNetworks").apply { isAccessible = true }.invoke(this)
        t.cycling = cycling
        val w = IntArray(n).also { it[from] = workers }
        val j = IntArray(n).also { it[to] = workers }
        t.newMonth(w, IntArray(n), IntArray(n), j, IntArray(n), 0)
        t.sendDay(1, 1)
        t.newMonth(IntArray(n), IntArray(n), IntArray(n), IntArray(n), IntArray(n), 0)
        return t
    }

    @Test
    fun cyclistsGoFasterThanWalkers() {
        val c = city()
        c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 16, 63, 16, true), RoadType.STREET))
        val from = c.map.index(2, 16)
        val to = c.map.index(60, 16)
        val walked = c.trips(10, from, to, 0).commute[from]
        val t = c.trips(10, from, to, 100)
        assertEquals(10, t.lastModes[Mode.BIKE.ordinal])
        assertTrue(t.commute[from] < walked / 2, "${t.commute[from]} s cycling against $walked s walking")
        assertTrue(t.lastBikeVolume[c.map.index(30, 16)] > 0)
        assertEquals(0, t.lastWalkVolume[c.map.index(30, 16)])
        assertTrue(c.trips(10, from, to, 0).lastWalkVolume[c.map.index(30, 16)] > 0)
    }

    @Test
    fun aCycleLaneKeepsCyclistsClearOfTheTraffic() {
        val c = city(1975)
        c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 16, 63, 16, true), RoadType.STREET))
        val from = c.map.index(2, 16)
        val to = c.map.index(60, 16)
        // A street full of traffic.
        fun jam() { for (x in 0..63) c.traffic.lastVolume[c.map.index(x, 16)] = RoadType.STREET.capacity }
        jam()
        val busy = c.trips(10, from, to, 100).commute[from]
        assertTrue(c.apply(Action.BuildCycleLane(Action.roadPath(c.map, 0, 16, 63, 16, true))).ok)
        jam()
        val laned = c.trips(10, from, to, 100).commute[from]
        assertTrue(laned < busy, "$laned s with a lane against $busy s without")
        // Undone and saved like the rest of the street.
        assertEquals(1, SaveGame.read(SaveGame.write(c)).map.cycleLane[c.map.index(30, 16)].toInt())
        c.undo()
        assertEquals(0, c.map.cycleLane[c.map.index(30, 16)].toInt())
    }

    @Test
    fun aFerryCrossesWhereThereIsNoBridge() {
        val c = city()
        for (y in 0 until 32) for (x in 28..35) c.map.terrain[c.map.index(x, y)] = Terrain.WATER
        c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 16, 25, 16, true), RoadType.STREET))
        c.apply(Action.BuildRoad(Action.roadPath(c.map, 38, 16, 63, 16, true), RoadType.STREET))
        val from = c.map.index(2, 16)
        val to = c.map.index(60, 16)
        // No way over: nobody gets there.
        assertEquals(-1, c.trips(10, from, to, 0).commute[from])
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.FERRY_TERMINAL, 26, 14)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.FERRY_TERMINAL, 36, 14)).ok)
        // Not away from the water.
        assertEquals(Problem.NeedsWater, c.plan(Action.PlaceBuilding(BuildingType.FERRY_TERMINAL, 10, 12)).problem)
        val t = c.trips(10, from, to, 0)
        assertEquals(10, t.lastModes[Mode.FERRY.ordinal])
        assertEquals(1, c.ferryRoutes.size)
    }
}
