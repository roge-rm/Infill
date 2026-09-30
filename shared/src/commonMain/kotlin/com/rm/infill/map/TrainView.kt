package com.rm.infill.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.rm.infill.sim.Balance
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.TrainRoute
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
internal fun DrawScope.drawTrains(routes: List<TrainRoute>, map: CityMap, camera: Camera, time: Float, most: Int, smoke: Boolean): Set<Int> {
    val t = camera.tilePx
    if (most == 0 || routes.isEmpty() || t < MIN_TILE_PX) return emptySet()
    val crossings = HashSet<Int>()
    val topLeft = camera.screenToTile(Offset.Zero, size)
    val bottomRight = camera.screenToTile(Offset(size.width, size.height), size)
    for ((k, route) in routes.withIndex()) {
        val path = route.tiles
        if (path.size < 2) continue
        val length = (path.size - 1).toFloat()
        val trains = min(most, 1 + route.load / (Balance.TRAIN_LOAD * 30))
        val cars = if (route.passengers) PASSENGER_CARS else FREIGHT_CARS
        val leg = length / SPEED
        val period = 2 * (leg + STOP)
        for (n in 0 until trains) {
            val phase = ((time + (k * 0.37f + n.toFloat() / trains) * period) % period + period) % period
            // Out, a stop, back, a stop.
            val (head, forward) = when {
                phase < leg -> length * ease(phase / leg) to true
                phase < leg + STOP -> length to true
                phase < 2 * leg + STOP -> length * (1 - ease((phase - leg - STOP) / leg)) to false
                else -> 0f to false
            }
            for (c in 0 until cars + 2) {
                // Engine, tender, then the cars, each behind the last.
                val s = if (forward) head - c * CAR_GAP else head + c * CAR_GAP
                if (s < 0f || s > length) continue
                val (x, y, heading) = pointOn(path, s, map.width)
                if (x < topLeft.x - 1 || y < topLeft.y - 1 || x > bottomRight.x + 1 || y > bottomRight.y + 1) continue
                val tile = path[min(path.size - 1, (s + 0.5f).toInt())]
                if (map.road[tile].toInt() != 0) crossings += tile
                val kind = when (c) {
                    0 -> Car.Engine
                    1 -> Car.Tender
                    else -> if (route.passengers) Car.Coach else FREIGHT[(k * 3 + c) % FREIGHT.size]
                }
                car(camera.tileToScreen(x, y, size), t, heading, kind, k + c)
                if (c == 0 && smoke) puffs(camera.tileToScreen(x, y, size), t, time, k * 7 + n)
            }
        }
    }
    return crossings
}

private enum class Car { Engine, Tender, Coach, Boxcar, Hopper, Flatcar }

private val FREIGHT = listOf(Car.Boxcar, Car.Hopper, Car.Flatcar, Car.Boxcar)

/**
 * Where [s] tiles along [path] is, as a point on the map in tiles, and which
 * way the track runs there, in degrees clockwise from east. Each tile is
 * crossed from the middle of the edge it's entered by to the middle of the
 * one it's left by: straight through, or round a quarter circle where the
 * track bends, as it's drawn.
 */
private fun pointOn(path: IntArray, s: Float, width: Int): Triple<Float, Float, Float> {
    val n = path.size
    val i = (s + 0.5f).toInt().coerceIn(0, n - 1)
    val u = (s - (i - 0.5f)).coerceIn(0f, 1f)
    val cx = path[i] % width + 0.5f
    val cy = path[i] / width + 0.5f
    fun way(a: Int, b: Int) = (b % width - a % width) to (b / width - a / width)
    val (ix, iy) = if (i > 0) way(path[i - 1], path[i]) else way(path[i], path[i + 1])
    val (ox, oy) = if (i < n - 1) way(path[i], path[i + 1]) else ix to iy
    if (ix == ox && iy == oy) {
        return Triple(cx + ix * (u - 0.5f), cy + iy * (u - 0.5f), degrees(atan2(iy.toFloat(), ix.toFloat())))
    }
    // Round the corner between the edge it came in by and the one it leaves by.
    val cornerX = cx - ix * 0.5f + ox * 0.5f
    val cornerY = cy - iy * 0.5f + oy * 0.5f
    val from = atan2(cy - iy * 0.5f - cornerY, cx - ix * 0.5f - cornerX)
    var sweep = atan2(cy + oy * 0.5f - cornerY, cx + ox * 0.5f - cornerX) - from
    if (sweep > PI) sweep -= (2 * PI).toFloat()
    if (sweep < -PI) sweep += (2 * PI).toFloat()
    val a = from + sweep * u
    // Moving round the circle one way or the other, the track runs square to the radius.
    val heading = a + if (sweep > 0) (PI / 2).toFloat() else -(PI / 2).toFloat()
    return Triple(cornerX + 0.5f * cos(a), cornerY + 0.5f * sin(a), degrees(heading))
}

private fun degrees(radians: Float) = radians * 180f / PI.toFloat()

/** Slow away and slow in, quicker in between. */
private fun ease(u: Float): Float = u * u * (3 - 2 * u)

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
        }
    }
}

/** A few puffs of smoke rising from the engine. */
private fun DrawScope.puffs(at: Offset, t: Float, time: Float, seed: Int) {
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
