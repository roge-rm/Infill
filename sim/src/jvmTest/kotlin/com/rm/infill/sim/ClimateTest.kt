package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClimateTest {
    /** Eighty years of [climate]'s weather, ten each from eight starts, a few days at a time: days of snow, days of heat wave, and gales. */
    private fun decade(climate: Climate): Triple<Int, Int, Int> {
        var snow = 0
        var heat = 0
        var gales = 0
        for (seed in 1L..8L) {
          val w = Weather(seed, climate)
          for (year in 0 until 10) for (month in 0 until 12) {
            var day = 1
            while (day <= 30) {
                w.nextDay(month, day, 30, 5)
                if (w.precipitation == Precipitation.Snow) snow++
                if (w.temperature >= climate.heatWave) heat++
                if (w.windSpeed >= Weather.GALE) gales++
                day += 5
            }
          }
        }
        return Triple(snow, heat, gales)
    }

    @Test
    fun eachClimateHasItsOwnWeather() {
        val (tSnow, tHeat, tGales) = decade(Climate.TEMPERATE)
        val (nSnow, _, _) = decade(Climate.NORTHERN)
        val (dSnow, dHeat, _) = decade(Climate.DRY)
        val (cSnow, _, cGales) = decade(Climate.COASTAL)
        assertTrue(nSnow > tSnow, "northern $nSnow, temperate $tSnow")
        assertTrue(dSnow < tSnow / 4, "dry $dSnow")
        assertTrue(cSnow < tSnow, "coastal $cSnow")
        assertTrue(dHeat > tHeat, "dry heat $dHeat, temperate $tHeat")
        assertTrue(cGales > tGales, "coastal gales $cGales, temperate $tGales")
    }

    @Test
    fun aDryMapHasFewerTreesAndANorthernOneMore() {
        fun trees(c: Climate) = City(5, 64, 64, TerrainOptions(climate = c)).map.terrain.count { it == Terrain.TREES }
        assertTrue(trees(Climate.DRY) < trees(Climate.TEMPERATE))
        assertTrue(trees(Climate.NORTHERN) > trees(Climate.TEMPERATE))
    }

    @Test
    fun theClimateSetsThePowerPeak() {
        // A northern winter needs more heating; a dry summer, once there's air conditioning, more cooling.
        assertTrue(Electricity.peak(1990, 0, Climate.NORTHERN) > Electricity.peak(1990, 0, Climate.TEMPERATE))
        assertTrue(Electricity.peak(1990, 6, Climate.DRY) > Electricity.peak(1990, 6, Climate.TEMPERATE))
        // Before air conditioning, no summer peak anywhere.
        assertEquals(Electricity.peak(1930, 6, Climate.TEMPERATE), Electricity.peak(1930, 6, Climate.DRY))
    }

    @Test
    fun theClimateIsSavedAndOldTownsAreTemperate() {
        val c = City(5, 64, 64, TerrainOptions(climate = Climate.COASTAL))
        repeat(10) { c.tick() }
        assertEquals(Climate.COASTAL, SaveGame.read(SaveGame.write(c)).climate)
        val old = SaveGame.read(javaClass.getResourceAsStream("/saves/v25.infill")!!.readBytes())
        assertEquals("Twenty-fifth", old.name)
        assertEquals(Climate.TEMPERATE, old.climate)
        val people = old.stats.population
        repeat(70) { old.tick() }
        assertTrue(old.stats.population > people / 2)
    }

    private fun cityIn(year: Int): City {
        val c = City(5, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true; it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        return c
    }

    @Test
    fun theWorldWarmsFrom1980() {
        assertEquals(0, cityIn(1970).warming)
        assertEquals(0, cityIn(1980).warming)
        assertTrue(cityIn(2050).warming in 19..23, "${cityIn(2050).warming}")
    }

    @Test
    fun warmerMeansMoreHeatWavesAndCloudbursts() {
        fun count(warming: Int): Pair<Int, Int> {
            var spells = 0
            var bursts = 0
            for (seed in 1L..6L) {
                val w = Weather(seed, Climate.TEMPERATE)
                var was = false
                for (year in 0 until 10) for (month in 0 until 12) {
                    var day = 1
                    while (day <= 30) {
                        w.nextDay(month, day, 30, 5, warming)
                        val hot = w.temperature >= Climate.TEMPERATE.heatWave
                        if (hot && !was) spells++
                        was = hot
                        if (w.intensity >= 85) bursts++
                        day += 5
                    }
                }
            }
            return spells to bursts
        }
        val (coolSpells, coolBursts) = count(0)
        val (warmSpells, warmBursts) = count(20)
        assertTrue(warmSpells > coolSpells * 2, "spells $coolSpells then $warmSpells")
        assertTrue(warmBursts > coolBursts, "bursts $coolBursts then $warmBursts")
    }

    /** Homes along a street with power from [station], a month or two on. */
    private fun powered(station: BuildingType): City {
        val c = cityIn(2000)
        val m = c.map
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 60, 30, true), RoadType.STREET)).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 26, 40, 29, Zone.RESIDENTIAL)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(station, 50, 24)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 49, 25, 5, 25, true))).ok)
        repeat(24) { repeat(31) { c.tick() } }
        return c
    }

    @Test
    fun coalPutsOutCarbonAndWindDoesnt() {
        val coal = powered(BuildingType.COAL_PLANT)
        val wind = powered(BuildingType.WIND_FARM)
        assertTrue(coal.stats.carbonPower > 0)
        assertEquals(0, wind.stats.carbonPower)
        assertTrue(coal.stats.carbon > wind.stats.carbon)
        assertTrue(coal.carbonTotal > 0)
        // The coal town's carbon shows on the map where the station is.
        assertTrue((coal.map.carbon[coal.map.index(50, 24)].toInt() and 0xff) > 0)
    }

    @Test
    fun aTownsOwnCarbonAddsALittleWarming() {
        val c = cityIn(2000)
        val base = c.warming
        City::class.java.getDeclaredField("carbonTotal").apply { isAccessible = true }.setLong(c, Balance.CARBON_PER_TENTH * 2)
        assertEquals(base + 2, c.warming)
        City::class.java.getDeclaredField("carbonTotal").apply { isAccessible = true }.setLong(c, Balance.CARBON_PER_TENTH * 50)
        assertEquals(base + Balance.TOWN_WARMING_MOST, c.warming)
    }

    @Test
    fun carbonIsSaved() {
        val c = powered(BuildingType.COAL_PLANT)
        val back = SaveGame.read(SaveGame.write(c))
        assertEquals(c.carbonTotal, back.carbonTotal)
        assertEquals(c.stats.carbon, back.stats.carbon)
        assertTrue(back.history.values(Series.Carbon).last() > 0)
    }
}
