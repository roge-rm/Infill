package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JunctionTest {
    private fun city(year: Int = 1930): City {
        val c = City(1, 48, 48, TerrainOptions(water = 0, trees = 0, river = false))
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        return c
    }

    private fun City.i(x: Int, y: Int) = map.index(x, y)

    private fun City.road(x0: Int, y0: Int, x1: Int, y1: Int) =
        apply(Action.BuildRoad(Action.roadPath(map, x0, y0, x1, y1, true), RoadType.STREET))

    @Test
    fun crossingsAreWhereThreeOrFourRoadsMeet() {
        val c = city()
        c.road(5, 20, 40, 20)
        c.road(20, 5, 20, 40)
        c.road(30, 20, 30, 30)
        assertTrue(Junction.at(c.map, c.i(20, 20)), "a crossroads")
        assertTrue(Junction.at(c.map, c.i(30, 20)), "a T")
        assertFalse(Junction.at(c.map, c.i(25, 20)), "a plain stretch")
        assertFalse(Junction.at(c.map, c.i(30, 30)), "a dead end")
    }

    @Test
    fun theTownPutsUpStopSignsThenLightsAsTrafficGrows() {
        val c = city()
        c.road(5, 20, 40, 20)
        c.road(20, 5, 20, 40)
        c.tick()
        val x = c.i(20, 20)
        assertEquals(Junction.FREE, c.map.control[x])
        val traffic = City::class.java.getDeclaredField("traffic").apply { isAccessible = true }.get(c) as Traffic
        val update = City::class.java.getDeclaredMethod("updateJunctions").apply { isAccessible = true }
        traffic.lastVolume[x] = RoadType.STREET.capacity * 40 / 100
        update.invoke(c)
        assertEquals(Junction.STOP, c.map.control[x])
        traffic.lastVolume[x] = RoadType.STREET.capacity
        update.invoke(c)
        assertEquals(Junction.LIGHTS, c.map.control[x])
        // Before the 1920s, stop signs are as far as it goes.
        val early = city(1910)
        early.road(5, 20, 40, 20)
        early.road(20, 5, 20, 40)
        val t2 = City::class.java.getDeclaredField("traffic").apply { isAccessible = true }.get(early) as Traffic
        t2.lastVolume[x] = RoadType.STREET.capacity
        update.invoke(early)
        assertEquals(Junction.STOP, early.map.control[x])
    }

    @Test
    fun aRoundaboutCostsAndCanBeUndone() {
        val c = city()
        c.road(5, 20, 40, 20)
        c.road(20, 5, 20, 40)
        val funds = c.funds
        val plan = c.apply(Action.SetJunction(intArrayOf(c.i(19, 20), c.i(20, 20), c.i(21, 20)), Junction.ROUNDABOUT))
        assertTrue(plan.ok)
        assertEquals(1, plan.changes.size, "only the crossing")
        assertEquals(Junction.price(Junction.ROUNDABOUT), plan.cost)
        assertEquals(funds - plan.cost, c.funds)
        assertEquals(Junction.ROUNDABOUT, c.map.control[c.i(20, 20)])
        c.undo()
        assertEquals(Junction.AUTO, c.map.junction[c.i(20, 20)])
        assertEquals(Junction.FREE, c.map.control[c.i(20, 20)])
        // An interchange waits for the 1950s.
        assertFalse(c.plan(Action.SetJunction(intArrayOf(c.i(20, 20)), Junction.INTERCHANGE)).ok)
        // Bulldozing the road takes the control with it.
        c.apply(Action.SetJunction(intArrayOf(c.i(20, 20)), Junction.LIGHTS))
        c.apply(Action.Bulldoze(20, 20, 20, 20))
        assertEquals(Junction.AUTO, c.map.junction[c.i(20, 20)])
    }

    @Test
    fun aBusyCrossingIsQuickerWithABetterControl() {
        // Through a crossing as busy as its road can take: stop signs back up, lights and roundabouts less so.
        val full = RoadType.STREET.capacity
        val stop = Junction.wait(Junction.STOP, RoadType.STREET.capacity, full)
        val lights = Junction.wait(Junction.LIGHTS, RoadType.STREET.capacity, full)
        val roundabout = Junction.wait(Junction.ROUNDABOUT, RoadType.STREET.capacity, full)
        val interchange = Junction.wait(Junction.INTERCHANGE, RoadType.STREET.capacity, full)
        assertTrue(lights < stop && roundabout < stop && interchange < lights, "stop $stop, lights $lights, roundabout $roundabout, interchange $interchange")
        // Quiet, a roundabout beats lights.
        assertTrue(Junction.wait(Junction.ROUNDABOUT, full, full / 10) < Junction.wait(Junction.LIGHTS, full, full / 10))
    }

    @Test
    fun junctionsSurviveASave() {
        val c = city()
        c.road(5, 20, 40, 20)
        c.road(20, 5, 20, 40)
        c.apply(Action.SetJunction(intArrayOf(c.i(20, 20)), Junction.ROUNDABOUT))
        val loaded = SaveGame.read(SaveGame.write(c))
        assertEquals(Junction.ROUNDABOUT, loaded.map.junction[c.i(20, 20)])
        assertEquals(Junction.ROUNDABOUT, loaded.map.control[c.i(20, 20)])
    }
}
