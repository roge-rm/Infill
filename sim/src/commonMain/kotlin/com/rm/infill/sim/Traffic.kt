package com.rm.infill.sim

import kotlin.math.max
import kotlin.math.min

/**
 * Trips on the roads and by transit. Each month every home's workers look for
 * the nearest jobs with room, its residents go to the nearest shops, and works
 * send freight to the edge of the map. Trips start and end on road tiles, the
 * one each building is reached from.
 *
 * The search runs over layers of the map: on foot along any road, driving
 * (for those with a car, and freight), and riding the buses, trolleybuses,
 * trams and subway, joined at their stops and stations, where boarding costs a wait.
 * Each trip goes the quickest way. Driving keeps to one-way roads and slows
 * as a road fills; walking and riding don't add to the traffic, buses do a
 * little. Stations on the same railway line are joined by train.
 *
 * The trips are spread over the month, a slice of the starting tiles each
 * day, and big groups go in parts. They add up on every tile they cross, and
 * a road is as slow as the busier of last month and this month so far, so
 * traffic finds its way round busy roads.
 */
internal class Traffic(private val map: CityMap) {
    private val n = map.size

    /** Vehicles across each tile this month so far, and a running average of past months. */
    val volume = IntArray(map.size)
    val lastVolume = IntArray(map.size)

    /** People going by each tile this month so far, on foot, by car or by bus, and last month's. */
    private val footfall = IntArray(map.size)
    val lastFootfall = IntArray(map.size)

    /** Riders on each tile of bus route, tram track and tunnel this month so far, and last month's. */
    private val busVolume = IntArray(map.size)
    val lastBusVolume = IntArray(map.size)
    private val trolleyVolume = IntArray(map.size)
    val lastTrolleyVolume = IntArray(map.size)
    private val tramVolume = IntArray(map.size)
    val lastTramVolume = IntArray(map.size)
    private val subwayVolume = IntArray(map.size)
    val lastSubwayVolume = IntArray(map.size)

    /** Riders getting on or off at each stop or station's road tile, this month and last. */
    private val stopRiders = IntArray(map.size)
    val lastStopRiders = IntArray(map.size)

    /** Workers' trips by [Mode] this month so far, and last month's. */
    private val modes = IntArray(Mode.entries.size)
    val lastModes = IntArray(Mode.entries.size)

    /** Riders boarding each transit network, by mode and network, this month and last. */
    private val networkRiders = HashMap<Int, Int>()
    private var lastNetworkRiders: Map<Int, Int> = emptyMap()

    fun ridersOn(mode: Int, network: Int): Int = lastNetworkRiders[mode * 65536 + network] ?: 0

    /** Last month's riders boarding a bus, trolleybus, tram or subway train, all told. */
    fun boardings(): Int = lastNetworkRiders.values.sum()

    /** Last month's riders on what runs on electricity: trams, trolleybuses and the subway. */
    fun electricRiders(): Int = lastNetworkRiders.entries.sumOf { (k, v) ->
        val mode = k / 65536
        if (mode == Mode.TRAM.ordinal || mode == Mode.TROLLEY.ordinal || mode == Mode.SUBWAY.ordinal) v else 0
    }

    /** This month's travellers by the road tile they start from, in the order they're sent, those with cars as well as all. */
    private var origins = IntArray(0)
    private var workers = IntArray(0)
    private var carWorkers = IntArray(0)
    private var shoppers = IntArray(0)
    private var carShoppers = IntArray(0)
    private var freight = IntArray(0)

    /** Room left for workers and shoppers at each road tile. */
    private val jobsLeft = IntArray(map.size)
    private val shopsLeft = IntArray(map.size)

    /** This month so far, by starting tile: workers placed, their seconds of travel, freight that got out. */
    private val placed = IntArray(map.size)
    private val travel = LongArray(map.size)
    private val shipped = IntArray(map.size)

    /**
     * Goods: loads of each [Good] to send from each starting tile this month,
     * room for them at each road tile, and how much room is left in all.
     */
    private var goods = Array(Good.COUNT) { IntArray(0) }
    private val wanted = Array(Good.COUNT) { IntArray(map.size) }
    private val wantedLeft = IntArray(Good.COUNT)

    /** This month so far and last month: loads delivered at each road tile, and by starting tile, sold in town and sent out of it. */
    private val delivered = Array(Good.COUNT) { IntArray(map.size) }
    val lastDelivered = Array(Good.COUNT) { IntArray(map.size) }
    private val sold = Array(Good.COUNT) { IntArray(map.size) }
    val lastSold = Array(Good.COUNT) { IntArray(map.size) }
    private val exported = Array(Good.COUNT) { IntArray(map.size) }
    val lastExported = Array(Good.COUNT) { IntArray(map.size) }

    /** Last month's room for goods that nobody in town filled, by road tile: what had to be brought in. */
    val lastUnmet = Array(Good.COUNT) { IntArray(map.size) }

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
     * How freely the traffic moves: this month so far, the seconds vehicles
     * would have taken on clear roads with nothing to stop for, and the
     * seconds they took. Last month's, in percent: 100 is free flowing.
     */
    private var freeSeconds = 0L
    private var tookSeconds = 0L
    var lastFlow = 100
        internal set

    /**
     * Last month's vehicle trips, for following them on the map: for each,
     * where it started, where it ended and how many went, and the road tiles
     * it took, from [tripRoute] to the next one's start.
     */
    private var routes = IntList()
    private var lastRoutes = IntList()

    /** Set by the city from its districts: stops boarded free, and streets heavy trucks are kept off. */
    var freeStop = BooleanArray(map.size)
    var noTrucks = BooleanArray(map.size)

    /** Boardings this month and last at stops boarded free, which bring in no fares. */
    private var freeBoardings = 0
    var lastFreeBoardings = 0
        private set

    /** Whether the search now is a truck's, which keeps off [noTrucks] streets where it can. */
    private var truck = false

    /** Snowed in by a blizzard: nothing on the roads moves but people on foot, and the subway and trains. */
    var snowedIn = false

    /** The buses, trams and subway, set by the city whenever they change. */
    var transit: TransitNetwork? = null
        private set

    /** For each subway tunnel tile with a station over it, the station's road tile; -1 elsewhere. */
    private val stationAbove = IntArray(map.size) { -1 }

    /** For each road tile that's a subway station's, the tunnel tile under it; -1 elsewhere. */
    private val tunnelBelow = IntArray(map.size) { -1 }

    /** Takes the stations from [transit], after it's been worked out again. */
    fun useTransit(net: TransitNetwork) {
        transit = net
        stationAbove.fill(-1)
        tunnelBelow.fill(-1)
        for (k in net.stationRoad.indices) {
            val road = net.stationRoad[k]
            val tunnel = net.stationTunnel[k]
            if (road < 0 || tunnel < 0) continue
            if (stationAbove[tunnel] < 0) stationAbove[tunnel] = road
            if (tunnelBelow[road] < 0) tunnelBelow[road] = tunnel
        }
    }

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

    // Search state, reused, over every tile in every layer: a state is the layer times the map's size, plus the tile.
    // A state's distance counts only if its stamp is this search's.
    private val dist = IntArray(LAYERS * map.size)
    private val from = IntArray(LAYERS * map.size)

    /** The rail stops a state was reached between by train, or -1 if it wasn't. */
    private val boarded = IntArray(LAYERS * map.size)
    private val alighted = IntArray(LAYERS * map.size)
    private val stamp = IntArray(LAYERS * map.size)
    private var search = 0
    private var heap = IntArray(256)
    private var heapKeys = IntArray(256)
    private var heapSize = 0

    /**
     * Ends one month and starts the next. Last month's trips become the
     * congestion layer and the results above; then this month's travellers
     * and places to go are taken from the arrays, each by road tile, with how
     * many of the workers and shoppers have a car. [salt] shuffles who goes
     * first, so no corner of the map always does. Goods, by kind and road
     * tile: [goodsAt] to send, and [wantedAt] room for them.
     */
    fun newMonth(
        workersAt: IntArray, shoppersAt: IntArray, freightAt: IntArray, jobsAt: IntArray, shopsAt: IntArray, salt: Int,
        carWorkersAt: IntArray = IntArray(n), carShoppersAt: IntArray = IntArray(n),
        goodsAt: Array<IntArray>? = null, wantedAt: Array<IntArray>? = null,
    ) {
        for (i in 0 until map.size) lastVolume[i] = (lastVolume[i] + volume[i] + 1) / 2
        volume.fill(0)
        for (i in 0 until map.size) {
            val road = RoadType.of(map.road[i])
            map.congestion[i] = if (road == null) 0 else min(255, (lastVolume[i] + (busVolume[i] + trolleyVolume[i]) / BUS_RIDERS) * 128 / capacity(i, road)).toByte()
        }
        footfall.copyInto(lastFootfall)
        footfall.fill(0)
        for ((now, last) in listOf(busVolume to lastBusVolume, trolleyVolume to lastTrolleyVolume, tramVolume to lastTramVolume, subwayVolume to lastSubwayVolume, stopRiders to lastStopRiders)) {
            now.copyInto(last)
            now.fill(0)
        }
        modes.copyInto(lastModes)
        modes.fill(0)
        lastFreeBoardings = freeBoardings
        freeBoardings = 0
        lastFlow = if (tookSeconds <= 0) 100 else (freeSeconds * 100 / tookSeconds).toInt().coerceIn(0, 100)
        freeSeconds = 0
        tookSeconds = 0
        val swap = lastRoutes
        lastRoutes = routes
        routes = swap
        routes.size = 0
        lastNetworkRiders = HashMap(networkRiders)
        networkRiders.clear()

        var sent = 0
        var got = 0
        commute.fill(0)
        freightStuck.fill(false)
        for (k in origins.indices) {
            val o = origins[k]
            sent += workers[k]
            got += placed[o]
            if (workers[k] > 0) commute[o] = if (placed[o] == 0) -1 else (travel[o] / placed[o]).toInt()
            var loads = freight[k]
            for (g in goods) if (k < g.size) loads += g[k]
            freightStuck[o] = loads > 0 && shipped[o] * 2 < loads
        }
        for (g in 0 until Good.COUNT) {
            delivered[g].copyInto(lastDelivered[g])
            delivered[g].fill(0)
            sold[g].copyInto(lastSold[g])
            sold[g].fill(0)
            exported[g].copyInto(lastExported[g])
            exported[g].fill(0)
            wanted[g].copyInto(lastUnmet[g])
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
        for (i in 0 until map.size) {
            if (workersAt[i] > 0 || shoppersAt[i] > 0 || freightAt[i] > 0 || goodsAt?.any { it[i] > 0 } == true) starts += i
        }
        starts.sortBy { mixed(it, salt) }
        origins = starts.toIntArray()
        workers = IntArray(origins.size) { workersAt[origins[it]] }
        carWorkers = IntArray(origins.size) { min(workersAt[origins[it]], carWorkersAt[origins[it]]) }
        shoppers = IntArray(origins.size) { shoppersAt[origins[it]] }
        carShoppers = IntArray(origins.size) { min(shoppersAt[origins[it]], carShoppersAt[origins[it]]) }
        freight = IntArray(origins.size) { freightAt[origins[it]] }
        for (g in 0 until Good.COUNT) {
            goods[g] = IntArray(origins.size) { goodsAt?.get(g)?.get(origins[it]) ?: 0 }
            if (wantedAt != null) wantedAt[g].copyInto(wanted[g]) else wanted[g].fill(0)
            wantedLeft[g] = wanted[g].sum()
        }
    }

    /** Sends the travellers in slice [day] of [days], so the whole month's have gone by its end. */
    fun sendDay(day: Int, days: Int) {
        val count = origins.size
        for (k in count * (day - 1) / days until count * day / days) send(k)
    }

    /**
     * One tile's trips, in parts of no more than [PART] so the later parts see
     * the roads the first ones filled. Those with cars and those without go
     * separately, since they have different ways to get there.
     */
    private fun send(k: Int) {
        var loads = 0
        for (g in goods) loads += g[k]
        val total = workers[k] + shoppers[k] + freight[k] + loads
        val parts = (total + PART - 1) / PART
        fun share(v: Int, p: Int) = v * (p + 1) / parts - v * p / parts
        val cargo = IntArray(Good.COUNT)
        for (p in 0 until parts) {
            val wCar = share(carWorkers[k], p)
            val sCar = share(carShoppers[k], p)
            val w = share(workers[k] - carWorkers[k], p)
            val s = share(shoppers[k] - carShoppers[k], p)
            val f = share(freight[k], p)
            var c = 0
            for (g in 0 until Good.COUNT) {
                cargo[g] = share(goods[g][k], p)
                c += cargo[g]
            }
            if (w + s > 0) send(origins[k], w, s, 0, car = false, null)
            if (wCar + sCar > 0) send(origins[k], wCar, sCar, 0, car = true, null)
            // Freight goes by truck, on its own way round.
            if (f + c > 0) {
                truck = true
                send(origins[k], 0, 0, f, car = true, if (c > 0) cargo else null)
                truck = false
            }
        }
    }

    private fun state(layer: Int, tile: Int) = layer * n + tile

    /** Reaches state [st] at [nd] from [at] if that's sooner than any way found yet. */
    private fun reach(st: Int, nd: Int, at: Int, train: Int = -1, off: Int = -1) {
        if (stamp[st] == search && nd >= dist[st]) return
        stamp[st] = search
        dist[st] = nd
        from[st] = at
        boarded[st] = train
        alighted[st] = off
        push(st, nd)
    }

    /**
     * A single search outward from [start], taking the nearest room first.
     * With a [car], the travellers can drive as well as walk and ride, and
     * freight goes along; without, they walk and ride. Goods in [cargo] go to
     * the nearest buyers with room for them, or out of town if none is near.
     */
    private fun send(start: Int, workers: Int, shoppers: Int, freight: Int, car: Boolean, cargo: IntArray?) {
        var w = workers
        var s = shoppers
        var f = freight
        var c = cargo?.sum() ?: 0
        // Where the goods could leave town, and how far that was.
        var out = -1
        var outAt = 0
        val net = transit?.takeIf { it.any }
        search++
        heapSize = 0
        if (w + s > 0) reach(state(WALK, start), 0, -1)
        if (car && !snowedIn) reach(state(CAR, start), 0, -1)
        while (heapSize > 0 && (w > 0 || s > 0 || f > 0 || c > 0)) {
            val d = heapKeys[0]
            val st = pop()
            if (d > dist[st]) continue
            if (d > Balance.LONGEST_TRIP) {
                // Too far to go every day; freight goes further.
                w = 0
                s = 0
                if (d > Balance.LONGEST_FREIGHT) break
            }
            if (c > 0 && out >= 0 && d > outAt + Balance.EXPORT_DETOUR) {
                // No buyer near enough: what's left goes out of town.
                ship(start, out, cargo!!)
                c = 0
            }
            val layer = st / n
            val a = st % n
            // People get where they're going on foot or by car; the transit layers only carry them between stops.
            if (layer == WALK || layer == CAR) {
                if (w > 0 && jobsLeft[a] > 0) {
                    val t = min(w, jobsLeft[a])
                    jobsLeft[a] -= t
                    w -= t
                    placed[start] += t
                    travel[start] += t.toLong() * d
                    carry(st, t, WORKER)
                }
                if (s > 0 && shopsLeft[a] > 0) {
                    val t = min(s, shopsLeft[a])
                    shopsLeft[a] -= t
                    s -= t
                    carry(st, t, SHOPPER)
                }
            }
            if (layer == CAR && f > 0 && (edge(a) || outlet[a] >= 0)) {
                shipped[start] += f
                carry(st, f, FREIGHT)
                if (!edge(a)) outByRail(a, f)
                f = 0
            }
            if (layer == CAR && c > 0) {
                for (g in 0 until Good.COUNT) {
                    val room = wanted[g][a]
                    if (cargo!![g] == 0 || room == 0) continue
                    val t = min(cargo[g], room)
                    wanted[g][a] -= t
                    wantedLeft[g] -= t
                    cargo[g] -= t
                    c -= t
                    delivered[g][a] += t
                    sold[g][start] += t
                    shipped[start] += t
                    carry(st, t, FREIGHT)
                }
                if (c > 0 && out < 0 && (edge(a) || outlet[a] >= 0)) {
                    out = st
                    outAt = d
                }
                // Goods nobody in town has room for go straight out.
                if (out >= 0) for (g in 0 until Good.COUNT) {
                    if (cargo!![g] == 0 || wantedLeft[g] > 0) continue
                    shipOne(start, out, g, cargo[g])
                    c -= cargo[g]
                    cargo[g] = 0
                }
            }
            when (layer) {
                WALK -> walkFrom(a, d, st, net)
                CAR -> driveFrom(a, d, st)
                BUS -> busFrom(a, d, st, net!!)
                TROLLEY -> trolleyFrom(a, d, st, net!!)
                TRAM -> tramFrom(a, d, st, net!!)
                SUBWAY -> subwayFrom(a, d, st, net!!)
            }
        }
        // The search ran out before the goods did: out of town if there's a way, else they stay where they are.
        if (c > 0 && out >= 0) ship(start, out, cargo!!)
    }

    /** Sends what's left in [cargo] out of town by the way to [out], the edge or a freight yard, once the search is done. */
    private fun ship(start: Int, out: Int, cargo: IntArray) {
        for (g in 0 until Good.COUNT) if (cargo[g] > 0) {
            shipOne(start, out, g, cargo[g])
            cargo[g] = 0
        }
    }

    private fun shipOne(start: Int, out: Int, g: Int, loads: Int) {
        exported[g][start] += loads
        shipped[start] += loads
        carry(out, loads, FREIGHT)
        val a = out % n
        if (!edge(a)) outByRail(a, loads)
    }

    /** Freight leaving town by train from the yard reached from road tile [a]. */
    private fun outByRail(a: Int, loads: Int) {
        railFreight[a] += loads
        val key = (stopTrack[outlet[a]].toLong() shl 32) or 0xffffffffL
        journeys[key] = (journeys[key] ?: 0) + loads
    }

    /** On foot: along any road either way, onto a train, a tram, a bus or the subway. */
    private fun walkFrom(a: Int, d: Int, st: Int, net: TransitNetwork?) {
        val x = a % map.width
        val y = a / map.width
        for (h in 1..4) {
            val nx = x + Heading.DX[h]
            val ny = y + Heading.DY[h]
            if (!map.inside(nx, ny)) continue
            val b = ny * map.width + nx
            if (map.road[b] == Road.NONE) continue
            // Deep floodwater stops walkers too; a dug-up street doesn't.
            if ((map.flood[b].toInt() and 0xff) >= Balance.FLOOD_DAMAGE) continue
            reach(state(WALK, b), d + walkTime(b), st)
        }
        // By train to the other stations on the line.
        val here = stopHere[a]
        if (here >= 0) {
            val times = stopTimes[here]
            for (k in times.indices) {
                val b = stopNode[k]
                if (times[k] < 0 || k == here || b < 0 || b == a) continue
                reach(state(WALK, b), d + Balance.RAIL_WAIT + times[k], st, here, k)
            }
        }
        if (net == null) return
        val tunnel = tunnelBelow[a]
        if (tunnel >= 0 && net.subway[tunnel] >= 0) reach(state(SUBWAY, tunnel), d + net.subwayWait[net.subway[tunnel]], st)
        // In a blizzard nothing runs on the roads.
        if (snowedIn) return
        // On at a stop a line calls at, after the wait for its next one; a free ride is worth some of the wait.
        val free = if (freeStop[a]) Balance.FREE_FARE_PULL else 0
        if (net.tramStop[a] >= 0) reach(state(TRAM, a), d + max(0, net.tramStop[a] - free), st)
        if (net.trolleyStop[a] >= 0) reach(state(TROLLEY, a), d + max(0, net.trolleyStop[a] - free), st)
        if (net.busStop[a] >= 0) reach(state(BUS, a), d + max(0, net.busStop[a] - free), st)
    }

    /** Driving: along the roads the way they run, slowed by traffic. */
    private fun driveFrom(a: Int, d: Int, st: Int) {
        val x = a % map.width
        val y = a / map.width
        for (h in 1..4) {
            val nx = x + Heading.DX[h]
            val ny = y + Heading.DY[h]
            if (!map.inside(nx, ny)) continue
            val b = ny * map.width + nx
            val road = RoadType.of(map.road[b]) ?: continue
            // Deep floodwater closes the road.
            if ((map.flood[b].toInt() and 0xff) >= Balance.FLOOD_DAMAGE) continue
            // As does digging it up.
            if (map.closed(b)) continue
            if (!canMove(map, a, b, h)) continue
            // A truck keeps off streets it's banned from unless there's no other way.
            val time = timeToCross(b, road)
            reach(state(CAR, b), d + if (truck && noTrucks[b]) time * Balance.TRUCK_BAN_SLOW else time, st)
        }
    }

    /** On a bus: along its line with the traffic, or past it in a bus lane, a stop's time added; off again at a stop it calls at. */
    private fun busFrom(a: Int, d: Int, st: Int, net: TransitNetwork) {
        if (net.busStop[a] >= 0) reach(state(WALK, a), d, st)
        val x = a % map.width
        val y = a / map.width
        for (h in 1..4) {
            val nx = x + Heading.DX[h]
            val ny = y + Heading.DY[h]
            if (!map.inside(nx, ny)) continue
            val b = ny * map.width + nx
            if (net.busDirs[a].toInt() and (1 shl h) == 0) continue
            val road = RoadType.of(map.road[b]) ?: continue
            if ((map.flood[b].toInt() and 0xff) >= Balance.FLOOD_DAMAGE || map.closed(b)) continue
            reach(state(BUS, b), d + busTime(b, road) + Balance.BUS_STOPPING, st)
        }
    }

    /** On a trolleybus: along its line under the wire with the traffic, or past it in a bus lane; off again at a stop it calls at. */
    private fun trolleyFrom(a: Int, d: Int, st: Int, net: TransitNetwork) {
        if (net.trolleyStop[a] >= 0) reach(state(WALK, a), d, st)
        val x = a % map.width
        val y = a / map.width
        for (h in 1..4) {
            val nx = x + Heading.DX[h]
            val ny = y + Heading.DY[h]
            if (!map.inside(nx, ny)) continue
            val b = ny * map.width + nx
            if (net.trolleyDirs[a].toInt() and (1 shl h) == 0) continue
            val road = RoadType.of(map.road[b]) ?: continue
            if ((map.flood[b].toInt() and 0xff) >= Balance.FLOOD_DAMAGE || map.closed(b)) continue
            reach(state(TROLLEY, b), d + busTime(b, road) + Balance.BUS_STOPPING, st)
        }
    }

    /** On a tram: along its line, held up by busy streets unless it has a lane of its own; off again at a stop it calls at. */
    private fun tramFrom(a: Int, d: Int, st: Int, net: TransitNetwork) {
        if (net.tramStop[a] >= 0) reach(state(WALK, a), d, st)
        val x = a % map.width
        val y = a / map.width
        for (h in 1..4) {
            val nx = x + Heading.DX[h]
            val ny = y + Heading.DY[h]
            if (!map.inside(nx, ny)) continue
            val b = ny * map.width + nx
            if (net.tramDirs[a].toInt() and (1 shl h) == 0) continue
            if ((map.flood[b].toInt() and 0xff) >= Balance.FLOOD_DAMAGE || map.closed(b)) continue
            val lane = map.lane[b].toInt() != 0
            val busy = if (lane) 0 else (map.congestion[b].toInt() and 0xff) * Balance.TRAM_TIME / 512
            // Trams in the street wait at the crossings with the rest, but for a moment where they have a lane.
            val wait = RoadType.of(map.road[b])?.let { if (lane) minOf(junctionWait(b, it), Balance.LANE_JUNCTION) else junctionWait(b, it) } ?: 0
            reach(state(TRAM, b), d + Balance.TRAM_TIME + busy + wait, st)
        }
    }

    /** On the subway: along its tunnels, quick and clear of everything; off again at a station. */
    private fun subwayFrom(a: Int, d: Int, st: Int, net: TransitNetwork) {
        val above = stationAbove[a]
        if (above >= 0) reach(state(WALK, above), d, st)
        val x = a % map.width
        val y = a / map.width
        for (h in 1..4) {
            val nx = x + Heading.DX[h]
            val ny = y + Heading.DY[h]
            if (!map.inside(nx, ny)) continue
            val b = ny * map.width + nx
            if (net.subway[b] != net.subway[a]) continue
            reach(state(SUBWAY, b), d + Balance.SUBWAY_TIME, st)
        }
    }

    /**
     * Adds [trips] to every tile on the way back from state [end] to where the
     * search began: vehicles to the traffic, people to the footfall, riders to
     * the buses, trams, subway and trains they took. Workers' trips are
     * counted by the fastest thing they used.
     */
    private fun carry(end: Int, trips: Int, kind: Int) {
        var st = end
        var mode = Mode.WALK.ordinal
        // The road tiles a vehicle took, for following the trip on the map.
        val head = routes.size
        routes.add(0); routes.add(end % n); routes.add(trips); routes.add(0)
        while (st >= 0) {
            val layer = st / n
            val at = st % n
            if (layer == CAR) {
                routes.add(at)
                val p = from[st]
                if (p >= 0 && p / n == CAR) {
                    RoadType.of(map.road[at])?.let { road ->
                        freeSeconds += road.time.toLong() * trips
                        tookSeconds += (dist[st] - dist[p]).toLong() * trips
                    }
                }
            }
            if (from[st] < 0) routes[head] = at
            when (layer) {
                WALK -> footfall[at] += trips
                CAR -> {
                    footfall[at] += trips
                    volume[at] += trips
                    mode = max(mode, Mode.CAR.ordinal)
                }
                BUS -> {
                    footfall[at] += trips
                    busVolume[at] += trips
                    mode = max(mode, Mode.BUS.ordinal)
                }
                TROLLEY -> {
                    footfall[at] += trips
                    trolleyVolume[at] += trips
                    mode = max(mode, Mode.TROLLEY.ordinal)
                }
                TRAM -> {
                    tramVolume[at] += trips
                    mode = max(mode, Mode.TRAM.ordinal)
                }
                SUBWAY -> {
                    subwayVolume[at] += trips
                    mode = max(mode, Mode.SUBWAY.ordinal)
                }
            }
            val prev = from[st]
            // Getting on or off: counted at the stop's road tile, and boarding against the network.
            if (prev >= 0) {
                val pl = prev / n
                if (pl != layer && (pl == WALK || layer == WALK)) {
                    val rideLayer = if (pl == WALK) layer else pl
                    val rideTile = if (pl == WALK) at else prev % n
                    val road = if (rideLayer == SUBWAY) stationAbove[rideTile] else rideTile
                    if (road >= 0) stopRiders[road] += trips
                    if (pl == WALK) transit?.let { net ->
                        // Counted against the line they boarded, or the subway network.
                        val network = when (rideLayer) {
                            BUS -> net.busStopLine[rideTile]
                            TROLLEY -> net.trolleyStopLine[rideTile]
                            TRAM -> net.tramStopLine[rideTile]
                            else -> net.subway[rideTile]
                        }
                        val m = when (rideLayer) {
                            BUS -> Mode.BUS
                            TROLLEY -> Mode.TROLLEY
                            TRAM -> Mode.TRAM
                            else -> Mode.SUBWAY
                        }
                        if (rideLayer != SUBWAY && freeStop[rideTile]) freeBoardings += trips
                        if (network >= 0) {
                            val key = m.ordinal * 65536 + network
                            networkRiders[key] = (networkRiders[key] ?: 0) + trips
                        }
                    }
                }
            }
            val on = boarded[st]
            if (on >= 0) {
                riders[at] += trips
                riders[stopNode[on]] += trips
                val key = (stopTrack[on].toLong() shl 32) or (stopTrack[alighted[st]].toLong() and 0xffffffffL)
                journeys[key] = (journeys[key] ?: 0) + trips
                mode = Mode.TRAIN.ordinal
            }
            st = prev
        }
        if (kind == WORKER) modes[mode] += trips
        // Only trips that drove are kept.
        val tiles = routes.size - head - 4
        if (tiles == 0) routes.size = head else routes[head + 3] = tiles
    }

    /** Seconds to walk across a tile, more over a level crossing or through floodwater. */
    private fun walkTime(b: Int): Int {
        val time = Balance.WALK_TIME + if (map.rail[b] != Rail.NONE) Balance.CROSSING_DELAY else 0
        return if ((map.flood[b].toInt() and 0xff) >= Balance.FLOODED) time * Balance.FLOOD_SLOW else time
    }

    /** Seconds for a bus to cross a tile: past the traffic in a lane of its own, with a moment at a crossing, or with the rest. */
    private fun busTime(b: Int, road: RoadType): Int {
        if (map.lane[b].toInt() == 0) return timeToCross(b, road)
        return road.time + minOf(junctionWait(b, road), Balance.LANE_JUNCTION)
    }

    /** How many vehicles a month a road tile takes before it fills: less where a lane's given over to buses and trams. */
    fun capacity(b: Int, road: RoadType): Int = if (map.lane[b].toInt() != 0) road.capacity * Balance.LANE_CAR_SHARE / 100 else road.capacity

    /** Seconds to cross a tile: its road's time when clear, half as long again at capacity, up to three times, and slower still flooded. */
    private fun timeToCross(b: Int, road: RoadType): Int {
        val buses = max(lastBusVolume[b], busVolume[b]) + max(lastTrolleyVolume[b], trolleyVolume[b])
        val load = (max(lastVolume[b], volume[b]) + buses / BUS_RIDERS) * 32 / capacity(b, road)
        val slow = min(2 * 1024, load * load / 2)
        val time = road.time + road.time * slow / 1024 + (if (map.rail[b] != Rail.NONE) Balance.CROSSING_DELAY else 0) + junctionWait(b, road)
        // Wading through floodwater, or picking a way round the potholes.
        val wading = if ((map.flood[b].toInt() and 0xff) >= Balance.FLOODED) time * Balance.FLOOD_SLOW else time
        return if (map.potholed(b)) wading * Balance.POTHOLE_SLOW else wading
    }

    /**
     * Last month's vehicles through road tile [through], by the tiles they
     * crossed on the way: where they came from, where they went and the roads
     * between, so the map can show where a road's traffic is going.
     */
    fun tripsThrough(through: Int): IntArray {
        val out = IntArray(n)
        val r = lastRoutes
        var k = 0
        while (k < r.size) {
            val trips = r[k + 2]
            val tiles = r[k + 3]
            var hit = false
            for (j in 0 until tiles) if (r[k + 4 + j] == through) { hit = true; break }
            if (hit) for (j in 0 until tiles) out[r[k + 4 + j]] += trips
            k += 4 + tiles
        }
        return out
    }

    /**
     * Seconds by road from road tile [start] to every other, driving as the
     * traffic is now, or -1 where it can't be reached within a long trip.
     */
    fun travelTimes(start: Int): IntArray {
        val t = IntArray(n) { -1 }
        if (map.road[start] == Road.NONE) return t
        val heap = LongHeap()
        t[start] = 0
        heap.push(0L shl 32 or start.toLong())
        while (heap.size > 0) {
            val top = heap.pop()
            val d = (top ushr 32).toInt()
            val a = (top and 0xffffffffL).toInt()
            if (d > t[a]) continue
            val x = a % map.width
            val y = a / map.width
            for (h in 1..4) {
                val nx = x + Heading.DX[h]
                val ny = y + Heading.DY[h]
                if (!map.inside(nx, ny)) continue
                val b = ny * map.width + nx
                val road = RoadType.of(map.road[b]) ?: continue
                if (map.closed(b) || !canMove(map, a, b, h)) continue
                val nd = d + timeToCross(b, road)
                if (nd > Balance.LONGEST_TRIP || (t[b] in 0..nd)) continue
                t[b] = nd
                heap.push(nd.toLong() shl 32 or b.toLong())
            }
        }
        return t
    }

    /** Seconds to get through the crossing at [b], if it is one, by its control and how busy it is. */
    fun junctionWait(b: Int, road: RoadType): Int {
        val control = map.control[b]
        if (control == Junction.NONE) return 0
        return Junction.wait(control, road.capacity, max(lastVolume[b], volume[b]))
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
        carWorkers = IntArray(n); carShoppers = IntArray(n)
        for (k in 0 until n) { origins[k] = tile(r); workers[k] = r.int(); shoppers[k] = r.int(); freight[k] = r.int() }
        // No goods before version 10; [readGoods] fills them in after.
        goods = Array(Good.COUNT) { IntArray(n) }
        freightStuck.fill(false)
        repeat(r.count()) { freightStuck[tile(r)] = true }
        workersSent = r.int(); workersPlaced = r.int()
    }

    /** Since save version 13. */
    internal fun writeFlow(w: SaveWriter) {
        w.long(freeSeconds); w.long(tookSeconds); w.int(lastFlow)
    }

    internal fun readFlow(r: SaveReader) {
        freeSeconds = r.long(); tookSeconds = r.long(); lastFlow = r.int()
    }

    /** Since save version 10. */
    internal fun writeGoods(w: SaveWriter) {
        w.count(origins.size)
        for (g in 0 until Good.COUNT) {
            for (k in origins.indices) w.int(goods[g][k])
            for (a in arrayOf(wanted[g], delivered[g], lastDelivered[g], sold[g], lastSold[g], exported[g], lastExported[g], lastUnmet[g])) sparse(w, a)
        }
    }

    internal fun readGoods(r: SaveReader) {
        if (r.count() != origins.size) throw SaveError("the freight doesn't add up")
        for (g in 0 until Good.COUNT) {
            goods[g] = IntArray(origins.size) { r.int() }
            for (a in arrayOf(wanted[g], delivered[g], lastDelivered[g], sold[g], lastSold[g], exported[g], lastExported[g], lastUnmet[g])) sparse(r, a)
            wantedLeft[g] = wanted[g].sum()
        }
    }

    /** Since save version 8. */
    internal fun writeTransit(w: SaveWriter) {
        for (a in arrayOf(footfall, lastFootfall, busVolume, lastBusVolume, trolleyVolume, lastTrolleyVolume, tramVolume, lastTramVolume, subwayVolume, lastSubwayVolume, stopRiders, lastStopRiders)) sparse(w, a)
        for (a in arrayOf(modes, lastModes)) for (v in a) w.int(v)
        for (m in listOf(networkRiders, lastNetworkRiders)) {
            w.count(m.size)
            for ((k, v) in m.entries.sortedBy { it.key }) { w.int(k); w.int(v) }
        }
        w.count(origins.size)
        for (k in origins.indices) { w.int(carWorkers[k]); w.int(carShoppers[k]) }
    }

    internal fun readTransit(r: SaveReader) {
        for (a in arrayOf(footfall, lastFootfall, busVolume, lastBusVolume, trolleyVolume, lastTrolleyVolume, tramVolume, lastTramVolume, subwayVolume, lastSubwayVolume, stopRiders, lastStopRiders)) sparse(r, a)
        for (a in arrayOf(modes, lastModes)) for (k in a.indices) a[k] = r.int()
        networkRiders.clear()
        repeat(r.count()) { networkRiders[r.int()] = r.int() }
        val last = HashMap<Int, Int>()
        repeat(r.count()) { last[r.int()] = r.int() }
        lastNetworkRiders = last
        if (r.count() != origins.size) throw SaveError("the travellers don't add up")
        for (k in origins.indices) { carWorkers[k] = r.int(); carShoppers[k] = r.int() }
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

        // The layers of the trip search.
        const val WALK = 0
        const val CAR = 1
        const val BUS = 2
        const val TRAM = 3
        const val SUBWAY = 4
        const val TROLLEY = 5
        const val LAYERS = 6

        // What a trip's for.
        private const val WORKER = 0
        private const val SHOPPER = 1
        private const val FREIGHT = 2

        /** Riders a bus carries, for what buses add to the traffic. */
        const val BUS_RIDERS = 20

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

/** A growing list of ints, without boxing. */
internal class IntList {
    private var a = IntArray(1024)
    var size = 0

    fun add(v: Int) {
        if (size == a.size) a = a.copyOf(a.size * 2)
        a[size++] = v
    }

    operator fun get(i: Int) = a[i]

    operator fun set(i: Int, v: Int) {
        a[i] = v
    }
}

/** A binary heap of longs, smallest first. */
internal class LongHeap {
    private var a = LongArray(256)
    var size = 0

    fun push(v: Long) {
        if (size == a.size) a = a.copyOf(a.size * 2)
        var i = size++
        while (i > 0) {
            val p = (i - 1) / 2
            if (a[p] <= v) break
            a[i] = a[p]
            i = p
        }
        a[i] = v
    }

    fun pop(): Long {
        val top = a[0]
        val v = a[--size]
        var i = 0
        while (true) {
            val l = i * 2 + 1
            if (l >= size) break
            val c = if (l + 1 < size && a[l + 1] < a[l]) l + 1 else l
            if (v <= a[c]) break
            a[i] = a[c]
            i = c
        }
        a[i] = v
        return top
    }
}
