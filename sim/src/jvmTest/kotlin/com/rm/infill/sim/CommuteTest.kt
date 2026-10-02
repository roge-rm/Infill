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

class LedgerTest {
    private val land = TerrainOptions(water = 0, trees = 0, river = false)

    private fun City.money() = City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(this, 5_000_000L)

    private fun City.months(n: Int) = repeat(n) { repeat(31) { tick() } }

    private fun Region.play(square: Int, city: City) {
        city.neighbourSpare = neighboursOf(city)
        city.neighbours = city.neighbourSpare.map { it?.border }.toTypedArray()
        city.meetNeighbours()
    }

    /** A works town in the middle of three, on a street running across all three. */
    private fun street(c: City) {
        c.everything = true
        c.money()
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 30, 63, 30, true), RoadType.STREET)).ok)
    }

    @Test
    fun twoTownsCantTakeTheSameJobs() {
        val r = Region("Row", 5, land, 3, 64)
        // The middle town: works, no homes.
        val mid = r.found(4, "Middle", "row")
        street(mid)
        assertTrue(mid.apply(Action.PlaceZone(5, 26, 60, 29, Zone.INDUSTRIAL, Density.LOW)).ok)
        mid.months(6)
        val going = mid.stats.vacant
        assertTrue(going > 0)
        r.record(mid, "row-4")
        // A homes town to the west takes what it can.
        val west = r.found(3, "West", "row")
        street(west)
        r.play(3, west)
        assertTrue(west.apply(Action.PlaceZone(1, 31, 60, 34, Zone.RESIDENTIAL, Density.LOW)).ok)
        west.months(18)
        val tookWest = west.stats.commutersOut
        assertTrue(tookWest > 0)
        r.record(west, "row-3")
        assertEquals(tookWest, r.ledger.get(3, 4, Flow.COMMUTERS))
        // The middle town's jobs going are fewer by that much.
        assertEquals(going - tookWest, r.towns[4]!!.vacant)
        // Then one to the east only gets what's left.
        val east = r.found(5, "East", "row")
        street(east)
        r.play(5, east)
        assertTrue(east.apply(Action.PlaceZone(1, 31, 60, 34, Zone.RESIDENTIAL, Density.LOW)).ok)
        east.months(18)
        assertTrue(east.stats.commutersOut <= going - tookWest, "east ${east.stats.commutersOut}, left ${going - tookWest}")
        r.record(east, "row-5")
        assertEquals(0, r.towns[4]!!.vacant.coerceAtMost(going) - (going - tookWest - east.stats.commutersOut))
    }

    @Test
    fun aTownPlayedAgainStartsWithWhatItsNeighboursAgreed() {
        val r = Region("Pair", 6, land, 2, 64)
        val works = r.found(0, "Works", "pair")
        street(works)
        assertTrue(works.apply(Action.PlaceZone(5, 26, 60, 29, Zone.INDUSTRIAL, Density.LOW)).ok)
        works.months(6)
        r.record(works, "pair-0")
        val homes = r.found(1, "Homes", "pair")
        street(homes)
        r.play(1, homes)
        assertTrue(homes.apply(Action.PlaceZone(1, 31, 60, 34, Zone.RESIDENTIAL, Density.LOW)).ok)
        homes.months(18)
        val coming = homes.stats.commutersOut
        assertTrue(coming > 0)
        r.record(homes, "pair-1")
        // Back to the works town, saved and loaded: the homes town's workers come in from the start.
        val again = SaveGame.read(SaveGame.write(works))
        val back = Region.read(r.write())
        assertEquals(coming, back.ledger.get(1, 0, Flow.COMMUTERS))
        back.play(0, again)
        // At least those agreed, and no more than the homes town had to spare besides.
        val spare = back.towns[1]!!.idle
        assertTrue(again.stats.commutersIn in coming..coming + spare, "${again.stats.commutersIn}, agreed $coming, spare $spare")
        // And it keeps them as the months go by, as long as it has the jobs.
        again.months(2)
        assertTrue(again.stats.commutersIn >= coming)
    }
}
