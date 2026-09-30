package com.rm.infill.sim

import kotlin.math.max
import kotlin.math.min

/** What falls from the sky. */
enum class Precipitation { None, Rain, Snow }

/**
 * A map's climate: the average temperature of each month in °C, how cloudy
 * each month tends to be (0 to 100) and the chance of rain or snow on a day.
 * Temperate is a city at about 50° north, well inland.
 */
class Climate(val temperature: IntArray, val cloudiness: IntArray, val wetDays: IntArray) {
    companion object {
        val TEMPERATE = Climate(
            temperature = intArrayOf(-5, -4, 1, 8, 14, 19, 22, 21, 16, 9, 3, -2),
            cloudiness = intArrayOf(70, 66, 62, 56, 52, 46, 42, 44, 50, 60, 70, 72),
            wetDays = intArrayOf(40, 36, 38, 38, 40, 38, 34, 32, 32, 34, 40, 42),
        )
    }
}

/**
 * The weather, a day at a time. Temperature and cloud wander around the
 * climate's averages from one day to the next, so there are runs of mild or
 * cold days rather than a new roll each morning. It has its own random
 * numbers, so the weather never changes how the town grows.
 */
class Weather(seed: Long, private val climate: Climate = Climate.TEMPERATE) {
    private val rng = Rng(seed xor WEATHER_SALT)
    private var warmth = 0 // how far from the month's average, in tenths of a degree
    private var cloudBias = 0

    /** °C. */
    var temperature = climate.temperature[0]
        private set

    /** 0 to 100. */
    var cloud = climate.cloudiness[0]
        private set

    var precipitation = Precipitation.None
        private set

    /** How hard it's raining or snowing, 0 to 100. */
    var intensity = 0
        private set

    var fog = false
        private set

    /** Snow on the ground, 0 to 100. A city founded in a freezing month starts under snow. */
    var snowCover = if (climate.temperature[0] <= 0) 60 else 0
        private set

    /** Which way the wind blows, 0 to 359 degrees clockwise from north, and how hard, 0 to 100. */
    var windDirection = 270
        private set
    var windSpeed = 30
        private set

    /**
     * The weather for the next [days] days from [day] of [month] (which has
     * [daysInMonth]). Temperature and cloud move on once; snow builds and melts
     * for all the days.
     */
    fun nextDay(month: Int, day: Int, daysInMonth: Int, days: Int = 1) {
        // The average blends into next month's over the second half of this one.
        val half = daysInMonth / 2
        val next = (month + 1) % 12
        val blend = if (day > half) (day - half) * 100 / (daysInMonth - half) else 0
        val average = climate.temperature[month] * (100 - blend / 2) / 100 + climate.temperature[next] * (blend / 2) / 100
        warmth = (warmth * 8 / 10 + rng.nextInt(61) - 30).coerceIn(-120, 120)
        temperature = average + warmth / 10

        cloudBias = (cloudBias * 7 / 10 + rng.nextInt(61) - 30).coerceIn(-60, 60)
        cloud = (climate.cloudiness[month] + cloudBias).coerceIn(0, 100)

        // Wet days come under the thickest cloud.
        val wet = cloud >= 55 && rng.nextInt(100) < climate.wetDays[month] * cloud / 60
        if (wet) {
            precipitation = if (temperature <= 0) Precipitation.Snow else Precipitation.Rain
            intensity = min(100, 20 + (cloud - 55) * 2 + rng.nextInt(40))
        } else {
            precipitation = Precipitation.None
            intensity = 0
        }

        // Fog on still, damp, cool days.
        fog = !wet && windSpeed < 40 && cloud in 30..85 && temperature in -3..12 && rng.nextInt(100) < 15

        windDirection = (windDirection + rng.nextInt(41) - 20 + 360) % 360
        windSpeed = (windSpeed + rng.nextInt(21) - 10).coerceIn(5, 90)

        // Snow lies when it falls below freezing, and melts with warmth, and faster in rain.
        if (precipitation == Precipitation.Snow) snowCover = min(100, snowCover + (intensity / 3 + 5) * days / 2 + 1)
        if (temperature > 0) {
            val melt = (temperature * 3 + if (precipitation == Precipitation.Rain) intensity / 4 else 0) * days / 2 + 1
            snowCover = max(0, snowCover - melt)
        }
    }

    companion object {
        private const val WEATHER_SALT = 0x5eed_c10dL

        /** Snow cover from which the map is drawn in its snow look. */
        const val SNOW_LOOK = 25
    }
}
