package com.rm.infill.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.rm.infill.sim.Balance
import com.rm.infill.sim.Callout
import com.rm.infill.sim.CalloutKind
import com.rm.infill.sim.CityMap
import kotlin.math.floor
import kotlin.math.max

/**
 * The service vehicles out on the roads: each callout from the sim is driven
 * once, along its route and through its stops, then it's gone. Kept for the
 * life of the map view; moved on by the game-speed clock, so they stop when
 * the game's paused.
 */
internal class ServiceTrips {
    private class Trip(val c: Callout) {
        /** Where it is along the route, in tiles from the start. */
        var pos = 0f
        var wait = 0f
        /** The next of its stops. */
        var next = 0
        /** On its way out, lights on if it's urgent. */
        val out get() = next == 0
    }

    private val trips = ArrayList<Trip>()
    private var newest = 0
    private var last = Float.NaN

    /** Picks up new callouts and moves every trip on to [time], in [year]. */
    fun update(callouts: List<Callout>, map: CityMap, time: Float, year: Int) {
        val dt = if (last.isNaN()) 0f else (time - last).coerceIn(0f, MAX_STEP)
        last = time
        for (c in callouts) if (c.id > newest) {
            if (trips.size < MOST) trips += Trip(c)
            newest = c.id
        }
        val gone = ArrayList<Trip>()
        for (t in trips) {
            val c = t.c
            if (t.wait > 0f) {
                t.wait -= dt
                // A fire engine stays till the fire's out.
                if (c.kind == CalloutKind.FIRE && map.fire[c.at] > 0) t.wait = max(t.wait, HOLD)
                continue
            }
            val horse = horseDrawn(c.kind, year)
            t.pos += dt * when {
                c.urgent && t.out -> if (horse) HORSE_URGENT_TILES else URGENT_TILES
                else -> SLOW_TILES
            }
            val stop = c.stops.getOrNull(t.next)
            if (stop != null && t.pos >= stop) {
                t.pos = stop.toFloat()
                t.wait = DWELL[c.kind.ordinal]
                t.next++
            }
            if (t.pos >= c.route.size - 1) gone += t
        }
        trips.removeAll(gone.toSet())
    }

    /** Each trip's vehicle where it is now, as it was in [year]. */
    fun DrawScope.draw(map: CityMap, camera: Camera, year: Int, time: Float) {
        val t = camera.tilePx
        if (t < MIN_TILE_PX) return
        val topLeft = camera.screenToTile(Offset.Zero, size)
        val bottomRight = camera.screenToTile(Offset(size.width, size.height), size)
        // Lights flash twice a second, at the game's speed.
        val flash = floor(time * 4f).toInt() % 2 == 0
        for (trip in trips) {
            val c = trip.c
            val route = c.route
            val n = route.size
            val i = floor(trip.pos + 0.5f).toInt().coerceIn(0, n - 1)
            val u = (trip.pos + 0.5f - i).coerceIn(0f, 1f)
            val (cx, cy, heading) = pointThrough(if (i > 0) route[i - 1] else -1, route[i], if (i < n - 1) route[i + 1] else -1, u, map.width, KERB)
            if (cx < topLeft.x - 1 || cx > bottomRight.x + 1 || cy < topLeft.y - 1 || cy > bottomRight.y + 1) continue
            val spot = camera.tileToScreen(cx, cy, size)
            val lights = c.urgent && (trip.out || c.kind == CalloutKind.FIRE && trip.wait > 0f && map.fire[c.at] > 0)
            vehicle(spot, t, heading, c.kind, year, if (lights) flash else null)
        }
    }
}

/**
 * A fire engine, ambulance, police car or garbage truck centred on [at],
 * [heading] degrees clockwise from east, drawn by a horse before its motor
 * came in. [flash] is which of its lights is lit, null with them off.
 */
private fun DrawScope.vehicle(at: Offset, t: Float, heading: Float, kind: CalloutKind, year: Int, flash: Boolean?) {
    val horse = horseDrawn(kind, year)
    val length = t * LENGTH[kind.ordinal]
    val width = t * 0.17f
    rotate(heading, at) {
        val left = at.x - length / 2
        val top = at.y - width / 2
        val body = if (kind == CalloutKind.POLICE && year >= WHITE_POLICE_YEAR) POLICE_LATE else BODY[kind.ordinal]
        if (horse) {
            // The cart behind, the horse ahead of it.
            val cart = length * 0.6f
            drawRect(body, Offset(left, top), Size(cart, width))
            drawRect(HORSE, Offset(left + cart + length * 0.05f, at.y - width * 0.25f), Size(length * 0.35f, width * 0.5f))
            if (kind == CalloutKind.FIRE) drawRect(BRASS, Offset(left + cart * 0.55f, at.y - width * 0.3f), Size(cart * 0.3f, width * 0.6f))
            return@rotate
        }
        drawRect(body, Offset(left, top), Size(length, width))
        // The cab at the front.
        val cab = length * if (kind == CalloutKind.POLICE) 0.3f else 0.22f
        drawRect(GLASS, Offset(left + length - cab, top + width * 0.12f), Size(cab * 0.7f, width * 0.76f))
        when (kind) {
            // A ladder down its back.
            CalloutKind.FIRE -> drawRect(LADDER, Offset(left + length * 0.08f, at.y - width * 0.15f), Size(length * 0.6f, width * 0.3f))
            CalloutKind.AMBULANCE -> {
                val s = width * 0.5f
                drawRect(CROSS, Offset(left + length * 0.3f - s / 2, at.y - s / 6), Size(s, s / 3))
                drawRect(CROSS, Offset(left + length * 0.3f - s / 6, at.y - s / 2), Size(s / 3, s))
            }
            CalloutKind.POLICE -> drawRect(GLASS, Offset(left + length * 0.12f, top + width * 0.12f), Size(length * 0.2f, width * 0.76f))
            CalloutKind.GARBAGE -> drawRect(BIN, Offset(left + length * 0.06f, top + width * 0.1f), Size(length * 0.66f, width * 0.8f))
        }
        if (flash != null) {
            val r = max(1f, width * 0.22f)
            val x = left + length - cab - r * 1.2f
            val second = if (kind == CalloutKind.POLICE) BLUE else RED
            drawCircle(if (flash) RED else DIM, r, Offset(x, at.y - width * 0.25f))
            drawCircle(if (flash) DIM else second, r, Offset(x, at.y + width * 0.25f))
        }
    }
}

/** Whether [kind] was still drawn by a horse in [year]. */
private fun horseDrawn(kind: CalloutKind, year: Int) = year < when (kind) {
    CalloutKind.FIRE -> Balance.MOTOR_FIRE_YEAR
    CalloutKind.AMBULANCE -> MOTOR_AMBULANCE_YEAR
    CalloutKind.POLICE -> Balance.PATROL_CAR_YEAR
    CalloutKind.GARBAGE -> MOTOR_TRUCK_YEAR
}

/** Too small to see below this. */
private const val MIN_TILE_PX = 12f

/** Trips at once, at most; more callouts than this wait for none and aren't shown. */
private const val MOST = 60

/** The longest step a trip takes in one frame, in seconds, so a stall doesn't send it jumping on. */
private const val MAX_STEP = 0.25f

/** How far right of the middle of the road it keeps. */
private const val KERB = 0.15f

/** Tiles a second: with lights on, a horse at the gallop, and otherwise. */
private const val URGENT_TILES = 1.1f
private const val HORSE_URGENT_TILES = 0.7f
private const val SLOW_TILES = 0.45f

/** Seconds at a stop, by kind, and how long a fire engine waits each time it looks to see if the fire's out. */
private val DWELL = floatArrayOf(2f, 3f, 4f, 0.8f)
private const val HOLD = 0.5f

/** Each kind's length, in tiles. */
private val LENGTH = floatArrayOf(0.5f, 0.38f, 0.32f, 0.45f)

/** When ambulances and garbage trucks got motors; police cars go white with a stripe from the 1960s. */
private const val MOTOR_AMBULANCE_YEAR = 1912
private const val MOTOR_TRUCK_YEAR = 1920
private const val WHITE_POLICE_YEAR = 1965

private val BODY = listOf(Color(0xFFC0281E), Color(0xFFEDEBE4), Color(0xFF1E2840), Color(0xFF4F6B3A))
private val POLICE_LATE = Color(0xFFE8E8EC)
private val GLASS = Color(0xFF2B3440)
private val HORSE = Color(0xFF4E3322)
private val BRASS = Color(0xFFC9A44A)
private val LADDER = Color(0xFFBFC3C7)
private val CROSS = Color(0xFFC0281E)
private val BIN = Color(0xFF3B4F2C)
private val RED = Color(0xFFFF3B30)
private val BLUE = Color(0xFF3B6BFF)
private val DIM = Color(0xFF3A2A2A)
