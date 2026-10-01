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

    /** Every building with people living in it, or empty for sale. */
    val homes get() = buildings.values.filter { it.people != null }

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
                for (k in layout.tiles.indices) {
                    val i = layout.tiles[k]
                    val road = roadCost(action.type, layout, k)
                    if (road == BLOCKED) {
                        blocked += i
                        continue
                    }
                    val pipes = if (action.pipes && m.terrain[i] != Terrain.WATER) pipeCost(i) else 0L
                    if (road != NO_CHANGE || pipes > 0) {
                        changes += i
                        cost += (if (road == NO_CHANGE) 0 else road) + pipes
                    }
                }
            }
            is Action.BuildPipe -> for (i in action.tiles) {
                when {
                    !inMap(i) -> {}
                    // Pipes go under everything but water.
                    m.terrain[i] == Terrain.WATER -> blocked += i
                    pipes(action.kind)[i].toInt() != 0 -> {}
                    else -> {
                        changes += i
                        cost += action.kind.price
                    }
                }
            }
            is Action.BuildRail -> {
                val path = action.tiles.filter { inMap(it) }
                val (runs, turns) = runsOf(path)
                for (k in path.indices) {
                    val i = path[k]
                    val water = m.terrain[i] == Terrain.WATER
                    when {
                        m.building[i] != 0 || m.bank[i].toInt() != 0 -> blocked += i
                        m.rail[i] != Rail.NONE -> {}
                        // Over water on a bridge of its own, straight across.
                        water && (turns[k] || m.road[i] != Road.NONE) -> blocked += i
                        // Across a road only straight over it, as a level crossing.
                        m.road[i] != Road.NONE && (turns[k] || !across(i, runs[k].toInt(), m.road)) -> blocked += i
                        m.power[i] != Power.NONE -> blocked += i
                        else -> {
                            changes += i
                            cost += Prices.RAIL * (if (water) Prices.BRIDGE else 1) + clearing(i)
                        }
                    }
                }
            }
            is Action.BuildPowerLine -> for (i in action.tiles) {
                when {
                    !inMap(i) -> {}
                    m.terrain[i] == Terrain.WATER || m.building[i] != 0 || m.zone[i] != Zone.NONE || m.rail[i] != Rail.NONE -> blocked += i
                    m.power[i] != Power.NONE -> {}
                    else -> {
                        changes += i
                        cost += Prices.POWER_LINE + clearing(i)
                    }
                }
            }
            is Action.PlaceZone -> forRect(action.x0, action.y0, action.x1, action.y1) { i ->
                when {
                    m.terrain[i] == Terrain.WATER || m.road[i] != Road.NONE || m.power[i] != Power.NONE || m.rail[i] != Rail.NONE ||
                        m.bank[i].toInt() != 0 -> blocked += i
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
                        m.zone[i] != Zone.NONE || m.building[i] != 0 || m.rail[i] != Rail.NONE || m.bank[i].toInt() != 0
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
                    m.zone[i] != Zone.NONE || m.building[i] != 0 || m.rail[i] != Rail.NONE || m.bank[i].toInt() != 0
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
                    if (m.rail[i] != Rail.NONE) c += Prices.REMOVE_RAIL * if (m.terrain[i] == Terrain.WATER) Prices.BRIDGE else 1
                    if (m.bank[i].toInt() != 0) c += Prices.REMOVE_BANK
                    if (m.terrain[i] == Terrain.TREES) c += Prices.CLEAR_TREES
                    if (c > 0 || m.zone[i] != Zone.NONE) {
                        changes += i
                        cost += c
                    }
                }
            }
            is Action.BuildBank -> for (i in action.tiles) {
                when {
                    !inMap(i) -> {}
                    m.terrain[i] == Terrain.WATER || m.building[i] != 0 || m.road[i] != Road.NONE || m.rail[i] != Rail.NONE -> blocked += i
                    m.bank[i].toInt() != 0 -> {}
                    else -> {
                        changes += i
                        cost += Prices.BANK + clearing(i)
                    }
                }
            }
            is Action.RemovePipes -> forRect(action.x0, action.y0, action.x1, action.y1) { i ->
                val count = m.waterPipe[i] + m.sewerPipe[i] + m.stormPipe[i]
                if (count > 0) {
                    changes += i
                    cost += Prices.REMOVE_PIPE * count
                }
            }
        }
        val problem = when {
            action is Action.PlaceBuilding && blocked.isNotEmpty() -> Problem.Blocked
            action is Action.PlaceBuilding && action.type.railway && Rail.trackSide(m, action.type, action.x, action.y) == 0 -> Problem.NeedsTrack
            action is Action.PlaceBuilding && action.type.onWater && !besideWater(action.type, action.x, action.y) -> Problem.NeedsWater
            changes.isEmpty() -> Problem.NothingToDo
            cost > funds -> Problem.NotEnoughMoney
            else -> null
        }
        return Plan(cost, changes.toIntArray(), blocked.toIntArray(), problem)
    }

    private fun clearing(i: Int) = if (map.terrain[i] == Terrain.TREES) Prices.CLEAR_TREES else 0L

    /**
     * What drawing a road of [type] does to the [k]th tile of [layout]: its
     * cost, [NO_CHANGE] if the road there stays as it is, or [BLOCKED].
     */
    private fun roadCost(type: RoadType, layout: RoadLayout, k: Int): Long {
        val m = map
        val i = layout.tiles[k]
        val run = layout.runs[k].toInt()
        val old = RoadType.of(m.road[i])
        val bridge = if (m.terrain[i] == Terrain.WATER) Prices.BRIDGE else 1
        return when {
            m.building[i] != 0 || m.bank[i].toInt() != 0 -> BLOCKED
            bridge > 1 && (!type.bridges || layout.turns[k] || m.rail[i] != Rail.NONE) -> BLOCKED
            // A road meets track only straight across it, as a level crossing.
            m.rail[i] != Rail.NONE && (layout.turns[k] || !across(i, run, m.rail)) -> BLOCKED
            old == null -> type.price * bridge + clearing(i)
            old == type && (layout.headings[k] == m.roadHeading[i] || across(run, m.roadHeading[i].toInt())) -> NO_CHANGE
            // A road drawn across a better one leaves the crossing as it is.
            old.capacity > type.capacity && crossing(i, run) -> NO_CHANGE
            else -> max(Prices.REMOVE_ROAD, type.price - old.price) * bridge
        }
    }

    /** Whether a building of [type] at [x], [y] would have water next to it, on any side or corner. */
    fun besideWater(type: BuildingType, x: Int, y: Int): Boolean {
        for (ty in y - 1..y + type.height) for (tx in x - 1..x + type.width) {
            if (map.inside(tx, ty) && map.terrain[map.index(tx, ty)] == Terrain.WATER) return true
        }
        return false
    }

    /** The map layer for a kind of pipe. */
    private fun pipes(kind: Pipe): ByteArray = when (kind) {
        Pipe.WATER -> map.waterPipe
        Pipe.SEWER -> map.sewerPipe
        Pipe.STORM -> map.stormPipe
    }

    /** What it costs to put every kind of pipe under a tile, as far as they're not there already. */
    private fun pipeCost(i: Int): Long = Pipe.entries.sumOf { if (pipes(it)[i].toInt() == 0) it.price else 0L }

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

    /**
     * The way a drawn path runs through each tile, and whether it turns there.
     * One tile on its own runs nowhere in particular.
     */
    private fun runsOf(path: List<Int>): Pair<ByteArray, BooleanArray> {
        val w = map.width
        fun step(k: Int): Byte {
            if (path.size < 2) return Heading.BOTH
            val a = path[max(0, k - 1)]
            val b = path[max(1, k)]
            return Heading.of(b % w - a % w, b / w - a / w)
        }
        val runs = ByteArray(path.size) { step(it) }
        val turns = BooleanArray(path.size) { it + 1 < path.size && step(it + 1) != runs[it] }
        return runs to turns
    }

    /**
     * Whether something running [run] through tile [i] goes straight across
     * what's already there in [layer]: nothing of it before or after along the
     * way it runs.
     */
    private fun across(i: Int, run: Int, layer: ByteArray): Boolean {
        if (run == 0) return false
        val x = i % map.width
        val y = i / map.width
        fun has(dx: Int, dy: Int) = map.inside(x + dx, y + dy) && layer[map.index(x + dx, y + dy)].toInt() != 0
        return !has(Heading.DX[run], Heading.DY[run]) && !has(-Heading.DX[run], -Heading.DY[run])
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
                val changing = plan.changes.toHashSet()
                for (k in layout.tiles.indices) {
                    val i = layout.tiles[k]
                    if (i !in changing) continue
                    if (roadCost(action.type, layout, k) != NO_CHANGE) {
                        m.road[i] = action.type.id
                        m.roadHeading[i] = layout.headings[k]
                        m.zone[i] = Zone.NONE
                        clearTrees(i)
                    }
                    if (action.pipes && m.terrain[i] != Terrain.WATER) for (kind in Pipe.entries) pipes(kind)[i] = 1
                }
            }
            is Action.BuildPipe -> for (i in plan.changes) pipes(action.kind)[i] = 1
            is Action.BuildRail -> for (i in plan.changes) {
                m.rail[i] = Rail.TRACK
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
            is Action.PlaceParks -> for (i in plan.changes) {
                clearTrees(i)
                added += addBuilding(BuildingType.PARK, i % m.width, i / m.width, rng.nextInt(1000))
            }
            is Action.Bulldoze -> for (i in plan.changes) {
                buildings[m.building[i]]?.let { removed += it; removeBuilding(it) }
                m.road[i] = Road.NONE
                m.roadHeading[i] = Heading.BOTH
                m.rail[i] = Rail.NONE
                m.bank[i] = 0
                m.zone[i] = Zone.NONE
                m.power[i] = Power.NONE
                clearTrees(i)
            }
            is Action.RemovePipes -> for (i in plan.changes) for (kind in Pipe.entries) pipes(kind)[i] = 0
            is Action.BuildBank -> for (i in plan.changes) {
                m.bank[i] = 1
                m.zone[i] = Zone.NONE
                clearTrees(i)
            }
        }
        funds -= plan.cost
        undoable.addLast(Edit(plan.changes, before, LongArray(plan.changes.size) { m.tileState(plan.changes[it]) }, plan.cost, added, removed))
        if (undoable.size > MAX_UNDO) undoable.removeFirst()
        redoable.clear()
        networksChanged()
        railChanged = true
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
        railChanged = true
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
        railChanged = true
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
        fitHousehold(b)
        return b
    }

    private fun removeBuilding(b: Building) {
        b.people?.let { departures += it.size }
        buildings.remove(b.id)
        forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { i ->
            map.building[i] = 0
            map.buildingType[i] = 0
            map.forSale[i] = false
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
            map.forSale[i] = b.people?.empty == true
        }
    }

    /** Puts a home's for sale sign up or takes it down, and has the map drawn again there. */
    private fun markForSale(b: Building) {
        forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) {
            map.forSale[it] = b.people?.empty == true
            townChanges += it
        }
    }

    /** After an undo or redo has put tiles back, brings their type and variant into line. */
    private fun restamp(tiles: IntArray) {
        for (i in tiles) {
            val b = buildings[map.building[i]]
            map.buildingType[i] = if (b == null) 0 else (b.type.ordinal + 1).toByte()
            map.buildingVariant[i] = if (b == null) 0 else b.variant.toByte()
            map.fire[i] = if (b == null) 0 else min(b.burning, 127).toByte()
            map.forSale[i] = b?.people?.empty == true
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
        drainFloods()
        river = max(0, river - Balance.RIVER_FALL)
        if (day % Balance.WEATHER_DAYS == 1) {
            val snow = weather.snowCover
            weather.nextDay(month, day, daysIn(month, year), Balance.WEATHER_DAYS)
            val rain = if (weather.precipitation == Precipitation.Rain) weather.intensity else 0
            val melt = max(0, snow - weather.snowCover) * Balance.MELT_RUNOFF
            val frozen = weather.temperature <= 0
            // Drizzle soaks in wherever it falls.
            if (rain + melt >= Balance.DOWNPOUR) rainfall(rain + melt, frozen)
            riseRivers(rain + melt)
            wetGround(rain + melt, frozen, weather.temperature)
            if (river > Balance.BANKFULL) overflowRivers()
        }
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
        waterCuts()
        updateFoul()
        fadeFloodMemory()
        updatePollution()
        updateGrime()
        updateServices()
        updatePeople()
        census()
        startTraffic()
        Effects.crime(
            map, { i -> buildings[map.building[i]]?.people?.size ?: 0 },
            { i -> map.building[i] != 0 }, stats.unemployment, map.crime,
        )
        Effects.landValue(map, { i -> buildings[map.building[i]]?.type }, nearRoad, map.landValue)
        startFires()
        demand()
        money()
        record()
    }

    // ---- railway ----------------------------------------------------------------

    private val railway = RailNetwork(map)
    private var railChanged = true

    /** A passenger station on a line to the edge, and a freight yard on one. */
    private var railPassengers = false
    private var railFreight = false

    /** The lines trains ran last month, for drawing them: the track, and what they carried. */
    var trainRoutes: List<TrainRoute> = emptyList()
        private set

    private fun updateRail() {
        railway.update(buildings.values.filter { it.type.railway })
        val stops = railway.buildings
        val passengers = BooleanArray(stops.size) { stops[it].type.station }
        val freightOut = BooleanArray(stops.size) { stops[it].type.yard && railway.linked(it) }
        railPassengers = stops.indices.any { passengers[it] && railway.linked(it) }
        railFreight = freightOut.any { it }
        val times = Array(stops.size) { railway.times[it].copyOf() }
        traffic.setRail(IntArray(stops.size) { accessOf(stops[it]) }, railway.stops.copyOf(), times, passengers, freightOut)
    }

    /**
     * How busy the track was last month, and the lines trains ran: each
     * journey's path along the track, between two stops or from a yard to the
     * nearest edge. Stations on a line to the edge also get a train in from
     * outside, busy or not.
     */
    private fun updateTrains() {
        val busy = IntArray(map.size)
        val routes = ArrayList<TrainRoute>()
        val byStart = traffic.lastJourneys.entries.sortedBy { it.key }.groupBy { (it.key ushr 32).toInt() }
        val linkedStations = railway.stops.indices.filter { railway.buildings[it].type.station && railway.linked(it) }
        val starts = (byStart.keys + linkedStations.map { railway.stops[it] }).distinct().sorted()
        for (start in starts) {
            if (start !in 0 until map.size || map.rail[start] != Rail.TRACK) continue
            val steps = railway.steps(start)
            fun toEdge(): Int {
                var best = -1
                for (i in 0 until map.size) {
                    if (steps[i] < 0) continue
                    val x = i % map.width
                    val y = i / map.width
                    if ((x == 0 || y == 0 || x == map.width - 1 || y == map.height - 1) && (best < 0 || steps[i] < steps[best])) best = i
                }
                return best
            }
            val journeys = byStart[start].orEmpty()
            for ((key, trips) in journeys) {
                val endTile = (key and 0xffffffffL).toInt()
                val freight = endTile == -1
                val end = if (freight) toEdge() else endTile
                if (end < 0 || end !in 0 until map.size || steps[end] < 0) continue
                val path = railway.pathBack(steps, end)
                for (i in path) busy[i] += trips
                routes += TrainRoute(path.reversedArray(), !freight, trips)
            }
            if (map.rail[start] == Rail.TRACK && linkedStations.any { railway.stops[it] == start }) {
                val end = toEdge()
                if (end >= 0) routes += TrainRoute(railway.pathBack(steps, end).reversedArray(), true, 0)
            }
        }
        val train = Balance.TRAIN_LOAD * 30
        for (i in 0 until map.size) map.railBusy[i] = min(255, busy[i] * 128 / train).toByte()
        trainRoutes = routes
    }

    /** Whether a road comes near enough to a building to reach it. */
    fun reachable(b: Building): Boolean {
        if (networksDirty) updateNetworks()
        return accessOf(b) >= 0
    }

    /** Passengers who boarded or left at a station last month, freight a yard sent, and whether its line reaches the edge. */
    fun riders(b: Building): Int = accessOf(b).let { if (it < 0) 0 else traffic.lastRiders[it] }
    fun freightSent(b: Building): Int = accessOf(b).let { if (it < 0) 0 else traffic.lastRailFreight[it] }
    fun railLinked(b: Building): Boolean {
        if (networksDirty) updateNetworks()
        val k = railway.buildings.indexOfFirst { it.id == b.id }
        return k >= 0 && railway.linked(k)
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
                    workersAt[node] += b.people?.workers() ?: 0
                    shoppersAt[node] += (b.people?.size ?: 0) * Demography.SPENDING_BY_WEALTH[b.people?.wealth ?: Wealth.MIDDLE] / 100 / Balance.RESIDENTS_PER_SHOPPER
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
        updateTrains()

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

    /**
     * How well the fire halls can fight a fire on a tile, 0 to 255: their cover,
     * and more where there's mains water for the hydrants.
     */
    internal fun fireCoverAt(i: Int): Int {
        val cover = map.fireCover[i].toInt() and 0xff
        return if (cover > 0 && map.watered[i]) min(255, cover + Balance.HYDRANT_COVER) else cover
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
            val cover = fireCoverAt(i)
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
        // Only the player changes track, stations and roads, so the railway waits for them.
        if (railChanged) {
            // Only the player lays pipes, as with track.
            anyPipes = map.waterPipe.any { it.toInt() != 0 } || map.sewerPipe.any { it.toInt() != 0 }
            railChanged = false
            updateRail()
        }
        updateWater()
        // Power spreads from the power stations along lines, through buildings and
        // across zoned land, so a line along the back of a zone powers all of it.
        val powered = m.powered
        powered.fill(false)
        head = 0
        tail = 0
        for (b in buildings.values) {
            if (b.type != BuildingType.COAL_PLANT || flooded(b)) continue
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

    // ---- water -------------------------------------------------------------------

    /** Whether there are any water mains or sewers, worked out after the player's changes. */
    private var anyPipes = false

    /** Last month's sewage at each outfall, by its tile, for fouling the water. */
    private val sewage = HashMap<Int, Int>()

    /**
     * Who has mains water and who's on the sewer. Water goes out along the
     * mains from the pumping stations and well fields; the pressure carries it
     * [Balance.PRESSURE_REACH] tiles, counted again from each water tower it
     * reaches. Each network's sources supply so many people, and the buildings
     * nearest them along the mains are served first. The sewers work wherever
     * a network of them has an outfall.
     */
    private fun updateWater() {
        val m = map
        m.watered.fill(false)
        m.sewered.fill(false)
        if (!anyPipes) {
            // Wells and septic tanks all round.
            stats.waterSupply = 0
            stats.waterUsed = 0
            stats.waterShort = 0
            sewage.clear()
            return
        }
        val all = buildings.values.toList()

        // Pressure: steps along the mains from a source, or from a tower the water reached.
        val nearTower = BooleanArray(m.size)
        val steps = IntArray(m.size) { -1 }
        val queue = ArrayDeque<Int>()
        for (b in all) {
            if (b.type != BuildingType.WATER_TOWER && !b.type.waterSource) continue
            forPipesNear(b, m.waterPipe) { i ->
                if (b.type == BuildingType.WATER_TOWER) nearTower[i] = true
                else if (steps[i] != 0) {
                    steps[i] = 0
                    queue.addLast(i)
                }
            }
        }
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val x = i % m.width
            val y = i / m.width
            for (k in 0 until 4) {
                val nx = x + DX[k]
                val ny = y + DY[k]
                if (!m.inside(nx, ny)) continue
                val j = m.index(nx, ny)
                if (m.waterPipe[j].toInt() == 0) continue
                val s = if (nearTower[j]) 0 else steps[i] + 1
                if (s > Balance.PRESSURE_REACH || (steps[j] in 0..s)) continue
                steps[j] = s
                queue.addLast(j)
            }
        }

        // Which network each main is on, and each network's supply.
        val net = components(m.waterPipe)
        val supply = HashMap<Int, Int>()
        var total = 0
        for (b in all) {
            if (!b.type.waterSource) continue
            var n = -1
            forPipesNear(b, m.waterPipe) { i -> if (n < 0) n = net[i] }
            if (n < 0) continue
            val s = sourceSupply(b)
            supply[n] = (supply[n] ?: 0) + s
            total += s
        }

        // Each building's nearest main with pressure, then the nearest served first.
        class Tap(val b: Building, val net: Int, val steps: Int)
        val taps = ArrayList<Tap>()
        for (b in all) {
            if (b.type.capacity == 0 && !b.type.waterSource) continue
            var best = -1
            var bestSteps = Int.MAX_VALUE
            forPipesNear(b, m.waterPipe) { i -> if (steps[i] in 0 until bestSteps) { bestSteps = steps[i]; best = i } }
            if (best >= 0) taps += Tap(b, net[best], bestSteps)
        }
        taps.sortWith(compareBy<Tap>({ it.steps }, { it.b.id }))
        var used = 0
        var short = 0
        for (t in taps) {
            val need = t.b.type.capacity
            val left = supply[t.net] ?: 0
            if (need <= left) {
                supply[t.net] = left - need
                used += need
                forRect(t.b.x, t.b.y, t.b.x + t.b.type.width - 1, t.b.y + t.b.type.height - 1) { m.watered[it] = true }
            } else {
                short += need
            }
        }
        stats.waterSupply = total
        stats.waterUsed = used
        stats.waterShort = short

        // Sewers: a network with an outfall takes the sewage of every building on it.
        val sewers = components(m.sewerPipe)
        val outfalls = HashMap<Int, MutableList<Building>>()
        for (b in all) {
            if (b.type != BuildingType.OUTFALL) continue
            var n = -1
            forPipesNear(b, m.sewerPipe) { i -> if (n < 0) n = sewers[i] }
            if (n >= 0) outfalls.getOrPut(n) { ArrayList() } += b
        }
        val flow = HashMap<Int, Int>()
        for (b in all) {
            if (b.type.capacity == 0) continue
            var n = -1
            forPipesNear(b, m.sewerPipe) { i -> if (n < 0 && outfalls.containsKey(sewers[i])) n = sewers[i] }
            if (n < 0) continue
            forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { m.sewered[it] = true }
            flow[n] = (flow[n] ?: 0) + b.type.capacity
        }
        sewage.clear()
        for ((n, list) in outfalls) {
            val each = (flow[n] ?: 0) / list.size
            for (b in list) sewage[m.index(b.x, b.y)] = each
        }
    }

    /** What a source supplies: a pumping station less if its water is foul, a well field less if its ground is grimy. */
    private fun sourceSupply(b: Building): Int {
        val m = map
        if (flooded(b)) return 0
        return if (b.type == BuildingType.PUMPING_STATION) {
            var foul = 0
            for (ty in b.y - 1..b.y + b.type.height) for (tx in b.x - 1..b.x + b.type.width) {
                if (m.inside(tx, ty) && m.terrain[m.index(tx, ty)] == Terrain.WATER) foul = max(foul, m.foul[m.index(tx, ty)].toInt() and 0xff)
            }
            Balance.PUMP_SUPPLY * (255 - foul * 7 / 10) / 255
        } else {
            val grime = m.grime[m.index(b.x, b.y)].toInt() and 0xff
            Balance.WELL_SUPPLY * (255 - grime * 7 / 10) / 255
        }
    }

    /** Each tile of [pipes] within [Balance.PIPE_REACH] of a building. */
    private inline fun forPipesNear(b: Building, pipes: ByteArray, each: (Int) -> Unit) {
        val r = Balance.PIPE_REACH
        forRect(b.x - r, b.y - r, b.x + b.type.width - 1 + r, b.y + b.type.height - 1 + r) { i -> if (pipes[i].toInt() != 0) each(i) }
    }

    /** Which joined-up network each tile of [pipes] is on, numbered from the north-west, -1 off them. */
    private fun components(pipes: ByteArray): IntArray {
        val m = map
        val out = IntArray(m.size) { -1 }
        var n = 0
        val queue = IntArray(m.size)
        for (start in 0 until m.size) {
            if (pipes[start].toInt() == 0 || out[start] >= 0) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            out[start] = n
            while (head < tail) {
                val i = queue[head++]
                val x = i % m.width
                val y = i / m.width
                for (k in 0 until 4) {
                    val nx = x + DX[k]
                    val ny = y + DY[k]
                    if (!m.inside(nx, ny)) continue
                    val j = m.index(nx, ny)
                    if (pipes[j].toInt() == 0 || out[j] >= 0) continue
                    out[j] = n
                    queue[tail++] = j
                }
            }
            n++
        }
        return out
    }

    /**
     * Sewage spreads through the water from each outfall, thinning with
     * distance, and the water follows it: fouled over a few months, clean
     * again over years.
     */
    private fun updateFoul() {
        val m = map
        val target = IntArray(m.size)
        for ((at, people) in sewage) if (people > 0) spreadFoul(at, people * Balance.FOUL_PER_HUNDRED / 100, target)
        for (i in 0 until m.size) {
            val f = m.foul[i].toInt() and 0xff
            val t = min(255, target[i])
            val next = when {
                t > f -> f + max(1, (t - f) / 4)
                t < f -> f - max(1, (f - t) / 20)
                else -> f
            }
            if (next == f) continue
            val before = m.foulLevel(i)
            m.foul[i] = next.toByte()
            if (m.foulLevel(i) != before) townChanges += i
        }
    }

    /** Adds sewage of [strength] from the outfall at [at] to [into], through the water, thinning with distance. */
    private fun spreadFoul(at: Int, strength: Int, into: IntArray) {
        val m = map
        val steps = IntArray(m.size) { -1 }
        val queue = ArrayDeque<Int>()
        val ox = at % m.width
        val oy = at / m.width
        // The water next to the outfall.
        for (ty in oy - 1..oy + 1) for (tx in ox - 1..ox + 1) {
            if (!m.inside(tx, ty)) continue
            val j = m.index(tx, ty)
            if (m.terrain[j] == Terrain.WATER && steps[j] < 0) {
                steps[j] = 0
                queue.addLast(j)
            }
        }
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            into[i] += strength * (Balance.FOUL_REACH + 1 - steps[i]) / (Balance.FOUL_REACH + 1)
            if (steps[i] == Balance.FOUL_REACH) continue
            val x = i % m.width
            val y = i / m.width
            for (k in 0 until 4) {
                val nx = x + DX[k]
                val ny = y + DY[k]
                if (!m.inside(nx, ny)) continue
                val j = m.index(nx, ny)
                if (m.terrain[j] != Terrain.WATER || steps[j] >= 0) continue
                steps[j] = steps[i] + 1
                queue.addLast(j)
            }
        }
    }

    // ---- stormwater ---------------------------------------------------------------

    /** Tiles that were flooded in the last downpour. */
    var floodedTiles = 0
        private set

    /**
     * A downpour, or snow melting, of [amount] over the whole map. Each tile
     * sheds rain by its share of hard surface. Storm drains to an outfall take
     * all the runoff from beside them, to a pond as much as the pond holds, and
     * a pond catches the rain round it too. Sewers take some of what's left,
     * and it all comes out at their outfalls. The soft ground round about soaks
     * up what it can, and the rest stands as a flood.
     */
    internal fun rainfall(amount: Int, frozen: Boolean = weather.temperature <= 0) {
        val m = map
        val hard = IntArray(m.size) { if (m.terrain[it] == Terrain.WATER) 0 else Stormwater.hardness(m, it) }
        val runoff = IntArray(m.size) { if (m.terrain[it] == Terrain.WATER) 0 else amount * hard[it] / 100 }
        val all = buildings.values.toList()

        // Storm drains: what each network drains into, and how much room it has.
        val drains = components(m.stormPipe)
        val room = HashMap<Int, Int>()
        val ponds = all.filter { it.type == BuildingType.STORM_POND }
        val pondRoom = IntArray(ponds.size) { Balance.POND_HOLDS }
        for (b in all) {
            if (b.type != BuildingType.STORM_OUTFALL && b.type != BuildingType.STORM_POND) continue
            var n = -1
            forPipesNear(b, m.stormPipe) { i -> if (n < 0) n = drains[i] }
            if (n < 0) continue
            room[n] = if (b.type == BuildingType.STORM_OUTFALL) Int.MAX_VALUE else (room[n] ?: 0).let { if (it == Int.MAX_VALUE) it else it + Balance.POND_HOLDS }
        }
        val r = Balance.PIPE_REACH
        if (room.isNotEmpty()) for (i in 0 until m.size) {
            if (runoff[i] == 0) continue
            val x = i % m.width
            val y = i / m.width
            var n = -1
            forRect(x - r, y - r, x + r, y + r) { j -> if (n < 0 && m.stormPipe[j].toInt() != 0 && room.containsKey(drains[j])) n = drains[j] }
            if (n < 0) continue
            val left = room.getValue(n)
            val take = min(left, runoff[i])
            runoff[i] -= take
            if (left != Int.MAX_VALUE) room[n] = left - take
        }
        // Ponds catch the rain round them, as far as they have room.
        for ((k, pond) in ponds.withIndex()) {
            val pr = Balance.POND_REACH
            forRect(pond.x - pr, pond.y - pr, pond.x + pond.type.width - 1 + pr, pond.y + pond.type.height - 1 + pr) { i ->
                val take = min(pondRoom[k], runoff[i])
                runoff[i] -= take
                pondRoom[k] -= take
            }
        }
        // Combined sewers take their share, and overflow at the outfalls.
        var sewersOverflowed = false
        if (sewage.isNotEmpty()) {
            val sewers = components(m.sewerPipe)
            val outfallOf = HashMap<Int, Int>()
            for (b in all) {
                if (b.type != BuildingType.OUTFALL) continue
                forPipesNear(b, m.sewerPipe) { i -> outfallOf.getOrPut(sewers[i]) { m.index(b.x, b.y) } }
            }
            val overflow = HashMap<Int, Int>()
            for (i in 0 until m.size) {
                if (runoff[i] == 0 || !m.sewered[i]) continue
                val x = i % m.width
                val y = i / m.width
                var at = -1
                forRect(x - r, y - r, x + r, y + r) { j -> if (at < 0 && m.sewerPipe[j].toInt() != 0) outfallOf[sewers[j]]?.let { at = it } }
                if (at < 0) continue
                val take = runoff[i] * Balance.SEWER_TAKES / 100
                runoff[i] -= take
                overflow[at] = (overflow[at] ?: 0) + take
                if (take > 0) sewersOverflowed = true
            }
            val bump = IntArray(m.size)
            for ((at, volume) in overflow.entries.sortedBy { it.key }) spreadFoul(at, volume * Balance.FOUL_PER_HUNDRED / 100, bump)
            for (i in 0 until m.size) if (bump[i] > 0) {
                val before = m.foulLevel(i)
                m.foul[i] = min(255, (m.foul[i].toInt() and 0xff) + bump[i]).toByte()
                if (m.foulLevel(i) != before) townChanges += i
            }
        }

        // What's left, against what the soft ground round about soaks up. Water soaks up the most.
        val left = SummedArea(m.width, m.height) { runoff[it] }
        // Frozen or soaked ground takes little in.
        val take = if (frozen) Balance.FROZEN_SOAK else 100 - ground * (100 - Balance.SOAKED_SOAK) / 100
        val soak = SummedArea(m.width, m.height) {
            if (m.terrain[it] == Terrain.WATER) amount * 3 else amount * (100 - hard[it]) / 100 * Balance.ABSORB / 100 * take / 100
        }
        val a = Balance.FLOOD_AREA
        val area = (2 * a + 1) * (2 * a + 1)
        // Some runs off over the ground to lower places, less when they're soaked or frozen too.
        val wetness = if (frozen) 100 else ground
        val runsAway = Balance.RUNS_AWAY_DRY - (Balance.RUNS_AWAY_DRY - Balance.RUNS_AWAY_SOAKED) * wetness / 100
        var worst = -1
        var worstLevel = 0
        val reached = ArrayList<Int>()
        for (i in 0 until m.size) {
            if (m.terrain[i] == Terrain.WATER) continue
            val x = i % m.width
            val y = i / m.width
            val excess = left.around(x, y, a) - soak.around(x, y, a) - runsAway * area
            if (excess <= 0) continue
            val level = min(255, excess * Balance.FLOOD_SCALE / area)
            if (level > (m.flood[i].toInt() and 0xff)) {
                m.flood[i] = level.toByte()
                floodsStanding = true
                if (level >= Balance.FLOODED) reached += i
            }
            if (level >= Balance.FLOODED && level > worstLevel) {
                worstLevel = level
                worst = i
            }
        }
        floodedTiles = reached.size
        if (worst >= 0) events += CityEvent(EventKind.Flooding, worst % m.width, worst / m.width, null)
        afterFlood(reached, sewersOverflowed)
    }

    /** Clean-up owed for floods this month, paid with the month's upkeep. */
    private var floodBill = 0L

    /**
     * What a flood leaves on the tiles it newly [reached]: a clean-up bill, mud,
     * the memory of it, damage where it's deep, and sickness in homes where it
     * carried sewage: on wells and septic tanks, and on the sewer when the
     * sewers [sewersOverflowed]. Flooded power and pumping stations and deep
     * track stop working until it drains.
     */
    private fun afterFlood(reached: List<Int>, sewersOverflowed: Boolean) {
        if (reached.isEmpty()) return
        val m = map
        val hit = HashSet<Int>()
        for (i in reached) {
            val level = m.flood[i].toInt() and 0xff
            if (level > (m.floodMemory[i].toInt() and 0xff)) m.floodMemory[i] = level.toByte()
            val grime = m.grime[i].toInt() and 0xff
            val before = m.grimeLevel(i)
            m.grime[i] = min(255, grime + level * Balance.MUD / 100).toByte()
            if (m.grimeLevel(i) != before) townChanges += i
            if (m.road[i] != Road.NONE) floodBill += Balance.CLEANUP_ROAD
            if (m.rail[i] != Rail.NONE) floodBill += Balance.CLEANUP_TRACK
            if (m.building[i] != 0) hit += m.building[i]
        }
        var sick = -1
        for (id in hit.sorted()) {
            val b = buildings[id] ?: continue
            floodBill += Balance.CLEANUP_BUILDING * max(1, b.type.stage)
            if (b.type.zone == Zone.NONE) continue
            val i = m.index(b.x, b.y)
            if ((m.flood[i].toInt() and 0xff) >= Balance.FLOOD_DAMAGE && rng.nextInt(Balance.FLOOD_DAMAGE_CHANCE) == 0) {
                shrink(b)
                continue
            }
            if (b.type.zone != Zone.RESIDENTIAL) continue
            val chance = when {
                !m.watered[i] || !m.sewered[i] -> Balance.SICK_CHANCE
                sewersOverflowed -> Balance.SICK_CHANCE_SEWER
                else -> 0
            }
            if (chance > 0 && rng.nextInt(chance) == 0) {
                if (sick < 0) sick = i
                b.people?.let { it.health = max(5, it.health - Balance.SICK_HEALTH) }
                shrink(b)
            }
        }
        if (sick >= 0) events += CityEvent(EventKind.Sickness, sick % m.width, sick / m.width, null)
        networksChanged()
        railChanged = true
    }

    /** Whether a building stands in floodwater, deep or not. */
    private fun flooded(b: Building, deep: Boolean = false): Boolean {
        val least = if (deep) Balance.FLOOD_DAMAGE else Balance.FLOODED
        var any = false
        forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { if ((map.flood[it].toInt() and 0xff) >= least) any = true }
        return any
    }

    /** How soaked the ground is, 0 dry to 100 soaked through. */
    internal var ground = 0

    /** How high the rivers and lakes are, 0 low to 100 in spate. Past [Balance.BANKFULL] they're over their banks. */
    var river = 0
        internal set

    /** Rain and melt soak the ground; dry spells dry it, faster when it's warm, not at all while it's frozen. */
    internal fun wetGround(water: Int, frozen: Boolean, temperature: Int) {
        ground = when {
            water > 0 -> min(100, ground + water * Balance.GROUND_WETS / 100)
            frozen -> ground
            else -> max(0, ground - if (temperature >= 15) Balance.GROUND_DRIES_WARM else Balance.GROUND_DRIES)
        }
    }

    /** The rivers rise with rain and melt, most when the ground's too soaked to take any in. */
    internal fun riseRivers(water: Int) {
        val share = Balance.RIVER_RISE_DRY + (Balance.RIVER_RISE_SOAKED - Balance.RIVER_RISE_DRY) * ground / 100
        river = min(100, river + water * share / 100)
    }

    /**
     * A river over its banks spills onto the land beside it, a tile or more
     * deep the higher it is, shallower the further from the water. It can't get
     * past an embankment.
     */
    internal fun overflowRivers() {
        val m = map
        val over = river - Balance.BANKFULL
        if (over <= 0) return
        val reach = min(Balance.SPILL_MOST, 1 + over / Balance.SPILL_STEP)
        val steps = IntArray(m.size) { -1 }
        val queue = ArrayDeque<Int>()
        for (i in 0 until m.size) if (m.terrain[i] == Terrain.WATER) {
            steps[i] = 0
            queue.addLast(i)
        }
        val reached = ArrayList<Int>()
        var worst = -1
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            if (steps[i] == reach) continue
            val x = i % m.width
            val y = i / m.width
            for (k in 0 until 4) {
                val nx = x + DX[k]
                val ny = y + DY[k]
                if (!m.inside(nx, ny)) continue
                val j = m.index(nx, ny)
                if (steps[j] >= 0 || m.bank[j].toInt() != 0) continue
                steps[j] = steps[i] + 1
                val level = min(255, Balance.FLOODED + 30 + over * 3 - 30 * (steps[j] - 1))
                if (level > (m.flood[j].toInt() and 0xff)) {
                    m.flood[j] = level.toByte()
                    floodsStanding = true
                    if (level >= Balance.FLOODED) {
                        reached += j
                        if (worst < 0 && m.building[j] != 0) worst = j
                    }
                }
                queue.addLast(j)
            }
        }
        if (reached.isNotEmpty()) {
            val at = if (worst >= 0) worst else reached.first()
            events += CityEvent(EventKind.RiverFlood, at % m.width, at / m.width, null)
        }
        afterFlood(reached, sewersOverflowed = false)
    }

    /** The memory of floods fades a little each month. */
    private fun fadeFloodMemory() {
        val f = map.floodMemory
        for (i in f.indices) {
            val v = f[i].toInt() and 0xff
            if (v > 0) f[i] = max(0, v - Balance.STIGMA_FADE).toByte()
        }
    }

    /** Whether any floodwater is standing, so dry days needn't look. */
    private var floodsStanding = false

    /** Floodwater goes down a little each day. */
    private fun drainFloods() {
        if (!floodsStanding) return
        // Shops and works under water are shut today.
        for (b in buildings.values) {
            if ((b.type.zone == Zone.COMMERCIAL || b.type.zone == Zone.INDUSTRIAL) && flooded(b)) b.closedDays++
        }
        val f = map.flood
        var any = false
        var receded = false
        for (i in f.indices) {
            val v = f[i].toInt() and 0xff
            if (v == 0) continue
            val next = max(0, v - Balance.FLOOD_DRAIN)
            f[i] = next.toByte()
            if (next > 0) any = true
            if ((v >= Balance.FLOODED && next < Balance.FLOODED) || (v >= Balance.FLOOD_DAMAGE && next < Balance.FLOOD_DAMAGE)) receded = true
        }
        floodsStanding = any
        // Whatever the water shut down may be working again.
        if (receded) {
            networksChanged()
            railChanged = true
        }
    }

    /** Buildings that need mains water or the sewer and are without come down a stage now and then. */
    private fun waterCuts() {
        val out = buildings.values.filter {
            val i = map.index(it.x, it.y)
            (it.type.needsWater && !map.watered[i]) || (it.type.needsSewer && !map.sewered[i])
        }
        for (b in out) if (rng.nextInt(3) == 0) shrink(b)
    }

    // ---- people ------------------------------------------------------------------

    /** How much of what schools and health care ask for they get, in percent. Less money, fewer places, less reach. */
    var schoolFunding = 100
    var healthFunding = 100

    /** Last month's share of the jobs at each level of schooling left unfilled, in percent. */
    private val skillShortage = IntArray(Education.LEVELS)

    /** How far each class of home falls short of the share the town's jobs call for, in thousandths. */
    private val wealthGap = IntArray(Wealth.LEVELS)

    /** People who moved in and out this month so far. */
    private var arrivals = 0
    private var departures = 0

    /** Whether the town is short of the people to staff a business of [type]: a level of schooling it leans on that's a quarter unfilled. */
    private fun skillsShort(type: BuildingType): Boolean {
        val skills = Demography.jobSkills(type)
        for (k in 0 until Education.LEVELS) {
            if (skills[k] >= 20 && skillShortage[k] >= Balance.SKILL_SHORT) return true
        }
        return false
    }

    /**
     * The class a new home on lot [i] is built for: what its land allows (the
     * poor can't afford dear land, the well off won't live on cheap), and of
     * that, what the town's jobs call for most.
     */
    private fun chooseWealth(i: Int): Int {
        val value = map.landValue[i].toInt() and 0xff
        val allowed = when {
            value < Balance.POOR_BELOW -> intArrayOf(Wealth.POOR, Wealth.MIDDLE)
            value >= Balance.WELL_OFF_FROM -> intArrayOf(Wealth.MIDDLE, Wealth.WELL_OFF)
            else -> intArrayOf(Wealth.POOR, Wealth.MIDDLE, Wealth.WELL_OFF)
        }
        var best = allowed[0]
        for (w in allowed) if (wealthGap[w] > wealthGap[best]) best = w
        return best
    }

    /** Fills a home with newcomers as it grows, or has some move out as it shrinks. Nothing for other buildings. */
    private fun fitHousehold(b: Building) {
        if (b.type.zone != Zone.RESIDENTIAL) {
            b.people = null
            return
        }
        val h = b.people ?: Household(0, 0, 0, chooseWealth(map.index(b.x, b.y))).also {
            b.people = it
            arrive(it, b.type.capacity)
        }
        // An empty home stays empty, whatever its size, until it sells.
        if (h.empty) return
        val gap = b.type.capacity - h.size
        if (gap > 0) arrive(h, gap) else if (gap < 0) leave(h, -gap, youngFirst = false)
    }

    /**
     * What's owed to each kind of newcomer, in ten-thousandths of a person:
     * children, adults by schooling, the elderly. Carried from one home to the
     * next so a town of small homes comes out in the right shares.
     */
    private val newcomerCarry = IntArray(2 + Education.LEVELS)

    /** [count] newcomers, made up as the era's households are and schooled as the region is. */
    private fun arrive(h: Household, count: Int) {
        val ages = Demography.newcomerAges(year)
        val schooling = Demography.newcomerSchooling(year)
        val shares = IntArray(newcomerCarry.size) { k ->
            when (k) {
                0 -> ages[0] * 100
                newcomerCarry.size - 1 -> ages[2] * 100
                else -> ages[1] * schooling[k - 1]
            }
        }
        repeat(count) {
            var pick = 0
            for (k in shares.indices) {
                newcomerCarry[k] += shares[k]
                if (newcomerCarry[k] > newcomerCarry[pick]) pick = k
            }
            newcomerCarry[pick] -= 10_000
            when (pick) {
                0 -> h.children++
                newcomerCarry.size - 1 -> h.elderly++
                else -> {
                    h.adults++
                    h.schooled[pick - 1]++
                }
            }
        }
        arrivals += count
    }

    /**
     * [count] people move out: when a home's full, young adults moving on
     * ([youngFirst]), else some of everyone, as when a building is cut down a size.
     */
    private fun leave(h: Household, count: Int, youngFirst: Boolean) {
        var left = min(count, h.size)
        departures += left
        if (youngFirst) {
            val adults = min(left, h.adults)
            removeAdults(h, adults)
            left -= adults
            val kids = min(left, h.children)
            h.children -= kids
            left -= kids
            h.elderly -= min(left, h.elderly)
            return
        }
        val size = h.size
        val kids = min(h.children, left * h.children / size)
        val elders = min(h.elderly, left * h.elderly / size)
        h.children -= kids
        h.elderly -= elders
        left -= kids + elders
        val adults = min(left, h.adults)
        removeAdults(h, adults)
        left -= adults
        val more = min(left, h.children)
        h.children -= more
        h.elderly -= min(left - more, h.elderly)
    }

    /** Takes [n] adults away, each from a level of schooling picked by chance in proportion to who's there. */
    private fun removeAdults(h: Household, n: Int) {
        repeat(min(n, h.adults)) {
            var pick = rng.nextInt(h.adults)
            var k = 0
            while (pick >= h.schooled[k]) pick -= h.schooled[k++]
            h.schooled[k]--
            h.adults--
        }
    }

    /**
     * Empty homes for sale: quick to sell when many are looking for a home,
     * slow when few are, and slower still when the town has more homes than
     * people. New owners are whoever the street suits now.
     */
    internal fun sell(homes: List<Building>) {
        val looking = stats.homeSeekers * 100 / max(100, stats.population)
        val chance = (Balance.SALE_BASE + looking * Balance.SALE_PER_DEMAND).coerceIn(Balance.SALE_LEAST, Balance.SALE_MOST)
        for (b in homes) {
            val h = b.people!!
            if (!h.empty) continue
            if (rng.nextInt(100) >= chance) {
                h.forSale++
                continue
            }
            h.forSale = 0
            h.wealth = chooseWealth(map.index(b.x, b.y))
            h.health = 60
            arrive(h, b.type.capacity)
            markForSale(b)
        }
    }

    /** [count] times [perThousand] thousandths, the part left over rounded up by chance. */
    private fun flow(count: Int, perThousand: Int): Int {
        val exact = count * perThousand
        return exact / 1000 + if (exact % 1000 > 0 && rng.nextInt(1000) < exact % 1000) 1 else 0
    }

    /** A month's move of [value] towards [target]: a [pace]th of the way, at least a step. */
    private fun towards(value: Int, target: Int, pace: Int): Int {
        val step = (target - value) / pace
        return value + if (step != 0) step else (target - value).coerceIn(-1, 1)
    }

    /**
     * Places for [need] in each home at the buildings of [type], each taking
     * [places] fully funded, within [reach] of it, nearest homes first. By home.
     */
    private fun allot(type: BuildingType, places: Int, reach: Int, funding: Int, homes: List<Building>, need: (Building) -> Int): HashMap<Int, Int> {
        val got = HashMap<Int, Int>()
        val r = reach(reach, funding)
        for (place in buildings.values) {
            if (place.type != type) continue
            var room = places * funding / 100
            val cx = place.x + place.type.width / 2
            val cy = place.y + place.type.height / 2
            val near = homes.filter { abs(it.x - cx) + abs(it.y - cy) <= r }
                .sortedWith(compareBy<Building>({ abs(it.x - cx) + abs(it.y - cy) }, { it.id }))
            for (b in near) {
                if (room == 0) break
                val want = need(b) - (got[b.id] ?: 0)
                if (want <= 0) continue
                val t = min(room, want)
                got[b.id] = (got[b.id] ?: 0) + t
                room -= t
            }
        }
        return got
    }

    /**
     * A month in every home: school and doctors for those with a place, health
     * moving towards what the place gives it, children born and growing up,
     * adults growing old, people dying, and the home kept full by the young
     * moving on or newcomers moving in.
     */
    private fun updatePeople() {
        val m = map
        val s = stats
        val homes = this.homes
        var births = 0
        var deaths = 0
        var emptied = 0
        val pupils = allot(BuildingType.SCHOOL, Balance.SCHOOL_PLACES, Balance.SCHOOL_REACH, schoolFunding, homes) { it.people!!.children }
        val teens = allot(BuildingType.HIGH_SCHOOL, Balance.HIGH_SCHOOL_PLACES, Balance.HIGH_SCHOOL_REACH, schoolFunding, homes) { it.people!!.children / Balance.TEENS }
        val clinic = allot(BuildingType.CLINIC, Balance.CLINIC_CARES, Balance.CLINIC_REACH, healthFunding, homes) { it.people!!.size }
        val hospital = allot(BuildingType.HOSPITAL, Balance.HOSPITAL_CARES, Balance.HOSPITAL_REACH, healthFunding, homes) { it.people!!.size - (clinic[it.id] ?: 0) }
        val parks = SummedArea(m.width, m.height) { if (m.buildingType[it].toInt() - 1 == BuildingType.PARK.ordinal) 1 else 0 }
        s.pupils = pupils.values.sum()
        s.highSchoolPupils = teens.values.sum()
        s.cared = clinic.values.sum() + hospital.values.sum()
        sell(homes)
        for (b in homes) {
            val h = b.people!!
            if (h.empty) continue
            val i = m.index(b.x, b.y)
            // School: the share of the children with a place, which their schooling follows.
            val kids = h.children
            val atSchool = if (kids == 0) 0 else min(100, (pupils[b.id] ?: 0) * 100 / kids)
            h.schooling = towards(h.schooling, atSchool, Balance.SCHOOLING_PACE)
            val older = kids / Balance.TEENS
            val atHighSchool = if (older == 0) 0 else min(100, (teens[b.id] ?: 0) * 100 / older)
            h.highSchooling = towards(h.highSchooling, atHighSchool, Balance.SCHOOLING_PACE)

            // Health, towards what the place gives it.
            val careShare = if (h.size == 0) 0 else min(100, ((clinic[b.id] ?: 0) + (hospital[b.id] ?: 0)) * 100 / h.size)
            var target = Balance.HEALTH_BASE + Balance.CARE_HEALTH * careShare / 100 + Balance.WEALTH_HEALTH * h.wealth
            if (m.watered[i]) target += Balance.MAINS_HEALTH
            if (m.sewered[i]) target += Balance.MAINS_HEALTH
            if (parks.around(b.x, b.y, 4) > 0) target += Balance.PARK_HEALTH
            target -= (m.pollution[i].toInt() and 0xff) / Balance.POLLUTION_HEALTH + m.grimeLevel(i) * Balance.GRIME_HEALTH
            if (b.type == BuildingType.TENEMENT) target -= Balance.CROWDING_HEALTH
            h.health = towards(h.health, target.coerceIn(5, 100), Balance.HEALTH_PACE)

            // Born, growing up, growing old, dying.
            val factor = Demography.healthFactor(h.health)
            val born = flow(h.adults, Demography.births(year, h.wealth))
            val grown = min(h.children, flow(h.children, Demography.GROWING_UP))
            val childDied = min(h.children - grown, flow(h.children, Demography.childDeaths(year) * factor / 100))
            val aged = min(h.adults, flow(h.adults, Demography.GROWING_OLD))
            val adultDied = min(h.adults - aged, flow(h.adults, Demography.adultDeaths(year) * factor / 100))
            val elderDied = min(h.elderly, flow(h.elderly, Demography.elderlyDeaths(year) * factor / 100))
            removeAdults(h, aged + adultDied)
            // Each child grown up has had the schooling the home's children get, as far as chance goes.
            repeat(grown) {
                val level = when {
                    rng.nextInt(100) >= h.schooling -> Education.UNSCHOOLED
                    rng.nextInt(100) >= h.highSchooling -> Education.SCHOOLED
                    else -> Education.EDUCATED
                }
                h.schooled[level]++
            }
            h.adults += grown
            h.children += born - grown - childDied
            h.elderly += aged - elderDied
            births += born
            deaths += childDied + adultDied + elderDied

            // With the last of the grown-ups gone the children go to family elsewhere, and the home's put up for sale.
            if (h.adults + h.elderly == 0) {
                departures += h.children
                h.children = 0
                h.schooled.fill(0)
                h.schooling = 0
                h.highSchooling = 0
                emptied++
                markForSale(b)
                continue
            }

            // Kept full.
            val gap = b.type.capacity - h.size
            if (gap > 0) arrive(h, gap) else if (gap < 0) leave(h, -gap, youngFirst = true)
        }
        s.births = births
        s.deaths = deaths
        s.emptied = emptied
        s.movedIn = arrivals
        s.movedOut = departures
        arrivals = 0
        departures = 0
        var places = 0
        var highPlaces = 0
        var carePlaces = 0
        for (b in buildings.values) when (b.type) {
            BuildingType.SCHOOL -> places += Balance.SCHOOL_PLACES * schoolFunding / 100
            BuildingType.HIGH_SCHOOL -> highPlaces += Balance.HIGH_SCHOOL_PLACES * schoolFunding / 100
            BuildingType.CLINIC -> carePlaces += Balance.CLINIC_CARES * healthFunding / 100
            BuildingType.HOSPITAL -> carePlaces += Balance.HOSPITAL_CARES * healthFunding / 100
            else -> {}
        }
        s.schoolPlaces = places
        s.highSchoolPlaces = highPlaces
        s.carePlaces = carePlaces
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
            // No one to build on to an empty home.
            if (b?.people?.empty == true) return@repeat
            if (next.needsPower && !map.powered[i]) return@repeat
            if ((next.needsWater && !map.watered[i]) || (next.needsSewer && !map.sewered[i])) return@repeat
            // Businesses can't grow into what the town hasn't the people to staff.
            if (zone != Zone.RESIDENTIAL && skillsShort(next)) return@repeat
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
            fitHousehold(b)
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
            val empty = buildings[map.building[i]]?.people?.empty == true
            val score = attraction(i, zone) - if (empty) Balance.EMPTY_SHRINK else 0
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
            fitHousehold(b)
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
                // Mains water and the sewer are wanted; a well in grimy ground is not.
                if (m.watered[i]) score += Balance.MAINS_APPEAL else if (m.grimeLevel(i) >= 2) score -= Balance.BAD_WELL
                if (m.sewered[i]) score += Balance.SEWER_APPEAL
                // The well off ask more of a place; the poor put up with more.
                val home = buildings[m.building[i]]?.people
                when (home?.wealth ?: chooseWealth(i)) {
                    Wealth.WELL_OFF -> score += value / 6 - pollution / 4 - crime / 6
                    Wealth.POOR -> score += pollution / 6
                }
                // People leave unhealthy homes.
                if (home != null && !home.empty && home.health < Balance.UNHEALTHY) score -= (Balance.UNHEALTHY - home.health) / 2
            }
            Zone.COMMERCIAL -> {
                var people = 0
                around(x, y, 6) { j, _ ->
                    val b = buildings[m.building[j]]
                    people += b?.people?.size ?: 0
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
        val stigma = if (zone == Zone.RESIDENTIAL) (m.floodMemory[i].toInt() and 0xff) / Balance.STIGMA_APPEAL else 0
        return score - floodPenalty(i) - stigma
    }

    /** How much a long commute puts people off, from the commute layer's half minutes. */
    private fun commutePenalty(c: Int): Int = when (c) {
        0 -> 0
        255 -> Balance.NO_COMMUTE
        else -> min(Balance.LONG_COMMUTE, max(0, (c - 1) / 2 - Balance.FINE_COMMUTE) / 2)
    }

    /** Appeal a lot loses while it stands in floodwater. */
    private fun floodPenalty(i: Int): Int = if ((map.flood[i].toInt() and 0xff) >= Balance.FLOODED) Balance.FLOOD_APPEAL else 0

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

    /**
     * The town's people and jobs: who lives here by age, schooling and wealth,
     * the jobs by the schooling they want, and workers matched to them
     * town-wide, the best schooled to the jobs that need them most and the
     * rest down from there.
     */
    private fun census() {
        val s = stats
        var residents = 0
        var shopJobs = 0
        var industryJobs = 0
        var otherJobs = 0
        var health = 0L
        var spending = 0L
        s.children = 0; s.adults = 0; s.elderly = 0; s.workers = 0; s.emptyHomes = 0; s.emptyRoom = 0
        s.workersBy.fill(0); s.byWealth.fill(0)
        val jobsBy = LongArray(Education.LEVELS)
        for (b in buildings.values) {
            val c = b.type.capacity
            when (b.type.zone) {
                Zone.RESIDENTIAL -> residents += b.people?.size ?: 0
                Zone.COMMERCIAL -> shopJobs += c
                Zone.INDUSTRIAL -> industryJobs += c
                else -> otherJobs += c
            }
            val h = b.people
            if (h != null) {
                if (h.empty) {
                    s.emptyHomes++
                    s.emptyRoom += c
                }
                s.children += h.children; s.adults += h.adults; s.elderly += h.elderly
                s.workers += h.workers()
                for (k in 0 until Education.LEVELS) s.workersBy[k] += h.workersAt(k)
                s.byWealth[h.wealth] += h.size
                health += h.health.toLong() * h.size
                spending += h.size.toLong() * Demography.SPENDING_BY_WEALTH[h.wealth] / 100
            } else if (c > 0) {
                val skills = Demography.jobSkills(b.type)
                for (k in 0 until Education.LEVELS) jobsBy[k] += c.toLong() * skills[k]
            }
        }
        s.population = residents
        s.shopJobs = shopJobs
        s.industryJobs = industryJobs
        s.otherJobs = otherJobs
        s.health = if (residents == 0) 0 else (health / residents).toInt()
        s.spending = spending.toInt()
        for (k in 0 until Education.LEVELS) s.jobsBy[k] = (jobsBy[k] / 100).toInt()

        // Each level of job filled first by workers at that level, then by better schooled ones with nothing better.
        val free = s.workersBy.copyOf()
        s.filledBy.fill(0)
        for (job in Education.LEVELS - 1 downTo 0) {
            for (worker in job until Education.LEVELS) {
                val take = min(s.jobsBy[job] - s.filledBy[job], free[worker])
                s.filledBy[job] += take
                free[worker] -= take
            }
        }
        val idle = free.sum()
        s.unemployment = if (s.workers == 0) 0 else idle * 100 / s.workers
        for (k in 0 until Education.LEVELS) {
            skillShortage[k] = if (s.jobsBy[k] == 0) 0 else (s.jobsBy[k] - s.filledBy[k]) * 100 / s.jobsBy[k]
        }

        // The homes the jobs call for: well off for educated work, middling for schooled, poor for the rest.
        val filled = s.filledBy.sum()
        val want = if (filled == 0) intArrayOf(500, 400, 100) else IntArray(Wealth.LEVELS) { s.filledBy[it] * 1000 / filled }
        for (w in 0 until Wealth.LEVELS) wealthGap[w] = want[w] - (if (residents == 0) 0 else s.byWealth[w] * 1000 / residents)
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
            (1 + Balance.EXPORT_GROWTH * years) * (if (connected) 1.0 else Balance.UNCONNECTED_EXPORTS) *
            (if (railFreight) Balance.RAIL_EXPORTS else 1.0)
        val jobs = s.shopJobs + s.industryJobs + s.otherJobs
        s.industryDemand = taxed(market - s.industryJobs, industrialTax)
        // The shops answer what people spend, more the better off they are.
        s.commercialDemand = taxed(s.spending / Balance.RESIDENTS_PER_SHOP_JOB - s.shopJobs, commercialTax)
        val settlers = (Balance.SETTLERS + Balance.SETTLERS_PER_RESIDENT * s.population) * (if (railPassengers) Balance.RAIL_SETTLERS else 1.0)
        // Homes for the people the jobs need, children and the elderly with them.
        val workersPerResident = if (s.population == 0) Balance.LABOUR_SHARE else (s.workers.toDouble() / s.population).coerceIn(0.25, 0.6)
        val seekers = jobs / workersPerResident + settlers - s.population
        s.homeSeekers = taxed(seekers, residentialTax)
        // The empty homes take what they can of it before anyone builds.
        s.residentialDemand = taxed(seekers - s.emptyRoom, residentialTax)
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
     * land. Upkeep goes out on roads, track, pipes, lines, the power stations,
     * the waterworks and the services, each service's scaled by its funding.
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
        var stations = 0
        var yards = 0
        var waterworks = 0.0
        var schools = 0.0
        var care = 0.0
        val days = daysIn(if (month == 0) 11 else month - 1, year).toDouble()
        for (b in buildings.values) {
            when (b.type) {
                BuildingType.SCHOOL -> schools += Balance.SCHOOL_UPKEEP
                BuildingType.HIGH_SCHOOL -> schools += Balance.HIGH_SCHOOL_UPKEEP
                BuildingType.CLINIC -> care += Balance.CLINIC_UPKEEP
                BuildingType.HOSPITAL -> care += Balance.HOSPITAL_UPKEEP
                else -> {}
            }
            waterworks += when (b.type) {
                BuildingType.PUMPING_STATION -> Balance.PUMP_UPKEEP
                BuildingType.WELL_FIELD -> Balance.WELL_UPKEEP
                BuildingType.WATER_TOWER -> Balance.TOWER_UPKEEP
                BuildingType.OUTFALL -> Balance.OUTFALL_UPKEEP
                BuildingType.STORM_POND -> Balance.POND_UPKEEP
                BuildingType.STORM_OUTFALL -> Balance.STORM_OUTFALL_UPKEEP
                else -> 0.0
            }
            // Shops and works pay nothing for the days they were shut by floods.
            val open = 1.0 - min(b.closedDays.toDouble(), days) / days
            b.closedDays = 0
            val worth = (0.5 + (map.landValue[map.index(b.x, b.y)].toInt() and 0xff) / 200.0) * open
            when {
                b.type.zone == Zone.RESIDENTIAL -> homes += b.type.capacity * worth * Demography.TAX_BY_WEALTH[b.people?.wealth ?: Wealth.MIDDLE] / 100.0
                b.type.zone == Zone.COMMERCIAL -> shops += b.type.capacity * worth
                b.type.zone == Zone.INDUSTRIAL -> works += b.type.capacity * worth
                b.type == BuildingType.POLICE_STATION -> police++
                b.type == BuildingType.FIRE_STATION -> fire++
                b.type == BuildingType.PARK -> parks++
                b.type == BuildingType.COAL_PLANT -> plants++
                b.type.station -> stations++
                b.type.yard -> yards++
            }
        }
        s.residentialIncome = (homes * residentialTax * Balance.RESIDENT_TAX).roundToLong()
        s.commercialIncome = (shops * commercialTax * Balance.JOB_TAX).roundToLong()
        s.industrialIncome = (works * industrialTax * Balance.JOB_TAX).roundToLong()
        var roads = 0.0
        var lines = 0
        var track = 0.0
        for (i in 0 until map.size) {
            val bridge = if (map.terrain[i] == Terrain.WATER) Balance.BRIDGE_UPKEEP else 1.0
            val road = RoadType.of(map.road[i])
            if (road != null) roads += road.upkeep * bridge
            if (map.power[i] != Power.NONE) lines++
            if (map.rail[i] != Rail.NONE) track += Balance.RAIL_UPKEEP * bridge
            waterworks += (map.waterPipe[i] + map.sewerPipe[i] + map.stormPipe[i] + map.bank[i]) * Balance.PIPE_UPKEEP
        }
        s.waterUpkeep = waterworks.roundToLong()
        s.roadUpkeep = (roads + lines * Balance.LINE_UPKEEP).roundToLong()
        s.railUpkeep = (track + stations * Balance.STATION_UPKEEP + yards * Balance.YARD_UPKEEP).roundToLong()
        s.powerUpkeep = (plants * Balance.PLANT_UPKEEP).roundToLong()
        s.policeUpkeep = (police * Balance.POLICE_UPKEEP * policeFunding / 100).roundToLong()
        s.fireUpkeep = (fire * Balance.FIRE_UPKEEP * fireFunding / 100).roundToLong()
        s.parkUpkeep = (parks * Balance.PARK_UPKEEP * parkFunding / 100).roundToLong()
        s.income = s.residentialIncome + s.commercialIncome + s.industrialIncome
        s.floodCost = floodBill
        floodBill = 0
        s.schoolUpkeep = (schools * schoolFunding / 100).roundToLong()
        s.healthUpkeep = (care * healthFunding / 100).roundToLong()
        s.upkeep = s.roadUpkeep + s.railUpkeep + s.waterUpkeep + s.powerUpkeep + s.policeUpkeep + s.fireUpkeep + s.parkUpkeep + s.floodCost + s.schoolUpkeep + s.healthUpkeep
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
        // Since version 3.
        w.layer(map.rail); w.layer(map.railBusy)
        w.long(stats.railUpkeep)
        traffic.writeRail(w)
        w.count(trainRoutes.size)
        for (t in trainRoutes) {
            w.count(t.tiles.size)
            for (i in t.tiles) w.int(i)
            w.bool(t.passengers); w.int(t.load)
        }
        // Since version 4.
        w.layer(map.waterPipe); w.layer(map.sewerPipe); w.layer(map.stormPipe); w.layer(map.foul); w.layer(map.flood); w.layer(map.bank)
        w.int(ground); w.int(river)
        w.layer(map.floodMemory); w.long(floodBill); w.long(stats.floodCost)
        val shut = buildings.values.filter { it.closedDays > 0 }
        w.count(shut.size)
        for (b in shut) { w.int(b.id); w.int(b.closedDays) }
        w.long(stats.waterUpkeep)
        w.count(sewage.size)
        for ((at, people) in sewage.entries.sortedBy { it.key }) { w.int(at); w.int(people) }
        // Since version 5.
        w.int(schoolFunding); w.int(healthFunding)
        for (v in skillShortage) w.int(v)
        for (v in wealthGap) w.int(v)
        w.int(arrivals); w.int(departures)
        for (v in newcomerCarry) w.int(v)
        w.count(homes.size)
        for (b in homes) { w.int(b.id); b.people!!.writeTo(w) }
        for (v in s.peopleNumbers()) w.int(v)
        w.long(s.schoolUpkeep); w.long(s.healthUpkeep)
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
            if (version >= 3) {
                r.layer(m.rail); r.layer(m.railBusy)
                s.railUpkeep = r.long()
                c.traffic.readRail(r)
                c.trainRoutes = List(r.count()) {
                    val tiles = IntArray(r.count()) { r.int() }
                    if (tiles.any { it !in 0 until m.size }) throw SaveError("a train runs off the map")
                    TrainRoute(tiles, r.bool(), r.int())
                }
            }
            if (version >= 4) {
                r.layer(m.waterPipe); r.layer(m.sewerPipe); r.layer(m.stormPipe); r.layer(m.foul); r.layer(m.flood); r.layer(m.bank)
                c.ground = r.int(); c.river = r.int()
                r.layer(m.floodMemory); c.floodBill = r.long(); s.floodCost = r.long()
                repeat(r.count()) {
                    val id = r.int()
                    val days = r.int()
                    c.buildings[id]?.closedDays = days
                }
                c.floodsStanding = m.flood.any { it.toInt() != 0 }
                s.waterUpkeep = r.long()
                repeat(r.count()) { c.sewage[r.int()] = r.int() }
            }
            if (version >= 5) {
                c.schoolFunding = r.int(); c.healthFunding = r.int()
                for (k in c.skillShortage.indices) c.skillShortage[k] = r.int()
                for (k in c.wealthGap.indices) c.wealthGap[k] = r.int()
                c.arrivals = r.int(); c.departures = r.int()
                for (k in c.newcomerCarry.indices) c.newcomerCarry[k] = r.int()
                repeat(r.count()) {
                    val b = c.buildings[r.int()] ?: throw SaveError("people in a home that isn't there")
                    b.people = Household.readFrom(r)
                    c.stamp(b)
                }
                s.readPeopleNumbers(r)
                s.schoolUpkeep = r.long(); s.healthUpkeep = r.long()
            } else {
                // Older towns had no people as such, only room for them: each home is filled as newcomers would fill it.
                for (b in c.buildings.values.sortedBy { it.id }) c.fitHousehold(b)
                c.arrivals = 0
                c.census()
            }
            // Worked out now rather than on the first day, so a city loaded paused shows its power and water.
            c.updateNetworks()
            return c
        }

        const val DEFAULT_SIZE = 128
        const val START_YEAR = 1900
        const val START_FUNDS = 20_000L

        /** How many actions can be undone. */
        const val MAX_UNDO = 100

        /** What [roadCost] says when a tile's road stays as it is, or can't be built. */
        private const val NO_CHANGE = -1L
        private const val BLOCKED = -2L

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

    /** The people by age, adults' schooling and wealth. */
    var children = 0
    var adults = 0
    var elderly = 0
    val byWealth = IntArray(Wealth.LEVELS)

    /** Workers, jobs and jobs filled at each level of schooling. */
    val workersBy = IntArray(Education.LEVELS)
    val jobsBy = IntArray(Education.LEVELS)
    val filledBy = IntArray(Education.LEVELS)

    /** Average health, 0 to 100, and what the town spends in the shops, in middling residents. */
    var health = 0
    var spending = 0

    /** Last month: born, died, moved in and out, and homes left empty by the last of their people dying. */
    var births = 0
    var deaths = 0
    var movedIn = 0
    var movedOut = 0
    var emptied = 0

    /** Homes standing empty for sale, and the people they'd hold. */
    var emptyHomes = 0
    var emptyRoom = 0

    /** Places at school, high school and with a doctor, and how many have one. */
    var schoolPlaces = 0
    var pupils = 0
    var highSchoolPlaces = 0
    var highSchoolPupils = 0
    var carePlaces = 0
    var cared = 0

    /** People looking for a home, in residents, before the empty homes take any. */
    var homeSeekers = 0

    internal fun peopleNumbers(): IntArray = intArrayOf(
        children, adults, elderly, *byWealth, *workersBy, *jobsBy, *filledBy, health, spending,
        births, deaths, movedIn, movedOut, emptied, emptyHomes, emptyRoom,
        schoolPlaces, pupils, highSchoolPlaces, highSchoolPupils, carePlaces, cared, homeSeekers,
    )

    internal fun readPeopleNumbers(r: SaveReader) {
        children = r.int(); adults = r.int(); elderly = r.int()
        for (a in arrayOf(byWealth, workersBy, jobsBy, filledBy)) for (k in a.indices) a[k] = r.int()
        health = r.int(); spending = r.int()
        births = r.int(); deaths = r.int(); movedIn = r.int(); movedOut = r.int(); emptied = r.int(); emptyHomes = r.int(); emptyRoom = r.int()
        schoolPlaces = r.int(); pupils = r.int(); highSchoolPlaces = r.int(); highSchoolPupils = r.int(); carePlaces = r.int(); cared = r.int()
        homeSeekers = r.int()
    }

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
    var railUpkeep = 0L
    var waterUpkeep = 0L

    /** Last month's clean-up after floods. */
    var floodCost = 0L

    /** People the sources can supply, people supplied, and people near a main who go without for want of supply. */
    var waterSupply = 0
    var waterUsed = 0
    var waterShort = 0
    var powerUpkeep = 0L
    var policeUpkeep = 0L
    var fireUpkeep = 0L
    var parkUpkeep = 0L
    var schoolUpkeep = 0L
    var healthUpkeep = 0L
    var upkeep = 0L

    /** Averages: crime and pollution where there are buildings, land value over all the land. 0 to 255. */
    var crime = 0
    var pollution = 0
    var landValue = 0

    val jobs get() = shopJobs + industryJobs + otherJobs
}

/** A line a train ran last month: the track from end to end, and whether it carried passengers or freight, and how many. */
class TrainRoute(val tiles: IntArray, val passengers: Boolean, val load: Int)

enum class EventKind { FireStarted, FireSaved, BuildingLost, Flooding, RiverFlood, Sickness }

/** Something that happened at [x], [y], to a building of [type] if it's about one. */
class CityEvent(val kind: EventKind, val x: Int, val y: Int, val type: BuildingType?)

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
