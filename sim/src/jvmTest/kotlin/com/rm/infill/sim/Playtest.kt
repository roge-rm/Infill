package com.rm.infill.sim

import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.test.Test

/**
 * The balance pass: a scripted player grows a few towns from 1900 and writes
 * down how each one went, year by year. It plays like a careful player:
 * streets in a grid of blocks out from a main street to the edge, zoning
 * what the town asks for, power, water and the sewer, services as the town
 * grows, and taxes up when the money runs low.
 *
 * Only runs with PLAYTEST_OUT set to a folder for the results, all the
 * towns at once; PLAYTEST_TOWNS picks some by name, PLAYTEST_YEARS how long:
 *   PLAYTEST_OUT=/some/folder ./gradlew :sim:jvmTest --tests '*Playtest*'
 */
class Playtest {
    /** The news worth noting in a town's log. */
    private val NOTED = setOf(
        EventKind.Epidemic, EventKind.MedicalAdvance, EventKind.Drought, EventKind.StormSurge, EventKind.WorkedOut, EventKind.Protest,
        EventKind.GrantOffered, EventKind.GrantPaid, EventKind.GrantLapsed, EventKind.RiverFlood,
    )

    private val out = System.getenv("PLAYTEST_OUT")?.let { File(it) }
    private val years = System.getenv("PLAYTEST_YEARS")?.toIntOrNull() ?: 120
    private val only = System.getenv("PLAYTEST_TOWNS")?.split(",")?.toSet()

    /** Years to save each town in, in July, as name-year.infill beside the results. */
    private val saveIn = System.getenv("PLAYTEST_SAVES")?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet().orEmpty()

    private val towns = listOf<Pair<String, () -> Unit>>(
        "grid" to { play("grid", 11, TerrainOptions(water = 10, trees = 30, river = false)) },
        "river" to { play("river", 23, TerrainOptions(water = 30, trees = 40, river = true)) },
        "rail" to { play("rail", 37, TerrainOptions(water = 10, trees = 30, river = false), rail = true) },
        "hightax" to { play("hightax", 11, TerrainOptions(water = 10, trees = 30, river = false), tax = 10) },
        "coast" to { play("coast", 53, TerrainOptions(water = 10, trees = 30, river = false, sea = Sea.ONE_SIDE)) },
    )

    @Test
    fun towns() {
        if (out == null) return
        val threads = towns.filter { only == null || it.first in only }.map { (_, run) -> Thread(run).also { it.start() } }
        threads.forEach { it.join() }
    }

    private fun play(name: String, seed: Long, terrain: TerrainOptions, rail: Boolean = false, tax: Int? = null) {
        val dir = out ?: return
        dir.mkdirs()
        val c = City(seed, 128, 128, terrain)
        val log = File(dir, "$name.log").printWriter()
        val p = Player(c, rail) { log.println("${c.year}-${(c.month + 1).toString().padStart(2, '0')} $it") }
        tax?.let {
            c.residentialTax = it
            c.commercialTax = it
            c.industrialTax = it
            p.taxFloor = it
        }
        val csv = File(dir, "$name.csv").printWriter()
        csv.println(Player.HEADER)
        // How long the sim took over each year, its slowest day, and how much happened, for the pace.
        val pace = File(dir, "$name-pace.csv").printWriter()
        pace.println("year,population,era,sim_ms,slowest_day_ms,events,news")
        var simNanos = 0L
        var slowest = 0L
        var events = 0
        var news = 0
        p.start()
        val end = c.year + years
        var lastYear = c.year
        val saved = HashSet<Int>()
        while (c.year < end) {
            repeat(Balance.DEMAND_DAYS) {
                val t0 = System.nanoTime()
                c.tick()
                val took = System.nanoTime() - t0
                simNanos += took
                slowest = max(slowest, took)
            }
            p.week()
            c.takeEvents { e ->
                events++
                if (e.kind in NOTED) news++
                if (e.kind in NOTED) log.println("${c.year}-${(c.month + 1).toString().padStart(2, '0')} news ${e.kind} ${e.type ?: ""} ${e.count}")
            }
            if (c.year in saveIn && c.month >= 6 && saved.add(c.year)) File(dir, "$name-${c.year}.infill").writeBytes(SaveGame.write(c))
            if (c.year != lastYear) {
                pace.println("$lastYear,${c.stats.population},${c.era},${simNanos / 1_000_000},${slowest / 1_000_000},$events,$news")
                pace.flush()
                simNanos = 0L
                slowest = 0L
                events = 0
                news = 0
                csv.println(p.row())
                csv.flush()
                log.flush()
                lastYear = c.year
            }
        }
        csv.close()
        log.close()
        pace.close()
    }
}

/** The scripted player. See [Playtest]. */
class Player(private val c: City, private val withRail: Boolean, private val note: (String) -> Unit) {
    private val m = c.map

    /** Blocks between streets every [S] tiles; block (i, j) is the land inside x S*i+1..S*i+S-1. */
    private val n = m.width / S
    private val centre = n / 2
    private val mainY = centre * S

    private enum class Use { NONE, ZONE, CIVIC, UTILITY, BLOCKED }

    private val use = Array(n) { Array(n) { Use.NONE } }
    private val zoneOf = Array(n) { ByteArray(n) }
    private val densityOf = Array(n) { ByteArray(n) }
    private val segments = ArrayList<IntArray>()
    private var powerUp = false
    private var mainsUp = false
    private var sewerUp = false
    private var phonesUp = false
    private var fibreUp = false
    private var weeks = 0
    private var lastUpkeep = 0L
    var taxFloor = Balance.DEFAULT_TAX
    private val seen = HashSet<String>()

    fun start() {
        // Land kept back: one block in nine for services, as a planner would.
        for (i in 1 until n - 1) for (j in 1 until n - 1) if (i % 3 == 1 && j % 3 == 1 && j != centre && j != centre - 1) use[i][j] = Use.CIVIC
        // And five out east for plants and dumps, for the town's whole life.
        for (j in listOf(centre - 5, centre - 3, centre + 2, centre + 4, centre + 6)) if (j in 1 until n - 1) use[n - 2][j] = Use.UTILITY
        // The main street, edge to edge: the way in.
        road(Action.roadPath(m, 0, mainY, m.width - 1, mainY, true))
        if (withRail) {
            // A line in from the west edge to a station by the middle, a block above the main street.
            val y = mainY - S / 2
            for (i in 0 until centre - 1) use[i][centre - 1] = Use.BLOCKED
            act("rail", Action.BuildRail(Action.roadPath(m, 0, y, (centre - 1) * S - 3, y, true)))
            act("station", Action.PlaceBuilding(BuildingType.STATION, (centre - 1) * S - 3, y))
            road(Action.roadPath(m, (centre - 1) * S, y - 1, (centre - 1) * S, mainY, false))
        }
        zoneMore(Zone.RESIDENTIAL)
        zoneMore(Zone.RESIDENTIAL)
        zoneMore(Zone.COMMERCIAL)
        zoneMore(Zone.INDUSTRIAL)
        zoneMore(Zone.FARMLAND)
    }

    /** What the player does each week, looking mostly once a month. */
    fun week() {
        weeks++
        // Zoning is quick to answer; the rest is looked at monthly.
        for (a in c.advice) if (a.kind == AdviceKind.ZONE_MORE) zoneMore(a.zone)
        if (weeks % 4 != 0) return
        val s = c.stats
        val kinds = c.advice.map { it.kind }.toSet()
        for (k in kinds) if (seen.add("advice $k")) note("first advice: $k")
        money()
        val upkeep = s.upkeep
        if (lastUpkeep > 0 && upkeep > lastUpkeep * 2 && upkeep > 2000) {
            val parts = Stats::class.java.declaredFields.filter { it.type == Long::class.javaPrimitiveType }
                .map { f -> f.isAccessible = true; f.name to f.getLong(s) }.filter { it.second > 0 }.sortedByDescending { it.second }
            note("upkeep jumped $lastUpkeep to $upkeep: " + parts.take(12).joinToString { "${it.first} ${it.second}" })
            if (s.floodCost > 0) {
                val flooded = (0 until m.size).count { (m.floodMemory[it].toInt() and 0xff) >= Balance.FLOODED }
                val built = (0 until m.size).count { m.building[it] != 0 }
                val w = c.weather
                note("flood: $flooded tiles remember one, of $built built; weather now ${w.precipitation} ${w.intensity}, storm drains ${(0 until m.size).count { m.stormPipe[it].toInt() != 0 }}")
            }
        }
        lastUpkeep = upkeep
        // After a flood, drains that lead somewhere.
        if (s.floodCost > 0 && mainsUp && count(BuildingType.STORM_OUTFALL) < 1 + s.population / 20000 && weeks % 4 == 0) {
            if (byWater(BuildingType.STORM_OUTFALL, Pipe.STORM)) note("storm outfall after a flood costing ${s.floodCost}")
        }
        if (AdviceKind.DEBT in kinds) return
        // Lots without power or water: lines and pipes round the block the advice points at.
        for (a in c.advice) if (a.x >= 0 && (a.kind == AdviceKind.NO_POWER || a.kind == AdviceKind.NO_WATER || a.kind == AdviceKind.NO_SEWER)) {
            val bi = a.x / S
            val bj = a.y / S
            for (edge in listOf(
                side(bi * S, bj * S, bi * S + S, bj * S), side(bi * S, bj * S + S, bi * S + S, bj * S + S),
                side(bi * S, bj * S, bi * S, bj * S + S), side(bi * S + S, bj * S, bi * S + S, bj * S + S),
            )) {
                val streets = edge.filter { it in 0 until m.size && m.road[it] != Road.NONE }.toIntArray()
                when (a.kind) {
                    AdviceKind.NO_POWER -> if (powerUp) wire(streets)
                    AdviceKind.NO_WATER -> if (mainsUp) lay(streets, m.waterPipe) { Action.BuildPipe(it, Pipe.WATER) }
                    else -> if (sewerUp) lay(streets, m.sewerPipe) { Action.BuildPipe(it, Pipe.SEWER) }
                }
            }
        }
        if (!powerUp && (s.population > 0 || c.funds > 8000)) power()
        if (AdviceKind.POWER_SHORT in kinds || AdviceKind.NO_POWER in kinds) power()
        val next = c.era.next
        val goals = next?.let { c.goals(it) }.orEmpty()
        val wantMains = AdviceKind.NO_WATER in kinds || AdviceKind.WATER_SHORT in kinds ||
            goals.any { (it.kind == GoalKind.MainsOrStation || it.kind == GoalKind.OnMains) && !it.met } || s.population > 600
        if (wantMains || mainsUp) water(AdviceKind.WATER_SHORT in kinds || AdviceKind.NO_WATER in kinds || sources() == 0)
        val wantSewer = AdviceKind.NO_SEWER in kinds || goals.any { it.kind == GoalKind.OnSewer && !it.met } || s.population > 4000
        if (wantSewer) sewer()
        if (AdviceKind.GARBAGE in kinds) place(if (c.allows(BuildingType.INCINERATOR) && s.population > 20000) BuildingType.INCINERATOR else BuildingType.DUMP, utility = true)
        c.advice.firstOrNull { it.kind == AdviceKind.GARBAGE_FAR }?.let { a ->
            val near = if (a.x >= 0) a.x / S to a.y / S else null
            if (weeks % 8 == 0) place(if (c.allows(BuildingType.TRANSFER_STATION)) BuildingType.TRANSFER_STATION else BuildingType.DUMP, near = near)
        }
        if (AdviceKind.UNAPPEALING in kinds && weeks % 12 == 0) parks()
        if (AdviceKind.ROUGH_SLEEPERS in kinds && weeks % 8 == 0) place(BuildingType.SHELTER)
        services()
        leisure(AdviceKind.LEISURE in kinds)
        civic()
        laws(kinds)
        keepUp()
        phones()
        zoneOnDemand()
        if (weeks % 52 == 0 && c.year % 5 == 0) {
            lotStates(Zone.RESIDENTIAL)
            whyUnappealing(Zone.RESIDENTIAL)
            val kinds = c.allBuildings.filter { it.type.zone == Zone.NONE }.groupingBy { it.type }.eachCount()
            note("services: " + kinds.entries.sortedBy { it.key.name }.joinToString { "${it.key.name.lowercase()} ${it.value}" })
            note("crime: town ${avg(m.crime)}, homes ${avg(m.crime, Zone.RESIDENTIAL)} (theft ${avg(m.theft, Zone.RESIDENTIAL)}, vice ${avg(m.vice, Zone.RESIDENTIAL)}, " +
                "rackets ${avg(m.rackets, Zone.RESIDENTIAL)}), police cover ${avg(m.policeCover, Zone.RESIDENTIAL)}; justice ${c.stats.justice}, arrests ${c.stats.arrests}, heard ${c.stats.heard}, cells ${c.stats.cells}")
        }
        if (weeks % 52 == 0) {
            for (z in listOf(Zone.RESIDENTIAL, Zone.COMMERCIAL)) if (c.advice.any { it.kind == AdviceKind.UNAPPEALING && it.zone == z }) whyUnappealing(z)
        }
        if (goals.any { it.kind == GoalKind.Downtown && !it.met } && c.era >= Era.MOTOR) downtown()
        if (goals.any { it.kind == GoalKind.HighSchool && !it.met }) place(BuildingType.HIGH_SCHOOL)
        if (goals.any { it.kind == GoalKind.Flow && !it.met } && weeks % 26 == 0) upgradeRoads()
        if (weeks % 52 == 0) {
            val missing = goals.filter { !it.met }
            if (next != null && missing.isNotEmpty()) note("toward $next: " + missing.joinToString { "${it.kind} ${it.have}/${it.need}" })
        }
    }

    // ---- money ----

    private fun money() {
        val change = c.stats.income - c.stats.upkeep
        val taxes = listOf(c.residentialTax, c.commercialTax, c.industrialTax)
        if (c.funds < 5000 && change < 0 && taxes.max() < 12) {
            setTaxes(1)
            note("taxes up to ${c.residentialTax}: funds ${c.funds}, a month ${change}")
        } else if (c.funds > max(20_000L, c.stats.upkeep * 6) && change > 0 && taxes.min() > floorNow() && weeks % 12 == 0) {
            setTaxes(-1)
            note("taxes down to ${c.residentialTax}: funds ${c.funds}")
        }
    }

    /** The lowest the player takes taxes: the floor, or lower still with years of upkeep in the bank, as people like low taxes. */
    private fun floorNow(): Int = if (taxFloor == Balance.DEFAULT_TAX && c.funds > c.stats.upkeep * RICH_MONTHS) LOW_TAX else taxFloor

    private fun setTaxes(by: Int) {
        c.residentialTax = (c.residentialTax + by).coerceIn(0, 20)
        c.commercialTax = (c.commercialTax + by).coerceIn(0, 20)
        c.industrialTax = (c.industrialTax + by).coerceIn(0, 20)
    }

    private fun afford(cost: Long, keep: Long = 2000) = c.funds - cost >= keep

    // ---- building ----

    private fun act(what: String, a: Action): Boolean {
        val plan = c.plan(a)
        if (!plan.ok) return false
        if (!afford(plan.cost, 0)) return false
        val done = c.apply(a)
        if (done.ok && seen.add("built $what")) note("first $what (${done.cost})")
        return done.ok
    }

    private fun roadType(): RoadType = if (c.era >= Era.STREETCAR && c.allows(RoadType.STREET)) RoadType.STREET else RoadType.DIRT

    /** A road along [tiles], with power and pipes along it once the town has them. */
    private fun road(tiles: IntArray): Boolean {
        val missing = tiles.filter { m.road[it] == Road.NONE }.toIntArray()
        if (missing.isEmpty()) return true
        val a = Action.BuildRoad(tiles, roadType(), pipes = mainsUp)
        val plan = c.plan(a)
        if (!plan.ok) {
            // Over water, try a bridge.
            val bridged = Action.BuildRoad(tiles, roadType(), pipes = mainsUp, bridge = BridgeKind.entries.first())
            if (!c.plan(bridged).ok || !act("bridge", bridged)) return false
        } else if (!act("road", a)) return false
        segments += tiles
        if (powerUp) wire(tiles)
        if (phonesUp) phone(tiles)
        if (sewerUp) c.apply(Action.BuildPipe(tiles, Pipe.SEWER))
        return true
    }

    private fun side(x0: Int, y0: Int, x1: Int, y1: Int) = Action.roadPath(m, x0, y0, x1, y1, y0 == y1)

    /** Builds the streets round block (i, j). */
    private fun open(i: Int, j: Int): Boolean {
        val x0 = i * S
        val y0 = j * S
        val x1 = x0 + S
        val y1 = y0 + S
        return listOf(side(x0, y0, x1, y0), side(x0, y1, x1, y1), side(x0, y0, x0, y1), side(x1, y0, x1, y1)).all { road(it) }
    }

    private fun inside(i: Int, j: Int) = i in 1 until n - 1 && j in 1 until n - 1

    /** Whether block (i, j) touches the streets built so far. */
    private fun reachable(i: Int, j: Int): Boolean {
        val x0 = i * S
        val y0 = j * S
        for (k in 0..S) {
            for ((x, y) in listOf(x0 + k to y0, x0 + k to y0 + S, x0 to y0 + k, x0 + S to y0 + k)) {
                if (m.inside(x, y) && m.road[m.index(x, y)] != Road.NONE) return true
            }
        }
        return false
    }

    /** How well block (i, j) suits [zone]: lower is better. */
    private fun score(zone: Byte, i: Int, j: Int): Double {
        val di = i - centre + 0.5
        val dj = j - centre + 0.5
        val d = sqrt(di * di + dj * dj)
        val nearIndustry = neighbours(i, j).any { (a, b) -> use[a][b] == Use.ZONE && zoneOf[a][b] == Zone.INDUSTRIAL }
        val onMain = j == centre || j == centre - 1
        return when (zone) {
            Zone.COMMERCIAL, Zone.OFFICE, Zone.MIXED -> d - (if (onMain) 2 else 0)
            Zone.INDUSTRIAL -> -i * 1.5 + abs(dj) * 0.8
            Zone.FARMLAND -> -d + (if (i > centre + 1) 4 else 0)
            else -> d + (if (nearIndustry) 3 else 0) + (if (i > centre + 2) 2 else 0)
        }
    }

    private fun neighbours(i: Int, j: Int) = listOf(i - 1 to j, i + 1 to j, i to j - 1, i to j + 1).filter { (a, b) -> a in 0 until n && b in 0 until n }

    private fun density(zone: Byte, i: Int, j: Int): Byte {
        val di = i - centre + 0.5
        val dj = j - centre + 0.5
        val d = sqrt(di * di + dj * dj)
        return when (zone) {
            Zone.FARMLAND -> Density.MEDIUM
            Zone.INDUSTRIAL -> if (c.era >= Era.MOTOR) Density.HIGH else Density.MEDIUM
            Zone.RESIDENTIAL -> when {
                c.era >= Era.MOTOR && d < 2.5 -> Density.HIGH
                c.era >= Era.STREETCAR && d < 4.5 -> Density.MEDIUM
                else -> Density.LOW
            }
            else -> if (c.era >= Era.MOTOR && d < 2.5) Density.HIGH else Density.MEDIUM
        }
    }

    private var lastZoned = HashMap<Byte, Int>()

    /** Zones a new block for [zone], the best place left that the streets reach or can reach. */
    private fun zoneMore(zone: Byte) {
        if (zone == Zone.NONE || !c.allowsZone(zone)) return
        // Not more than one block of a kind a fortnight: growth takes a while to show.
        if (weeks - (lastZoned[zone] ?: -99) < 2) return
        val candidates = ArrayList<Pair<Int, Int>>()
        for (i in 1 until n - 1) for (j in 1 until n - 1) if (use[i][j] == Use.NONE && reachable(i, j)) candidates += i to j
        for ((i, j) in candidates.sortedBy { (i, j) -> score(zone, i, j) }) {
            if (!afford(1500)) return
            if (!open(i, j)) {
                use[i][j] = Use.BLOCKED
                continue
            }
            val d = density(zone, i, j)
            // A green in the middle of homes and shops, where the lots are furthest from the street anyway.
            if (zone == Zone.RESIDENTIAL || zone == Zone.COMMERCIAL) {
                val mx = i * S + S / 2
                val my = j * S + S / 2
                val green = Action.PlaceParks(mx - 1, my - 1, mx + 1, my + 1)
                if (!act("green", green)) note("green failed: ${c.plan(green).problem}")
            }
            val a = Action.PlaceZone(i * S + 1, j * S + 1, i * S + S - 1, j * S + S - 1, zone, d)
            if (!c.plan(a).ok) {
                use[i][j] = Use.BLOCKED
                continue
            }
            if (!act("zone $zone", a)) return
            use[i][j] = Use.ZONE
            zoneOf[i][j] = zone
            densityOf[i][j] = d
            lastZoned[zone] = weeks
            return
        }
        // No land left: build up, or take in farmland near the town.
        if (afford(3000)) {
            if (upzone(zone) || takeFarm(zone)) lastZoned[zone] = weeks
        }
    }

    /** Raises the density of the most central block of [zone] that can go higher in this era. */
    private fun upzone(zone: Byte): Boolean {
        if (zone == Zone.FARMLAND) return false
        val top = when {
            zone != Zone.INDUSTRIAL && c.allowsDensity(Density.TOWER) && c.era >= Era.RENEWAL -> Density.TOWER
            c.era >= Era.MOTOR -> Density.HIGH
            c.era >= Era.STREETCAR -> Density.MEDIUM
            else -> return false
        }
        var best: Pair<Int, Int>? = null
        for (i in 1 until n - 1) for (j in 1 until n - 1) {
            if (use[i][j] != Use.ZONE || zoneOf[i][j] != zone || Density.rank(densityOf[i][j]) >= Density.rank(top)) continue
            if (best == null || score(Zone.COMMERCIAL, i, j) < score(Zone.COMMERCIAL, best.first, best.second)) best = i to j
        }
        val (i, j) = best ?: return false
        val d = (densityOf[i][j] + 1).toByte()
        if (!act("upzone $zone", Action.PlaceZone(i * S + 1, j * S + 1, i * S + S - 1, j * S + S - 1, zone, d))) return false
        densityOf[i][j] = d
        note("upzoned $zone at $i,$j to $d")
        return true
    }

    /** Rezones the farm block nearest the middle for [zone]. */
    private fun takeFarm(zone: Byte): Boolean {
        if (zone == Zone.FARMLAND) return false
        var best: Pair<Int, Int>? = null
        for (i in 1 until n - 1) for (j in 1 until n - 1) {
            if (use[i][j] != Use.ZONE || zoneOf[i][j] != Zone.FARMLAND) continue
            if (best == null || score(zone, i, j) < score(zone, best.first, best.second)) best = i to j
        }
        val (i, j) = best ?: return false
        val d = density(zone, i, j)
        if (!act("farm to $zone", Action.PlaceZone(i * S + 1, j * S + 1, i * S + S - 1, j * S + S - 1, zone, d))) return false
        zoneOf[i][j] = zone
        densityOf[i][j] = d
        note("farm block $i,$j rezoned $zone")
        return true
    }

    private var band = 0

    /** Renovates worn services and plants each quarter, and relays a band of the town's streets and pipes each year. */
    private fun keepUp() {
        if (weeks % 13 != 0) return
        for (b in c.allBuildings.filter { c.renovatable(it) }.sortedBy { c.condition(it) }.take(6)) {
            act("renovation", Action.RenewArea(b.x, b.y, b.x, b.y))
        }
        if (weeks % 52 == 0) {
            val rows = m.height / 16
            band = (band + 1) % rows
            val a = Action.RenewArea(0, band * 16, m.width - 1, band * 16 + 15)
            val cost = c.plan(a).cost
            if (cost > 0 && afford(cost, 20_000)) act("relaying", a)
        }
    }

    /** Somewhere for [type]: in a civic block (or a utility block for the dirty ones), opening a new one if they're full. */
    private fun place(first: BuildingType, utility: Boolean = false, near: Pair<Int, Int>? = null): Boolean {
        // The newest kind of it the town can build.
        val type = c.newest(first)
        if (!c.allows(type) || weeks < (failed[type] ?: 0)) return false
        if (placeAt(type, utility, near)) return true
        failed[type] = weeks + 13
        return false
    }

    /** Types that found nowhere lately, and the week to try again. */
    private val failed = HashMap<BuildingType, Int>()

    private fun placeAt(type: BuildingType, utility: Boolean, near: Pair<Int, Int>?): Boolean {
        val want = if (utility) Use.UTILITY else Use.CIVIC
        val price = c.plan(Action.PlaceBuilding(type, 0, 0)).cost
        if (!afford(price, 4000)) return false
        val mine = ArrayList<Pair<Int, Int>>()
        for (i in 1 until n - 1) for (j in 1 until n - 1) if (use[i][j] == want) mine += i to j
        // Near where it's wanted: within a block or so of it, else a new block there.
        val close = if (near == null) mine else mine.filter { (i, j) -> max(abs(i - near.first), abs(j - near.second)) <= 2 }
        val sorted = close.sortedBy { (i, j) -> if (near == null) abs(i - centre) + abs(j - centre) else abs(i - near.first) + abs(j - near.second) }
        for ((i, j) in sorted) {
            // A block kept back gets its streets when it's first used, if the town has reached it.
            if (!reachable(i, j)) continue
            if (!open(i, j)) continue
            if (fit(type, i, j)) return true
        }
        // A new block: civic ones near the people, utility ones out east.
        val candidates = ArrayList<Pair<Int, Int>>()
        for (i in 1 until n - 1) for (j in 1 until n - 1) if (use[i][j] == Use.NONE && reachable(i, j)) candidates += i to j
        val order = when {
            utility -> candidates.sortedBy { (i, j) -> -i * 2.0 + abs(j - centre) }
            near != null -> candidates.sortedBy { (i, j) -> abs(i - near.first) + abs(j - near.second) }
            else -> candidates.sortedBy { (i, j) -> score(Zone.RESIDENTIAL, i, j) }
        }
        for ((i, j) in order) {
            if (!open(i, j)) {
                use[i][j] = Use.BLOCKED
                continue
            }
            use[i][j] = want
            if (fit(type, i, j)) return true
        }
        return false
    }

    private fun fit(type: BuildingType, i: Int, j: Int): Boolean {
        for (y in j * S + 1..j * S + S - type.height) for (x in i * S + 1..i * S + S - type.width) {
            // Up against a street, so it's reached.
            val edge = x == i * S + 1 || y == j * S + 1 || x + type.width == i * S + S || y + type.height == j * S + S
            if (!edge) continue
            val a = Action.PlaceBuilding(type, x, y)
            if (c.plan(a).ok) return act(type.name, a)
        }
        return false
    }

    // ---- utilities ----

    private fun power() {
        val type = when {
            c.allows(BuildingType.GAS_PLANT) -> BuildingType.GAS_PLANT
            c.allows(BuildingType.OIL_PLANT) -> BuildingType.OIL_PLANT
            else -> BuildingType.COAL_PLANT
        }
        if (powerUp && weeks % 8 != 0) return
        // Out east if there's room, else in any block kept back, else clear the outermost block of works or homes for it.
        if (!place(type, utility = true) && !place(type) && !(powerUp && clearFor(type))) return
        if (!powerUp) {
            powerUp = true
            note("power: lines along ${segments.size} streets")
            for (s in segments) wire(s)
            val roads = (0 until m.size).count { m.road[it] != Road.NONE }
            val lined = (0 until m.size).count { m.road[it] != Road.NONE && m.power[it] != Power.NONE }
            note("lines on $lined of $roads street tiles")
        }
    }

    /** Clears the zoned block furthest out, works before homes, and puts [first] there. */
    private fun clearFor(first: BuildingType): Boolean {
        val type = c.newest(first)
        var best: Pair<Int, Int>? = null
        fun rank(i: Int, j: Int) = (if (zoneOf[i][j] == Zone.INDUSTRIAL) 1000 else 0) + abs(i - centre) + abs(j - centre)
        for (i in 1 until n - 1) for (j in 1 until n - 1) {
            if (use[i][j] != Use.ZONE || zoneOf[i][j] !in listOf(Zone.INDUSTRIAL, Zone.RESIDENTIAL)) continue
            if (best == null || rank(i, j) > rank(best.first, best.second)) best = i to j
        }
        val (i, j) = best ?: return false
        val clear = Action.Bulldoze(i * S + 1, j * S + 1, i * S + S - 1, j * S + S - 1)
        val cost = c.plan(clear).cost + c.plan(Action.PlaceBuilding(type, 0, 0)).cost
        if (!afford(cost, 10_000)) return false
        if (!act("clearing for ${type.name}", clear)) return false
        use[i][j] = Use.UTILITY
        note("cleared block $i,$j for ${type.name}")
        return fit(type, i, j)
    }

    /** Power lines along [tiles] where there aren't any yet. */
    private fun wire(tiles: IntArray) = lay(tiles, m.power) { Action.BuildPowerLine(it, buried = wet(it)) }

    /** Phone lines the same way, copper and later fibre. */
    private fun phone(tiles: IntArray) = lay(tiles, m.phone) { Action.BuildPhoneLine(it, fibre = fibreUp, buried = wet(it)) }

    /** Whether a run crosses water or track, where it has to go under. */
    private fun wet(tiles: IntArray) = tiles.any { m.terrain[it] == Terrain.WATER || m.rail[it] != Rail.NONE }

    /** [make] along the tiles of [tiles] that [layer] has nothing on, in runs, so a corner already done doesn't stop the rest. */
    private fun lay(tiles: IntArray, layer: ByteArray, make: (IntArray) -> Action) {
        var run = ArrayList<Int>()
        fun go() {
            if (run.isNotEmpty()) c.apply(make(run.toIntArray()))
            run = ArrayList()
        }
        for (i in tiles) if (layer[i].toInt() == 0) run += i else go()
        go()
    }

    /** Mains: pipes under the streets and a source, a pumping station by water or a well field. */
    private fun water(more: Boolean) {
        if (!mainsUp) {
            val cost = segments.sumOf { it.size } * Pipe.WATER.price
            if (!afford(cost, 6000)) return
            mainsUp = true
            note("mains under ${segments.size} streets")
            for (s in segments) c.apply(Action.BuildPipe(s, Pipe.WATER))
        }
        if (more && (sources() == 0 || weeks % 8 == 0)) {
            if (!byWater(BuildingType.PUMPING_STATION, Pipe.WATER) && !place(BuildingType.WELL_FIELD, utility = true) && weeks % 26 == 0) clearFor(BuildingType.WELL_FIELD)
        }
        if (!mainsUp) return
        // Enough sources for everyone, with some to spare.
        val s = c.stats
        if (s.waterUsed > 0 && (s.waterShort > 0 || s.waterUsed * 10 > s.waterSupply * 9) && weeks % 8 == 4) {
            if (!byWater(BuildingType.PUMPING_STATION, Pipe.WATER) && !place(BuildingType.WELL_FIELD, utility = true) && weeks % 26 == 0) clearFor(BuildingType.WELL_FIELD)
        }
        // Towers so the pressure reaches every block, as the water map shows.
        cover(listOf(BuildingType.PUMPING_STATION, BuildingType.WELL_FIELD, BuildingType.WATER_TOWER), Balance.PRESSURE_REACH - 6, BuildingType.WATER_TOWER)
    }

    private fun sewer() {
        if (!sewerUp) {
            val cost = segments.sumOf { it.size } * Pipe.SEWER.price
            if (!afford(cost, 6000)) return
            sewerUp = true
            note("sewers under ${segments.size} streets")
            for (s in segments) c.apply(Action.BuildPipe(s, Pipe.SEWER))
            val works = if (c.allows(BuildingType.SEWAGE_WORKS)) BuildingType.SEWAGE_WORKS else BuildingType.OUTFALL
            if (!byWater(works, Pipe.SEWER) && works != BuildingType.OUTFALL) byWater(BuildingType.OUTFALL, Pipe.SEWER)
        } else if (AdviceKind.NO_SEWER in c.advice.map { it.kind } && weeks % 26 == 0) {
            val works = if (c.allows(BuildingType.TREATMENT_PLANT)) BuildingType.TREATMENT_PLANT else BuildingType.SEWAGE_WORKS
            if (!byWater(works, Pipe.SEWER)) byWater(BuildingType.OUTFALL, Pipe.SEWER)
        }
    }

    /** Puts [type] where it can go by water, nearest the middle, piped and wired to the nearest street. */
    private fun byWater(type: BuildingType, pipe: Pipe): Boolean {
        if (!c.allows(type)) return false
        val cx = centre * S
        val spots = ArrayList<Int>()
        for (y in 1 until m.height - type.height) for (x in 1 until m.width - type.width) {
            val i = m.index(x, y)
            if (m.terrain[i] == Terrain.WATER || m.building[i] != 0 || m.zone[i] != Zone.NONE || m.road[i] != Road.NONE) continue
            // Within a tile of water all round it.
            var wet = false
            for (ty in y - 1..y + type.height) for (tx in x - 1..x + type.width) if (m.inside(tx, ty) && m.terrain[m.index(tx, ty)] == Terrain.WATER) wet = true
            if (wet) spots += i
        }
        spots.sortBy { val x = it % m.width; val y = it / m.width; abs(x - cx) + abs(y - mainY) }
        var tried = 0
        for (i in spots) {
            if (++tried > 300) return false
            val x = i % m.width
            val y = i / m.width
            val a = Action.PlaceBuilding(type, x, y)
            if (!c.plan(a).ok) continue
            // Find the nearest street tile and run a pipe and a line to it.
            val road = nearestRoad(x, y) ?: continue
            val path = Action.roadPath(m, x, y, road % m.width, road / m.width, true)
            if (!afford(c.plan(a).cost + path.size * 40L, 4000)) return false
            if (!act(type.name, a)) return false
            c.apply(Action.BuildPipe(path, pipe))
            if (type.needsPower) c.apply(Action.BuildPowerLine(path))
            return true
        }
        return false
    }

    private fun nearestRoad(x: Int, y: Int): Int? {
        var best: Int? = null
        var bestD = 30
        for (dy in -bestD..bestD) for (dx in -bestD..bestD) {
            val tx = x + dx
            val ty = y + dy
            if (!m.inside(tx, ty) || m.road[m.index(tx, ty)] == Road.NONE) continue
            val d = abs(dx) + abs(dy)
            if (d < bestD) {
                bestD = d
                best = m.index(tx, ty)
            }
        }
        return best
    }

    // ---- services ----

    private fun waterSupply() = count(BuildingType.PUMPING_STATION) * Balance.PUMP_SUPPLY + count(BuildingType.WELL_FIELD) * Balance.WELL_SUPPLY

    private fun sources() = count(BuildingType.PUMPING_STATION) + count(BuildingType.WELL_FIELD)

    private fun count(type: BuildingType) = c.allBuildings.count { it.type.root == type.root }

    private fun services() {
        val s = c.stats
        val pop = s.population
        if (pop < 200) return
        // Every zoned block in reach of a fire station, police and a school, as the coverage maps show.
        cover(listOf(BuildingType.FIRE_STATION, BuildingType.VOLUNTEER_HALL), if (c.era >= Era.MOTOR) Balance.MOTOR_FIRE_REACH else Balance.FIRE_REACH,
            if (pop < 1500) BuildingType.VOLUNTEER_HALL else BuildingType.FIRE_STATION)
        cover(listOf(BuildingType.POLICE_STATION, BuildingType.POLICE_HQ), Balance.POLICE_REACH, BuildingType.POLICE_STATION)
        cover(listOf(BuildingType.SCHOOL), Balance.SCHOOL_REACH, BuildingType.SCHOOL, homesOnly = true)
        if (s.schoolPlaces < s.children * 6 / 10 && weeks % 8 == 0) place(BuildingType.SCHOOL)
        if (pop > 3000 && s.highSchoolPlaces < s.highSchoolPupils + 50 && weeks % 12 == 0) place(BuildingType.HIGH_SCHOOL)
        if (count(BuildingType.CLINIC) * 4000 < pop && count(BuildingType.HOSPITAL) == 0) place(BuildingType.CLINIC)
        if (pop > 12000 && count(BuildingType.HOSPITAL) * 30000 < pop) place(BuildingType.HOSPITAL)
        if (pop > 5000 && count(BuildingType.LIBRARY) * 15000 < pop) place(BuildingType.LIBRARY)
        // Rackets come in 1920: detectives at a headquarters, and courts and cells so justice is done.
        if (c.year >= Balance.RACKETS_YEAR && pop > 5000 && count(BuildingType.POLICE_HQ) == 0) place(BuildingType.POLICE_HQ)
        if (pop > 5000 && c.stats.justice < 80 && weeks % 26 == 0) {
            if (c.stats.heard < c.stats.arrests) place(BuildingType.COURTHOUSE) else place(BuildingType.JAIL)
        }
    }

    /** Puts a [build] by the first zoned block that no [types] reach, one a month. */
    private fun cover(types: List<BuildingType>, reach: Int, build: BuildingType, homesOnly: Boolean = false) {
        val have = c.allBuildings.filter { it.type.root in types }.map { it.x + it.type.width / 2 to it.y + it.type.height / 2 }
        var gap: Pair<Int, Int>? = null
        var best = Int.MAX_VALUE
        for (i in 1 until n - 1) for (j in 1 until n - 1) {
            if (use[i][j] != Use.ZONE || zoneOf[i][j] == Zone.FARMLAND) continue
            if (homesOnly && zoneOf[i][j] != Zone.RESIDENTIAL) continue
            val x = i * S + S / 2
            val y = j * S + S / 2
            if (have.any { (hx, hy) -> abs(hx - x) + abs(hy - y) <= reach }) continue
            val d = abs(i - centre) + abs(j - centre)
            if (d < best) {
                best = d
                gap = i to j
            }
        }
        gap?.let { place(build, near = it) }
    }

    /** Leisure near the homes: green space, sport and culture, each where none reaches, and the big draws as the town grows. */
    private fun leisure(wanted: Boolean) {
        val pop = c.stats.population
        if (pop < 1000 || weeks % 4 != 0) return
        val reach = if (wanted) 0 else -2
        val green = listOf(BuildingType.PLAYGROUND, BuildingType.POCKET_PARK, BuildingType.TOWN_SQUARE, BuildingType.FORMAL_GARDEN, BuildingType.CITY_PARK)
        cover(green, 6 + reach, if (c.allows(BuildingType.POCKET_PARK)) BuildingType.POCKET_PARK else BuildingType.PLAYGROUND, homesOnly = true)
        if (pop > 2500) cover(listOf(BuildingType.SPORTS_GROUND, BuildingType.REC_CENTRE, BuildingType.PUBLIC_BATHS), 8 + reach,
            if (c.allows(BuildingType.REC_CENTRE) && pop > 15000) BuildingType.REC_CENTRE else BuildingType.SPORTS_GROUND, homesOnly = true)
        if (pop > 4000) cover(listOf(BuildingType.VARIETY_THEATRE, BuildingType.MUSEUM, BuildingType.ART_GALLERY, BuildingType.BANDSTAND, BuildingType.OPERA_HOUSE), 10 + reach,
            if (pop > 8000) BuildingType.VARIETY_THEATRE else BuildingType.BANDSTAND, homesOnly = true)
        if (weeks % 26 != 0) return
        if (count(BuildingType.CITY_PARK) * 20_000 < pop - 8_000) place(BuildingType.CITY_PARK)
        if (pop > 15_000 && count(BuildingType.MUSEUM) == 0) place(BuildingType.MUSEUM)
        if (pop > 25_000 && count(BuildingType.PUBLIC_BATHS) * 25_000 < pop) place(BuildingType.PUBLIC_BATHS)
        if (pop > 40_000 && count(BuildingType.STADIUM) == 0) place(BuildingType.STADIUM)
    }

    /** A town hall once the town has laws worth saving on, and a post office per so many people. */
    private fun civic() {
        val pop = c.stats.population
        if (weeks % 26 != 0) return
        if (pop > 3000 && count(BuildingType.TOWN_HALL) == 0) place(BuildingType.TOWN_HALL)
        if (pop > 5000 && count(BuildingType.POST_OFFICE) * 15_000 < pop) place(BuildingType.POST_OFFICE)
    }

    /** The laws a careful player passes once they can, and drops when the money's gone. */
    private fun laws(kinds: Set<AdviceKind>) {
        if (weeks % 13 != 0) return
        val s = c.stats
        val want = listOf(
            Ordinance.BUILDING_CODE, Ordinance.PUBLIC_HEALTH_ACT, Ordinance.LIQUOR_LICENCES, Ordinance.TENEMENT_ACT, Ordinance.SCHOOL_MEALS,
            Ordinance.FLUORIDATION, Ordinance.DOG_LICENCES, Ordinance.PARKING_METERS, Ordinance.SPEED_LIMITS, Ordinance.CLEAN_AIR_ACT,
            Ordinance.BOTTLE_DEPOSIT, Ordinance.ENERGY_CODE, Ordinance.CURBSIDE_RECYCLING, Ordinance.HEAT_PLAN, Ordinance.SMOKING_BAN,
        )
        if (AdviceKind.DEBT in kinds || c.funds < 0) {
            Ordinance.entries.filter { c.passed(it) && it.perThousand > 0 }.maxByOrNull { it.perThousand }?.let {
                c.setOrdinance(it, false)
                note("repealed $it to save money")
            }
            return
        }
        if (s.income - s.upkeep < 0 || c.funds < 20_000) return
        want.firstOrNull { !c.passed(it) && c.allows(it) }?.let {
            c.setOrdinance(it, true)
            if (c.passed(it)) note("passed $it")
        }
    }

    private fun parks() {
        for (k in 0 until 3) place(BuildingType.PARK)
    }

    /** Rezones the most central shop block for high density, for the downtown. */
    private fun downtown() {
        if (weeks % 26 != 0) return
        var best: Pair<Int, Int>? = null
        for (i in 1 until n - 1) for (j in 1 until n - 1) {
            if (use[i][j] != Use.ZONE || zoneOf[i][j] != Zone.COMMERCIAL || densityOf[i][j] == Density.HIGH) continue
            if (best == null || score(Zone.COMMERCIAL, i, j) < score(Zone.COMMERCIAL, best.first, best.second)) best = i to j
        }
        val (i, j) = best ?: return
        if (act("downtown", Action.PlaceZone(i * S + 1, j * S + 1, i * S + S - 1, j * S + S - 1, Zone.COMMERCIAL, Density.HIGH))) {
            densityOf[i][j] = Density.HIGH
        }
    }

    private var widened = 0

    /** Widens the main street and the cross street through the middle, then a street further out each time. */
    private fun upgradeRoads() {
        val type = listOf(RoadType.AVENUE, RoadType.STREET).firstOrNull { c.allows(it) } ?: return
        val k = widened++
        val off = (k / 2 + 1) / 2 * (if (k / 2 % 2 == 0) 1 else -1) * 2 * S
        val lines = if (k == 0) listOf(Action.roadPath(m, 0, mainY, m.width - 1, mainY, true), Action.roadPath(m, centre * S, S, centre * S, m.height - S, false))
        else if (k % 2 == 0) listOf(Action.roadPath(m, 0, (mainY + off).coerceIn(S, m.height - S), m.width - 1, (mainY + off).coerceIn(S, m.height - S), true))
        else listOf(Action.roadPath(m, (centre * S + off).coerceIn(S, m.width - S), S, (centre * S + off).coerceIn(S, m.width - S), m.height - S, false))
        for (tiles in lines) {
            val built = tiles.filter { m.road[it] != Road.NONE }.toIntArray()
            if (built.isEmpty()) continue
            act("upgrade to $type", Action.BuildRoad(built, type, pipes = mainsUp))
        }
    }

    /** An exchange and lines along the streets once the town's big enough, and fibre when it comes. */
    private fun phones() {
        if (!phonesUp) {
            if (c.stats.population < 1500 || !c.allows(BuildingType.EXCHANGE)) return
            if (!place(BuildingType.EXCHANGE)) return
            phonesUp = true
            note("phones along ${segments.size} streets")
            for (s in segments) phone(s)
        } else if (!fibreUp && c.allowsFibre() && afford(segments.sumOf { it.size } * 30L, 20_000)) {
            fibreUp = true
            note("fibre along ${segments.size} streets")
            for (s in segments) c.apply(Action.BuildPhoneLine(s.filter { m.phone[it] != Phone.FIBRE && m.road[it] != Road.NONE }.toIntArray(), fibre = true))
        } else cover(listOf(BuildingType.EXCHANGE), Balance.PHONE_REACH, BuildingType.EXCHANGE)
    }

    /**
     * What a player does when the town plainly wants more of a zone but the
     * advice is about something else: zones more of it somewhere new.
     */
    private fun zoneOnDemand() {
        val s = c.stats
        for ((zone, demand, floor) in listOf(
            Triple(Zone.RESIDENTIAL, s.residentialDemand, 150), Triple(Zone.COMMERCIAL, s.commercialDemand, 120),
            Triple(Zone.INDUSTRIAL, s.industryDemand, 150), Triple(Zone.FARMLAND, s.farmDemand, 80), Triple(Zone.OFFICE, s.officeDemand, 120),
        )) {
            if (demand < floor || weeks - (lastZoned[zone] ?: -99) < 13) continue
            // Not while the advice says what's wrong is something the player has to build.
            val fixable = c.advice.any { it.zone == zone && it.kind in listOf(AdviceKind.NO_POWER, AdviceKind.NO_WATER, AdviceKind.NO_SEWER, AdviceKind.NO_ROAD) }
            if (fixable) continue
            // Businesses short of staff need more homes for their workers first.
            if (zone != Zone.RESIDENTIAL && c.advice.any { it.zone == zone && it.kind == AdviceKind.NO_STAFF }) continue
            zoneMore(zone)
        }
    }

    private fun avg(layer: ByteArray, zone: Byte? = null): Int {
        var sum = 0L
        var k = 0
        for (i in 0 until m.size) if (if (zone == null) m.zone[i] != Zone.NONE else m.zone[i] == zone) {
            sum += layer[i].toInt() and 0xff
            k++
        }
        return if (k == 0) 0 else (sum / k).toInt()
    }

    private fun avgCover(type: BuildingType, reach: Int): Int {
        val have = c.allBuildings.filter { it.type == type }
        var lots = 0
        var covered = 0
        for (i in 0 until m.size) if (m.zone[i] == Zone.RESIDENTIAL) {
            lots++
            val x = i % m.width
            val y = i / m.width
            if (have.any { abs(it.x - x) + abs(it.y - y) <= reach }) covered++
        }
        return if (lots == 0) 0 else covered * 100 / lots
    }

    /** How [zone]'s lots stand: empty or built, and what each would need to go up a rung. */
    private fun lotStates(zone: Byte) {
        val states = sortedMapOf<String, Int>()
        fun add(k: String) { states[k] = (states[k] ?: 0) + 1 }
        val seenB = HashSet<Int>()
        for (i in 0 until m.size) {
            if (m.zone[i] != zone) continue
            val b = c.buildingAt(i % m.width, i / m.width)
            if (b != null && !seenB.add(b.id)) continue
            if (b != null && b.underway > 0) { add("going up"); continue }
            if (b != null && b.type.zone != zone) { add("other zone's building"); continue }
            val height = m.density[i]
            val next = (if (b == null) BuildingType.rung(zone, 1) else b.type.next).filter { it.year <= c.year }
            if (next.isEmpty()) { add(if (b == null) "empty, nothing yet" else "top of ladder"); continue }
            val t = next.firstOrNull { Density.rank(it.density) <= Density.rank(height) && (it.density == Density.RURAL) == (height == Density.RURAL) }
            if (t == null) { add("as dense as zoned (${b?.type?.density})"); continue }
            val k = when {
                t.needsPower && !m.powered[i] -> "no power"
                t.needsWater && !m.watered[i] -> "no water"
                t.needsSewer && !m.sewered[i] -> "no sewer"
                c.attraction(i, zone) < c.appealNeeded(t) -> "short of appeal"
                (m.landValue[i].toInt() and 0xff) < t.value -> "short of land value"
                t.large -> "could, if a block assembles"
                else -> "could grow"
            }
            add((if (b == null) "empty: " else "built: ") + k)
        }
        note("lots of $zone: " + states.entries.joinToString { "${it.key} ${it.value}" })
    }

    /** Why lots of [zone] that could grow but for their appeal don't, on average. */
    private fun whyUnappealing(zone: Byte) {
        var lots = 0
        var pull = 0L
        var needPull = 0L
        var value = 0L
        var needValue = 0L
        var short = 0
        var cheap = 0
        var crime = 0L
        var pollution = 0L
        var noise = 0L
        var commute = 0L
        var noJobs = 0
        for (i in 0 until m.size) {
            if (m.zone[i] != zone) continue
            val b = c.buildingAt(i % m.width, i / m.width)
            if (b != null && (b.underway > 0 || b.type.zone != zone)) continue
            val height = m.density[i]
            val t = (if (b == null) BuildingType.rung(zone, 1) else b.type.next).firstOrNull {
                it.year <= c.year && Density.rank(it.density) <= Density.rank(height) && (it.density == Density.RURAL) == (height == Density.RURAL)
            } ?: continue
            if ((t.needsPower && !m.powered[i]) || (t.needsWater && !m.watered[i]) || (t.needsSewer && !m.sewered[i])) continue
            val p = c.attraction(i, zone)
            val v = m.landValue[i].toInt() and 0xff
            if (p >= c.appealNeeded(t) && v >= t.value) continue
            lots++
            pull += p
            needPull += c.appealNeeded(t)
            value += v
            needValue += t.value
            if (p < c.appealNeeded(t)) short++
            if (v < t.value) cheap++
            crime += m.crime[i].toInt() and 0xff
            pollution += m.pollution[i].toInt() and 0xff
            noise += m.noise[i].toInt() and 0xff
            val cm = m.commute[i].toInt() and 0xff
            if (cm == 255) noJobs++ else commute += cm
        }
        if (lots == 0) return
        note("unappealing $zone: $lots lots, appeal ${pull / lots} of ${needPull / lots} needed ($short short), " +
            "land value ${value / lots} of ${needValue / lots} needed ($cheap short); crime ${crime / lots}, pollution ${pollution / lots}, " +
            "noise ${noise / lots}, commute ${commute / max(1, lots - noJobs)}, no job in reach $noJobs")
    }

    // ---- the record ----

    fun row(): String {
        val s = c.stats
        val blocks = IntArray(Zone.COUNT)
        for (i in 0 until n) for (j in 0 until n) if (use[i][j] == Use.ZONE) blocks[zoneOf[i][j].toInt()]++
        val advice = c.advice.joinToString(" ") { it.kind.name + (if (it.zone != Zone.NONE) "/${it.zone}" else "") }
        val next = c.era.next?.let { e -> c.goals(e).filter { !it.met }.joinToString(" ") { "${it.kind}:${it.have}/${it.need}" } } ?: ""
        return listOf(
            c.year, c.era, s.population, s.shopJobs, s.industryJobs, s.farmJobs, s.officeJobs, s.workers,
            c.funds, s.income, s.upkeep, s.residentialDemand, s.commercialDemand, s.industryDemand, s.farmDemand, s.officeDemand,
            c.residentialTax, s.emptyHomes, s.health, s.onMains, s.onSewer, s.powered,
            blocks[Zone.RESIDENTIAL.toInt()], blocks[Zone.COMMERCIAL.toInt()], blocks[Zone.INDUSTRIAL.toInt()], blocks[Zone.FARMLAND.toInt()], blocks[Zone.OFFICE.toInt()],
            c.buildingCount, s.leisure, avg(m.crime, Zone.RESIDENTIAL), Ordinance.entries.count { c.passed(it) }, "\"$advice\"", "\"$next\"",
            c.approval, Opinion.worst(c.era, c.concerns), s.pricedOut, s.roughSleepers, s.sheltered, c.drought, s.births, s.deaths,
            s.residentialIncome, s.commercialIncome, s.industrialIncome, s.officeIncome, s.fareIncome + s.duesIncome + s.tollIncome, s.tradeIncome,
            s.roadUpkeep + s.railUpkeep, s.waterUpkeep, s.powerUpkeep, s.policeUpkeep + s.fireUpkeep, s.parkUpkeep, s.schoolUpkeep + s.healthUpkeep,
            s.transitUpkeep, s.repairCost, s.environmentUpkeep, s.civicUpkeep, s.ordinanceCost, s.landBuilt, "\"${c.concerns.joinToString(" ")}\"", c.petitions.size, c.grant?.kind ?: "",
        ).joinToString(",")
    }

    companion object {
        const val S = 8
        const val RICH_MONTHS = 36
        const val LOW_TAX = 4
        const val HEADER = "year,era,population,shopJobs,industryJobs,farmJobs,officeJobs,workers,funds,income,upkeep," +
            "demandR,demandC,demandI,demandF,demandO,tax,emptyHomes,health,onMains,onSewer,powered,blocksR,blocksC,blocksI,blocksF,blocksO,buildings,leisure,homeCrime,laws,advice,goals,approval,worst,pricedOut,rough,sheltered,drought,births,deaths,inHomes,inShops,inWorks,inOffices,inFares,inTrade,upRoads,upWater,upPower,upSafety,upParks,upSchoolHealth,upTransit,upRepairs,upEnv,upCivic,upLaws,landBuilt,concerns,petitions,grant"
    }
}
