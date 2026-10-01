package com.rm.infill.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.rm.infill.sim.CityMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * What's under the ground, for laying pipes: the map dimmed, the water mains
 * in blue, the sewers in brown and the storm drains in grey, each a little to
 * one side of the others, and a dot on each tile that has mains water or is on
 * the sewer.
 */
internal fun DrawScope.drawUnderground(map: CityMap, camera: Camera) {
    drawRect(VEIL)
    val t = camera.tilePx
    forVisibleTiles(map, camera) { x, y, i ->
        val corner = camera.tileToScreen(x.toFloat(), y.toFloat(), size)
        if (map.watered[i]) drawRect(WATERED, Offset(corner.x + t * 0.1f, corner.y + t * 0.1f), Size(t * 0.14f, t * 0.14f))
        if (map.sewered[i]) drawRect(SEWERED, Offset(corner.x + t * 0.76f, corner.y + t * 0.76f), Size(t * 0.14f, t * 0.14f))
        pipe(map, map.waterPipe, x, y, corner, t, -0.18f, WATER_MAIN)
        pipe(map, map.sewerPipe, x, y, corner, t, 0f, SEWER)
        pipe(map, map.stormPipe, x, y, corner, t, 0.18f, STORM_DRAIN)
    }
}

/** One kind of pipe on a tile: from just off its middle out to each side joined to more of it, or a stub if none are. */
private fun DrawScope.pipe(map: CityMap, layer: ByteArray, x: Int, y: Int, corner: Offset, t: Float, offset: Float, colour: Color) {
    if (layer[map.index(x, y)].toInt() == 0) return
    fun has(nx: Int, ny: Int) = map.inside(nx, ny) && layer[map.index(nx, ny)].toInt() != 0
    val cx = corner.x + t * (0.5f + offset)
    val cy = corner.y + t * (0.5f + offset)
    val width = max(1.5f, t * 0.1f)
    var joined = false
    if (has(x, y - 1)) { drawLine(colour, Offset(cx, cy), Offset(cx, corner.y), width); joined = true }
    if (has(x, y + 1)) { drawLine(colour, Offset(cx, cy), Offset(cx, corner.y + t), width); joined = true }
    if (has(x - 1, y)) { drawLine(colour, Offset(cx, cy), Offset(corner.x, cy), width); joined = true }
    if (has(x + 1, y)) { drawLine(colour, Offset(cx, cy), Offset(corner.x + t, cy), width); joined = true }
    if (!joined) drawCircle(colour, width, Offset(cx, cy))
}

/** Floodwater standing after rain, deeper the bluer. */
internal fun DrawScope.drawFloods(map: CityMap, camera: Camera) {
    val t = camera.tilePx
    forVisibleTiles(map, camera) { x, y, i ->
        val level = map.flood[i].toInt() and 0xff
        if (level == 0) return@forVisibleTiles
        drawRect(FLOODWATER, camera.tileToScreen(x.toFloat(), y.toFloat(), size), Size(t, t), alpha = min(0.65f, level / 255f * 0.9f))
    }
}

private inline fun DrawScope.forVisibleTiles(map: CityMap, camera: Camera, each: (Int, Int, Int) -> Unit) {
    val topLeft = camera.screenToTile(Offset.Zero, size)
    val bottomRight = camera.screenToTile(Offset(size.width, size.height), size)
    val x0 = floor(topLeft.x).toInt().coerceAtLeast(0)
    val y0 = floor(topLeft.y).toInt().coerceAtLeast(0)
    val x1 = ceil(bottomRight.x).toInt().coerceAtMost(map.width - 1)
    val y1 = ceil(bottomRight.y).toInt().coerceAtMost(map.height - 1)
    for (y in y0..y1) for (x in x0..x1) each(x, y, map.index(x, y))
}

private val VEIL = Color(0xB0101820)
private val WATER_MAIN = Color(0xFF4FA3E0)
private val SEWER = Color(0xFFB0824A)
private val STORM_DRAIN = Color(0xFFB8BCC2)
private val WATERED = Color(0xFF7CC4F2)
private val SEWERED = Color(0xFFC9A06A)
private val FLOODWATER = Color(0xFF3F6E9E)
