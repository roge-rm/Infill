package com.rm.infill.map

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.rm.infill.sim.City

/**
 * Where the town's rough sleepers are seen: in its parks and squares, a
 * bedroll for every [PER_ROLL] of them, the same places each time. Map
 * indices; looks over every building, so it's run under the town's lock.
 */
internal fun roughSpots(city: City): IntArray {
    val rolls = minOf(MOST, (city.stats.roughSleepers + PER_ROLL - 1) / PER_ROLL)
    if (rolls == 0) return IntArray(0)
    val m = city.map
    val open = ArrayList<Int>()
    for (b in city.allBuildings) {
        if (!b.type.green || b.underway > 0) continue
        for (dy in 0 until b.type.height) for (dx in 0 until b.type.width) open += m.index(b.x + dx, b.y + dy)
    }
    // The same spots in the same order, so they stay put as the number changes.
    open.sortBy { (it * 2_654_435_761L).toInt() }
    return IntArray(minOf(rolls, open.size)) { open[it] }
}

/** A bedroll on each of [spots] that's on screen, lying along the tile with its sleeper's head at one end. */
internal fun DrawScope.drawRough(spots: IntArray, width: Int, camera: Camera) {
    val t = camera.tilePx
    if (spots.isEmpty() || t < MIN_TILE_PX) return
    for (i in spots) {
        val x = i % width
        val y = i / width
        val seed = i * 31
        val at = camera.tileToScreen(x + 0.25f + (seed % 5) * 0.08f, y + 0.4f + (seed % 3) * 0.12f, size)
        if (at.x < -t || at.y < -t || at.x > size.width + t || at.y > size.height + t) continue
        val w = t * 0.34f
        val h = t * 0.11f
        drawRoundRect(BEDROLLS[seed % BEDROLLS.size], at, Size(w, h), CornerRadius(h / 2))
        drawCircle(HEAD, h * 0.45f, Offset(at.x + h * 0.55f, at.y + h / 2))
    }
}

/** Rough sleepers a bedroll stands for, and the most shown. */
private const val PER_ROLL = 4
private const val MOST = 120

/** Too small to make out below this, a tile in pixels. */
private const val MIN_TILE_PX = 16f

private val BEDROLLS = arrayOf(Color(0xFF5A6A7A), Color(0xFF6A5A4A), Color(0xFF4A5A4A), Color(0xFF7A4A3A))
private val HEAD = Color(0xFF4A3A30)
