package com.rm.infill.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.Mode
import com.rm.infill.sim.LineState
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
        val across = road(x - 1, y) || road(x + 1, y)
        val down = road(x, y - 1) || road(x, y + 1)
        val heading = map.roadHeading[i].toInt()
        if (across && down) {
            // Round a bend of a two-way road, each lane in from one side and out the other; junctions stay clear.
            val ways = (0 until 4).filter { road(x + Heading.DX[it + 1], y + Heading.DY[it + 1]) }
            if (ways.size != 2 || heading != 0) continue
            val a = map.index(x + Heading.DX[ways[0] + 1], y + Heading.DY[ways[0] + 1])
            val b = map.index(x + Heading.DX[ways[1] + 1], y + Heading.DY[ways[1] + 1])
            // As busy as the busier road either side, so what comes up to the bend is seen going round it.
            val busy = maxOf(map.congestion[i].toInt() and 0xff, map.congestion[a].toInt() and 0xff, map.congestion[b].toInt() and 0xff)
            if (busy == 0) continue
            val slots = min(most, 1 + busy / 96)
            val speed = BASE_SPEED * (1f - min(0.75f, busy / 340f))
            for (lane in 0..1) for (k in 0 until slots) {
                val seed = x * 7919 + y * 104729 + lane * 31 + k * 977
                if (k == 0 && unit(seed) * 128f > busy + 16) continue
                val along = (time * speed + (k + unit(seed * 3) * 0.5f) / slots) % 1f
                val (cx, cy, angle) = if (lane == 0) pointThrough(a, i, b, along, map.width, LANE) else pointThrough(b, i, a, along, map.width, LANE)
                vehicle(camera.tileToScreen(cx, cy, size), t, angle, unit(seed * 5) < cars, seed)
            }
            continue
        }
        // Along straight runs.
        val busy = map.congestion[i].toInt() and 0xff
        if (busy == 0 || !across && !down) continue
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
                vehicle(camera.tileToScreen(cx, cy, size), t, DEGREES[dir], car, seed)
            }
        }
    }
}

/**
 * Each line's trams, buses or trolleybuses, as many as it has, spaced along
 * its route and going round it out and back: trams down the middle of their
 * track, buses keeping to the right.
 */
internal fun DrawScope.drawTransit(map: CityMap, camera: Camera, time: Float, lines: Collection<LineState>) {
    val t = camera.tilePx
    if (t < MIN_TILE_PX) return
    val topLeft = camera.screenToTile(Offset.Zero, size)
    val bottomRight = camera.screenToTile(Offset(size.width, size.height), size)
    for (line in lines) {
        val route = line.route
        if (!line.running || route.size < 2 || line.vehicles <= 0) continue
        val n = route.size
        val tram = line.mode == Mode.TRAM
        val speed = if (tram) TRAM_TILES else BUS_TILES
        for (k in 0 until line.vehicles) {
            // Along the route from tile middle to tile middle, round the bends; buses keep to the right.
            val pos = (time * speed + k * n / line.vehicles.toFloat()) % n
            val i = floor(pos + 0.5f).toInt()
            val u = pos + 0.5f - i
            val (cx, cy, heading) = pointThrough(route[(i - 1 + n) % n], route[i % n], route[(i + 1) % n], u, map.width, if (tram) 0f else KERB)
            if (cx < topLeft.x - 1 || cx > bottomRight.x + 1 || cy < topLeft.y - 1 || cy > bottomRight.y + 1) continue
            val at = camera.tileToScreen(cx, cy, size)
            when (line.mode) {
                Mode.TRAM -> car(at, t, heading, 0.7f, 0.24f, TRAM_BODY, TRAM_ENDS)
                Mode.TROLLEY -> {
                    car(at, t, heading, 0.45f, 0.17f, TROLLEY_BODY, GLASS)
                    // Its two poles back and up to the wire.
                    rotate(heading, at) {
                        val back = Offset(at.x - t * 0.15f, at.y)
                        drawLine(POLE, back, Offset(back.x - t * 0.2f, back.y - t * 0.05f), max(1f, t * 0.03f))
                    }
                }
                else -> car(at, t, heading, 0.45f, 0.17f, BUS_BODY, GLASS)
            }
        }
    }
}

/** A tram, bus or trolleybus centred on [at], [heading] degrees clockwise from east, its ends picked out. */
private fun DrawScope.car(at: Offset, t: Float, heading: Float, length: Float, width: Float, body: Color, ends: Color) {
    val l = t * length
    val w = t * width
    rotate(heading, at) {
        val topLeft = Offset(at.x - l / 2, at.y - w / 2)
        drawRect(body, topLeft, Size(l, w))
        val end = Size(l * 0.12f, w)
        drawRect(ends, topLeft, end)
        drawRect(ends, Offset(topLeft.x + l - end.width, topLeft.y), end)
    }
}

/** One vehicle centred on [at], [heading] degrees clockwise from east: a horse and cart, or a car. */
private fun DrawScope.vehicle(at: Offset, t: Float, heading: Float, car: Boolean, seed: Int) {
    val length = t * 0.28f
    val width = t * 0.13f
    val body = if (car) CAR_COLOURS[(seed ushr 3).mod(CAR_COLOURS.size)] else CART_COLOURS[(seed ushr 3).mod(CART_COLOURS.size)]
    // Drawn facing east, then turned.
    rotate(heading, at) {
        val topLeft = Offset(at.x - length / 2, at.y - width / 2)
        drawRect(body, topLeft, Size(length, width))
        // The front: a horse ahead of a cart, or a windscreen.
        val front = if (car) 0.3f else 0.4f
        val part = Size(length * front, width * (if (car) 0.8f else 0.6f))
        drawRect(if (car) GLASS else HORSE, Offset(topLeft.x + length * (1 - front), topLeft.y + (width - part.height) / 2), part)
    }
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

/** How far right of the middle a lane is, round a bend. */
private const val LANE = NEAR - 0.5f

/** Each heading as degrees clockwise from east, by its number: none, north, east, south, west. */
private val DEGREES = floatArrayOf(0f, 270f, 0f, 90f, 180f)

/** Too small to see below this. */
private const val MIN_TILE_PX = 12f

/** Tiles a second a tram and a bus go along their line, and how far to the right of the middle a bus keeps. */
private const val TRAM_TILES = 0.55f
private const val BUS_TILES = 0.45f
private const val KERB = 0.15f

private val TRAM_BODY = Color(0xFFE8DCC0)
private val TRAM_ENDS = Color(0xFF8E2A2A)
private val BUS_BODY = Color(0xFF2F7A5A)
private val TROLLEY_BODY = Color(0xFF2F5F9A)
private val POLE = Color(0xFF2A2A2E)
private val HORSE = Color(0xFF4E3322)
private val GLASS = Color(0xFF2B3440)
private val CART_COLOURS = listOf(Color(0xFF8B6B45), Color(0xFF6E5236), Color(0xFFA07A4A), Color(0xFF3F3A34))
private val CAR_COLOURS = listOf(Color(0xFF26262A), Color(0xFF6B2330), Color(0xFF2E4A3A), Color(0xFF263A5A), Color(0xFFD9CFB0))
