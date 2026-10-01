package com.rm.infill.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.Heading
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Carts and, as the years go on, more and more cars along the straight runs
 * of road. A road has more of them the busier it was last month, and they go
 * slower. They keep to the right, and fill both lanes of a one-way road.
 * [most] is how many there can be in a lane on one tile. At the level
 * [crossings] a train is on, the road is clear and traffic waits either side.
 * A road shut for works has nothing on it.
 */
internal fun DrawScope.drawVehicles(map: CityMap, camera: Camera, year: Int, time: Float, most: Int, crossings: Set<Int> = emptySet()) {
    val t = camera.tilePx
    if (most == 0 || t < MIN_TILE_PX) return
    val topLeft = camera.screenToTile(Offset.Zero, size)
    val bottomRight = camera.screenToTile(Offset(size.width, size.height), size)
    val x0 = floor(topLeft.x).toInt().coerceAtLeast(0)
    val y0 = floor(topLeft.y).toInt().coerceAtLeast(0)
    val x1 = ceil(bottomRight.x).toInt().coerceAtMost(map.width - 1)
    val y1 = ceil(bottomRight.y).toInt().coerceAtMost(map.height - 1)
    // Cars start to show up after 1905 and are most of the traffic by the 1940s.
    val cars = ((year - 1905) / 40f).coerceIn(0f, 0.9f)
    fun road(x: Int, y: Int) = map.inside(x, y) && map.road[map.index(x, y)].toInt() != 0
    for (y in y0..y1) for (x in x0..x1) {
        val i = map.index(x, y)
        // Nothing on a road that's dug up.
        if (map.road[i].toInt() == 0 || i in crossings || map.closed(i)) continue
        val busy = map.congestion[i].toInt() and 0xff
        if (busy == 0) continue
        val across = road(x - 1, y) || road(x + 1, y)
        val down = road(x, y - 1) || road(x, y + 1)
        // Only along straight runs; junctions and bends stay clear.
        if (across == down) continue
        val heading = map.roadHeading[i].toInt()
        val slots = min(most, 1 + busy / 96)
        val speed = BASE_SPEED * (1f - min(0.75f, busy / 340f))
        for (lane in 0..1) {
            val dir = when {
                heading != 0 -> heading
                across -> if (lane == 0) Heading.EAST.toInt() else Heading.WEST.toInt()
                else -> if (lane == 0) Heading.NORTH.toInt() else Heading.SOUTH.toInt()
            }
            // The right-hand lane for each way, both lanes on a one-way road.
            val side = if (heading != 0) (if (lane == 0) NEAR else FAR) else if (dir == Heading.EAST.toInt() || dir == Heading.NORTH.toInt()) NEAR else FAR
            for (k in 0 until slots) {
                val seed = x * 7919 + y * 104729 + lane * 31 + k * 977
                // A quiet road only has something on it now and then.
                if (k == 0 && unit(seed) * 128f > busy + 16) continue
                val forward = dir == Heading.EAST.toInt() || dir == Heading.SOUTH.toInt()
                // Waiting at a crossing just ahead, queued up to it.
                val ax = x + Heading.DX[dir]
                val ay = y + Heading.DY[dir]
                val waiting = crossings.isNotEmpty() && map.inside(ax, ay) && map.index(ax, ay) in crossings
                val along = if (waiting) 0.8f - k * 0.3f else (time * speed + (k + unit(seed * 3) * 0.5f) / slots) % 1f
                val u = if (forward) along else 1f - along
                val cx = if (across) x + u else x + side
                val cy = if (across) y + side else y + u
                val car = unit(seed * 5) < cars
                vehicle(camera.tileToScreen(cx, cy, size), t, dir, car, seed)
            }
        }
    }
}

/**
 * Trams along their track, and buses and trolleybuses on their roads, more
 * of them the more riders there were, on straight runs where the riding was:
 * [tramAt], [busAt] and [trolleyAt] give last month's riders on a tile.
 */
internal fun DrawScope.drawTransit(map: CityMap, camera: Camera, time: Float, tramAt: (Int) -> Int, busAt: (Int) -> Int, trolleyAt: (Int) -> Int) {
    val t = camera.tilePx
    if (t < MIN_TILE_PX) return
    val topLeft = camera.screenToTile(Offset.Zero, size)
    val bottomRight = camera.screenToTile(Offset(size.width, size.height), size)
    val x0 = floor(topLeft.x).toInt().coerceAtLeast(0)
    val y0 = floor(topLeft.y).toInt().coerceAtLeast(0)
    val x1 = ceil(bottomRight.x).toInt().coerceAtMost(map.width - 1)
    val y1 = ceil(bottomRight.y).toInt().coerceAtMost(map.height - 1)
    fun road(x: Int, y: Int) = map.inside(x, y) && map.road[map.index(x, y)].toInt() != 0
    for (y in y0..y1) for (x in x0..x1) {
        val i = map.index(x, y)
        if (map.road[i].toInt() == 0 || map.closed(i)) continue
        val across = road(x - 1, y) || road(x + 1, y)
        val down = road(x, y - 1) || road(x, y + 1)
        if (across == down) continue
        val seed = x * 7919 + y * 104729
        val trams = tramAt(i)
        if (trams > 0 && map.tram[i].toInt() != 0) {
            // A tram every few tiles, sliding along; the busier the line, the closer they come.
            val gap = if (trams > 2000) 3 else if (trams > 500) 5 else 8
            val u = (time * TRAM_SPEED + (if (across) x else y) / gap.toFloat() + unit(seed) * 0.1f) % 1f
            if (u < 1f / gap * 3) {
                val along = u * gap / 3
                val cx = if (across) x + along else x + 0.5f
                val cy = if (across) y + 0.5f else y + along
                car(camera.tileToScreen(cx, cy, size), t, across, 0.7f, 0.24f, TRAM_BODY, TRAM_ENDS)
            }
        }
        val trolleys = trolleyAt(i)
        if (trolleys > 0) {
            val gap = if (trolleys > 1500) 4 else if (trolleys > 400) 6 else 10
            val u = (time * BASE_SPEED * 0.8f + (if (across) x else y) / gap.toFloat() + unit(seed * 11) * 0.2f) % 1f
            if (u < 1f / gap * 2) {
                val along = u * gap / 2
                val cx = if (across) x + along else x + NEAR
                val cy = if (across) y + NEAR else y + along
                val at = camera.tileToScreen(cx, cy, size)
                car(at, t, across, 0.45f, 0.17f, TROLLEY_BODY, GLASS)
                // Its two poles up to the wire.
                val back = if (across) Offset(at.x - t * 0.15f, at.y) else Offset(at.x, at.y - t * 0.15f)
                val up = if (across) Offset(back.x - t * 0.2f, back.y - t * 0.05f) else Offset(back.x - t * 0.05f, back.y - t * 0.2f)
                drawLine(POLE, back, up, max(1f, t * 0.03f))
            }
        }
        val buses = busAt(i)
        if (buses > 0) {
            val gap = if (buses > 1500) 4 else if (buses > 400) 6 else 10
            val u = (time * BASE_SPEED * 0.8f + (if (across) x else y) / gap.toFloat() + unit(seed * 7) * 0.2f) % 1f
            if (u < 1f / gap * 2) {
                val along = u * gap / 2
                val cx = if (across) x + along else x + NEAR
                val cy = if (across) y + NEAR else y + along
                car(camera.tileToScreen(cx, cy, size), t, across, 0.45f, 0.17f, BUS_BODY, GLASS)
            }
        }
    }
}

/** A long vehicle lying [across] or down, [length] and [width] in tiles, with its ends in [ends]. */
private fun DrawScope.car(at: Offset, t: Float, across: Boolean, length: Float, width: Float, body: Color, ends: Color) {
    val l = t * length
    val w = t * width
    val size = if (across) Size(l, w) else Size(w, l)
    val topLeft = Offset(at.x - size.width / 2, at.y - size.height / 2)
    drawRect(body, topLeft, size)
    val end = if (across) Size(l * 0.12f, w) else Size(w, l * 0.12f)
    drawRect(ends, topLeft, end)
    drawRect(ends, if (across) Offset(topLeft.x + l - end.width, topLeft.y) else Offset(topLeft.x, topLeft.y + l - end.height), end)
}

/** One vehicle centred on [at], facing [dir]: a horse and cart, or a car. */
private fun DrawScope.vehicle(at: Offset, t: Float, dir: Int, car: Boolean, seed: Int) {
    val along = dir == Heading.EAST.toInt() || dir == Heading.WEST.toInt()
    val length = t * 0.28f
    val width = t * 0.13f
    val size = if (along) Size(length, width) else Size(width, length)
    val topLeft = Offset(at.x - size.width / 2, at.y - size.height / 2)
    val body = if (car) CAR_COLOURS[(seed ushr 3).mod(CAR_COLOURS.size)] else CART_COLOURS[(seed ushr 3).mod(CART_COLOURS.size)]
    drawRect(body, topLeft, size)
    // The front: a horse ahead of a cart, or a windscreen.
    val front = if (car) 0.3f else 0.4f
    val frontColour = if (car) GLASS else HORSE
    val part = if (along) Size(length * front, width * (if (car) 0.8f else 0.6f)) else Size(width * (if (car) 0.8f else 0.6f), length * front)
    val inset = if (along) Offset(0f, (width - part.height) / 2) else Offset((width - part.width) / 2, 0f)
    val frontAt = when (dir) {
        Heading.EAST.toInt() -> Offset(topLeft.x + length * (1 - front), topLeft.y + inset.y)
        Heading.WEST.toInt() -> Offset(topLeft.x, topLeft.y + inset.y)
        Heading.SOUTH.toInt() -> Offset(topLeft.x + inset.x, topLeft.y + length * (1 - front))
        else -> Offset(topLeft.x + inset.x, topLeft.y)
    }
    drawRect(frontColour, frontAt, part)
}

private fun unit(n: Int): Float {
    var h = n * 374761393
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0xffff) / 65536f
}

/** Tiles a second on a clear road. */
private const val BASE_SPEED = 0.5f

/**
 * Where the two lanes run across a tile: either side of the centre line,
 * which is at the tile's middle, with room for a car between it and the kerb.
 */
private const val NEAR = 0.65f
private const val FAR = 0.35f

/** Too small to see below this. */
private const val MIN_TILE_PX = 12f

/** How far a tram slides along its run each second, as a share of the gap between trams. */
private const val TRAM_SPEED = 0.06f

private val TRAM_BODY = Color(0xFFE8DCC0)
private val TRAM_ENDS = Color(0xFF8E2A2A)
private val BUS_BODY = Color(0xFF2F7A5A)
private val TROLLEY_BODY = Color(0xFF2F5F9A)
private val POLE = Color(0xFF2A2A2E)
private val HORSE = Color(0xFF4E3322)
private val GLASS = Color(0xFF2B3440)
private val CART_COLOURS = listOf(Color(0xFF8B6B45), Color(0xFF6E5236), Color(0xFFA07A4A), Color(0xFF3F3A34))
private val CAR_COLOURS = listOf(Color(0xFF26262A), Color(0xFF6B2330), Color(0xFF2E4A3A), Color(0xFF263A5A), Color(0xFFD9CFB0))
