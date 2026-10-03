package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenewablesTest {
    @Test
    fun windFollowsTheWindAndStopsInAGale() {
        assertEquals(0, Generation.windShare(5))
        assertTrue(Generation.windShare(40) in 1..99)
        assertEquals(100, Generation.windShare(70))
        assertEquals(0, Generation.windShare(Weather.GALE + 2))
    }

    @Test
    fun solarFollowsTheSunAndTheCloud() {
        // More in summer than winter, less under cloud, and none at a winter evening's peak.
        assertTrue(Generation.solarDay(6, 0) > Generation.solarDay(0, 0))
        assertTrue(Generation.solarDay(6, 100) < Generation.solarDay(6, 0))
        assertEquals(0, Generation.solarPeak(0, 0))
        assertTrue(Generation.solarPeak(6, 0) > 0)
    }

    /** A town of homes along a street in [year], with [stations] wired in. */
    private fun town(year: Int, vararg stations: BuildingType): City {
        val c = City(12, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true; it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        val m = c.map
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 60, 30, true))).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 28, 40, 29, Zone.RESIDENTIAL)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 2, 27, 60, 27, true))).ok)
        var x = 44
        for (t in stations) {
            assertTrue(c.apply(Action.PlaceBuilding(t, x, 24 - t.height + 1 + 2)).ok, "$t")
            x += t.width + 1
        }
        return c
    }

    @Test
    fun aWindFarmMakesWhatTheWindAllows() {
        val c = town(2010, BuildingType.WIND_FARM)
        repeat(40) { c.tick() }
        val farm = c.buildingAt(44, 25)!!
        val expected = Generation.capacity(BuildingType.WIND_FARM).toLong() * Generation.windShare(c.weather.windSpeed) / 100
        assertEquals(expected.toInt(), c.stationAvailable(farm))
    }

    @Test
    fun batteriesKeepWhatTheDayLeftOver() {
        // A big solar farm and wind farm and a small town: plenty spare by day, kept for the evening.
        val c = town(2035, BuildingType.SOLAR_FARM, BuildingType.WIND_FARM, BuildingType.BATTERY)
        City::class.java.getDeclaredField("month").apply { isAccessible = true }.setInt(c, 6)
        repeat(40) { c.tick() }
        val battery = c.buildingAt(52, 25) ?: c.buildingAt(52, 26)!!
        assertEquals(BuildingType.BATTERY, battery.type)
        assertTrue(c.stationAvailable(battery) > 0, "the battery has something for the evening")
        // With nothing to charge it, it has nothing.
        val dark = town(2035, BuildingType.BATTERY)
        repeat(40) { dark.tick() }
        val empty = (0 until dark.map.size).mapNotNull { dark.building(dark.map.building[it]) }.first { it.type == BuildingType.BATTERY }
        assertEquals(0, dark.stationAvailable(empty))
    }

    @Test
    fun aWindFarmHumsAndTakesALittleOffTheLandNearIt() {
        val c = town(2010, BuildingType.WIND_FARM)
        repeat(40) { c.tick() }
        val near = c.map.landValue[c.map.index(43, 25)].toInt() and 0xff
        val far = c.map.landValue[c.map.index(43, 10)].toInt() and 0xff
        assertTrue(near < far + Balance.WIND_VALUE, "near $near, far $far")
    }

    /** An empty map in [year] with a river down x 30 to 32 from edge to edge, and a lake in the middle of the land. */
    private fun watery(year: Int): City {
        val c = City(13, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true; it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        val m = c.map
        for (y in 0 until 64) for (x in 30..32) m.terrain[m.index(x, y)] = Terrain.WATER
        for (y in 20..26) for (x in 8..16) m.terrain[m.index(x, y)] = Terrain.WATER
        return c
    }

    private fun City.can(t: BuildingType, x: Int, y: Int) = plan(Action.PlaceBuilding(t, x, y)).ok

    @Test
    fun turbinesOnTheWaterGoWhereTheirWaterIs() {
        val c = watery(2015)
        // A river turbine goes in the river's current, and can't go on land or in still water.
        assertTrue(c.can(BuildingType.RIVER_TURBINE, 31, 30))
        assertTrue(!c.can(BuildingType.RIVER_TURBINE, 40, 30))
        assertTrue(!c.can(BuildingType.RIVER_TURBINE, 12, 22))
        // Tidal turbines only near the edge, where the tide comes in.
        assertTrue(c.can(BuildingType.TIDAL_TURBINE, 30, 2))
        assertTrue(!c.can(BuildingType.TIDAL_TURBINE, 30, 32))
        // Offshore wind on any open water, all of it on the water.
        assertTrue(c.can(BuildingType.OFFSHORE_WIND, 10, 22))
        assertTrue(!c.can(BuildingType.OFFSHORE_WIND, 16, 25))
        // And land stations still can't go in the water.
        assertTrue(!c.can(BuildingType.WIND_FARM, 10, 22))
    }

    @Test
    fun theTideComesRoundAndTheSeaIsWindier() {
        val tides = (1..30).map { Generation.tideAtPeak(it) }
        assertTrue(tides.max() == 100 && tides.min() == Balance.TIDE_LEAST, "$tides")
        assertTrue(Generation.offshoreShare(30) > Generation.windShare(30))
        assertEquals(0, Generation.offshoreShare(Weather.GALE))
    }

    @Test
    fun aRiverTurbineFeedsTheTownThroughACable() {
        val c = watery(2015)
        val m = c.map
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.RIVER_TURBINE, 31, 30)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 32, 30, 33, 30, true), buried = true)).ok)
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 34, 40, 63, 40, true))).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 34, 30, 34, 38, false))).ok)
        assertTrue(c.apply(Action.PlaceZone(35, 36, 60, 39, Zone.RESIDENTIAL)).ok)
        repeat(365) { c.tick() }
        assertTrue(c.stats.population > 0, "homes grew")
        val turbine = c.buildingAt(31, 30)!!
        assertTrue(c.stationAvailable(turbine) > 0)
        assertTrue(c.stationOutput(turbine) > 0, "the town uses it")
    }

    @Test
    fun aWindFarmsOutputGoesWithEachSpellOfWeather() {
        // A wind farm on a line and nothing else, so nothing grows to have the grid worked out again.
        val c = City(12, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true; it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, 2005)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.WIND_FARM, 30, 30)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 32, 31, 50, 31, true))).ok)
        c.tick()
        val seen = HashSet<Long>()
        repeat(90) {
            c.tick()
            seen += c.stats.powerCapacity
        }
        assertTrue(seen.size > 2, "capacity $seen")
    }
}
