package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActionTest {
    /** A city on plain grass, with a strip of water down column 10 and trees in column 5. */
    private fun city(): City {
        val c = City(1, 32, 32, TerrainOptions(water = 0, trees = 0, river = false))
        for (y in 0 until 32) {
            c.map.terrain[c.map.index(10, y)] = Terrain.WATER
            c.map.terrain[c.map.index(5, y)] = Terrain.TREES
        }
        return c
    }

    @Test
    fun roadCostsPerTileAndClearsTrees() {
        val c = city()
        val path = Action.roadPath(c.map, 3, 2, 7, 2, acrossFirst = true)
        assertEquals(5, path.size)
        val plan = c.apply(Action.BuildRoad(path))
        assertTrue(plan.ok)
        assertEquals(5 * Prices.DIRT_ROAD + Prices.CLEAR_TREES, plan.cost)
        assertEquals(City.START_FUNDS - plan.cost, c.funds)
        assertEquals(Road.DIRT, c.map.roadAt(5, 2))
        assertEquals(Terrain.GRASS, c.map.terrainAt(5, 2))
    }

    @Test
    fun roadsSkipWaterAndExistingRoad() {
        val c = city()
        c.apply(Action.BuildRoad(Action.roadPath(c.map, 8, 4, 9, 4, true)))
        val plan = c.plan(Action.BuildRoad(Action.roadPath(c.map, 8, 4, 12, 4, true)))
        assertEquals(listOf(c.map.index(10, 4)), plan.blocked.toList())
        assertEquals(2, plan.changes.size) // 11 and 12; 8 and 9 are already road
    }

    @Test
    fun theLGoesTheWayItsTold() {
        val c = city()
        val across = Action.roadPath(c.map, 0, 0, 2, 2, acrossFirst = true)
        val down = Action.roadPath(c.map, 0, 0, 2, 2, acrossFirst = false)
        assertEquals(c.map.index(1, 0), across[1])
        assertEquals(c.map.index(0, 1), down[1])
        assertEquals(5, across.size)
    }

    @Test
    fun zonesLeaveRoadsAndWater() {
        val c = city()
        c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 3, 15, 3, true)))
        val plan = c.apply(Action.PlaceZone(8, 2, 11, 4, Zone.RESIDENTIAL))
        assertTrue(plan.ok)
        assertEquals(Zone.RESIDENTIAL, c.map.zoneAt(8, 2))
        assertEquals(Zone.NONE, c.map.zoneAt(8, 3)) // road
        assertEquals(Zone.NONE, c.map.zoneAt(10, 2)) // water
        assertEquals(12 - 4 - 3 + 1, plan.changes.size)
    }

    @Test
    fun notEnoughMoneyChangesNothing() {
        val c = City(2, 128, 128, TerrainOptions(water = 0, trees = 0, river = false))
        val before = c.map.hash()
        val plan = c.apply(Action.PlaceZone(0, 0, 127, 127, Zone.RESIDENTIAL))
        assertEquals(Problem.NotEnoughMoney, plan.problem)
        assertEquals(City.START_FUNDS, c.funds)
        assertEquals(before, c.map.hash())
    }

    @Test
    fun bulldozeClearsEverything() {
        val c = city()
        c.apply(Action.BuildRoad(Action.roadPath(c.map, 2, 6, 6, 6, true)))
        c.apply(Action.PlaceZone(2, 7, 6, 8, Zone.COMMERCIAL))
        val plan = c.apply(Action.Bulldoze(2, 5, 6, 8))
        assertTrue(plan.ok)
        for (y in 5..8) for (x in 2..6) {
            assertEquals(Road.NONE, c.map.roadAt(x, y))
            assertEquals(Zone.NONE, c.map.zoneAt(x, y))
            assertTrue(c.map.terrainAt(x, y) != Terrain.TREES)
        }
        assertEquals(Problem.NothingToDo, c.plan(Action.Bulldoze(2, 5, 6, 8)).problem)
    }

    @Test
    fun sameActionsSameCity() {
        fun run(): Long {
            val c = City(1900)
            c.apply(Action.BuildRoad(Action.roadPath(c.map, 40, 40, 80, 60, true)))
            c.apply(Action.PlaceZone(41, 41, 50, 50, Zone.RESIDENTIAL))
            c.apply(Action.Bulldoze(45, 45, 46, 46))
            return c.map.hash() * 31 + c.funds
        }
        assertEquals(run(), run())
    }
}
