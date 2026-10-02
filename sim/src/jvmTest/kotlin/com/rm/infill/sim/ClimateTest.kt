package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClimateTest {
    /** Ten years of [climate]'s weather, a few days at a time: days of snow, days of heat wave, and gales. */
    private fun decade(climate: Climate): Triple<Int, Int, Int> {
        val w = Weather(11, climate)
        var snow = 0
        var heat = 0
        var gales = 0
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
}
