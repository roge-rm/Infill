package com.rm.infill.sim

import kotlin.math.max
import kotlin.math.min

/**
 * Trips on the roads. Each month every home's workers look for the nearest
 * jobs with room, its residents go to the nearest shops, and works send
 * freight to the edge of the map. Trips start and end on road tiles, the one
 * each building is reached from. The search runs over road tiles: one-way
 * tiles can't be driven backwards, and each tile takes its road's time to
 * cross, longer the busier it was last month.
 *
 * Stations on the same line are joined by train: a trip can board at one and
 * get off at another, for the wait and the time on the train, when that's
 * quicker. A freight yard on a line to the edge takes freight like the edge.
 *
 * The trips are spread over the month, a slice of the starting tiles each
 * day, and big groups go in parts. They add up on every tile they cross, and
 * a road is as slow as the busier of last month and this month so far, so
 * traffic finds its way round busy roads.
 */
internal class Traffic(private val map: CityMap) {
    /** Trips across each tile this month so far, and a running average of past months. */
    val volume = IntArray(map.size)
    val lastVolume = IntArray(map.size)

    /** This month's travellers by the road tile they start from, in the order they're sent. */
    private var origins = IntArray(0)
    private var workers = IntArray(0)
    private var shoppers = IntArray(0)
    private var freight = IntArray(0)

    /** Room left for workers and shoppers at each road tile. */
    private val jobsLeft = IntArray(map.size)
    private val shopsLeft = IntArray(map.size)

    /** This month so far, by starting tile: workers placed, their seconds of travel, freight that got out. */
    private val placed = IntArray(map.size)
    private val travel = LongArray(map.size)
    private val shipped = IntArray(map.size)

    /**
     * Last month, by starting tile: the average commute in seconds, -1 if
     * workers set out and none found a job, 0 if none set out.
     */
    val commute = IntArray(map.size)

    /** Last month, by starting tile: most of the freight from here couldn't reach the edge. */
    val freightStuck = BooleanArray(map.size)

    /** Last month's workers who set out, and those who found a job they could get to. */
    var workersSent = 0
        private set
    var workersPlaced = 0
        private set

    // The railway, set by the city whenever it changes. Each stop is a station
    // or yard: the road tile it's reached from and the track tile its trains stop at.
    private var stopNode = IntArray(0)
    private var stopTrack = IntArray(0)
    private var stopTimes = emptyArray<IntArray>()
    private var stopHere = IntArray(map.size) { -1 }

    /** The freight yard on a line to the edge that each road tile reaches, or -1. */
    private val outlet = IntArray(map.size) { -1 }

    /** This month and last: passengers boarding or leaving at each road tile, and freight sent by train from it. */
    private val riders = IntArray(map.size)
    val lastRiders = IntArray(map.size)
    private val railFreight = IntArray(map.size)
    val lastRailFreight = IntArray(map.size)

    /**
     * Trips by train this month and last, by the track tiles they start and end
     * at (the end is -1 for freight out to the edge), packed into one number.
     */
    private val journeys = HashMap<Long, Int>()
    var lastJourneys: Map<Long, Int> = emptyMap()
        private set

    /**
     * Sets the railway: for each stop the road tile it's reached from (-1 if
     * none), the track tile trains stop at, seconds by train to each other
     * stop (-1 if not on the same line), whether passengers board there, and
     * whether it's a freight yard on a line to the edge.
     */
    fun setRail(node: IntArray, track: IntArray, times: Array<IntArray>, passengers: BooleanArray, freightOut: BooleanArray) {
        stopNode = node
        stopTrack = track
        stopTimes = times
        stopHere.fill(-1)
        outlet.fill(-1)
        for (k in node.indices) {
            if (node[k] < 0) continue
            if (passengers[k] && stopHere[node[k]] < 0) stopHere[node[k]] = k
            if (freightOut[k] && outlet[node[k]] < 0) outlet[node[k]] = k
        }
        // Only passenger stops take passengers.
        for (a in node.indices) for (b in node.indices) if (!passengers[a] || !passengers[b]) times[a][b] = -1
    }

    // Search state, reused. A tile's distance counts only if its stamp is this search's.
    private val dist = IntArray(map.size)
    private val from = IntArray(map.size)

    /** The stops a tile was reached between by train, or -1 if it was reached by road. */
    private val boarded = IntArray(map.size)
    private val alighted = IntArray(map.size)
    private val stamp = IntArray(map.size)
    private var search = 0
    private var heap = IntArray(256)
    private var heapKeys = IntArray(256)
    private var heapSize = 0

    /**
     * Ends one month and starts the next. Last month's trips become the
     * congestion layer and the results above; then this month's travellers
     * and places to go are taken from the arrays, each by road tile.
     * [salt] shuffles who goes first, so no corner of the map always does.
     */
    fun newMonth(workersAt: IntArray, shoppersAt: IntArray, freightAt: IntArray, jobsAt: IntArray, shopsAt: IntArray, salt: Int) {
        for (i in 0 until map.size) lastVolume[i] = (lastVolume[i] + volume[i] + 1) / 2
        volume.fill(0)
        for (i in 0 until map.size) {
            val road = RoadType.of(map.road[i])
            map.congestion[i] = if (road == null) 0 else min(255, lastVolume[i] * 128 / road.capacity).toByte()
        }

        var sent = 0
        var got = 0
        commute.fill(0)
        freightStuck.fill(false)
        for (k in origins.indices) {
            val o = origins[k]
            sent += workers[k]
            got += placed[o]
            if (workers[k] > 0) commute[o] = if (placed[o] == 0) -1 else (travel[o] / placed[o]).toInt()
            freightStuck[o] = freight[k] > 0 && shipped[o] * 2 < freight[k]
        }
        workersSent = sent
        workersPlaced = got
        riders.copyInto(lastRiders)
        riders.fill(0)
        railFreight.copyInto(lastRailFreight)
        railFreight.fill(0)
        lastJourneys = HashMap(journeys)
        journeys.clear()
        placed.fill(0)
        travel.fill(0)
        shipped.fill(0)

        jobsAt.copyInto(jobsLeft)
        shopsAt.copyInto(shopsLeft)
        val starts = ArrayList<Int>()
        for (i in 0 until map.size) if (workersAt[i] > 0 || shoppersAt[i] > 0 || freightAt[i] > 0) starts += i
        starts.sortBy { mixed(it, salt) }
        origins = starts.toIntArray()
        workers = IntArray(origins.size) { workersAt[origins[it]] }
        shoppers = IntArray(origins.size) { shoppersAt[origins[it]] }
        freight = IntArray(origins.size) { freightAt[origins[it]] }
    }

    /** Sends the travellers in slice [day] of [days], so the whole month's have gone by its end. */
    fun sendDay(day: Int, days: Int) {
        val n = origins.size
        for (k in n * (day - 1) / days until n * day / days) send(k)
    }

    /** One tile's trips, in parts of no more than [PART] so the later parts see the roads the first ones filled. */
    private fun send(k: Int) {
        val total = workers[k] + shoppers[k] + freight[k]
        val parts = (total + PART - 1) / PART
        fun share(v: Int, p: Int) = v * (p + 1) / parts - v * p / parts
        for (p in 0 until parts) send(origins[k], share(workers[k], p), share(shoppers[k], p), share(freight[k], p))
    }

    /** A single search outward from [start], taking the nearest room first. */
    private fun send(start: Int, workers: Int, shoppers: Int, freight: Int) {
        var w = workers
        var s = shoppers
        var f = freight
        search++
        heapSize = 0
        dist[start] = 0
        from[start] = -1
        boarded[start] = -1
        stamp[start] = search
        push(start, 0)
        while (heapSize > 0 && (w > 0 || s > 0 || f > 0)) {
            val d = heapKeys[0]
            val a = pop()
            if (d > dist[a]) continue
            if (d > Balance.LONGEST_TRIP) {
                // Too far to go every day; freight goes further.
                w = 0
                s = 0
                if (d > Balance.LONGEST_FREIGHT) break
            }
            if (w > 0 && jobsLeft[a] > 0) {
                val t = min(w, jobsLeft[a])
                jobsLeft[a] -= t
                w -= t
                placed[start] += t
                travel[start] += t.toLong() * d
                carry(a, t)
            }
            if (s > 0 && shopsLeft[a] > 0) {
                val t = min(s, shopsLeft[a])
                shopsLeft[a] -= t
                s -= t
                carry(a, t)
            }
            if (f > 0 && (edge(a) || outlet[a] >= 0)) {
                shipped[start] += f
                carry(a, f)
                if (!edge(a)) {
                    // Out through the yard.
                    railFreight[a] += f
                    val key = (stopTrack[outlet[a]].toLong() shl 32) or 0xffffffffL
                    journeys[key] = (journeys[key] ?: 0) + f
                }
                f = 0
            }
            val x = a % map.width
            val y = a / map.width
            for (h in 1..4) {
                val nx = x + Heading.DX[h]
                val ny = y + Heading.DY[h]
                if (!map.inside(nx, ny)) continue
                val b = ny * map.width + nx
                val road = RoadType.of(map.road[b]) ?: continue
                if (!canMove(map, a, b, h)) continue
                val nd = d + timeToCross(b, road)
                if (stamp[b] == search && nd >= dist[b]) continue
                stamp[b] = search
                dist[b] = nd
                from[b] = a
                boarded[b] = -1
                push(b, nd)
            }
            // By train to the other stations on the line.
            val here = stopHere[a]
            if (here >= 0 && (w > 0 || s > 0)) {
                val times = stopTimes[here]
                for (k in times.indices) {
                    val b = stopNode[k]
                    if (times[k] < 0 || k == here || b < 0 || b == a) continue
                    val nd = d + Balance.RAIL_WAIT + times[k]
                    if (stamp[b] == search && nd >= dist[b]) continue
                    stamp[b] = search
                    dist[b] = nd
                    from[b] = a
                    boarded[b] = here
                    alighted[b] = k
                    push(b, nd)
                }
            }
        }
    }

    /** Adds [trips] to every tile on the way back from [end] to where the search began, and to the trains they took. */
    private fun carry(end: Int, trips: Int) {
        var at = end
        while (at >= 0) {
            volume[at] += trips
            val on = boarded[at]
            if (on >= 0) {
                riders[at] += trips
                riders[stopNode[on]] += trips
                val key = (stopTrack[on].toLong() shl 32) or (stopTrack[alighted[at]].toLong() and 0xffffffffL)
                journeys[key] = (journeys[key] ?: 0) + trips
            }
            at = from[at]
        }
    }

    /** Seconds to cross a tile: its road's time when clear, half as long again at capacity, up to three times. */
    private fun timeToCross(b: Int, road: RoadType): Int {
        val load = max(lastVolume[b], volume[b]) * 32 / road.capacity
        val slow = min(2 * 1024, load * load / 2)
        return road.time + road.time * slow / 1024 + if (map.rail[b] != Rail.NONE) Balance.CROSSING_DELAY else 0
    }

    private fun edge(a: Int): Boolean {
        val x = a % map.width
        val y = a / map.width
        return x == 0 || y == 0 || x == map.width - 1 || y == map.height - 1
    }

    // A binary heap of tiles by distance, ties by tile, so the search always goes the same way.
    private fun push(node: Int, key: Int) {
        if (heapSize == heap.size) {
            heap = heap.copyOf(heapSize * 2)
            heapKeys = heapKeys.copyOf(heapSize * 2)
        }
        var i = heapSize++
        while (i > 0) {
            val p = (i - 1) / 2
            if (heapKeys[p] < key || (heapKeys[p] == key && heap[p] <= node)) break
            heap[i] = heap[p]
            heapKeys[i] = heapKeys[p]
            i = p
        }
        heap[i] = node
        heapKeys[i] = key
    }

    private fun pop(): Int {
        val top = heap[0]
        val node = heap[--heapSize]
        val key = heapKeys[heapSize]
        var i = 0
        while (true) {
            val l = i * 2 + 1
            if (l >= heapSize) break
            val r = l + 1
            val c = if (r < heapSize && (heapKeys[r] < heapKeys[l] || (heapKeys[r] == heapKeys[l] && heap[r] < heap[l]))) r else l
            if (key < heapKeys[c] || (key == heapKeys[c] && node <= heap[c])) break
            heap[i] = heap[c]
            heapKeys[i] = heapKeys[c]
            i = c
        }
        heap[i] = node
        heapKeys[i] = key
        return top
    }

    private fun mixed(i: Int, salt: Int): Int {
        var h = i * 374761393 + salt * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    internal fun writeTo(w: SaveWriter) {
        for (a in arrayOf(volume, lastVolume, jobsLeft, shopsLeft, placed, shipped, commute)) sparse(w, a)
        var n = 0
        for (v in travel) if (v != 0L) n++
        w.count(n)
        for (i in travel.indices) if (travel[i] != 0L) { w.int(i); w.long(travel[i]) }
        w.count(origins.size)
        for (k in origins.indices) { w.int(origins[k]); w.int(workers[k]); w.int(shoppers[k]); w.int(freight[k]) }
        n = freightStuck.count { it }
        w.count(n)
        for (i in freightStuck.indices) if (freightStuck[i]) w.int(i)
        w.int(workersSent); w.int(workersPlaced)
    }

    internal fun readFrom(r: SaveReader) {
        for (a in arrayOf(volume, lastVolume, jobsLeft, shopsLeft, placed, shipped, commute)) sparse(r, a)
        travel.fill(0)
        repeat(r.count()) { travel[tile(r)] = r.long() }
        val n = r.count()
        origins = IntArray(n); workers = IntArray(n); shoppers = IntArray(n); freight = IntArray(n)
        for (k in 0 until n) { origins[k] = tile(r); workers[k] = r.int(); shoppers[k] = r.int(); freight[k] = r.int() }
        freightStuck.fill(false)
        repeat(r.count()) { freightStuck[tile(r)] = true }
        workersSent = r.int(); workersPlaced = r.int()
    }

    /** Since save version 3. */
    internal fun writeRail(w: SaveWriter) {
        for (a in arrayOf(riders, lastRiders, railFreight, lastRailFreight)) sparse(w, a)
        for (m in listOf(journeys, lastJourneys)) {
            w.count(m.size)
            for ((k, v) in m.entries.sortedBy { it.key }) { w.long(k); w.int(v) }
        }
    }

    internal fun readRail(r: SaveReader) {
        for (a in arrayOf(riders, lastRiders, railFreight, lastRailFreight)) sparse(r, a)
        journeys.clear()
        repeat(r.count()) { journeys[r.long()] = r.int() }
        val last = HashMap<Long, Int>()
        repeat(r.count()) { last[r.long()] = r.int() }
        lastJourneys = last
    }

    private fun tile(r: SaveReader): Int = r.int().also { if (it !in 0 until map.size) throw SaveError("traffic off the map") }

    private fun sparse(w: SaveWriter, a: IntArray) {
        w.count(a.count { it != 0 })
        for (i in a.indices) if (a[i] != 0) { w.int(i); w.int(a[i]) }
    }

    private fun sparse(r: SaveReader, a: IntArray) {
        a.fill(0)
        repeat(r.count()) { a[tile(r)] = r.int() }
    }

    companion object {
        /** Trips sent together from one tile at most. */
        const val PART = 100

        /**
         * Whether a trip can go from tile [a] to its neighbour [b], heading [h].
         * Not against a one-way tile's flow, at either end. Not across the middle
         * of a two-carriageway road either, except where a road crosses it or
         * where it ends, so traffic can turn round there.
         */
        fun canMove(map: CityMap, a: Int, b: Int, h: Int): Boolean {
            val back = Heading.opposite(h)
            val ha = map.roadHeading[a].toInt()
            val hb = map.roadHeading[b].toInt()
            if (ha == back || hb == back) return false
            if (ha != 0 && hb != 0 && ha == Heading.opposite(hb) && ha != h && hb != h) {
                // Side by side and running opposite ways: the middle of a boulevard.
                return road(map, a, -h) || road(map, b, h) || !road(map, a, ha) || !road(map, b, Heading.opposite(hb))
            }
            return true
        }

        /** Whether the neighbour of [i] toward [h] is road, or away from it if [h] is negative. */
        private fun road(map: CityMap, i: Int, h: Int): Boolean {
            val k = if (h < 0) Heading.opposite(-h) else h
            val x = i % map.width + Heading.DX[k]
            val y = i / map.width + Heading.DY[k]
            return map.inside(x, y) && map.road[map.index(x, y)] != Road.NONE
        }
    }
}
