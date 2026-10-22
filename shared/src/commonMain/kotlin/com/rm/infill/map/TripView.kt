package com.rm.infill.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.RoadType
import com.rm.infill.sim.TripSample
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The cars, trucks, walkers and cyclists out on the map, each following one
 * of last month's real trips from its start to its end. Routes are picked
 * from the sim's [TripSample] by how many took them, so busy roads get more.
 * Kept for the life of the map view and moved on by the game-speed clock, so
 * they stop when the game's paused. All of them move; only those on screen
 * are drawn.
 */
internal class TripMovers {
    /** One trip's way across the map, worked out when it sets off. Its arrays are used again for the next trip if they're long enough. */
    private class Mover {
        var kind = 0
        var seed = 0
        /** The route's tiles, start to end, the first [n] of [route]. */
        var n = 0
        var route = IntArray(0)
        /** Where it comes onto each of the route's tiles, in tiles from the start. */
        var enter = FloatArray(0)
        /** The first [count] points along its way, in tiles, and how far each is from the start. */
        var count = 0
        var xs = FloatArray(0)
        var ys = FloatArray(0)
        var along = FloatArray(0)
        /** For each stretch between two points: its heading in degrees clockwise from east, which of the route's tiles it's on, and whether that's in a tunnel. */
        var heading = FloatArray(0)
        var step = IntArray(0)
        var under = BooleanArray(0)
        /** How far it's gone, in tiles, and the stretch that's on. */
        var dist = 0f
        var seg = 0
        val segs get() = count - 1
        val length get() = along[count - 1]
        val vehicle get() = kind == TripSample.CAR || kind == TripSample.TRUCK
    }

    private val movers = ArrayList<Mover>()
    /** Movers that have reached their ends, kept to set off again. */
    private val spare = ArrayList<Mover>()
    private var last = Float.NaN
    /** Movers owed, building up while there's room, so they set off a few at a time. */
    private var due = 0f
    private var state = 0x2545F4914F6CDD1DL
    /** The tiles traffic waits short of this frame, kept between frames. */
    private var blocked = BooleanArray(0)
    /** The sample last used to fill an empty map, so it's tried once, and whether that's still going. */
    private var filled: TripSample? = null
    private var filling = false
    /** The setting last asked for, so fewer wanted takes the extras off at once. */
    private var lastMost = -1

    /** Room for working out a mover's way, kept between builds. */
    private var px = FloatArray(0)
    private var py = FloatArray(0)
    private var pStep = IntArray(0)
    private var sideAt = FloatArray(0)
    private var count = 0
    private val spot = FloatArray(3)
    private var bSample: TripSample? = null
    private var bMap: CityMap? = null
    private var bRoute = IntArray(0)
    private var bFirst = 0
    private var bLast = 0
    private var bW = 0
    private var bEdge = 0f

    /**
     * Moves every mover on to [time] and starts new ones from [sample] while
     * there are fewer than [most] for a 128 tile square map. Vehicles wait
     * short of the [stopped] tiles, a level crossing with a train on it say.
     */
    fun update(sample: TripSample, map: CityMap, time: Float, most: Int, stopped: Set<Int>) {
        val dt = if (last.isNaN()) 0f else (time - last).coerceIn(0f, MAX_STEP)
        last = time
        val want = if (sample.size == 0) 0 else min((most.toLong() * map.size / AREA).toInt(), (sample.trips / TRIPS_EACH).toInt())
        val lowered = most < lastMost
        lastMost = most
        if (want == 0) {
            movers.clear()
            spare.clear()
            filled = null
            filling = false
            due = 0f
            return
        }
        // Graphics turned down: the extras go now.
        if (lowered) while (movers.size > want) retire(movers.size - 1)
        if (blocked.size != map.size) blocked = BooleanArray(map.size)
        if (stopped.isNotEmpty()) for (i in stopped) blocked[i] = true
        var k = 0
        while (k < movers.size) {
            if (move(movers[k], map, dt)) k++ else retire(k)
        }
        if (stopped.isNotEmpty()) for (i in stopped) blocked[i] = false
        if (movers.isEmpty() && filled !== sample) {
            filled = sample
            filling = true
        }
        if (filling) {
            // Starting out, say just after loading: each somewhere along its way, a batch a frame.
            var budget = FILL_EACH_FRAME
            var failed = 0
            while (budget-- > 0 && movers.size < want && failed < FILL_FAILS) if (!spawn(sample, map, partway = true)) failed++
            // Nothing can be built from this sample: stop until the next one.
            if (movers.size >= want || failed >= FILL_FAILS) filling = false
            due = 0f
            return
        }
        if (movers.size >= want) {
            due = 0f
            return
        }
        due += dt * want / SPREAD
        while (due >= 1f && movers.size < want) {
            spawn(sample, map, partway = false)
            due -= 1f
        }
    }

    /** Takes mover [k] off the map, keeping it to set off again. */
    private fun retire(k: Int) {
        val m = movers[k]
        movers[k] = movers[movers.size - 1]
        movers.removeAt(movers.size - 1)
        if (spare.size < SPARE) spare.add(m)
    }

    /** Moves [m] on by [dt] seconds; false once it's at its end. */
    private fun move(m: Mover, map: CityMap, dt: Float): Boolean {
        val j = m.step[m.seg]
        val tile = m.route[j]
        val speed = when (m.kind) {
            TripSample.WALKER -> WALK_TILES
            TripSample.CYCLIST -> BIKE_TILES
            else -> ROAD_TILES * (1f - min(0.75f, (map.congestion[tile].toInt() and 0xff) / 340f))
        }
        var d = m.dist + dt * speed
        // Waiting short of a crossing, a little further back for some so they queue.
        if (m.vehicle && j + 1 < m.n && blocked[m.route[j + 1]]) {
            d = min(d, max(m.dist, m.enter[j + 1] - QUEUE_GAP - (m.seed ushr 3 and 3) * QUEUE_STEP))
        }
        m.dist = d
        if (d >= m.length) return false
        while (m.seg < m.segs - 1 && m.along[m.seg + 1] <= d) m.seg++
        // Gone at a road that's been taken up since last month.
        return !m.vehicle || m.under[m.seg] || map.road[m.route[m.step[m.seg]]].toInt() != 0
    }

    /** Starts a trip picked from [sample] by weight, at its start or, if [partway], somewhere along it. */
    private fun spawn(sample: TripSample, map: CityMap, partway: Boolean): Boolean {
        repeat(TRIES) {
            val roll = (next() * sample.totalWeight).toLong().coerceIn(0L, sample.totalWeight - 1)
            val into = if (spare.isEmpty()) Mover() else spare.removeAt(spare.size - 1)
            if (!build(sample, sample.pick(roll), map, (state ushr 32).toInt(), into)) {
                spare.add(into)
                return@repeat
            }
            if (partway) {
                into.dist = next().toFloat() * into.length
                while (into.seg < into.segs - 1 && into.along[into.seg + 1] <= into.dist) into.seg++
            }
            movers.add(into)
            return true
        }
        return false
    }

    /**
     * Route [r]'s way across the map, into [m]: through each tile's middle,
     * kept to its lane or pavement, round each turn in a quarter circle, out
     * from the curb by a lot at its start and in to one at its end. False if
     * it can't be driven now, a road on it shut for works say.
     */
    private fun build(sample: TripSample, r: Int, map: CityMap, seed: Int, m: Mover): Boolean {
        val kind = sample.kind(r)
        val w = map.width
        // Only up to the first jump between tiles that don't touch, on a ferry say.
        var n = sample.length(r)
        for (j in 1 until n) {
            val a = sample.tile(r, j - 1)
            val b = sample.tile(r, j)
            if (abs(a % w - b % w) + abs(a / w - b / w) != 1) {
                n = j
                break
            }
        }
        if (n < 2) return false
        val vehicle = kind == TripSample.CAR || kind == TripSample.TRUCK
        if (vehicle) for (j in 0 until n) if (!sample.under(r, j) && map.closed(sample.tile(r, j))) return false
        if (m.route.size < n) {
            m.route = IntArray(n)
            m.enter = FloatArray(n)
        }
        val route = m.route
        for (j in 0 until n) route[j] = sample.tile(r, j)
        var first = 0
        var last = n - 1
        if (vehicle) {
            while (first < n && !(sample.under(r, first) || map.road[route[first]].toInt() != 0)) first++
            while (last > first && !(sample.under(r, last) || map.road[route[last]].toInt() != 0)) last--
            if (first >= n) return false
        }
        // Which side it keeps to: a walker either pavement, a vehicle either lane of a one-way road.
        val left = (seed ushr 7 and 1) == 0
        if (sideAt.size < n) sideAt = FloatArray(n)
        for (j in 0 until n) sideAt[j] = when (kind) {
            TripSample.WALKER -> if (left) -PAVEMENT else PAVEMENT
            TripSample.CYCLIST -> KERB
            else -> if (left && map.roadHeading[route[j]].toInt() != 0) -LANE else LANE
        }
        val cap = n * (2 * ARC + 3) + 2 * PULL + 4
        if (px.size < cap) {
            px = FloatArray(cap)
            py = FloatArray(cap)
            pStep = IntArray(cap)
        }
        count = 0
        bSample = sample
        bMap = map
        bRoute = route
        bFirst = first
        bLast = last
        bW = w
        bEdge = if (kind == TripSample.WALKER) LOT_EDGE else KERB_EDGE
        for (j in first..last) {
            val prev = if (j > 0) route[j - 1] else -1
            val next = if (j < n - 1) route[j + 1] else -1
            val out = if (j == 0 && first < last) lot(r, j, next, ahead = true) else -1
            val into = if (j == n - 1 && last > first) lot(r, j, prev, ahead = false) else -1
            if (out >= 0) {
                pull(j, -1, next, PULL_TO, out, inward = false)
                continue
            }
            if (into >= 0) {
                add(prev, j, -1, 0f)
                pull(j, prev, -1, 1f - PULL_TO, into, inward = true)
                continue
            }
            // From the middle of the route's first tile to the middle of its last, or from and to the curb by a lot.
            val from = if (j == 0) 0.5f else 0f
            val to = if (j == n - 1) 0.5f else 1f
            if (prev >= 0 && prev == next) {
                // Turning back: in to the middle, round a small loop to the other side, and out the way it came.
                add(prev, j, next, 0f)
                val s = sideAt[j]
                val cx = route[j] % w + 0.5f
                val cy = route[j] / w + 0.5f
                val h = atan2((route[j] / w - prev / w).toFloat(), (route[j] % w - prev % w).toFloat())
                val a0 = atan2(s * cos(h), -s * sin(h))
                val sweep = if (s > 0f) -PI.toFloat() else PI.toFloat()
                for (k in 0..2 * ARC) {
                    val a = a0 + sweep * k / (2 * ARC)
                    put(cx + abs(s) * cos(a), cy + abs(s) * sin(a), j)
                }
                if (j == last) add(prev, j, next, 1f)
            } else if (prev >= 0 && next >= 0 && next - route[j] != route[j] - prev) {
                for (k in 0 until ARC) add(prev, j, next, k / ARC.toFloat())
                if (j == last) add(prev, j, next, 1f)
            } else {
                add(prev, j, next, from)
                // Halfway too where its lane changes, so it moves across over the tiles either side.
                if (j > first && sideAt[j - 1] != sideAt[j] || j < last && sideAt[j + 1] != sideAt[j]) add(prev, j, next, 0.5f)
                if (j == last) add(prev, j, next, to)
            }
        }
        bSample = null
        bMap = null
        if (count < 2) return false
        if (m.xs.size < count) {
            m.xs = FloatArray(count)
            m.ys = FloatArray(count)
            m.along = FloatArray(count)
            m.heading = FloatArray(count - 1)
            m.step = IntArray(count - 1)
            m.under = BooleanArray(count - 1)
        }
        val along = m.along
        along[0] = 0f
        for (s in 0 until count) {
            m.xs[s] = px[s]
            m.ys[s] = py[s]
        }
        for (s in 0 until count - 1) {
            val dx = px[s + 1] - px[s]
            val dy = py[s + 1] - py[s]
            along[s + 1] = along[s] + sqrt(dx * dx + dy * dy)
            m.heading[s] = atan2(dy, dx) * 180f / PI.toFloat()
            m.step[s] = pStep[s]
            m.under[s] = sample.under(r, pStep[s])
        }
        // Each tile starts at its first point; one with none starts where the tile before did.
        val enter = m.enter
        enter.fill(-1f, 0, n)
        enter[0] = 0f
        for (s in 1 until count) if (pStep[s] != pStep[s - 1]) enter[pStep[s]] = along[s]
        for (j in 1 until n) if (enter[j] < enter[j - 1]) enter[j] = enter[j - 1]
        m.kind = kind
        m.seed = seed
        m.n = n
        m.count = count
        m.dist = 0f
        m.seg = 0
        return true
    }

    /** Across a tile it moves from halfway between its side and the last tile's to halfway to the next's, so a change of lane is smooth. */
    private fun side(j: Int, u: Float): Float {
        val here = sideAt[j]
        return if (u < 0.5f) {
            val edge = if (j > bFirst) (sideAt[j - 1] + here) / 2 else here
            edge + (here - edge) * u * 2
        } else {
            val edge = if (j < bLast) (here + sideAt[j + 1]) / 2 else here
            here + (edge - here) * (u - 0.5f) * 2
        }
    }

    /** Adds a point unless it's where the last one is. */
    private fun put(x: Float, y: Float, j: Int) {
        if (count > 0 && abs(px[count - 1] - x) < 1e-4f && abs(py[count - 1] - y) < 1e-4f) return
        px[count] = x
        py[count] = y
        pStep[count] = j
        count++
    }

    private fun add(prev: Int, j: Int, next: Int, u: Float) {
        pointThroughInto(prev, bRoute[j], next, u, bW, side(j, u), spot)
        put(spot[0], spot[1], j)
    }

    /**
     * The side of tile [j] with a building on it, away from [other], as a
     * step across packed by [pack]: its own side first so it doesn't cross
     * the road, then north, east, south and west; -1 if none.
     */
    private fun lot(r: Int, j: Int, other: Int, ahead: Boolean): Int {
        if (bSample!!.under(r, j)) return -1
        val w = bW
        val x = bRoute[j] % w
        val y = bRoute[j] / w
        val dx = if (ahead) other % w - x else x - other % w
        val dy = if (ahead) other / w - y else y - other / w
        val keep = if (sideAt[j] < 0f) -1 else 1
        if (building(x, y, -dy * keep, dx * keep, other)) return pack(-dy * keep, dx * keep)
        for (h in 0 until 4) if (building(x, y, LOT_DX[h], LOT_DY[h], other)) return pack(LOT_DX[h], LOT_DY[h])
        return -1
    }

    private fun building(x: Int, y: Int, sx: Int, sy: Int, other: Int): Boolean {
        val map = bMap!!
        val nx = x + sx
        val ny = y + sy
        if (!map.inside(nx, ny)) return false
        val i = map.index(nx, ny)
        return i != other && map.road[i].toInt() == 0 && map.building[i] != 0
    }

    private fun pack(sx: Int, sy: Int) = (sx + 1) * 3 + sy + 1

    /** From the curb on the [toward] ([pack]ed) side of tile [j] to its lane at [u] along it, or back if [inward], the swing across eased. */
    private fun pull(j: Int, prev: Int, next: Int, u: Float, toward: Int, inward: Boolean) {
        val w = bW
        val here = bRoute[j]
        val cx = here % w + 0.5f
        val cy = here / w + 0.5f
        val other = if (next >= 0) next else prev
        val dx = (if (next >= 0) other % w - here % w else here % w - other % w).toFloat()
        val dy = (if (next >= 0) other / w - here / w else here / w - other / w).toFloat()
        pointThroughInto(prev, here, next, u, w, side(j, u), spot)
        val lx = spot[0]
        val ly = spot[1]
        val edge = if (bEdge == KERB_EDGE && bMap!!.road[here] == DIRT_ID) DIRT_EDGE else bEdge
        val kx = (toward / 3 - 1) * edge
        val ky = (toward % 3 - 1) * edge
        // Along the way and across it, to the right.
        val fk = kx * dx + ky * dy
        val sk = -kx * dy + ky * dx
        val fl = (lx - cx) * dx + (ly - cy) * dy
        val sl = -(lx - cx) * dy + (ly - cy) * dx
        for (k in 0..PULL) {
            val f = (if (inward) PULL - k else k) / PULL.toFloat()
            val fa = fk + (fl - fk) * f
            val sa = sk + (sl - sk) * ease(f)
            put(cx + fa * dx - sa * dy, cy + fa * dy + sa * dx, j)
        }
    }

    /** Each mover on screen where it is now, as it looked in [year]. */
    fun DrawScope.draw(map: CityMap, camera: Camera, year: Int, behind: (Float, Float) -> Boolean = { _, _ -> false }) {
        val t = camera.tilePx
        if (t < MIN_VEHICLE_PX) return
        val topLeft = camera.screenToTile(Offset.Zero, size)
        val bottomRight = camera.screenToTile(Offset(size.width, size.height), size)
        for (k in 0 until movers.size) {
            val m = movers[k]
            val s = m.seg
            if (m.count < 2 || m.under[s]) continue
            if (!m.vehicle && t < MIN_PEOPLE_PX) continue
            val a = m.along[s]
            val span = m.along[s + 1] - a
            val u = if (span > 0f) ((m.dist - a) / span).coerceIn(0f, 1f) else 0f
            val cx = m.xs[s] + (m.xs[s + 1] - m.xs[s]) * u
            val cy = m.ys[s] + (m.ys[s + 1] - m.ys[s]) * u
            if (cx < topLeft.x - 1 || cx > bottomRight.x + 1 || cy < topLeft.y - 1 || cy > bottomRight.y + 1) continue
            if (behind(cx, cy)) continue
            val at = camera.tileToScreen(cx, cy, size)
            when (m.kind) {
                TripSample.WALKER -> person(at, t, year, m.seed)
                TripSample.CYCLIST -> rider(at, t, m.heading[s], year, m.seed)
                else -> roadVehicle(at, t, m.heading[s], year, m.seed, truck = m.kind == TripSample.TRUCK)
            }
        }
    }

    /** From 0 up to 1, from this view's own generator, which leaves the sim's alone. */
    private fun next(): Double {
        state = state xor (state shl 13)
        state = state xor (state ushr 7)
        state = state xor (state shl 17)
        return (state ushr 11).toDouble() / (1L shl 53).toDouble()
    }
}

/** The map area [Graphics.movers] is for, 128 tiles square. */
private const val AREA = 128L * 128L

/** At most one mover for so many of last month's trips, so a small town isn't crowded. */
private const val TRIPS_EACH = 5L

/** Seconds at normal speed for a town's worth of movers to set off, so they don't all go at once. */
private const val SPREAD = 4f

/** Routes tried before giving up on starting a mover this time. */
private const val TRIES = 4

/** The longest step a mover takes in one frame, in seconds, so a stall doesn't send it jumping on. */
private const val MAX_STEP = 0.25f

/** Tiles a second: a vehicle on a clear road, as [drawVehicles] has them, on foot and cycling. */
private const val ROAD_TILES = 0.5f
private const val WALK_TILES = 0.12f
private const val BIKE_TILES = 0.3f

/**
 * How far right of the middle each keeps, in tiles: a vehicle in its lane
 * and a cyclist at the curb as [drawVehicles] and the pavements have them,
 * and a walker on the pavement, either side.
 */
private const val LANE = 0.15f
private const val KERB = 0.25f
private const val PAVEMENT = 0.35f

/** Points round a quarter turn. */
private const val ARC = 6

/** Points pulling out from the curb or in to it, and how far along the first or last tile it's in its lane. */
private const val PULL = 6
private const val PULL_TO = 0.8f

/** How far from a tile's middle a vehicle stops at the curb, and a walker leaves or reaches the lot, in tiles. */
private const val KERB_EDGE = 0.32f
private const val LOT_EDGE = 0.5f

/** On dirt roads, which have a verge by the walkers, the curb point is closer in. */
private const val DIRT_EDGE = 0.24f
private val DIRT_ID = RoadType.DIRT.id

/** North, east, south and west, the order a tile's sides are looked at for a lot. */
private val LOT_DX = intArrayOf(0, 1, 0, -1)
private val LOT_DY = intArrayOf(-1, 0, 1, 0)

/** Movers set off part-way along their trips in one frame when the map fills. */
private const val FILL_EACH_FRAME = 300

/** Spawns in a row that find no route before filling the map gives up on this sample. */
private const val FILL_FAILS = 8

/** Movers kept back to set off again. */
private const val SPARE = 64

/** How far short of a crossing the first vehicle waits, and each after it, in tiles. */
private const val QUEUE_GAP = 0.12f
private const val QUEUE_STEP = 0.3f

/** Too small to see below these. */
private const val MIN_VEHICLE_PX = 12f
private const val MIN_PEOPLE_PX = 20f
