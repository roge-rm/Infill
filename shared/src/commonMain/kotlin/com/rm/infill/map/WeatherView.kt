package com.rm.infill.map

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.rm.infill.sim.Precipitation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** The day's weather, as the map draws it. Wind is a direction on the map and a strength from 0 to 1. */
@Immutable
data class WeatherLook(
    val cloud: Int,
    val precipitation: Precipitation,
    val intensity: Int,
    val fog: Boolean,
    val windX: Float,
    val windY: Float,
    val wind: Float,
    /** Smog hanging over the town, 0 to 255. */
    val smog: Int = 0,
) {
    /** Anything that moves, so the map has to be drawn every frame while the game runs. */
    val moving get() = precipitation != Precipitation.None || cloud > CLOUD_SHADOWS_FROM || fog

    /**
     * How strongly the sun's shadows are baked, 0 to 3, from how clear the sky is.
     * It's part of what a chunk is baked for, so it only changes with the day's weather.
     */
    val clearness: Int get() = (3 - cloud / 30 - if (precipitation != Precipitation.None) 1 else 0).coerceIn(0, 3)

    companion object {
        val CLEAR = WeatherLook(0, Precipitation.None, 0, false, 1f, 0f, 0.3f)
        const val CLOUD_SHADOWS_FROM = 25

        fun of(cloud: Int, precipitation: Precipitation, intensity: Int, fog: Boolean, direction: Int, speed: Int, smog: Int = 0): WeatherLook {
            // Direction is where the wind comes from, so it blows the other way.
            val a = (direction + 180) * PI.toFloat() / 180f
            return WeatherLook(cloud, precipitation, intensity, fog, sin(a), -cos(a), speed / 100f, smog)
        }
    }
}

/** How much each clearness level keeps of the sun's shadows. */
val SHADOW_KEEP = floatArrayOf(0.2f, 0.45f, 0.75f, 1f)

/** The light of the hour with the weather's grey over it. */
fun weatherTint(sky: Color, look: WeatherLook): Color {
    var grey = ((look.cloud - 40) / 60f).coerceIn(0f, 1f) * 0.45f
    if (look.precipitation != Precipitation.None) grey += look.intensity / 100f * 0.25f
    if (look.fog) grey += 0.1f
    var overcast = lerp(Color.White, OVERCAST, grey.coerceIn(0f, 1f))
    // Smog browns the light.
    if (look.smog > 0) overcast = lerp(overcast, SMOG, (look.smog / 255f * 0.8f).coerceIn(0f, 0.5f))
    return Color(sky.red * overcast.red, sky.green * overcast.green, sky.blue * overcast.blue, 1f)
}

private val OVERCAST = Color(0xFFA9AFB9)
private val SMOG = Color(0xFFB8A27A)

/**
 * Cloud shadows: a soft, tiling noise in four densities, from a few scattered
 * clouds to a sky nearly full. Made once, as alpha over black.
 */
class CloudTextures(val levels: List<ImageBitmap>) {
    fun forCloud(cloud: Int): ImageBitmap? = when {
        cloud < WeatherLook.CLOUD_SHADOWS_FROM -> null
        cloud < 45 -> levels[0]
        cloud < 65 -> levels[1]
        cloud < 85 -> levels[2]
        else -> levels[3]
    }

    companion object {
        const val SIZE = 128

        fun make(): CloudTextures {
            val noise = IntArray(SIZE * SIZE) { i -> tiledNoise(i % SIZE, i / SIZE) }
            val sorted = noise.copyOf().also { it.sort() }
            val levels = listOf(78, 60, 40, 20).map { percentClear ->
                val line = sorted[sorted.size * percentClear / 100]
                val pixels = IntArray(SIZE * SIZE) { i ->
                    // Soft edges: alpha rises over a band above the line.
                    val a = ((noise[i] - line) * 255 / 6000).coerceIn(0, 255)
                    (a shl 24)
                }
                imageBitmapOf(pixels, SIZE, SIZE)
            }
            return CloudTextures(levels)
        }

        /** Value noise that wraps at the texture's edges, 0 to 65535. */
        private fun tiledNoise(x: Int, y: Int): Int {
            var sum = 0
            var weight = 8
            var cells = 4
            var total = 0
            while (cells <= 32) {
                val cell = SIZE / cells
                val cx = x / cell
                val cy = y / cell
                val fx = (x % cell).toFloat() / cell
                val fy = (y % cell).toFloat() / cell
                val sx = fx * fx * (3 - 2 * fx)
                val sy = fy * fy * (3 - 2 * fy)
                fun at(i: Int, j: Int) = lattice(i % cells, j % cells, cells)
                val top = at(cx, cy) + (at(cx + 1, cy) - at(cx, cy)) * sx
                val bottom = at(cx, cy + 1) + (at(cx + 1, cy + 1) - at(cx, cy + 1)) * sx
                sum += ((top + (bottom - top) * sy) * weight).toInt()
                total += weight
                weight /= 2
                cells *= 2
            }
            return sum / total
        }

        private fun lattice(x: Int, y: Int, salt: Int): Int {
            var h = x * 374761393 + y * 668265263 + salt * 1442695041
            h = (h xor (h ushr 13)) * 1274126177
            return (h xor (h ushr 16)) and 0xffff
        }
    }
}

/**
 * Draws the weather over the map at [time] seconds: cloud shadows drifting
 * across the ground, then rain or snow and fog over everything. What's drawn
 * depends on [graphics].
 */
internal fun DrawScope.drawWeather(
    look: WeatherLook,
    camera: Camera,
    clouds: CloudTextures?,
    time: Float,
    sunStrength: Float,
    graphics: Graphics,
) {
    val texture = clouds?.forCloud(look.cloud)
    if (graphics.cloudShadows && texture != null && sunStrength > 0f) {
        cloudShadows(texture, look, camera, time, (0.28f * sunStrength).coerceIn(0f, 1f))
    }
    if (graphics.particles > 0f) {
        when (look.precipitation) {
            Precipitation.Rain -> rain(look, time, graphics.particles)
            Precipitation.Snow -> snow(look, time, graphics.particles)
            Precipitation.None -> {}
        }
    }
    if (look.fog) {
        drawRect(FOG, alpha = 0.32f)
        // Banks of thicker fog drifting through.
        if (graphics.cloudShadows && clouds != null) {
            cloudShadows(clouds.levels[1], look, camera, time * 0.4f, 0.16f, colour = FOG_BANK, scale = 0.6f)
        }
    }
}

/** A texture repeated over the whole map in world space, moving with the wind, so it pans with the map. */
private fun DrawScope.cloudShadows(
    texture: ImageBitmap,
    look: WeatherLook,
    camera: Camera,
    time: Float,
    alpha: Float,
    colour: Color? = null,
    scale: Float = 1f,
) {
    val span = CLOUD_TILES * scale
    val drift = time * (0.2f + look.wind * 1.2f)
    val ox = look.windX * drift
    val oy = look.windY * drift
    val topLeft = camera.screenToTile(Offset.Zero, size)
    val bottomRight = camera.screenToTile(Offset(size.width, size.height), size)
    val i0 = floor((topLeft.x - ox) / span).toInt()
    val j0 = floor((topLeft.y - oy) / span).toInt()
    val i1 = floor((bottomRight.x - ox) / span).toInt()
    val j1 = floor((bottomRight.y - oy) / span).toInt()
    val filter = androidx.compose.ui.graphics.ColorFilter.tint(colour ?: Color.Black)
    for (j in j0..j1) for (i in i0..i1) {
        val a = camera.tileToScreen(i * span + ox, j * span + oy, size)
        val b = camera.tileToScreen((i + 1) * span + ox, (j + 1) * span + oy, size)
        val left = a.x.roundToInt()
        val top = a.y.roundToInt()
        drawImage(
            texture,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(texture.width, texture.height),
            dstOffset = IntOffset(left, top),
            dstSize = IntSize(b.x.roundToInt() - left, b.y.roundToInt() - top),
            alpha = alpha,
            colorFilter = filter,
            filterQuality = FilterQuality.Low,
        )
    }
}

/** Streaks falling fast and slanting with the wind. */
private fun DrawScope.rain(look: WeatherLook, time: Float, amount: Float) {
    val count = (look.intensity / 100f * amount * size.width * size.height / (22f * density * 22f * density)).toInt().coerceAtMost(900)
    val fall = 1100f * density
    val length = 16f * density
    val slant = look.windX * look.wind * 0.6f
    val stroke = 1.2f * density
    for (k in 0 until count) {
        val x0 = unit(k * 2) * (size.width + length * 2) - length
        val phase = unit(k * 2 + 1)
        val speed = fall * (0.8f + 0.4f * unit(k * 3 + 7))
        val y = ((phase * (size.height + length) + speed * time) % (size.height + length)) - length
        val x = x0 + slant * (y + length)
        drawLine(RAIN, Offset(x, y), Offset(x - slant * length, y - length), stroke)
    }
}

/** Flakes drifting down and swaying, carried along by the wind. */
private fun DrawScope.snow(look: WeatherLook, time: Float, amount: Float) {
    val count = (look.intensity / 100f * amount * size.width * size.height / (26f * density * 26f * density)).toInt().coerceAtMost(700)
    val fall = 70f * density
    val carry = look.windX * look.wind * 60f * density
    for (k in 0 until count) {
        val flake = (1.5f + 2f * unit(k * 5 + 3)) * density
        val speed = fall * (0.6f + 0.8f * unit(k * 3 + 1))
        val phase = unit(k * 2 + 1)
        val y = ((phase * (size.height + flake) + speed * time) % (size.height + flake)) - flake
        val sway = sin(time * 1.3f + k) * 8f * density
        val w = size.width + 40f * density
        val x = ((unit(k * 2) * w + carry * time + sway) % w + w) % w - 20f * density
        drawRect(SNOW, Offset(x, y), androidx.compose.ui.geometry.Size(flake, flake))
    }
}

/** The same scatter of numbers, 0 to 1, for each particle every frame. */
private fun unit(k: Int): Float {
    var h = k * 374761393 + 668265263
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xffffff) / 16777216f
}

/** A cloud texture covers this many tiles before it repeats. */
private const val CLOUD_TILES = 48f

private val RAIN = Color(0x8CC8D6E6)
private val SNOW = Color(0xE6FFFFFF)
private val FOG = Color(0xFFD9DEE3)
private val FOG_BANK = Color(0xFFE8ECF0)

/**
 * Flames and smoke over each burning tile that's on screen, flickering with
 * [time]: the building darkened, then columns of flame in art-sized pixels,
 * yellow at the base to red at the tips, and smoke leaning with the wind.
 */
internal fun DrawScope.drawFires(map: com.rm.infill.sim.CityMap, camera: Camera, look: WeatherLook, time: Float) {
    val topLeft = camera.screenToTile(Offset.Zero, size)
    val bottomRight = camera.screenToTile(Offset(size.width, size.height), size)
    val x0 = floor(topLeft.x).toInt().coerceAtLeast(0)
    val y0 = floor(topLeft.y).toInt().coerceAtLeast(0)
    val x1 = kotlin.math.ceil(bottomRight.x).toInt().coerceAtMost(map.width - 1)
    val y1 = (kotlin.math.ceil(bottomRight.y).toInt() + 1).coerceAtMost(map.height - 1)
    val t = camera.tilePx
    // One art pixel on screen, and flames drawn in blocks of two of them.
    val px = t / 32f
    val block = px * 2
    for (y in y0..y1) for (x in x0..x1) {
        if (map.fire[map.index(x, y)].toInt() == 0) continue
        val corner = camera.tileToScreen(x.toFloat(), y.toFloat(), size)
        drawRect(Color.Black, corner, androidx.compose.ui.geometry.Size(t, t), alpha = 0.3f)
        val seed = x * 31 + y * 17
        val baseY = corner.y + t * 0.55f
        for (k in 0 until 5) {
            val age = (time * 0.5f + unit(seed + k)) % 1f
            val drift = look.windX * look.wind * age * t * 0.8f
            val r = t * (0.08f + age * 0.16f)
            val cx = corner.x + t * (0.3f + 0.4f * unit(seed * 3 + k)) + drift
            drawCircle(SMOKE, r, Offset(cx, baseY - t * 0.35f - age * t * 1.4f), alpha = 0.5f * (1f - age))
        }
        val columns = 8
        for (k in 0 until columns) {
            val flicker = 0.5f + 0.5f * sin(time * 9f + k * 1.7f + seed)
            val rows = (2 + (4 * flicker + 3 * unit(seed + k * 7))).toInt()
            val fx = corner.x + t * 0.19f + k * block
            for (r in 0 until rows) {
                val colour = when {
                    r >= rows - 1 -> FLAME_TIP
                    r >= rows / 2 -> FLAME_OUTER
                    else -> FLAME_INNER
                }
                drawRect(colour, Offset(fx, baseY - (r + 1) * block), androidx.compose.ui.geometry.Size(block, block))
            }
        }
    }
}

private val SMOKE = Color(0xFF55585E)
private val FLAME_TIP = Color(0xFFC0392B)
private val FLAME_OUTER = Color(0xFFE8622A)
private val FLAME_INNER = Color(0xFFF7C948)
