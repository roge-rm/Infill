package com.rm.infill.map

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/** Where the sun is, as far as drawing goes. */
@Immutable
data class Sun(
    /** How far a shadow reaches along the ground for each pixel of height, east and south. */
    val shadowX: Float,
    val shadowY: Float,
    /** 0 at night to 1 in full day: how dark shadows are. */
    val strength: Float,
) {
    val length get() = hypot(shadowX, shadowY)
}

/**
 * The sun and the light over a day, for a city at about 50° north. The sun
 * rises in the east, crosses the south and sets in the west, higher and for
 * longer in summer. Shadows move in [STEPS] steps a day, since every step means
 * drawing the map's chunks again.
 */
object Sky {
    const val STEPS = 32

    fun step(hour: Float): Int = ((hour / 24f) * STEPS).toInt().coerceIn(0, STEPS - 1)

    /** The middle of a step, in hours. */
    fun hourOf(step: Int): Float = (step + 0.5f) * 24f / STEPS

    /** The sun in the middle of [step], in [month] (0 is January). */
    fun sun(step: Int, month: Int): Sun {
        val (elevation, azimuth) = position(hourOf(step), month)
        if (elevation <= 0f) return Sun(0f, 0f, 0f)
        val reach = (1f / tan(elevation * DEG)).coerceAtMost(MAX_REACH)
        // The shadow points away from the sun. Azimuth is from north, clockwise.
        val sx = -sin(azimuth * DEG) * reach
        val sy = cos(azimuth * DEG) * reach
        return Sun(sx, sy, (elevation / FULL_SHADOW_AT).coerceIn(0f, 1f))
    }

    /** The colour multiplied over the map at [hour]: white by day, warm at dawn and dusk, blue at night. */
    fun tint(hour: Float, month: Int): Color {
        val e = position(hour, month).first
        return when {
            e >= 12f -> Color.White
            e >= 0f -> lerp(GOLDEN, Color.White, e / 12f)
            e >= -6f -> lerp(TWILIGHT, GOLDEN, (e + 6f) / 6f)
            e >= -12f -> lerp(NIGHT, TWILIGHT, (e + 12f) / 6f)
            else -> NIGHT
        }
    }

    /**
     * The hour at [progress] (0 to 1) through a month's day and night, starting
     * an hour before sunrise. The daylight, with an hour of twilight either
     * side, takes [DAY_SHARE] of the time whatever the season, so a winter
     * month isn't mostly spent in the dark.
     */
    fun hourAt(progress: Float, month: Int): Float {
        val (rise, set) = daylight(month)
        val start = rise - 1f
        val lit = set - rise + 2f
        val p = progress.mod(1f)
        val hour = if (p < DAY_SHARE) start + p / DAY_SHARE * lit
        else start + lit + (p - DAY_SHARE) / (1f - DAY_SHARE) * (24f - lit)
        return hour.mod(24f)
    }

    /** Sunrise and sunset in hours for [month]. */
    private fun daylight(month: Int): Pair<Float, Float> {
        val season = cos(2f * PI.toFloat() * (month - 5.5f) / 12f)
        val halfDay = 6f + 2f * season
        return (12f - halfDay) to (12f + halfDay)
    }

    private const val DAY_SHARE = 0.72f

    /** Elevation and azimuth in degrees. Below the horizon the elevation goes about 10° down an hour. */
    private fun position(hour: Float, month: Int): Pair<Float, Float> {
        val season = cos(2f * PI.toFloat() * (month - 5.5f) / 12f) // 1 at midsummer, -1 at midwinter
        val (rise, set) = daylight(month)
        val highest = 40f + 23f * season
        return when {
            hour < rise -> -(rise - hour) * 10f to 90f
            hour > set -> -(hour - set) * 10f to 270f
            else -> {
                val f = (hour - rise) / (set - rise)
                highest * sin(PI.toFloat() * f) to 90f + 180f * f
            }
        }
    }

    private const val DEG = (PI / 180.0).toFloat()
    private const val MAX_REACH = 1.6f
    private const val FULL_SHADOW_AT = 8f
    private val GOLDEN = Color(0xFFFFE2BE)
    private val TWILIGHT = Color(0xFFA89ECC)
    private val NIGHT = Color(0xFF6470A6)
}

/** Which look the map is drawn in for each month, until weather decides when there's snow. */
object Seasons {
    fun lookFor(month: Int): Int = when (month) {
        0, 1, 11 -> Atlas.SNOW
        2, 10 -> Atlas.BARE
        3, 4 -> Atlas.SPRING
        5, 6, 7 -> Atlas.SUMMER
        else -> Atlas.AUTUMN
    }

    /** A month that looks like [look], for the sun when a look is picked by hand. */
    fun monthOf(look: Int): Int = when (look) {
        Atlas.SPRING -> 4
        Atlas.SUMMER -> 6
        Atlas.AUTUMN -> 9
        Atlas.BARE -> 10
        else -> 0
    }
}
