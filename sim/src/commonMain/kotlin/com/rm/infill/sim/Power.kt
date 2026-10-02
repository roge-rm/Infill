package com.rm.infill.sim

import kotlin.math.min

/** The power on a tile, if any: an ordinary line, or a high-voltage one (from the 1920s). */
object Power {
    const val NONE: Byte = 0
    const val LINE: Byte = 1
    const val HIGH: Byte = 2
}

/** What each kind of power station makes at most, in watts, and the smoke it gives off at full output. */
object Generation {
    fun capacity(t: BuildingType): Int = when (t) {
        BuildingType.COAL_PLANT -> 10_000_000
        BuildingType.OIL_PLANT -> 20_000_000
        BuildingType.GAS_PLANT -> 50_000_000
        BuildingType.HYDRO_PLANT -> 12_000_000
        BuildingType.NUCLEAR_PLANT -> 300_000_000
        BuildingType.WIND_FARM -> 12_000_000
        BuildingType.SOLAR_FARM -> 15_000_000
        BuildingType.BATTERY -> 25_000_000
        BuildingType.RIVER_TURBINE -> 3_000_000
        BuildingType.TIDAL_TURBINE -> 10_000_000
        BuildingType.OFFSHORE_WIND -> 20_000_000
        else -> 0
    }

    /** Makes its power from the weather, and costs nothing to run. */
    fun renewable(t: BuildingType): Boolean =
        t == BuildingType.WIND_FARM || t == BuildingType.SOLAR_FARM || t == BuildingType.RIVER_TURBINE || t == BuildingType.TIDAL_TURBINE || t == BuildingType.OFFSHORE_WIND

    /** The wind out on open water: steadier and stronger than on land. */
    fun offshoreShare(speed: Int): Int = if (speed >= Weather.GALE) 0 else windShare(speed + Balance.OFFSHORE_WIND_GAIN)

    /**
     * How strongly the tide's running at the evening peak, in percent, on
     * [day] of the month: the tide comes round about 50 minutes later each
     * day, so twice a month it's running hard at the peak and twice slack.
     */
    fun tideAtPeak(day: Int): Int {
        val phase = 2 * kotlin.math.PI * day / Balance.TIDE_DAYS
        return (50 + 50 * kotlin.math.cos(phase)).toInt().coerceIn(Balance.TIDE_LEAST, 100)
    }

    /**
     * What a wind farm makes, in percent of its most, at a wind speed: none
     * in a calm, rising to full in a strong breeze, and none in a gale, when
     * the turbines are stopped to save them.
     */
    fun windShare(speed: Int): Int = if (speed >= Weather.GALE) 0 else ((speed - Balance.WIND_START) * 100 / (Balance.WIND_FULL - Balance.WIND_START)).coerceIn(0, 100)

    /**
     * What solar panels make, in percent of their most, over the day and at
     * the evening peak, in [month] under [cloud] percent cloud. The peak comes
     * after sunset in winter and in the low sun of summer evenings.
     */
    fun solarDay(month: Int, cloud: Int): Int = SOLAR_DAY[month] * (100 - cloud * Balance.CLOUD_SHADE / 100) / 100
    fun solarPeak(month: Int, cloud: Int): Int = SOLAR_PEAK[month] * (100 - cloud * Balance.CLOUD_SHADE / 100) / 100

    private val SOLAR_DAY = intArrayOf(10, 15, 22, 28, 33, 36, 35, 31, 25, 18, 12, 9)
    private val SOLAR_PEAK = intArrayOf(0, 0, 2, 8, 15, 20, 18, 12, 5, 0, 0, 0)

    /** Carbon a station gives off, in hundredths of a tonne for each megawatt-hour it makes. */
    fun carbon(t: BuildingType): Int = when (t) {
        BuildingType.COAL_PLANT -> 100
        BuildingType.OIL_PLANT -> 80
        BuildingType.GAS_PLANT -> 45
        else -> 0
    }

    fun fumes(t: BuildingType): Int = when (t) {
        BuildingType.COAL_PLANT -> 40
        BuildingType.OIL_PLANT -> 30
        BuildingType.GAS_PLANT -> 12
        else -> 0
    }

    /**
     * What a station's fuel costs a month for each megawatt it makes. The grid
     * runs the cheapest first: water costs nothing, uranium little, then coal,
     * gas and oil.
     */
    fun fuel(t: BuildingType): Double = when (t) {
        BuildingType.COAL_PLANT -> Balance.COAL_FUEL
        BuildingType.OIL_PLANT -> Balance.OIL_FUEL
        BuildingType.GAS_PLANT -> Balance.GAS_FUEL
        BuildingType.NUCLEAR_PLANT -> Balance.NUCLEAR_FUEL
        else -> 0.0
    }

    fun station(t: BuildingType): Boolean = capacity(t) > 0
}

/**
 * How much power the town uses: each person and job draws more as the years
 * bring electric light, then appliances, then air conditioning, and a little
 * less again later as things grow efficient. The stations have to cover the
 * evening peak, higher in winter and, once there's air conditioning, in summer.
 */
object Electricity {
    private val years = intArrayOf(1900, 1920, 1950, 1970, 1990, 2010, 2030)
    private val watts = intArrayOf(20, 80, 250, 500, 700, 750, 650)

    /** Watts each person draws in [year], on average over the month. */
    fun perPerson(year: Int): Int {
        if (year <= years.first()) return watts.first()
        for (k in 1 until years.size) {
            if (year <= years[k]) return watts[k - 1] + (watts[k] - watts[k - 1]) * (year - years[k - 1]) / (years[k] - years[k - 1])
        }
        return watts.last()
    }

    /** What the peak is above the month's average, in percent: the evening, winter's dark, and summer's air conditioning from the 1960s. */
    fun peak(year: Int, month: Int, climate: Climate = Climate.TEMPERATE, warming: Int = 0): Int {
        // Heating in the cold months, and air conditioning, coming in from 1960, in the hot ones; [warming] in tenths of a degree.
        val t = climate.temperature[month] + warming / 10
        val winter = if (t < 12) min(45, (12 - t) * 3 / 2) else 0
        val cooling = ((year - 1960) * 100 / 40).coerceIn(0, 100)
        val summer = if (t > 16) min(60, (t - 16) * 4) * cooling / 100 else 0
        return 100 + Balance.EVENING_PEAK + winter + summer
    }
}

/**
 * The power grid, worked out again when the map or the month changes. Power
 * spreads as it always has, along ordinary lines and through buildings and
 * zoned land, into networks. Networks joined by high-voltage lines, through
 * substations or a station beside the line, make one grid, each substation
 * passing at most its rating. A grid runs its cheapest stations first. A
 * network that isn't sent enough for its peak powers the buildings nearest
 * where the power comes in first, and the rest go dark. The further a
 * building is from where the power comes in, the more is lost on the way.
 */
internal class PowerGrid(private val map: CityMap) {
    /** The ordinary network each tile's on, -1 if none. */
    val network = IntArray(map.size) { -1 }

    /**
     * What each line tile carries, in kilowatts, as last worked out: the peak
     * draw of everything beyond it, back to where the power comes in. A line
     * carrying more than it's rated for loses more on the way.
     */
    val load = IntArray(map.size)

    /** Last worked out: each station's output in watts, by building id. */
    val output = HashMap<Int, Int>()

    /** The whole grid's capacity, its peak demand, and what of that it couldn't meet, in watts. */
    var capacity = 0L
        private set
    var demand = 0L
        private set
    var short = 0L
        private set

    /** What came in from the neighbours, and what went out to them, last worked out, in watts. */
    var imported = 0L
        private set
    var exported = 0L
        private set

    /** What's wanted on lines with no power at all, in watts: more than [short], which counts only those with some. */
    var unlit = 0L
        private set

    private fun conducts(i: Int): Boolean =
        (map.power[i] == Power.LINE && !map.out(i, Broken.POWER)) || map.building[i] != 0 || map.zone[i] != Zone.NONE

    private fun high(i: Int): Boolean = map.power[i] == Power.HIGH && !map.out(i, Broken.POWER)

    /**
     * Works the grid out: [buildings] with what each draws at the month's
     * average ([draw], in watts) and what each station can make now
     * ([available]); [peak] in percent of the average. Sets [CityMap.powered].
     */
    fun update(
        buildings: Collection<Building>,
        draw: (Building) -> Int,
        available: (Building) -> Int,
        peak: Int,
        /** Power bought from a neighbour, by the line tile at the border it comes in at, in watts: used after the town's own. */
        imports: Map<Int, Long> = emptyMap(),
        /** Power sold to a neighbour, by the line tile it goes out at, in watts: only what's left once the town's served. */
        exports: Map<Int, Long> = emptyMap(),
    ) {
        val m = map
        val n = m.size
        network.fill(-1)
        output.clear()
        val queue = IntArray(n)
        var count = 0
        for (start in 0 until n) {
            if (network[start] >= 0 || !conducts(start)) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            network[start] = count
            while (head < tail) {
                val i = queue[head++]
                val x = i % m.width
                val y = i / m.width
                for (k in 0 until 4) {
                    val nx = x + DX[k]
                    val ny = y + DY[k]
                    if (!m.inside(nx, ny)) continue
                    val j = m.index(nx, ny)
                    if (network[j] >= 0 || !conducts(j)) continue
                    network[j] = count
                    queue[tail++] = j
                }
            }
            count++
        }

        // Where the power comes in to each network: its stations and substations. Steps from them, for the losses,
        // and the way back towards them, for the load on each line.
        val steps = IntArray(n) { -1 }
        val back = IntArray(n) { -1 }
        var head = 0
        var tail = 0
        val feeds = buildings.filter { b -> (Generation.station(b.type) || b.type == BuildingType.SUBSTATION) && b.underway == 0 }
        for (b in feeds) for (y in b.y until b.y + b.type.height) for (x in b.x until b.x + b.type.width) {
            val i = m.index(x, y)
            if (steps[i] < 0) {
                steps[i] = 0
                queue[tail++] = i
            }
        }
        while (head < tail) {
            val i = queue[head++]
            val x = i % m.width
            val y = i / m.width
            for (k in 0 until 4) {
                val nx = x + DX[k]
                val ny = y + DY[k]
                if (!m.inside(nx, ny)) continue
                val j = m.index(nx, ny)
                if (steps[j] >= 0 || !conducts(j)) continue
                steps[j] = steps[i] + 1
                back[j] = i
                queue[tail++] = j
            }
        }

        // Each network's supply and its peak demand, losses and all.
        val supply = LongArray(count)
        val need = LongArray(count)
        val users = Array(count) { ArrayList<Pair<Building, Long>>() }
        var totalCapacity = 0L
        for (b in buildings) {
            val i = m.index(b.x, b.y)
            val c = network[i]
            if (Generation.station(b.type)) {
                val a = available(b).toLong()
                totalCapacity += a
                if (c >= 0) supply[c] += a
            }
            if (c < 0) continue
            val w = draw(b).toLong() * peak / 100
            if (w <= 0) continue
            // More is lost the further it comes, and more again over lines that were overloaded last time.
            var over = 0
            var at = i
            while (at >= 0 && steps[at] > 0) {
                if (m.power[at] == Power.LINE && load[at] > Balance.LINE_RATING) over++
                at = back[at]
            }
            val lost = w * (1000 + Balance.LINE_LOSS * maxOf(0, steps[i]) + Balance.OVERLOAD_LOSS * over) / 1000
            need[c] += lost
            users[c] += b to lost
        }
        // What each line carries now, added up in watts, since a home draws less than a kilowatt.
        val watts = LongArray(n)
        for (c in 0 until count) for ((b, w) in users[c]) {
            var at = m.index(b.x, b.y)
            while (at >= 0 && steps[at] > 0) {
                if (m.power[at] != Power.NONE) watts[at] += w
                at = back[at]
            }
        }
        for (i in 0 until n) load[i] = (watts[i] / 1000).toInt()

        // High-voltage lines join networks into grids, through substations or a station beside the line.
        val hv = IntArray(n) { -1 }
        var hvCount = 0
        for (start in 0 until n) {
            if (hv[start] >= 0 || !high(start)) continue
            head = 0
            tail = 0
            queue[tail++] = start
            hv[start] = hvCount
            while (head < tail) {
                val i = queue[head++]
                val x = i % m.width
                val y = i / m.width
                for (k in 0 until 4) {
                    val nx = x + DX[k]
                    val ny = y + DY[k]
                    if (!m.inside(nx, ny)) continue
                    val j = m.index(nx, ny)
                    if (hv[j] >= 0 || !high(j)) continue
                    hv[j] = hvCount
                    queue[tail++] = j
                }
            }
            hvCount++
        }
        // How much each network can pass to or from the high-voltage lines, and which grid it's in.
        val link = LongArray(count)
        val grid = IntArray(count + hvCount) { it }
        fun root(a: Int): Int {
            var r = a
            while (grid[r] != r) r = grid[r]
            var k = a
            while (grid[k] != r) { val next = grid[k]; grid[k] = r; k = next }
            return r
        }
        for (b in feeds) {
            val c = network[m.index(b.x, b.y)]
            if (c < 0) continue
            val touched = HashSet<Int>()
            for (y in b.y - 1..b.y + b.type.height) for (x in b.x - 1..b.x + b.type.width) {
                if (!m.inside(x, y)) continue
                val h = hv[m.index(x, y)]
                if (h >= 0) touched += h
            }
            if (touched.isEmpty()) continue
            val rating = if (b.type == BuildingType.SUBSTATION) Balance.SUBSTATION_RATING.toLong() else UNLIMITED
            link[c] = minOf(UNLIMITED, link[c] + rating)
            for (h in touched) grid[root(count + h)] = root(c)
        }

        // Each grid runs its cheapest stations first: a station meets its own
        // network's need, then sends what's left to the rest of the grid, as far
        // as the substations at each end can pass it.
        val left = need.copyOf()
        val linkLeft = link.copyOf()
        val got = LongArray(count)
        val members = HashMap<Int, MutableList<Int>>()
        for (c in 0 until count) members.getOrPut(root(c)) { ArrayList() } += c
        val stations = buildings.filter { Generation.station(it.type) && network[m.index(it.x, it.y)] >= 0 }
            .sortedWith(compareBy<Building>({ Generation.fuel(it.type) }, { it.id }))
        for (b in stations) {
            val c = network[m.index(b.x, b.y)]
            var a = available(b).toLong()
            var made = minOf(a, left[c])
            left[c] -= made
            a -= made
            if (a > 0 && linkLeft[c] > 0) for (d in members.getValue(root(c))) {
                if (d == c || left[d] <= 0 || linkLeft[d] <= 0) continue
                val t = minOf(a, left[d], linkLeft[c], linkLeft[d])
                left[d] -= t
                got[d] += t
                linkLeft[c] -= t
                linkLeft[d] -= t
                a -= t
                made += t
                if (a <= 0 || linkLeft[c] <= 0) break
            }
            output[b.id] = made.toInt()
        }
        // Then what's bought from the neighbours, where it comes in.
        var boughtIn = 0L
        for ((i, w) in imports) {
            val c = network[i]
            if (c < 0 || w <= 0) continue
            val take = minOf(w, left[c])
            left[c] -= take
            got[c] += take
            boughtIn += take
        }
        // Sold: what the stations on the seller's network still have to give.
        var soldOut = 0L
        if (exports.isNotEmpty()) {
            val room = LongArray(count)
            for (b in stations) {
                val c = network[m.index(b.x, b.y)]
                room[c] += maxOf(0L, available(b).toLong() - (output[b.id] ?: 0))
            }
            for ((i, w) in exports) {
                val c = network[i]
                if (c < 0 || w <= 0) continue
                val take = minOf(w, room[c])
                room[c] -= take
                soldOut += take
            }
        }
        imported = boughtIn
        exported = soldOut

        // Each network powers what it was sent, nearest the power first.
        m.powered.fill(false)
        val lit = BooleanArray(count)
        var totalNeed = 0L
        var totalShort = 0L
        var dark = 0L
        for (c in 0 until count) {
            totalNeed += need[c]
            // A network with power to hand is live, whether or not anything on it needs it yet.
            lit[c] = supply[c] > 0 || got[c] > 0 || link[c] > 0 && members.getValue(root(c)).any { supply[it] > 0 } ||
                imports.any { (i, w) -> w > 0 && network[i] == c }
            if (!lit[c]) {
                dark += need[c]
                continue
            }
            var have = need[c] - left[c]
            users[c].sortWith(compareBy<Pair<Building, Long>>({ steps[m.index(it.first.x, it.first.y)] }, { it.first.id }))
            for ((b, w) in users[c]) {
                if (w > have) {
                    totalShort += w
                    continue
                }
                have -= w
                for (y in b.y until b.y + b.type.height) for (x in b.x until b.x + b.type.width) m.powered[m.index(x, y)] = true
            }
        }
        // Lines, zoned land and buildings that draw nothing are powered with their network.
        for (i in 0 until n) {
            val c = network[i]
            if (c >= 0 && lit[c] && m.building[i] == 0) m.powered[i] = true
        }
        for (b in buildings) {
            val c = network[m.index(b.x, b.y)]
            if (c >= 0 && lit[c] && draw(b) <= 0) {
                for (y in b.y until b.y + b.type.height) for (x in b.x until b.x + b.type.width) m.powered[m.index(x, y)] = true
            }
        }
        capacity = totalCapacity
        demand = totalNeed
        short = totalShort
        unlit = dark
    }

    private companion object {
        const val UNLIMITED = Long.MAX_VALUE / 4
        val DX = intArrayOf(0, 1, 0, -1)
        val DY = intArrayOf(-1, 0, 1, 0)
    }
}
