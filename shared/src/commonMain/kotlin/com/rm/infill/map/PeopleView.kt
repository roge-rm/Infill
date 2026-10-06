package com.rm.infill.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.rm.infill.sim.City
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.RoadType
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * People on the pavements and cyclists at the kerb, more of them where more
 * went by last month. Each pavement has people going both ways, spaced down
 * the whole row or column of road and moving along it together, so someone
 * carries on from tile to tile; how busy a tile is decides who shows on it.
 * Nobody walks or cycles along a highway.
 */
internal fun DrawScope.drawPeople(city: City, map: CityMap, camera: Camera, year: Int, time: Float, behind: (Float, Float) -> Boolean = { _, _ -> false }) {
    val t = camera.tilePx
    if (t < MIN_PEOPLE_PX) return
    val topLeft = camera.screenToTile(Offset.Zero, size)
    val bottomRight = camera.screenToTile(Offset(size.width, size.height), size)
    val x0 = floor(topLeft.x).toInt().coerceAtLeast(0)
    val y0 = floor(topLeft.y).toInt().coerceAtLeast(0)
    val x1 = ceil(bottomRight.x).toInt().coerceAtMost(map.width - 1)
    val y1 = ceil(bottomRight.y).toInt().coerceAtMost(map.height - 1)
    val clothes = if (year < BRIGHT_YEAR) DRAB else BRIGHT
    fun road(x: Int, y: Int) = map.inside(x, y) && map.road[map.index(x, y)].toInt() != 0
    for (y in y0..y1) for (x in x0..x1) {
        val i = map.index(x, y)
        val type = RoadType.of(map.road[i]) ?: continue
        if (type.limited || type.ramp) continue
        val walkers = city.walkers(i)
        val cyclists = city.cyclists(i)
        if (walkers == 0 && cyclists == 0) continue
        val across = road(x - 1, y) || road(x + 1, y)
        val down = road(x, y - 1) || road(x, y + 1)
        // Along a row, a column, or both through a crossing.
        for (row in listOf(true, false)) {
            if (row && !across && down || !row && !down) continue
            val line0 = (if (row) y else LINES + x) * 8
            val start = (if (row) x else y).toFloat()
            fun each(line: Int, most: Int, want: Float, speed: Float, draw: (Float, Int) -> Unit) {
                if (want <= 0f) return
                val gap = 1f / most
                val shift = (time * speed.toDouble() + unit(line * 7) * gap).mod(gap * WRAP.toDouble()).toFloat()
                var j = ceil((start - shift) / gap).toInt()
                while (true) {
                    val pos = j * gap + shift
                    if (pos >= start + 1f) break
                    val seed = line * 104729 + j.mod(WRAP) * 977
                    j++
                    if (unit(seed) * most > want) continue
                    draw(pos, seed)
                }
            }
            // On foot: both pavements, both ways along each.
            val walking = min(MOST_WALKERS.toFloat(), walkers / PER_WALKER)
            for (side in 0..1) for (way in 0..1) {
                val line = line0 + side * 2 + way
                val off = PAVEMENT[side] + if (way == 0) -STEP else STEP
                each(line, MOST_WALKERS, walking, if (way == 0) WALK_SPEED else -WALK_SPEED) { pos, seed ->
                    if (behind(if (row) pos else x + off, if (row) y + off else pos)) return@each
                    val at = camera.tileToScreen(if (row) pos else x + off, if (row) y + off else pos, size)
                    walker(at, t, clothes[(seed ushr 4).mod(clothes.size)], HAIR[(seed ushr 9).mod(HAIR.size)])
                }
            }
            // Cycling: at the kerb on the right, each way.
            val riding = min(MOST_CYCLISTS.toFloat(), cyclists / PER_CYCLIST)
            for (way in 0..1) {
                val line = line0 + 4 + way
                // East or south on the far side of the middle, west or north on the near.
                val forward = way == 0
                val off = if (row) (if (forward) KERB_FAR else KERB_NEAR) else (if (forward) KERB_NEAR else KERB_FAR)
                val heading = if (row) (if (forward) 0f else 180f) else (if (forward) 90f else 270f)
                each(line, MOST_CYCLISTS, riding, if (forward) BIKE_SPEED else -BIKE_SPEED) { pos, seed ->
                    if (behind(if (row) pos else x + off, if (row) y + off else pos)) return@each
                    val at = camera.tileToScreen(if (row) pos else x + off, if (row) y + off else pos, size)
                    cyclist(at, t, heading, clothes[(seed ushr 4).mod(clothes.size)])
                }
            }
        }
    }
}

/** Someone on foot from above: shoulders in their clothes, and the top of their head. */
private fun DrawScope.walker(at: Offset, t: Float, clothes: Color, hair: Color) {
    drawCircle(clothes, max(1.2f, t * 0.045f), at)
    drawCircle(hair, max(0.8f, t * 0.026f), at)
}

/** A cyclist from above, [heading] degrees clockwise from east: the wheels ahead and behind, and the rider between. */
private fun DrawScope.cyclist(at: Offset, t: Float, heading: Float, clothes: Color) {
    rotate(heading, at) {
        val long = t * 0.26f
        val thin = max(1f, t * 0.03f)
        drawRect(FRAME, Offset(at.x - long / 2, at.y - thin / 2), Size(long, thin))
        // Handlebars across the front.
        drawRect(FRAME, Offset(at.x + long * 0.22f, at.y - t * 0.05f), Size(thin, t * 0.1f))
        drawOval(clothes, Offset(at.x - t * 0.06f, at.y - t * 0.055f), Size(t * 0.1f, t * 0.11f))
        drawCircle(HAIR[0], max(0.8f, t * 0.027f), Offset(at.x + t * 0.01f, at.y))
    }
}

/**
 * The ferries, one on each crossing, going back and forth between the two
 * terminals with a wait at each end.
 */
internal fun DrawScope.drawFerries(routes: List<IntArray>, map: CityMap, camera: Camera, time: Float) {
    val t = camera.tilePx
    if (t < MIN_FERRY_PX) return
    val topLeft = camera.screenToTile(Offset.Zero, size)
    val bottomRight = camera.screenToTile(Offset(size.width, size.height), size)
    for ((k, path) in routes.withIndex()) {
        val n = path.size
        if (n < 2) continue
        val crossing = (n - 1) / FERRY_TILES
        val period = 2 * (crossing + FERRY_DOCK)
        // Where it is in its round: tied up, across, tied up at the other side, back.
        val at = (time + k * 7.3f).mod(period)
        val out = at < crossing + FERRY_DOCK
        val inRun = if (out) at - FERRY_DOCK else at - crossing - 2 * FERRY_DOCK
        val s = (inRun / crossing).coerceIn(0f, 1f) * (n - 1)
        val pos = if (out) s else n - 1 - s
        val i = floor(pos + 0.5f).toInt().coerceIn(0, n - 1)
        val u = (pos + 0.5f - i).coerceIn(0f, 1f)
        // Which way along the path it's going.
        val dir = if (out) 1 else -1
        val prev = (i - dir).let { if (it in 0 until n) path[it] else -1 }
        val next = (i + dir).let { if (it in 0 until n) path[it] else -1 }
        val (cx, cy, heading) = pointThrough(prev, path[i], next, if (out) u else 1f - u, map.width)
        if (cx < topLeft.x - 1 || cx > bottomRight.x + 1 || cy < topLeft.y - 1 || cy > bottomRight.y + 1) continue
        ferry(camera.tileToScreen(cx, cy, size), t, heading, inRun in 0f..crossing)
    }
}

/** A ferry centred on [at], bow [heading] degrees clockwise from east: a white hull with a cabin, and a wake when [moving]. */
private fun DrawScope.ferry(at: Offset, t: Float, heading: Float, moving: Boolean) {
    val long = t * 0.85f
    val wide = t * 0.34f
    rotate(heading, at) {
        val stern = at.x - long / 2
        val bow = at.x + long / 2
        if (moving) {
            val trail = Path().apply {
                moveTo(stern, at.y - wide * 0.3f)
                lineTo(stern - long * 0.6f, at.y - wide * 0.6f)
                lineTo(stern - long * 0.6f, at.y + wide * 0.6f)
                lineTo(stern, at.y + wide * 0.3f)
                close()
            }
            drawPath(trail, WAKE, alpha = 0.2f)
        }
        val hull = Path().apply {
            moveTo(stern, at.y - wide / 2)
            lineTo(bow - wide * 0.5f, at.y - wide / 2)
            lineTo(bow, at.y)
            lineTo(bow - wide * 0.5f, at.y + wide / 2)
            lineTo(stern, at.y + wide / 2)
            close()
        }
        drawPath(hull, FERRY_HULL)
        drawRect(FERRY_STRIPE, Offset(stern, at.y - wide / 2), Size(long * 0.85f, wide * 0.12f))
        drawRect(FERRY_STRIPE, Offset(stern, at.y + wide * 0.38f), Size(long * 0.85f, wide * 0.12f))
        drawRect(FERRY_CABIN, Offset(stern + long * 0.25f, at.y - wide * 0.3f), Size(long * 0.45f, wide * 0.6f))
        drawCircle(FERRY_FUNNEL, wide * 0.14f, Offset(stern + long * 0.38f, at.y))
    }
}

private fun unit(n: Int): Float {
    var h = n * 374761393
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0xffff) / 65536f
}

/** Too small to see below these. */
private const val MIN_PEOPLE_PX = 20f
private const val MIN_FERRY_PX = 8f

/** People along a row repeat after this many places; column lines are numbered after the rows'. */
private const val WRAP = 4096
private const val LINES = 1 shl 16

/** At most so many on each way along a pavement of one tile, and one more for each so many who went by last month. */
private const val MOST_WALKERS = 2
private const val PER_WALKER = 400f
private const val MOST_CYCLISTS = 2
private const val PER_CYCLIST = 150f

/** Tiles a second, on foot and cycling. */
private const val WALK_SPEED = 0.12f
private const val BIKE_SPEED = 0.3f

/** Where the pavements are across a tile, and how far apart the two ways along one keep. */
private val PAVEMENT = floatArrayOf(0.15f, 0.85f)
private const val STEP = 0.02f

/** Where cyclists keep, inside each kerb. */
private const val KERB_NEAR = 0.25f
private const val KERB_FAR = 0.75f

/** Ferries: tiles a second across, and seconds tied up at each end. */
private const val FERRY_TILES = 0.6f
private const val FERRY_DOCK = 4f

/** Clothes go brighter from the 1950s. */
private const val BRIGHT_YEAR = 1950
private val DRAB = listOf(Color(0xFF3A3530), Color(0xFF4A4038), Color(0xFF2E3442), Color(0xFF5A4E3E), Color(0xFF6B6258), Color(0xFF7A3A30))
private val BRIGHT = listOf(Color(0xFFC0392B), Color(0xFF2E86C1), Color(0xFFF1C40F), Color(0xFF27AE60), Color(0xFFECF0F1), Color(0xFF34495E), Color(0xFFE67E22), Color(0xFF8E44AD))
private val HAIR = listOf(Color(0xFF2A1E16), Color(0xFF5A3A22), Color(0xFF8A6A3A), Color(0xFFB8B0A0), Color(0xFF1A1A1A))
private val FRAME = Color(0xFF22252A)
private val WAKE = Color(0xFFFFFFFF)
private val FERRY_HULL = Color(0xFFF2F0EA)
private val FERRY_STRIPE = Color(0xFF2F5F6F)
private val FERRY_CABIN = Color(0xFFD8D4C8)
private val FERRY_FUNNEL = Color(0xFFC0392B)
