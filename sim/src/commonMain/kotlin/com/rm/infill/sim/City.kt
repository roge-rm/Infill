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
    terrain: TerrainOptions? = TerrainOptions(),
) {
    /** Blank when [terrain] is null, for a city being loaded. */
    val map = CityMap(width, height).also { if (terrain != null) TerrainGen.generate(it, seed, terrain) }

    /** What the player calls the town. */
    var name = "New town"
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

    /** How much of what each service asks for it gets, in percent. Less money, less reach. */
    var policeFunding = 100
    var fireFunding = 100
    var parkFunding = 100

    /** The town month by month, for the graphs. */
    val history = History()

    /** Things that happened that the player should hear about, since the UI last asked. */
    private val events = ArrayList<CityEvent>()

    fun takeEvents(each: (CityEvent) -> Unit) {
        for (e in events) each(e)
        events.clear()
    }

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
            is Action.BuildRoad -> {
                val layout = roadLayout(action)
                val type = action.type
                for (k in layout.tiles.indices) {
                    val i = layout.tiles[k]
                    val run = layout.runs[k].toInt()
                    val old = RoadType.of(m.road[i])
                    val bridge = if (m.terrain[i] == Terrain.WATER) Prices.BRIDGE else 1
                    when {
                        m.building[i] != 0 -> blocked += i
                        bridge > 1 && (!type.bridges || layout.turns[k]) -> blocked += i
                        old == null -> {
                            changes += i
                            cost += type.price * bridge + clearing(i)
                        }
                        old == type && (layout.headings[k] == m.roadHeading[i] || across(run, m.roadHeading[i].toInt())) -> {}
                        // A road drawn across a better one leaves the crossing as it is.
                        old.capacity > type.capacity && crossing(i, run) -> {}
                        else -> {
                            changes += i
                            cost += max(Prices.REMOVE_ROAD, type.price - old.price) * bridge
                        }
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
            is Action.PlaceParks -> forRect(action.x0, action.y0, action.x1, action.y1) { i ->
                if (m.terrain[i] == Terrain.WATER || m.road[i] != Road.NONE || m.power[i] != Power.NONE ||
                    m.zone[i] != Zone.NONE || m.building[i] != 0
                ) {
                    blocked += i
                } else {
                    changes += i
                    cost += Prices.PARK
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
                    if (m.road[i] != Road.NONE) c += Prices.REMOVE_ROAD * if (m.terrain[i] == Terrain.WATER) Prices.BRIDGE else 1
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
     * The tiles a road goes on, the way it was drawn through each, the heading
     * each gets (only one-way roads have one) and whether it turns there.
     */
    private class RoadLayout(val tiles: IntArray, val runs: ByteArray, val headings: ByteArray, val turns: BooleanArray)

    /**
     * Where a drawn road goes. Each tile runs the way the drawing went through
     * it, if the road is one-way. A two-wide road is drawn along its right-hand
     * carriageway, stops at the first turn, and gets the other carriageway on
     * its left running back the other way.
     */
    private fun roadLayout(action: Action.BuildRoad): RoadLayout {
        val path = action.tiles.filter { inMap(it) }
        val w = map.width
        fun step(k: Int): Byte {
            if (path.size < 2) return Heading.BOTH
            val a = path[max(0, k - 1)]
            val b = path[max(1, k)]
            return Heading.of(b % w - a % w, b / w - a / w)
        }
        val tiles = ArrayList<Int>()
        val runs = ArrayList<Byte>()
        val headings = ArrayList<Byte>()
        val turns = ArrayList<Boolean>()
        val type = action.type
        for (k in path.indices) {
            val h = step(k)
            if (type.width == 2 && h != step(0)) break
            tiles += path[k]
            runs += h
            headings += if (type.oneWay) h else Heading.BOTH
            turns += type.width == 1 && k + 1 < path.size && step(k + 1) != h
        }
        if (type.width == 2) {
            val n = tiles.size
            if (path.size < 2) return RoadLayout(IntArray(0), ByteArray(0), ByteArray(0), BooleanArray(0))
            for (k in 0 until n) {
                val h = runs[k].toInt()
                // To the left of the way it runs.
                val x = tiles[k] % w + Heading.DY[h]
                val y = tiles[k] / w - Heading.DX[h]
                if (!map.inside(x, y)) continue
                tiles += map.index(x, y)
                runs += Heading.opposite(h).toByte()
                headings += Heading.opposite(h).toByte()
                turns += false
            }
        }
        return RoadLayout(tiles.toIntArray(), runs.toByteArray(), headings.toByteArray(), turns.toBooleanArray())
    }

    /** Whether two headings run at right angles. */
    private fun across(a: Int, b: Int) = a != 0 && b != 0 && (a + b) % 2 == 1

    /** Whether the tile is part of a road running across [heading]: road on both sides of it. */
    private fun crossing(i: Int, heading: Int): Boolean {
        if (heading == 0) return false
        val side = heading % 4 + 1
        val x = i % map.width
        val y = i / map.width
        fun road(dx: Int, dy: Int) = map.inside(x + dx, y + dy) && map.road[map.index(x + dx, y + dy)] != Road.NONE
        return road(Heading.DX[side], Heading.DY[side]) && road(-Heading.DX[side], -Heading.DY[side])
    }

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
            is Action.BuildRoad -> {
                val layout = roadLayout(action)
                val heading = HashMap<Int, Byte>()
                for (k in layout.tiles.indices) heading[layout.tiles[k]] = layout.headings[k]
                for (i in plan.changes) {
                    m.road[i] = action.type.id
                    m.roadHeading[i] = heading[i] ?: Heading.BOTH
                    m.zone[i] = Zone.NONE
                    clearTrees(i)
                }
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
            is Action.PlaceParks -> for (i in plan.changes) {
                clearTrees(i)
                added += addBuilding(BuildingType.PARK, i % m.width, i / m.width, rng.nextInt(1000))
            }
            is Action.Bulldoze -> for (i in plan.changes) {
                buildings[m.building[i]]?.let { removed += it; removeBuilding(it) }
                m.road[i] = Road.NONE
                m.roadHeading[i] = Heading.BOTH
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
            map.fire[i] = 0
        }
    }

    /** Writes a building onto its tiles, for the map to draw. */
    private fun stamp(b: Building) {
        forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { i ->
            map.building[i] = b.id
            map.buildingType[i] = (b.type.ordinal + 1).toByte()
            map.buildingVariant[i] = b.variant.toByte()
            map.fire[i] = min(b.burning, 127).toByte()
        }
    }

    /** After an undo or redo has put tiles back, brings their type and variant into line. */
    private fun restamp(tiles: IntArray) {
        for (i in tiles) {
            val b = buildings[map.building[i]]
            map.buildingType[i] = if (b == null) 0 else (b.type.ordinal + 1).toByte()
            map.buildingVariant[i] = if (b == null) 0 else b.variant.toByte()
            map.fire[i] = if (b == null) 0 else min(b.burning, 127).toByte()
        }
    }

    // ---- time ----------------------------------------------------------------

    /** Moves on a day: growth every day, and the census, demand, money and grime on the first of each month. */
    fun tick() {
        if (networksDirty) updateNetworks()
        for (b in buildings.values) b.age++
        burnDay()
        growDay()
        traffic.sendDay(day, daysIn(month, year))
        if (day % Balance.WEATHER_DAYS == 1) weather.nextDay(month, day, daysIn(month, year), Balance.WEATHER_DAYS)
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
        updateServices()
        census()
        startTraffic()
        Effects.crime(
            map, { i -> buildings[map.building[i]]?.let { if (it.type.zone == Zone.RESIDENTIAL) it.type.capacity else 0 } ?: 0 },
            { i -> map.building[i] != 0 }, stats.unemployment, map.crime,
        )
        Effects.landValue(map, { i -> buildings[map.building[i]]?.type }, nearRoad, map.landValue)
        startFires()
        demand()
        money()
        record()
    }

    // ---- traffic ----------------------------------------------------------------

    private val traffic = Traffic(map)

    /** The road tile a building is reached from, or -1. */
    private fun accessOf(b: Building): Int {
        var node = -1
        forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { i -> if (node < 0) node = access[i] }
        return node
    }

    /**
     * Ends last month's trips and sets out this month's: workers from homes to
     * jobs, shoppers to shops, freight from works to the edge of the map.
     * Workers who couldn't get to a job count as out of work, and each home
     * learns how long its commute was.
     */
    private fun startTraffic() {
        val n = map.size
        val workersAt = IntArray(n)
        val shoppersAt = IntArray(n)
        val freightAt = IntArray(n)
        val jobsAt = IntArray(n)
        val shopsAt = IntArray(n)
        for (b in buildings.values) {
            val node = accessOf(b)
            if (node < 0) continue
            val c = b.type.capacity
            when (b.type.zone) {
                Zone.RESIDENTIAL -> {
                    workersAt[node] += (c * Balance.LABOUR_SHARE).toInt()
                    shoppersAt[node] += c / Balance.RESIDENTS_PER_SHOPPER
                }
                Zone.COMMERCIAL -> {
                    jobsAt[node] += c
                    shopsAt[node] += c * Balance.SHOPPERS_PER_SHOP_JOB
                }
                Zone.INDUSTRIAL -> {
                    jobsAt[node] += c
                    freightAt[node] += c * Balance.FREIGHT_PER_TEN_JOBS / 10
                }
                else -> jobsAt[node] += c
            }
        }
        traffic.newMonth(workersAt, shoppersAt, freightAt, jobsAt, shopsAt, year * 12 + month)

        val s = stats
        if (traffic.workersSent > 0) {
            val stranded = (traffic.workersSent - traffic.workersPlaced) * 100 / traffic.workersSent
            s.unemployment = max(s.unemployment, stranded)
        }
        map.commute.fill(0)
        var total = 0L
        var people = 0
        for (b in buildings.values) {
            if (b.type.zone != Zone.RESIDENTIAL) continue
            val node = accessOf(b)
            val seconds = if (node < 0) -1 else traffic.commute[node]
            if (seconds == 0) continue
            val v = if (seconds < 0) 255 else min(254, 1 + seconds / 30)
            forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { map.commute[it] = v.toByte() }
            if (seconds > 0) {
                total += seconds.toLong() * b.type.capacity
                people += b.type.capacity
            }
        }
        s.commute = if (people == 0) 0 else (total / people / 60).toInt()
    }

    // ---- services and fires ----------------------------------------------------

    private fun reach(base: Int, funding: Int) = base * (40 + 60 * funding.coerceIn(0, 100) / 100) / 100

    private fun updateServices() {
        val police = buildings.values.filter { it.type == BuildingType.POLICE_STATION }
        val fire = buildings.values.filter { it.type == BuildingType.FIRE_STATION }
        Effects.cover(map, police, reach(Balance.POLICE_REACH, policeFunding), map.policeCover)
        Effects.cover(map, fire, reach(Balance.FIRE_REACH, fireFunding), map.fireCover)
    }

    /**
     * Now and then a home, shop or works catches fire, far less often near a
     * fire station. Power stations and services don't, until disasters come.
     */
    private fun startFires() {
        for (b in buildings.values.toList()) {
            if (b.type.zone == Zone.NONE || b.burning > 0) continue
            val chance = if (b.type.zone == Zone.INDUSTRIAL) Balance.FIRE_CHANCE_INDUSTRY else Balance.FIRE_CHANCE
            val cover = map.fireCover[map.index(b.x, b.y)].toInt() and 0xff
            if (rng.nextInt(10_000) < chance * (255 - cover * 85 / 100) / 255) ignite(b)
        }
    }

    /** How many buildings are on fire. */
    var burningNow = 0
        private set

    /** Sets the building on [x], [y] alight, if there's one that can burn. For disasters, and for trying fires out. */
    fun startFireAt(x: Int, y: Int): Boolean {
        val b = buildingAt(x, y) ?: return false
        if (b.type.zone == Zone.NONE || b.burning > 0) return false
        ignite(b)
        return true
    }

    private fun ignite(b: Building) {
        if (b.burning == 0) burningNow++
        b.burning = Balance.FIRE_DAYS + rng.nextInt(Balance.FIRE_DAYS_MORE)
        stamp(b)
        events += CityEvent(EventKind.FireStarted, b.x, b.y, b.type)
    }

    /**
     * Fires burn down a day at a time and may spread next door. When one burns
     * out, a building a fire station can reach is saved, a stage lower, and one
     * it can't is lost.
     */
    private fun burnDay() {
        val burning = buildings.values.filter { it.burning > 0 }
        for (b in burning) {
            val i = map.index(b.x, b.y)
            val cover = map.fireCover[i].toInt() and 0xff
            // A covered fire burns out twice as fast.
            b.burning -= if (cover >= Balance.FIRE_SAVED) 2 else 1
            if (rng.nextInt(100) < Balance.FIRE_SPREAD && cover < 160) {
                val k = rng.nextInt(4)
                val next = buildings[neighbour(b, k)]
                if (next != null && next.burning == 0 && next.type.zone != Zone.NONE) ignite(next)
            }
            if (b.burning > 0) {
                stamp(b)
                continue
            }
            b.burning = 0
            burningNow--
            if (cover >= Balance.FIRE_SAVED) {
                if (b.type.previous != null) shrink(b) else stamp(b)
                events += CityEvent(EventKind.FireSaved, b.x, b.y, b.type)
            } else {
                events += CityEvent(EventKind.BuildingLost, b.x, b.y, b.type)
                forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { townChanges += it }
                removeBuilding(b)
                networksChanged()
            }
        }
    }

    /** The building next to [b] on side [k] (north, east, south, west), by id, or 0. */
    private fun neighbour(b: Building, k: Int): Int {
        val x = when (k) { 1 -> b.x + b.type.width; 3 -> b.x - 1; else -> b.x }
        val y = when (k) { 0 -> b.y - 1; 2 -> b.y + b.type.height; else -> b.y }
        return if (map.inside(x, y)) map.building[map.index(x, y)] else 0
    }

    // ---- networks ------------------------------------------------------------

    private var networksDirty = true

    /** Tiles within reach of a road, where zoned land can grow. */
    private val nearRoad = BooleanArray(map.size)

    /** The road tile each tile is reached from, the nearest within reach, or -1. */
    private val access = IntArray(map.size)

    /** A road reaches the edge of the map, so the town can trade with the outside. */
    var connected = false
        private set

    private fun networksChanged() {
        networksDirty = true
    }

    private fun updateNetworks() {
        networksDirty = false
        val m = map
        connected = false
        // Out from every road tile at once, a step at a time, so each tile gets the nearest.
        access.fill(-1)
        val steps = IntArray(m.size)
        val queue = IntArray(m.size)
        var head = 0
        var tail = 0
        for (i in 0 until m.size) {
            if (m.road[i] == Road.NONE) continue
            val x = i % m.width
            val y = i / m.width
            if (x == 0 || y == 0 || x == m.width - 1 || y == m.height - 1) connected = true
            access[i] = i
            queue[tail++] = i
        }
        while (head < tail) {
            val i = queue[head++]
            if (steps[i] == Balance.ROAD_REACH) continue
            val x = i % m.width
            val y = i / m.width
            for (k in 0 until 4) {
                val nx = x + DX[k]
                val ny = y + DY[k]
                if (!m.inside(nx, ny)) continue
                val j = m.index(nx, ny)
                if (access[j] >= 0) continue
                access[j] = access[i]
                steps[j] = steps[i] + 1
                queue[tail++] = j
            }
        }
        for (i in 0 until m.size) nearRoad[i] = access[i] >= 0
        // Power spreads from the power stations along lines, through buildings and
        // across zoned land, so a line along the back of a zone powers all of it.
        val powered = m.powered
        powered.fill(false)
        head = 0
        tail = 0
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
     * How much a zone wants a lot, roughly 0 to 100. Homes want valuable land,
     * power, and no crime, pollution or industry next door; shops want valuable
     * land and people nearby; industry wants power and water.
     */
    fun attraction(i: Int, zone: Byte): Int {
        val m = map
        val x = i % m.width
        val y = i / m.width
        val value = m.landValue[i].toInt() and 0xff
        val crime = m.crime[i].toInt() and 0xff
        val pollution = m.pollution[i].toInt() and 0xff
        var score = if (m.powered[i]) 10 else 0
        when (zone) {
            Zone.RESIDENTIAL -> {
                var industry = false
                around(x, y, 2) { j, _ -> if (buildings[m.building[j]]?.type?.zone == Zone.INDUSTRIAL) industry = true }
                score += 35 + value / 3 - crime / 5 - pollution / 3 - if (industry) 10 else 0
                score -= commutePenalty(m.commute[i].toInt() and 0xff)
            }
            Zone.COMMERCIAL -> {
                var people = 0
                around(x, y, 6) { j, _ ->
                    val b = buildings[m.building[j]]
                    if (b != null && b.type.zone == Zone.RESIDENTIAL) people += b.type.capacity
                }
                score += 28 + value / 4 + min(people / 8, 30) - crime / 6 - pollution / 5
                // Passing trade.
                if (access[i] >= 0) score += min(Balance.PASSING_TRADE, traffic.lastVolume[access[i]] / Balance.TRIPS_PER_PASSING_POINT)
            }
            Zone.INDUSTRIAL -> {
                var water = false
                around(x, y, 3) { j, _ -> if (m.terrain[j] == Terrain.WATER) water = true }
                score += 50 + (if (water) 5 else 0) - crime / 8
                if (access[i] >= 0 && traffic.freightStuck[access[i]]) score -= Balance.FREIGHT_STUCK
            }
        }
        return score
    }

    /** How much a long commute puts people off, from the commute layer's half minutes. */
    private fun commutePenalty(c: Int): Int = when (c) {
        0 -> 0
        255 -> Balance.NO_COMMUTE
        else -> min(Balance.LONG_COMMUTE, max(0, (c - 1) / 2 - Balance.FINE_COMMUTE) / 2)
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
        val jobs = shopJobs + industryJobs + otherJobs
        stats.unemployment = if (stats.workers == 0) 0 else max(0, (stats.workers - jobs) * 100 / stats.workers)
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

    /**
     * Taxes come in by the resident and the job, worth more on more valuable
     * land. Upkeep goes out on roads, lines, the power stations and the services,
     * each service's scaled by its funding.
     */
    private fun money() {
        val s = stats
        var homes = 0.0
        var shops = 0.0
        var works = 0.0
        var police = 0
        var fire = 0
        var parks = 0
        var plants = 0
        for (b in buildings.values) {
            val worth = 0.5 + (map.landValue[map.index(b.x, b.y)].toInt() and 0xff) / 200.0
            when {
                b.type.zone == Zone.RESIDENTIAL -> homes += b.type.capacity * worth
                b.type.zone == Zone.COMMERCIAL -> shops += b.type.capacity * worth
                b.type.zone == Zone.INDUSTRIAL -> works += b.type.capacity * worth
                b.type == BuildingType.POLICE_STATION -> police++
                b.type == BuildingType.FIRE_STATION -> fire++
                b.type == BuildingType.PARK -> parks++
                b.type == BuildingType.COAL_PLANT -> plants++
            }
        }
        s.residentialIncome = (homes * residentialTax * Balance.RESIDENT_TAX).roundToLong()
        s.commercialIncome = (shops * commercialTax * Balance.JOB_TAX).roundToLong()
        s.industrialIncome = (works * industrialTax * Balance.JOB_TAX).roundToLong()
        var roads = 0.0
        var lines = 0
        for (i in 0 until map.size) {
            val road = RoadType.of(map.road[i])
            if (road != null) roads += road.upkeep * if (map.terrain[i] == Terrain.WATER) Balance.BRIDGE_UPKEEP else 1.0
            if (map.power[i] != Power.NONE) lines++
        }
        s.roadUpkeep = (roads + lines * Balance.LINE_UPKEEP).roundToLong()
        s.powerUpkeep = (plants * Balance.PLANT_UPKEEP).roundToLong()
        s.policeUpkeep = (police * Balance.POLICE_UPKEEP * policeFunding / 100).roundToLong()
        s.fireUpkeep = (fire * Balance.FIRE_UPKEEP * fireFunding / 100).roundToLong()
        s.parkUpkeep = (parks * Balance.PARK_UPKEEP * parkFunding / 100).roundToLong()
        s.income = s.residentialIncome + s.commercialIncome + s.industrialIncome
        s.upkeep = s.roadUpkeep + s.powerUpkeep + s.policeUpkeep + s.fireUpkeep + s.parkUpkeep
        funds += s.income - s.upkeep
    }

    /** This month's numbers into the history. */
    private fun record() {
        var crime = 0L
        var pollution = 0L
        var value = 0L
        var built = 0
        var land = 0
        for (i in 0 until map.size) {
            if (map.building[i] != 0) {
                crime += map.crime[i].toInt() and 0xff
                pollution += map.pollution[i].toInt() and 0xff
                built++
            }
            if (map.terrain[i] != Terrain.WATER) {
                value += map.landValue[i].toInt() and 0xff
                land++
            }
        }
        val s = stats
        s.crime = if (built == 0) 0 else (crime / built).toInt()
        s.pollution = if (built == 0) 0 else (pollution / built).toInt()
        s.landValue = if (land == 0) 0 else (value / land).toInt()
        history.record(this)
    }

    private fun inMap(i: Int) = i in 0 until map.size

    private inline fun forRect(x0: Int, y0: Int, x1: Int, y1: Int, action: (Int) -> Unit) {
        val left = maxOf(0, minOf(x0, x1))
        val right = minOf(map.width - 1, maxOf(x0, x1))
        val top = maxOf(0, minOf(y0, y1))
        val bottom = minOf(map.height - 1, maxOf(y0, y1))
        for (y in top..bottom) for (x in left..right) action(map.index(x, y))
    }

    // ---- saving ----------------------------------------------------------------

    internal fun writeTo(w: SaveWriter) {
        // The summary comes first so a list of saves can read it without the rest.
        w.string(name); w.int(year); w.int(month); w.int(stats.population); w.long(funds)
        w.long(seed); w.int(map.width); w.int(map.height)
        w.int(day)
        w.int(residentialTax); w.int(commercialTax); w.int(industrialTax)
        w.int(policeFunding); w.int(fireFunding); w.int(parkFunding)
        w.long(rng.state)
        w.int(nextId)
        for (q in quota) w.int(q)
        val s = stats
        for (v in intArrayOf(
            s.population, s.workers, s.shopJobs, s.industryJobs, s.otherJobs, s.unemployment,
            s.residentialDemand, s.commercialDemand, s.industryDemand, s.crime, s.pollution, s.landValue,
        )) w.int(v)
        for (v in longArrayOf(
            s.residentialIncome, s.commercialIncome, s.industrialIncome, s.income,
            s.roadUpkeep, s.powerUpkeep, s.policeUpkeep, s.fireUpkeep, s.parkUpkeep, s.upkeep,
        )) w.long(v)
        weather.writeTo(w)
        history.writeTo(w)
        for (layer in arrayOf(map.terrain, map.road, map.zone, map.power, map.grime, map.pollution, map.landValue, map.crime, map.policeCover, map.fireCover)) {
            w.layer(layer)
        }
        w.count(buildings.size)
        for (b in buildings.values) {
            w.int(b.id); w.string(b.type.name); w.int(b.x); w.int(b.y); w.int(b.variant); w.int(b.age); w.int(b.burning)
        }
        // Since version 2.
        w.layer(map.roadHeading); w.layer(map.congestion); w.layer(map.commute)
        w.int(stats.commute)
        traffic.writeTo(w)
    }

    companion object {
        internal fun readSummary(r: SaveReader) = SaveSummary(r.string(), r.int(), r.int(), r.int(), r.long())

        internal fun readFrom(r: SaveReader, version: Int): City {
            val name = r.string()
            val year = r.int()
            val month = r.int()
            r.int() // population, from the summary; the stats below have it too
            val funds = r.long()
            val seed = r.long()
            val width = r.int()
            val height = r.int()
            if (width !in 8..1024 || height !in 8..1024) throw SaveError("the map size doesn't make sense")
            val c = City(seed, width, height, terrain = null)
            c.name = name
            c.year = year
            c.month = month
            c.funds = funds
            c.day = r.int()
            c.residentialTax = r.int(); c.commercialTax = r.int(); c.industrialTax = r.int()
            c.policeFunding = r.int(); c.fireFunding = r.int(); c.parkFunding = r.int()
            c.rng.state = r.long()
            c.nextId = r.int()
            for (k in c.quota.indices) c.quota[k] = r.int()
            val s = c.stats
            s.population = r.int(); s.workers = r.int(); s.shopJobs = r.int(); s.industryJobs = r.int()
            s.otherJobs = r.int(); s.unemployment = r.int(); s.residentialDemand = r.int(); s.commercialDemand = r.int()
            s.industryDemand = r.int(); s.crime = r.int(); s.pollution = r.int(); s.landValue = r.int()
            s.residentialIncome = r.long(); s.commercialIncome = r.long(); s.industrialIncome = r.long(); s.income = r.long()
            s.roadUpkeep = r.long(); s.powerUpkeep = r.long(); s.policeUpkeep = r.long(); s.fireUpkeep = r.long()
            s.parkUpkeep = r.long(); s.upkeep = r.long()
            c.weather.readFrom(r)
            c.history.readFrom(r)
            val m = c.map
            for (layer in arrayOf(m.terrain, m.road, m.zone, m.power, m.grime, m.pollution, m.landValue, m.crime, m.policeCover, m.fireCover)) {
                r.layer(layer)
            }
            repeat(r.count()) {
                val id = r.int()
                val typeName = r.string()
                val type = BuildingType.entries.firstOrNull { it.name == typeName } ?: throw SaveError("a building of a kind this version doesn't know")
                val b = Building(id, type, r.int(), r.int(), r.int())
                b.age = r.int()
                b.burning = r.int()
                if (b.burning > 0) c.burningNow++
                c.buildings[b.id] = b
                c.stamp(b)
            }
            if (version >= 2) {
                r.layer(m.roadHeading); r.layer(m.congestion); r.layer(m.commute)
                s.commute = r.int()
                c.traffic.readFrom(r)
            }
            c.networksChanged()
            return c
        }

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
    /** Percent of workers without a job, or without a way to get to one. */
    var unemployment = 0

    /** The average commute in minutes. */
    var commute = 0
    var residentialDemand = 0
    var commercialDemand = 0
    var industryDemand = 0

    var residentialIncome = 0L
    var commercialIncome = 0L
    var industrialIncome = 0L
    var income = 0L

    var roadUpkeep = 0L
    var powerUpkeep = 0L
    var policeUpkeep = 0L
    var fireUpkeep = 0L
    var parkUpkeep = 0L
    var upkeep = 0L

    /** Averages: crime and pollution where there are buildings, land value over all the land. 0 to 255. */
    var crime = 0
    var pollution = 0
    var landValue = 0

    val jobs get() = shopJobs + industryJobs + otherJobs
}

enum class EventKind { FireStarted, FireSaved, BuildingLost }

/** Something that happened to a building at [x], [y]. */
class CityEvent(val kind: EventKind, val x: Int, val y: Int, val type: BuildingType)

/** What the graphs can show. */
enum class Series { Population, Jobs, Funds, Income, Upkeep, Crime, Pollution, LandValue }

/** The town month by month, the last [capacity] months of it. */
class History(val capacity: Int = 240) {
    private val data = Array(Series.entries.size) { LongArray(capacity) }
    private val years = IntArray(capacity)
    private val months = IntArray(capacity)
    var count = 0
        private set
    private var next = 0

    internal fun record(city: City) {
        val s = city.stats
        val values = longArrayOf(
            s.population.toLong(), s.jobs.toLong(), city.funds, s.income, s.upkeep,
            s.crime.toLong(), s.pollution.toLong(), s.landValue.toLong(),
        )
        for (k in values.indices) data[k][next] = values[k]
        years[next] = city.year
        months[next] = city.month
        next = (next + 1) % capacity
        if (count < capacity) count++
    }

    internal fun writeTo(w: SaveWriter) {
        w.count(count)
        for (k in 0 until count) {
            val at = (next - count + k + capacity) % capacity
            w.int(years[at]); w.int(months[at])
            for (d in data) w.long(d[at])
        }
    }

    internal fun readFrom(r: SaveReader) {
        val n = r.count()
        count = 0
        next = 0
        repeat(n) {
            val y = r.int()
            val m = r.int()
            val values = LongArray(data.size) { r.long() }
            if (count == capacity) count--
            years[next] = y
            months[next] = m
            for (k in data.indices) data[k][next] = values[k]
            next = (next + 1) % capacity
            count++
        }
    }

    /** A series oldest first. */
    fun values(series: Series): LongArray {
        val d = data[series.ordinal]
        return LongArray(count) { d[(next - count + it + capacity) % capacity] }
    }

    /** The year and month (0 is January) of the [k]th recorded month, oldest first. */
    fun dateOf(k: Int): Pair<Int, Int> {
        val at = (next - count + k + capacity) % capacity
        return years[at] to months[at]
    }
}
