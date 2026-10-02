package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DealsTest {
    private val land = TerrainOptions(water = 0, trees = 0, river = false)

    private fun City.money() = City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(this, 5_000_000L)

    private fun City.months(n: Int) = repeat(n) { repeat(31) { tick() } }

    private fun City.in1950() = City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(this, 1950)

    private fun Region.play(city: City) {
        city.neighbourSpare = neighboursOf(city)
        city.neighbours = city.neighbourSpare.map { it?.border }.toTypedArray()
        city.meetNeighbours()
    }

    /** A region of two: [west] built and recorded, then the east town to play, with [east] built. */
    private fun pair(seed: Long, west: (City) -> Unit, east: (City) -> Unit): Pair<Region, City> {
        val r = Region("Pair", seed, land, 2, 64)
        val w = r.found(0, "Westby", "pair")
        w.everything = true
        w.money()
        w.in1950()
        west(w)
        w.months(2)
        r.record(w, "pair-0")
        val e = r.found(1, "Eastby", "pair")
        e.everything = true
        e.money()
        e.in1950()
        r.play(e)
        east(e)
        return r to e
    }

    /** A station in the west town and a line to the east edge on row 20; police stations on a line in the east town, on row [row]. */
    private fun power(row: Int) = pair(
        11,
        { w ->
            assertTrue(w.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 1, 19)).ok)
            assertTrue(w.apply(Action.BuildPowerLine(Action.roadPath(w.map, 3, 20, 63, 20, true))).ok)
        },
        { e ->
            assertTrue(e.apply(Action.BuildPowerLine(Action.roadPath(e.map, 0, row, 40, row, true))).ok)
            for (x in listOf(10, 20, 30)) assertTrue(e.apply(Action.PlaceBuilding(BuildingType.POLICE_STATION, x, row + 1)).ok)
        },
    )

    @Test
    fun powerIsBoughtOverALineThatMeetsTheNeighbours() {
        val (r, linked) = power(20)
        val (_, apart) = power(24)
        linked.months(3)
        apart.months(3)
        assertTrue(linked.stats.powerIn > 0, "in ${linked.stats.powerIn}")
        assertTrue(linked.map.powered[linked.map.index(10, 21)])
        assertEquals(0, apart.stats.powerIn)
        assertTrue(!apart.map.powered[apart.map.index(10, 25)])
        // The region keeps what was agreed, and the west town sees it sold.
        r.record(linked, "pair-1")
        val back = Region.read(r.write())
        assertEquals(linked.edgePowerIn[Border.WEST], back.ledger.get(0, 1, Flow.POWER))
        assertTrue(back.ledger.get(0, 1, Flow.POWER) > 0)
        // And the town keeps it too, back with its neighbours.
        val saved = SaveGame.read(SaveGame.write(linked))
        assertEquals(linked.stats.neighbourCost, saved.stats.neighbourCost)
        assertContentEquals(linked.edgePowerIn, saved.edgePowerIn)
        back.play(saved)
        assertTrue(saved.map.powered[saved.map.index(10, 21)])
        assertTrue(saved.stats.powerIn > 0)
    }

    /** A pumping station on a lake in the west town and a main to the east edge on row 12; homes on a street with mains in the east, on row [row]. */
    private fun water(row: Int) = pair(
        12,
        { w ->
            val m = w.map
            for (y in 0 until 64) for (x in 0..3) m.terrain[m.index(x, y)] = Terrain.WATER
            assertTrue(w.apply(Action.PlaceBuilding(BuildingType.PUMPING_STATION, 4, 10)).ok)
            assertTrue(w.apply(Action.BuildPipe(Action.roadPath(m, 6, 12, 63, 12, true), Pipe.WATER)).ok)
        },
        { e ->
            assertTrue(e.apply(Action.BuildRoad(Action.roadPath(e.map, 0, row, 63, row, true), RoadType.STREET, pipes = true)).ok)
            assertTrue(e.apply(Action.PlaceZone(5, row + 1, 60, row + 3, Zone.RESIDENTIAL, Density.LOW)).ok)
        },
    )

    @Test
    fun waterIsBoughtOverAMainThatMeetsTheNeighbours() {
        val (r, linked) = water(12)
        val (_, apart) = water(16)
        linked.months(6)
        apart.months(6)
        assertTrue(linked.stats.waterIn > 0, "in ${linked.stats.waterIn}")
        assertTrue((0 until linked.map.size).any { linked.map.watered[it] })
        assertEquals(0, apart.stats.waterIn)
        assertTrue((0 until apart.map.size).none { apart.map.watered[it] })
        // Saved and played again, it makes the same deal.
        r.record(linked, "pair-1")
        val again = SaveGame.read(SaveGame.write(linked))
        Region.read(r.write()).play(again)
        assertTrue(again.stats.waterIn >= linked.stats.waterIn, "${again.stats.waterIn} against ${linked.stats.waterIn}")
        assertTrue(again.stats.neighbourCost > 0)
    }

    @Test
    fun theTownSellingGetsPaid() {
        val (r, buyer) = water(12)
        buyer.months(4)
        r.record(buyer, "pair-1")
        // Back to the west town, built the same: it sells what the east town agreed to.
        val seller = r.found(0, "Westby", "pair")
        seller.everything = true
        seller.money()
        seller.in1950()
        val m = seller.map
        for (y in 0 until 64) for (x in 0..3) m.terrain[m.index(x, y)] = Terrain.WATER
        assertTrue(seller.apply(Action.PlaceBuilding(BuildingType.PUMPING_STATION, 4, 10)).ok)
        assertTrue(seller.apply(Action.BuildPipe(Action.roadPath(m, 6, 12, 63, 12, true), Pipe.WATER)).ok)
        r.play(seller)
        seller.months(2)
        assertTrue(seller.stats.waterOut > 0, "out ${seller.stats.waterOut}")
        assertTrue(seller.stats.neighbourIncome > 0)
    }

    /** A dump in the west town on a street along row 30; homes in the east town on a street on row [row], with no dump. */
    private fun garbage(row: Int) = pair(
        13,
        { w ->
            assertTrue(w.apply(Action.BuildRoad(Action.roadPath(w.map, 0, 30, 63, 30, true), RoadType.STREET)).ok)
            assertTrue(w.apply(Action.PlaceBuilding(BuildingType.DUMP, 30, 31)).ok)
        },
        { e ->
            e.garbageTown = 0
            assertTrue(e.apply(Action.BuildRoad(Action.roadPath(e.map, 0, row, 63, row, true), RoadType.STREET)).ok)
            assertTrue(e.apply(Action.PlaceZone(5, row + 1, 60, row + 3, Zone.RESIDENTIAL, Density.LOW)).ok)
        },
    )

    @Test
    fun garbageGoesToTheNeighboursDumpForAFee() {
        val (r, linked) = garbage(30)
        val (_, apart) = garbage(36)
        assertTrue(r.towns[0]!!.dumpRoom > 0)
        linked.months(8)
        apart.months(8)
        assertTrue(linked.stats.garbageOut > 0, "out ${linked.stats.garbageOut}")
        assertTrue(linked.stats.wasteCollected > apart.stats.wasteCollected, "${linked.stats.wasteCollected} against ${apart.stats.wasteCollected}")
        assertTrue(linked.stats.neighbourCost > 0)
        assertEquals(0, apart.stats.garbageOut)
    }

    @Test
    fun smogPollutionAndNoiseDriftOverTheBorder() {
        val (r, east) = pair(14, { w -> }, { })
        r.towns[0]!!.smog = 200
        r.towns[0]!!.borders[Border.EAST].pollution.fill(200.toByte())
        r.towns[0]!!.borders[Border.EAST].noise.fill(150.toByte())
        r.play(east)
        east.months(1)
        assertTrue(east.stats.smog >= 200 * Balance.SMOG_DRIFT / 100, "smog ${east.stats.smog}")
        val m = east.map
        val p = (0 until 10).map { m.pollution[m.index(it, 30)].toInt() and 0xff }
        val n = (0 until 10).map { m.noise[m.index(it, 30)].toInt() and 0xff }
        // Strongest at the border, fading, gone a few tiles in.
        assertTrue(p[0] > p[3] && p[3] > 0 && p[Balance.NUISANCE_REACH] == 0, "$p")
        assertTrue(n[0] > n[3] && n[3] > 0 && n[Balance.NUISANCE_REACH] == 0, "$n")
    }

    @Test
    fun foulWaterComesDownTheRiver() {
        val (r, east) = pair(15, { w -> }, { })
        r.towns[0]!!.borders[Border.EAST].foul.fill(220.toByte())
        r.play(east)
        // A lake across the border.
        val m = east.map
        for (y in 20..40) for (x in 0..8) m.terrain[m.index(x, y)] = Terrain.WATER
        east.months(4)
        assertTrue((m.foul[m.index(0, 30)].toInt() and 0xff) > 0)
        assertEquals(0, m.foul[m.index(8, 10)].toInt())
    }
}
