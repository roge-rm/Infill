package com.rm.infill.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.ShipRoute
import kotlin.math.min

/**
 * Ships on the way in to the ports they came to last month: in from the edge
 * of the map, tied up a while at the quay, and out again. A busier port has
 * more of them, up to [most].
 */
internal fun DrawScope.drawShips(routes: List<ShipRoute>, map: CityMap, camera: Camera, time: Float, most: Int, smoke: Boolean) {
    val t = camera.tilePx
    if (most == 0 || routes.isEmpty() || t < MIN_SHIP_PX) return
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
            val (x, y, heading) = pointOn(path, s, map.width)
            if (x < topLeft.x - 2 || y < topLeft.y - 2 || x > bottomRight.x + 2 || y > bottomRight.y + 2) continue
            val point = camera.tileToScreen(x, y, size)
            val berthed = at >= length
            // Coming alongside, a ship swings round to lie along the quay.
            val way = if (forward) heading else heading + 180f
            val quay = alongQuay(map, path.last()).let { q -> if (kotlin.math.abs(turn(way, q)) > 90f) q + 180f else q }
            val near = ((s - (length - SWING)) / SWING).coerceIn(0f, 1f)
            val facing = way + turn(way, quay) * near
            ship(point, t, facing, route.kind, k + n, wake = !berthed)
            if (smoke && route.kind != ShipRoute.CONTAINER && route.kind != ShipRoute.LINER) puffs(point, t, time, k * 5 + n)
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
