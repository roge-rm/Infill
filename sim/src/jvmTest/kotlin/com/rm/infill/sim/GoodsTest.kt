package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoodsTest {
    private fun city(size: Int = 64, year: Int = 1920): City {
        val c = City(5, size, size, TerrainOptions(water = 0, trees = 0, river = false)).also { it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        c.map.resource.fill(Resource.NONE)
        return c
    }

    private fun City.i(x: Int, y: Int) = map.index(x, y)

    private fun City.road(x0: Int, y0: Int, x1: Int, y1: Int) =
        apply(Action.BuildRoad(Action.roadPath(map, x0, y0, x1, y1, true), RoadType.STREET))

    private fun City.all(): List<Building> = (0 until map.size).mapNotNull { building(map.building[it]) }.distinctBy { it.id }

    private fun City.month() = repeat(31) { tick() }

    @Test
    fun newMapsHaveSoilOreAndCoal() {
        val c = City(3, 128, 128)
        val r = c.map.resource
        for (kind in listOf(Resource.FERTILE, Resource.ORE, Resource.COAL)) assertTrue(r.any { it == kind }, "no $kind")
        assertTrue((0 until c.map.size).none { c.map.terrain[it] == Terrain.WATER && r[it] != Resource.NONE })
        // The same land as before there were resources.
        val plain = CityMap(128, 128)
        TerrainGen.generate(plain, 3)
        assertTrue(plain.terrain.contentEquals(c.map.terrain))
    }

    @Test
    fun anOldTownGetsSeamsAwayFromIt() {
        val c = SaveGame.read(javaClass.getResourceAsStream("/saves/v9.infill")!!.readBytes())
        val seams = (0 until c.map.size).filter { c.map.resource[it] == Resource.ORE || c.map.resource[it] == Resource.COAL }
        assertTrue(seams.isNotEmpty())
        assertTrue(seams.none { c.map.building[it] != 0 || c.map.zone[it] != Zone.NONE })
        // Its works each make something.
        assertTrue(c.all().filter { it.type.zone == Zone.INDUSTRIAL }.all { it.kind >= 0 })
    }

    @Test
    fun farmlandGrowsByWhatsUnderIt() {
        val c = city()
        c.road(0, 30, 63, 30)
        // Woods, an ore seam, a coal seam and good soil along the road.
        for (y in 26..29) {
            for (x in 2..9) c.map.terrain[c.i(x, y)] = Terrain.TREES
            for (x in 12..19) c.map.resource[c.i(x, y)] = Resource.ORE
            for (x in 22..29) c.map.resource[c.i(x, y)] = Resource.COAL
            for (x in 32..45) c.map.resource[c.i(x, y)] = Resource.FERTILE
        }
        // Homes and works for the farms to serve.
        c.apply(Action.PlaceZone(2, 31, 30, 33, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(32, 31, 60, 33, Zone.INDUSTRIAL))
        assertTrue(c.apply(Action.PlaceZone(2, 27, 45, 29, Zone.FARMLAND)).ok)
        repeat(36) { c.month() }
        fun on(x0: Int, x1: Int) = c.all().filter { it.type.zone == Zone.FARMLAND && it.x in x0..x1 }.map { it.type }.toSet()
        assertTrue(on(2, 9).all { it == BuildingType.WOODLOT }, "woods: ${on(2, 9)}")
        assertTrue(on(12, 18).all { it == BuildingType.MINE }, "ore: ${on(12, 18)}")
        assertTrue(on(22, 28).all { it == BuildingType.COLLIERY }, "coal: ${on(22, 28)}")
        assertTrue(on(32, 45).all { it == BuildingType.FARM }, "soil: ${on(32, 45)}")
        assertTrue(c.stats.farmJobs > 0)
        // Woodlots keep their trees.
        val woodlot = c.all().firstOrNull { it.type == BuildingType.WOODLOT }
        if (woodlot != null) assertEquals(Terrain.TREES, c.map.terrain[c.i(woodlot.x, woodlot.y)])
    }

    @Test
    fun goodsGoToTheNearestBuyerAndTheRestLeaveTown() {
        val c = city()
        c.road(0, 30, 63, 30)
        val n = c.map.size
        val t = Traffic(c.map)
        val goods = Array(Good.COUNT) { IntArray(n) }
        val wanted = Array(Good.COUNT) { IntArray(n) }
        goods[Good.TIMBER.ordinal][c.i(10, 30)] = 30
        wanted[Good.TIMBER.ordinal][c.i(20, 30)] = 12
        wanted[Good.TIMBER.ordinal][c.i(40, 30)] = 5
        t.newMonth(IntArray(n), IntArray(n), IntArray(n), IntArray(n), IntArray(n), 0, goodsAt = goods, wantedAt = wanted)
        t.sendDay(1, 1)
        t.newMonth(IntArray(n), IntArray(n), IntArray(n), IntArray(n), IntArray(n), 0)
        val g = Good.TIMBER.ordinal
        assertEquals(12, t.lastDelivered[g][c.i(20, 30)])
        assertEquals(5, t.lastDelivered[g][c.i(40, 30)])
        assertEquals(17, t.lastSold[g][c.i(10, 30)])
        assertEquals(13, t.lastExported[g][c.i(10, 30)], "the rest goes out")
        assertEquals(0, t.lastUnmet[g].sum())
        // The trucks are on the road.
        assertTrue(t.lastVolume[c.i(15, 30)] > 0)
    }

    @Test
    fun aCoalTownBurnsItsOwnCoalForLess() {
        fun town(colliery: Boolean): City {
            val c = city()
            c.road(0, 30, 63, 30)
            c.apply(Action.PlaceZone(5, 27, 40, 29, Zone.RESIDENTIAL))
            c.apply(Action.PlaceZone(5, 31, 30, 32, Zone.INDUSTRIAL))
            c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 50, 31))
            c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 50, 33, 5, 33, true)))
            c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 52, 31, 52, 26, false)))
            c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 52, 26, 5, 26, true)))
            if (colliery) {
                for (y in 27..29) for (x in 44..49) c.map.resource[c.i(x, y)] = Resource.COAL
                c.apply(Action.PlaceZone(44, 27, 49, 29, Zone.FARMLAND))
            }
            repeat(36) { c.month() }
            return c
        }
        val own = town(colliery = true)
        val bought = town(colliery = false)
        val station = own.all().first { it.type == BuildingType.COAL_PLANT }
        assertTrue(own.all().any { it.type == BuildingType.COLLIERY }, "a colliery on the seam")
        assertTrue(station.local > 0, "some coal from the colliery")
        assertEquals(0, bought.all().first { it.type == BuildingType.COAL_PLANT }.local)
        assertTrue(bought.stats.goodsImported[Good.COAL.ordinal] > 0)
        // Per megawatt, the town's own coal is cheaper.
        fun perMw(c: City): Double {
            val s = c.all().first { it.type == BuildingType.COAL_PLANT }
            return c.fuelCost(s) / (c.stationOutput(s) / 1e6)
        }
        assertTrue(perMw(own) < perMw(bought), "own ${perMw(own)}, bought ${perMw(bought)}")
    }

    @Test
    fun worksMakeWhatTheTownBringsIn() {
        val c = city(year = 1930)
        c.road(0, 30, 63, 30)
        c.apply(Action.PlaceZone(5, 27, 60, 29, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(5, 31, 60, 33, Zone.INDUSTRIAL))
        repeat(60) { c.month() }
        val kinds = c.all().mapNotNull { it.worksKind }.groupingBy { it }.eachCount()
        // Factories need lumber and metal, so sawmills and foundries follow.
        assertTrue(kinds.keys.size >= 2, "$kinds")
        assertTrue(c.stats.goodsMade.sum() > 0)
    }

    @Test
    fun goodsSurviveASave() {
        val c = city()
        c.road(0, 30, 63, 30)
        for (y in 26..29) for (x in 30..45) c.map.resource[c.i(x, y)] = Resource.COAL
        c.apply(Action.PlaceZone(5, 27, 28, 29, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(5, 31, 40, 33, Zone.INDUSTRIAL))
        c.apply(Action.PlaceZone(30, 26, 45, 29, Zone.FARMLAND))
        repeat(24) { c.month() }
        repeat(12) { c.tick() }
        val loaded = SaveGame.read(SaveGame.write(c)).also { it.disasterLevel = 0 }
        assertTrue(loaded.map.resource.contentEquals(c.map.resource))
        repeat(200) {
            c.tick()
            loaded.tick()
        }
        assertEquals(c.map.hash(), loaded.map.hash())
        assertEquals(c.funds, loaded.funds)
        assertTrue(c.stats.goodsMade.contentEquals(loaded.stats.goodsMade))
    }

    @Test
    fun shopsStockFromTownOrBringItIn() {
        fun town(edge: Boolean): City {
            val c = city()
            // A road to the edge, or one that stops short of it.
            if (edge) c.road(0, 30, 63, 30) else c.road(4, 30, 59, 30)
            // Without the road, people still come by train, but the train brings no goods.
            if (!edge) {
                c.everything = true
                c.apply(Action.BuildRail(Action.roadPath(c.map, 0, 40, 12, 40, true)))
                c.apply(Action.PlaceBuilding(BuildingType.STATION, 6, 41))
                c.everything = false
            }
            c.apply(Action.PlaceZone(6, 27, 57, 29, Zone.RESIDENTIAL))
            c.apply(Action.PlaceZone(6, 31, 30, 32, Zone.COMMERCIAL))
            c.apply(Action.PlaceZone(32, 31, 57, 32, Zone.INDUSTRIAL))
            repeat(36) { c.month() }
            return c
        }
        val open = town(edge = true)
        val shops = open.all().filter { it.type.zone == Zone.COMMERCIAL && it.underway == 0 }
        assertTrue(shops.isNotEmpty())
        // No farms: the food's all brought in, and comes by road.
        assertTrue(open.stats.goodsImported[Good.FOOD.ordinal] > 0)
        assertTrue(shops.none { open.shortOfStock(it) })
        assertTrue(open.stats.importValue > 0)
        // Cut off from the outside, what the town doesn't make can't be had.
        val shut = town(edge = false)
        assertTrue(shut.all().any { it.type.zone == Zone.COMMERCIAL && shut.shortOfStock(it) })
    }

    @Test
    fun oilFromTheWellsFuelsTheStation() {
        val c = city(year = 1925)
        // A township still: oil stations are the world's by now, if not yet the town's era.
        c.everything = true
        c.road(0, 30, 63, 30)
        for (y in 26..29) for (x in 40..52) c.map.resource[c.i(x, y)] = Resource.OIL
        c.apply(Action.PlaceZone(5, 27, 38, 29, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(5, 31, 30, 33, Zone.INDUSTRIAL))
        c.apply(Action.PlaceZone(40, 26, 52, 29, Zone.FARMLAND))
        c.apply(Action.PlaceBuilding(BuildingType.OIL_PLANT, 55, 31))
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 55, 33, 5, 33, true)))
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 54, 31, 54, 26, false)))
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 54, 26, 5, 26, true)))
        repeat(60) { c.month() }
        assertTrue(c.all().any { it.type == BuildingType.OIL_WELL }, "wells on the oil")
        assertTrue(c.all().any { it.worksKind == WorksKind.REFINERY }, "a refinery for the crude")
        val station = c.all().first { it.type == BuildingType.OIL_PLANT }
        assertTrue(station.local > 0, "fuel oil from the refinery")
    }
}
