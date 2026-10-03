package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TradeTest {
    private val land = TerrainOptions(water = 0, trees = 0, river = false)

    private fun City.months(n: Int) = repeat(n) { repeat(31) { tick() } }

    /** A region of two with an empty neighbour on the west recorded, and the east town on a street meeting it, or not. */
    private fun pair(meet: Boolean, setUp: (RegionTown) -> Unit): Pair<Region, City> {
        val r = Region("Trade", 9, land, 2, 64)
        val west = r.found(0, "West", "trade")
        west.everything = true
        assertTrue(west.apply(Action.BuildRoad(Action.roadPath(west.map, 0, 30, 63, 30, true), RoadType.STREET)).ok)
        r.record(west, "trade-0")
        setUp(r.towns[0]!!)
        val east = r.found(1, "East", "trade")
        east.everything = true
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(east, 5_000_000L)
        val y = if (meet) 30 else 36
        assertTrue(east.apply(Action.BuildRoad(Action.roadPath(east.map, 0, y, 63, y, true), RoadType.STREET)).ok)
        east.neighbourSpare = r.neighboursOf(east)
        east.neighbours = east.neighbourSpare.map { it?.border }.toTypedArray()
        return r to east
    }

    @Test
    fun aTownShortOfShopsShopsNextDoor() {
        fun town(meet: Boolean): City {
            val (_, c) = pair(meet) { it.shopsSpare = 200 }
            assertTrue(c.apply(Action.PlaceZone(1, 31, 60, 35, Zone.RESIDENTIAL, Density.LOW)).ok)
            assertTrue(c.apply(Action.PlaceZone(1, 26, 60, 29, Zone.INDUSTRIAL, Density.LOW)).ok)
            c.months(12)
            return c
        }
        val linked = town(true)
        val apart = town(false)
        assertTrue(linked.stats.shoppingOut > 0, "shopping out ${linked.stats.shoppingOut}")
        assertEquals(0, apart.stats.shoppingOut)
        // Shops next door: fewer wanted here, by what's spent there.
        val out = linked.demandParts().first { it.zone == Zone.COMMERCIAL }.parts.firstOrNull { it.source == DemandSource.SHOPPERS_OUT }
        assertTrue(out != null && out.amount < 0, "shopping next door takes from the shops wanted: ${out?.amount}")
        assertTrue(apart.demandParts().first { it.zone == Zone.COMMERCIAL }.parts.none { it.source == DemandSource.SHOPPERS_OUT })
    }

    @Test
    fun goodsNextDoorIsShortOfAreSoldToItAndWantMoreWorks() {
        fun town(meet: Boolean): City {
            val (_, c) = pair(meet) { t -> for (g in 0 until Good.COUNT) t.goodsShort[g] = 300 }
            assertTrue(c.apply(Action.PlaceZone(1, 31, 60, 35, Zone.RESIDENTIAL, Density.LOW)).ok)
            assertTrue(c.apply(Action.PlaceZone(1, 26, 60, 29, Zone.INDUSTRIAL, Density.LOW)).ok)
            c.months(12)
            return c
        }
        val linked = town(true)
        val apart = town(false)
        assertTrue(linked.stats.toNeighbours.sum() > 0, "to neighbours ${linked.stats.toNeighbours.toList()}")
        assertEquals(0, apart.stats.toNeighbours.sum())
        // The sales next door have grown more works.
        assertTrue(linked.stats.industryJobs + linked.stats.industryJobsComing > apart.stats.industryJobs + apart.stats.industryJobsComing,
            "linked ${linked.stats.industryJobs}+${linked.stats.industryJobsComing}, apart ${apart.stats.industryJobs}+${apart.stats.industryJobsComing}")
    }

    @Test
    fun goodsNextDoorHasSpareComeInBeforeImportsAndCostLess() {
        val (r, c) = pair(true) { t -> for (g in 0 until Good.COUNT) t.goodsSpare[g] = 300 }
        assertTrue(c.apply(Action.PlaceZone(1, 31, 60, 35, Zone.RESIDENTIAL, Density.LOW)).ok)
        assertTrue(c.apply(Action.PlaceZone(1, 26, 60, 29, Zone.COMMERCIAL, Density.LOW)).ok)
        c.months(12)
        val from = c.stats.fromNeighbours.sum()
        assertTrue(from > 0, "from neighbours ${c.stats.fromNeighbours.toList()}, imported ${c.stats.goodsImported.toList()}")
        // Recorded: the west town's spare is less what came here, and the ledger has it.
        r.record(c, "trade-1")
        val g = (0 until Good.COUNT).first { c.stats.fromNeighbours[it] > 0 }
        assertEquals(c.stats.fromNeighbours[g], r.ledger.get(0, 1, Flow.goods(g)))
        assertEquals(300 - c.stats.fromNeighbours[g], r.towns[0]!!.goodsSpare[g])
        // And saved, both.
        val back = Region.read(r.write())
        assertEquals(c.stats.fromNeighbours[g], back.ledger.get(0, 1, Flow.goods(g)))
        val saved = SaveGame.read(SaveGame.write(c))
        assertEquals(c.stats.fromNeighbours.toList(), saved.stats.fromNeighbours.toList())
    }
}
