package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EdgeLinkTest {
    private fun town(link: Boolean): City {
        val c = City(3, 32, 32, TerrainOptions(water = 0, trees = 0, river = false))
        val road = Action.BuildRoad(Action.roadPath(c.map, 0, 10, 20, 10, true), link = link)
        assertTrue(c.reachesEdge(road))
        assertTrue(c.apply(road).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 8, 15, 9, Zone.RESIDENTIAL)).ok)
        c.tick()
        return c
    }

    @Test
    fun aRoadKeptInTownIsNoWayIn() {
        val kept = town(link = false)
        assertFalse(kept.map.leadsOut(kept.map.index(0, 10)))
        assertTrue(kept.needsWayIn())
        val linked = town(link = true)
        assertTrue(linked.map.leadsOut(linked.map.index(0, 10)))
        assertFalse(linked.needsWayIn())
    }

    @Test
    fun itCanBeLinkedLaterAndUndone() {
        val c = town(link = false)
        val i = c.map.index(0, 10)
        assertTrue(c.apply(Action.LinkOut(i, true)).ok)
        c.tick()
        assertTrue(c.map.leadsOut(i))
        assertFalse(c.needsWayIn())
        c.undo()
        assertFalse(c.map.leadsOut(i))
        // Only an edge road can be linked.
        assertFalse(c.plan(Action.LinkOut(c.map.index(5, 10), true)).ok)
        // A road laid along the edge already there isn't asked about again.
        assertFalse(c.reachesEdge(Action.BuildRoad(Action.roadPath(c.map, 0, 10, 3, 10, true))))
    }

    @Test
    fun theChoiceIsSaved() {
        val c = town(link = false)
        val back = SaveGame.read(SaveGame.write(c))
        assertEquals(0, back.map.unlinked.indices.count { back.map.leadsOut(it) })
        assertTrue(back.map.unlinked[back.map.index(0, 10)].toInt() == 1)
    }
}
