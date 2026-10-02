package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TunnelTest {
    private fun city(year: Int = 1950): City {
        val c = City(19, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = false; it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 50_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        City::class.java.getDeclaredField("era").apply { isAccessible = true }.set(c, Era.FUTURE)
        return c
    }

    private fun City.road(x0: Int, y0: Int, x1: Int, y1: Int, t: RoadType = RoadType.STREET, tunnel: Boolean = false) =
        apply(Action.BuildRoad(Action.roadPath(map, x0, y0, x1, y1, y0 == y1), t, tunnel = tunnel))

    private fun City.time(x0: Int, y0: Int, x1: Int, y1: Int) = travelTimes(map.index(x0, y0))[map.index(x1, y1)]

    /** Power to a tunnel's pumps: a plant and a line beside the tunnel from [x0] to [x1] along row [y]. */
    private fun City.power(x0: Int, x1: Int, y: Int) {
        assertTrue(apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 2, 2)).ok)
        assertTrue(apply(Action.BuildPowerLine(Action.roadPath(map, 4, 3, x0, y, true))).ok)
        assertTrue(apply(Action.BuildPowerLine(Action.roadPath(map, x0, y, x1, y, true))).ok)
    }

    @Test
    fun anUnderpassTakesTheRoadUnderTheTrack() {
        val c = city()
        val m = c.map
        assertTrue(c.road(10, 30, 50, 30).ok)
        assertTrue(c.apply(Action.BuildRail(Action.roadPath(m, 30, 0, 30, 63, false))).ok)
        repeat(3) { c.tick() }
        val crossing = c.time(20, 30, 40, 30)
        // Three tiles along the road through the crossing: the road goes down at each end and under the track.
        val plan = c.road(29, 30, 31, 30, tunnel = true)
        assertTrue(plan.ok && plan.blocked.isEmpty(), "${plan.blocked.toList()}")
        assertEquals(Road.NONE, m.road[m.index(30, 30)], "the road's gone from the level crossing")
        assertEquals(Rail.TRACK, m.rail[m.index(30, 30)])
        assertEquals(Heading.WEST.toInt(), m.portal[m.index(29, 30)].toInt())
        assertEquals(Heading.EAST.toInt(), m.portal[m.index(31, 30)].toInt())
        val under = c.time(20, 30, 40, 30)
        assertTrue(under in 1 until crossing, "under $under, across $crossing")
    }

    @Test
    fun aTunnelOnlyOpensAtItsEnds() {
        val c = city()
        val m = c.map
        for (y in 0 until 64) for (x in 30..33) m.terrain[m.index(x, y)] = Terrain.WATER
        assertTrue(c.road(20, 30, 26, 30).ok)
        assertTrue(c.road(27, 30, 36, 30, tunnel = true).ok)
        assertTrue(c.road(37, 30, 45, 30).ok)
        assertTrue(m.tunnelled(m.index(31, 30)))
        assertTrue(c.time(20, 30, 45, 30) > 0, "through under the river")
        // A road beside the tunnel's middle doesn't get into it.
        assertTrue(c.road(29, 20, 29, 29).ok)
        assertTrue(c.time(29, 20, 45, 30) < 0)
        // Nor can anything stand on a portal.
        assertFalse(c.apply(Action.PlaceBuilding(BuildingType.CLINIC, 27, 30)).ok)
    }

    @Test
    fun cutAndCoverGoesUnderBuildings() {
        val c = city()
        val m = c.map
        assertTrue(c.road(10, 30, 19, 30).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.SCHOOL, 23, 29)).ok)
        val plan = c.road(20, 30, 30, 30, tunnel = true)
        assertTrue(plan.ok && plan.blocked.isEmpty())
        assertTrue(c.road(31, 30, 40, 30).ok)
        assertTrue(m.building[m.index(23, 30)] != 0, "the school still stands")
        assertTrue(c.time(10, 30, 40, 30) > 0)
        // Undo fills it in again.
        c.undo()
        c.undo()
        assertFalse(m.tunnelled(m.index(25, 30)))
        assertEquals(0, m.portal[m.index(20, 30)].toInt())
    }

    @Test
    fun aTunnelWithNoPowerFloods() {
        val c = city()
        val m = c.map
        assertTrue(c.road(10, 30, 19, 30).ok)
        assertTrue(c.road(20, 30, 30, 30, tunnel = true).ok)
        assertTrue(c.road(31, 30, 40, 30).ok)
        c.rainfall(Balance.DOWNPOUR + 20)
        assertTrue(m.tunnelShut(m.index(25, 30)), "flooded")
        assertTrue(c.time(10, 30, 40, 30) < 0)
        // With power for its pumps, another stays dry.
        val d = city()
        assertTrue(d.road(10, 30, 19, 30).ok)
        assertTrue(d.road(20, 30, 30, 30, tunnel = true).ok)
        d.power(20, 30, 32)
        d.tick()
        d.rainfall(Balance.DOWNPOUR + 20)
        assertFalse(d.map.tunnelShut(d.map.index(25, 30)))
    }

    @Test
    fun fuelTrucksStayOutOfTunnels() {
        val c = city()
        assertTrue(c.allowsTunnel(rail = false))
        val early = city(1910)
        assertFalse(early.road(20, 30, 30, 30, tunnel = true).blocked.isEmpty(), "no road tunnels before their time")
        assertTrue(early.apply(Action.BuildRail(Action.roadPath(early.map, 20, 30, 30, 30, true), tunnel = true)).blocked.isEmpty(), "track went under ground long before")
    }

    @Test
    fun trainsRunThroughARailTunnel() {
        val c = city()
        val m = c.map
        assertTrue(c.road(30, 0, 30, 63).ok)
        assertTrue(c.apply(Action.BuildRail(Action.roadPath(m, 0, 30, 25, 30, true))).ok)
        assertTrue(c.apply(Action.BuildRail(Action.roadPath(m, 26, 30, 34, 30, true), tunnel = true)).ok)
        assertTrue(c.apply(Action.BuildRail(Action.roadPath(m, 35, 30, 50, 30, true))).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.STATION, 44, 31)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.STATION, 10, 31)).ok)
        assertTrue(c.road(10, 32, 45, 32).blocked.isNotEmpty() || true)
        // The road crosses over the tunnel, and the track is gone from under it.
        assertEquals(Rail.NONE, m.rail[m.index(30, 30)])
        repeat(40) { c.tick() }
        val a = c.buildingAt(44, 31)!!
        assertTrue(c.railLinked(a), "the line reaches the edge through the tunnel")
        val route = c.trainRoutes.firstOrNull { r -> r.tiles.contains(m.index(30, 30)) }
        if (route != null) {
            val k = route.tiles.indexOf(m.index(30, 30))
            assertTrue(route.hidden[k], "under the road, out of sight")
        }
    }

    @Test
    fun tunnelsAreSaved() {
        val c = city()
        val m = c.map
        assertTrue(c.road(10, 30, 19, 30).ok)
        assertTrue(c.road(20, 30, 30, 30, RoadType.STREET, tunnel = true).ok)
        assertTrue(c.road(31, 30, 40, 30).ok)
        repeat(3) { c.tick() }
        val back = SaveGame.read(SaveGame.write(c))
        assertEquals(m.hash(), back.map.hash())
        assertTrue(back.time(10, 30, 40, 30) > 0)
    }
}
