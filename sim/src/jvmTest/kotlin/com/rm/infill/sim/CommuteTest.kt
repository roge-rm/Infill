package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommuteTest {
    private val land = TerrainOptions(water = 0, trees = 0, river = false)

    private fun City.money() = City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(this, 5_000_000L)

    private fun City.months(n: Int) = repeat(n) { repeat(31) { tick() } }

    /** A region of two: a works town on the west with jobs going and no one to fill them, recorded; the east town to play. */
    private fun region(meet: Boolean): Pair<Region, City> {
        val r = Region("Pair", 3, land, 2, 64)
        val west = r.found(0, "Millbank", "pair")
        west.everything = true
        west.money()
        val m = west.map
        assertTrue(west.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 63, 30, true), RoadType.STREET)).ok)
        assertTrue(west.apply(Action.PlaceZone(5, 26, 60, 29, Zone.INDUSTRIAL, Density.LOW)).ok)
        west.months(6)
        // Jobs and no homes: all of them going.
        assertTrue(west.stats.vacant > 0, "vacant ${west.stats.vacant}")
        r.record(west, "pair-0")
        val east = r.found(1, "Eastholm", "pair")
        east.everything = true
        east.money()
        east.neighbourSpare = r.neighboursOf(east)
        east.neighbours = east.neighbourSpare.map { it?.border }.toTypedArray()
        val e = east.map
        // A street from the west edge, at the same row as the works town's, or a few rows off.
        val y = if (meet) 30 else 36
        assertTrue(east.apply(Action.BuildRoad(Action.roadPath(e, 0, y, 63, y, true), RoadType.STREET)).ok)
        assertTrue(east.apply(Action.PlaceZone(5, y + 1, 60, y + 3, Zone.RESIDENTIAL, Density.LOW)).ok)
        assertTrue(east.apply(Action.PlaceZone(5, y - 2, 20, y - 1, Zone.COMMERCIAL, Density.LOW)).ok)
        return r to east
    }

    @Test
    fun aRoadMeetingTheNeighboursIsALinkAndOneThatDoesntIsNot() {
        val (_, linked) = region(meet = true)
        assertEquals(1, linked.links().size)
        val (_, apart) = region(meet = false)
        assertTrue(apart.links().isEmpty())
    }

    @Test
    fun peopleCommuteToTheJobsNextDoorAndMoreHomesAreWanted() {
        val (_, linked) = region(meet = true)
        val (_, apart) = region(meet = false)
        linked.months(18)
        apart.months(18)
        assertTrue(linked.stats.commutersOut > 0, "out ${linked.stats.commutersOut}")
        assertEquals(0, apart.stats.commutersOut)
        // The jobs over the border bring more people to live here.
        assertTrue(linked.stats.population > apart.stats.population, "linked ${linked.stats.population}, apart ${apart.stats.population}")
    }

    @Test
    fun whatsToSpareIsKeptInTheRegionAndTheCommutersInTheTown() {
        val (r, east) = region(meet = true)
        east.months(12)
        r.record(east, "pair-1")
        val back = Region.read(r.write())
        assertEquals(east.stats.idle, back.towns[1]!!.idle)
        assertEquals(east.stats.vacant, back.towns[1]!!.vacant)
        val saved = SaveGame.read(SaveGame.write(east))
        assertEquals(east.stats.commutersOut, saved.stats.commutersOut)
    }
}
