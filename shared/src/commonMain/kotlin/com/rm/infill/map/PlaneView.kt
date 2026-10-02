package com.rm.infill.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import com.rm.infill.sim.Building

/**
 * Planes at each airport: one landing from the west, rolling out along the
 * runway, then later one taking off to the east and climbing away. Bigger,
 * and jets, at the bigger airports. Each one's shadow falls on the ground
 * below it, further off the higher it is.
 */
internal fun DrawScope.drawPlanes(airports: List<Building>, camera: Camera, time: Float, jets: Boolean) {
    val t = camera.tilePx
    if (airports.isEmpty() || t < MIN_PLANE_PX) return
    for ((k, b) in airports.withIndex()) {
        val tier = b.type.airTier
        val runwayY = b.y + RUNWAY_ROW * b.type.height
        val x0 = b.x.toFloat()
        val x1 = (b.x + b.type.width).toFloat()
        val span = t * SIZES[tier]
        val period = CYCLE / tier
        val phase = ((time + k * 7.3f) % period + period) % period
        val u = phase / period
        // Half the time one comes in to land, half one goes out; between, the sky's clear.
        val (x, height, _) = when {
            u < 0.35f -> {
                val v = u / 0.35f
                Triple(x0 - APPROACH + (x1 - x0 + APPROACH) * ease(v) * 0.8f, (1 - v).coerceAtLeast(0f) * CLIMB, true)
            }
            u in 0.5f..0.85f -> {
                val v = (u - 0.5f) / 0.35f
                Triple(x0 + (x1 - x0 + APPROACH) * v * v, (v - 0.4f).coerceAtLeast(0f) * CLIMB * 1.6f, false)
            }
            else -> continue
        }
        val ground = camera.tileToScreen(x, runwayY, size)
        val air = Offset(ground.x, ground.y - height * t)
        // The shadow first, off to the south east.
        drawPlane(ground + Offset(height * t * 0.4f, height * t * 0.25f), span, SHADOW, jets && tier >= 3)
        drawPlane(air, span * (1 + height * 0.15f), BODY, jets && tier >= 3)
    }
}

/** A plane seen from above, nose to the east. */
private fun DrawScope.drawPlane(at: Offset, size: Float, colour: Color, jet: Boolean) {
    rotate(90f, at) {
        scale(size / 24f, size / 24f, at) {
            val p = Path().apply {
                fun to(x: Float, y: Float) = lineTo(at.x + (x - 12) * 1f, at.y + (y - 12) * 1f)
                moveTo(at.x, at.y - 10)
                to(13.5f, 4f); to(13.5f, if (jet) 9f else 10f); to(23f, if (jet) 15f else 13f); to(23f, 15.5f); to(13.5f, 14f)
                to(13.5f, 19f); to(16.5f, 21.5f); to(16.5f, 22.5f); to(12f, 21.5f); to(7.5f, 22.5f); to(7.5f, 21.5f)
                to(10.5f, 19f); to(10.5f, 14f); to(1f, 15.5f); to(1f, if (jet) 15f else 13f); to(10.5f, if (jet) 9f else 10f); to(10.5f, 4f)
                close()
            }
            drawPath(p, colour)
        }
    }
}

/** Where the runway runs, as a share of the airport's depth from its north side. */
private const val RUNWAY_ROW = 0.22f

/** Seconds between planes at an airfield, fewer at bigger ones; tiles out from the runway's end a landing starts. */
private const val CYCLE = 30f
private const val APPROACH = 6f

/** How high, in tiles of screen, a plane is at the start of its approach and the end of its climb. */
private const val CLIMB = 1.2f

private const val MIN_PLANE_PX = 6f

/** Size in tiles, by airport. */
private val SIZES = floatArrayOf(0f, 0.6f, 1.0f, 1.5f)
private val BODY = Color(0xFFF2F4F6)
private val SHADOW = Color(0x55000000)
