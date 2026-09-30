package com.rm.infill.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.rm.infill.sim.Balance
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.TrainRoute
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

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
                val (x, y, along) = pointOn(path, s, map.width)
                if (x < topLeft.x - 1 || y < topLeft.y - 1 || x > bottomRight.x + 1 || y > bottomRight.y + 1) continue
                val tile = path[min(path.size - 1, (s + 0.5f).toInt())]
                if (map.road[tile].toInt() != 0) crossings += tile
                val kind = when (c) {
                    0 -> Car.Engine
                    1 -> Car.Tender
                    else -> if (route.passengers) Car.Coach else FREIGHT[(k * 3 + c) % FREIGHT.size]
                }
                car(camera.tileToScreen(x + 0.5f, y + 0.5f, size), t, along, kind, k + c)
                if (c == 0 && smoke) puffs(camera.tileToScreen(x + 0.5f, y + 0.5f, size), t, time, k * 7 + n)
            }
        }
    }
    return crossings
}

private enum class Car { Engine, Tender, Coach, Boxcar, Hopper, Flatcar }

private val FREIGHT = listOf(Car.Boxcar, Car.Hopper, Car.Flatcar, Car.Boxcar)

/** A point [s] tiles along [path], as tile x and y, and whether that stretch runs east to west. */
private fun pointOn(path: IntArray, s: Float, width: Int): Triple<Float, Float, Boolean> {
    val i = min(path.size - 2, max(0, floor(s).toInt()))
    val f = s - i
    val a = path[i]
    val b = path[i + 1]
    val ax = (a % width).toFloat()
    val ay = (a / width).toFloat()
    val bx = (b % width).toFloat()
    val by = (b / width).toFloat()
    return Triple(ax + (bx - ax) * f, ay + (by - ay) * f, ay == by)
}

/** Slow away and slow in, quicker in between. */
private fun ease(u: Float): Float = u * u * (3 - 2 * u)

private fun DrawScope.car(at: Offset, t: Float, alongX: Boolean, kind: Car, seed: Int) {
    val long = t * CAR_LENGTH
    val wide = t * 0.36f
    val size = if (alongX) Size(long, wide) else Size(wide, long)
    val topLeft = Offset(at.x - size.width / 2, at.y - size.height / 2)
    when (kind) {
        Car.Engine -> {
            drawRect(ENGINE, topLeft, size)
            // The boiler down the middle, and the chimney.
            val boiler = if (alongX) Size(long * 0.7f, wide * 0.5f) else Size(wide * 0.5f, long * 0.7f)
            drawRect(BOILER, Offset(at.x - boiler.width / 2, at.y - boiler.height / 2), boiler)
            drawCircle(Color.Black, t * 0.07f, at)
        }
        Car.Tender -> {
            drawRect(ENGINE, topLeft, size)
            val coal = if (alongX) Size(long * 0.7f, wide * 0.6f) else Size(wide * 0.6f, long * 0.7f)
            drawRect(COAL, Offset(at.x - coal.width / 2, at.y - coal.height / 2), coal)
        }
        Car.Coach -> {
            drawRect(COACHES[seed.mod(COACHES.size)], topLeft, size)
            val roof = if (alongX) Size(long * 0.9f, wide * 0.4f) else Size(wide * 0.4f, long * 0.9f)
            drawRect(COACH_ROOF, Offset(at.x - roof.width / 2, at.y - roof.height / 2), roof)
        }
        Car.Boxcar -> {
            drawRect(BOXCAR, topLeft, size)
            val seam = if (alongX) Size(t * 0.03f, wide) else Size(wide, t * 0.03f)
            drawRect(BOXCAR_SEAM, Offset(at.x - seam.width / 2, at.y - seam.height / 2), seam)
        }
        Car.Hopper -> {
            drawRect(HOPPER, topLeft, size)
            val load = if (alongX) Size(long * 0.8f, wide * 0.6f) else Size(wide * 0.6f, long * 0.8f)
            drawRect(COAL, Offset(at.x - load.width / 2, at.y - load.height / 2), load)
        }
        Car.Flatcar -> {
            drawRect(FLATCAR, topLeft, size)
            val load = if (alongX) Size(long * 0.6f, wide * 0.7f) else Size(wide * 0.7f, long * 0.6f)
            drawRect(LUMBER, Offset(at.x - load.width / 2, at.y - load.height / 2), load)
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
