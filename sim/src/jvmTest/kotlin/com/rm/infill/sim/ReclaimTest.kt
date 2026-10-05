package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReclaimTest {
    /** A flat town with a lake down its east side. */
    private fun town(): City {
        val c = City(5, 64, 64, TerrainOptions(water = 0, trees = 0, river = false))
        for (y in 0 until 64) for (x in 40 until 64) c.map.terrain[c.map.index(x, y)] = Terrain.WATER
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        return c
    }

    @Test
    fun waterIsFilledInAndGreensOver() {
        val c = town()
        val m = c.map
        val funds = c.funds
        val plan = c.apply(Action.FillWater(36, 10, 43, 12))
        assertTrue(plan.ok)
        // Only the water: 4 columns by 3 rows of it.
        assertEquals(12, plan.changes.size)
        assertEquals(funds - 12 * Prices.FILL_WATER, c.funds)
        assertEquals(Terrain.GRASS, m.terrain[m.index(41, 11)])
        assertEquals(Balance.FRESH_YEARS, m.fresh[m.index(41, 11)].toInt())
        // Undone and redone, it's fresh fill still.
        c.undo()
        assertEquals(Terrain.WATER, m.terrain[m.index(41, 11)])
        c.redo()
        assertEquals(Balance.FRESH_YEARS, m.fresh[m.index(41, 11)].toInt())
        // Ten years on it's like any other land.
        repeat(Balance.FRESH_YEARS * 365 + 31) { c.tick() }
        assertEquals(0, m.fresh[m.index(41, 11)].toInt())
    }

    @Test
    fun landIsDugOutAndUndone() {
        val c = town()
        val m = c.map
        c.apply(Action.PlaceZone(10, 10, 12, 12, Zone.RESIDENTIAL))
        val before = m.hash()
        val plan = c.apply(Action.DigWater(10, 10, 12, 12))
        assertTrue(plan.ok)
        assertEquals(Terrain.WATER, m.terrain[m.index(11, 11)])
        assertEquals(Zone.NONE, m.zone[m.index(11, 11)])
        c.undo()
        assertEquals(before, m.hash())
    }

    @Test
    fun aRoadOrBuildingStopsIt() {
        val c = town()
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 20, 30, 20, true))).ok)
        val plan = c.plan(Action.DigWater(5, 20, 8, 20))
        assertEquals(Problem.NothingToDo, plan.problem)
    }

    @Test
    fun freshLandIsSaved() {
        val c = town()
        c.apply(Action.FillWater(40, 5, 41, 5))
        val back = SaveGame.read(SaveGame.write(c))
        assertEquals(Balance.FRESH_YEARS, back.map.fresh[back.map.index(40, 5)].toInt())
        assertEquals(Terrain.GRASS, back.map.terrain[back.map.index(40, 5)])
    }
}
