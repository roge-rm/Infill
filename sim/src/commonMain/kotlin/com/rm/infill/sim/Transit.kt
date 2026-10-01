package com.rm.infill.sim

/** The stops on a road tile, as bits. */
object Stop {
    const val TRAM = 1
    const val BUS = 2
}

/** How a trip was made, by the fastest thing it used. */
enum class Mode { WALK, CAR, BUS, TROLLEY, TRAM, SUBWAY, TRAIN }

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
internal class TransitNetwork(private val map: CityMap) {
    /** Each tile's tram network, bus network and subway network, -1 for none with a service. */
    val tram = IntArray(map.size) { -1 }
    val bus = IntArray(map.size) { -1 }
    val trolley = IntArray(map.size) { -1 }
    val subway = IntArray(map.size) { -1 }

    /** The wait to board, in seconds, by network. */
    var tramWait = IntArray(0)
        private set
    var busWait = IntArray(0)
        private set
    var trolleyWait = IntArray(0)
        private set
    var subwayWait = IntArray(0)
        private set

    /** Where the subway's stations are: the road tile each is reached from, and the tunnel under it. */
    var stationRoad = IntArray(0)
        private set
    var stationTunnel = IntArray(0)
        private set

    /** Whether anything runs at all, so the trip search can skip what doesn't. */
    val any get() = tramWait.isNotEmpty() || busWait.isNotEmpty() || trolleyWait.isNotEmpty() || subwayWait.isNotEmpty()

    /**
     * Works the networks out again: [depots] and [garages] are the road or
     * track tiles each depot or garage is joined at, [poweredGarages] those of
     * garages with power, for trolleybuses, [stations] the road tile
     * and tunnel tile of each powered subway station. [riders] is last month's
     * riders by network kind and number, for crowding.
     */
    fun update(depots: List<Int>, garages: List<Int>, poweredGarages: List<Int>, stations: List<Pair<Int, Int>>, riders: (Int, Int) -> Int) {
        tramWait = run {
            val (count, tiles, served) = networks(tram, depots) { map.tram[it].toInt() != 0 && !map.out(it, Broken.TRAM) }
            IntArray(count) { wait(Balance.TRAM_WAIT, tiles[it], served[it], Balance.TRACK_PER_DEPOT, riders(Mode.TRAM.ordinal, it), Balance.TRAM_CAPACITY) }
        }
        busWait = run {
            val (count, tiles, served) = networks(bus, garages) { map.road[it] != Road.NONE }
            IntArray(count) { wait(Balance.BUS_WAIT, tiles[it], served[it], Balance.ROAD_PER_GARAGE, riders(Mode.BUS.ordinal, it), Balance.BUS_CAPACITY) }
        }
        trolleyWait = run {
            val (count, tiles, served) = networks(trolley, poweredGarages) { map.wire[it].toInt() != 0 && !map.out(it, Broken.WIRE) }
            IntArray(count) { wait(Balance.BUS_WAIT, tiles[it], served[it], Balance.ROAD_PER_GARAGE, riders(Mode.TROLLEY.ordinal, it), Balance.BUS_CAPACITY) }
        }
        val ends = stations.filter { map.subway[it.second].toInt() != 0 }
        subwayWait = run {
            val (count, tiles, served) = networks(subway, ends.map { it.second }) { map.subway[it].toInt() != 0 && !map.out(it, Broken.SUBWAY) }
            IntArray(count) { wait(Balance.SUBWAY_WAIT, tiles[it], served[it], Balance.TUNNEL_PER_STATION, riders(Mode.SUBWAY.ordinal, it), Balance.SUBWAY_CAPACITY) }
        }
        stationRoad = IntArray(ends.size) { ends[it].first }
        stationTunnel = IntArray(ends.size) { ends[it].second }
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
