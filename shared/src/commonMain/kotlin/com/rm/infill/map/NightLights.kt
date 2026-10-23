package com.rm.infill.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The lamps of what moves, kept to be drawn again over the night's tint so
 * they keep their colour: signal lamps, sirens and headlights. Recorded in
 * screen pixels while the moving layer is drawn, then put down by [draw].
 * Its arrays only grow, so after the first few frames nothing is allocated.
 */
internal class NightLights(private val renderer: MapRenderer) {
    private var xs = FloatArray(START)
    private var ys = FloatArray(START)
    private var radii = FloatArray(START)
    private var colours = IntArray(START)
    private var count = 0
    private val paint = Paint()

    /** The heading [turn] last set, as a unit step forward and to the right on screen. */
    private var fx = 1f
    private var fy = 0f

    /** Whether something at [x], [y] tiles shows, with no building in front of it. */
    fun shows(x: Float, y: Float) = !renderer.hidden(x, y)

    /** A round lamp of [colour], ARGB, [radius] pixels, centred on [x], [y] pixels; [alpha] scales the colour's own. */
    fun lamp(x: Float, y: Float, radius: Float, colour: Int, alpha: Float = 1f) {
        if (count == xs.size) grow()
        xs[count] = x
        ys[count] = y
        radii[count] = radius
        colours[count] = if (alpha >= 1f) colour else (colour and 0xffffff) or (((colour ushr 24) * alpha).toInt() shl 24)
        count++
    }

    /** Sets the heading, degrees clockwise from east, that [ahead] and [aside] step along. */
    fun turn(heading: Float) {
        val r = heading * (PI / 180.0).toFloat()
        fx = cos(r)
        fy = sin(r)
    }

    /** How far across from [at] a point [along] ahead and [side] to the right of it is, at the heading [turn] set. */
    fun ax(at: Offset, along: Float, side: Float) = at.x + fx * along - fy * side
    fun ay(at: Offset, along: Float, side: Float) = at.y + fy * along + fx * side

    /**
     * Two headlights and two tail lights on a motor vehicle
     * [length] by [width] pixels centred on [at], at [heading]; a lantern on
     * a cart if not [motor]. [t] is the tile size, [x], [y] where it is in tiles.
     */
    fun vehicle(x: Float, y: Float, at: Offset, t: Float, heading: Float, length: Float, width: Float, motor: Boolean, alpha: Float = 1f) {
        if (t < MIN_TILE_PX || !shows(x, y)) return
        turn(heading)
        val front = length / 2
        if (!motor) {
            lamp(ax(at, front * 0.2f, 0f), ay(at, front * 0.2f, 0f), max(0.8f, t * 0.022f), LANTERN, alpha)
            return
        }
        val r = max(0.7f, t * 0.018f)
        if (t < NEAR_TILE_PX) {
            // Zoomed out, one dot each end.
            lamp(ax(at, front, 0f), ay(at, front, 0f), r, HEAD, alpha)
            lamp(ax(at, -front, 0f), ay(at, -front, 0f), r, TAIL, alpha)
            return
        }
        val side = width * 0.32f
        lamp(ax(at, front, -side), ay(at, front, -side), r, HEAD, alpha)
        lamp(ax(at, front, side), ay(at, front, side), r, HEAD, alpha)
        lamp(ax(at, -front, -side), ay(at, -front, -side), r, TAIL, alpha)
        lamp(ax(at, -front, side), ay(at, -front, side), r, TAIL, alpha)
    }

    /**
     * The lamps of a railway car [length] by [width] pixels centred on [at],
     * [heading] the way the train is going: a headlamp if it's the [lead] car,
     * dimmer and warmer for [steam], a red lamp if it's the [last], and lit
     * windows down both sides if it carries [riders]. [x], [y] where it is in tiles.
     */
    fun railCar(x: Float, y: Float, at: Offset, t: Float, heading: Float, length: Float, width: Float, lead: Boolean, last: Boolean, riders: Boolean, steam: Boolean) {
        if (t < MIN_TILE_PX || !shows(x, y)) return
        turn(heading)
        val front = length / 2
        val r = max(0.9f, t * 0.034f)
        if (lead) lamp(ax(at, front, 0f), ay(at, front, 0f), if (steam) r * 1.2f else r, if (steam) HEADLAMP else HEAD)
        if (last) lamp(ax(at, -front, 0f), ay(at, -front, 0f), max(0.8f, t * 0.024f), TAIL)
        if (riders) windows(at, t, length, width, if (steam) GASLIT else CABIN)
    }

    /** Lit windows down both sides of a car [length] by [width] pixels on [at], at the heading [turn] set; fewer zoomed out. */
    fun windows(at: Offset, t: Float, length: Float, width: Float, colour: Int) {
        val n = if (t < NEAR_TILE_PX) 2 else 4
        val r = max(0.7f, t * 0.02f)
        val side = width * 0.34f
        for (k in 0 until n) {
            val along = length * ((k + 0.5f) / n - 0.5f) * 0.8f
            lamp(ax(at, along, -side), ay(at, along, -side), r, colour)
            lamp(ax(at, along, side), ay(at, along, side), r, colour)
        }
    }

    /** A white masthead light a little ahead of the middle of a boat [length] pixels long on [at], and one at its stern. */
    fun boat(x: Float, y: Float, at: Offset, t: Float, heading: Float, length: Float) {
        if (t < MIN_TILE_PX || !shows(x, y)) return
        turn(heading)
        val r = max(0.8f, t * 0.022f)
        lamp(ax(at, length * 0.15f, 0f), ay(at, length * 0.15f, 0f), r, MASTHEAD)
        lamp(ax(at, -length * 0.45f, 0f), ay(at, -length * 0.45f, 0f), r * 0.8f, MASTHEAD)
    }

    /** Puts down what was recorded at [strength], 0 to 1, and empties the list for the next frame. */
    fun draw(scope: DrawScope, strength: Float) {
        if (count == 0) return
        scope.drawIntoCanvas { canvas ->
            for (k in 0 until count) {
                val c = colours[k]
                paint.color = Color(c).copy(alpha = (c ushr 24) / 255f * strength)
                canvas.drawCircle(Offset(xs[k], ys[k]), radii[k], paint)
            }
        }
        count = 0
    }

    private fun grow() {
        val n = xs.size * 2
        xs = xs.copyOf(n)
        ys = ys.copyOf(n)
        radii = radii.copyOf(n)
        colours = colours.copyOf(n)
    }

    companion object {
        private const val START = 1024

        /** No headlights below this tile size, and two each end from the next. */
        const val MIN_TILE_PX = 12f
        private const val NEAR_TILE_PX = 24f

        private const val HEAD = 0xFFFFF4D0.toInt()
        private const val TAIL = 0xFFFF2A1E.toInt()
        private const val LANTERN = 0xC0FFB04A.toInt()

        /** A steam engine's oil or carbide headlamp, warmer and dimmer than an electric one. */
        private const val HEADLAMP = 0xB0FFC468.toInt()

        /** Lit windows in passenger cars and trams: electric, and gaslit in the steam years. */
        const val CABIN = 0xA0FFE2A0.toInt()
        const val GASLIT = 0x90FFC060.toInt()

        private const val MASTHEAD = 0xE0FFFFF0.toInt()
    }
}
