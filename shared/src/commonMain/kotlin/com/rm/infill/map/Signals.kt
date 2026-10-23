package com.rm.infill.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.Junction
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** What a light shows to one way through a crossing. */
internal const val LIGHT_GREEN = 0
internal const val LIGHT_AMBER = 1
internal const val LIGHT_RED = 2

/**
 * What the lights at crossing [tile] show to traffic going north or south
 * if [vertical], else east or west, at [time] on the game clock: green one
 * way, amber, a moment of red both ways, then the other way. Each crossing
 * starts at its own point in the cycle so neighbours don't all change at once.
 */
internal fun signal(tile: Int, vertical: Boolean, time: Float): Int {
    var p = (time + signalOffset(tile)).mod(SIGNAL_CYCLE)
    if (!vertical) p = (p + SIGNAL_CYCLE / 2).mod(SIGNAL_CYCLE)
    return when {
        p < SIGNAL_GREEN -> LIGHT_GREEN
        p < SIGNAL_GREEN + SIGNAL_AMBER -> LIGHT_AMBER
        else -> LIGHT_RED
    }
}

private fun signalOffset(tile: Int): Float {
    var h = tile * -1640531535
    h = (h xor (h ushr 15)) * 0x2c1b3c6d
    h = h xor (h ushr 12)
    return (h and 0xffff) / 65536f * SIGNAL_CYCLE
}

/**
 * Each lit crossing's lamps on screen, coloured as they show now, over the
 * lamps drawn on the crossing art: the north-west and south-east lamps for
 * traffic going north or south, the other two for east or west. At night
 * each lamp also goes into [night], with a glow round it.
 */
internal fun DrawScope.drawSignals(map: CityMap, camera: Camera, time: Float, behind: (Float, Float) -> Boolean = { _, _ -> false }, night: NightLights? = null) {
    val t = camera.tilePx
    if (t < MIN_SIGNAL_PX) return
    val topLeft = camera.screenToTile(Offset.Zero, size)
    val bottomRight = camera.screenToTile(Offset(size.width, size.height), size)
    val x0 = floor(topLeft.x).toInt().coerceAtLeast(0)
    val y0 = floor(topLeft.y).toInt().coerceAtLeast(0)
    val x1 = ceil(bottomRight.x).toInt().coerceAtMost(map.width - 1)
    val y1 = ceil(bottomRight.y).toInt().coerceAtMost(map.height - 1)
    // One pixel of the 32 pixel art, or a little more so it shows.
    val dot = max(1.5f, t / ART_PX)
    val lamp = Size(dot, dot)
    for (y in y0..y1) for (x in x0..x1) {
        val i = map.index(x, y)
        if (map.control[i] != Junction.LIGHTS || map.road[i].toInt() == 0) continue
        val down = SIGNAL_COLOURS[signal(i, vertical = true, time)]
        val across = SIGNAL_COLOURS[signal(i, vertical = false, time)]
        for (k in 0 until 4) {
            val lx = x + (LAMP_X[k] + 0.5f) / ART_PX
            val ly = y + (LAMP_Y[k] + 0.5f) / ART_PX
            if (behind(lx, ly)) continue
            val at = camera.tileToScreen(lx, ly, size)
            val colour = if (k == 0 || k == 3) down else across
            drawRect(colour, Offset(at.x - dot / 2, at.y - dot / 2), lamp)
            if (night != null && night.shows(lx, ly)) {
                night.lamp(at.x, at.y, dot * 2.2f, colour.toArgb(), SIGNAL_GLOW)
                night.lamp(at.x, at.y, dot * 0.7f, colour.toArgb())
            }
        }
    }
}

/** Seconds of green each way, amber after it, and red both ways before the other way's green, on the game clock at normal speed. */
private const val SIGNAL_GREEN = 7f
private const val SIGNAL_AMBER = 2f
private const val SIGNAL_CLEAR = 1f
private const val SIGNAL_CYCLE = 2 * (SIGNAL_GREEN + SIGNAL_AMBER + SIGNAL_CLEAR)

/** Where the lit lamps are in the crossing art, 32 pixels a tile: north-west, north-east, south-west, south-east. */
private const val ART_PX = 32f
private val LAMP_X = floatArrayOf(5f, 26f, 5f, 26f)
private val LAMP_Y = floatArrayOf(3f, 3f, 24f, 24f)

private val SIGNAL_COLOURS = arrayOf(Color(0xFF3FBF5A), Color(0xFFF0B030), Color(0xFFD84343))

/** How strong a lamp's glow is at night. */
private const val SIGNAL_GLOW = 0.3f

/** Lamps too small to see below this. */
private const val MIN_SIGNAL_PX = 16f
