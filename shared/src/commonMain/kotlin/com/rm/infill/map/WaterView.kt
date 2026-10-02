package com.rm.infill.map

import com.rm.infill.sim.Balance
import com.rm.infill.sim.Power
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.rm.infill.sim.Ageing
import com.rm.infill.sim.Broken
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.Material
import com.rm.infill.sim.Pipe
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * What's under the ground, for laying pipes: the map dimmed, the water mains
 * in blue, the sewers in brown and the storm drains in grey, each a little to
 * one side of the others, and a dot on each tile that has mains water or is on
 * the sewer. Pipes go rusty as they wear towards their expected life, and red
 * where they've broken.
 */
internal fun DrawScope.drawUnderground(map: CityMap, camera: Camera, now: Int) {
    drawRect(VEIL)
    val t = camera.tilePx
    forVisibleTiles(map, camera) { x, y, i ->
        val corner = camera.tileToScreen(x.toFloat(), y.toFloat(), size)
        if (map.watered[i]) drawRect(WATERED, Offset(corner.x + t * 0.1f, corner.y + t * 0.1f), Size(t * 0.14f, t * 0.14f))
        if (map.sewered[i]) drawRect(SEWERED, Offset(corner.x + t * 0.76f, corner.y + t * 0.76f), Size(t * 0.14f, t * 0.14f))
        pipe(map, map.waterPipe, x, y, corner, t, -0.18f, aged(WATER_MAIN, map, i, Pipe.WATER, map.waterPipe, map.waterLaid, Broken.WATER, now))
        pipe(map, map.sewerPipe, x, y, corner, t, 0f, aged(SEWER, map, i, Pipe.SEWER, map.sewerPipe, map.sewerLaid, Broken.SEWER, now))
        pipe(map, map.stormPipe, x, y, corner, t, 0.18f, aged(STORM_DRAIN, map, i, Pipe.STORM, map.stormPipe, map.stormLaid, Broken.STORM, now))
        // Power cable, amber, and high-voltage cable, thicker and red, across the tile's other diagonal.
        if (map.cable(i)) cable(map, x, y, corner, t, now)
        if (map.duct(i)) duct(map, x, y, corner, t)
        // Subway tunnels, wide, through the middle.
        pipe(map, map.subway, x, y, corner, t, 0f, if (map.out(i, Broken.SUBWAY)) BROKEN else TUNNEL, wide = true)
        // Road and rail tunnels, and where they come up.
        if (map.lowRoad[i].toInt() != 0) pipe(map, map.lowRoad, x, y, corner, t, 0f, if (map.tunnelShut(i)) BROKEN else ROAD_TUNNEL, wide = true)
        if (map.lowRail[i].toInt() != 0) pipe(map, map.lowRail, x, y, corner, t, 0f, if (map.tunnelShut(i)) BROKEN else RAIL_TUNNEL, wide = true)
        if (map.portal[i].toInt() != 0) drawCircle(PORTAL, t * 0.22f, Offset(corner.x + t / 2, corner.y + t / 2), style = Stroke(t * 0.08f))
    }
}

/** A pipe's colour, rusting towards its expected life, red if it's out of use. */
private fun aged(colour: Color, map: CityMap, i: Int, kind: Pipe, layer: ByteArray, laid: ShortArray, bit: Int, now: Int): Color {
    if (map.out(i, bit)) return BROKEN
    val material = Material.of(kind, layer[i]) ?: return colour
    val wear = (Ageing.wear(now - laid[i], material.life) / 100f).coerceIn(0f, 1f)
    return lerp(colour, RUST, wear * 0.8f)
}

/**
 * Where the road's shut, barriers across it and the hole the crews have dug;
 * where it's broken up, potholes; where works are waiting to start, a cone.
 */
internal fun DrawScope.drawWorks(map: CityMap, camera: Camera) {
    val t = camera.tilePx
    if (t < MIN_WORKS_PX) return
    forVisibleTiles(map, camera) { x, y, i ->
        // A shut tunnel shows only at its portals, barred.
        val shutPortal = map.portal[i].toInt() != 0 && map.tunnelShut(i)
        if (map.broken[i].toInt() and Broken.LOW.inv() == 0 && !shutPortal) return@forVisibleTiles
        val c = camera.tileToScreen(x.toFloat(), y.toFloat(), size)
        when {
            map.closed(i) || map.out(i, Broken.RAIL) || shutPortal -> {
                drawRect(DUG, Offset(c.x + t * 0.3f, c.y + t * 0.3f), Size(t * 0.4f, t * 0.4f))
                for (edge in listOf(0.12f, 0.8f)) {
                    val y0 = c.y + t * edge
                    var k = 0
                    var xx = c.x + t * 0.1f
                    val w = t * 0.8f / 5
                    while (k < 5) {
                        drawRect(if (k % 2 == 0) BARRIER else Color.White, Offset(xx, y0), Size(w, t * 0.08f))
                        xx += w
                        k++
                    }
                }
            }
            map.potholed(i) -> for ((px, py) in listOf(0.3f to 0.35f, 0.62f to 0.55f, 0.4f to 0.7f)) {
                drawOval(POTHOLE, Offset(c.x + t * px, c.y + t * py), Size(t * 0.14f, t * 0.09f))
            }
            else -> drawCircle(BARRIER, t * 0.07f, Offset(c.x + t * 0.85f, c.y + t * 0.2f))
        }
    }
}

/** One kind of pipe on a tile: from just off its middle out to each side joined to more of it, or a stub if none are. */
private fun DrawScope.pipe(map: CityMap, layer: ByteArray, x: Int, y: Int, corner: Offset, t: Float, offset: Float, colour: Color, wide: Boolean = false) {
    if (layer[map.index(x, y)].toInt() == 0) return
    fun has(nx: Int, ny: Int) = map.inside(nx, ny) && layer[map.index(nx, ny)].toInt() != 0
    val cx = corner.x + t * (0.5f + offset)
    val cy = corner.y + t * (0.5f + offset)
    val width = max(1.5f, t * (if (wide) 0.3f else 0.1f))
    var joined = false
    if (has(x, y - 1)) { drawLine(colour, Offset(cx, cy), Offset(cx, corner.y), width); joined = true }
    if (has(x, y + 1)) { drawLine(colour, Offset(cx, cy), Offset(cx, corner.y + t), width); joined = true }
    if (has(x - 1, y)) { drawLine(colour, Offset(cx, cy), Offset(corner.x, cy), width); joined = true }
    if (has(x + 1, y)) { drawLine(colour, Offset(cx, cy), Offset(corner.x + t, cy), width); joined = true }
    if (!joined) drawCircle(colour, width, Offset(cx, cy))
}

/** Power cable on a tile, joined to the line it carries on as, whether that's underground or up on poles. */
private fun DrawScope.cable(map: CityMap, x: Int, y: Int, corner: Offset, t: Float, now: Int) {
    val i = map.index(x, y)
    val high = map.power[i] == Power.HIGH
    val life = if (high) Balance.HIGH_CABLE_LIFE else Balance.CABLE_LIFE
    val colour = if (map.out(i, Broken.POWER)) BROKEN
    else lerp(if (high) HIGH_CABLE else CABLE, RUST, (Ageing.wear(now - map.powerLaid[i], life) / 100f).coerceIn(0f, 1f) * 0.8f)
    fun has(nx: Int, ny: Int) = map.inside(nx, ny) && map.power[map.index(nx, ny)] == map.power[i]
    val cx = corner.x + t * 0.32f
    val cy = corner.y + t * 0.68f
    val width = max(1.5f, t * (if (high) 0.14f else 0.08f))
    var joined = false
    if (has(x, y - 1)) { drawLine(colour, Offset(cx, cy), Offset(cx, corner.y), width); joined = true }
    if (has(x, y + 1)) { drawLine(colour, Offset(cx, cy), Offset(cx, corner.y + t), width); joined = true }
    if (has(x - 1, y)) { drawLine(colour, Offset(cx, cy), Offset(corner.x, cy), width); joined = true }
    if (has(x + 1, y)) { drawLine(colour, Offset(cx, cy), Offset(corner.x + t, cy), width); joined = true }
    if (!joined) drawCircle(colour, width, Offset(cx, cy))
}

/** A phone duct: green for copper, orange for fibre, near the tile's top right. */
private fun DrawScope.duct(map: CityMap, x: Int, y: Int, corner: Offset, t: Float) {
    val i = map.index(x, y)
    val colour = if (map.out(i, Broken.PHONE)) BROKEN else if (map.phone[i] == com.rm.infill.sim.Phone.FIBRE) FIBRE_DUCT else COPPER_DUCT
    fun has(nx: Int, ny: Int) = map.inside(nx, ny) && map.phone[map.index(nx, ny)].toInt() != 0
    val cx = corner.x + t * 0.72f
    val cy = corner.y + t * 0.28f
    val width = max(1.2f, t * 0.06f)
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
private val RUST = Color(0xFFA8823C)
private val TUNNEL = Color(0xAA9A6AD0)
private val CABLE = Color(0xFFE8A33A)
private val COPPER_DUCT = Color(0xFF6FBF6A)
private val FIBRE_DUCT = Color(0xFFDE7828)
private val HIGH_CABLE = Color(0xFFE0503A)
private val BROKEN = Color(0xFFFF2A2A)
private val DUG = Color(0xFF5A4430)
private val BARRIER = Color(0xFFE8792A)
private val POTHOLE = Color(0xCC2A2622)

/** Below this many pixels a tile, works and potholes aren't drawn. */
private const val MIN_WORKS_PX = 10f

/** Road tunnels grey, rail tunnels dark, portals ringed. */
private val ROAD_TUNNEL = Color(0xDD8C8C90)
private val RAIL_TUNNEL = Color(0xDD4A3A2E)
private val PORTAL = Color(0xFFF2F2F2)
