package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnvironmentTest {
    private fun city(river: Int = -1, year: Int = 1950): City {
        val c = City(3, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true; it.disasterLevel = 0 }
        if (river >= 0) for (y in 0 until 64) for (x in river..river + 2) c.map.terrain[c.map.index(x, y)] = Terrain.WATER
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 10_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        return c
    }

    private fun City.i(x: Int, y: Int) = map.index(x, y)

    private fun City.road(x0: Int, y0: Int, x1: Int, y1: Int, type: RoadType = RoadType.STREET) =
        apply(Action.BuildRoad(Action.roadPath(map, x0, y0, x1, y1, true), type))

    private fun City.month() = repeat(31) { tick() }

    /** A street grid with homes and shops, powered from a coal station. */
    private fun town(c: City, dump: Boolean = false) {
        for (k in 20..44 step 6) {
            c.road(20, k, 44, k)
            c.road(k, 20, k, 44)
        }
        if (dump) c.apply(Action.PlaceBuilding(BuildingType.DUMP, 33, 33))
        c.apply(Action.PlaceZone(21, 21, 43, 37, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(21, 39, 31, 43, Zone.COMMERCIAL))
        c.apply(Action.PlaceZone(33, 39, 43, 43, Zone.INDUSTRIAL))
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 4, 4))
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 6, 6, 20, 20, true)))
    }

    @Test
    fun streetTreesCoolThePavedStreets() {
        val c = city()
        town(c)
        repeat(24) { c.month() }
        val at = c.i(32, 32)
        val before = c.map.heat[at].toInt() and 0xff
        assertTrue(before > 0, "a built-up street runs hot")
        val streets = (0 until c.map.size).filter { c.map.road[it] != Road.NONE }.toIntArray()
        val plan = c.apply(Action.PlantStreetTrees(streets))
        assertTrue(plan.ok)
        assertEquals(Prices.STREET_TREE * streets.size, plan.cost)
        c.month()
        val after = c.map.heat[at].toInt() and 0xff
        assertTrue(after < before, "heat went from $before to $after")
        // Planting them again does nothing; bulldozing a street takes its trees.
        assertEquals(Problem.NothingToDo, c.plan(Action.PlantStreetTrees(streets)).problem)
        c.apply(Action.Bulldoze(32, 20, 32, 20))
        assertEquals(0, c.map.streetTrees[c.i(32, 20)].toInt())
        // They save with the town.
        val loaded = SaveGame.read(SaveGame.write(c))
        assertEquals(1, loaded.map.streetTrees[c.i(26, 26)].toInt())
        assertEquals(after, loaded.map.heat[at].toInt() and 0xff)
    }

    @Test
    fun aCoalTownHasSmog() {
        val coal = city()
        town(coal)
        var worst = 0
        repeat(36) {
            coal.month()
            worst = maxOf(worst, coal.stats.smog)
        }
        assertTrue(worst > 0, "no smog")
    }

    @Test
    fun garbageNobodyTakesPilesUp() {
        fun run(dump: Boolean): City {
            val c = city()
            c.garbageTown = 0
            town(c, dump)
            repeat(24) { c.month() }
            return c
        }
        val none = run(false)
        assertTrue(none.stats.wasteCollected < 100, "collected ${none.stats.wasteCollected}%")
        assertTrue(none.homes.any { it.uncollected })
        val dumped = run(true)
        assertEquals(100, dumped.stats.wasteCollected)
        assertTrue(dumped.homes.none { it.uncollected })
        assertTrue(dumped.stats.dumpRoom < Balance.DUMP_ROOM / 1000, "the dump fills")
        // It costs to keep.
        assertTrue(dumped.stats.environmentUpkeep >= Balance.DUMP_UPKEEP.toLong())
    }

    @Test
    fun aSmallTownBurnsItsOwn() {
        val c = city()
        town(c)
        repeat(12) { c.month() }
        assertEquals(100, c.stats.wasteCollected)
        assertTrue(c.homes.none { it.uncollected })
    }

    @Test
    fun aHydroStationFloodsItsReservoir() {
        val c = city(river = 30)
        val land = (0 until c.map.size).count { c.map.terrain[it] != Terrain.WATER }
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.HYDRO_PLANT, 33, 20)).ok)
        val after = (0 until c.map.size).count { c.map.terrain[it] != Terrain.WATER && c.map.building[it] == 0 }
        assertTrue(after < land - 4, "land went from $land to $after")
        repeat(40) { c.tick() }
        val b = c.buildingAt(33, 20)!!
        assertTrue(c.stationAvailable(b) > 0)
        // Away from water it can't go.
        assertEquals(Problem.NeedsWater, c.plan(Action.PlaceBuilding(BuildingType.HYDRO_PLANT, 10, 10)).problem)
    }

    @Test
    fun aFullDumpSaysSo() {
        val c = city()
        c.garbageTown = 0
        town(c, dump = true)
        val dump = (0 until c.map.size).mapNotNull { c.building(c.map.building[it]) }.first { it.type == BuildingType.DUMP }
        dump.fill = Balance.DUMP_ROOM - Balance.DUMP_FULL - 1
        var told = 0
        repeat(6) { c.month(); c.takeEvents { if (it.kind == EventKind.DumpFull) told++ } }
        assertEquals(1, told, "said once")
    }

    /** The pollution at ([x], [y]) next month from a coal station at (20, 30), with [around] done to the land first. */
    private fun smokeAt(x: Int, y: Int, around: (City) -> Unit = {}): Int {
        val c = city()
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 20, 30))
        around(c)
        c.month()
        return c.map.pollution[c.i(x, y)].toInt() and 0xff
    }

    @Test
    fun parksAndWoodsSoakUpPollution() {
        val bare = smokeAt(23, 31)
        val green = smokeAt(23, 31) { c -> c.apply(Action.PlaceParks(23, 29, 25, 33)) }
        assertTrue(bare > 0)
        assertTrue(green < bare, "$green beside a park, $bare without")
        assertTrue(green >= bare * (100 - Balance.GREEN_SINK_MOST) / 100, "never more than half")
    }

    @Test
    fun aGreenBeltBlocksPollution() {
        // Across a belt of woods, from the station to the far side.
        val open = smokeAt(24, 31)
        val belt = smokeAt(24, 31) { c -> for (y in 25..36) c.map.terrain[c.i(22, y)] = Terrain.TREES }
        assertTrue(belt < open * 60 / 100, "$belt past the belt, $open in the open")
    }

    @Test
    fun scrubbersCleanAStationsSmoke() {
        val raw = smokeAt(20, 30)
        val c = city(year = 1975)
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 20, 30))
        val funds = c.funds
        assertTrue(c.apply(Action.FitScrubbers(21, 31)).ok)
        assertEquals(funds - Balance.SCRUBBER_PRICE, c.funds)
        assertTrue(c.buildingAt(20, 30)!!.scrubbed)
        c.undo()
        assertTrue(!c.buildingAt(20, 30)!!.scrubbed)
        c.redo()
        assertTrue(c.buildingAt(20, 30)!!.scrubbed)
        c.month()
        val clean = c.map.pollution[c.i(20, 30)].toInt() and 0xff
        assertTrue(clean < raw / 2, "$clean scrubbed, $raw not")
        // Fitted once, and kept in a save.
        assertEquals(Problem.NothingToDo, c.plan(Action.FitScrubbers(20, 30)).problem)
        assertTrue(SaveGame.read(SaveGame.write(c)).buildingAt(20, 30)!!.scrubbed)
        // Not before the 1970s, and only for coal and oil.
        val early = City(3, 64, 64, TerrainOptions(water = 0, trees = 0, river = false))
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(early, 100_000L)
        early.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 20, 30))
        assertTrue(!early.plan(Action.FitScrubbers(20, 30)).ok)
    }
}
