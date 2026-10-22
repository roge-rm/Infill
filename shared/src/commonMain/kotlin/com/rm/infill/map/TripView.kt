package com.rm.infill.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.Junction
import com.rm.infill.sim.LineState
import com.rm.infill.sim.RoadType
import com.rm.infill.sim.TripSample
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
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
        /** Whether each of the route's tiles is in a tunnel, and whether it keeps to the left lane of a one-way road. */
        var tileUnder = BooleanArray(0)
        var left = false
        /** How far it's gone, in tiles, and the stretch that's on. */
        var dist = 0f
        var seg = 0
        /** Where the next stretch starts. */
        var segEnd = 0f
        /**
         * At the crossing on route tile [heldAt]: when it was first held at
         * the line on the game clock, or -1, and whether it's been let through.
         */
        var heldAt = -1
        var held = -1f
        var cleared = false
        /** Whether it came within [BRAKE] of the line there on green, so it goes on through amber. */
        var committed = false
        /** Whether it barely moved this frame, and since when it's barely moved on the game clock, or -1. */
        var stuck = false
        var still = -1f
        /**
         * Kept for the route tile [at] it's on, so a frame reads little: the
         * tile and whether it's in a tunnel, its speed there, its chain in
         * the queues or -1 off the road, where its lane on the tile starts in
         * its own distance and whether it's the route's last tile. Then the
         * next tile or -1, where it comes onto it, whether that's in a
         * tunnel and its chain there. [half] is half its length.
         */
        var at = -1
        var tile = 0
        var hereUnder = false
        var speed = 0f
        var key = -1
        var from = 0f
        var last = false
        var next = -1
        var edge = 0f
        var nextUnder = false
        var nextKey = -1
        var half = 0f
        /** Where on its tile it's halfway round a turn, and its chain from there on, in the lane it turns into; -1 going straight. */
        var turnAt = Float.MAX_VALUE
        var exitKey = -1
        /** The sample and route it was built from, to build it again if a crossing on its way changes. */
        var sample: TripSample? = null
        var r = 0
        /**
         * Its chain, and how far on it is in it. Past halfway round a turn,
         * and on its first tile, where it pulls out from the curb, that's
         * counted back from the tile's far side as for one going straight.
         */
        val chainKey get() = if (dist >= turnAt) exitKey else key
        val chainPos get() = if (dist >= turnAt || at == 0 && !last) 1f - (edge - dist) else dist - from
        val segs get() = count - 1
        val length get() = along[count - 1]
        val vehicle get() = kind == TripSample.CAR || kind == TripSample.TRUCK
        /** On the road with the traffic: a vehicle or a cyclist. */
        val rides get() = kind != TripSample.WALKER
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

    /**
     * Who's where, noted into [noting] as each mover moves and read from
     * [seen] the next frame, so each follows the one ahead of it in its lane
     * as they all were before any moved.
     */
    private class Notes {
        /**
         * Numbered by place in [movers]: each one's chain, by the tile it's
         * on, the way it came onto it and its lane, or -1 off the road;
         * whether it's standing still or nearly; how far it is onto its tile
         * and half its length, in tiles; if it's waiting at a stop sign,
         * since when and where; and the crossing with lights or a stop sign
         * its front or back reaches onto from another tile, as tile * 8, the
         * way it comes or came onto it * 2 and 1 if it turns left or back
         * there, or -1; and whether it turns left or back on its own tile.
         * [cameOf] is its chain by the way it came onto its tile, for
         * crossings, where [keyOf] has it in the lane it turns into.
         */
        var keyOf = IntArray(0)
        var cameOf = IntArray(0)
        var stuck = BooleanArray(0)
        var pos = FloatArray(0)
        var half = FloatArray(0)
        var waitOf = FloatArray(0)
        var nextOf = IntArray(0)
        var reachOf = IntArray(0)
        var leftOf = BooleanArray(0)

        fun room(n: Int) {
            if (n <= keyOf.size) return
            val room = n + n / 2 + 64
            keyOf = keyOf.copyOf(room)
            cameOf = cameOf.copyOf(room)
            stuck = stuck.copyOf(room)
            pos = pos.copyOf(room)
            half = half.copyOf(room)
            waitOf = waitOf.copyOf(room)
            nextOf = nextOf.copyOf(room)
            reachOf = reachOf.copyOf(room)
            leftOf = leftOf.copyOf(room)
        }

        fun copy(from: Int, to: Int) {
            keyOf[to] = keyOf[from]
            cameOf[to] = cameOf[from]
            stuck[to] = stuck[from]
            pos[to] = pos[from]
            half[to] = half[from]
            waitOf[to] = waitOf[from]
            nextOf[to] = nextOf[from]
            reachOf[to] = reachOf[from]
            leftOf[to] = leftOf[from]
        }
    }

    private var seen = Notes()
    private var noting = Notes()
    /**
     * The chains, hashed by chain into slots, each slot a list from the
     * furthest on to the least: [head] and [tail] its ends or -1, [link] the
     * next back and [back] the next forward. [started] lists the slots
     * in use, emptied before they're filled again. Chained at the start of
     * each frame from where all were before they moved, and again in
     * [chainedAt]'s frame from where they are after, for [crowded].
     */
    private var frame = 0
    private var head = IntArray(0)
    private var tail = IntArray(0)
    private var bits = 0
    private var link = IntArray(0)
    private var back = IntArray(0)
    private var started = IntArray(0)
    private var startedCount = 0
    private var chainedAt = 0
    /** At each stop sign: the earliest arrival still waiting this frame, and when the next can go, on the game clock. */
    private var firstAt = FloatArray(0)
    private var firstStamp = IntArray(0)
    private var freeAt = FloatArray(0)
    /** On each crossing with lights or stop signs: the frame it was last marked in and which ways those on it came, as [mark] has them. */
    private var boxStamp = IntArray(0)
    private var boxWays = IntArray(0)
    /** By tile and the way it's come onto: when a queue there last reached back to where it comes on, on the game clock. */
    private var packedAt = FloatArray(0)
    /** The frame a tram or bus was last on or coming onto each crossing. */
    private var transitAt = IntArray(0)
    private val transitRoom = TransitRoom()
    /** The map revision last looked at, and each tile's junction control then. */
    private var revisionSeen = 0
    private var controlSeen = ByteArray(0)

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
     * short of the [stopped] tiles, a level crossing with a train on it say,
     * stop for lights and stop signs, wait for a crossing to clear before
     * going onto it, wait for a tram or bus of [lines] to cross, and queue
     * behind the one ahead. One held still for [STILL_MOST] seconds leaves the
     * map, so no jam lasts for good. When [revision] moves on, those with a
     * crossing ahead whose control has changed find their way again.
     */
    fun update(sample: TripSample, map: CityMap, time: Float, most: Int, stopped: Set<Int>, lines: Collection<LineState> = emptyList(), revision: Int = 0) {
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
        if (blocked.size != map.size) {
            blocked = BooleanArray(map.size)
            firstAt = FloatArray(map.size)
            firstStamp = IntArray(map.size)
            freeAt = FloatArray(map.size)
            boxStamp = IntArray(map.size)
            boxWays = IntArray(map.size)
            packedAt = FloatArray(map.size * 4).also { it.fill(-PACKED_HOLD) }
            transitAt = IntArray(map.size)
            controlSeen = map.control.copyOf()
        }
        if (revision != revisionSeen) {
            revisionSeen = revision
            recontrol(map)
        }
        if (stopped.isNotEmpty()) for (i in stopped) blocked[i] = true
        frame++
        if (lines.isNotEmpty()) markTransit(map, time, lines, transitRoom) { transitAt[it] = frame }
        snapshot(map)
        // Last first, so one taken off swaps in one already moved.
        noting.room(movers.size)
        var k = movers.size - 1
        while (k >= 0) {
            val m = movers[k]
            if (move(k, m, map, dt, time)) note(noting, k, m, map) else retire(k, noting)
            k--
        }
        val was = seen
        seen = noting
        noting = was
        if (stopped.isNotEmpty()) for (i in stopped) blocked[i] = false
        if (movers.isEmpty() && filled !== sample) {
            filled = sample
            filling = true
        }
        if (filling) {
            // Starting out, say just after loading: each somewhere along its way, a batch a frame.
            var budget = FILL_EACH_FRAME
            var failed = 0
            while (budget-- > 0 && movers.size < want && failed < FILL_FAILS) if (!spawn(sample, map, time, partway = true)) failed++
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
            spawn(sample, map, time, partway = false)
            due -= 1f
        }
    }

    /** Takes mover [k] off the map, keeping it to set off again, and its place in [into]. */
    private fun retire(k: Int, into: Notes = seen) {
        val m = movers[k]
        val last = movers.size - 1
        movers[k] = movers[last]
        movers.removeAt(last)
        into.copy(last, k)
        if (spare.size < SPARE) spare.add(m)
    }

    /**
     * Builds again the way of each mover with a crossing on its way on whose
     * control has changed, a new roundabout say, from the same share of the
     * way across the tile it's on; one that can't be built again leaves.
     */
    private fun recontrol(map: CityMap) {
        val control = map.control
        var any = false
        for (i in 0 until map.size) if (controlSeen[i] != control[i]) {
            any = true
            break
        }
        if (!any) return
        var k = movers.size - 1
        while (k >= 0) {
            val m = movers[k]
            var changed = false
            for (j in max(0, m.at) until m.n) if (controlSeen[m.route[j]] != control[m.route[j]]) {
                changed = true
                break
            }
            if (changed) {
                if (rebuild(m, map)) note(seen, k, m, map) else retire(k)
            }
            k--
        }
        control.copyInto(controlSeen)
    }

    private fun rebuild(m: Mover, map: CityMap): Boolean {
        val sample = m.sample ?: return false
        val j = max(0, m.at)
        val n = m.n
        val start = m.enter[j]
        val end = if (j < n - 1) m.enter[j + 1] else m.length
        val share = if (end > start) ((m.dist - start) / (end - start)).coerceIn(0f, 1f) else 0f
        val still = m.still
        if (!build(sample, m.r, map, m.seed, m) || m.n != n) return false
        val to = if (j < n - 1) m.enter[j + 1] else m.length
        m.dist = m.enter[j] + share * (to - m.enter[j])
        m.still = still
        settle(m, map)
        return true
    }

    /** Notes where [m], number [k] in [movers], is into [notes], for the next frame's queues. */
    private fun note(notes: Notes, k: Int, m: Mover, map: CityMap) {
        notes.room(k + 1)
        notes.keyOf[k] = m.chainKey
        notes.cameOf[k] = m.key
        notes.stuck[k] = m.stuck
        notes.pos[k] = m.chainPos
        notes.half[k] = m.half
        val waiting = m.held >= 0f && !m.cleared && m.next >= 0 && m.heldAt == m.at + 1 && map.control[m.next] == Junction.STOP
        notes.waitOf[k] = if (waiting) m.held else -1f
        notes.nextOf[k] = m.next
        notes.reachOf[k] = reach(m, map)
        notes.leftOf[k] = m.at >= 0 && left(m, m.at)
    }

    /** Whether [m] turns left or back on route tile [j]: across the way of those coming the other way. */
    private fun left(m: Mover, j: Int): Boolean {
        if (j <= 0 || j >= m.n - 1) return false
        val into = way(m.route[j - 1], m.route[j])
        val out = way(m.route[j], m.route[j + 1])
        return out == (into + 3) % 4 || out == (into + 2) % 4
    }

    /** The crossing with lights or a stop sign that [m]'s front or back reaches onto from the tile it's on, as [Notes.reachOf] has it, or -1. */
    private fun reach(m: Mover, map: CityMap): Int {
        if (!m.rides || m.at < 0 || m.hereUnder) return -1
        val next = m.next
        if (next >= 0 && !m.nextUnder && m.dist + m.half > m.edge + LINE_SLACK && signed(map.control[next])) {
            return next * 8 + way(m.tile, next) * 2 + if (left(m, m.at + 1)) 1 else 0
        }
        val j = m.at
        if (j > 0 && !m.tileUnder[j - 1] && m.dist - m.half < m.from) {
            val prev = m.route[j - 1]
            if (signed(map.control[prev])) return prev * 8 + (if (j > 1) way(m.route[j - 2], prev) else way(prev, m.tile)) * 2 + if (left(m, j - 1)) 1 else 0
        }
        return -1
    }

    private fun signed(control: Byte) = control == Junction.LIGHTS || control == Junction.STOP

    private fun slot(key: Int) = (key * -1640531535) ushr (32 - bits)

    /**
     * Chains every mover on the road by its tile, the way it came onto it and
     * its lane, for [ahead], from where they were noted, and marks which ways
     * those on each crossing with lights or stop signs came onto it.
     */
    private fun snapshot(map: CityMap) {
        val count = movers.size
        unchain(count)
        val keyOf = seen.keyOf
        for (k in 0 until count) {
            // Reaching onto a crossing from the tile before or after it.
            val reach = seen.reachOf[k]
            if (reach >= 0) mark(reach / 8, reach / 2 % 4, reach % 2 == 1)
            val key = keyOf[k]
            if (key < 0) continue
            chain(k)
            val came = seen.cameOf[k]
            val tile = came / 12
            if (signed(map.control[tile])) mark(tile, came / 3 % 4, seen.leftOf[k])
            // Waiting at a stop sign: the earliest there goes first.
            val wait = seen.waitOf[k]
            if (wait >= 0f) {
                val next = seen.nextOf[k]
                if (firstStamp[next] != frame || wait < firstAt[next]) firstAt[next] = wait
                firstStamp[next] = frame
            }
        }
    }

    /** Notes that someone's on crossing [tile] come onto it going [way], and whether they turn [left] or back there: bits 0-3 by way, 4-7 by way for those turning left. */
    private fun mark(tile: Int, way: Int, left: Boolean) {
        if (boxStamp[tile] != frame) {
            boxStamp[tile] = frame
            boxWays[tile] = 0
        }
        boxWays[tile] = boxWays[tile] or (1 shl way) or (if (left) 1 shl (4 + way) else 0)
    }

    /** Empties the slots, with room for [count] movers in them. */
    private fun unchain(count: Int) {
        for (c in 0 until startedCount) {
            head[started[c]] = -1
            tail[started[c]] = -1
        }
        startedCount = 0
        if (link.size < count) {
            val room = count + count / 2 + 16
            link = link.copyOf(room)
            back = back.copyOf(room)
            started = started.copyOf(room)
        }
        // Four slots or more a mover, so few chains share one.
        if (head.size < count * 4 || head.isEmpty()) {
            var b = 10
            while ((1 shl b) < count * 4) b++
            bits = b
            head = IntArray(1 shl b).also { it.fill(-1) }
            tail = IntArray(1 shl b).also { it.fill(-1) }
        }
    }

    /** Chains number [k] of [movers] by where it's noted in [seen]. */
    private fun chain(k: Int) {
        if (link.size <= k) {
            val room = k + k / 2 + 16
            link = link.copyOf(room)
            back = back.copyOf(room)
            started = started.copyOf(room)
        }
        val pos = seen.pos
        val at = slot(seen.keyOf[k])
        val p = pos[k]
        // In behind the last one as far on or further, looking from the back.
        var o = tail[at]
        if (o < 0) started[startedCount++] = at
        while (o >= 0 && pos[o] < p) o = back[o]
        val after = if (o >= 0) link[o] else head[at]
        back[k] = o
        link[k] = after
        if (o >= 0) link[o] = k else head[at] = k
        if (after >= 0) back[after] = k else tail[at] = k
    }

    /** Whether anyone's in chain [key]. */
    private fun anyIn(key: Int): Boolean {
        val keyOf = seen.keyOf
        var o = head[slot(key)]
        while (o >= 0) {
            if (keyOf[o] == key) return true
            o = link[o]
        }
        return false
    }

    /** Finds the stretch [m] is on now and, if it's come onto a new tile, notes the tile and what's ahead of it. */
    private fun settle(m: Mover, map: CityMap) {
        while (m.seg < m.segs - 1 && m.along[m.seg + 1] <= m.dist) m.seg++
        m.segEnd = if (m.seg < m.segs - 1) m.along[m.seg + 1] else Float.MAX_VALUE
        val j = m.step[m.seg]
        if (j == m.at) return
        m.at = j
        val tile = m.route[j]
        m.tile = tile
        m.hereUnder = m.tileUnder[j]
        m.last = j == m.n - 1
        m.from = if (j == 0) -0.5f else m.enter[j]
        m.key = if (!m.rides || m.hereUnder) -1 else key(m, tile, if (j > 0) way(m.route[j - 1], tile) else way(tile, m.route[1]), map)
        var speed = when (m.kind) {
            TripSample.WALKER -> WALK_TILES
            TripSample.CYCLIST -> BIKE_TILES
            else -> ROAD_TILES * (1f - min(0.75f, (map.congestion[tile].toInt() and 0xff) / 340f))
        }
        if (m.rides && !m.hereUnder && map.control[tile] == Junction.ROUNDABOUT) speed *= ROUND_SLOW
        m.speed = speed
        m.next = if (m.last) -1 else m.route[j + 1]
        m.edge = if (m.last) 0f else m.enter[j + 1]
        m.nextUnder = !m.last && m.tileUnder[j + 1]
        m.nextKey = if (m.last || m.nextUnder || !m.rides) -1 else key(m, m.next, way(tile, m.next), map)
        // Not round a roundabout, where those on it give way by the road they came from.
        val turns = m.rides && !m.hereUnder && j > 0 && !m.last && way(m.route[j - 1], tile) != way(tile, m.next) && map.control[tile] != Junction.ROUNDABOUT
        m.exitKey = if (turns) key(m, tile, way(tile, m.next), map) else -1
        m.turnAt = if (turns) (m.from + m.edge) / 2 else Float.MAX_VALUE
    }

    /** The chain for [m] on [tile], come onto it going [way]: by its lane, its own for cyclists, the left of a one-way road, or the right. */
    private fun key(m: Mover, tile: Int, way: Int, map: CityMap): Int {
        val lane = when {
            m.kind == TripSample.CYCLIST -> 2
            m.left && map.roadHeading[tile].toInt() != 0 -> 1
            else -> 0
        }
        return (tile * 4 + way) * 3 + lane
    }

    /**
     * How far [m], number [k] in [movers], can go before it reaches the one
     * ahead of it in its lane, on its tile or the next, as they were at the
     * start of the frame; the most there is if nobody's ahead. Short of
     * halfway round a turn, that's the one ahead in the lane it turns into.
     */
    private fun ahead(k: Int, m: Mover): Float {
        val keyOf = seen.keyOf
        val key = keyOf[k]
        if (key < 0) return Float.MAX_VALUE
        val pos = seen.pos
        var o = back[k]
        while (o >= 0 && keyOf[o] != key) o = back[o]
        if (o >= 0) return m.dist - pos[k] + pos[o] - seen.half[o] - m.half - FOLLOW_GAP
        val exit = m.exitKey
        if (exit >= 0 && key != exit) {
            val p = 1f - (m.edge - m.dist)
            o = last(exit)
            while (o >= 0 && (keyOf[o] != exit || pos[o] <= p)) o = back[o]
            if (o >= 0) return m.dist - p + pos[o] - seen.half[o] - m.half - FOLLOW_GAP
        }
        val key2 = m.nextKey
        if (key2 < 0) return Float.MAX_VALUE
        // Nobody ahead on this tile: the last one onto the next, the way this one's going.
        o = last(key2)
        if (o < 0) return Float.MAX_VALUE
        return m.edge + pos[o] - seen.half[o] - m.half - FOLLOW_GAP
    }

    /** The least far on in chain [key], or -1 if nobody's in it. */
    private fun last(key: Int): Int {
        val keyOf = seen.keyOf
        var o = tail[slot(key)]
        while (o >= 0 && keyOf[o] != key) o = back[o]
        return o
    }

    /**
     * Whether [m], at the line of the crossing [next] from route tile [j], has
     * to wait for the crossing to clear: someone's on it, even partly, who
     * came from across its way, or from the other way when either of them
     * turns left, or the lane it leaves by is stopped on the crossing or too
     * close past it. A ring of queues that locks up waiting is undone by
     * [STILL_MOST].
     */
    private fun boxed(m: Mover, map: CityMap, j: Int, next: Int): Boolean {
        if (boxStamp[next] == frame) {
            val w = way(m.route[j], next)
            val ways = boxWays[next]
            val other = (w + 2) % 4
            if (ways and ((1 shl ((w + 1) % 4)) or (1 shl ((w + 3) % 4))) != 0) return true
            if (ways and (if (left(m, j + 1)) 1 shl other else 1 shl (4 + other)) != 0) return true
        }
        if (j + 2 >= m.n || m.tileUnder[j + 2]) return false
        val exit = m.route[j + 2]
        val out = way(next, exit)
        val on = last(key(m, next, out, map))
        if (on >= 0 && seen.stuck[on]) return true
        val o = last(key(m, exit, out, map))
        return o >= 0 && seen.stuck[o] && seen.pos[o] - seen.half[o] - FOLLOW_GAP < 2 * m.half
    }

    /** Moves [m], number [k] in [movers], on by [dt] seconds to [time]; false once it's at its end. */
    private fun move(k: Int, m: Mover, map: CityMap, dt: Float, time: Float): Boolean {
        val j = m.at
        if (m.heldAt != j + 1) {
            m.heldAt = j + 1
            m.held = -1f
            m.cleared = false
            m.committed = false
        }
        var d = m.dist + dt * m.speed
        val next = m.next
        val edge = m.edge
        // Only near the next tile is there anything to stop for.
        if (next >= 0 && d > edge - m.half - BRAKE) {
            // Waiting short of a level crossing with a train on it, a bridge that's up or a crossing a tram or bus is on. One already past where it'd wait carries on through.
            val wait = edge - QUEUE_GAP - m.half
            if (m.rides && m.dist <= wait && (blocked[next] || transitAt[next] == frame && !m.nextUnder)) {
                d = min(d, wait)
            } else if (!m.cleared && !m.nextUnder) {
                // At the line: its front at the edge of the crossing, or a walker at the curb.
                val line = line(m)
                // Within BRAKE of the line on green: too close to stop if it turns amber.
                if (m.rides && !m.committed && m.held < 0f && m.dist > line - BRAKE && map.control[next] == Junction.LIGHTS &&
                    signal(next, m.tile % map.width == next % map.width, time) == LIGHT_GREEN
                ) m.committed = true
                if (m.dist <= line && d > line && waits(m, map, j, next, time)) {
                    d = max(m.dist, line)
                    if (m.held < 0f) m.held = time
                }
            }
        }
        if (m.rides) {
            val most = ahead(k, m)
            if (d > most) d = max(m.dist, most)
        }
        m.stuck = d - m.dist < dt * m.speed * STUCK
        m.dist = d
        if (d >= m.length) return false
        // Held still too long, in a ring of queues that's locked up say: it goes, which frees the rest.
        if (dt > 0f) {
            if (!m.stuck) m.still = -1f
            else if (m.still < 0f || m.still > time) m.still = time
            else if (time - m.still > STILL_MOST) return false
        }
        // Held in a queue that reaches back to where its lane comes onto the tile: nobody new sets off this way for a while.
        if (m.stuck && m.key >= 0 && d - m.from - m.half < PACKED_BACK) packedAt[m.key / 3] = time
        if (d >= m.segEnd) settle(m, map)
        // Gone at a road that's been taken up since last month.
        return !m.vehicle || m.hereUnder || map.road[m.tile].toInt() != 0
    }

    /**
     * Whether [m], on route tile [j] and about to cross its line onto the
     * crossing at [next], has to wait there at [time]. Lights go by the way
     * it comes in, whichever way it turns; on amber it stops unless it was
     * already within [BRAKE] of the line on green. At a stop sign it waits a moment, then goes once those there
     * before it have. At a roundabout it gives way to those already on it
     * from the road to its left, but not for ever.
     */
    private fun waits(m: Mover, map: CityMap, j: Int, next: Int, time: Float): Boolean {
        when (map.control[next]) {
            Junction.LIGHTS -> {
                val vertical = m.route[j] % map.width == next % map.width
                return when (signal(next, vertical, time)) {
                    LIGHT_GREEN -> m.rides && boxed(m, map, j, next)
                    LIGHT_AMBER -> !m.rides || m.held >= 0f || !m.committed || boxed(m, map, j, next)
                    else -> true
                }
            }
            Junction.STOP -> {
                if (!m.rides) return false
                if (m.held < 0f) return true
                val free = freeAt[next]
                // A clock that's gone back, after loading say, leaves it free.
                val clear = free <= time || free > time + CROSS_GAP
                val first = firstStamp[next] != frame || m.held <= firstAt[next]
                if (time - m.held < STOP_WAIT || !clear || !first || boxed(m, map, j, next)) return true
                freeAt[next] = time + CROSS_GAP
                m.cleared = true
                return false
            }
            Junction.ROUNDABOUT -> {
                if (!m.rides) return false
                // Those on it from the road to its left, going round ahead of it, in any lane.
                val key = (next * 4 + (way(m.route[j], next) + 1) % 4) * 3
                val on = (m.held < 0f || time - m.held < ROUND_MOST) && (anyIn(key) || anyIn(key + 1) || anyIn(key + 2))
                if (!on) m.cleared = true
                return on
            }
            else -> return false
        }
    }

    /** Where [m] waits for the next tile: its front at the edge of it, or a walker at the curb. */
    private fun line(m: Mover) = if (m.rides) m.edge - m.half else m.edge - CURB_GAP

    /** Starts a trip picked from [sample] by weight at [time], at its start or, if [partway], somewhere along it. */
    private fun spawn(sample: TripSample, map: CityMap, time: Float, partway: Boolean): Boolean {
        repeat(TRIES) {
            val roll = (next() * sample.totalWeight).toLong().coerceIn(0L, sample.totalWeight - 1)
            val into = if (spare.isEmpty()) Mover() else spare.removeAt(spare.size - 1)
            if (!build(sample, sample.pick(roll), map, (state ushr 32).toInt(), into)) {
                spare.add(into)
                return@repeat
            }
            if (partway) into.dist = next().toFloat() * into.length
            settle(into, map)
            // Not past the line of a crossing it'd stop at, so it still stops there: back at the line.
            if (partway && into.next >= 0 && !into.nextUnder && map.control[into.next] != Junction.NONE && into.dist > line(into)) {
                into.dist = max(0f, line(into))
                into.seg = 0
                into.at = -1
                settle(into, map)
            }
            // Not on top of one already there, at the back of a queue say: another trip instead.
            // Nor into a queue ahead of it that's backed up, so a jam fills no faster than the lights clear it.
            if (into.chainKey >= 0 && crowded(into) || into.rides && packed(into, time)) {
                spare.add(into)
                return@repeat
            }
            movers.add(into)
            note(seen, movers.size - 1, into, map)
            if (into.chainKey >= 0 && chainedAt == frame) chain(movers.size - 1)
            return true
        }
        return false
    }

    /** Whether a queue on the way ahead of [m] has reached back over a whole tile in the last [PACKED_HOLD] seconds before [time]. */
    private fun packed(m: Mover, time: Float): Boolean {
        val route = m.route
        for (j in max(0, m.at) until m.n) {
            if (m.tileUnder[j]) continue
            val w = if (j > 0) way(route[j - 1], route[j]) else way(route[0], route[1])
            val at = packedAt[route[j] * 4 + w]
            // A clock that's gone back, after loading say, leaves it clear.
            if (at <= time && time - at < PACKED_HOLD) return true
        }
        return false
    }

    /** Whether one of [movers] is where [m] would be in its lane, from the chains as they are after this frame's moves. */
    private fun crowded(m: Mover): Boolean {
        if (chainedAt != frame) {
            chainedAt = frame
            unchain(movers.size)
            for (k in 0 until movers.size) if (seen.keyOf[k] >= 0) chain(k)
        }
        val p = m.chainPos
        val key = m.chainKey
        val keyOf = seen.keyOf
        var o = head[slot(key)]
        while (o >= 0) {
            if (keyOf[o] == key && abs(seen.pos[o] - p) < seen.half[o] + m.half + FOLLOW_GAP) return true
            o = link[o]
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
            m.tileUnder = BooleanArray(n)
        }
        val route = m.route
        for (j in 0 until n) {
            route[j] = sample.tile(r, j)
            m.tileUnder[j] = sample.under(r, j)
        }
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
            if (kind != TripSample.WALKER && prev >= 0 && next >= 0 && map.control[route[j]] == Junction.ROUNDABOUT && !sample.under(r, j)) {
                ring(j, prev, next)
            } else if (prev >= 0 && prev == next) {
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
        m.sample = sample
        m.r = r
        m.left = left
        m.n = n
        m.count = count
        m.dist = 0f
        m.seg = 0
        m.heldAt = -1
        m.held = -1f
        m.cleared = false
        m.committed = false
        m.stuck = false
        m.still = -1f
        m.at = -1
        m.half = halfLength(kind)
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

    /**
     * Round the roundabout on route tile [j], from [prev] to [next]: in along
     * its lane to the ring, anticlockwise round the island, since traffic
     * keeps right, and out along its lane. Turning back goes most of the way round.
     */
    private fun ring(j: Int, prev: Int, next: Int) {
        val w = bW
        val here = bRoute[j]
        val cx = here % w + 0.5f
        val cy = here / w + 0.5f
        // In and out on the side the tiles either side end on, so there's no jump.
        val sIn = side(j, 0f)
        val sOut = side(j, 1f)
        // The way in and out, and right of each: (-y, x) with y down the map.
        val ix = (here % w - prev % w).toFloat()
        val iy = (here / w - prev / w).toFloat()
        val ox = (next % w - here % w).toFloat()
        val oy = (next / w - here / w).toFloat()
        put(cx - ix * 0.5f - iy * sIn, cy - iy * 0.5f + ix * sIn, j)
        ringInto(prev, here, next, w, sideAt[j], spot)
        val radius = spot[0]
        val from = spot[1]
        val sweep = spot[2]
        val points = max(2, ceil(-sweep / (PI.toFloat() / 2) * ARC).toInt())
        for (k in 0..points) {
            val a = from + sweep * k / points
            put(cx + radius * cos(a), cy + radius * sin(a), j)
        }
        put(cx + ox * 0.5f - oy * sOut, cy + oy * 0.5f + ox * sOut, j)
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

/** How far short of a level crossing the first vehicle's front waits, in tiles. */
private const val QUEUE_GAP = 0.12f

/** The room left between one in a queue and the one ahead, and how far short of the crossing a walker waits at the curb, in tiles. */
private const val FOLLOW_GAP = 0.06f
private const val CURB_GAP = 0.02f

/** Half the length of each kind, in tiles: a cart or car, a lorry and a bicycle. */
private fun halfLength(kind: Int) = when (kind) {
    TripSample.TRUCK -> 0.2f
    TripSample.CYCLIST -> 0.09f
    TripSample.WALKER -> 0.05f
    else -> 0.14f
}

/** How far past its line in tiles one held there can be and not count as on the crossing, as Float sums put it a hair over. */
private const val LINE_SLACK = 1e-3f

/** On amber, one this close to the line in tiles goes on through. */
private const val BRAKE = 0.15f

/** The share of its speed below which one counts as stopped, and the longest it stays stopped before leaving the map, in seconds. */
private const val STUCK = 0.5f
private const val STILL_MOST = 25f

/**
 * A queue whose last one is held this close to where its lane comes onto the
 * tile, in tiles, has filled the tile; for so many seconds after, no trip
 * sets off through it.
 */
private const val PACKED_BACK = 0.3f
private const val PACKED_HOLD = 5f

/** Seconds at a stop sign before going, and after one goes before the next can. */
private const val STOP_WAIT = 1f
private const val CROSS_GAP = 0.8f

/** How far out from a roundabout's middle traffic goes round, in tiles, past the island, plus how far right it keeps. */
private const val RING = 0.2f

/**
 * Round the roundabout on tile [here] from [prev] to [next], keeping [side]
 * tiles right of the way, into [out]: the ring's radius, the angle it comes
 * onto the ring at and the sweep round to where it leaves, in radians,
 * negative since it goes anticlockwise, as traffic keeps right. Turning back
 * goes most of the way round.
 */
internal fun ringInto(prev: Int, here: Int, next: Int, width: Int, side: Float, out: FloatArray) {
    val radius = RING + abs(side)
    val reach = sqrt(radius * radius - side * side)
    val ix = (here % width - prev % width).toFloat()
    val iy = (here / width - prev / width).toFloat()
    val ox = (next % width - here % width).toFloat()
    val oy = (next / width - here / width).toFloat()
    val from = atan2(-iy * reach + ix * side, -ix * reach - iy * side)
    val to = atan2(oy * reach + ox * side, ox * reach - oy * side)
    // Anticlockwise on the map is the angle going down, with y down.
    var sweep = to - from
    while (sweep >= 0f) sweep -= 2 * PI.toFloat()
    while (sweep < -2 * PI.toFloat()) sweep += 2 * PI.toFloat()
    out[0] = radius
    out[1] = from
    out[2] = sweep
}

/**
 * As [pointThroughInto], for a roundabout on tile [here]: [u] of the way
 * along, by length, from the edge by [prev] in along its lane, round the
 * ring as [ringInto] has it and out to the edge by [next], into [out] as x,
 * y and heading in degrees.
 */
internal fun pointRoundInto(prev: Int, here: Int, next: Int, u: Float, width: Int, side: Float, out: FloatArray) {
    ringInto(prev, here, next, width, side, out)
    val radius = out[0]
    val from = out[1]
    val sweep = out[2]
    val cx = here % width + 0.5f
    val cy = here / width + 0.5f
    val ix = (here % width - prev % width).toFloat()
    val iy = (here / width - prev / width).toFloat()
    val ox = (next % width - here % width).toFloat()
    val oy = (next / width - here / width).toFloat()
    // In from the edge to the ring, round it, and out from the ring to the edge.
    val inX = cx - ix * 0.5f - iy * side
    val inY = cy - iy * 0.5f + ix * side
    val onX = cx + radius * cos(from)
    val onY = cy + radius * sin(from)
    val offX = cx + radius * cos(from + sweep)
    val offY = cy + radius * sin(from + sweep)
    val outX = cx + ox * 0.5f - oy * side
    val outY = cy + oy * 0.5f + ox * side
    val a = sqrt((onX - inX) * (onX - inX) + (onY - inY) * (onY - inY))
    val b = -sweep * radius
    val c = sqrt((outX - offX) * (outX - offX) + (outY - offY) * (outY - offY))
    var d = u.coerceIn(0f, 1f) * (a + b + c)
    if (d < a) {
        val f = if (a > 0f) d / a else 0f
        out[0] = inX + (onX - inX) * f
        out[1] = inY + (onY - inY) * f
        out[2] = atan2(onY - inY, onX - inX) * 180f / PI.toFloat()
        return
    }
    d -= a
    if (d < b) {
        val angle = from + sweep * (d / b)
        out[0] = cx + radius * cos(angle)
        out[1] = cy + radius * sin(angle)
        // Going anticlockwise, the way runs a quarter turn back from the radius.
        out[2] = (angle - PI.toFloat() / 2) * 180f / PI.toFloat()
        return
    }
    d -= b
    val f = if (c > 0f) (d / c).coerceAtMost(1f) else 1f
    out[0] = offX + (outX - offX) * f
    out[1] = offY + (outY - offY) * f
    out[2] = atan2(outY - offY, outX - offX) * 180f / PI.toFloat()
}

/** Speed round a roundabout, as a share of the road's, and the longest wait to get on it in seconds. */
private const val ROUND_SLOW = 0.6f
private const val ROUND_MOST = 2f

/** Too small to see below these. */
private const val MIN_VEHICLE_PX = 12f
private const val MIN_PEOPLE_PX = 20f

/** Which way it is from tile [a] to the next tile [b]: 0 north, 1 east, 2 south, 3 west. */
private fun way(a: Int, b: Int): Int {
    val d = b - a
    return when {
        d == 1 -> 1
        d == -1 -> 3
        d > 0 -> 2
        else -> 0
    }
}
