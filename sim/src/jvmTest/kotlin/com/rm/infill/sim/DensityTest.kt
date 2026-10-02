package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DensityTest {
    /** A town on the east bank of a river, with power, mains water and sewers under every street. */
    private fun city(seed: Long = 9): City {
        val c = City(seed, 64, 64, TerrainOptions(water = 0, trees = 0, river = false))
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 2_000_000L)
        val m = c.map
        for (y in 0 until 64) for (x in 60..63) m.terrain[m.index(x, y)] = Terrain.WATER
        fun road(x0: Int, y0: Int, x1: Int, y1: Int) = c.apply(Action.BuildRoad(Action.roadPath(m, x0, y0, x1, y1, true), RoadType.STREET, pipes = true))
        for (y in listOf(10, 18, 26, 34, 42)) road(0, y, 56, y)
        road(56, 10, 56, 42)
        for (x in listOf(10, 20, 30, 40, 50)) road(x, 10, x, 42)
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 2, 50))
        c.apply(Action.BuildPowerLine(Action.roadPath(m, 3, 49, 3, 42, false)))
        for (y in listOf(10, 18, 26, 34, 42)) c.apply(Action.BuildPowerLine(Action.roadPath(m, 3, y, 56, y, true)))
        for (py in listOf(12, 16, 20, 24, 28, 36)) {
            c.apply(Action.PlaceBuilding(BuildingType.PUMPING_STATION, 58, py))
            c.apply(Action.BuildPipe(Action.roadPath(m, 57, py, 56, py, true), Pipe.WATER))
        }
        c.apply(Action.PlaceBuilding(BuildingType.OUTFALL, 59, 40))
        c.apply(Action.BuildPipe(Action.roadPath(m, 58, 40, 56, 40, true), Pipe.SEWER))
        for (tx in listOf(15, 35)) {
            c.apply(Action.PlaceBuilding(BuildingType.WATER_TOWER, tx, 8))
            c.apply(Action.BuildPipe(Action.roadPath(m, tx, 9, tx, 10, false), Pipe.WATER))
        }
        return c
    }

    private fun City.all(): List<Building> = (0 until map.size).mapNotNull { building(map.building[it]) }.distinctBy { it.id }

    private fun City.months(n: Int) = repeat(n) {
        val m = month
        while (month == m) tick()
    }

    /** Puts a town's jobs and homes in, all at [density], and runs it [years] years. */
    private fun town(density: Byte, years: Int, seed: Long = 9): City {
        val c = city(seed)
        c.apply(Action.PlaceZone(11, 11, 49, 25, Zone.RESIDENTIAL, density))
        c.apply(Action.PlaceZone(11, 27, 29, 33, Zone.COMMERCIAL, density))
        c.apply(Action.PlaceZone(31, 27, 49, 41, Zone.INDUSTRIAL, density))
        c.months(years * 12)
        return c
    }

    @Test
    fun lowDensityStopsAtLargeHouses() {
        val c = town(Density.LOW, 12)
        val types = c.all().map { it.type }.toSet()
        assertTrue(BuildingType.LARGE_HOUSE in types || BuildingType.HOUSE in types, "nothing grew: $types")
        for (t in types) if (t.zone != Zone.NONE) assertEquals(Density.LOW, t.density, "$t in a low density zone")
    }

    @Test
    fun denserZonesHoldMorePeople() {
        val low = town(Density.LOW, 12)
        val medium = town(Density.MEDIUM, 12)
        // Not by as much as the land alone would hold: the odd heat wave falls hardest on the denser, hotter blocks.
        assertTrue(medium.stats.population > low.stats.population * 6 / 5, "${medium.stats.population} at medium, ${low.stats.population} at low")
    }

    @Test
    fun cheapLandStaysLow() {
        val c = city()
        c.apply(Action.PlaceZone(11, 11, 49, 25, Zone.RESIDENTIAL, Density.HIGH))
        // Nothing to make the land dear: no shops and no jobs. Cap it low to be sure.
        c.months(6)
        val choices = City::class.java.getDeclaredMethod("choices", Building::class.java, Int::class.java, Byte::class.java, Int::class.java).apply { isAccessible = true }
        val house = c.all().first { it.type.zone == Zone.RESIDENTIAL && it.underway == 0 }
        val i = c.map.index(house.x, house.y)
        c.map.landValue[i] = 60
        c.map.watered[i] = true
        c.map.powered[i] = true
        house.type = BuildingType.LARGE_HOUSE
        @Suppress("UNCHECKED_CAST")
        val options = choices.invoke(c, house, i, Zone.RESIDENTIAL, 100) as List<BuildingType>
        assertTrue(options.isEmpty(), "row houses on land worth 60: $options")
        c.map.landValue[i] = 90
        @Suppress("UNCHECKED_CAST")
        val dearer = choices.invoke(c, house, i, Zone.RESIDENTIAL, 100) as List<BuildingType>
        assertEquals(listOf(BuildingType.ROW_HOUSES), dearer)
    }

    @Test
    fun buildingsGoUpAsSitesAndTakeLongerInWinter() {
        val c = city()
        c.apply(Action.PlaceZone(11, 11, 19, 17, Zone.RESIDENTIAL, Density.LOW))
        var sawSite = false
        repeat(60) {
            c.tick()
            if (c.all().any { it.underway > 0 }) sawSite = true
        }
        assertTrue(sawSite, "nothing went up as a site")
        val site = c.all().firstOrNull { it.underway > 0 }
        if (site != null) {
            assertEquals(null, site.people, "people living on a building site")
            assertTrue(c.map.site[c.map.index(site.x, site.y)] > 0)
        }
        // A cottage started in summer and one in a hard winter.
        fun daysToBuild(temperature: Int): Int {
            val t = city()
            t.apply(Action.PlaceZone(11, 11, 11, 11, Zone.RESIDENTIAL, Density.LOW))
            val weather = Weather::class.java.getDeclaredField("temperature").apply { isAccessible = true }
            val sites = City::class.java.getDeclaredField("sites").apply { isAccessible = true }.get(t) as LinkedHashSet<*>
            var days = 0
            while (t.all().none { it.type.zone == Zone.RESIDENTIAL } && days < 400) { t.tick(); days++ }
            val b = t.all().first { it.type.zone == Zone.RESIDENTIAL }
            assertTrue(b.id in sites)
            var built = 0
            while (b.underway > 0 && built < 400) {
                weather.setInt(t.weather, temperature)
                t.tick()
                built++
            }
            return built
        }
        val summer = daysToBuild(20)
        val winter = daysToBuild(-10)
        assertTrue(summer in BuildingType.COTTAGE.buildDays - 2..BuildingType.COTTAGE.buildDays + 2, "a cottage took $summer days in summer")
        assertTrue(winter > summer * 3 / 2, "$winter days in winter, $summer in summer")
    }

    @Test
    fun rebuildingSendsThePeopleAwayAndBringsNewOnes() {
        val c = town(Density.MEDIUM, 2)
        val home = c.all().first { it.type == BuildingType.COTTAGE && it.underway == 0 && it.people?.empty == false }
        val rebuild = City::class.java.getDeclaredMethod("rebuild", Building::class.java, BuildingType::class.java).apply { isAccessible = true }
        rebuild.invoke(c, home, BuildingType.HOUSE)
        assertEquals(null, home.people)
        assertEquals(BuildingType.HOUSE.buildDays, home.underway)
        repeat(BuildingType.HOUSE.buildDays * 2 + 2) { c.tick() }
        assertEquals(0, home.underway)
        assertEquals(BuildingType.HOUSE.capacity, home.people?.size, "the new house filled")
    }

    @Test
    fun fourLotsMakeABigBuilding() {
        val c = city()
        val m = c.map
        c.apply(Action.PlaceZone(11, 11, 19, 17, Zone.COMMERCIAL, Density.HIGH))
        c.months(3)
        val assemble = City::class.java.getDeclaredMethod("assemble", BuildingType::class.java, Int::class.java).apply { isAccessible = true }
        val before = c.all().count { it.type.zone == Zone.COMMERCIAL && it.x in 11..12 && it.y in 11..12 }
        assemble.invoke(c, BuildingType.DEPARTMENT_STORE, m.index(11, 11))
        val store = c.buildingAt(11, 11)!!
        assertEquals(BuildingType.DEPARTMENT_STORE, store.type)
        for (y in 11..12) for (x in 11..12) assertEquals(store.id, m.building[m.index(x, y)], "lot $x, $y")
        assertTrue(store.underway > 0)
        assertTrue(before >= 0)
        // It comes back down to one lot.
        store.underway = 0
        val shrink = City::class.java.getDeclaredMethod("shrink", Building::class.java).apply { isAccessible = true }
        shrink.invoke(c, store)
        val after = c.buildingAt(11, 11)!!
        assertEquals(BuildingType.OFFICE_BLOCK, after.type)
        assertEquals(0, m.building[m.index(12, 12)])
    }

    @Test
    fun rezoningKeepsWhatsThere() {
        val c = town(Density.MEDIUM, 6)
        val tall = c.all().firstOrNull { it.type == BuildingType.TENEMENT || it.type == BuildingType.ROW_HOUSES }
        assertTrue(tall != null, "nothing medium grew: ${c.all().map { it.type }.toSet()}")
        val plan = c.apply(Action.PlaceZone(tall.x, tall.y, tall.x, tall.y, Zone.RESIDENTIAL, Density.LOW))
        assertTrue(plan.ok)
        assertEquals(tall.id, c.map.building[c.map.index(tall.x, tall.y)])
        assertEquals(Density.LOW, c.map.density[c.map.index(tall.x, tall.y)])
        c.undo()
        assertEquals(Density.MEDIUM, c.map.density[c.map.index(tall.x, tall.y)])
    }

    @Test
    fun sitesAndDensitiesSurviveASave() {
        val c = town(Density.HIGH, 3)
        // Somewhere mid-build.
        while (c.all().none { it.underway > 0 }) c.tick()
        val loaded = SaveGame.read(SaveGame.write(c))
        assertTrue(c.map.density.contentEquals(loaded.map.density))
        for (b in c.all()) assertEquals(b.underway, loaded.building(b.id)!!.underway, "${b.type} at ${b.x}, ${b.y}")
        repeat(200) { c.tick(); loaded.tick() }
        assertEquals(c.map.hash(), loaded.map.hash())
        assertEquals(c.stats.population, loaded.stats.population)
    }

    @Test
    fun aVersionFiveSaveIsMediumDensity() {
        val c = SaveGame.read(javaClass.getResourceAsStream("/saves/v5.infill")!!.readBytes())
        assertEquals("Fivecorners", c.name)
        assertEquals(235, c.stats.population)
        for (i in 0 until c.map.size) {
            assertEquals(if (c.map.zone[i] == Zone.NONE) Density.NONE else Density.MEDIUM, c.map.density[i])
        }
        assertFalse(c.all().any { it.underway > 0 })
        c.months(2)
        assertTrue(c.stats.population > 0)
    }
}
