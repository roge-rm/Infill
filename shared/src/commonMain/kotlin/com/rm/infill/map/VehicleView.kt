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
import com.rm.infill.sim.Stop
import com.rm.infill.sim.drivable
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Carts and, as the years go on, more and more cars along the runs of road
 * and through their junctions. A road has more of them the busier it was
 * last month, and they go slower. They keep to the right, and fill both
 * lanes of a one-way road.
 * [most] is how many there can be in a lane on one tile. At the level
 * [crossings] a train is on, the road is clear and traffic waits either side.
 * A road shut for works has nothing on it.
 */
internal fun DrawScope.drawVehicles(map: CityMap, camera: Camera, year: Int, time: Float, most: Int, crossings: Set<Int> = emptySet(), behind: (Float, Float) -> Boolean = { _, _ -> false }) {
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
    // Whether traffic passes between x, y and its neighbour toward [h] one way or the other: not across the middle of a boulevard.
    fun linked(x: Int, y: Int, h: Int): Boolean {
        val nx = x + Heading.DX[h]
        val ny = y + Heading.DY[h]
        if (!road(nx, ny)) return false
        val a = map.index(x, y)
        val b = map.index(nx, ny)
        return map.drivable(a, b, h) || map.drivable(b, a, Heading.opposite(h))
    }
    // Each row's and column's speed, worked out once a frame.
    val speeds = HashMap<Int, Float>()
    for (y in y0..y1) for (x in x0..x1) {
        val i = map.index(x, y)
        // Nothing on a road that's dug up.
        if (map.road[i].toInt() == 0 || i in crossings || map.closed(i)) continue
        val across = linked(x, y, Heading.WEST.toInt()) || linked(x, y, Heading.EAST.toInt())
        val down = linked(x, y, Heading.NORTH.toInt()) || linked(x, y, Heading.SOUTH.toInt())
        val heading = map.roadHeading[i].toInt()
        // One direction of travel's lanes across tile x, y, for the stretch of tile from [lo] to [hi] along it.
        // Each lane's cars are spaced down the whole row or column and move along it together, so a car carries
        // on from tile to tile and through junctions; how busy a tile is decides which of them show on it.
        fun stream(across: Boolean, way: Int, busy: Int, lo: Float, hi: Float, queue: Boolean, stem: Int = -1) {
            val want = min(most.toFloat(), (busy + 16) / 96f)
            val gap = 1f / most
            for (lane in 0..1) {
                val dir = when {
                    way != 0 -> way
                    across -> if (lane == 0) Heading.EAST.toInt() else Heading.WEST.toInt()
                    else -> if (lane == 0) Heading.NORTH.toInt() else Heading.SOUTH.toInt()
                }
                // The right-hand lane for each way, both lanes on a one-way road.
                val side = if (way != 0) (if (lane == 0) NEAR else FAR) else if (dir == Heading.EAST.toInt() || dir == Heading.NORTH.toInt()) NEAR else FAR
                val forward = dir == Heading.EAST.toInt() || dir == Heading.SOUTH.toInt()
                // Waiting at a crossing just ahead: queued up to it.
                val ax = x + Heading.DX[dir]
                val ay = y + Heading.DY[dir]
                if (queue && crossings.isNotEmpty() && map.inside(ax, ay) && map.index(ax, ay) in crossings) {
                    for (k in 0 until min(most, 1 + busy / 96)) {
                        val seed = x * 7919 + y * 104729 + lane * 31 + k * 977
                        val u = if (forward) 0.8f - k * 0.3f else 0.2f + k * 0.3f
                        val cx = if (across) x + u else x + side
                        val cy = if (across) y + side else y + u
                        if (!behind(cx, cy)) vehicle(camera.tileToScreen(cx, cy, size), t, DEGREES[dir], unit(seed * 5) < cars, seed)
                    }
                    continue
                }
                val line = if (across) y * 2 + lane else LINES + x * 2 + lane
                val speed = speeds.getOrPut(if (across) y else LINES + x) { lineSpeed(map, across, if (across) y else x) }
                // Where the lane's cars are now, wrapped so it stays exact however long the game runs.
                val shift = ((if (forward) time * speed.toDouble() else -time * speed.toDouble()) + unit(line * 7) * gap).mod(gap * WRAP.toDouble()).toFloat()
                val start = (if (across) x else y) + lo
                val end = (if (across) x else y) + hi
                var j = ceil((start - shift) / gap).toInt()
                while (true) {
                    val pos = j * gap + shift
                    if (pos >= end) break
                    // Which car this is, the same all along the lane.
                    val seed = line * 104729 + j.mod(WRAP) * 977
                    j++
                    if (unit(seed) * most > want) continue
                    if (stem >= 0) {
                        // The end of a side street at a junction: in round the corner onto the cross road, or out
                        // of it round the corner, left or right, to the right of the way it's going.
                        val into = (pos - (if (across) x else y)).let { if (forward) it else 1f - it }
                        val inbound = map.index(x - Heading.DX[dir], y - Heading.DY[dir]) == stem
                        val exits = (0 until 4).filter { linked(x, y, it + 1) }.map { map.index(x + Heading.DX[it + 1], y + Heading.DY[it + 1]) }
                            .filter { it != stem && turnable(map, i, it, inbound) }
                        if (exits.isEmpty()) continue
                        val other = exits[(unit(seed * 11) * exits.size).toInt().coerceAtMost(exits.size - 1)]
                        val offset = (side - 0.5f) * (if (dir == Heading.EAST.toInt() || dir == Heading.NORTH.toInt()) 1f else -1f)
                        val (cx, cy, angle) = if (inbound) pointThrough(stem, i, other, into, map.width, offset) else pointThrough(other, i, stem, into, map.width, offset)
                        if (!behind(cx, cy)) vehicle(camera.tileToScreen(cx, cy, size), t, angle, unit(seed * 5) < cars, seed)
                        continue
                    }
                    val cx = if (across) pos else x + side
                    val cy = if (across) y + side else pos
                    if (!behind(cx, cy)) vehicle(camera.tileToScreen(cx, cy, size), t, DEGREES[dir], unit(seed * 5) < cars, seed)
                }
            }
        }
        fun busyAt(x: Int, y: Int) = if (road(x, y)) map.congestion[map.index(x, y)].toInt() and 0xff else 0
        if (across && down) {
            val ways = (0 until 4).filter { linked(x, y, it + 1) }
            if (ways.size == 2 && heading == 0) {
                // Round a bend of a two-way road, each lane in from one side and out the other.
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
                    if (!behind(cx, cy)) vehicle(camera.tileToScreen(cx, cy, size), t, angle, unit(seed * 5) < cars, seed)
                }
                continue
            }
            if (ways.size == 2) {
                // Round a bend of a one-way road or a boulevard's carriageway, both lanes in from the road behind and out to the one ahead.
                val a = map.index(x + Heading.DX[ways[0] + 1], y + Heading.DY[ways[0] + 1])
                val b = map.index(x + Heading.DX[ways[1] + 1], y + Heading.DY[ways[1] + 1])
                val (from, to) = if (map.drivable(a, i, Heading.opposite(ways[0] + 1))) a to b else b to a
                val busy = maxOf(map.congestion[i].toInt() and 0xff, map.congestion[a].toInt() and 0xff, map.congestion[b].toInt() and 0xff)
                if (busy == 0) continue
                val slots = min(most, 1 + busy / 96)
                val speed = BASE_SPEED * (1f - min(0.75f, busy / 340f))
                for (lane in 0..1) for (k in 0 until slots) {
                    val seed = x * 7919 + y * 104729 + lane * 31 + k * 977
                    if (k == 0 && unit(seed) * 128f > busy + 16) continue
                    val along = (time * speed + (k + unit(seed * 3) * 0.5f) / slots) % 1f
                    val (cx, cy, angle) = pointThrough(from, i, to, along, map.width, if (lane == 0) LANE else -LANE)
                    if (!behind(cx, cy)) vehicle(camera.tileToScreen(cx, cy, size), t, angle, unit(seed * 5) < cars, seed)
                }
                continue
            }
            // A junction: each road's streams carry on across it, as busy as the road either side, so the cars
            // seen coming up to it go on through. A side street that stops here turns its cars onto the cross road.
            for (axis in 0..1) {
                val row = axis == 0
                val before = if (row) linked(x, y, Heading.WEST.toInt()) else linked(x, y, Heading.NORTH.toInt())
                val after = if (row) linked(x, y, Heading.EAST.toInt()) else linked(x, y, Heading.SOUTH.toInt())
                val busy = if (row) max(busyAt(x - 1, y), busyAt(x + 1, y)) else max(busyAt(x, y - 1), busyAt(x, y + 1))
                if (busy == 0) continue
                val from = if (row) (if (before) x - 1 else x + 1) else x
                val fromY = if (row) y else (if (before) y - 1 else y + 1)
                // One-way only if the road beside runs one way along this axis.
                val way = map.roadHeading[map.index(from, fromY)].toInt()
                val along = if (row) way == Heading.EAST.toInt() || way == Heading.WEST.toInt() else way == Heading.NORTH.toInt() || way == Heading.SOUTH.toInt()
                val ends = before != after
                stream(row, if (along) way else 0, busy, 0f, 1f, queue = false, stem = if (ends) map.index(from, fromY) else -1)
            }
            continue
        }
        // Along straight runs.
        val busy = map.congestion[i].toInt() and 0xff
        if (busy == 0 || !across && !down) continue
        stream(across, heading, busy, 0f, 1f, queue = true)
    }
}

/**
 * Whether a car can turn at junction [here] onto the road at [other], or in
 * from it when [inbound] is false: not against the way a one-way road runs.
 */
private fun turnable(map: CityMap, here: Int, other: Int, inbound: Boolean): Boolean {
    val h = map.roadHeading[other].toInt()
    if (h == 0) return true
    val dx = other % map.width - here % map.width
    val dy = other / map.width - here / map.width
    val sx = if (inbound) dx else -dx
    val sy = if (inbound) dy else -dy
    return Heading.DX[h] == sx && Heading.DY[h] == sy
}

/** How fast traffic goes along a whole row or column of road, slower the busier it is, in tiles a second. */
private fun lineSpeed(map: CityMap, across: Boolean, at: Int): Float {
    var sum = 0
    var count = 0
    val n = if (across) map.width else map.height
    for (k in 0 until n) {
        val i = if (across) map.index(k, at) else map.index(at, k)
        if (map.road[i].toInt() == 0) continue
        sum += map.congestion[i].toInt() and 0xff
        count++
    }
    val busy = if (count == 0) 0f else sum / count.toFloat()
    return BASE_SPEED * (1f - min(0.75f, busy / 340f))
}

/**
 * Each line's trams, buses or trolleybuses, as many as it has, spaced along
 * its route and going round it out and back: trams down the middle of their
 * track, buses keeping to the right. They pause at each stop, and slow in
 * traffic where there's no lane kept for them; the fuller the line, the more
 * riders show in the windows.
 */
internal fun DrawScope.drawTransit(map: CityMap, camera: Camera, time: Float, lines: Collection<LineState>, behind: (Float, Float) -> Boolean = { _, _ -> false }) {
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
        val stopBit = if (tram) Stop.TRAM else Stop.BUS
        // The line's timetable: how long each tile takes to cross, and the pause at a stop in its middle.
        val moving = FloatArray(n)
        val pause = FloatArray(n)
        val starts = FloatArray(n + 1)
        for (j in 0 until n) {
            val i = route[j]
            val busy = map.congestion[i].toInt() and 0xff
            val jammed = map.road[i].toInt() != 0 && map.lane[i].toInt() == 0
            moving[j] = 1f / speed / (if (jammed) 1f - min(0.75f, busy / 340f) else 1f)
            pause[j] = if (map.stop[i].toInt() and stopBit != 0) STOP_SECONDS else 0f
            starts[j + 1] = starts[j] + moving[j] + pause[j]
        }
        val period = starts[n]
        val seats = if (tram) TRAM_SEATS else BUS_SEATS
        val seated = min(seats, ceil(seats * line.full / 100f).toInt())
        for (k in 0 until line.vehicles) {
            // Where it is on the timetable, then on the route: half across its tile, any pause, the other half.
            val at = (time + k * period / line.vehicles).mod(period)
            var j = 0
            while (j < n - 1 && starts[j + 1] <= at) j++
            val into = at - starts[j]
            val half = moving[j] / 2f
            val pos = when {
                into < half -> j - 0.5f + into / moving[j]
                into < half + pause[j] -> j.toFloat()
                else -> j - 0.5f + (into - pause[j]) / moving[j]
            }
            val i = floor(pos + 0.5f).toInt()
            val u = pos + 0.5f - i
            val (cx, cy, heading) = pointThrough(route[(i - 1 + n) % n], route[i.mod(n)], route[(i + 1) % n], u, map.width, if (tram) 0f else KERB)
            if (cx < topLeft.x - 1 || cx > bottomRight.x + 1 || cy < topLeft.y - 1 || cy > bottomRight.y + 1) continue
            if (behind(cx, cy)) continue
            val spot = camera.tileToScreen(cx, cy, size)
            when (line.mode) {
                Mode.TRAM -> car(spot, t, heading, 0.7f, 0.24f, TRAM_BODY, TRAM_ENDS)
                Mode.TROLLEY -> {
                    car(spot, t, heading, 0.45f, 0.17f, TROLLEY_BODY, GLASS)
                    // Its two poles back and up to the wire.
                    rotate(heading, spot) {
                        val back = Offset(spot.x - t * 0.15f, spot.y)
                        drawLine(POLE, back, Offset(back.x - t * 0.2f, back.y - t * 0.05f), max(1f, t * 0.03f))
                    }
                }
                else -> car(spot, t, heading, 0.45f, 0.17f, BUS_BODY, GLASS)
            }
            if (seated > 0 && t >= RIDERS_TILE_PX) riders(spot, t, heading, if (tram) 0.7f else 0.45f, seats, seated)
        }
    }
}

/** [count] of [seats] riders as heads in a row down the middle of a tram or bus [length] tiles long. */
private fun DrawScope.riders(at: Offset, t: Float, heading: Float, length: Float, seats: Int, count: Int) {
    val inside = t * length * 0.72f
    val r = max(1f, t * 0.03f)
    rotate(heading, at) {
        for (k in 0 until count) {
            val x = at.x - inside / 2 + inside * (k + 0.5f) / seats
            drawCircle(RIDER, r, Offset(x, at.y))
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

/** Cars down a lane repeat after this many places, longer than any row or column of road. */
private const val WRAP = 4096

/** Column lanes are numbered after the rows'. */
private const val LINES = 1 shl 16

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

/** How long a tram or bus waits at a stop, in seconds at normal speed. */
private const val STOP_SECONDS = 1.2f

/** Riders show once a tile is this big, and as heads in this many seats. */
private const val RIDERS_TILE_PX = 28f
private const val TRAM_SEATS = 6
private const val BUS_SEATS = 4
private val RIDER = Color(0xFF1E1A16)

private val TRAM_BODY = Color(0xFFE8DCC0)
private val TRAM_ENDS = Color(0xFF8E2A2A)
private val BUS_BODY = Color(0xFF2F7A5A)
private val TROLLEY_BODY = Color(0xFF2F5F9A)
private val POLE = Color(0xFF2A2A2E)
private val HORSE = Color(0xFF4E3322)
private val GLASS = Color(0xFF2B3440)
private val CART_COLOURS = listOf(Color(0xFF8B6B45), Color(0xFF6E5236), Color(0xFFA07A4A), Color(0xFF3F3A34))
private val CAR_COLOURS = listOf(Color(0xFF26262A), Color(0xFF6B2330), Color(0xFF2E4A3A), Color(0xFF263A5A), Color(0xFFD9CFB0))
