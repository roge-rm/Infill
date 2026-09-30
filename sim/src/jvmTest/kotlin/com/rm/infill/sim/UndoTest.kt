package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UndoTest {
    @Test
    fun undoPutsEverythingBack() {
        val c = City(1900)
        val hash = c.map.hash()
        c.apply(Action.BuildRoad(Action.roadPath(c.map, 30, 30, 70, 50, true)))
        c.apply(Action.PlaceZone(31, 31, 40, 40, Zone.RESIDENTIAL))
        c.apply(Action.Bulldoze(35, 30, 36, 45))
        repeat(3) { c.undo() }
        assertEquals(hash, c.map.hash())
        assertEquals(City.START_FUNDS, c.funds)
        assertFalse(c.canUndo)
        assertNull(c.undo())
    }

    @Test
    fun redoDoesItAgain() {
        val c = City(1900)
        c.apply(Action.BuildRoad(Action.roadPath(c.map, 30, 30, 70, 50, true)))
        c.apply(Action.PlaceZone(31, 31, 40, 40, Zone.COMMERCIAL))
        val hash = c.map.hash()
        val funds = c.funds
        c.undo()
        c.undo()
        assertTrue(c.redo()!!.ok)
        assertTrue(c.redo()!!.ok)
        assertEquals(hash, c.map.hash())
        assertEquals(funds, c.funds)
        assertFalse(c.canRedo)
    }

    @Test
    fun aNewActionForgetsWhatWasUndone() {
        val c = City(1900)
        c.apply(Action.PlaceZone(10, 10, 12, 12, Zone.INDUSTRIAL))
        c.undo()
        assertTrue(c.canRedo)
        c.apply(Action.PlaceZone(20, 20, 22, 22, Zone.INDUSTRIAL))
        assertFalse(c.canRedo)
    }

    @Test
    fun historyIsLimited() {
        val c = City(1900, terrain = TerrainOptions(water = 0, trees = 0, river = false))
        repeat(City.MAX_UNDO + 20) { c.apply(Action.PlaceZone(it % 128, 0, it % 128, 0, (1 + it % 3).toByte())) }
        var undone = 0
        while (c.undo() != null) undone++
        assertEquals(City.MAX_UNDO, undone)
    }
}
