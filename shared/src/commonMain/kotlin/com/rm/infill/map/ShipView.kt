package com.rm.infill.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.rm.infill.sim.Bridge
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.ShipRoute
import kotlin.math.min

/**
 * Ships on the way in to the ports they came to last month: in from the edge
 * of the map, tied up a while at the quay, and out again. A busier port has
 * more of them, up to [most].
 */
internal fun DrawScope.drawShips(routes: List<ShipRoute>, map: CityMap, camera: Camera, time: Float, most: Int, smoke: Boolean): Set<Int> {
    val t = camera.tilePx
    if (most == 0 || routes.isEmpty() || t < MIN_SHIP_PX) return emptySet()
    // Where each ship is, worked out first so the bridges they pass can open before they're drawn.
    val placed = ArrayList<Placed>()
    val open = HashSet<Int>()
    val topLeft = camera.screenToTile(Offset.Zero, size)
    val bottomRight = camera.screenToTile(Offset(size.width, size.height), size)
    for ((k, route) in routes.withIndex()) {
        val path = route.tiles
        if (path.size < 2) continue
        val length = (path.size - 1).toFloat()
        val ships = min(most, route.ships)
        val leg = length / SHIP_SPEED
        val period = 2 * leg + BERTHED + AWAY
        for (n in 0 until ships) {
            val phase = ((time + (k * 0.61f + n.toFloat() / ships) * period) % period + period) % period
            // In, tied up, out, then gone a while.
            val (at, forward) = when {
                phase < leg -> length * ease(phase / leg) to true
                phase < leg + BERTHED -> length to true
                phase < 2 * leg + BERTHED -> length * (1 - ease((phase - leg - BERTHED) / leg)) to false
                else -> continue
            }
            // Tied up a little short of the berth, so a second ship waits behind the first.
            val s = (at - n * SHIP_GAP * (if (at >= length) 1 else 0)).coerceIn(0f, length)
            // A lifting or swing bridge opens while a ship's near it.
            for (k in maxOf(0, (s - OPEN_REACH).toInt())..minOf(path.size - 1, (s + OPEN_REACH).toInt() + 1)) {
                val i = path[k]
                if (map.bridged(i) && map.clearance(i) == Bridge.OPENS) open += i
            }
            val (x, y, heading) = pointOn(path, s, map.width)
            if (x < topLeft.x - 2 || y < topLeft.y - 2 || x > bottomRight.x + 2 || y > bottomRight.y + 2) continue
            val point = camera.tileToScreen(x, y, size)
            val berthed = at >= length
            // Coming alongside, a ship swings round to lie along the quay.
            val way = if (forward) heading else heading + 180f
            val quay = alongQuay(map, path.last()).let { q -> if (kotlin.math.abs(turn(way, q)) > 90f) q + 180f else q }
            val near = ((s - (length - SWING)) / SWING).coerceIn(0f, 1f)
            val facing = way + turn(way, quay) * near
            placed += Placed(point, facing, route.kind, k + n, !berthed, smoke && route.kind != ShipRoute.CONTAINER && route.kind != ShipRoute.LINER)
        }
    }
    // The whole of each bridge that's opening, from bank to bank.
    val spans = HashSet<Int>()
    for (i in open) spans += bridgeTiles(map, i)
    drawOpenBridges(spans, map, camera)
    for (p in placed) {
        ship(p.at, t, p.facing, p.kind, p.seed, wake = p.wake)
        if (p.smoke) puffs(p.at, t, time, p.seed)
    }
    return spans
}

private class Placed(val at: Offset, val facing: Float, val kind: Int, val seed: Int, val wake: Boolean, val smoke: Boolean)

/** The tiles of the bridge [i] is on, bank to bank. */
private fun bridgeTiles(map: CityMap, i: Int): List<Int> {
    val ew = map.bridge[i].toInt() and Bridge.ACROSS != 0
    val dx = if (ew) 1 else 0
    val dy = if (ew) 0 else 1
    val x = i % map.width
    val y = i / map.width
    val out = ArrayList<Int>()
    var k = 0
    while (map.inside(x - dx * k, y - dy * k) && map.bridged(map.index(x - dx * k, y - dy * k))) { out += map.index(x - dx * k, y - dy * k); k++ }
    k = 1
    while (map.inside(x + dx * k, y + dy * k) && map.bridged(map.index(x + dx * k, y + dy * k))) { out += map.index(x + dx * k, y + dy * k); k++ }
    return out
}

/**
 * Bridges open for a ship: a lift bridge's span raised on its towers, its
 * shadow on the water where it was; a swing bridge's span turned on its
 * pivot to lie along the channel.
 */
private fun DrawScope.drawOpenBridges(tiles: Set<Int>, map: CityMap, camera: Camera) {
    val t = camera.tilePx
    val done = HashSet<Int>()
    for (i in tiles) {
        if (i in done) continue
        val span = bridgeTiles(map, i)
        done += span
        val kind = map.bridgeKind(i) ?: continue
        val ew = map.bridge[i].toInt() and Bridge.ACROSS != 0
        val xs = span.map { it % map.width }
        val ys = span.map { it / map.width }
        val x0 = xs.min().toFloat()
        val y0 = ys.min().toFloat()
        val x1 = xs.max() + 1f
        val y1 = ys.max() + 1f
        val topLeft = camera.tileToScreen(x0, y0, size)
        val bottomRight = camera.tileToScreen(x1, y1, size)
        val w = bottomRight.x - topLeft.x
        val h = bottomRight.y - topLeft.y
        // The water shows where the deck was.
        drawRect(OPEN_WATER, topLeft, Size(w, h))
        if (kind == com.rm.infill.sim.BridgeKind.SWING) {
            // Turned square to the road, on the middle of the span.
            val mx = (topLeft.x + bottomRight.x) / 2
            val my = (topLeft.y + bottomRight.y) / 2
            val long = if (ew) w else h
            val wide = t * 0.8f
            val size = if (ew) Size(wide, long) else Size(long, wide)
            drawRect(DECK_SHADOW, Offset(mx - size.width / 2 + t * 0.1f, my - size.height / 2 + t * 0.1f), size)
            drawRect(STEEL, Offset(mx - size.width / 2, my - size.height / 2), size)
            drawCircle(PIVOT, t * 0.3f, Offset(mx, my))
        } else {
            // Raised: its shadow where it was, the span itself lifted and drawn a little up the screen.
            val lift = t * 0.6f
            drawRect(DECK_SHADOW, topLeft + Offset(t * 0.15f, t * 0.15f), Size(w, h))
            drawRect(STEEL, topLeft - Offset(0f, lift), Size(w, h))
            drawRect(STEEL_EDGE, topLeft - Offset(0f, lift), Size(w, h), style = androidx.compose.ui.graphics.drawscope.Stroke(t * 0.06f))
        }
    }
}

/** The shortest turn from [from] to [to], in degrees, -180 to 180. */
private fun turn(from: Float, to: Float): Float = ((to - from) % 360f + 540f) % 360f - 180f

/** Which way a ship tied up at [berth] lies: along the shore, north to south if the land's east or west of it. */
private fun alongQuay(map: CityMap, berth: Int): Float {
    val x = berth % map.width
    val y = berth / map.width
    fun land(dx: Int, dy: Int) = map.inside(x + dx, y + dy) && map.terrain[map.index(x + dx, y + dy)] != com.rm.infill.sim.Terrain.WATER
    return if (land(1, 0) || land(-1, 0)) 90f else 0f
}

private fun DrawScope.ship(at: Offset, t: Float, heading: Float, kind: Int, seed: Int, wake: Boolean) {
    val long = t * LENGTHS[kind]
    val wide = t * WIDTHS[kind]
    // Drawn bow to the east, then turned the way it's going.
    rotate(heading, at) {
        val stern = at.x - long / 2
        val bow = at.x + long / 2
        val hull = Path().apply {
            moveTo(stern, at.y - wide / 2)
            lineTo(bow - wide * 0.7f, at.y - wide / 2)
            lineTo(bow, at.y)
            lineTo(bow - wide * 0.7f, at.y + wide / 2)
            lineTo(stern, at.y + wide / 2)
            close()
        }
        // A wake behind, then the hull.
        if (wake) {
            val trail = Path().apply {
                moveTo(stern, at.y - wide * 0.3f)
                lineTo(stern - long * 0.5f, at.y - wide * 0.55f)
                lineTo(stern - long * 0.5f, at.y + wide * 0.55f)
                lineTo(stern, at.y + wide * 0.3f)
                close()
            }
            drawPath(trail, WAKE, alpha = 0.18f)
        }
        drawPath(hull, HULLS[kind])
        fun block(from: Float, to: Float, share: Float, colour: Color) {
            drawRect(colour, Offset(stern + long * from, at.y - wide * share / 2), Size(long * (to - from), wide * share))
        }
        when (kind) {
            ShipRoute.STEAMER -> {
                block(0.08f, 0.75f, 0.7f, DECK)
                block(0.35f, 0.55f, 0.55f, CABIN)
                drawCircle(FUNNEL, wide * 0.18f, Offset(stern + long * 0.45f, at.y))
            }
            ShipRoute.COLLIER -> {
                block(0.08f, 0.8f, 0.7f, DECK)
                for (h in 0 until 3) block(0.15f + h * 0.2f, 0.3f + h * 0.2f, 0.55f, COAL_HOLD)
                block(0.05f, 0.15f, 0.6f, CABIN)
                drawCircle(FUNNEL, wide * 0.16f, Offset(stern + long * 0.1f, at.y))
            }
            ShipRoute.TANKER -> {
                block(0.06f, 0.8f, 0.7f, TANKER_DECK)
                drawRect(PIPE, Offset(stern + long * 0.15f, at.y - wide * 0.04f), Size(long * 0.6f, wide * 0.08f))
                block(0.04f, 0.14f, 0.65f, CABIN)
            }
            ShipRoute.LINER -> {
                block(0.08f, 0.8f, 0.75f, LINER_DECK)
                block(0.2f, 0.7f, 0.5f, CABIN)
                for (f in 0 until 2) drawCircle(LINER_FUNNEL, wide * 0.15f, Offset(stern + long * (0.38f + f * 0.16f), at.y))
            }
            ShipRoute.CONTAINER -> {
                // Rows of boxes, the bridge at the stern.
                var x = 0.16f
                var n = seed
                while (x < 0.82f) {
                    block(x, x + 0.07f, 0.8f, BOXES[n.mod(BOXES.size)])
                    x += 0.08f
                    n += 3
                }
                block(0.04f, 0.14f, 0.7f, CABIN)
            }
        }
    }
}

/** Tiles a second under way, seconds tied up and away, and how far behind a second ship waits. */
private const val SHIP_SPEED = 1.1f
private const val BERTHED = 14f
private const val AWAY = 10f
private const val SHIP_GAP = 2.4f

/** Tiles from the berth over which a ship swings round to come alongside. */
private const val SWING = 2f

private const val MIN_SHIP_PX = 8f

/** How near a ship is, in tiles along its way, when a bridge opens for it. */
private const val OPEN_REACH = 1.5f

private val OPEN_WATER = Color(0xFF3A6FB0)
private val DECK_SHADOW = Color(0x660A192D)
private val STEEL = Color(0xFF5C636B)
private val STEEL_EDGE = Color(0xFF3F4A56)
private val PIVOT = Color(0xFF8E8A82)

// By kind: steamer, collier, tanker, liner, container ship.
private val LENGTHS = floatArrayOf(1.3f, 1.6f, 2.0f, 2.2f, 2.6f)
private val WIDTHS = floatArrayOf(0.42f, 0.48f, 0.55f, 0.55f, 0.62f)
private val HULLS = listOf(Color(0xFF2A2A30), Color(0xFF3A2A24), Color(0xFF6A2A24), Color(0xFFF2F2EE), Color(0xFF2A3A5A))

private val DECK = Color(0xFF9A7A52)
private val CABIN = Color(0xFFE8E4DA)
private val FUNNEL = Color(0xFF1A1A1E)
private val COAL_HOLD = Color(0xFF18181C)
private val TANKER_DECK = Color(0xFF8A3A30)
private val PIPE = Color(0xFFC9B48A)
private val LINER_DECK = Color(0xFFC9B48A)
private val LINER_FUNNEL = Color(0xFFC0392B)
private val WAKE = Color(0xFFE8F2F8)
private val BOXES = listOf(Color(0xFFB5452F), Color(0xFF2F5F9A), Color(0xFF3F8A4A), Color(0xFFD08A2A), Color(0xFF8A8F96), Color(0xFF7A3F7A))
