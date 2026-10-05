package com.rm.infill.sim

/** The stops on a road tile, as bits. */
object Stop {
    const val TRAM = 1
    const val BUS = 2
}

/** How a trip was made, by the fastest thing it used; a ferry counts over the rest. */
enum class Mode { WALK, CAR, BUS, TROLLEY, TRAM, SUBWAY, TRAIN, BIKE, FERRY }

/**
 * Who rides a bicycle, of those without a car to hand, in percent: plenty
 * in the early years, fewer once cars and buses come, more again from the
 * 1990s.
 */
object Bikes {
    private val years = intArrayOf(1900, 1920, 1940, 1955, 1970, 1990, 2010, 2030)
    private val shares = intArrayOf(20, 30, 35, 18, 8, 10, 18, 25)

    fun share(year: Int): Int {
        if (year <= years.first()) return shares.first()
        for (k in 1 until years.size) {
            if (year <= years[k]) return shares[k - 1] + (shares[k] - shares[k - 1]) * (year - years[k - 1]) / (years[k] - years[k - 1])
        }
        return shares.last()
    }
}

/** Who has a car: next to none in 1905, a fifth of homes by the mid 1920s, most by the 1960s, and the better off sooner. */
object Cars {
    private val years = intArrayOf(1900, 1905, 1915, 1925, 1935, 1945, 1955, 1965, 1980, 2000)
    private val shares = intArrayOf(0, 0, 6, 20, 30, 38, 55, 72, 85, 88)

    /** Percent of a home's workers and shoppers with a car to hand in [year], by [wealth]. */
    fun share(year: Int, wealth: Int): Int {
        var base = shares.last()
        if (year <= years.first()) base = shares.first()
        else for (k in 1 until years.size) {
            if (year <= years[k]) {
                base = shares[k - 1] + (shares[k] - shares[k - 1]) * (year - years[k - 1]) / (years[k] - years[k - 1])
                break
            }
        }
        return when (wealth) {
            Wealth.POOR -> base * 6 / 10
            Wealth.WELL_OFF -> minOf(98, base * 14 / 10)
            else -> base
        }
    }
}

/**
 * How dirty the vehicles on the road are, by year, in percent of a 1920s
 * car: carts raise a little dust and dung; cars get dirtier through the
 * leaded decades to the early 1970s, then cleaner engines come in, and in
 * the end most go electric.
 */
object Fumes {
    private val years = intArrayOf(1900, 1915, 1925, 1950, 1972, 1985, 2000, 2020, 2040)
    private val levels = intArrayOf(20, 30, 100, 120, 120, 60, 40, 25, 10)

    fun level(year: Int): Int {
        if (year <= years.first()) return levels.first()
        for (k in 1 until years.size) {
            if (year <= years[k]) return levels[k - 1] + (levels[k] - levels[k - 1]) * (year - years[k - 1]) / (years[k] - years[k - 1])
        }
        return levels.last()
    }
}

/**
 * Which tram track, roads, overhead wire and tunnels have a service, and how
 * long the wait is. A tram network runs on track joined to a depot with
 * power; buses run on the roads joined to a garage; trolleybuses run on wire
 * that reaches a garage with power; the subway runs on tunnels with powered
 * stations. The more depots or garages for the size of a network, the
 * shorter the wait, and crowding lengthens it again.
 */
/**
 * A planned transit line: buses or trams calling at [stops] in that order
 * and back again, run by [vehicles] of them. A bus line under trolleybus
 * wire the whole way runs trolleybuses.
 */
class TransitLine(val id: Int, val tram: Boolean, val stops: IntArray, var vehicles: Int) {
    fun copy() = TransitLine(id, tram, stops.copyOf(), vehicles)
}

/**
 * How a line is doing, as last worked out: whether it runs, as what, its round trip and wait, the tiles it runs
 * over, and how [full] it was, last month's riders as a share of what its vehicles can carry, in percent.
 */
class LineState(val mode: Mode, val running: Boolean, val roundTrip: Int, val wait: Int, val vehicles: Int, val route: IntArray, val full: Int = 0)

internal class TransitNetwork(private val map: CityMap) {
    /**
     * For each tile, a line of that kind running over it, -1 for none; and
     * for the subway, the network. For drawing and inspect, and for whether
     * a stop has a service.
     */
    val tram = IntArray(map.size) { -1 }
    val bus = IntArray(map.size) { -1 }
    val trolley = IntArray(map.size) { -1 }
    val subway = IntArray(map.size) { -1 }

    /** Which ways the lines of each kind run out of each tile: a bit for each heading. */
    val tramDirs = ByteArray(map.size)
    val busDirs = ByteArray(map.size)
    val trolleyDirs = ByteArray(map.size)

    /** At each stop, the shortest wait for a line of each kind calling there and that line's id, or -1. */
    val tramStop = IntArray(map.size) { -1 }
    val busStop = IntArray(map.size) { -1 }
    val trolleyStop = IntArray(map.size) { -1 }
    val tramStopLine = IntArray(map.size) { -1 }
    val busStopLine = IntArray(map.size) { -1 }
    val trolleyStopLine = IntArray(map.size) { -1 }

    /** The subway's wait to board, by network. */
    var subwayWait = IntArray(0)
        private set

    /** Each line's state, by id. */
    var lines: Map<Int, LineState> = emptyMap()
        private set

    /** Where the subway's stations are: the road tile each is reached from, and the tunnel under it. */
    var stationRoad = IntArray(0)
        private set
    var stationTunnel = IntArray(0)
        private set

    /** Whether anything runs at all, so the trip search can skip what doesn't. */
    var any = false
        private set

    /**
     * Works the lines and networks out again: [lines] as planned, [depots]
     * and [garages] the track or road tiles each depot or garage is joined
     * at, [poweredGarages] those with power for trolleybuses, [stations] the
     * road and tunnel tile of each powered subway station. [riders] is last
     * month's riders by mode and line or network, for crowding.
     */
    fun update(
        lines: List<TransitLine>, depots: List<Int>, garages: List<Int>, poweredGarages: List<Int>, stations: List<Pair<Int, Int>>,
        riders: (Int, Int) -> Int,
    ) {
        // Where a depot or garage can send its vehicles: the track, roads and wire joined to it.
        val tramHome = IntArray(map.size)
        val busHome = IntArray(map.size)
        val wireHome = IntArray(map.size)
        networks(tramHome, depots) { map.tram[it].toInt() != 0 && !map.out(it, Broken.TRAM) }
        networks(busHome, garages) { map.road[it] != Road.NONE }
        networks(wireHome, poweredGarages) { map.wire[it].toInt() != 0 && !map.out(it, Broken.WIRE) }

        for (a in arrayOf(tram, bus, trolley, tramStop, busStop, trolleyStop, tramStopLine, busStopLine, trolleyStopLine)) a.fill(-1)
        for (a in arrayOf(tramDirs, busDirs, trolleyDirs)) a.fill(0)

        // How many each depot and garage can keep, shared out if the lines ask for more.
        val tramRoom = depots.distinct().size * Balance.DEPOT_HOLDS
        val busRoom = garages.distinct().size * Balance.GARAGE_HOLDS
        val tramAsked = lines.filter { it.tram }.sumOf { it.vehicles }
        val busAsked = lines.filter { !it.tram }.sumOf { it.vehicles }

        val states = HashMap<Int, LineState>()
        for (line in lines.sortedBy { it.id }) {
            val stops = line.stops.filter { it in 0 until map.size && map.stop[it].toInt() and (if (line.tram) Stop.TRAM else Stop.BUS) != 0 }
            val route = if (stops.size >= 2) route(stops, line.tram) else null
            val home = if (line.tram) tramHome else busHome
            val wired = !line.tram && route != null && route.all { map.wire[it].toInt() != 0 && !map.out(it, Broken.WIRE) } && wireHome[stops[0]] >= 0
            val mode = if (line.tram) Mode.TRAM else if (wired) Mode.TROLLEY else Mode.BUS
            val room = if (line.tram) tramRoom else busRoom
            val asked = if (line.tram) tramAsked else busAsked
            val vehicles = if (asked <= room) line.vehicles else line.vehicles * room / maxOf(1, asked)
            if (route == null || home[stops[0]] < 0 || vehicles <= 0) {
                states[line.id] = LineState(mode, false, 0, 0, vehicles, route ?: IntArray(0))
                continue
            }
            var trip = 0
            for (t in route) {
                trip += if (line.tram) Balance.TRAM_TIME else (RoadType.of(map.road[t])?.time ?: Balance.WALK_TIME) + Balance.BUS_STOPPING
            }
            val headway = trip / vehicles
            val perVehicle = if (line.tram) Balance.TRAM_VEHICLE_RIDERS else Balance.BUS_VEHICLE_RIDERS
            val crowd = riders(mode.ordinal, line.id) / maxOf(1, vehicles * perVehicle)
            val full = riders(mode.ordinal, line.id) * 100 / maxOf(1, vehicles * perVehicle)
            val wait = maxOf(Balance.SHORTEST_WAIT, headway / 2 * (1 + minOf(crowd, 3)))
            states[line.id] = LineState(mode, true, trip, wait, vehicles, route, full)
            val (onTile, dirs, atStop, stopLine) = when (mode) {
                Mode.TRAM -> Quad(tram, tramDirs, tramStop, tramStopLine)
                Mode.TROLLEY -> Quad(trolley, trolleyDirs, trolleyStop, trolleyStopLine)
                else -> Quad(bus, busDirs, busStop, busStopLine)
            }
            for (k in route.indices) {
                val a = route[k]
                if (onTile[a] < 0) onTile[a] = line.id
                val b = route[(k + 1) % route.size]
                val h = Heading.of(b % map.width - a % map.width, b / map.width - a / map.width).toInt()
                if (h in 1..4) dirs[a] = (dirs[a].toInt() or (1 shl h)).toByte()
            }
            for (s in stops) if (atStop[s] < 0 || wait < atStop[s]) {
                atStop[s] = wait
                stopLine[s] = line.id
            }
        }
        this.lines = states

        val ends = stations.filter { map.subway[it.second].toInt() != 0 }
        subwayWait = run {
            val (count, tiles, served) = networks(subway, ends.map { it.second }) { map.subway[it].toInt() != 0 && !map.out(it, Broken.SUBWAY) }
            IntArray(count) { wait(Balance.SUBWAY_WAIT, tiles[it], served[it], Balance.TUNNEL_PER_STATION, riders(Mode.SUBWAY.ordinal, it), Balance.SUBWAY_CAPACITY) }
        }
        stationRoad = IntArray(ends.size) { ends[it].first }
        stationTunnel = IntArray(ends.size) { ends[it].second }
        any = states.values.any { it.running } || subwayWait.isNotEmpty()
    }

    private data class Quad(val a: IntArray, val b: ByteArray, val c: IntArray, val d: IntArray)

    /**
     * The tiles a line runs over, from its first stop through each in turn to
     * the last and back again, the quickest way along track for trams and
     * along the roads the way they run for buses; null if a leg can't be made.
     */
    fun route(stops: List<Int>, tram: Boolean): IntArray? {
        val out = ArrayList<Int>()
        val order = stops + stops.dropLast(1).reversed()
        for (k in 0 until order.size - 1) {
            val leg = leg(order[k], order[k + 1], tram) ?: return null
            // Each leg starts where the last ended.
            for (j in 0 until leg.size - 1) out += leg[j]
        }
        return out.toIntArray()
    }

    /** The shortest way from tile [a] to [b] for a tram or bus, [a] first and [b] last, or null. */
    private fun leg(a: Int, b: Int, tram: Boolean): IntArray? {
        if (a == b) return intArrayOf(a)
        val from = IntArray(map.size) { -2 }
        val queue = IntArray(map.size)
        var head = 0
        var tail = 0
        queue[tail++] = a
        from[a] = -1
        while (head < tail) {
            val i = queue[head++]
            if (i == b) break
            val x = i % map.width
            val y = i / map.width
            for (h in 1..4) {
                val nx = x + Heading.DX[h]
                val ny = y + Heading.DY[h]
                if (!map.inside(nx, ny)) continue
                val j = map.index(nx, ny)
                if (from[j] != -2) continue
                val ok = if (tram) map.tram[j].toInt() != 0 && !map.out(j, Broken.TRAM)
                else map.road[j] != Road.NONE && !map.closed(j) && Traffic.canMove(map, i, j, h)
                if (!ok) continue
                from[j] = i
                queue[tail++] = j
            }
        }
        if (from[b] == -2) return null
        val path = ArrayList<Int>()
        var i = b
        while (i != -1) {
            path += i
            i = from[i]
        }
        path.reverse()
        return path.toIntArray()
    }

    /** The wait: [base] for a well served network, longer the more [tiles] each of its [served] serves, and longer when [riders] crowd its [capacity]. */
    private fun wait(base: Int, tiles: Int, served: Int, perServer: Int, riders: Int, capacity: Int): Int {
        val stretch = maxOf(1, tiles / maxOf(1, served * perServer))
        val crowd = riders / maxOf(1, served * capacity)
        return base * stretch + base * minOf(crowd, 4)
    }

    /**
     * Numbers the networks of [tiles] that reach one of [seeds], into [into],
     * leaving -1 elsewhere. Returns how many there are, and each one's size and seeds.
     */
    private fun networks(into: IntArray, seeds: List<Int>, tiles: (Int) -> Boolean): Triple<Int, IntArray, IntArray> {
        into.fill(-1)
        val sizes = ArrayList<Int>()
        val served = ArrayList<Int>()
        val queue = IntArray(map.size)
        for (seed in seeds.distinct().sorted()) {
            if (seed !in 0 until map.size || !tiles(seed)) continue
            if (into[seed] >= 0) {
                served[into[seed]]++
                continue
            }
            val n = sizes.size
            var head = 0
            var tail = 0
            queue[tail++] = seed
            into[seed] = n
            while (head < tail) {
                val i = queue[head++]
                val x = i % map.width
                val y = i / map.width
                for (h in 1..4) {
                    val nx = x + Heading.DX[h]
                    val ny = y + Heading.DY[h]
                    if (!map.inside(nx, ny)) continue
                    val j = map.index(nx, ny)
                    if (into[j] >= 0 || !tiles(j)) continue
                    into[j] = n
                    queue[tail++] = j
                }
            }
            sizes += tail
            served += 1
        }
        return Triple(sizes.size, sizes.toIntArray(), served.toIntArray())
    }
}
