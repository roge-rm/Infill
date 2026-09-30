package com.rm.infill.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * One city: its map, its buildings, its money and the date. The UI changes it
 * only through [plan] and [apply] (and [undo], [redo]), and time moves on one
 * day at a time with [tick].
 */
class City(
    val seed: Long,
    width: Int = DEFAULT_SIZE,
    height: Int = DEFAULT_SIZE,
    terrain: TerrainOptions = TerrainOptions(),
) {
    val map = CityMap(width, height).also { TerrainGen.generate(it, seed, terrain) }
    val rng = Rng(seed)
    val weather = Weather(seed)

    /** Whole dollars. */
    var funds: Long = START_FUNDS
        private set

    var year: Int = START_YEAR
        private set

    /** 0 is January. */
    var month: Int = 0
        private set

    /** 1 is the first of the month. */
    var day: Int = 1
        private set

    /** Buildings by id, in the order they were built. */
    private val buildings = LinkedHashMap<Int, Building>()
    private var nextId = 1

    fun building(id: Int): Building? = buildings[id]
    fun buildingAt(x: Int, y: Int): Building? = buildings[map.buildingAt(x, y)]
    val buildingCount get() = buildings.size

    /** Tax rates in percent. */
    var residentialTax = Balance.DEFAULT_TAX
    var commercialTax = Balance.DEFAULT_TAX
    var industrialTax = Balance.DEFAULT_TAX

    /** Last month's census, demand and money. */
    val stats = Stats()

    /** Tiles the town changed since the UI last asked, as map indices. */
    private val townChanges = ArrayList<Int>()

    fun takeTownChanges(each: (Int) -> Unit) {
        for (i in townChanges) each(i)
        townChanges.clear()
    }

    // ---- actions -------------------------------------------------------------

    /** What [action] would do now, without doing it. */
    fun plan(action: Action): Plan {
        val changes = ArrayList<Int>()
        val blocked = ArrayList<Int>()
        var cost = 0L
        val m = map
        when (action) {
            is Action.BuildRoad -> for (i in action.tiles) {
                when {
                    !inMap(i) -> {}
                    m.terrain[i] == Terrain.WATER || m.building[i] != 0 -> blocked += i
                    m.road[i] != Road.NONE -> {}
                    else -> {
                        changes += i
                        cost += Prices.DIRT_ROAD + clearing(i)
                    }
                }
            }
            is Action.BuildPowerLine -> for (i in action.tiles) {
                when {
                    !inMap(i) -> {}
                    m.terrain[i] == Terrain.WATER || m.building[i] != 0 || m.zone[i] != Zone.NONE -> blocked += i
                    m.power[i] != Power.NONE -> {}
                    else -> {
                        changes += i
                        cost += Prices.POWER_LINE + clearing(i)
                    }
                }
            }
            is Action.PlaceZone -> forRect(action.x0, action.y0, action.x1, action.y1) { i ->
                when {
                    m.terrain[i] == Terrain.WATER || m.road[i] != Road.NONE || m.power[i] != Power.NONE -> blocked += i
                    m.zone[i] == action.zone -> {}
                    m.building[i] != 0 -> blocked += i
                    else -> {
                        changes += i
                        cost += Prices.ZONE
                    }
                }
            }
            is Action.PlaceBuilding -> {
                val t = action.type
                var ok = action.x >= 0 && action.y >= 0 && action.x + t.width <= m.width && action.y + t.height <= m.height
                if (ok) forRect(action.x, action.y, action.x + t.width - 1, action.y + t.height - 1) { i ->
                    if (m.terrain[i] == Terrain.WATER || m.road[i] != Road.NONE || m.power[i] != Power.NONE ||
                        m.zone[i] != Zone.NONE || m.building[i] != 0
                    ) {
                        blocked += i
                        ok = false
                    }
                    changes += i
                    cost += clearing(i)
                }
                if (!ok) {
                    changes.clear()
                    cost = 0
                } else {
                    cost += Prices.of(t)
                }
            }
            is Action.Bulldoze -> {
                val seen = HashSet<Int>()
                forRect(action.x0, action.y0, action.x1, action.y1) { i ->
                    val b = buildings[m.building[i]]
                    if (b != null) {
                        // A building goes as a whole, even the parts outside the rectangle.
                        if (seen.add(b.id)) {
                            forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { j ->
                                changes += j
                                cost += Prices.DEMOLISH
                            }
                        }
                        return@forRect
                    }
                    var c = 0L
                    if (m.road[i] != Road.NONE) c += Prices.REMOVE_ROAD
                    if (m.power[i] != Power.NONE) c += Prices.REMOVE_LINE
                    if (m.terrain[i] == Terrain.TREES) c += Prices.CLEAR_TREES
                    if (c > 0 || m.zone[i] != Zone.NONE) {
                        changes += i
                        cost += c
                    }
                }
            }
        }
        val problem = when {
            action is Action.PlaceBuilding && blocked.isNotEmpty() -> Problem.Blocked
            changes.isEmpty() -> Problem.NothingToDo
            cost > funds -> Problem.NotEnoughMoney
            else -> null
        }
        return Plan(cost, changes.toIntArray(), blocked.toIntArray(), problem)
    }

    private fun clearing(i: Int) = if (map.terrain[i] == Terrain.TREES) Prices.CLEAR_TREES else 0L

    /**
     * What an action changed, tile by tile, and the buildings it put up or took
     * down, so it can be undone and done again.
     */
    private class Edit(
        val tiles: IntArray,
        val before: LongArray,
        val after: LongArray,
        val cost: Long,
        val added: List<Building>,
        val removed: List<Building>,
    )

    private val undoable = ArrayDeque<Edit>()
    private val redoable = ArrayDeque<Edit>()

    val canUndo get() = undoable.isNotEmpty()
    val canRedo get() = redoable.isNotEmpty()

    /** Does [action] if it can, all of it or none of it. The plan says what happened. */
    fun apply(action: Action): Plan {
        val plan = plan(action)
        if (!plan.ok) return plan
        val m = map
        val before = LongArray(plan.changes.size) { m.tileState(plan.changes[it]) }
        val added = ArrayList<Building>()
        val removed = ArrayList<Building>()
        when (action) {
            is Action.BuildRoad -> for (i in plan.changes) {
                m.road[i] = Road.DIRT
                m.zone[i] = Zone.NONE
                clearTrees(i)
            }
            is Action.BuildPowerLine -> for (i in plan.changes) {
                m.power[i] = Power.LINE
                clearTrees(i)
            }
            is Action.PlaceZone -> for (i in plan.changes) m.zone[i] = action.zone
            is Action.PlaceBuilding -> {
                for (i in plan.changes) clearTrees(i)
                added += addBuilding(action.type, action.x, action.y, rng.nextInt(1000))
            }
            is Action.Bulldoze -> for (i in plan.changes) {
                buildings[m.building[i]]?.let { removed += it; removeBuilding(it) }
                m.road[i] = Road.NONE
                m.zone[i] = Zone.NONE
                m.power[i] = Power.NONE
                clearTrees(i)
            }
        }
        funds -= plan.cost
        undoable.addLast(Edit(plan.changes, before, LongArray(plan.changes.size) { m.tileState(plan.changes[it]) }, plan.cost, added, removed))
        if (undoable.size > MAX_UNDO) undoable.removeFirst()
        redoable.clear()
        networksChanged()
        zonesChanged = true
        return plan
    }

    /**
     * Puts back what the last action changed and gives its money back. It can't
     * if the town has built over those tiles since, and then the history goes.
     * Null if there's nothing to undo.
     */
    fun undo(): Plan? {
        val e = undoable.lastOrNull() ?: return null
        if (e.tiles.indices.any { map.tileState(e.tiles[it]) != e.after[it] }) {
            undoable.clear()
            redoable.clear()
            return Plan(0, IntArray(0), IntArray(0), Problem.TownBuiltThere)
        }
        undoable.removeLast()
        for (b in e.added) buildings.remove(b.id)
        for (b in e.removed) buildings[b.id] = b
        for (k in e.tiles.indices) map.setTileState(e.tiles[k], e.before[k])
        restamp(e.tiles)
        funds += e.cost
        redoable.addLast(e)
        networksChanged()
        zonesChanged = true
        return Plan(-e.cost, e.tiles, IntArray(0), null)
    }

    /** Does the last undone action again, if there's still the money. Null if there's nothing to redo. */
    fun redo(): Plan? {
        val e = redoable.lastOrNull() ?: return null
        if (e.cost > funds) return Plan(e.cost, IntArray(0), IntArray(0), Problem.NotEnoughMoney)
        if (e.tiles.indices.any { map.tileState(e.tiles[it]) != e.before[it] }) {
            redoable.clear()
            return Plan(0, IntArray(0), IntArray(0), Problem.TownBuiltThere)
        }
        redoable.removeLast()
        for (b in e.removed) buildings.remove(b.id)
        for (b in e.added) buildings[b.id] = b
        for (k in e.tiles.indices) map.setTileState(e.tiles[k], e.after[k])
        restamp(e.tiles)
        funds -= e.cost
        undoable.addLast(e)
        networksChanged()
        zonesChanged = true
        return Plan(e.cost, e.tiles, IntArray(0), null)
    }

    private fun clearTrees(i: Int) {
        if (map.terrain[i] == Terrain.TREES) map.terrain[i] = Terrain.GRASS
    }

    private fun addBuilding(type: BuildingType, x: Int, y: Int, variant: Int): Building {
        val b = Building(nextId++, type, x, y, variant)
        buildings[b.id] = b
        stamp(b)
        return b
    }

    private fun removeBuilding(b: Building) {
        buildings.remove(b.id)
        forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { i ->
            map.building[i] = 0
            map.buildingType[i] = 0
            map.buildingVariant[i] = 0
        }
    }

    /** Writes a building onto its tiles, for the map to draw. */
    private fun stamp(b: Building) {
        forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { i ->
            map.building[i] = b.id
            map.buildingType[i] = (b.type.ordinal + 1).toByte()
            map.buildingVariant[i] = b.variant.toByte()
        }
    }

    /** After an undo or redo has put tiles back, brings their type and variant into line. */
    private fun restamp(tiles: IntArray) {
        for (i in tiles) {
            val b = buildings[map.building[i]]
            map.buildingType[i] = if (b == null) 0 else (b.type.ordinal + 1).toByte()
            map.buildingVariant[i] = if (b == null) 0 else b.variant.toByte()
        }
    }

    // ---- time ----------------------------------------------------------------

    /** Moves on a day: growth every day, and the census, demand, money and grime on the first of each month. */
    fun tick() {
        if (networksDirty) updateNetworks()
        for (b in buildings.values) b.age++
        growDay()
        weather.nextDay(month, day, daysIn(month, year))
        day++
        if (day > daysIn(month, year)) {
            day = 1
            month++
            if (month == 12) {
                month = 0
                year++
            }
            newMonth()
        }
    }

    private fun newMonth() {
        // Undo is for slips of the finger, not for getting a month's use of something back.
        undoable.clear()
        redoable.clear()
        if (networksDirty) updateNetworks()
        powerCuts()
        updatePollution()
        updateGrime()
        census()
        demand()
        money()
    }

    // ---- networks ------------------------------------------------------------

    private var networksDirty = true

    /** Tiles within reach of a road, where zoned land can grow. */
    private val nearRoad = BooleanArray(map.size)

    /** A road reaches the edge of the map, so the town can trade with the outside. */
    var connected = false
        private set

    private fun networksChanged() {
        networksDirty = true
    }

    private fun updateNetworks() {
        networksDirty = false
        val m = map
        val r = Balance.ROAD_REACH
        nearRoad.fill(false)
        connected = false
        for (y in 0 until m.height) for (x in 0 until m.width) {
            if (m.road[m.index(x, y)] == Road.NONE) continue
            if (x == 0 || y == 0 || x == m.width - 1 || y == m.height - 1) connected = true
            for (dy in -r..r) for (dx in -r..r) {
                if (abs(dx) + abs(dy) > r || !m.inside(x + dx, y + dy)) continue
                nearRoad[m.index(x + dx, y + dy)] = true
            }
        }
        // Power spreads from the power stations along lines, through buildings and
        // across zoned land, so a line along the back of a zone powers all of it.
        val powered = m.powered
        powered.fill(false)
        val queue = IntArray(m.size)
        var head = 0
        var tail = 0
        for (b in buildings.values) {
            if (b.type != BuildingType.COAL_PLANT) continue
            forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { i ->
                if (!powered[i]) {
                    powered[i] = true
                    queue[tail++] = i
                }
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
                if (powered[j] || (m.power[j] == Power.NONE && m.building[j] == 0 && m.zone[j] == Zone.NONE)) continue
                powered[j] = true
                queue[tail++] = j
            }
        }
    }

    // ---- growth --------------------------------------------------------------

    /** How much each zone may still grow (or has to shrink) this month, in residents or jobs. */
    private val quota = IntArray(4)

    private fun growDay() {
        val left = daysIn(month, year) - day + 1
        for (zone in 1..3) {
            var events = 0
            val most = 1 + abs(quota[zone]) / (left * 6)
            while (quota[zone] != 0 && events < most) {
                val moved = if (quota[zone] > 0) growOnce(zone.toByte()) else shrinkOnce(zone.toByte())
                if (moved == 0) {
                    // Nowhere to grow or shrink today.
                    break
                }
                quota[zone] = if (quota[zone] > 0) max(0, quota[zone] - moved) else min(0, quota[zone] + moved)
                events++
            }
        }
    }

    /** Grows one building a stage, or puts up a new one, on the best of a few lots. Returns the capacity it added. */
    private fun growOnce(zone: Byte): Int {
        val lots = zoneLots(zone)
        if (lots.isEmpty()) return 0
        var best = -1
        var bestScore = Int.MIN_VALUE
        repeat(Balance.CANDIDATES) {
            val i = lots[rng.nextInt(lots.size)]
            if (!nearRoad[i]) return@repeat
            val b = buildings[map.building[i]]
            val next = if (b == null) BuildingType.firstFor(zone) else b.type.next
            if (next == null) return@repeat
            if (b != null && b.age < Balance.SETTLE_DAYS) return@repeat
            if (next.needsPower && !map.powered[i]) return@repeat
            val pull = attraction(i, zone)
            if (pull < Balance.STAGE_ATTRACTION[zone - 1][next.stage]) return@repeat
            val score = pull + rng.nextInt(10) + if (b != null) 4 else 0
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        if (best < 0) return 0
        val b = buildings[map.building[best]]
        val added: Int
        if (b == null) {
            clearTrees(best)
            val type = BuildingType.firstFor(zone)
            addBuilding(type, best % map.width, best / map.width, rng.nextInt(1000))
            added = type.capacity
            networksChanged()
        } else {
            val next = b.type.next!!
            added = next.capacity - b.type.capacity
            b.type = next
            b.age = 0
            stamp(b)
        }
        townChanges += best
        return added
    }

    /** Shrinks the least attractive building a stage, or clears its lot. Returns the capacity it took away. */
    private fun shrinkOnce(zone: Byte): Int {
        val lots = zoneLots(zone)
        var worst = -1
        var worstScore = Int.MAX_VALUE
        repeat(Balance.CANDIDATES) {
            if (lots.isEmpty()) return@repeat
            val i = lots[rng.nextInt(lots.size)]
            if (map.building[i] == 0) return@repeat
            val score = attraction(i, zone)
            if (score < worstScore) {
                worstScore = score
                worst = i
            }
        }
        if (worst < 0) return 0
        return shrink(buildings[map.building[worst]]!!)
    }

    private fun shrink(b: Building): Int {
        val previous = b.type.previous
        val removed: Int
        if (previous == null) {
            removed = b.type.capacity
            removeBuilding(b)
            networksChanged()
        } else {
            removed = b.type.capacity - previous.capacity
            b.type = previous
            b.age = 0
            stamp(b)
        }
        townChanges += map.index(b.x, b.y)
        return removed
    }

    /** Buildings that need power and have lost it come down a stage now and then. */
    private fun powerCuts() {
        val out = buildings.values.filter { it.type.needsPower && !map.powered[map.index(it.x, it.y)] }
        for (b in out) if (rng.nextInt(3) == 0) shrink(b)
    }

    private val lotCache = arrayOfNulls<IntArray>(4)

    /** Set when the player changes the map, since only the player changes zones. */
    private var zonesChanged = true

    /** Every tile zoned [zone], worked out again after the player changes the map. */
    private fun zoneLots(zone: Byte): IntArray {
        if (zonesChanged) {
            zonesChanged = false
            for (z in 1..3) lotCache[z] = null
        }
        return lotCache[zone.toInt()] ?: run {
            val list = ArrayList<Int>()
            for (i in 0 until map.size) if (map.zone[i] == zone) list += i
            list.toIntArray().also { lotCache[zone.toInt()] = it }
        }
    }

    /**
     * How much a zone wants a lot, roughly 0 to 100: power, water and trees
     * nearby, and for homes, shops within reach and no industry or pollution;
     * for shops, people nearby.
     */
    fun attraction(i: Int, zone: Byte): Int {
        val m = map
        val x = i % m.width
        val y = i / m.width
        var score = 50
        if (m.powered[i]) score += 10
        val pollution = m.pollution[i].toInt() and 0xff
        when (zone) {
            Zone.RESIDENTIAL -> {
                var trees = 0
                var water = false
                var industry = false
                var shops = 0
                around(x, y, 6) { j, d ->
                    val b = buildings[m.building[j]]
                    if (d <= 2) {
                        if (m.terrain[j] == Terrain.TREES) trees++
                        if (b != null && b.type.zone == Zone.INDUSTRIAL) industry = true
                    }
                    if (d <= 3 && m.terrain[j] == Terrain.WATER) water = true
                    if (b != null && b.type.zone == Zone.COMMERCIAL) shops += b.type.capacity
                }
                score += min(trees, 8) + (if (water) 6 else 0) - (if (industry) 10 else 0) + min(shops / 3, 10)
                score -= pollution / 2
            }
            Zone.COMMERCIAL -> {
                score -= 10
                var people = 0
                around(x, y, 6) { j, _ ->
                    val b = buildings[m.building[j]]
                    if (b != null && b.type.zone == Zone.RESIDENTIAL) people += b.type.capacity
                }
                score += min(people / 8, 30) - pollution / 4
            }
            Zone.INDUSTRIAL -> {
                var water = false
                around(x, y, 3) { j, _ -> if (m.terrain[j] == Terrain.WATER) water = true }
                if (water) score += 5
            }
        }
        return score
    }

    private inline fun around(x: Int, y: Int, r: Int, each: (Int, Int) -> Unit) {
        for (dy in -r..r) for (dx in -r..r) {
            val d = abs(dx) + abs(dy)
            if (d > r) continue
            val nx = x + dx
            val ny = y + dy
            if (nx < 0 || ny < 0 || nx >= map.width || ny >= map.height) continue
            each(ny * map.width + nx, d)
        }
    }

    // ---- the month -----------------------------------------------------------

    /** Pollution spreads from each building that makes it, fading with distance. */
    private fun updatePollution() {
        val m = map
        val field = IntArray(m.size)
        for (b in buildings.values) {
            val p = b.type.pollution
            if (p == 0) continue
            val cx = b.x + b.type.width / 2
            val cy = b.y + b.type.height / 2
            around(cx, cy, POLLUTION_REACH) { j, d -> field[j] += p * 4 * (POLLUTION_REACH + 1 - d) / (POLLUTION_REACH + 1) }
        }
        for (i in 0 until m.size) m.pollution[i] = min(255, field[i]).toByte()
    }

    /**
     * Grime follows pollution: up by a sixth of the gap each month, down by a
     * fortieth, so soot builds over months and takes years to wash away.
     */
    private fun updateGrime() {
        val m = map
        for (i in 0 until m.size) {
            val g = m.grime[i].toInt() and 0xff
            val p = m.pollution[i].toInt() and 0xff
            val next = when {
                p > g -> g + max(1, (p - g) / 6)
                p < g -> g - max(1, (g - p) / 40)
                else -> g
            }
            if (next == g) continue
            val before = m.grimeLevel(i)
            m.grime[i] = next.toByte()
            if (m.grimeLevel(i) != before) townChanges += i
        }
    }

    private fun census() {
        var residents = 0
        var shopJobs = 0
        var industryJobs = 0
        var otherJobs = 0
        for (b in buildings.values) {
            when (b.type.zone) {
                Zone.RESIDENTIAL -> residents += b.type.capacity
                Zone.COMMERCIAL -> shopJobs += b.type.capacity
                Zone.INDUSTRIAL -> industryJobs += b.type.capacity
                else -> otherJobs += b.type.capacity
            }
        }
        stats.population = residents
        stats.shopJobs = shopJobs
        stats.industryJobs = industryJobs
        stats.otherJobs = otherJobs
        stats.workers = (residents * Balance.LABOUR_SHARE).toInt()
    }

    /**
     * Level 1 demand. Industry answers the outside market, shops answer the
     * town's people, and homes answer the jobs plus a trickle of settlers.
     * Taxes above or below the default move each one.
     */
    private fun demand() {
        val s = stats
        val years = year - START_YEAR
        val market = (Balance.EXPORT_BASE + Balance.EXPORT_PER_RESIDENT * s.population) *
            (1 + Balance.EXPORT_GROWTH * years) * (if (connected) 1.0 else Balance.UNCONNECTED_EXPORTS)
        val jobs = s.shopJobs + s.industryJobs + s.otherJobs
        s.industryDemand = taxed(market - s.industryJobs, industrialTax)
        s.commercialDemand = taxed(s.population / Balance.RESIDENTS_PER_SHOP_JOB - s.shopJobs, commercialTax)
        val settlers = Balance.SETTLERS + Balance.SETTLERS_PER_RESIDENT * s.population
        s.residentialDemand = taxed(jobs / Balance.LABOUR_SHARE + settlers - s.population, residentialTax)
        quota[Zone.RESIDENTIAL.toInt()] = cap(s.residentialDemand, s.population)
        quota[Zone.COMMERCIAL.toInt()] = cap(s.commercialDemand, s.shopJobs)
        quota[Zone.INDUSTRIAL.toInt()] = cap(s.industryDemand, s.industryJobs)
    }

    private fun taxed(gap: Double, tax: Int): Int {
        val factor = (1 + (Balance.DEFAULT_TAX - tax) * Balance.TAX_DEMAND).coerceIn(0.1, 2.0)
        // A tax cut can't make the town want less, only more of what it wants.
        return (if (gap > 0) gap * factor else gap / factor).toInt()
    }

    private fun cap(demand: Int, current: Int): Int {
        val most = max(Balance.GROWTH_FLOOR, (current * Balance.GROWTH_SHARE).toInt())
        return demand.coerceIn(-most, most)
    }

    private fun money() {
        val s = stats
        val income = s.population * residentialTax * Balance.RESIDENT_TAX +
            (s.shopJobs * commercialTax + s.industryJobs * industrialTax) * Balance.JOB_TAX
        var roads = 0
        var lines = 0
        for (i in 0 until map.size) {
            if (map.road[i] != Road.NONE) roads++
            if (map.power[i] != Power.NONE) lines++
        }
        val plants = buildings.values.count { it.type == BuildingType.COAL_PLANT }
        val upkeep = roads * Balance.ROAD_UPKEEP + lines * Balance.LINE_UPKEEP + plants * Balance.PLANT_UPKEEP
        s.income = income.roundToLong()
        s.upkeep = upkeep.roundToLong()
        funds += s.income - s.upkeep
    }

    private fun inMap(i: Int) = i in 0 until map.size

    private inline fun forRect(x0: Int, y0: Int, x1: Int, y1: Int, action: (Int) -> Unit) {
        val left = maxOf(0, minOf(x0, x1))
        val right = minOf(map.width - 1, maxOf(x0, x1))
        val top = maxOf(0, minOf(y0, y1))
        val bottom = minOf(map.height - 1, maxOf(y0, y1))
        for (y in top..bottom) for (x in left..right) action(map.index(x, y))
    }

    companion object {
        const val DEFAULT_SIZE = 128
        const val START_YEAR = 1900
        const val START_FUNDS = 20_000L

        /** How many actions can be undone. */
        const val MAX_UNDO = 100

        /** How far pollution spreads, in tiles. */
        const val POLLUTION_REACH = 5

        private val DX = intArrayOf(0, 1, 0, -1)
        private val DY = intArrayOf(-1, 0, 1, 0)

        fun daysIn(month: Int, year: Int): Int = when (month) {
            1 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
            3, 5, 8, 10 -> 30
            else -> 31
        }
    }
}

/** The town's numbers as of the start of the month. Demand is in residents or jobs, negative when there's too much. */
class Stats {
    var population = 0
    var workers = 0
    var shopJobs = 0
    var industryJobs = 0
    var otherJobs = 0
    var residentialDemand = 0
    var commercialDemand = 0
    var industryDemand = 0
    var income = 0L
    var upkeep = 0L

    val jobs get() = shopJobs + industryJobs + otherJobs
}
