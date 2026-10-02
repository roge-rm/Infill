package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortTest {
    /** An empty map in [year] with a river down x 40 to 42 from edge to edge, and a lake with no way out. */
    private fun watery(year: Int = 1930): City {
        val c = City(15, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true; it.disasterLevel = 0; it.needsApply = false }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        val m = c.map
        for (y in 0 until 64) for (x in 40..42) m.terrain[m.index(x, y)] = Terrain.WATER
        for (y in 10..16) for (x in 8..16) m.terrain[m.index(x, y)] = Terrain.WATER
        return c
    }

    private fun City.road(x0: Int, y0: Int, x1: Int, y1: Int) =
        apply(Action.BuildRoad(Action.roadPath(map, x0, y0, x1, y1, y0 == y1)))

    @Test
    fun aPortNeedsWaterShipsCanReach() {
        val c = watery()
        // Beside the river, the long side on the water.
        assertTrue(c.plan(Action.PlaceBuilding(BuildingType.WHARF_NS, 38, 30)).ok)
        assertTrue(c.plan(Action.PlaceBuilding(BuildingType.WHARF_NS, 43, 30)).ok)
        // Away from it, or the short side on it.
        assertEquals(Problem.NeedsWater, c.plan(Action.PlaceBuilding(BuildingType.WHARF_NS, 30, 30)).problem)
        assertEquals(Problem.NeedsWater, c.plan(Action.PlaceBuilding(BuildingType.WHARF, 37, 30)).problem)
        // On a lake ships can't get to.
        assertEquals(Problem.NoSeaRoute, c.plan(Action.PlaceBuilding(BuildingType.WHARF, 10, 17)).problem)
    }

    @Test
    fun aBridgeCantCutAPortOff() {
        val c = watery()
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.WHARF_NS, 43, 30)).ok)
        // One bridge leaves the way out to the south.
        assertTrue(c.road(30, 10, 50, 10).ok)
        // A second shuts it in.
        val plan = c.plan(Action.BuildRoad(Action.roadPath(c.map, 30, 50, 50, 50, true)))
        assertEquals(Problem.CutsOffPort, plan.problem)
        // Away from the port's way out, the lake's fine to cross.
        assertTrue(c.plan(Action.BuildRoad(Action.roadPath(c.map, 6, 13, 18, 13, true))).ok)
    }

    /** A town with works and no road to the edge, so its freight has only [port] to go by. */
    private fun works(port: Boolean): City {
        val c = watery(1930)
        val m = c.map
        assertTrue(c.road(44, 20, 60, 20).ok)
        assertTrue(c.road(44, 40, 60, 40).ok)
        assertTrue(c.road(52, 20, 52, 40).ok)
        assertTrue(c.apply(Action.PlaceZone(53, 21, 60, 39, Zone.INDUSTRIAL)).ok)
        assertTrue(c.apply(Action.PlaceZone(44, 21, 51, 30, Zone.RESIDENTIAL)).ok)
        assertTrue(c.apply(Action.PlaceZone(44, 31, 51, 39, Zone.COMMERCIAL)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 58, 8)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 57, 9, 57, 39, false))).ok)
        if (port) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.DOCKS_NS, 43, 41)).ok)
        repeat(3 * 365) { c.tick() }
        return c
    }

    @Test
    fun freightGoesOutByShipAndThePortPaysDues() {
        val c = works(true)
        val none = works(false)
        assertTrue(c.stats.portLoads > 0, "loads through the port")
        assertTrue(c.stats.duesIncome > 0)
        // Without it, nothing gets out of town.
        assertTrue(c.stats.goodsExported.sum() > 0)
        assertEquals(0, none.stats.goodsExported.sum())
        assertTrue(c.shipRoutes.isNotEmpty())
        val route = c.shipRoutes.first()
        assertTrue(route.tiles.all { c.map.terrain[it] == Terrain.WATER })
        val first = route.tiles.first()
        assertTrue(first / 64 == 0 || first / 64 == 63, "in from the edge")
        // Coal comes cheaper by ship.
        val plant = c.buildingAt(58, 8)!!
        assertTrue(c.stationOutput(plant) > 0)
        val withPort = c.fuelCost(plant)
        City::class.java.getDeclaredField("seaTier").apply { isAccessible = true }.setInt(c, 0)
        assertTrue(c.fuelCost(plant) > withPort)
    }

    @Test
    fun visitorsComeByRoadAndBySea() {
        val c = watery(1930)
        assertTrue(c.road(44, 30, 63, 30).ok)
        assertTrue(c.apply(Action.PlaceParks(45, 31, 50, 34)).ok)
        assertTrue(c.apply(Action.PlaceZone(52, 31, 62, 36, Zone.RESIDENTIAL)).ok)
        repeat(40) { c.tick() }
        val byRoad = c.stats.visitors
        assertTrue(byRoad > 0)
        assertEquals(0, c.stats.visitorsBy[Tourism.SEA])
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.DOCKS_NS, 43, 24)).ok)
        repeat(40) { c.tick() }
        assertTrue(c.stats.visitorsBy[Tourism.SEA] > 0, "off the ships")
        assertTrue(c.stats.duesIncome > 0)
    }

    @Test
    fun portsAreSavedAndOldTownsLoad() {
        val c = works(true)
        val back = SaveGame.read(SaveGame.write(c))
        assertEquals(c.stats.portLoads, back.stats.portLoads)
        assertEquals(c.stats.visitors, back.stats.visitors)
        assertEquals(c.shipRoutes.size, back.shipRoutes.size)
        assertTrue(c.shipRoutes.zip(back.shipRoutes).all { (a, b) -> a.tiles.contentEquals(b.tiles) && a.kind == b.kind && a.ships == b.ships })
        assertTrue(back.map.hash() == c.map.hash())
        // A town from before ports carries on.
        val old = SaveGame.read(javaClass.getResourceAsStream("/saves/v22.infill")!!.readBytes())
        assertEquals("Twenty-second", old.name)
        val people = old.stats.population
        repeat(70) { old.tick() }
        assertTrue(old.stats.population > people / 2)
        assertTrue(old.stats.visitors > 0, "visitors by road")
    }
}
