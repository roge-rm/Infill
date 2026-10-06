package com.rm.infill.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.rm.infill.sim.Balance
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.TrainRoute
import com.rm.infill.sound.Recipes
import com.rm.infill.sound.TownSound
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Trains along the lines they ran last month: out along the track, a stop at
 * the far end, and back again. A busier line has more trains, up to [most].
 * Returns the level crossings a train is on, so the road traffic can wait.
 */
internal fun DrawScope.drawTrains(routes: List<TrainRoute>, map: CityMap, camera: Camera, time: Float, most: Int, smoke: Boolean, steam: Boolean = true, behind: (Float, Float) -> Boolean = { _, _ -> false }): Set<Int> {
    val t = camera.tilePx
    if (most == 0 || routes.isEmpty() || t < MIN_TILE_PX) return emptySet()
    val crossings = HashSet<Int>()
    val topLeft = camera.screenToTile(Offset.Zero, size)
    val bottomRight = camera.screenToTile(Offset(size.width, size.height), size)
    for ((k, route) in routes.withIndex()) {
        val path = route.tiles
        if (path.size < 2) continue
        val length = (path.size - 1).toFloat()
        val trains = trainsOn(route, most)
        val cars = if (route.passengers) PASSENGER_CARS else if (route.containers) CONTAINER_CARS else FREIGHT_CARS
        for (n in 0 until trains) {
            val (head, forward) = trainAt(route, k, n, trains, time).let { it.head to it.forward }
            for (c in 0 until cars + 2) {
                // Engine, tender, then the cars, each behind the last.
                val s = if (forward) head - c * CAR_GAP else head + c * CAR_GAP
                if (s < 0f || s > length) continue
                // Out of sight in a tunnel.
                if (route.hidden[min(path.size - 1, (s + 0.5f).toInt())]) continue
                val (x, y, heading) = pointOn(path, s, map.width)
                if (x < topLeft.x - 1 || y < topLeft.y - 1 || x > bottomRight.x + 1 || y > bottomRight.y + 1) continue
                val tile = path[min(path.size - 1, (s + 0.5f).toInt())]
                if (map.road[tile].toInt() != 0) crossings += tile
                val kind = when (c) {
                    0 -> if (steam) Car.Engine else Car.Diesel
                    1 -> if (steam) Car.Tender else Car.Diesel
                    else -> if (route.passengers) Car.Coach else if (route.containers) Car.Container else FREIGHT[(k * 3 + c) % FREIGHT.size]
                }
                if (!behind(x, y)) car(camera.tileToScreen(x, y, size), t, heading, kind, k + c)
                if (c == 0 && smoke && steam) puffs(camera.tileToScreen(x, y, size), t, time, k * 7 + n)
            }
        }
    }
    return crossings
}

/** How many trains run on [route]: more on a busier line, up to [most]. */
internal fun trainsOn(route: TrainRoute, most: Int) = min(most, 1 + route.load / (Balance.TRAIN_LOAD * 30))

/** Where a train's engine is along its route, which way it's going, and how fast, 0 to 1. */
internal class TrainAt(val head: Float, val forward: Boolean, val speed: Float)

/** Train [n] of [trains] on route [k] at [time]: out along the track, a stop at the far end, back, and a stop. */
internal fun trainAt(route: TrainRoute, k: Int, n: Int, trains: Int, time: Float): TrainAt {
    val length = (route.tiles.size - 1).toFloat()
    val leg = length / SPEED
    val period = 2 * (leg + STOP)
    val phase = ((time + (k * 0.37f + n.toFloat() / trains) * period) % period + period) % period
    // How fast the eased run is going, as a share of its fastest, halfway.
    fun pace(u: Float) = 4f * u * (1 - u)
    return when {
        phase < leg -> TrainAt(length * ease(phase / leg), true, pace(phase / leg))
        phase < leg + STOP -> TrainAt(length, true, 0f)
        phase < 2 * leg + STOP -> TrainAt(length * (1 - ease((phase - leg - STOP) / leg)), false, pace((phase - leg - STOP) / leg))
        else -> TrainAt(0f, false, 0f)
    }
}

/** Every train's engine as something the town's sound can place, out of sight in a tunnel or not. */
internal fun trainSounds(routes: List<TrainRoute>, width: Int, time: Float, most: Int, kind: Float): List<TownSound.Mover> {
    if (most == 0) return emptyList()
    val out = ArrayList<TownSound.Mover>()
    for ((k, route) in routes.withIndex()) {
        if (route.tiles.size < 2) continue
        val trains = trainsOn(route, most)
        for (n in 0 until trains) {
            val at = trainAt(route, k, n, trains, time)
            val (x, y, _) = pointOn(route.tiles, at.head, width)
            val hidden = route.hidden[min(route.tiles.size - 1, (at.head + 0.5f).toInt())]
            out += TownSound.Mover(
                TownSound.KEY_MOVERS + k * 8 + n, Recipes.TRAIN, x, y, kind, 0.15f + 0.85f * at.speed,
                loudness = if (hidden) 0.3f else 1f,
            )
        }
    }
    return out
}

private enum class Car { Engine, Tender, Diesel, Coach, Boxcar, Hopper, Flatcar, Container }

private val FREIGHT = listOf(Car.Boxcar, Car.Hopper, Car.Flatcar, Car.Boxcar)

/**
 * Where [s] tiles along [path] is, as a point on the map in tiles, and which
 * way the track runs there, in degrees clockwise from east. Each tile is
 * crossed from the middle of the edge it's entered by to the middle of the
 * one it's left by: straight through, or round a quarter circle where the
 * track bends, as it's drawn.
 */
internal fun pointOn(path: IntArray, s: Float, width: Int): Triple<Float, Float, Float> {
    val n = path.size
    val i = (s + 0.5f).toInt().coerceIn(0, n - 1)
    val u = (s - (i - 0.5f)).coerceIn(0f, 1f)
    return pointThrough(if (i > 0) path[i - 1] else -1, path[i], if (i < n - 1) path[i + 1] else -1, u, width)
}

/**
 * Where something is [u] of the way across tile [here], come from [prev] and
 * going on to [next] (-1 for neither, at an end), and its heading in degrees
 * clockwise from east: straight through, or round a quarter circle about the
 * corner between the edge it came in by and the one it leaves by. [side] moves
 * it that far to the right of the way it's going, for a lane, so round a bend
 * it keeps the same distance from the middle.
 */
internal fun pointThrough(prev: Int, here: Int, next: Int, u: Float, width: Int, side: Float = 0f): Triple<Float, Float, Float> {
    val cx = here % width + 0.5f
    val cy = here / width + 0.5f
    fun way(a: Int, b: Int) = (b % width - a % width).coerceIn(-1, 1) to (b / width - a / width).coerceIn(-1, 1)
    val (ix, iy) = if (prev >= 0) way(prev, here) else if (next >= 0) way(here, next) else 1 to 0
    val (ox, oy) = if (next >= 0) way(here, next) else ix to iy
    fun offset(x: Float, y: Float, heading: Float): Triple<Float, Float, Float> {
        if (side == 0f) return Triple(x, y, degrees(heading))
        return Triple(x - side * sin(heading), y + side * cos(heading), degrees(heading))
    }
    if (ix == ox && iy == oy) {
        return offset(cx + ix * (u - 0.5f), cy + iy * (u - 0.5f), atan2(iy.toFloat(), ix.toFloat()))
    }
    if (ix == -ox && iy == -oy) {
        // Turning back at the end of a line: in to the middle, then out the way it came.
        return if (u < 0.5f) offset(cx + ix * (u - 0.5f), cy + iy * (u - 0.5f), atan2(iy.toFloat(), ix.toFloat()))
        else offset(cx + ox * (u - 0.5f), cy + oy * (u - 0.5f), atan2(oy.toFloat(), ox.toFloat()))
    }
    val cornerX = cx - ix * 0.5f + ox * 0.5f
    val cornerY = cy - iy * 0.5f + oy * 0.5f
    val from = atan2(cy - iy * 0.5f - cornerY, cx - ix * 0.5f - cornerX)
    var sweep = atan2(cy + oy * 0.5f - cornerY, cx + ox * 0.5f - cornerX) - from
    if (sweep > PI) sweep -= (2 * PI).toFloat()
    if (sweep < -PI) sweep += (2 * PI).toFloat()
    val a = from + sweep * u
    // Moving round the circle one way or the other, the way runs square to the radius.
    val heading = a + if (sweep > 0) (PI / 2).toFloat() else -(PI / 2).toFloat()
    return offset(cornerX + 0.5f * cos(a), cornerY + 0.5f * sin(a), heading)
}

private fun degrees(radians: Float) = radians * 180f / PI.toFloat()

/** Slow away and slow in, quicker in between. */
internal fun ease(u: Float): Float = u * u * (3 - 2 * u)

private fun DrawScope.car(at: Offset, t: Float, heading: Float, kind: Car, seed: Int) {
    val long = t * CAR_LENGTH
    val wide = t * 0.36f
    // Drawn lying east to west, then turned the way the track runs.
    rotate(heading, at) {
        fun part(lengthShare: Float, widthShare: Float, colour: Color) {
            val size = Size(long * lengthShare, wide * widthShare)
            drawRect(colour, Offset(at.x - size.width / 2, at.y - size.height / 2), size)
        }
        when (kind) {
            Car.Engine -> {
                part(1f, 1f, ENGINE)
                // The boiler down the middle, and the chimney.
                part(0.7f, 0.5f, BOILER)
                drawCircle(Color.Black, t * 0.07f, at)
            }
            Car.Diesel -> {
                // A hood unit: the long body, the cab at the front end, the radiator grilles on the roof.
                part(1f, 1f, DIESEL)
                part(0.9f, 0.55f, DIESEL_ROOF)
                drawRect(DIESEL_CAB, Offset(at.x + long * 0.25f, at.y - wide * 0.4f), Size(long * 0.2f, wide * 0.8f))
            }
            Car.Tender -> {
                part(1f, 1f, ENGINE)
                part(0.7f, 0.6f, COAL)
            }
            Car.Coach -> {
                part(1f, 1f, COACHES[seed.mod(COACHES.size)])
                part(0.9f, 0.4f, COACH_ROOF)
            }
            Car.Boxcar -> {
                part(1f, 1f, BOXCAR)
                part(0.04f, 1f, BOXCAR_SEAM)
            }
            Car.Hopper -> {
                part(1f, 1f, HOPPER)
                part(0.8f, 0.6f, COAL)
            }
            Car.Flatcar -> {
                part(1f, 1f, FLATCAR)
                part(0.6f, 0.7f, LUMBER)
            }
            Car.Container -> {
                part(1f, 0.8f, FLATCAR)
                part(0.9f, 0.9f, BOXES[seed.mod(BOXES.size)])
                part(0.02f, 0.9f, FLATCAR)
            }
        }
    }
}

/** A few puffs of smoke rising from the engine. */
internal fun DrawScope.puffs(at: Offset, t: Float, time: Float, seed: Int) {
    for (k in 0 until 4) {
        val age = (time * 0.8f + k / 4f + seed * 0.13f) % 1f
        drawCircle(SMOKE, t * (0.08f + age * 0.18f), Offset(at.x + age * t * 0.3f, at.y - t * 0.2f - age * t * 0.9f), alpha = 0.55f * (1 - age))
    }
}

/** Tiles a second on the way, and seconds stopped at each end. */
private const val SPEED = 2.2f
private const val STOP = 5f

/** How long a car is, and how far apart their middles are, in tiles. */
private const val CAR_LENGTH = 0.78f
private const val CAR_GAP = 0.85f
private const val PASSENGER_CARS = 3
private const val FREIGHT_CARS = 5
private const val CONTAINER_CARS = 8
private val DIESEL = Color(0xFF8A2A22)
private val DIESEL_ROOF = Color(0xFF5A5A60)
private val DIESEL_CAB = Color(0xFFD8C8A0)
private val BOXES = listOf(Color(0xFFB5452F), Color(0xFF2F5F9A), Color(0xFF3F8A4A), Color(0xFFD08A2A), Color(0xFF8A8F96), Color(0xFF7A3F7A))

private const val MIN_TILE_PX = 12f

private val ENGINE = Color(0xFF1F2024)
private val BOILER = Color(0xFF3A3C42)
private val COAL = Color(0xFF15151A)
private val COACHES = listOf(Color(0xFF6B2330), Color(0xFF2E4A3A))
private val COACH_ROOF = Color(0xFF4A4A50)
private val BOXCAR = Color(0xFF7A4A2E)
private val BOXCAR_SEAM = Color(0xFF4E2E1C)
private val HOPPER = Color(0xFF2A2A2E)
private val FLATCAR = Color(0xFF5A4632)
private val LUMBER = Color(0xFFC29A62)
private val SMOKE = Color(0xFFD8D8DA)
