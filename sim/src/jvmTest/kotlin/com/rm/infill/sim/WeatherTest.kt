package com.rm.infill.sim

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeatherTest {
    /** Runs a year of weather from January 1900 and hands over each day. */
    private fun year(seed: Long, each: (month: Int, Weather) -> Unit) {
        val w = Weather(seed)
        for (month in 0 until 12) {
            val days = City.daysIn(month, 1900)
            for (day in 1..days) {
                w.nextDay(month, day, days)
                each(month, w)
            }
        }
    }

    @Test
    fun monthsAverageNearTheClimate() {
        val sum = IntArray(12)
        val count = IntArray(12)
        for (seed in 1L..20L) year(seed) { m, w -> sum[m] += w.temperature; count[m]++ }
        for (m in 0 until 12) {
            val mean = sum[m].toDouble() / count[m]
            assertTrue(abs(mean - Climate.TEMPERATE.temperature[m]) < 3.5, "month $m averages $mean")
        }
    }

    @Test
    fun itRainsAndSnowsInTheRightSeasons() {
        var winterSnow = 0
        var summerSnow = 0
        var summerRain = 0
        var wet = 0
        var days = 0
        for (seed in 1L..10L) year(seed) { m, w ->
            days++
            if (w.precipitation != Precipitation.None) wet++
            if (m in listOf(0, 1, 11) && w.precipitation == Precipitation.Snow) winterSnow++
            if (m in 5..7 && w.precipitation == Precipitation.Snow) summerSnow++
            if (m in 5..7 && w.precipitation == Precipitation.Rain) summerRain++
        }
        assertTrue(winterSnow > 50, "winter snow days $winterSnow")
        assertEquals(0, summerSnow)
        assertTrue(summerRain > 50, "summer rain days $summerRain")
        val share = wet * 100 / days
        assertTrue(share in 15..45, "wet on $share% of days")
    }

    @Test
    fun snowLiesInWinterAndIsGoneBySummer() {
        for (seed in 1L..10L) {
            var januaryCover = 0
            var juneCover = 0
            year(seed) { m, w ->
                if (m == 0) januaryCover = maxOf(januaryCover, w.snowCover)
                if (m == 5) juneCover = maxOf(juneCover, w.snowCover)
            }
            assertTrue(januaryCover >= Weather.SNOW_LOOK, "seed $seed: January snow $januaryCover")
            assertEquals(0, juneCover, "seed $seed")
        }
    }

    @Test
    fun sameSeedSameWeather() {
        val a = ArrayList<Int>()
        val b = ArrayList<Int>()
        year(9) { _, w -> a += w.temperature * 1000 + w.cloud * 10 + w.precipitation.ordinal }
        year(9) { _, w -> b += w.temperature * 1000 + w.cloud * 10 + w.precipitation.ordinal }
        assertEquals(a, b)
    }

    /** Not a check: a year of weather, month by month, for tuning. */
    @Test
    fun monthByMonth() {
        println("month  mean  low  high  rain  snow  fog  most snow")
        val w = Weather(1900)
        for (month in 0 until 12) {
            val days = City.daysIn(month, 1900)
            var sum = 0; var low = 99; var high = -99; var rain = 0; var snow = 0; var fog = 0; var cover = 0
            for (day in 1..days) {
                w.nextDay(month, day, days)
                sum += w.temperature; low = minOf(low, w.temperature); high = maxOf(high, w.temperature)
                if (w.precipitation == Precipitation.Rain) rain++
                if (w.precipitation == Precipitation.Snow) snow++
                if (w.fog) fog++
                cover = maxOf(cover, w.snowCover)
            }
            println("${month + 1}".padStart(5) + "${sum / days}".padStart(6) + "$low".padStart(5) + "$high".padStart(6) +
                "$rain".padStart(6) + "$snow".padStart(6) + "$fog".padStart(5) + "$cover".padStart(11))
        }
    }
}
