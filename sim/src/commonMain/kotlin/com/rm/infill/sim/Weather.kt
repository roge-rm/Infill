package com.rm.infill.sim

import kotlin.math.max
import kotlin.math.min

/** What falls from the sky. */
enum class Precipitation { None, Rain, Snow }

/**
 * A map's climate: the average temperature of each month in °C, how cloudy
 * each month tends to be (0 to 100) and the chance of rain or snow on a day;
 * one autumn or winter spell in [galeOdds] has a gale, one wet spell in
 * [cloudburst] is a cloudburst, it's a heat wave from [heatWave] °C, and its
 * land has [trees] percent of the usual woods.
 */
enum class Climate(
    val temperature: IntArray, val cloudiness: IntArray, val wetDays: IntArray,
    val galeOdds: Int, val cloudburst: Int, val heatWave: Int, val trees: Int,
) {
    /** About 50° north, well inland. */
    TEMPERATE(
        temperature = intArrayOf(-5, -4, 1, 8, 14, 19, 22, 21, 16, 9, 3, -2),
        cloudiness = intArrayOf(70, 66, 62, 56, 52, 46, 42, 44, 50, 60, 70, 72),
        wetDays = intArrayOf(40, 36, 38, 38, 40, 38, 34, 32, 32, 34, 40, 42),
        galeOdds = 120, cloudburst = 20, heatWave = 30, trees = 100,
    ),

    /** About 60° north: long snowy winters and short summers. */
    NORTHERN(
        temperature = intArrayOf(-15, -13, -7, 1, 8, 14, 17, 15, 9, 2, -5, -12),
        cloudiness = intArrayOf(70, 65, 60, 55, 52, 50, 52, 56, 62, 70, 75, 74),
        wetDays = intArrayOf(42, 38, 36, 34, 36, 40, 44, 46, 44, 44, 46, 44),
        galeOdds = 100, cloudburst = 30, heatWave = 27, trees = 130,
    ),

    /** On the coast at about 48° north: mild and wet all year, windy in winter, little snow. */
    COASTAL(
        temperature = intArrayOf(4, 5, 6, 8, 11, 14, 17, 17, 15, 11, 7, 5),
        cloudiness = intArrayOf(78, 75, 70, 64, 60, 56, 52, 54, 60, 70, 78, 80),
        wetDays = intArrayOf(58, 52, 50, 44, 40, 38, 34, 36, 44, 54, 58, 60),
        galeOdds = 60, cloudburst = 20, heatWave = 28, trees = 90,
    ),

    /** About 35° north, inland: hot summers, mild winters, little rain but heavy when it comes. */
    DRY(
        temperature = intArrayOf(8, 10, 14, 18, 23, 29, 33, 32, 27, 20, 13, 9),
        cloudiness = intArrayOf(40, 38, 34, 28, 22, 14, 10, 12, 18, 26, 34, 40),
        wetDays = intArrayOf(26, 24, 20, 14, 8, 2, 1, 2, 6, 14, 20, 26),
        galeOdds = 160, cloudburst = 6, heatWave = 38, trees = 30,
    ),
    ;

    /** The warmest month's average. */
    val hottest: Int get() = temperature.max()
}

/**
 * The weather, a day at a time. Temperature and cloud wander around the
 * climate's averages from one day to the next, so there are runs of mild or
 * cold days rather than a new roll each morning. It has its own random
 * numbers, so the weather never changes how the town grows.
 */
class Weather(seed: Long, climate: Climate = Climate.TEMPERATE) {
    /** The map's climate, set when the town's made or loaded. */
    var climate = climate
        internal set

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

        // Wet days come under the thickest cloud. Most rain is light or steady;
        // now and then, about one wet spell in twenty, the sky opens.
        val wet = cloud >= 55 && rng.nextInt(100) < climate.wetDays[month] * cloud / 60
        if (wet) {
            precipitation = if (temperature <= 0) Precipitation.Snow else Precipitation.Rain
            intensity = if (rng.nextInt(climate.cloudburst) == 0) 85 + rng.nextInt(16) else min(70, 10 + (cloud - 55) / 2 + rng.nextInt(30))
        } else {
            precipitation = Precipitation.None
            intensity = 0
        }

        // Fog on still, damp, cool days.
        fog = !wet && windSpeed < 40 && cloud in 30..85 && temperature in -3..12 && rng.nextInt(100) < 15

        windDirection = (windDirection + rng.nextInt(41) - 20 + 360) % 360
        windSpeed = (windSpeed + rng.nextInt(21) - 10).coerceIn(5, 85)
        // Now and then in autumn and winter, a gale.
        if ((month >= 9 || month <= 2) && rng.nextInt(climate.galeOdds) == 0) windSpeed = GALE + rng.nextInt(10)

        // Snow lies when it falls below freezing, and melts with warmth, and faster in rain.
        if (precipitation == Precipitation.Snow) snowCover = min(100, snowCover + (intensity / 3 + 5) * days / 2 + 1)
        if (temperature > 0) {
            val melt = (temperature * 3 + if (precipitation == Precipitation.Rain) intensity / 4 else 0) * days / 2 + 1
            snowCover = max(0, snowCover - melt)
        }
    }

    internal fun writeTo(w: SaveWriter) {
        w.long(rng.state)
        w.int(warmth); w.int(cloudBias); w.int(temperature); w.int(cloud)
        w.int(precipitation.ordinal); w.int(intensity); w.bool(fog); w.int(snowCover)
        w.int(windDirection); w.int(windSpeed)
    }

    internal fun readFrom(r: SaveReader) {
        rng.state = r.long()
        warmth = r.int(); cloudBias = r.int(); temperature = r.int(); cloud = r.int()
        precipitation = Precipitation.entries.getOrElse(r.int()) { Precipitation.None }
        intensity = r.int(); fog = r.bool(); snowCover = r.int()
        windDirection = r.int(); windSpeed = r.int()
    }

    companion object {
        private const val WEATHER_SALT = 0x5eed_c10dL

        /** Wind this strong is a gale. */
        const val GALE = 90

        /** Snow cover from which the map is drawn in its snow look. */
        const val SNOW_LOOK = 25
    }
}
