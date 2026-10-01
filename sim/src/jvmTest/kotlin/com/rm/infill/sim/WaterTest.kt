package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WaterTest {
    /** A city on grass with a river down column [river] if there is one. */
    private fun city(size: Int = 48, river: Int = -1): City {
        val c = City(1, size, size, TerrainOptions(water = 0, trees = 0, river = false))
        if (river >= 0) for (y in 0 until size) for (x in river..river + 2) c.map.terrain[c.map.index(x, y)] = Terrain.WATER
        val field = City::class.java.getDeclaredField("funds").apply { isAccessible = true }
        field.setLong(c, 1_000_000L)
        return c
    }

    private fun City.pipe(x0: Int, y0: Int, x1: Int, y1: Int, kind: Pipe = Pipe.WATER, acrossFirst: Boolean = true) =
        apply(Action.BuildPipe(Action.roadPath(map, x0, y0, x1, y1, acrossFirst), kind))

    private fun City.put(t: BuildingType, x: Int, y: Int) = apply(Action.PlaceBuilding(t, x, y))

    private fun City.i(x: Int, y: Int) = map.index(x, y)

    @Test
    fun pipesCostAndStayOffWater() {
        val c = city(river = 20)
        val funds = c.funds
        val plan = c.pipe(10, 5, 25, 5)
        assertTrue(plan.ok)
        assertEquals(13 * Pipe.WATER.price, plan.cost)
        assertEquals(3, plan.blocked.size)
        assertEquals(funds - plan.cost, c.funds)
        // Pipes go under roads, and roads over them.
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(c.map, 10, 5, 15, 5, true))).ok)
        assertEquals(1, c.map.waterPipe[c.i(12, 5)].toInt())
        // The bulldozer leaves them; taking them up doesn't.
        c.apply(Action.Bulldoze(10, 5, 15, 5))
        assertEquals(1, c.map.waterPipe[c.i(12, 5)].toInt())
        assertTrue(c.apply(Action.RemovePipes(10, 5, 15, 5)).ok)
        assertEquals(0, c.map.waterPipe[c.i(12, 5)].toInt())
        c.undo()
        assertEquals(1, c.map.waterPipe[c.i(12, 5)].toInt())
    }

    @Test
    fun aRoadCanTakeItsPipesWithIt() {
        val c = city()
        val plan = c.apply(Action.BuildRoad(Action.roadPath(c.map, 5, 5, 14, 5, true), RoadType.STREET, pipes = true))
        assertEquals(10 * (RoadType.STREET.price + Pipe.entries.sumOf { it.price }), plan.cost)
        for (layer in listOf(c.map.waterPipe, c.map.sewerPipe, c.map.stormPipe)) assertEquals(1, layer[c.i(9, 5)].toInt())
        // Pipes under an existing road without changing it.
        c.apply(Action.BuildRoad(Action.roadPath(c.map, 5, 8, 14, 8, true), RoadType.AVENUE))
        val again = c.apply(Action.BuildRoad(Action.roadPath(c.map, 5, 8, 14, 8, true), RoadType.AVENUE, pipes = true))
        assertTrue(again.ok)
        assertEquals(RoadType.AVENUE.id, c.map.road[c.i(9, 8)])
        assertEquals(1, c.map.sewerPipe[c.i(9, 8)].toInt())
    }

    @Test
    fun pumpsAndOutfallsGoBesideWater() {
        val c = city(river = 20)
        assertEquals(Problem.NeedsWater, c.plan(Action.PlaceBuilding(BuildingType.PUMPING_STATION, 5, 5)).problem)
        assertTrue(c.put(BuildingType.PUMPING_STATION, 18, 5).ok)
        assertEquals(Problem.NeedsWater, c.plan(Action.PlaceBuilding(BuildingType.OUTFALL, 5, 9)).problem)
        assertTrue(c.put(BuildingType.OUTFALL, 19, 9).ok)
        // A well field goes anywhere.
        assertTrue(c.put(BuildingType.WELL_FIELD, 5, 5).ok)
    }

    @Test
    fun mainsReachAsFarAsThePressure() {
        val c = city(64, river = 0)
        c.put(BuildingType.PUMPING_STATION, 3, 10)
        c.pipe(5, 12, 60, 12)
        c.put(BuildingType.POLICE_STATION, 20, 13)
        c.put(BuildingType.POLICE_STATION, 50, 13)
        c.tick()
        assertTrue(c.map.watered[c.i(20, 13)], "near the pump")
        assertFalse(c.map.watered[c.i(50, 13)], "past the pressure")
        // A tower on the way carries it further.
        c.put(BuildingType.WATER_TOWER, 30, 11)
        c.tick()
        assertTrue(c.map.watered[c.i(50, 13)], "past the tower")
    }

    @Test
    fun theSupplyRunsOutNearestFirst() {
        val c = city(64)
        c.put(BuildingType.WELL_FIELD, 2, 20)
        // Grime spoils the wells, so it supplies fewer than these stations' jobs.
        c.map.grime[c.i(2, 20)] = 255.toByte()
        c.pipe(4, 20, 60, 20)
        for (x in 6 until 36 step 2) {
            c.put(BuildingType.FIRE_STATION, x, 21)
            c.put(BuildingType.FIRE_STATION, x, 18)
        }
        c.tick()
        assertTrue(c.map.watered[c.i(6, 21)], "nearest")
        assertFalse(c.map.watered[c.i(32, 21)], "furthest, though the pressure reaches")
        assertTrue(c.stats.waterShort > 0)
        assertTrue(c.stats.waterSupply < Balance.WELL_SUPPLY / 2)
        assertTrue(c.stats.waterUsed <= c.stats.waterSupply)
    }

    /**
     * The growth test's town, with or without mains and sewers along its main
     * street. The sewer outfall is downstream of the pump, near it or far off.
     */
    private fun town(water: Boolean, outfallNear: Boolean = false): City {
        val c = city(64, river = 61)
        val m = c.map
        c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 60, 30, true), RoadType.STREET, pipes = water))
        c.apply(Action.PlaceZone(5, 28, 40, 29, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(5, 31, 18, 32, Zone.COMMERCIAL))
        c.apply(Action.PlaceZone(24, 31, 40, 32, Zone.INDUSTRIAL))
        c.put(BuildingType.COAL_PLANT, 50, 25)
        c.apply(Action.BuildPowerLine(Action.roadPath(m, 51, 27, 5, 27, true)))
        c.apply(Action.BuildPowerLine(Action.roadPath(m, 51, 27, 51, 33, true)))
        c.apply(Action.BuildPowerLine(Action.roadPath(m, 50, 33, 5, 33, true)))
        if (water) {
            c.put(BuildingType.PUMPING_STATION, 59, 26)
            c.pipe(58, 27, 58, 30, acrossFirst = false)
            if (outfallNear) {
                c.put(BuildingType.OUTFALL, 60, 32)
                c.pipe(60, 31, 60, 31, Pipe.SEWER)
            } else {
                c.put(BuildingType.OUTFALL, 60, 60)
                c.pipe(60, 31, 60, 59, Pipe.SEWER, acrossFirst = false)
            }
        }
        repeat(8 * 365) { c.tick() }
        return c
    }

    private fun City.upper(): Int = (0 until map.size).mapNotNull { building(map.building[it]) }.distinctBy { it.id }.count { it.type.stage >= 3 }

    @Test
    fun theBusiestBuildingsNeedMainsWater() {
        val dry = town(water = false)
        val wet = town(water = true)
        assertEquals(0, dry.upper(), "without mains")
        assertTrue(wet.upper() > 0, "with mains")
        assertTrue(wet.stats.population > dry.stats.population, "${wet.stats.population} vs ${dry.stats.population}")
    }

    @Test
    fun sewageFoulsTheRiverAndTheWaterDrawnFromIt() {
        val c = town(water = true, outfallNear = true)
        val near = c.i(61, 32)
        assertTrue((c.map.foul[near].toInt() and 0xff) > 0, "the river by the outfall is clean")
        // The pump is upstream only in name: it's within reach of the sewage, so it supplies less.
        assertTrue(c.stats.waterSupply < Balance.PUMP_SUPPLY, "supply ${c.stats.waterSupply}")
    }

    @Test
    fun hydrantsHelpTheFireHalls() {
        val c = city(river = 0)
        c.put(BuildingType.FIRE_STATION, 20, 20)
        // Near the edge of the fire hall's reach.
        c.put(BuildingType.POLICE_STATION, 31, 20)
        repeat(40) { c.tick() }
        val dry = c.fireCoverAt(c.i(31, 20))
        c.put(BuildingType.PUMPING_STATION, 3, 20)
        c.pipe(5, 21, 34, 21)
        c.tick()
        assertTrue(c.fireCoverAt(c.i(31, 20)) > dry, "$dry without hydrants")
    }

    /** A block paved end to end with avenues, on a map with a river down the west side. */
    private fun paved(): City {
        val c = city(48, river = 0)
        for (y in 10..30) c.apply(Action.BuildRoad(Action.roadPath(c.map, 10, y, 30, y, true), RoadType.AVENUE))
        return c
    }

    private fun City.flooded() = (0 until map.size).count { (map.flood[it].toInt() and 0xff) >= Balance.FLOODED }

    @Test
    fun pavingFloodsInHeavyRain() {
        val c = paved()
        c.rainfall(80, frozen = false)
        assertTrue(c.flooded() > 50, "flooded ${c.flooded()}")
        assertTrue(c.floodedTiles > 50)
        // Grass soaks it up.
        assertEquals(0, (c.map.flood[c.i(40, 40)].toInt() and 0xff))
        // It drains away over days.
        repeat(15) { c.tick() }
        assertEquals(0, c.flooded())
    }

    @Test
    fun stormDrainsToAnOutfallStopTheFlooding() {
        val c = paved()
        c.put(BuildingType.STORM_OUTFALL, 3, 20)
        for (y in 11..30 step 2) c.pipe(4, y, 30, y, Pipe.STORM)
        c.pipe(4, 11, 4, 30, Pipe.STORM, acrossFirst = false)
        c.rainfall(80, frozen = false)
        assertEquals(0, c.flooded())
    }

    @Test
    fun aPondHoldsOnlySoMuch() {
        val small = paved().also { it.put(BuildingType.STORM_POND, 31, 19); it.rainfall(80, frozen = false) }
        val none = paved().also { it.rainfall(80, frozen = false) }
        assertTrue(small.flooded() < none.flooded(), "${small.flooded()} with a pond, ${none.flooded()} without")
        assertTrue(small.flooded() > 0, "one pond shouldn't hold a whole paved block")
    }

    /** A built-up district: streets and solid buildings between them, nearly all hard surface. */
    private fun street(): City {
        val c = city(48, river = 0)
        for (y in 10..30 step 2) c.apply(Action.BuildRoad(Action.roadPath(c.map, 10, y, 30, y, true), RoadType.STREET))
        for (y in 11..29 step 2) for (x in 10..28 step 2) c.put(BuildingType.POLICE_STATION, x, y)
        return c
    }

    @Test
    fun steadyRainFloodsOnlySoakedGround() {
        val dry = street().also { it.ground = 0; it.rainfall(50, frozen = false) }
        val soaked = street().also { it.ground = 100; it.rainfall(50, frozen = false) }
        assertEquals(0, dry.flooded(), "steady rain on dry ground")
        assertTrue(soaked.flooded() > 0, "steady rain after weeks of it")
    }

    @Test
    fun frozenGroundShedsTheMelt() {
        val thawed = street().also { it.rainfall(60, frozen = false) }
        val frozen = street().also { it.rainfall(60, frozen = true) }
        assertTrue(frozen.flooded() > thawed.flooded(), "${frozen.flooded()} frozen, ${thawed.flooded()} thawed")
    }

    @Test
    fun theGroundSoaksAndDries() {
        val c = city()
        repeat(4) { c.wetGround(60, frozen = false, temperature = 10) }
        assertTrue(c.ground >= 70, "after four wet spells ${c.ground}")
        val soaked = c.ground
        c.wetGround(0, frozen = true, temperature = -5)
        assertEquals(soaked, c.ground, "frozen ground holds its water")
        c.wetGround(0, frozen = false, temperature = 20)
        assertTrue(c.ground < soaked)
    }

    @Test
    fun aSwollenRiverSpillsButNotPastAnEmbankment() {
        val c = city(48, river = 20)
        // An embankment down the east bank only.
        assertTrue(c.apply(Action.BuildBank(Action.roadPath(c.map, 23, 0, 23, 47, false))).ok)
        c.ground = 100
        repeat(4) { c.riseRivers(90) }
        assertTrue(c.river > Balance.BANKFULL)
        c.overflowRivers()
        assertTrue((c.map.flood[c.i(19, 20)].toInt() and 0xff) >= Balance.FLOODED, "the west bank")
        assertEquals(0, c.map.flood[c.i(24, 20)].toInt() and 0xff, "behind the embankment")
        // It goes down again.
        repeat(40) { c.tick() }
        assertTrue(c.river < Balance.BANKFULL)
    }

    @Test
    fun aWaterTownSavesAndCarriesOn() {
        val original = town(water = true)
        original.rainfall(80, frozen = false)
        val loaded = SaveGame.read(SaveGame.write(original))
        for (layer in listOf<(City) -> ByteArray>({ it.map.waterPipe }, { it.map.sewerPipe }, { it.map.stormPipe }, { it.map.foul }, { it.map.flood }, { it.map.bank })) {
            assertTrue(layer(original).contentEquals(layer(loaded)))
        }
        repeat(400) {
            original.tick()
            loaded.tick()
        }
        assertEquals(original.map.hash(), loaded.map.hash())
        assertEquals(original.funds, loaded.funds)
        assertTrue(original.map.foul.contentEquals(loaded.map.foul))
        assertEquals(original.ground, loaded.ground)
        assertEquals(original.river, loaded.river)
    }

    @Test
    fun aVersionThreeSaveStillLoads() {
        val c = SaveGame.read(javaClass.getResourceAsStream("/saves/v3.infill")!!.readBytes())
        assertEquals("Threeton", c.name)
        assertEquals(270, c.stats.population)
        assertEquals(48, c.map.rail.count { it != Rail.NONE })
        assertFalse(c.map.waterPipe.any { it.toInt() != 0 })
        repeat(70) { c.tick() }
        assertTrue(c.stats.population > 0)
    }
}
