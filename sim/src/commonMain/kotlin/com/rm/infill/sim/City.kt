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
    val weather = Weather(seed, terrain?.climate ?: Climate.TEMPERATE)

    /** The map's climate. */
    val climate: Climate get() = weather.climate

    /** Whole dollars. */
    var funds: Long = START_FUNDS
        private set

    var year: Int = START_YEAR
        private set

    /** 0 is January. */
    var month: Int = 0
        private set

    /** The town's era, which gates what it can build. Moves on in [newEra]. */
    var era: Era = Era.TOWNSHIP
        internal set

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
        // Water a low bridge would go over, which ships can't pass.
        val lowDecks = ArrayList<Int>()
        var cost = 0L
        var noRoute = false
        val m = map
        when (action) {
            is Action.BuildRoad -> if (action.tunnel) {
                val layout = roadLayout(action)
                cost += planTunnel(layout.tiles, layout.runs, layout.turns, roadSegments(action.type, layout.tiles.size), action.type, changes, blocked)
            } else {
                val layout = roadLayout(action)
                for (k in layout.tiles.indices) {
                    val i = layout.tiles[k]
                    if (m.terrain[i] == Terrain.WATER && layout.bridges[k] >= 0 && blocksShips(layout.bridges, k)) lowDecks += i
                    val road = roadCost(action.type, layout, k)
                    // A road the town can't build yet is blocked all along.
                    if (road == BLOCKED || !allows(action.type)) {
                        blocked += i
                        continue
                    }
                    // Drawn over a worn road of its own kind, the road's relaid, and the worn pipes under it with it, for less.
                    val renew = roadRenewable(action.type, layout, k)
                    val relay = if (renew) action.type.price * Balance.RENEW_ROAD / 100 else 0L
                    val pipes = if (action.pipes && m.terrain[i] != Terrain.WATER) pipeCost(i) + (if (renew) pipeRenewCost(i) else 0L) else 0L
                    // Where a highway and another road cross, an interchange, with its ramps on the corners that are clear.
                    val interchange = if (meetsHighway(action.type, layout, k) && m.junction[i] != Junction.INTERCHANGE) Junction.price(Junction.INTERCHANGE) else 0L
                    if (road != NO_CHANGE || pipes > 0 || renew || interchange > 0) {
                        changes += i
                        cost += (if (road == NO_CHANGE) 0 else road) + pipes + relay + interchange
                    }
                    if (interchange > 0) for (j in rampCorners(action.type, layout, k)) if (j !in changes) changes += j
                }
            }
            is Action.BuildPipe -> {
                val material = action.material ?: best(action.kind)
                for (i in action.tiles) {
                    val there = pipes(action.kind)[i].toInt()
                    when {
                        !inMap(i) -> {}
                        // Pipes go under everything but water.
                        m.terrain[i] == Terrain.WATER || !allows(material) -> blocked += i
                        // A pipe that's there stays unless it's worn or this is better.
                        there != 0 && !relays(action.kind, i, material) -> {}
                        else -> {
                            changes += i
                            cost += material.cost
                        }
                    }
                }
            }
            is Action.BuildTram -> for (i in action.tiles) {
                when {
                    !inMap(i) -> {}
                    // Tram track goes along a street, never on its own.
                    m.road[i] == Road.NONE -> blocked += i
                    // Worn track's relaid.
                    m.tram[i].toInt() != 0 -> if (worn(m.tramLaid, i, Balance.TRAM_TRACK_LIFE)) {
                        changes += i
                        cost += Prices.TRAM_TRACK * Balance.RENEW_ROAD / 100
                    }
                    else -> {
                        changes += i
                        cost += Prices.TRAM_TRACK
                    }
                }
            }
            is Action.SetJunction -> for (i in action.tiles) {
                when {
                    !inMap(i) || !Junction.at(m, i) -> {}
                    !everything && year < Junction.year(action.control) -> blocked += i
                    m.junction[i] == action.control -> {}
                    else -> {
                        changes += i
                        cost += Junction.price(action.control)
                    }
                }
            }
            is Action.FitScrubbers -> buildings[if (m.inside(action.x, action.y)) m.building[m.index(action.x, action.y)] else 0]?.let { b ->
                when {
                    b.type != BuildingType.COAL_PLANT && b.type != BuildingType.OIL_PLANT -> blocked += m.index(action.x, action.y)
                    !everything && year < Balance.SCRUBBER_YEAR -> blocked += m.index(action.x, action.y)
                    b.scrubbed -> {}
                    else -> {
                        changes += m.index(b.x, b.y)
                        cost += Balance.SCRUBBER_PRICE
                    }
                }
            }
            is Action.PaintDistrict -> {
                val id = if (action.id == NEW_DISTRICT) nextDistrictId else action.id
                val ok = action.id == 0 || action.id == NEW_DISTRICT && districts.size < Balance.MAX_DISTRICTS ||
                    districts.any { it.id == action.id }
                if (ok) forRect(action.x0, action.y0, action.x1, action.y1) { i ->
                    if (m.terrain[i] != Terrain.WATER && m.district[i].toInt() and 0xff != id) changes += i
                }
            }
            is Action.SetDistrict -> districts.firstOrNull { it.id == action.id }?.let { changes += 0 }
            is Action.RemoveDistrict -> districts.firstOrNull { it.id == action.id }?.let { d ->
                changes += 0
                for (i in 0 until m.size) if (m.district[i].toInt() and 0xff == d.id) changes += i
            }
            is Action.AddLine -> {
                val kind = if (action.tram) Stop.TRAM else Stop.BUS
                val stops = action.stops.filter { inMap(it) && m.stop[it].toInt() and kind != 0 }
                if (stops.size >= 2 && stops.size == action.stops.size) {
                    changes += stops.distinct()
                    cost += vehiclePrice(action.tram) * action.vehicles
                    if (transit.route(stops, action.tram) == null) noRoute = true
                }
            }
            is Action.SetVehicles -> lines.firstOrNull { it.id == action.id }?.let { line ->
                if (action.vehicles != line.vehicles && action.vehicles >= 1) {
                    changes += line.stops[0]
                    // Buying more costs; selling some brings in half.
                    val more = (action.vehicles - line.vehicles).toLong()
                    cost += vehiclePrice(line.tram) * if (more > 0) more else more / 2
                }
            }
            is Action.RemoveLine -> lines.firstOrNull { it.id == action.id }?.let { line ->
                changes += line.stops[0]
                cost -= vehiclePrice(line.tram) * line.vehicles / 2
            }
            is Action.BuildLane -> for (i in action.tiles) {
                when {
                    !inMap(i) -> {}
                    m.road[i] == Road.NONE -> blocked += i
                    m.lane[i].toInt() != 0 -> {}
                    else -> {
                        changes += i
                        cost += Balance.LANE_PRICE
                    }
                }
            }
            is Action.PlantStreetTrees -> for (i in action.tiles) {
                when {
                    !inMap(i) -> {}
                    m.road[i] == Road.NONE || m.terrain[i] == Terrain.WATER -> blocked += i
                    m.streetTrees[i].toInt() != 0 -> {}
                    else -> {
                        changes += i
                        cost += Prices.STREET_TREE
                    }
                }
            }
            is Action.BuildWire -> for (i in action.tiles) {
                when {
                    !inMap(i) -> {}
                    // Wire hangs over a road, and only once trolleybuses have come.
                    m.road[i] == Road.NONE || !allowsTrolleybuses() -> blocked += i
                    m.wire[i].toInt() != 0 -> if (worn(m.wireLaid, i, Balance.WIRE_LIFE)) {
                        changes += i
                        cost += Prices.WIRE * Balance.RENEW_ROAD / 100
                    }
                    else -> {
                        changes += i
                        cost += Prices.WIRE
                    }
                }
            }
            is Action.BuildSubway -> for (i in action.tiles) {
                when {
                    !inMap(i) -> {}
                    m.terrain[i] == Terrain.WATER || !allows(BuildingType.SUBWAY_STATION) -> blocked += i
                    m.subway[i].toInt() != 0 -> if (worn(m.subwayLaid, i, Balance.TUNNEL_LIFE)) {
                        changes += i
                        cost += Prices.TUNNEL * Balance.RENEW_ROAD / 100
                    }
                    else -> {
                        changes += i
                        cost += Prices.TUNNEL
                    }
                }
            }
            is Action.PlaceStop -> if (m.inside(action.x, action.y)) {
                val i = m.index(action.x, action.y)
                val ok = m.road[i] != Road.NONE &&
                    (action.kind != Stop.TRAM || m.tram[i].toInt() != 0) &&
                    (action.kind != Stop.BUS || allows(BuildingType.BUS_GARAGE))
                when {
                    !ok -> blocked += i
                    m.stop[i].toInt() and action.kind != 0 -> {}
                    else -> {
                        changes += i
                        cost += Prices.STOP
                    }
                }
            }
            is Action.RenewArea -> {
                forRect(action.x0, action.y0, action.x1, action.y1) { i ->
                    val (bits, price) = renewal(i)
                    if (bits != 0) {
                        changes += i
                        cost += price
                    }
                }
                // Worn services under it are renovated, by their first tile.
                for (b in renovations(action)) {
                    val i = m.index(b.x, b.y)
                    if (i !in changes) changes += i
                    cost += Prices.of(b.type) * Balance.RENOVATE_SHARE / 100
                }
            }
            is Action.RemoveTransit -> forRect(action.x0, action.y0, action.x1, action.y1) { i ->
                if (m.tram[i].toInt() != 0 || m.wire[i].toInt() != 0 || m.stop[i].toInt() != 0 || m.subway[i].toInt() != 0 || m.lane[i].toInt() != 0) {
                    changes += i
                    cost += Prices.REMOVE_TRANSIT
                }
            }
            is Action.BuildRail -> if (action.tunnel) {
                val path = action.tiles.filter { inMap(it) }
                val (runs, turns) = runsOf(path)
                cost += planTunnel(path.toIntArray(), runs, turns, listOf(path.indices), null, changes, blocked)
            } else {
                val path = action.tiles.filter { inMap(it) }
                val (runs, turns) = runsOf(path)
                val spans = bridgesAlong(path.toIntArray(), runs, turns, listOf(path.indices), action.bridge, rail = true)
                for (k in path.indices) if (m.terrain[path[k]] == Terrain.WATER && spans[k] >= 0 && blocksShips(spans, k)) lowDecks += path[k]
                for (k in path.indices) {
                    val i = path[k]
                    val water = m.terrain[i] == Terrain.WATER
                    when {
                        m.building[i] != 0 || m.bank[i].toInt() != 0 || m.portal[i].toInt() != 0 -> blocked += i
                        m.rail[i] != Rail.NONE -> if (Ageing.wear(monthNow - m.railLaid[i], Balance.TRACK_LIFE) >= Balance.RENEWABLE_WEAR) {
                            // Worn track is relaid.
                            changes += i
                            cost += Prices.RAIL * Balance.RENEW_ROAD / 100
                        }
                        // Over water on a bridge of its own, straight across.
                        water && (turns[k] || m.road[i] != Road.NONE || spans[k] == NO_BRIDGE) -> blocked += i
                        !water && sideways(i, runs[k].toInt()) -> blocked += i
                        // Across a road only straight over it, as a level crossing, both carriageways of a divided one.
                        m.road[i] != Road.NONE && (turns[k] || !levelCrossing(i, runs[k].toInt())) -> blocked += i
                        // Under a line on poles or a phone line only straight across it, where the wires span the track.
                        (m.power[i] != Power.NONE && !m.cable(i) || m.phone[i].toInt() != 0 && !m.duct(i)) &&
                            (turns[k] || !across(i, runs[k].toInt(), m.power) && m.power[i] != Power.NONE || !across(i, runs[k].toInt(), m.phone) && m.phone[i].toInt() != 0) -> blocked += i
                        else -> {
                            changes += i
                            cost += Prices.RAIL * (if (water) bridgePrice(spans[k]) else 1) + clearing(i)
                        }
                    }
                }
            }
            is Action.BuildPhoneLine -> {
              val path = action.tiles.filter { inMap(it) }
              val (runs, turns) = runsOf(path)
              for ((k, i) in path.withIndex()) {
                val kind = if (action.fibre) Phone.FIBRE else Phone.COPPER
                val crossing = m.terrain[i] == Terrain.WATER || (m.rail[i] != Rail.NONE && (turns[k] || !across(i, runs[k].toInt(), m.rail)))
                when {
                    !inMap(i) -> {}
                    m.building[i] != 0 || m.zone[i] != Zone.NONE -> blocked += i
                    crossing && !action.buried -> blocked += i
                    action.fibre && !allowsFibre() -> blocked += i
                    m.phone[i] == kind && m.duct(i) == action.buried -> {}
                    else -> {
                        changes += i
                        val price = when {
                            action.fibre && action.buried -> Prices.FIBRE_DUCT
                            action.fibre -> Prices.FIBRE
                            action.buried -> Prices.COPPER_DUCT
                            else -> Prices.COPPER
                        }
                        cost += price * (if (m.terrain[i] == Terrain.WATER) Prices.BRIDGE else 1) + clearing(i)
                    }
                }
            }
              }
            is Action.RemovePhone -> forRect(action.x0, action.y0, action.x1, action.y1) { i ->
                if (m.phone[i].toInt() != 0) {
                    changes += i
                    cost += Prices.REMOVE_PHONE
                }
            }
            is Action.BuildPowerLine -> {
              val path = action.tiles.filter { inMap(it) }
              val (runs, turns) = runsOf(path)
              for ((k, i) in path.withIndex()) {
                val kind = if (action.high) Power.HIGH else Power.LINE
                // Cable goes under rivers, which poles can't stand in; wires on poles span track straight across it.
                val crossing = m.terrain[i] == Terrain.WATER || (m.rail[i] != Rail.NONE && (turns[k] || !across(i, runs[k].toInt(), m.rail)))
                when {
                    !inMap(i) -> {}
                    m.building[i] != 0 || m.zone[i] != Zone.NONE -> blocked += i
                    crossing && !action.buried -> blocked += i
                    action.high && !allowsHighLines() -> blocked += i
                    action.high && action.buried && !allowsHighCable() -> blocked += i
                    m.power[i] == kind && m.cable(i) == action.buried -> {}
                    else -> {
                        // An ordinary line can be strung again as a high-voltage one, or put underground, or back.
                        changes += i
                        val price = when {
                            action.buried && action.high -> Prices.HIGH_CABLE
                            action.buried -> Prices.CABLE
                            action.high -> Prices.HIGH_LINE
                            else -> Prices.POWER_LINE
                        }
                        cost += price * (if (m.terrain[i] == Terrain.WATER) Prices.BRIDGE else 1) + clearing(i)
                    }
                }
            }
              }
            is Action.PlaceZone -> forRect(action.x0, action.y0, action.x1, action.y1) { i ->
                when {
                    m.terrain[i] == Terrain.WATER || m.road[i] != Road.NONE || m.power[i] != Power.NONE || m.rail[i] != Rail.NONE ||
                        m.bank[i].toInt() != 0 || m.portal[i].toInt() != 0 -> blocked += i
                    !Density.fits(action.zone, action.density) || !allowsDensity(action.density) -> blocked += i
                    m.zone[i] == action.zone && m.density[i] == action.density -> {}
                    // A zone's density can change under its buildings; they stay, but grow no further than it allows.
                    m.zone[i] == action.zone -> changes += i
                    // Never over what the town runs. Over what's grown, it stands until it's worn out (see [replaceNonconforming]).
                    buildings[m.building[i]]?.type?.zone == Zone.NONE -> blocked += i
                    else -> {
                        changes += i
                        cost += if (action.density == Density.RURAL) Prices.ZONE_RURAL else Prices.ZONE
                    }
                }
            }
            is Action.PlaceBuilding -> {
                val t = action.type
                var ok = action.x >= 0 && action.y >= 0 && action.x + t.width <= m.width && action.y + t.height <= m.height
                if (ok) forRect(action.x, action.y, action.x + t.width - 1, action.y + t.height - 1) { i ->
                    // Turbines out on the water stand on nothing else.
                    val water = m.terrain[i] == Terrain.WATER
                    if (!allows(t) || water != t.inWater || !inWaterFits(t, i) || m.road[i] != Road.NONE || m.power[i] != Power.NONE ||
                        m.zone[i] != Zone.NONE || m.building[i] != 0 || m.rail[i] != Rail.NONE || m.bank[i].toInt() != 0 || m.portal[i].toInt() != 0
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
                    // A hydro station's dam floods the clear land beside the river upstream of it.
                    if (t == BuildingType.HYDRO_PLANT) for (i in reservoir(action.x, action.y)) {
                        changes += i
                        cost += clearing(i)
                    }
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
                            // The owners are paid what it's worth.
                            cost += worth(b)
                        }
                        return@forRect
                    }
                    var c = 0L
                    if (m.road[i] != Road.NONE) c += Prices.REMOVE_ROAD * if (m.terrain[i] == Terrain.WATER) bridgePrice(m.bridge[i].toInt()) else 1
                    if (m.power[i] != Power.NONE) c += Prices.REMOVE_LINE
                    if (m.rail[i] != Rail.NONE) c += Prices.REMOVE_RAIL * if (m.terrain[i] == Terrain.WATER) bridgePrice(m.bridge[i].toInt()) else 1
                    if (m.bank[i].toInt() != 0) c += Prices.REMOVE_BANK
                    if (m.terrain[i] == Terrain.TREES) c += Prices.CLEAR_TREES
                    if (m.brownfield[i].toInt() != 0) c += Prices.CLEAN_UP
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
            is Action.RemoveTunnel -> forRect(action.x0, action.y0, action.x1, action.y1) { i ->
                if (m.tunnelled(i)) {
                    changes += i
                    cost += Prices.REMOVE_TUNNEL
                }
            }
            is Action.SetBridge -> {
                // The whole bridge each tile's on, a toll booth at each one that gets a toll.
                bridges()
                val runs = bridgeRuns.filter { run -> run.any { it in action.tiles } }
                for (run in runs) {
                    var changed = false
                    for (i in run) {
                        val now = bridgeBits(m.bridge[i].toInt(), action)
                        if (now != m.bridge[i].toInt()) {
                            changes += i
                            changed = true
                        }
                    }
                    if (changed && action.toll == true && m.bridge[run[0]].toInt() and Bridge.TOLL == 0) cost += Prices.TOLL_BOOTH
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
            action is Action.PlaceBuilding && action.type.port && Port.waterSide(m, action.type, action.x, action.y) == 0 -> Problem.NeedsWater
            action is Action.PlaceBuilding && action.type.port && !ships().reaches(Port.berth(m, action.type, action.x, action.y)) -> Problem.NoSeaRoute
            action is Action.PlaceBuilding && action.type.inWater && cutsOffPort(changes) -> Problem.CutsOffPort
            (action is Action.BuildRoad || action is Action.BuildRail) && cutsOffPort(lowDecks.filter { it in changes }) -> Problem.CutsOffPort
            action is Action.PlaceBuilding && action.type.onWater && !besideWater(action.type, action.x, action.y) -> Problem.NeedsWater
            action is Action.PlaceBuilding && action.type == BuildingType.TRAM_DEPOT && besideTram(action.type, action.x, action.y).isEmpty() -> Problem.NeedsTramTrack
            action is Action.PlaceBuilding && action.type == BuildingType.SUBWAY_STATION && m.inside(action.x, action.y) &&
                m.subway[m.index(action.x, action.y)].toInt() == 0 -> Problem.NeedsTunnel
            changes.isEmpty() -> Problem.NothingToDo
            noRoute -> Problem.NoRoute
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
    /**
     * The corners where an interchange at step [k] of [layout] lays its ramps:
     * the two beside the crossing on the far side of each carriageway from the
     * other, along the highway, as far as they're clear land.
     */
    private fun rampCorners(type: RoadType, layout: RoadLayout, k: Int): List<Int> {
        val m = map
        val i = layout.tiles[k]
        val x = i % m.width
        val y = i / m.width
        // Which way the highway runs here, and where its other carriageway is.
        val heading = if (type.limited) layout.headings[k].toInt() else m.roadHeading[i].toInt()
        if (heading == 0) return emptyList()
        val along = heading == Heading.EAST.toInt() || heading == Heading.WEST.toInt()
        val drawn = layout.tiles.toHashSet()
        fun highway(j: Int) = RoadType.of(m.road[j])?.limited == true || (type.limited && j in drawn)
        val (sx, sy) = if (along) 0 to 1 else 1 to 0
        val out = when {
            m.inside(x + sx, y + sy) && highway(m.index(x + sx, y + sy)) -> -1
            m.inside(x - sx, y - sy) && highway(m.index(x - sx, y - sy)) -> 1
            else -> return emptyList()
        }
        val corners = if (along) listOf(x - 1 to y + out, x + 1 to y + out) else listOf(x + out to y - 1, x + out to y + 1)
        return corners.filter { (cx, cy) -> m.inside(cx, cy) }.map { (cx, cy) -> m.index(cx, cy) }.filter { j ->
            m.building[j] == 0 && m.road[j] == Road.NONE && m.rail[j] == Rail.NONE && m.terrain[j] != Terrain.WATER && m.bank[j].toInt() == 0
        }
    }

    /** Whether laying [type] at step [k] of [layout] crosses a highway with another road, or another road with a highway. */
    private fun meetsHighway(type: RoadType, layout: RoadLayout, k: Int): Boolean {
        val i = layout.tiles[k]
        val old = RoadType.of(map.road[i]) ?: return false
        return old.limited != type.limited && crossing(i, layout.runs[k].toInt())
    }

    private fun roadCost(type: RoadType, layout: RoadLayout, k: Int): Long {
        val m = map
        val i = layout.tiles[k]
        val run = layout.runs[k].toInt()
        val old = RoadType.of(m.road[i])
        val water = m.terrain[i] == Terrain.WATER
        val bridge = if (water) bridgePrice(layout.bridges[k]) else 1
        return when {
            m.building[i] != 0 || m.bank[i].toInt() != 0 || m.portal[i].toInt() != 0 -> BLOCKED
            water && (!type.bridges || layout.turns[k] || m.rail[i] != Rail.NONE || layout.bridges[k] == NO_BRIDGE) -> BLOCKED
            // Nothing joins the approach to a high bridge from the side.
            !water && sideways(i, run) -> BLOCKED
            // A road meets track only straight across it, as a level crossing.
            m.rail[i] != Rail.NONE && (layout.turns[k] || !across(i, run, m.rail)) -> BLOCKED
            old == null -> type.price * bridge + clearing(i)
            // Another kind of bridge in place of the one there.
            old == type && water && (layout.bridges[k] and Bridge.KIND) != (m.bridge[i].toInt() and Bridge.KIND) -> type.price * bridge
            old == type && (layout.headings[k] == m.roadHeading[i] || across(run, m.roadHeading[i].toInt())) -> NO_CHANGE
            // A road drawn across a better one leaves the crossing as it is.
            old.capacity > type.capacity && crossing(i, run) -> NO_CHANGE
            else -> max(Prices.REMOVE_ROAD, type.price - old.price) * bridge
        }
    }

    /** The tram track tiles beside a building of [type] at [x], [y], on its sides rather than its corners. */
    fun besideTram(type: BuildingType, x: Int, y: Int): List<Int> {
        val out = ArrayList<Int>()
        for (ty in y until y + type.height) for (tx in listOf(x - 1, x + type.width)) if (map.inside(tx, ty) && map.tram[map.index(tx, ty)].toInt() != 0) out += map.index(tx, ty)
        for (tx in x until x + type.width) for (ty in listOf(y - 1, y + type.height)) if (map.inside(tx, ty) && map.tram[map.index(tx, ty)].toInt() != 0) out += map.index(tx, ty)
        return out
    }

    /**
     * Whether tile [i] is the right water for [t]: a river's current for a
     * river turbine, tidal water near the map's edge for a tidal one, any
     * open water for wind; and anything for a building on land.
     */
    private fun inWaterFits(t: BuildingType, i: Int): Boolean = when (t) {
        BuildingType.RIVER_TURBINE -> flow()[i] >= 0
        BuildingType.TIDAL_TURBINE -> {
            val x = i % map.width
            val y = i / map.width
            minOf(x, y, map.width - 1 - x, map.height - 1 - y) < Balance.TIDE_REACH
        }
        else -> true
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
    private fun pipeCost(i: Int): Long = Pipe.entries.sumOf { if (pipes(it)[i].toInt() == 0) best(it).cost else 0L }

    /** What it costs to relay the worn pipes under a road being relaid, which is less than on their own. */
    private fun pipeRenewCost(i: Int): Long = Pipe.entries.sumOf { if (pipeRenewable(it, i)) best(it).cost * Balance.PIPES_WITH_ROAD / 100 else 0L }

    /** When each tile's pipe of [kind] was laid. */
    private fun laid(kind: Pipe): ShortArray = when (kind) {
        Pipe.WATER -> map.waterLaid
        Pipe.SEWER -> map.sewerLaid
        Pipe.STORM -> map.stormLaid
    }

    /** The [Broken] bit for a kind of pipe. */
    private fun bit(kind: Pipe): Int = when (kind) {
        Pipe.WATER -> Broken.WATER
        Pipe.SEWER -> Broken.SEWER
        Pipe.STORM -> Broken.STORM
    }

    /**
     * What relaying tile [i] would take in hand, as [Broken] bits, and what it
     * would cost: whatever's worn, and pipes of an older kind; pipes under a
     * road being relaid for less. Nothing for what's already being worked on.
     */
    private fun renewal(i: Int): Pair<Int, Long> {
        val m = map
        if (m.broken[i].toInt() and Broken.WORKS != 0) return 0 to 0L
        var bits = 0
        var cost = 0L
        val bridge = if (m.terrain[i] == Terrain.WATER) bridgePrice(m.bridge[i].toInt()) else 1
        val road = RoadType.of(m.road[i])
        if (road != null && worn(m.roadLaid, i, roadLife(i, road))) {
            bits = bits or Broken.ROAD
            cost += road.price * Balance.RENEW_ROAD / 100 * bridge
        }
        for (kind in Pipe.entries) if (pipeRenewable(kind, i)) {
            bits = bits or bit(kind)
            cost += best(kind).cost * (if (bits and Broken.ROAD != 0) Balance.PIPES_WITH_ROAD else 100) / 100
        }
        if (m.rail[i] != Rail.NONE && worn(m.railLaid, i, Balance.TRACK_LIFE)) {
            bits = bits or Broken.RAIL
            cost += Prices.RAIL * Balance.RENEW_ROAD / 100 * bridge
        }
        if (m.tunnelled(i) && worn(m.lowLaid, i, Balance.TUNNEL_LIFE)) {
            bits = bits or Broken.LOW
            cost += (if (m.lowRail[i].toInt() != 0) Prices.RAIL_TUNNEL else Prices.ROAD_TUNNEL) * Balance.RENEW_ROAD / 100
        }
        if (m.tram[i].toInt() != 0 && worn(m.tramLaid, i, Balance.TRAM_TRACK_LIFE)) {
            bits = bits or Broken.TRAM
            cost += Prices.TRAM_TRACK * Balance.RENEW_ROAD / 100
        }
        if (m.wire[i].toInt() != 0 && worn(m.wireLaid, i, Balance.WIRE_LIFE)) {
            bits = bits or Broken.WIRE
            cost += Prices.WIRE * Balance.RENEW_ROAD / 100
        }
        if (m.subway[i].toInt() != 0 && worn(m.subwayLaid, i, Balance.TUNNEL_LIFE)) {
            bits = bits or Broken.SUBWAY
            cost += Prices.TUNNEL * Balance.RENEW_ROAD / 100
        }
        if (m.phone[i].toInt() != 0 && worn(m.phoneLaid, i, phoneLife(i))) {
            bits = bits or Broken.PHONE
            val price = if (m.phone[i] == Phone.FIBRE) (if (m.duct(i)) Prices.FIBRE_DUCT else Prices.FIBRE) else (if (m.duct(i)) Prices.COPPER_DUCT else Prices.COPPER)
            cost += price * Balance.RENEW_ROAD / 100 * bridge
        }
        if (m.cable(i) && worn(m.powerLaid, i, cableLife(i))) {
            bits = bits or Broken.POWER
            cost += (if (m.power[i] == Power.HIGH) Prices.HIGH_CABLE else Prices.CABLE) * Balance.RENEW_ROAD / 100 * bridge
        }
        return bits to cost
    }

    /** Puts [b]'s age and closure back to [built] and [outage], for undo and redo. */
    private fun reopen(b: Building, built: Int, outage: Int) {
        b.built = built
        b.outage = outage
        if (outage > 0) outages += b.id else outages -= b.id
    }

    /** The worn services with a tile under [action]'s rectangle, which it renovates. */
    private fun renovations(action: Action.RenewArea): List<Building> {
        val m = map
        val ids = HashSet<Int>()
        forRect(action.x0, action.y0, action.x1, action.y1) { i -> if (m.building[i] != 0) ids += m.building[i] }
        return ids.sorted().mapNotNull { buildings[it] }.filter { renovatable(it) }
    }

    /** Whether [b] is a service worn enough to renovate, and open. */
    fun renovatable(b: Building): Boolean =
        b.type.zone == Zone.NONE && (b.type.service || Needs.of(b.type).isNotEmpty()) && b.outage == 0 && b.underway == 0 &&
            (b.type.life > 0 && Ageing.wear(monthNow - b.built, b.type.life) >= Balance.RENEWABLE_WEAR || unmet(b).any { it.second })

    /** Whether what was laid on tile [i] in [laid], expected to last [life] years, is worn enough to relay. */
    private fun worn(laid: ShortArray, i: Int, life: Int): Boolean = Ageing.wear(monthNow - laid[i], life) >= Balance.RENEWABLE_WEAR

    /** Months from January 1900 to now. */
    val monthNow: Int get() = Ageing.monthOf(year, month)

    /** The best of a kind of pipe the town lays now: the newest of its era, never wood. */
    fun best(kind: Pipe): Material =
        Material.entries.filter { it.pipe == kind && it.gone == null && year >= it.year && era >= Era.of(it.year) }.maxByOrNull { it.year }
            ?: Material.entries.first { it.pipe == kind }

    /** Whether the town can lay [material] yet, or still. */
    fun allows(material: Material): Boolean =
        everything || (year >= material.year && era >= Era.of(material.year) && (material.gone == null || era < material.gone))

    /** Whether the pipe of [kind] on tile [i] would be relaid with [material]: it's worn, or that's a longer-lived pipe than it. */
    private fun relays(kind: Pipe, i: Int, material: Material): Boolean {
        val there = Material.of(kind, pipes(kind)[i]) ?: return true
        return material.life > there.life || Ageing.wear(monthNow - laid(kind)[i], there.life) >= Balance.RENEWABLE_WEAR
    }

    /** Whether the pipe of [kind] on tile [i] would be relaid with the best the town has now. */
    private fun pipeRenewable(kind: Pipe, i: Int): Boolean = pipes(kind)[i].toInt() != 0 && relays(kind, i, best(kind))

    /** Whether drawing [type] over tile [k] of [layout] relays a worn road of the same kind. */
    private fun roadRenewable(type: RoadType, layout: RoadLayout, k: Int): Boolean {
        val i = layout.tiles[k]
        return RoadType.of(map.road[i]) == type && roadCost(type, layout, k) == NO_CHANGE &&
            Ageing.wear(monthNow - map.roadLaid[i], type.life) >= Balance.RENEWABLE_WEAR
    }

    /**
     * The tiles a road goes on, the way it was drawn through each, the heading
     * each gets (only one-way roads have one) and whether it turns there.
     */
    private class RoadLayout(val tiles: IntArray, val runs: ByteArray, val headings: ByteArray, val turns: BooleanArray, val bridges: IntArray)

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
            if (path.size < 2) return RoadLayout(IntArray(0), ByteArray(0), ByteArray(0), BooleanArray(0), IntArray(0))
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
        val first = if (type.width == 2) (0 until tiles.size / 2) else tiles.indices
        val segments = if (type.width == 2) listOf(first, first.last + 1 until tiles.size) else listOf(first)
        val t = tiles.toIntArray()
        val r = runs.toByteArray()
        val turning = turns.toBooleanArray()
        return RoadLayout(t, r, headings.toByteArray(), turning, bridgesAlong(t, r, turning, segments, action.bridge, rail = false))
    }

    /** The stretches of a road's layout each carriageway takes: both halves of a two-wide road, or the whole of the rest. */
    private fun roadSegments(type: RoadType, size: Int): List<IntRange> =
        if (type.width == 2) listOf(0 until size / 2, size / 2 until size) else listOf(0 until size)

    /** Whether the town can dig road tunnels yet; track's gone under ground from the start. */
    fun allowsTunnel(rail: Boolean) = everything || rail || year >= Balance.ROAD_TUNNEL_YEAR

    /**
     * What a tunnel for a road of [road] (or track, if null) along [tiles]
     * would take: each segment from a portal at one end to a portal at the
     * other, straight, three tiles at least, through anything but another
     * tunnel or the subway. The portals go on land with nothing on it but the
     * same road or track running the same way, which goes down into it. Adds
     * the tiles to [changes], or every tile of a segment to [blocked] if any
     * of it can't be dug, and returns the cost.
     */
    private fun planTunnel(
        tiles: IntArray, runs: ByteArray, turns: BooleanArray, segments: List<IntRange>, road: RoadType?, changes: MutableList<Int>, blocked: MutableList<Int>,
    ): Long {
        val m = map
        val rail = road == null
        var cost = 0L
        for (seg in segments) {
            if (seg.isEmpty()) continue
            var ok = seg.count() >= 3 && allowsTunnel(rail) && (road == null || allows(road)) && seg.none { turns[it] && it != seg.last }
            var here = 0L
            for (k in seg) {
                val i = tiles[k]
                val run = runs[k].toInt()
                val end = k == seg.first || k == seg.last
                // What's on the surface running the same way, that would go down into the tunnel.
                val along = if (rail) m.rail[i] != Rail.NONE && !across(i, run, m.rail) else RoadType.of(m.road[i]) == road && !across(i, run, m.road)
                when {
                    m.tunnelled(i) || m.subway[i].toInt() != 0 || m.portal[i].toInt() != 0 -> ok = false
                    end && (m.terrain[i] == Terrain.WATER || m.building[i] != 0 || m.bank[i].toInt() != 0) -> ok = false
                    // A portal is open ground but for the road or track going down.
                    end && (if (rail) m.road[i] != Road.NONE else m.rail[i] != Rail.NONE) -> ok = false
                    end && (if (rail) m.rail[i] != Rail.NONE && !along else m.road[i] != Road.NONE && !along) -> ok = false
                    buildings[m.building[i]]?.underway?.let { it > 0 } == true -> ok = false
                }
                val dig = if (rail) Prices.RAIL_TUNNEL else Prices.ROAD_TUNNEL + road!!.price * 2
                here += dig * (if (m.terrain[i] == Terrain.WATER) Prices.UNDER_WATER else 1) +
                    (if (!end && (m.building[i] != 0 || m.road[i] != Road.NONE || m.rail[i] != Rail.NONE)) Prices.CUT_AND_COVER else 0L) +
                    (if (end) clearing(i) else 0L)
            }
            if (ok) {
                for (k in seg) changes += tiles[k]
                cost += here
            } else {
                for (k in seg) blocked += tiles[k]
            }
        }
        return cost
    }

    /**
     * Digs the tunnels [planTunnel] planned on [changing]: the road (or track)
     * below each tile, a portal at each end facing out, and the same road or
     * track running the same way on the surface taken down into it. Digging
     * under a road shuts it a few days, through [works].
     */
    private fun layTunnel(
        tiles: IntArray, runs: ByteArray, headings: ByteArray, segments: List<IntRange>, road: RoadType?, changing: Set<Int>, now: Int, works: (Int) -> Unit,
    ) {
        val m = map
        val rail = road == null
        for (seg in segments) {
            if (seg.isEmpty() || tiles[seg.first] !in changing) continue
            for (k in seg) {
                val i = tiles[k]
                val run = runs[k].toInt()
                val along = if (rail) m.rail[i] != Rail.NONE && !across(i, run, m.rail) else RoadType.of(m.road[i]) == road && !across(i, run, m.road)
                if (rail) {
                    m.lowRail[i] = 1
                    m.lowHeading[i] = Tunnel.heading(run, 0).toByte()
                } else {
                    m.lowRoad[i] = road!!.id
                    m.lowHeading[i] = Tunnel.heading(run, headings[k].toInt()).toByte()
                }
                m.lowLaid[i] = now.toShort()
                val end = k == seg.first || k == seg.last
                if (end) {
                    // Facing out, away from the rest of the tunnel.
                    val inner = tiles[if (k == seg.first) k + 1 else k - 1]
                    m.portal[i] = Heading.of(i % m.width - inner % m.width, i / m.width - inner / m.width)
                    m.zone[i] = Zone.NONE
                    clearTrees(i)
                }
                if (end || along) {
                    // The road or track that was here goes down into the tunnel.
                    if (rail) {
                        m.rail[i] = Rail.NONE
                        m.railLaid[i] = 0
                    } else {
                        m.road[i] = Road.NONE
                        m.roadHeading[i] = Heading.BOTH
                        m.roadLaid[i] = 0
                        m.junction[i] = Junction.AUTO
                    }
                } else if (m.road[i] != Road.NONE && m.terrain[i] != Terrain.WATER) {
                    works(i)
                }
            }
        }
    }

    /** Whether the town can build bridges of [kind] yet. */
    fun allows(kind: BridgeKind) = everything || year >= kind.year

    /**
     * The bridges a line drawn along [tiles] would cross the water on: for
     * each tile, what [CityMap.bridge] gets there (the kind and the way it
     * runs), 0 on land, or [NO_BRIDGE] where nothing fits. Each stretch of
     * water in a segment is one span. It keeps the kind already there unless
     * [want] asks for another, and otherwise takes the cheapest that fits the
     * span and the year, with room for a high one's straight approaches.
     */
    private fun bridgesAlong(tiles: IntArray, runs: ByteArray, turns: BooleanArray, segments: List<IntRange>, want: BridgeKind?, rail: Boolean): IntArray {
        val m = map
        val out = IntArray(tiles.size)
        fun water(k: Int) = m.terrain[tiles[k]] == Terrain.WATER
        for (seg in segments) {
            var k = seg.first
            while (k <= seg.last) {
                if (!water(k)) {
                    k++
                    continue
                }
                // A span ends where the line turns, which can't be on a bridge anyway.
                var e = k
                while (e + 1 <= seg.last && water(e + 1) && !turns[e]) e++
                val span = e - k + 1
                val run = runs[k].toInt()
                val across = if (run == Heading.EAST.toInt() || run == Heading.WEST.toInt()) Bridge.ACROSS else 0
                // Land in line with the bridge that runs straight up to it.
                fun straight(j: Int) = j in seg && !water(j) && runs[j].toInt() == run && (j > e || !turns[j])
                fun fits(kind: BridgeKind) = allows(kind) && kind.spans(span) && (!rail || kind.rail) &&
                    (1..kind.approach).all { straight(k - it) && straight(e + it) }
                val there = (k..e).map { m.bridge[tiles[it]].toInt() and Bridge.KIND }.distinct()
                val built = (k..e).all { if (rail) m.rail[tiles[it]] != Rail.NONE else m.road[tiles[it]] != Road.NONE } && there.size == 1
                val kind = when {
                    want != null -> if (fits(want)) want.id else NO_BRIDGE
                    built -> there[0]
                    else -> BridgeKind.entries.filter { fits(it) }.minByOrNull { it.price }?.id ?: NO_BRIDGE
                }
                for (j in k..e) out[j] = if (kind == NO_BRIDGE) NO_BRIDGE else kind or across
                k = e + 1
            }
        }
        return out
    }

    /** A bridge tile's [CityMap.bridge] with [action]'s toll and shutting set. */
    private fun bridgeBits(bits: Int, action: Action.SetBridge): Int {
        var v = bits
        action.toll?.let { v = if (it) v or Bridge.TOLL else v and Bridge.TOLL.inv() }
        action.shut?.let { v = if (it) v or Bridge.SHUT else v and Bridge.SHUT.inv() }
        return v
    }

    /**
     * Whether the [k]th of a line's [bridges] would stop ships: a low one, or
     * the middle of a swing bridge, where it turns.
     */
    private fun blocksShips(bridges: IntArray, k: Int): Boolean {
        val kind = BridgeKind.of(bridges[k] and Bridge.KIND)
        if (kind == null || kind.clearance == Bridge.LOW) return true
        if (kind != BridgeKind.SWING) return false
        var before = 0
        while (k - before - 1 >= 0 && bridges[k - before - 1] > 0 && (bridges[k - before - 1] and Bridge.KIND) == kind.id) before++
        var after = 0
        while (k + after + 1 < bridges.size && bridges[k + after + 1] > 0 && (bridges[k + after + 1] and Bridge.KIND) == kind.id) after++
        return before == (before + after + 1) / 2
    }

    /** What a bridge of the kind in [bridge] costs against the same on land: plain ones as they always did. */
    private fun bridgePrice(bridge: Int): Int = BridgeKind.of(bridge and Bridge.KIND)?.price ?: Prices.BRIDGE

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

    /**
     * Whether track running [run] can cross the road on tile [i] on the level: a road one tile wide,
     * or a divided road (a boulevard or highway) whose two carriageways both run square across it.
     */
    private fun levelCrossing(i: Int, run: Int): Boolean {
        if (across(i, run, map.road)) return true
        if (run == 0) return false
        val x = i % map.width
        val y = i / map.width
        fun carriageway(n: Int): Boolean {
            val nx = x + Heading.DX[run] * n
            val ny = y + Heading.DY[run] * n
            return map.inside(nx, ny) && map.road[map.index(nx, ny)] != Road.NONE && across(run, map.roadHeading[map.index(nx, ny)].toInt())
        }
        fun road(n: Int): Boolean {
            val nx = x + Heading.DX[run] * n
            val ny = y + Heading.DY[run] * n
            return map.inside(nx, ny) && map.road[map.index(nx, ny)] != Road.NONE
        }
        if (!carriageway(0)) return false
        // The other carriageway on one side, and no more road beyond either.
        return carriageway(1) && !road(2) && !road(-1) || carriageway(-1) && !road(-2) && !road(1)
    }

    /** Whether something running [run] would come onto the approach to a high bridge on tile [i] from the side. */
    private fun sideways(i: Int, run: Int): Boolean {
        val a = bridges().approach[i].toInt() and 0xff
        return a != 0 && run != 0 && (a == Bridge.ACROSS) != (run == Heading.EAST.toInt() || run == Heading.WEST.toInt())
    }

    /** How long the road on tile [i] lasts: as long as its bridge, if it's on one. */
    private fun roadLife(i: Int, road: RoadType): Int =
        if (map.terrain[i] == Terrain.WATER) map.bridgeKind(i)?.life ?: road.life else road.life

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
        /** When things on the tiles were laid, and what was broken there, before and after. */
        val laidBefore: LongArray,
        val laidAfter: LongArray,
        val transitBefore: LongArray,
        val transitAfter: LongArray,
        val fixBefore: IntArray,
        val fixAfter: IntArray,
        /** The transit lines before and after. */
        val linesBefore: List<TransitLine>,
        val linesAfter: List<TransitLine>,
        val districtsBefore: List<District>,
        val districtsAfter: List<District>,
        /** The stations with scrubbers before and after. */
        val scrubbedBefore: Set<Int>,
        val scrubbedAfter: Set<Int>,
        /** Each building renovated: its id, and when it was built and how long it's shut, before and after. */
        val renovated: List<IntArray> = emptyList(),
        /** The phone lines on the tiles, before and after. */
        val utilBefore: LongArray = LongArray(0),
        val utilAfter: LongArray = LongArray(0),
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
        val laidBefore = LongArray(plan.changes.size) { m.tileLaid(plan.changes[it]) }
        val transitBefore = LongArray(plan.changes.size) { m.tileTransitLaid(plan.changes[it]) }
        val utilBefore = LongArray(plan.changes.size) { m.tileUtil(plan.changes[it]) }
        val fixBefore = IntArray(plan.changes.size) { m.tileFix(plan.changes[it]) }
        val linesBefore = lines.map { it.copy() }
        val districtsBefore = districts.map { it.copy() }
        val scrubbedBefore = buildings.values.filter { it.scrubbed }.map { it.id }.toSet()
        val renovated = ArrayList<IntArray>()
        val now = monthNow
        var queued = 0
        val added = ArrayList<Building>()
        val removed = ArrayList<Building>()
        when (action) {
            is Action.BuildRoad -> if (action.tunnel) {
                val layout = roadLayout(action)
                layTunnel(layout.tiles, layout.runs, layout.headings, roadSegments(action.type, layout.tiles.size), action.type, plan.changes.toHashSet(), now) { works ->
                    startWorks(works, Broken.ROAD, queued++ / Balance.WORKS_PER_DAY)
                }
            } else {
                val layout = roadLayout(action)
                val changing = plan.changes.toHashSet()
                for (k in layout.tiles.indices) {
                    val i = layout.tiles[k]
                    if (i !in changing) continue
                    val renewing = roadRenewable(action.type, layout, k)
                    if (meetsHighway(action.type, layout, k)) {
                        m.junction[i] = Junction.INTERCHANGE
                        for (j in rampCorners(action.type, layout, k)) {
                            m.road[j] = RoadType.RAMP.id
                            m.roadHeading[j] = Heading.BOTH
                            m.zone[j] = Zone.NONE
                            m.roadLaid[j] = now.toShort()
                            clearTrees(j)
                        }
                    }
                    if (m.terrain[i] == Terrain.WATER && layout.bridges[k] >= 0) {
                        m.bridge[i] = (layout.bridges[k] or (m.bridge[i].toInt() and (Bridge.SHUT or Bridge.TOLL))).toByte()
                    }
                    if (roadCost(action.type, layout, k) != NO_CHANGE) {
                        m.road[i] = action.type.id
                        m.roadHeading[i] = layout.headings[k]
                        m.zone[i] = Zone.NONE
                        m.roadLaid[i] = now.toShort()
                        clearTrees(i)
                    }
                    var works = 0
                    if (renewing) {
                        m.roadLaid[i] = now.toShort()
                        works = Broken.ROAD
                    }
                    if (action.pipes && m.terrain[i] != Terrain.WATER) for (kind in Pipe.entries) {
                        val layer = pipes(kind)
                        if (layer[i].toInt() == 0) {
                            layer[i] = best(kind).id
                            laid(kind)[i] = now.toShort()
                        } else if (renewing && pipeRenewable(kind, i)) {
                            layer[i] = best(kind).id
                            laid(kind)[i] = now.toShort()
                            works = works or bit(kind)
                        }
                    }
                    if (works != 0) startWorks(i, works, queued++ / Balance.WORKS_PER_DAY)
                }
            }
            is Action.BuildPipe -> {
                val material = action.material ?: best(action.kind)
                val layer = pipes(action.kind)
                for (i in plan.changes) {
                    // Relaying an old pipe digs the street up; a new one goes in alongside, out of the way.
                    if (layer[i].toInt() != 0) startWorks(i, bit(action.kind), queued++ / Balance.WORKS_PER_DAY)
                    layer[i] = material.id
                    laid(action.kind)[i] = now.toShort()
                }
            }
            is Action.BuildTram -> for (i in plan.changes) {
                if (m.tram[i].toInt() != 0) startWorks(i, Broken.TRAM, queued++ / Balance.WORKS_PER_DAY)
                m.tram[i] = 1
                m.tramLaid[i] = now.toShort()
            }
            is Action.PlantStreetTrees -> for (i in plan.changes) m.streetTrees[i] = 1
            is Action.FitScrubbers -> buildings[m.building[plan.changes[0]]]?.scrubbed = true
            is Action.PaintDistrict -> {
                var id = action.id
                if (id == NEW_DISTRICT) {
                    id = nextDistrictId++
                    districts += District(id, TownNames.make(seed * 31 + id))
                }
                for (i in plan.changes) m.district[i] = id.toByte()
                // A district painted out of all its tiles is gone.
                if (action.id == 0) {
                    val left = HashSet<Int>()
                    for (i in 0 until m.size) left += m.district[i].toInt() and 0xff
                    districts.removeAll { it.id !in left }
                }
            }
            is Action.SetDistrict -> {
                val k = districts.indexOfFirst { it.id == action.id }
                if (k >= 0) districts[k] = action.to.copy()
            }
            is Action.RemoveDistrict -> {
                for (i in plan.changes) if (m.district[i].toInt() and 0xff == action.id) m.district[i] = 0
                districts.removeAll { it.id == action.id }
            }
            is Action.AddLine -> lines += TransitLine(nextLineId++, action.tram, action.stops.copyOf(), action.vehicles)
            is Action.SetVehicles -> lines.firstOrNull { it.id == action.id }?.vehicles = action.vehicles
            is Action.RemoveLine -> lines.removeAll { it.id == action.id }
            is Action.BuildLane -> for (i in plan.changes) m.lane[i] = 1
            is Action.SetJunction -> for (i in plan.changes) m.junction[i] = action.control
            is Action.BuildWire -> for (i in plan.changes) {
                if (m.wire[i].toInt() != 0) startWorks(i, Broken.WIRE, queued++ / Balance.WORKS_PER_DAY)
                m.wire[i] = 1
                m.wireLaid[i] = now.toShort()
            }
            is Action.BuildSubway -> for (i in plan.changes) {
                if (m.subway[i].toInt() != 0) startWorks(i, Broken.SUBWAY, queued++ / Balance.WORKS_PER_DAY)
                m.subway[i] = 1
                m.subwayLaid[i] = now.toShort()
            }
            is Action.PlaceStop -> for (i in plan.changes) m.stop[i] = (m.stop[i].toInt() or action.kind).toByte()
            is Action.RenewArea -> for (i in plan.changes) {
                // A renovated service is as good as new, once it opens again.
                val b = buildings[m.building[i]]
                if (b != null && b.x == i % m.width && b.y == i / m.width && renovatable(b)) {
                    val was = intArrayOf(b.id, b.built, b.outage, 0, 0)
                    b.built = now
                    b.outage = Balance.RENOVATE_DAYS
                    outages += b.id
                    was[3] = b.built
                    was[4] = b.outage
                    renovated += was
                }
                val (bits, _) = renewal(i)
                if (bits == 0) continue
                val stamp = now.toShort()
                if (bits and Broken.ROAD != 0) m.roadLaid[i] = stamp
                for (kind in Pipe.entries) if (bits and bit(kind) != 0) {
                    pipes(kind)[i] = best(kind).id
                    laid(kind)[i] = stamp
                }
                if (bits and Broken.RAIL != 0) m.railLaid[i] = stamp
                if (bits and Broken.TRAM != 0) m.tramLaid[i] = stamp
                if (bits and Broken.WIRE != 0) m.wireLaid[i] = stamp
                if (bits and Broken.SUBWAY != 0) m.subwayLaid[i] = stamp
                if (bits and Broken.POWER != 0) m.powerLaid[i] = stamp
                if (bits and Broken.PHONE != 0) m.phoneLaid[i] = stamp
                if (bits and Broken.LOW != 0) m.lowLaid[i] = stamp
                startWorks(i, bits, queued++ / Balance.WORKS_PER_DAY)
            }
            is Action.RemoveTransit -> for (i in plan.changes) {
                m.lane[i] = 0
                m.tram[i] = 0
                m.wire[i] = 0
                m.stop[i] = 0
                m.subway[i] = 0
                m.tramLaid[i] = 0
                m.wireLaid[i] = 0
                m.subwayLaid[i] = 0
                clearBroken(i, Broken.TRAM or Broken.WIRE or Broken.SUBWAY)
            }
            is Action.BuildRail -> if (action.tunnel) {
                val path = action.tiles.filter { inMap(it) }
                val (runs, _) = runsOf(path)
                layTunnel(path.toIntArray(), runs, ByteArray(path.size), listOf(path.indices), null, plan.changes.toHashSet(), now) { works ->
                    startWorks(works, Broken.ROAD, queued++ / Balance.WORKS_PER_DAY)
                }
                railChanged = true
            } else {
              val path = action.tiles.filter { inMap(it) }
              val (runs, turns) = runsOf(path)
              val spans = bridgesAlong(path.toIntArray(), runs, turns, listOf(path.indices), action.bridge, rail = true)
              val spanAt = HashMap<Int, Int>()
              for (k in path.indices) spanAt[path[k]] = spans[k]
              for (i in plan.changes) {
                if (m.terrain[i] == Terrain.WATER) spanAt[i]?.takeIf { it >= 0 }?.let { m.bridge[i] = (it or (m.bridge[i].toInt() and (Bridge.SHUT or Bridge.TOLL))).toByte() }
                if (m.rail[i] != Rail.NONE) startWorks(i, Broken.RAIL, queued++ / Balance.WORKS_PER_DAY)
                m.rail[i] = Rail.TRACK
                m.railLaid[i] = now.toShort()
                m.zone[i] = Zone.NONE
                clearTrees(i)
              }
            }
            is Action.BuildPhoneLine -> for (i in plan.changes) {
                m.phone[i] = if (action.fibre) Phone.FIBRE else Phone.COPPER
                m.buried[i] = (if (action.buried) m.buried[i].toInt() or BURIED_PHONE else m.buried[i].toInt() and BURIED_PHONE.inv()).toByte()
                m.phoneLaid[i] = now.toShort()
                clearBroken(i, Broken.PHONE)
                clearTrees(i)
                if (action.buried && m.road[i] != Road.NONE) startWorks(i, Broken.ROAD, queued++ / Balance.WORKS_PER_DAY)
            }
            is Action.RemovePhone -> for (i in plan.changes) {
                m.phone[i] = 0
                m.phoneLaid[i] = 0
                m.buried[i] = (m.buried[i].toInt() and BURIED_PHONE.inv()).toByte()
                clearBroken(i, Broken.PHONE)
            }
            is Action.BuildPowerLine -> for (i in plan.changes) {
                m.power[i] = if (action.high) Power.HIGH else Power.LINE
                m.buried[i] = (if (action.buried) m.buried[i].toInt() or BURIED_POWER else m.buried[i].toInt() and BURIED_POWER.inv()).toByte()
                m.powerLaid[i] = now.toShort()
                clearBroken(i, Broken.POWER)
                clearTrees(i)
                // Cable under a street means digging it up for a few days.
                if (action.buried && m.road[i] != Road.NONE) startWorks(i, Broken.ROAD, queued++ / Balance.WORKS_PER_DAY)
            }
            is Action.PlaceZone -> for (i in plan.changes) {
                m.zone[i] = action.zone
                m.density[i] = action.density
            }
            is Action.PlaceBuilding -> {
                val t = action.type
                for (i in plan.changes) {
                    clearTrees(i)
                    val x = i % m.width
                    val y = i / m.width
                    // Outside the building's own lot: the reservoir.
                    if (x !in action.x until action.x + t.width || y !in action.y until action.y + t.height) {
                        m.terrain[i] = Terrain.WATER
                        flowDirty = true
                    }
                }
                added += addBuilding(t, action.x, action.y, rng.nextInt(1000))
            }
            is Action.PlaceParks -> for (i in plan.changes) {
                clearTrees(i)
                added += addBuilding(BuildingType.PARK, i % m.width, i / m.width, rng.nextInt(1000))
            }
            is Action.Bulldoze -> {
              var forcedOut = 0
              var jobsLost = 0
              var first = -1
              for (i in plan.changes) {
                buildings[m.building[i]]?.let {
                    if (first < 0 && it.type.zone != Zone.NONE) first = i
                    if (it.type.zone != Zone.NONE && it.underway == 0 && it.people == null) jobsLost += it.type.capacity
                    forcedOut += clearOut(it)
                    removed += it
                    removeBuilding(it)
                }
                m.road[i] = Road.NONE
                m.roadHeading[i] = Heading.BOTH
                m.rail[i] = Rail.NONE
                m.bridge[i] = 0
                m.bridgeShut[i] = 0
                m.roadLaid[i] = 0
                m.railLaid[i] = 0
                m.brownfield[i] = 0
                // The tram track, wire, stops, street trees and the crossing's control go with the road.
                m.streetTrees[i] = 0
                m.junction[i] = Junction.AUTO
                m.lane[i] = 0
                m.tram[i] = 0
                m.wire[i] = 0
                m.tramLaid[i] = 0
                m.wireLaid[i] = 0
                clearBroken(i, Broken.TRAM or Broken.WIRE)
                m.stop[i] = 0
                clearBroken(i, Broken.ROAD or Broken.RAIL)
                m.bank[i] = 0
                m.zone[i] = Zone.NONE
                m.density[i] = Density.NONE
                m.power[i] = Power.NONE
                m.phone[i] = 0
                m.buried[i] = 0
                clearBroken(i, Broken.POWER or Broken.PHONE)
                clearTrees(i)
              }
              if (forcedOut > 0) events += CityEvent(EventKind.ForcedOut, first % m.width, first / m.width, null, count = forcedOut)
              if (jobsLost > 0) events += CityEvent(EventKind.JobsLost, first % m.width, first / m.width, null, count = jobsLost)
            }
            is Action.RemoveTunnel -> {
                for (i in plan.changes) {
                    if (m.lowRail[i].toInt() != 0) railChanged = true
                    m.lowRoad[i] = 0
                    m.lowHeading[i] = 0
                    m.lowRail[i] = 0
                    m.portal[i] = 0
                    m.lowLaid[i] = 0
                    clearBroken(i, Broken.LOW)
                }
            }
            is Action.SetBridge -> {
                for (i in plan.changes) {
                    if (action.shut != null && m.rail[i] != Rail.NONE) railChanged = true
                    m.bridge[i] = bridgeBits(m.bridge[i].toInt(), action).toByte()
                }
                bridgeState()
            }
            is Action.RemovePipes -> for (i in plan.changes) {
                for (kind in Pipe.entries) {
                    pipes(kind)[i] = 0
                    laid(kind)[i] = 0
                }
                clearBroken(i, Broken.DUG)
            }
            is Action.BuildBank -> for (i in plan.changes) {
                m.bank[i] = 1
                m.zone[i] = Zone.NONE
                clearTrees(i)
            }
        }
        funds -= plan.cost
        undoable.addLast(
            Edit(
                plan.changes, before, LongArray(plan.changes.size) { m.tileState(plan.changes[it]) }, plan.cost, added, removed,
                laidBefore, LongArray(plan.changes.size) { m.tileLaid(plan.changes[it]) },
                transitBefore, LongArray(plan.changes.size) { m.tileTransitLaid(plan.changes[it]) },
                fixBefore, IntArray(plan.changes.size) { m.tileFix(plan.changes[it]) },
                linesBefore, lines.map { it.copy() },
                districtsBefore, districts.map { it.copy() },
                scrubbedBefore, buildings.values.filter { it.scrubbed }.map { it.id }.toSet(),
                renovated,
                utilBefore, LongArray(plan.changes.size) { m.tileUtil(plan.changes[it]) },
            ),
        )
        if (undoable.size > MAX_UNDO) undoable.removeFirst()
        redoable.clear()
        networksChanged()
        railChanged = true
        zonesChanged = true
        updateJunctions()
        if (action is Action.PaintDistrict || action is Action.SetDistrict || action is Action.RemoveDistrict) districtTraffic()
        // Lines show and run at once.
        if (action is Action.AddLine || action is Action.SetVehicles || action is Action.RemoveLine) updateTransit()
        // The ways to what's been cleared go with it.
        if (action is Action.Bulldoze) updatePathways()
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
        for (k in e.tiles.indices) {
            map.setTileState(e.tiles[k], e.before[k])
            map.setTileLaid(e.tiles[k], e.laidBefore[k])
            map.setTileTransitLaid(e.tiles[k], e.transitBefore[k])
            if (e.utilBefore.isNotEmpty()) map.setTileUtil(e.tiles[k], e.utilBefore[k])
            map.setTileFix(e.tiles[k], e.fixBefore[k])
            if (map.mendingDays(e.tiles[k]) > 0) mendingTiles += e.tiles[k] else mendingTiles -= e.tiles[k]
        }
        restamp(e.tiles)
        updateJunctions()
        lines.clear()
        lines += e.linesBefore.map { it.copy() }
        districts.clear()
        districts += e.districtsBefore.map { it.copy() }
        for (b in buildings.values) b.scrubbed = b.id in e.scrubbedBefore
        for (r in e.renovated) buildings[r[0]]?.let { reopen(it, r[1], r[2]) }
        updateTransit()
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
        for (k in e.tiles.indices) {
            map.setTileState(e.tiles[k], e.after[k])
            map.setTileLaid(e.tiles[k], e.laidAfter[k])
            map.setTileTransitLaid(e.tiles[k], e.transitAfter[k])
            if (e.utilAfter.isNotEmpty()) map.setTileUtil(e.tiles[k], e.utilAfter[k])
            map.setTileFix(e.tiles[k], e.fixAfter[k])
            if (map.mendingDays(e.tiles[k]) > 0) mendingTiles += e.tiles[k] else mendingTiles -= e.tiles[k]
        }
        restamp(e.tiles)
        updateJunctions()
        lines.clear()
        lines += e.linesAfter.map { it.copy() }
        districts.clear()
        districts += e.districtsAfter.map { it.copy() }
        for (b in buildings.values) b.scrubbed = b.id in e.scrubbedAfter
        for (r in e.renovated) buildings[r[0]]?.let { reopen(it, r[3], r[4]) }
        updateTransit()
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

    private fun addBuilding(type: BuildingType, x: Int, y: Int, variant: Int, underway: Int = 0): Building {
        val b = Building(nextId++, type, x, y, variant)
        b.underway = underway
        b.built = monthNow
        if (type.zone == Zone.INDUSTRIAL) b.kind = chooseKind(type.capacity)
        if (underway > 0) sites += b.id
        buildings[b.id] = b
        stamp(b)
        fitHousehold(b)
        return b
    }

    /** People forced out of town by clearings lately, fading a twelfth a month; it puts settlers off. */
    var displaced = 0
        private set

    /**
     * What clearing [b] pays its owners: by the places in it and the land's
     * value, a share if it stands empty or isn't finished, twice over for
     * heritage. Nothing for what the town runs.
     */
    fun worth(b: Building): Long {
        val per = when (b.type.zone) {
            Zone.RESIDENTIAL -> Balance.WORTH_HOME
            Zone.COMMERCIAL, Zone.OFFICE -> Balance.WORTH_SHOP
            Zone.INDUSTRIAL, Zone.FARMLAND -> Balance.WORTH_WORKS
            else -> return 0
        }
        val value = map.landValue[map.index(b.x, b.y)].toInt() and 0xff
        var w = b.type.capacity.toLong() * per * (100 + value) / 200
        if (b.underway > 0) w = w * (b.type.buildDays - b.underway) / max(1, b.type.buildDays)
        else if (b.people?.empty == true) w = w * Balance.WORTH_EMPTY / 100
        if (isHeritage(b)) w *= Balance.HERITAGE_WORTH
        return w
    }

    /**
     * The town clears [b]: the neighbours are upset, and its people find what
     * empty homes there are, nearest first, or leave town. It goes on the undo
     * list empty, since they've gone. How many left town.
     */
    private fun clearOut(b: Building): Int {
        if (b.type.zone == Zone.NONE) return 0
        val h = b.people
        val lived = h != null && !h.empty
        if (lived || (b.people == null && b.underway == 0) || isHeritage(b)) upsetAround(b)
        if (h == null || h.empty) return 0
        val empty = homes.filter { it !== b && it.underway == 0 && it.people!!.empty }.sortedBy { abs(it.x - b.x) + abs(it.y - b.y) }
        for (e in empty) {
            if (h.empty) break
            val to = e.people!!
            to.wealth = h.wealth
            to.health = h.health
            to.forSale = 0
            moveOut(h, to, min(h.size, e.type.capacity))
            // Whoever buys the rest of a bigger home.
            if (to.size < e.type.capacity) arrive(to, e.type.capacity - to.size)
            markForSale(e)
        }
        val left = h.size
        departures += left
        displaced += left
        b.people = Household(0, 0, 0, h.wealth)
        return left
    }

    /** Moves [count] of [from]'s people, picked at random, to [to]. */
    private fun moveOut(from: Household, to: Household, count: Int) {
        repeat(count) {
            val pick = rng.nextInt(from.size)
            when {
                pick < from.children -> { from.children--; to.children++ }
                pick < from.children + from.elderly -> { from.elderly--; to.elderly++ }
                else -> {
                    var p = rng.nextInt(from.adults)
                    var k = 0
                    while (p >= from.schooled[k]) p -= from.schooled[k++]
                    from.schooled[k]--
                    from.adults--
                    to.schooled[k]++
                    to.adults++
                }
            }
        }
    }

    /** Upsets the neighbours of [b], cleared: those nearby, or for heritage its whole district or further round. */
    private fun upsetAround(b: Building) {
        val m = map
        fun raise(j: Int) {
            val u = m.upset[j].toInt() and 0xff
            m.upset[j] = min(250, if (u < Balance.UPSET) Balance.UPSET else u + Balance.UPSET_MORE).toByte()
        }
        val heritage = isHeritage(b)
        val district = if (heritage) districtAt(m.index(b.x, b.y)) else null
        if (district != null) {
            for (j in 0 until m.size) if ((m.district[j].toInt() and 0xff) == district.id) raise(j)
        } else {
            around(b.x, b.y, if (heritage) Balance.HERITAGE_UPSET_REACH else Balance.UPSET_REACH) { j, _ -> raise(j) }
        }
    }

    /** The upset fades, and the town's name for forcing people out. */
    private fun fadeUpset() {
        val m = map
        for (j in 0 until m.size) {
            val u = m.upset[j].toInt() and 0xff
            if (u > 0) m.upset[j] = max(0, u - Balance.UPSET_FADE).toByte()
        }
        displaced = displaced * 11 / 12
    }

    /** By how much, in percent, the people forced out put settlers off. */
    private fun displacedCut(): Int = min(Balance.DISPLACED_MOST, displaced * 100 / (stats.population / 10 + 50))

    /**
     * Whether the zoning still allows [b]: its kind of zone on all its lots,
     * built no denser than they allow, and rural only on rural lots and the
     * other way round. What the town runs always does.
     */
    fun conforms(b: Building): Boolean {
        val zone = b.type.zone
        if (zone == Zone.NONE) return true
        var ok = true
        forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { i ->
            if (map.zone[i] != zone) {
                ok = false
            } else if (zone != Zone.FARMLAND) {
                val height = heightAt(i)
                if (Density.rank(b.type.density) > Density.rank(height) || (b.type.density == Density.RURAL) != (height == Density.RURAL)) ok = false
            }
        }
        return ok
    }

    /**
     * Buildings the zoning no longer allows: an empty home comes down rather
     * than sell, and the rest once they're old, a few each month, leaving the
     * lot to grow as it's zoned now. Kept heritage stays.
     */
    private fun replaceNonconforming() {
        val old = Balance.NONCONFORMING_YEARS * 12
        for (b in buildings.values.toList()) {
            if (b.type.zone == Zone.NONE || b.underway > 0 || b.burning > 0 || conforms(b)) continue
            val i = map.index(b.x, b.y)
            if (districtAt(i)?.heritage == true && isHeritage(b)) continue
            if (b.people?.empty == true || (monthNow - b.built >= old && rng.nextInt(Balance.NONCONFORMING_ODDS) == 0)) {
                forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { townChanges += it }
                removeBuilding(b)
                networksChanged()
            }
        }
    }

    private fun removeBuilding(b: Building) {
        b.people?.let { departures += it.size }
        buildings.remove(b.id)
        sites.remove(b.id)
        outages.remove(b.id)
        forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { i ->
            map.building[i] = 0
            map.buildingType[i] = 0
            map.forSale[i] = false
            map.site[i] = 0
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
            map.site[i] = sitePhase(b)
        }
    }

    /** How far along a building going up is, for the map: 1 while the ground's dug, 2 once the frame's up, 0 when it stands. */
    private fun sitePhase(b: Building): Byte = when {
        b.underway == 0 -> 0
        b.underway * 2 > b.type.buildDays -> 1
        else -> 2
    }.toByte()

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
            map.site[i] = if (b == null) 0 else sitePhase(b)
        }
    }

    // ---- time ----------------------------------------------------------------

    /** Moves on a day: growth every day, and the census, demand, money and grime on the first of each month. */
    fun tick() {
        if (networksDirty) updateNetworks()
        for (b in buildings.values) b.age++
        burnDay()
        mendDay()
        buildDay()
        growDay()
        traffic.sendDay(day, daysIn(month, year))
        drainFloods()
        river = max(0, river - Balance.RIVER_FALL)
        if (snowedIn > 0) snowedIn--
        if (day % Balance.WEATHER_DAYS == 1) {
            val snow = weather.snowCover
            weather.nextDay(month, day, daysIn(month, year), Balance.WEATHER_DAYS, warming)
            val rain = if (weather.precipitation == Precipitation.Rain) weather.intensity else 0
            val melt = max(0, snow - weather.snowCover) * Balance.MELT_RUNOFF
            val frozen = weather.temperature <= 0
            // Drizzle soaks in wherever it falls.
            if (rain + melt >= Balance.DOWNPOUR) rainfall(rain + melt, frozen)
            riseRivers(rain + melt)
            wetGround(rain + melt, frozen, weather.temperature)
            if (river > Balance.BANKFULL) overflowRivers()
            weatherDisasters()
        }
        bridgeWeather()
        traffic.snowedIn = snowedIn > 0
        day++
        if (day > daysIn(month, year)) {
            day = 1
            month++
            if (month == 12) {
                month = 0
                year++
                heatWavesLastYear = heatWavesThisYear
                floodsLastYear = floodsThisYear
                heatWavesThisYear = 0
                floodsThisYear = 0
            }
            newMonth()
        }
    }

    private fun newMonth() {
        // Undo is for slips of the finger, so it's cleared each month.
        undoable.clear()
        redoable.clear()
        if (networksDirty) updateNetworks()
        powerCuts()
        waterCuts()
        updateFoul()
        fadeFloodMemory()
        updatePollution()
        updateEnvironment()
        updateGrime()
        updateServices()
        updateComms()
        railFlags()
        updatePorts()
        updatePathways()
        wearOut()
        floodTunnels(underWaterOnly = true)
        truckWear()
        bridgeState()
        accidents()
        earthquake()
        epidemic()
        replaceNonconforming()
        updatePeople()
        fadeUpset()
        heatWaveDays = 0
        census()
        updateAirports()
        tourism()
        startTraffic()
        updateCrime()
        Effects.landValue(map, { i -> buildings[map.building[i]]?.type }, nearRoad, map.landValue) { i ->
            // People and jobs on the tile, a building's shared over its lots.
            val b = buildings[map.building[i]]
            if (b == null || b.underway > 0) 0 else (b.people?.size ?: b.type.capacity) / (b.type.width * b.type.height)
        }
        startFires()
        demand()
        money()
        carbon()
        record()
        newEra()
    }

    // ---- wear and repairs --------------------------------------------------------------

    /** Tiles with something broken being mended, or works on them. */
    private val mendingTiles = LinkedHashSet<Int>()

    /** Works the city runs that have broken down, by id. */
    private val outages = LinkedHashSet<Int>()

    /** What mending has cost so far this month. */
    private var repairBill = 0L

    /** Starts works relaying what [bits] marks on tile [i], once the crews get there in [wait] days. */
    private fun startWorks(i: Int, bits: Int, wait: Int) {
        val m = map
        m.broken[i] = (m.broken[i].toInt() or bits or Broken.WORKS).toShort()
        m.mending[i] = min(255, max(m.mendingDays(i), Balance.WORKS_DAYS + wait)).toByte()
        mendingTiles += i
    }

    /** Clears what [bits] marks broken on tile [i], when what's broken has been taken away. */
    private fun clearBroken(i: Int, bits: Int) {
        val m = map
        val left = m.broken[i].toInt() and bits.inv()
        if (left and Broken.WORKS.inv() == 0) {
            m.broken[i] = 0
            m.mending[i] = 0
            mendingTiles -= i
        } else {
            m.broken[i] = left.toShort()
        }
    }

    /** A pipe layer with what's out of use taken out, or the layer itself if nothing is. */
    private fun working(pipes: ByteArray, bit: Int): ByteArray {
        if (mendingTiles.isEmpty()) return pipes
        var copy: ByteArray? = null
        for (i in mendingTiles) {
            if (pipes[i].toInt() == 0 || !map.out(i, bit)) continue
            val c = copy ?: pipes.copyOf().also { copy = it }
            c[i] = 0
        }
        return copy ?: pipes
    }

    /** Whether something laid in [laid] and expected to last [life] years gives way this month. */
    private fun givesWay(laid: Int, life: Int): Boolean {
        val chance = Ageing.failChance(monthNow - laid, life)
        return chance > 0 && rng.nextInt(1_000_000) < chance
    }

    /**
     * A month's wear: anything old may give way. A burst main floods the street
     * and cuts the water beyond it, a broken sewer fouls the street and cuts the
     * homes beyond it off the sewer, and both shut the road while it's dug up.
     * A broken road surface slows the traffic, broken track stops the trains, and
     * a worn-out power station or waterworks breaks down for weeks. Crews mend
     * each in days, for a bill.
     */
    private fun wearOut() {
        val m = map
        var burst = -1
        var collapsed = -1
        var track = -1
        var tramBroke = -1
        var wireDown = -1
        var tunnelShut = -1
        for (i in 0 until m.size) {
            if (m.broken[i].toInt() != 0) continue
            val water = Material.of(Pipe.WATER, m.waterPipe[i])
            if (water != null && givesWay(m.waterLaid[i].toInt(), water.life)) {
                fail(i, Broken.WATER, Balance.MEND_MAIN, Balance.REPAIR_MAIN)
                m.flood[i] = max(m.flood[i].toInt() and 0xff, Balance.BURST_FLOOD).toByte()
                floodsStanding = true
                burst = i
                continue
            }
            val sewer = Material.of(Pipe.SEWER, m.sewerPipe[i])
            if (sewer != null && givesWay(m.sewerLaid[i].toInt(), sewer.life)) {
                fail(i, Broken.SEWER, Balance.MEND_SEWER, Balance.REPAIR_SEWER)
                around(i % m.width, i / m.width, 1) { j, _ -> m.grime[j] = min(255, (m.grime[j].toInt() and 0xff) + Balance.SEWER_GRIME).toByte() }
                collapsed = i
                continue
            }
            val storm = Material.of(Pipe.STORM, m.stormPipe[i])
            if (storm != null && givesWay(m.stormLaid[i].toInt(), storm.life)) {
                fail(i, Broken.STORM, Balance.MEND_DRAIN, Balance.REPAIR_DRAIN)
                continue
            }
            val road = RoadType.of(m.road[i])
            if (road != null && givesWay(m.roadLaid[i].toInt(), roadLife(i, road))) {
                fail(i, Broken.ROAD, Balance.MEND_ROAD, Balance.REPAIR_ROAD * (if (m.terrain[i] == Terrain.WATER) bridgePrice(m.bridge[i].toInt()) else 1))
                continue
            }
            if (m.rail[i] != Rail.NONE && givesWay(m.railLaid[i].toInt(), Balance.TRACK_LIFE)) {
                fail(i, Broken.RAIL, Balance.MEND_TRACK, Balance.REPAIR_TRACK)
                railChanged = true
                track = i
                continue
            }
            if (m.tram[i].toInt() != 0 && givesWay(m.tramLaid[i].toInt(), Balance.TRAM_TRACK_LIFE)) {
                fail(i, Broken.TRAM, Balance.MEND_TRACK, Balance.REPAIR_TRACK)
                tramBroke = i
                continue
            }
            if (m.wire[i].toInt() != 0 && givesWay(m.wireLaid[i].toInt(), Balance.WIRE_LIFE)) {
                fail(i, Broken.WIRE, Balance.MEND_WIRE, Balance.REPAIR_WIRE)
                wireDown = i
                continue
            }
            // Cable underground wears out and has to be dug up to mend; lines overhead are kept up as they go.
            if (m.cable(i) && givesWay(m.powerLaid[i].toInt(), cableLife(i))) {
                fail(i, Broken.POWER, Balance.MEND_CABLE, Balance.REPAIR_CABLE)
                continue
            }
            if (m.phone[i].toInt() != 0 && givesWay(m.phoneLaid[i].toInt(), phoneLife(i))) {
                fail(i, Broken.PHONE, if (m.duct(i)) Balance.MEND_DUCT else Balance.MEND_PHONE, Balance.REPAIR_PHONE)
                continue
            }
            if (m.subway[i].toInt() != 0 && givesWay(m.subwayLaid[i].toInt(), Balance.TUNNEL_LIFE)) {
                fail(i, Broken.SUBWAY, Balance.MEND_TUNNEL, Balance.REPAIR_TUNNEL)
                tunnelShut = i
            }
            if (m.tunnelled(i) && !m.tunnelShut(i) && givesWay(m.lowLaid[i].toInt(), Balance.TUNNEL_LIFE)) {
                fail(i, Broken.LOW, Balance.MEND_TUNNEL, Balance.REPAIR_TUNNEL)
                if (m.lowRail[i].toInt() != 0) railChanged = true
                tunnelShut = i
            }
        }
        if (burst >= 0) events += CityEvent(EventKind.MainBurst, burst % m.width, burst / m.width, null)
        if (collapsed >= 0) events += CityEvent(EventKind.SewerCollapsed, collapsed % m.width, collapsed / m.width, null)
        if (track >= 0) events += CityEvent(EventKind.TrackBroken, track % m.width, track / m.width, null)
        if (tramBroke >= 0) events += CityEvent(EventKind.TramTrackBroken, tramBroke % m.width, tramBroke / m.width, null)
        if (wireDown >= 0) events += CityEvent(EventKind.WireDown, wireDown % m.width, wireDown / m.width, null)
        if (tunnelShut >= 0) events += CityEvent(EventKind.TunnelShut, tunnelShut % m.width, tunnelShut / m.width, null)
        for (b in buildings.values.toList()) {
            if (b.type.life == 0 || b.outage > 0 || b.underway > 0) continue
            if (!givesWay(b.built, b.type.life)) continue
            b.outage = max(1, Balance.MEND_PLANT * 100 / reliefFunding)
            outages += b.id
            repairBill += Prices.of(b.type) / 10
            networksDirty = true
            events += CityEvent(EventKind.BrokeDown, b.x, b.y, b.type)
        }
    }

    /** Marks [bit] broken on tile [i] for [days], and adds the [cost] of mending it to the month's bill. */
    private fun fail(i: Int, bit: Int, days: Int, cost: Long) {
        map.broken[i] = (map.broken[i].toInt() or bit).toShort()
        // Emergency repairs: more money, quicker mending.
        map.mending[i] = max(1, days * 100 / reliefFunding).coerceAtMost(255).toByte()
        mendingTiles += i
        repairBill += cost * reliefFunding / 100
        if (bit and Broken.NETWORKS != 0) networksDirty = true
    }

    /**
     * A day's mending. A failure that's mended is patched, which puts back a
     * little of its life but doesn't make it new. Works that are done leave what
     * they relaid new; works that reach the next street dig it up.
     */
    private fun mendDay() {
        val m = map
        if (mendingTiles.isNotEmpty()) {
            val done = ArrayList<Int>()
            for (i in mendingTiles) {
                val days = m.mendingDays(i) - 1
                m.mending[i] = days.toByte()
                // The crews reaching a tile take its pipes out of use and shut its road.
                if (days == Balance.WORKS_DAYS && (m.broken[i].toInt() and Broken.WORKS) != 0) {
                    networksDirty = true
                    railChanged = true
                }
                if (days <= 0) done += i
            }
            for (i in done) {
                val bits = m.broken[i].toInt()
                if (bits and Broken.WORKS == 0) patch(i, bits)
                m.broken[i] = 0
                m.mending[i] = 0
                mendingTiles -= i
                if (bits and (Broken.NETWORKS or Broken.WORKS) != 0) {
                    networksDirty = true
                    railChanged = true
                }
            }
        }
        if (outages.isNotEmpty()) {
            val done = ArrayList<Int>()
            for (id in outages) {
                val b = buildings[id]
                if (b == null) {
                    done += id
                    continue
                }
                b.outage--
                if (b.outage <= 0) {
                    b.outage = 0
                    b.built = min(monthNow, b.built + b.type.life * 12 * Balance.PATCH_SHARE / 100)
                    done += id
                    networksDirty = true
                }
            }
            outages.removeAll(done.toSet())
        }
    }

    /**
     * How worn the most worn thing on tile [i] is, in percent of its expected
     * life: its road, pipes, track or building. -1 if there's nothing there to wear.
     */
    fun wearAt(i: Int): Int {
        val m = map
        var most = -1
        val now = monthNow
        RoadType.of(m.road[i])?.let { most = max(most, Ageing.wear(now - m.roadLaid[i], it.life)) }
        Material.of(Pipe.WATER, m.waterPipe[i])?.let { most = max(most, Ageing.wear(now - m.waterLaid[i], it.life)) }
        Material.of(Pipe.SEWER, m.sewerPipe[i])?.let { most = max(most, Ageing.wear(now - m.sewerLaid[i], it.life)) }
        Material.of(Pipe.STORM, m.stormPipe[i])?.let { most = max(most, Ageing.wear(now - m.stormLaid[i], it.life)) }
        if (m.rail[i] != Rail.NONE) most = max(most, Ageing.wear(now - m.railLaid[i], Balance.TRACK_LIFE))
        if (m.tram[i].toInt() != 0) most = max(most, Ageing.wear(now - m.tramLaid[i], Balance.TRAM_TRACK_LIFE))
        if (m.wire[i].toInt() != 0) most = max(most, Ageing.wear(now - m.wireLaid[i], Balance.WIRE_LIFE))
        if (m.subway[i].toInt() != 0) most = max(most, Ageing.wear(now - m.subwayLaid[i], Balance.TUNNEL_LIFE))
        if (m.cable(i)) most = max(most, Ageing.wear(now - m.powerLaid[i], cableLife(i)))
        if (m.phone[i].toInt() != 0) most = max(most, Ageing.wear(now - m.phoneLaid[i], phoneLife(i)))
        buildings[m.building[i]]?.let { b ->
            val life = if (b.type.life > 0) b.type.life else Balance.WORN_YEARS * 2
            if (b.underway == 0) most = max(most, Ageing.wear(now - b.built, life))
        }
        return most
    }

    /** Puts back a little of the life of what [bits] marks on tile [i]. */
    private fun patch(i: Int, bits: Int) {
        val m = map
        fun younger(a: ShortArray, life: Int) {
            a[i] = min(monthNow, a[i] + life * 12 * Balance.PATCH_SHARE / 100).toShort()
        }
        if (bits and Broken.WATER != 0) Material.of(Pipe.WATER, m.waterPipe[i])?.let { younger(m.waterLaid, it.life) }
        if (bits and Broken.SEWER != 0) Material.of(Pipe.SEWER, m.sewerPipe[i])?.let { younger(m.sewerLaid, it.life) }
        if (bits and Broken.STORM != 0) Material.of(Pipe.STORM, m.stormPipe[i])?.let { younger(m.stormLaid, it.life) }
        if (bits and Broken.ROAD != 0) RoadType.of(m.road[i])?.let { younger(m.roadLaid, it.life) }
        if (bits and Broken.RAIL != 0) younger(m.railLaid, Balance.TRACK_LIFE)
        if (bits and Broken.TRAM != 0) younger(m.tramLaid, Balance.TRAM_TRACK_LIFE)
        if (bits and Broken.WIRE != 0) younger(m.wireLaid, Balance.WIRE_LIFE)
        if (bits and Broken.SUBWAY != 0) younger(m.subwayLaid, Balance.TUNNEL_LIFE)
        if (bits and Broken.POWER != 0 && m.cable(i)) younger(m.powerLaid, cableLife(i))
        if (bits and Broken.PHONE != 0 && m.phone[i].toInt() != 0) younger(m.phoneLaid, phoneLife(i))
    }

    /** How far a back lane runs along a block looking for a road at its end. */
    private val LANE_REACH = 16

    /** In [CityMap.pathway], a way as wide as a lane. */
    private val WIDE_WAY = 0x400

    /** How long the phone line on tile [i] is expected to last, in years. */
    fun phoneLife(i: Int): Int = if (map.phone[i] == Phone.FIBRE) Balance.FIBRE_LIFE else Balance.COPPER_LIFE

    /** Whether the town can lay fibre yet. */
    fun allowsFibre(): Boolean = everything || year >= Balance.FIBRE_YEAR

    /** How long the cable on tile [i] is expected to last, in years. */
    fun cableLife(i: Int): Int = if (map.power[i] == Power.HIGH) Balance.HIGH_CABLE_LIFE else Balance.CABLE_LIFE

    /** Whether the town can lay high-voltage cable yet. */
    fun allowsHighCable(): Boolean = everything || year >= Balance.HIGH_CABLE_YEAR

    // ---- environment -------------------------------------------------------------

    private var flowCache = IntArray(0)
    private var flowDirty = true

    /**
     * Which way the rivers run: for each water tile on a river (water that
     * reaches the map's edge in more than one place), its distance from where
     * the river comes in, so higher is downstream. -1 for lakes and land.
     */
    private fun flow(): IntArray {
        if (!flowDirty && flowCache.size == map.size) return flowCache
        flowDirty = false
        val m = map
        val f = IntArray(m.size) { -1 }
        val seen = BooleanArray(m.size)
        val queue = IntArray(m.size)
        fun edge(i: Int): Boolean {
            val x = i % m.width
            val y = i / m.width
            return x == 0 || y == 0 || x == m.width - 1 || y == m.height - 1
        }
        for (start in 0 until m.size) {
            if (seen[start] || m.terrain[start] != Terrain.WATER) continue
            // The whole body of water, and where it meets the edge.
            var head = 0
            var tail = 0
            queue[tail++] = start
            seen[start] = true
            val edges = ArrayList<Int>()
            while (head < tail) {
                val i = queue[head++]
                if (edge(i)) edges += i
                val x = i % m.width
                val y = i / m.width
                for (k in 0 until 4) {
                    val nx = x + DX[k]
                    val ny = y + DY[k]
                    if (!m.inside(nx, ny)) continue
                    val j = m.index(nx, ny)
                    if (seen[j] || m.terrain[j] != Terrain.WATER) continue
                    seen[j] = true
                    queue[tail++] = j
                }
            }
            // A river meets the edge at two places far apart; it comes in at the first of them in reading order.
            val inlet = edges.minOrNull() ?: continue
            val outlet = edges.maxOrNull() ?: continue
            val apart = kotlin.math.abs(inlet % m.width - outlet % m.width) + kotlin.math.abs(inlet / m.width - outlet / m.width)
            if (apart < m.width / 3) continue
            head = 0
            tail = 0
            for (e in edges) if (kotlin.math.abs(e % m.width - inlet % m.width) + kotlin.math.abs(e / m.width - inlet / m.width) <= 4) {
                f[e] = 0
                queue[tail++] = e
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
                    if (f[j] >= 0 || m.terrain[j] != Terrain.WATER) continue
                    f[j] = f[i] + 1
                    queue[tail++] = j
                }
            }
        }
        flowCache = f
        return f
    }

    /**
     * The land a hydro station at [x], [y] floods: clear land within
     * [Balance.RESERVOIR_REACH] beside water that's level with or upstream of
     * the water by the station.
     */
    private fun reservoir(x: Int, y: Int): List<Int> {
        val m = map
        val t = BuildingType.HYDRO_PLANT
        val f = flow()
        var here = Int.MAX_VALUE
        forRect(x - 1, y - 1, x + t.width, y + t.height) { j -> if (m.terrain[j] == Terrain.WATER && f[j] >= 0) here = min(here, f[j]) }
        // The water rises a tile at a time, out from the river above the station.
        val wet = LinkedHashSet<Int>()
        val r = Balance.RESERVOIR_REACH
        repeat(r) {
            val rising = ArrayList<Int>()
            forRect(x - r, y - r, x + t.width - 1 + r, y + t.height - 1 + r) { i ->
                val ix = i % m.width
                val iy = i / m.width
                if (i in wet || ix in x until x + t.width && iy in y until y + t.height) return@forRect
                if (m.terrain[i] == Terrain.WATER || m.building[i] != 0 || m.road[i] != Road.NONE || m.rail[i] != Rail.NONE ||
                    m.zone[i] != Zone.NONE || m.power[i] != Power.NONE || m.bank[i].toInt() != 0
                ) return@forRect
                for (k in 0 until 4) {
                    val nx = ix + DX[k]
                    val ny = iy + DY[k]
                    if (!m.inside(nx, ny)) continue
                    val j = m.index(nx, ny)
                    if (j in wet || m.terrain[j] == Terrain.WATER && (f[j] < 0 || here == Int.MAX_VALUE || f[j] <= here)) {
                        rising += i
                        break
                    }
                }
            }
            wet += rising
        }
        return wet.toList()
    }

    /** The river's flow at tile [i] as [flow] has it, for drawing and tests. */
    fun flowAt(i: Int): Int = flow()[i]

    /** Kilograms of garbage each person makes a month, by era; a job half as much. */
    private fun wastePerPerson(): Int = when {
        year < 1950 -> 15 + (year - 1900).coerceAtLeast(0) / 5
        year < 1970 -> 25 + (year - 1950) * 3 / 4
        year < 2000 -> 40 + (year - 1970) / 6
        else -> maxOf(30, 45 - (year - 2000) / 3)
    }

    /**
     * A month's environment: how hot each tile runs, the smog hanging over the
     * town in still air, and the garbage: each building's taken to the nearest
     * dump or incinerator with room within reach, some to recycling first, and
     * what nobody takes piles up. A small town burns its own in the yard.
     */
    private fun updateEnvironment() {
        val m = map
        val s = stats
        // Heat: paving and roofs round a tile warm it; trees, parks, street trees and water cool it.
        // Green counted in hundredths: a park cools more once it has splash pads and shade, a green roof half as much.
        val park = if (year >= Balance.PARK_COOLS_YEAR) 100 + Balance.PARK_COOLS_MORE else 100
        val green = SummedArea(m.width, m.height) {
            when {
                m.buildingType[it].toInt() - 1 == BuildingType.PARK.ordinal -> park
                m.terrain[it] == Terrain.TREES || m.terrain[it] == Terrain.WATER || m.streetTrees[it].toInt() != 0 -> 100
                m.building[it] != 0 && greenRoof(it) -> Balance.GREEN_ROOF_GREEN
                else -> 0
            }
        }
        val hard = SummedArea(m.width, m.height) {
            if (m.terrain[it] == Terrain.WATER) 0
            else Stormwater.hardness(m, it) * (if (coolRoof(it)) Balance.COOL_ROOF_HEAT else 100) / 100
        }
        val r = Balance.HEAT_REACH
        val area = (2 * r + 1) * (2 * r + 1)
        for (i in 0 until m.size) {
            val x = i % m.width
            val y = i / m.width
            val h = hard.around(x, y, r) * 2 / area - green.around(x, y, r) * Balance.GREEN_COOLS / 100
            m.heat[i] = h.coerceIn(0, 255).toByte()
        }

        // Smog: the town's pollution, held in still air, more in the cold and fog.
        var pollution = 0L
        var built = 0
        for (i in 0 until m.size) if (m.building[i] != 0) {
            pollution += m.pollution[i].toInt() and 0xff
            built++
        }
        val w = weather
        // Spread over the town, and over at least a small town's land, so one smoking chimney in a field is no smog.
        var smog = if (built == 0) 0L else pollution / maxOf(built, Balance.SMOG_TOWN) * (100 - w.windSpeed) / 100
        if (w.temperature <= 5) smog = smog * Balance.SMOG_COLD / 100
        if (w.fog) smog = smog * Balance.SMOG_FOG / 100
        val before = s.smog
        s.smog = smog.coerceIn(0, 255).toInt()
        if (before < Balance.SMOG_WARNING && s.smog >= Balance.SMOG_WARNING) events += CityEvent(EventKind.Smog, -1, -1, null)

        // Garbage.
        val dumps = buildings.values.filter { it.type == BuildingType.DUMP && it.underway == 0 }.sortedBy { it.id }
        val fullBefore = dumps.filter { it.fill > Balance.DUMP_ROOM - Balance.DUMP_FULL }.map { it.id }.toSet()
        val burners = buildings.values.filter { it.type == BuildingType.INCINERATOR && it.underway == 0 && it.outage == 0 }.sortedBy { it.id }
        val recyclers = buildings.values.filter { it.type == BuildingType.RECYCLING && it.underway == 0 }.sortedBy { it.id }
        val burnt = HashMap<Int, Int>()
        val recycled = HashMap<Int, Int>()
        val per = wastePerPerson()
        var made = 0L
        var taken = 0L
        fun near(f: Building, b: Building) = kotlin.math.abs(f.x - b.x) + kotlin.math.abs(f.y - b.y) <= Balance.GARBAGE_REACH
        for (b in buildings.values.sortedBy { it.id }) {
            // Farms and mines see to their own, out where they are.
            val people = b.people?.size ?: if (b.type.zone != Zone.NONE && b.type.zone != Zone.FARMLAND) b.type.capacity / 2 else 0
            if (people == 0 || b.underway > 0) {
                b.uncollected = false
                continue
            }
            var waste = people * per
            made += waste
            // Some to recycling first.
            recyclers.firstOrNull { near(it, b) && (recycled[it.id] ?: 0) < Balance.RECYCLING_TAKES }?.let { r0 ->
                val take = min(waste * Balance.RECYCLED / 100, Balance.RECYCLING_TAKES - (recycled[r0.id] ?: 0))
                recycled[r0.id] = (recycled[r0.id] ?: 0) + take
                waste -= take
                taken += take
            }
            val burner = burners.firstOrNull { near(it, b) && (burnt[it.id] ?: 0) + waste <= Balance.INCINERATOR_TAKES }
            val dump = if (burner == null) dumps.firstOrNull { near(it, b) && it.fill + waste <= Balance.DUMP_ROOM } else null
            when {
                burner != null -> burnt[burner.id] = (burnt[burner.id] ?: 0) + waste
                dump != null -> dump.fill += waste
            }
            b.uncollected = burner == null && dump == null && s.population >= garbageTown
            if (b.uncollected) {
                // It piles up in the yard.
                forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { j ->
                    m.grime[j] = min(255, (m.grime[j].toInt() and 0xff) + Balance.UNCOLLECTED_GRIME).toByte()
                }
            } else {
                taken += waste
            }
        }
        incinerated.clear()
        incinerated.putAll(burnt)
        s.waste = (made / 1000).toInt()
        s.wasteCollected = if (made == 0L) 100 else (taken * 100 / made).toInt()
        s.dumpRoom = dumps.sumOf { (Balance.DUMP_ROOM - it.fill).toLong() / 1000 }.toInt()
        // Say so when a dump's filled up this month.
        for (d in dumps) if (d.fill > Balance.DUMP_ROOM - Balance.DUMP_FULL && d.id !in fullBefore) {
            events += CityEvent(EventKind.DumpFull, d.x, d.y, d.type)
        }
    }

    /** Last month's garbage burnt at each incinerator, in kilograms, for its smoke. */
    private val incinerated = HashMap<Int, Int>()

    // ---- disasters ---------------------------------------------------------------

    /** The size of town from which garbage needs taking away; lowered in tests. */
    internal var garbageTown = Balance.GARBAGE_TOWN

    /** How often disasters come: 0 never, 1 fewer, 2 normal. Set from the player's settings; not saved. */
    var disasterLevel = 2

    /** Whether this map has earthquakes, chosen with its land. */
    var quakes = terrain?.quakes ?: false
        internal set

    /** How much emergency repairs get, in percent of normal: more mends things faster, for more. */
    var reliefFunding = 100

    /** Days left snowed in by a blizzard, days of heat wave this month, and the epidemic going round, if there is one. */
    var snowedIn = 0
        private set
    private var heatWaveDays = 0
    private var epidemicMonths = 0
    private var epidemicStrength = 0
    private var hadFlu = false

    /** The clean-up after earthquakes, accidents and storms, this month so far. */
    private var disasterBill = 0L

    /** Whether a chance in a million [ppm], scaled by the disaster setting, comes up. */
    private fun disaster(ppm: Int): Boolean = disasterLevel > 0 && rng.nextInt(1_000_000) < ppm * disasterLevel / 2

    /** Is the epidemic going round now. */
    val epidemicNow get() = epidemicMonths > 0

    /** What comes with the weather: gales, blizzards and heat waves. */
    private fun weatherDisasters() {
        if (disasterLevel == 0) return
        val w = weather
        if (w.windSpeed >= Weather.GALE) gale()
        if (w.precipitation == Precipitation.Snow && w.intensity >= Balance.BLIZZARD && w.windSpeed >= Balance.BLIZZARD_WIND && snowedIn == 0) {
            val garages = buildings.values.count { it.type == BuildingType.BUS_GARAGE }
            snowedIn = max(1, Balance.BLIZZARD_DAYS - garages)
            events += CityEvent(EventKind.Blizzard, -1, -1, null)
        }
        if (w.temperature >= w.climate.heatWave) {
            if (heatWaveDays == 0) {
                events += CityEvent(EventKind.HeatWave, -1, -1, null)
                heatWavesThisYear++
            }
            heatWaveDays += Balance.WEATHER_DAYS
        }
    }

    /** A gale brings down power lines, trolleybus wire and trees. */
    internal fun gale() {
        val m = map
        var hit = -1
        val chance = Balance.GALE_DOWN * disasterLevel / 2
        for (i in 0 until m.size) {
            // Cable underground is out of the wind.
            if (m.phone[i].toInt() != 0 && !m.duct(i) && !m.out(i, Broken.PHONE) && rng.nextInt(100) < chance) {
                fail(i, Broken.PHONE, Balance.MEND_PHONE, Balance.REPAIR_PHONE)
                hit = i
            }
            if (m.power[i] != Power.NONE && !m.cable(i) && !m.out(i, Broken.POWER) && rng.nextInt(100) < chance) {
                fail(i, Broken.POWER, Balance.MEND_LINE, Balance.REPAIR_LINE)
                hit = i
            }
            if (m.wire[i].toInt() != 0 && !m.out(i, Broken.WIRE) && rng.nextInt(100) < chance) {
                fail(i, Broken.WIRE, Balance.MEND_WIRE, Balance.REPAIR_WIRE)
                hit = i
            }
            if (m.terrain[i] == Terrain.TREES && rng.nextInt(100) < chance) {
                m.terrain[i] = Terrain.GRASS
                townChanges += i
            }
            if (m.streetTrees[i].toInt() != 0 && rng.nextInt(100) < chance) {
                m.streetTrees[i] = 0
                townChanges += i
            }
        }
        events += CityEvent(EventKind.Gale, if (hit >= 0) hit % m.width else -1, if (hit >= 0) hit / m.width else -1, null)
    }

    /** Heavy works may blow up or spill, the more so worn and crowded; a nuclear station, rarely, worst of all. */
    private fun accidents() {
        if (disasterLevel == 0) return
        for (b in buildings.values.sortedBy { it.id }.toList()) {
            if (b.underway > 0 || buildings[b.id] == null) continue
            val t = b.type
            if (t == BuildingType.NUCLEAR_PLANT) {
                val wear = Ageing.wear(monthNow - b.built, t.life)
                if (disaster(Balance.NUCLEAR_PPM + Balance.NUCLEAR_WEAR_PPM * wear / 100 * wear / 100)) nuclearAccident(b)
                continue
            }
            val heavy = t == BuildingType.MILL || t == BuildingType.WAREHOUSE || t == BuildingType.FACTORY || t == BuildingType.WORKS ||
                t == BuildingType.MINE || t == BuildingType.COLLIERY ||
                t == BuildingType.COAL_PLANT || t == BuildingType.OIL_PLANT || t == BuildingType.GAS_PLANT
            if (!heavy) continue
            val wear = Ageing.wear(monthNow - b.built, if (t.life > 0) t.life else 40)
            var crowd = 0
            forRect(b.x - 2, b.y - 2, b.x + t.width + 1, b.y + t.height + 1) { j -> if (buildings[map.building[j]]?.type?.zone == Zone.INDUSTRIAL) crowd++ }
            if (!disaster(Balance.ACCIDENT_PPM * (100 + wear) / 100 * (4 + crowd / 4) / 4)) continue
            events += CityEvent(EventKind.IndustrialAccident, b.x, b.y, t)
            if (rng.nextInt(2) == 0) {
                // An explosion: the fire spreads, and works are lost, a power station badly damaged.
                for (k in 0 until 4) buildings[neighbour(b, k)]?.let { if (it.type.zone != Zone.NONE && it.burning == 0) ignite(it) }
                if (t.zone == Zone.NONE) damage(b, Balance.MEND_EXPLOSION)
                else {
                    forRect(b.x, b.y, b.x + t.width - 1, b.y + t.height - 1) { map.brownfield[it] = 1; townChanges += it }
                    removeBuilding(b)
                    networksChanged()
                }
            } else {
                // A spill: the land round about fouled, and the water if it reaches it.
                forRect(b.x - 1, b.y - 1, b.x + t.width, b.y + t.height) { j ->
                    if (map.terrain[j] == Terrain.WATER) map.foul[j] = min(255, (map.foul[j].toInt() and 0xff) + Balance.SPILL_FOUL).toByte()
                    else if (map.building[j] == 0 || map.building[j] == b.id) map.brownfield[j] = 1
                    townChanges += j
                }
                disasterBill += Prices.CLEAN_UP * t.width * t.height * 4
            }
        }
    }

    /** A building out of use for [days], or longer on less emergency funding, while it's put right. */
    private fun damage(b: Building, days: Int) {
        b.outage = max(b.outage, max(1, days * 100 / reliefFunding))
        outages += b.id
        disasterBill += Prices.of(b.type) / 5 * reliefFunding / 100
        networksDirty = true
    }

    /** The worst accident of all: the station's lost, and everything round it cleared and fouled for years. */
    internal fun nuclearAccident(b: Building) {
        events += CityEvent(EventKind.NuclearAccident, b.x, b.y, b.type)
        val r = Balance.NUCLEAR_REACH
        val cx = b.x + b.type.width / 2
        val cy = b.y + b.type.height / 2
        val gone = HashSet<Int>()
        forRect(cx - r, cy - r, cx + r, cy + r) { j ->
            if (kotlin.math.abs(j % map.width - cx) + kotlin.math.abs(j / map.width - cy) > r) return@forRect
            buildings[map.building[j]]?.let { if (gone.add(it.id)) removeBuilding(it) }
            if (map.terrain[j] != Terrain.WATER) map.brownfield[j] = 1
            townChanges += j
        }
        networksChanged()
        disasterBill += Balance.NUCLEAR_BILL
    }

    /**
     * Now and then, on a map that has them, an earthquake: buildings near it
     * damaged, old unreinforced brick worst and those put up under building
     * codes least; old pipes burst, roads crack, and fires break out.
     */
    private fun earthquake() {
        if (!quakes || !disaster(Balance.QUAKE_PPM)) return
        val m = map
        var at = rng.nextInt(m.size)
        repeat(50) { if (m.terrain[at] == Terrain.WATER) at = rng.nextInt(m.size) }
        quake(at % m.width, at / m.width)
    }

    /** An earthquake centred on ([cx], [cy]). */
    internal fun quake(cx: Int, cy: Int) {
        val m = map
        events += CityEvent(EventKind.Earthquake, cx, cy, null)
        val r = Balance.QUAKE_REACH
        for (b in buildings.values.sortedBy { it.id }.toList()) {
            val d = kotlin.math.abs(b.x - cx) + kotlin.math.abs(b.y - cy)
            if (d > r || buildings[b.id] == null) continue
            val strength = 100 - d * 6
            val old = 1900 + b.built / 12 < Balance.BUILDING_CODES
            val weakness = when {
                b.underway > 0 -> 50
                old && b.type.heritage -> 60
                b.type.stage == 1 -> 25
                old -> 35
                else -> 12
            }
            if (rng.nextInt(100) >= strength * weakness / 100) continue
            when {
                b.type.zone == Zone.NONE -> damage(b, Balance.MEND_QUAKE)
                rng.nextInt(10) == 0 -> ignite(b)
                else -> shrink(b)
            }
            disasterBill += Balance.QUAKE_BILL
        }
        forRect(cx - r, cy - r, cx + r, cy + r) { i ->
            val d = kotlin.math.abs(i % m.width - cx) + kotlin.math.abs(i / m.width - cy)
            if (d > r || m.broken[i].toInt() != 0) return@forRect
            val strength = 100 - d * 6
            Material.of(Pipe.WATER, m.waterPipe[i])?.let {
                if (rng.nextInt(1000) < strength * (10 + Ageing.wear(monthNow - m.waterLaid[i], it.life) / 2) / 100) {
                    fail(i, Broken.WATER, Balance.MEND_MAIN, Balance.REPAIR_MAIN)
                    return@forRect
                }
            }
            Material.of(Pipe.SEWER, m.sewerPipe[i])?.let {
                if (rng.nextInt(1000) < strength * (10 + Ageing.wear(monthNow - m.sewerLaid[i], it.life) / 2) / 100) {
                    fail(i, Broken.SEWER, Balance.MEND_SEWER, Balance.REPAIR_SEWER)
                    return@forRect
                }
            }
            if (m.road[i] != Road.NONE && rng.nextInt(1000) < strength * 4) fail(i, Broken.ROAD, Balance.MEND_ROAD, Balance.REPAIR_ROAD)
        }
    }

    /**
     * An epidemic: the 1918 flu in its year, and otherwise now and then, more
     * likely the more crowded the town and the fewer the doctors. It goes
     * round for a few months.
     */
    private fun epidemic() {
        if (epidemicMonths > 0) {
            epidemicMonths--
            if (epidemicMonths == 0) events += CityEvent(EventKind.EpidemicOver, -1, -1, null)
            return
        }
        if (disasterLevel == 0 || stats.population < 300) return
        if (year == 1918 && month >= 9 && !hadFlu) {
            hadFlu = true
            epidemicMonths = 4
            epidemicStrength = 50
            events += CityEvent(EventKind.Epidemic, -1, -1, null)
            return
        }
        val crowded = homes.filter { it.type == BuildingType.TENEMENT || it.type == BuildingType.APARTMENTS || it.type == BuildingType.APARTMENT_COURT }
            .sumOf { it.people?.size ?: 0 } * 100 / max(1, stats.population)
        val cared = min(100, stats.cared * 100 / max(1, stats.population))
        if (!disaster(Balance.EPIDEMIC_PPM * (100 + crowded * 3) / 100 * (100 - cared) / 100)) return
        startEpidemic(3 + rng.nextInt(3), 25 + rng.nextInt(21))
    }

    /** An epidemic for [months], striking a home in a hundred [strength] times a month at worst. */
    internal fun startEpidemic(months: Int, strength: Int) {
        epidemicMonths = months
        epidemicStrength = strength
        events += CityEvent(EventKind.Epidemic, -1, -1, null)
    }

    // ---- eras ----------------------------------------------------------------------

    /** What the town needs for [e], against what it has as of the last census. */
    fun goals(e: Era): List<Goal> {
        val s = stats
        return when (e) {
            Era.TOWNSHIP -> emptyList()
            Era.STREETCAR -> listOf(
                Goal(GoalKind.People, s.population, Balance.STREETCAR_PEOPLE),
                Goal(GoalKind.MainsOrStation, if (s.onMains > 0 || stationsBuilt > 0) 1 else 0, 1),
            )
            Era.MOTOR -> listOf(
                Goal(GoalKind.People, s.population, Balance.MOTOR_PEOPLE),
                Goal(GoalKind.OnMains, s.onMains, Balance.MOTOR_SERVED),
                Goal(GoalKind.OnSewer, s.onSewer, Balance.MOTOR_SERVED),
                Goal(GoalKind.Powered, s.powered, Balance.MOTOR_POWERED),
            )
            Era.RENEWAL -> listOf(
                Goal(GoalKind.People, s.population, Balance.RENEWAL_PEOPLE),
                Goal(GoalKind.Downtown, s.downtown, 1),
                Goal(GoalKind.HighSchool, s.highSchools, 1),
                // The motor age's jams kept in hand.
                Goal(GoalKind.Flow, s.flow, Balance.RENEWAL_FLOW),
            )
            Era.INFILL -> listOf(Goal(GoalKind.LandBuilt, s.landBuilt, Balance.INFILL_LAND))
            // A town that's kept up what it inherited, and moves its people well.
            Era.FUTURE -> listOf(
                Goal(GoalKind.KeptUp, s.keptUp, Balance.FUTURE_KEPT_UP),
                Goal(GoalKind.GreenTrips, s.greenTrips, Balance.FUTURE_GREEN_TRIPS),
                Goal(GoalKind.LowCarbon, lowCarbon(), 100),
            )
        }
    }

    /** How near the town's carbon a person last month is to the Future era's line, in percent. */
    private fun lowCarbon(): Int {
        val s = stats
        if (s.population == 0) return 100
        val perPerson = s.carbon * 1000 / s.population
        return if (perPerson <= Balance.FUTURE_CARBON) 100 else (Balance.FUTURE_CARBON * 100 / perPerson).toInt()
    }

    /** Percent of roads, pipes and track within their expected life, 100 if there are none. */
    private fun keptUp(): Int {
        val m = map
        val now = monthNow
        var all = 0
        var fine = 0
        fun count(laid: Int, life: Int) {
            all++
            if (Ageing.wear(now - laid, life) < 100) fine++
        }
        for (i in 0 until m.size) {
            RoadType.of(m.road[i])?.let { count(m.roadLaid[i].toInt(), it.life) }
            Material.of(Pipe.WATER, m.waterPipe[i])?.let { count(m.waterLaid[i].toInt(), it.life) }
            Material.of(Pipe.SEWER, m.sewerPipe[i])?.let { count(m.sewerLaid[i].toInt(), it.life) }
            Material.of(Pipe.STORM, m.stormPipe[i])?.let { count(m.stormLaid[i].toInt(), it.life) }
            if (m.rail[i] != Rail.NONE) count(m.railLaid[i].toInt(), Balance.TRACK_LIFE)
            if (m.tram[i].toInt() != 0) count(m.tramLaid[i].toInt(), Balance.TRAM_TRACK_LIFE)
            if (m.wire[i].toInt() != 0) count(m.wireLaid[i].toInt(), Balance.WIRE_LIFE)
            if (m.subway[i].toInt() != 0) count(m.subwayLaid[i].toInt(), Balance.TUNNEL_LIFE)
        }
        return if (all == 0) 100 else fine * 100 / all
    }

    /** Percent of last month's commutes made other than by car. */
    private fun greenTrips(): Int {
        val all = traffic.lastModes.sum()
        return if (all == 0) 0 else (all - traffic.lastModes[Mode.CAR.ordinal]) * 100 / all
    }

    /** Railway stations standing, for the Streetcar city's milestone. */
    private val stationsBuilt get() = buildings.values.count { it.type.station }

    /** Moves the town into its next era once the date has come and the milestone's met. */
    private fun newEra() {
        val next = era.next ?: return
        if (year < next.year || goals(next).any { !it.met }) return
        era = next
        events += CityEvent(EventKind.EraArrived, -1, -1, null, next)
    }

    /** Whether the town can build [road] yet: the year has to have come, and the town to be in its era. */
    fun allows(road: RoadType): Boolean = everything || (year >= road.year && era >= Era.of(road.year))

    /** Whether the town can put down [type] yet, the same way. */
    fun allows(type: BuildingType): Boolean = everything || (year >= type.year && era >= Era.of(type.year))

    /** Whether lots can be zoned at [density] yet: towers come with the motor age. */
    fun allowsDensity(density: Byte): Boolean = everything || density != Density.TOWER || era >= Era.MOTOR

    /** Lets anything be built whatever the year, for trying things out. Not saved. */
    var everything = false

    /**
     * What a home makes of something it has or hasn't, as the years go by: a
     * draw at first, fading to nothing by [expectedBy]; and going without it
     * counts against a home more and more from [fadesFrom] to [expectedBy].
     */
    private fun amenity(have: Boolean, bonus: Int, fadesFrom: Int, expectedBy: Int, penalty: Int): Int {
        val t = ((year - fadesFrom) * 100 / (expectedBy - fadesFrom)).coerceIn(0, 100)
        return if (have) bonus * (100 - t) / 100 else -penalty * t / 100
    }

    // ---- railway ----------------------------------------------------------------

    private val railway = RailNetwork(map)
    private var railChanged = true

    /** A passenger station on a line to the edge, and a freight yard on one. */
    private var railPassengers = false
    private var railFreight = false

    /** Freight terminals on lines to the edge, and the tiles near enough one for works to feel it. */
    private var linkedTerminals: List<Building> = emptyList()
    private val nearTerminal = BooleanArray(map.size)

    /** Whether a terminal and a port are near each other, so containers go straight from ship to train. */
    private fun portByRail(): Boolean = linkedTerminals.any { t ->
        linkedPorts.any { p -> abs(p.x - t.x) + abs(p.y - t.y) <= Balance.PORT_RAIL_REACH + t.type.width + p.type.width }
    }

    /** The lines trains ran last month, for drawing them: the track, and what they carried. */
    var trainRoutes: List<TrainRoute> = emptyList()
        private set

    private fun updateRail() {
        railway.update(buildings.values.filter { it.type.railway })
        railFlags()
    }

    /** Which stations take passengers and which yards send freight out, as far as they have what they need. Monthly, and when the railway changes. */
    private fun railFlags() {
        val stops = railway.buildings
        val passengers = BooleanArray(stops.size) { stops[it].type.station && working(stops[it]) }
        val freightOut = BooleanArray(stops.size) { stops[it].type.yard && railway.linked(it) && working(stops[it]) }
        railPassengers = stops.indices.any { passengers[it] && railway.linked(it) }
        railFreight = freightOut.any { it }
        // The terminals on lines out, and the works near enough to one to feel it.
        linkedTerminals = stops.indices.filter { stops[it].type.terminal && freightOut[it] }.map { stops[it] }
        nearTerminal.fill(false)
        val reach = Balance.TERMINAL_REACH
        for (b in linkedTerminals) forRect(max(0, b.x - reach), max(0, b.y - reach), min(map.width - 1, b.x + b.type.width - 1 + reach), min(map.height - 1, b.y + b.type.height - 1 + reach)) { nearTerminal[it] = true }
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
                for (i in path) busy[i % map.size] += trips
                routes += trainRoute(path.reversedArray(), !freight, trips)
            }
            if (map.rail[start] == Rail.TRACK && linkedStations.any { railway.stops[it] == start }) {
                val end = toEdge()
                if (end >= 0) routes += trainRoute(railway.pathBack(steps, end).reversedArray(), true, 0)
            }
        }
        val train = Balance.TRAIN_LOAD * 30
        for (i in 0 until map.size) map.railBusy[i] = min(255, busy[i] * 128 / train).toByte()
        trainRoutes = routes
        markContainerTrains()
    }

    /** Freight trains from a terminal carry containers. */
    private fun markContainerTrains() {
        val terminals = railway.stops.indices.filter { railway.buildings[it].type.terminal }.map { railway.stops[it] }.toSet()
        for (t in trainRoutes) t.containers = !t.passengers && t.tiles.isNotEmpty() && t.tiles[0] in terminals
    }

    /** A train's way along [path], tunnels and all: the tiles, with those under ground hidden but for the portals. */
    private fun trainRoute(path: IntArray, passengers: Boolean, load: Int): TrainRoute {
        val n = map.size
        val tiles = IntArray(path.size) { path[it] % n }
        val hidden = BooleanArray(path.size) { path[it] >= n && map.portal[path[it] - n].toInt() == 0 }
        return TrainRoute(tiles, passengers, load, hidden)
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

    // ---- goods ---------------------------------------------------------------------

    /** What [b] makes a month, in hundredths of a load, or null if it makes nothing to carry. */
    private fun makes(b: Building): Pair<Good, Int>? {
        val t = b.type
        b.worksKind?.let { return it.output to t.capacity * it.rate }
        val (g, rate) = Land.output(t) ?: return null
        // Farms on poor soil grow less.
        val soil = if (t == BuildingType.FARM && map.resource[map.index(b.x, b.y)] != Resource.FERTILE) Balance.POOR_SOIL else 100
        return g to t.capacity * rate * soil / 100
    }

    /** What [b] needs a month, in hundredths of a load: a works its makings, a coal station its coal. */
    private fun needs(b: Building): List<Pair<Good, Int>> {
        b.worksKind?.let { k -> return k.inputs.map { (g, rate) -> g to b.type.capacity * rate } }
        burns(b)?.let { (g, per) -> return listOf(g to (stationOutput(b).toLong() * per / 10_000).toInt()) }
        if (b.type.zone == Zone.COMMERCIAL && !b.type.office) {
            val c = b.type.capacity
            val needs = arrayListOf(Good.FOOD to c * Balance.SHOP_FOOD, Good.GOODS to c * Balance.SHOP_GOODS)
            // Fuel for the cars, as there come to be more of them.
            val cars = Cars.share(year, Wealth.MIDDLE)
            if (cars > 0) needs += Good.FUEL to c * Balance.SHOP_FUEL * cars / 100
            return needs
        }
        return emptyList()
    }

    /** Whether [b] couldn't get in most of what it needed last month: nothing in town, and no way in from outside. */
    fun shortOfStock(b: Building): Boolean {
        val node = accessOf(b)
        return node >= 0 && traffic.lastUnmet.any { it[node] > 0 } && traffic.freightStuck[node]
    }

    /** Tells the traffic which stops ride free and which streets keep trucks off, from the districts. */
    private fun districtTraffic() {
        val free = BooleanArray(map.size)
        val banned = BooleanArray(map.size)
        if (districts.any { it.freeFares || it.noTrucks }) for (i in 0 until map.size) {
            val d = districtAt(i) ?: continue
            if (d.freeFares) free[i] = true
            if (d.noTrucks) banned[i] = true
        }
        traffic.freeStop = free
        traffic.noTrucks = banned
    }

    /** Whether the town can fit scrubbers to its stations yet. */
    fun allowsScrubbers(): Boolean = everything || year >= Balance.SCRUBBER_YEAR

    /** How dense lot [i] may build: its zone's density, no higher than its district allows. */
    private fun heightAt(i: Int): Byte {
        val limit = districtAt(i)?.height ?: Density.NONE
        val zoned = map.density[i]
        return if (limit == Density.NONE || Density.rank(limit) >= Density.rank(zoned)) zoned else limit
    }

    /** What a station burns that the town can make, and how many loads a month for each megawatt. */
    private fun burns(b: Building): Pair<Good, Int>? = when (b.type) {
        BuildingType.COAL_PLANT -> Good.COAL to Balance.COAL_PER_MW
        BuildingType.OIL_PLANT -> Good.FUEL to Balance.FUEL_PER_MW
        else -> null
    }

    /** What a station's fuel costs a month: coal and fuel oil, the town's own cheaper than what's brought in; the others by output. */
    internal fun fuelCost(b: Building): Double {
        val (g, per) = burns(b) ?: return Generation.fuel(b.type) * stationOutput(b) / 1_000_000.0
        val loads = stationOutput(b) / 1_000_000.0 * per
        // Coal and oil come cheaper by ship.
        val markup = if (seaTier > 0) Balance.PORT_IMPORT_MARKUP else Balance.IMPORT_MARKUP
        return loads * g.price * (b.local + (100 - b.local) * markup) / 100.0
    }

    /**
     * How much more a farmland lot appeals for building [t]: the share of
     * what the town needed last month of what it makes, and of what's made
     * from that, that had to be brought in.
     */
    private fun shortOf(t: BuildingType): Int {
        val (g, _) = Land.output(t) ?: return 0
        val imported = stats.goodsImported
        var short = imported[g.ordinal]
        for (k in WorksKind.entries) if (k.inputs.any { it.first == g }) short += imported[k.output.ordinal]
        if (short == 0) return 0
        return Balance.SHORT_APPEAL * short / (short + stats.goodsMade[g.ordinal])
    }

    /** How much the town wants each kind of works this month, in jobs: what it brings in of the works' goods, and the makings it sends away. */
    private val kindPull = DoubleArray(WorksKind.entries.size)

    /**
     * What a new works of [jobs] makes: by chance, weighted by how much the
     * town brings in of each kind's goods or sends away of its makings; with
     * no call for any, finished goods.
     */
    private fun chooseKind(jobs: Int): Int {
        val open = WorksKind.entries.filter { it.year <= year }
        val total = open.sumOf { max(0.0, kindPull[it.ordinal]) }
        var pick = WorksKind.FACTORY
        if (total > 0) {
            var r = rng.nextInt(1_000_000) / 1_000_000.0 * total
            for (k in open) {
                r -= max(0.0, kindPull[k.ordinal])
                if (r < 0) {
                    pick = k
                    break
                }
            }
        }
        kindPull[pick.ordinal] -= jobs.toDouble()
        return pick.ordinal
    }

    /**
     * Last month's goods, now the trips are in: what each good came to, and
     * for each works, farm, mine and coal station, how much was its town's.
     * Buildings sharing a road tile share its deliveries.
     */
    private fun settleGoods() {
        val s = stats
        val t = traffic
        for (g in 0 until Good.COUNT) {
            s.goodsSold[g] = t.lastSold[g].sum()
            s.goodsExported[g] = t.lastExported[g].sum()
            s.goodsMade[g] = s.goodsSold[g] + s.goodsExported[g]
            s.goodsImported[g] = t.lastUnmet[g].sum()
        }
        for (b in buildings.values) {
            val node = accessOf(b)
            if (node < 0 || b.underway > 0) {
                b.local = 0
                continue
            }
            val needed = needs(b)
            b.local = if (needed.isNotEmpty()) {
                // Each of its needs by weight, at the share its road tile got of what it wanted.
                var want = 0L
                var got = 0L
                for ((g, h) in needed) {
                    val came = t.lastDelivered[g.ordinal][node]
                    val all = came + t.lastUnmet[g.ordinal][node]
                    want += h
                    if (all > 0) got += h.toLong() * came / all
                }
                if (want == 0L) 0 else (got * 100 / want).toInt()
            } else {
                val g = makes(b)?.first ?: continue
                val sold = t.lastSold[g.ordinal][node]
                val all = sold + t.lastExported[g.ordinal][node]
                if (all == 0) 0 else sold * 100 / all
            }
        }
        for (k in WorksKind.entries) {
            var pull = s.goodsImported[k.output.ordinal] * 100.0 / k.rate
            for ((g, rate) in k.inputs) pull += s.goodsExported[g.ordinal] * 100.0 / rate / k.inputs.size
            kindPull[k.ordinal] = pull
        }
    }

    /**
     * Each crossing's control: the player's choice, or the town's. The town
     * puts stop signs at a crossing as busy as [Balance.AUTO_STOP] percent of
     * its road's capacity, and from the 1920s lights at [Balance.AUTO_LIGHTS].
     */
    private fun updateJunctions() {
        val m = map
        for (i in 0 until m.size) {
            val was = m.control[i]
            val now = when {
                !Junction.at(m, i) -> Junction.NONE
                m.junction[i] != Junction.AUTO -> m.junction[i]
                // A highway never stops for a ramp joining it.
                RoadType.of(m.road[i])?.limited == true -> Junction.FREE
                else -> {
                    val road = RoadType.of(m.road[i])
                    val busy = if (road == null) 0 else traffic.lastVolume[i] * 100 / road.capacity
                    when {
                        busy >= Balance.AUTO_LIGHTS && year >= Junction.year(Junction.LIGHTS) -> Junction.LIGHTS
                        busy >= Balance.AUTO_STOP -> Junction.STOP
                        // Lights once up stay up while it's still busy.
                        was == Junction.LIGHTS && busy >= Balance.AUTO_STOP -> Junction.LIGHTS
                        else -> Junction.FREE
                    }
                }
            }
            if (now != was) {
                m.control[i] = now
                townChanges += i
            }
        }
    }

    /** What the town's traffic flow does for shops and offices: more where it moves freely, less where it's jammed. */
    private fun flowAppeal(): Int = ((stats.flow - Balance.FLOW_PAR) * Balance.FLOW_APPEAL / (100 - Balance.FLOW_PAR)).coerceIn(-Balance.FLOW_APPEAL, Balance.FLOW_APPEAL)

    /** Last month's vehicles through road tile [i], by the road tiles they crossed. */
    fun tripsThrough(i: Int): IntArray = traffic.tripsThrough(i)

    /** Seconds by road from road tile [i] to every other, as the traffic is now; -1 out of reach. */
    fun travelTimes(i: Int): IntArray = traffic.travelTimes(i)

    /** Seconds a car waits at the crossing on tile [i] now, 0 if it isn't one. */
    fun junctionWait(i: Int): Int = RoadType.of(map.road[i])?.let { traffic.junctionWait(i, it) } ?: 0

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
        // Who has a car, in hundredths of a person, rounded once each tile's added up.
        val carWorkers = IntArray(n)
        val carShoppers = IntArray(n)
        var wfh = 0
        for (b in buildings.values) {
            if (b.underway > 0) continue
            val node = accessOf(b)
            if (node < 0) continue
            val c = b.type.capacity
            when (b.type.zone) {
                Zone.RESIDENTIAL -> {
                    val wealth = b.people?.wealth ?: Wealth.MIDDLE
                    // Where parking's limited, fewer drive.
                    val share = Cars.share(year, wealth) * (if (districtAt(node)?.parking == true || districtAt(map.index(b.x, b.y))?.parking == true) 100 - Balance.PARKING_CUT else 100) / 100
                    // From 2000, some educated workers with good internet work from home.
                    val stayHome = workFromHome(b)
                    wfh += stayHome
                    val workers = (b.people?.workers() ?: 0) - stayHome
                    val shoppers = (b.people?.size ?: 0) * Demography.SPENDING_BY_WEALTH[wealth] / 100 / Balance.RESIDENTS_PER_SHOPPER
                    workersAt[node] += workers
                    shoppersAt[node] += shoppers
                    carWorkers[node] += workers * share
                    carShoppers[node] += shoppers * share
                }
                Zone.COMMERCIAL -> {
                    jobsAt[node] += c
                    shopsAt[node] += c * Balance.SHOPPERS_PER_SHOP_JOB
                }
                Zone.OFFICE -> jobsAt[node] += c
                Zone.INDUSTRIAL -> {
                    jobsAt[node] += c
                    freightAt[node] += c * Balance.FREIGHT_PER_TEN_JOBS / 10
                }
                else -> jobsAt[node] += c
            }
        }
        stats.workingFromHome = wfh
        // Visitors go shopping too: from their hotels, and day trippers from the stations and ports they came in at.
        val stay = 100 - Balance.STAY_SHARE
        val stations = railway.stops.indices.filter { railway.buildings[it].type.station && railway.linked(it) }.map { railway.buildings[it] }
        for ((at, by) in listOf(stations to Tourism.RAIL, linkedPorts.filter { Balance.SEA_VISITORS[it.type.portTier] > 0 } to Tourism.SEA, airports to Tourism.AIR)) {
            if (at.isEmpty()) continue
            val each = stats.visitorsBy[by] * stay / 100 / at.size / Balance.RESIDENTS_PER_SHOPPER
            for (b in at) accessOf(b).let { if (it >= 0) shoppersAt[it] += each }
        }
        for (b in buildings.values) if (b.type == BuildingType.HOTEL && b.served > 0) {
            accessOf(b).let { if (it >= 0) shoppersAt[it] += b.served / Balance.RESIDENTS_PER_SHOPPER }
        }
        // Goods, in hundredths of a load until each tile's are added up.
        val goodsAt = Array(Good.COUNT) { IntArray(n) }
        val wantedAt = Array(Good.COUNT) { IntArray(n) }
        for (b in buildings.values.sortedBy { it.id }) {
            if (b.underway > 0 || b.outage > 0) continue
            val node = accessOf(b)
            if (node < 0) continue
            makes(b)?.let { (g, h) -> goodsAt[g.ordinal][node] += h }
            for ((g, h) in needs(b)) wantedAt[g.ordinal][node] += h
        }
        // What's made to the nearest load; what's wanted up to the next, so any need asks for something.
        for (g in 0 until Good.COUNT) for (i in 0 until n) {
            goodsAt[g][i] = (goodsAt[g][i] + 50) / 100
            wantedAt[g][i] = (wantedAt[g][i] + 99) / 100
        }
        // What was brought in comes by road too, from the edge or a freight yard.
        for (g in 0 until Good.COUNT) {
            val unmet = traffic.lastUnmet[g]
            for (i in 0 until n) if (unmet[i] > 0) freightAt[i] += unmet[i]
        }
        traffic.newMonth(
            workersAt, shoppersAt, freightAt, jobsAt, shopsAt, year * 12 + month,
            IntArray(n) { (carWorkers[it] + 50) / 100 }, IntArray(n) { (carShoppers[it] + 50) / 100 },
            goodsAt, wantedAt,
        )
        settleGoods()
        updateJunctions()
        districtTraffic()
        // The waits follow last month's riders.
        updateTransit()
        traffic.lastModes.copyInto(stats.byMode)
        stats.flow = traffic.lastFlow
        stats.greenTrips = greenTrips()
        updateTrains()
        updateShips()

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

    /**
     * Police and fire cover. On foot and by horse it reaches so far round each
     * station; once they have motors, the crews drive, and cover is how long
     * it takes them to get there by road, the traffic and the crossings as
     * they are. Either way it's as much as the funding and the staff allow.
     */
    private fun updateServices() {
        fun of(t: BuildingType) = buildings.values.filter { it.type == t }.sortedBy { it.id }
        val motor = year >= Balance.MOTOR_FIRE_YEAR
        cover(buildings.values.filter { it.type.patrols }.sortedBy { it.id }, year >= Balance.PATROL_CAR_YEAR, Balance.POLICE_REACH, Balance.POLICE_RESPONSE_FULL, Balance.POLICE_RESPONSE_MOST, policeFunding, 100, map.policeCover)
        // Fire halls, and volunteer halls at half the strength.
        cover(of(BuildingType.FIRE_STATION), motor, Balance.FIRE_REACH, Balance.FIRE_RESPONSE_FULL, Balance.FIRE_RESPONSE_MOST, fireFunding, 100, map.fireCover)
        val volunteers = ByteArray(map.size)
        cover(of(BuildingType.VOLUNTEER_HALL), motor, Balance.FIRE_REACH, Balance.FIRE_RESPONSE_FULL, Balance.FIRE_RESPONSE_MOST, fireFunding, Balance.VOLUNTEER_STRENGTH, volunteers)
        for (i in 0 until map.size) if ((volunteers[i].toInt() and 0xff) > (map.fireCover[i].toInt() and 0xff)) map.fireCover[i] = volunteers[i]
        cover(of(BuildingType.LADDER_COMPANY), motor, Balance.FIRE_REACH, Balance.FIRE_RESPONSE_FULL, Balance.FIRE_RESPONSE_MOST, fireFunding, 100, map.ladderCover)
        cover(of(BuildingType.AMBULANCE_STATION), true, Balance.FIRE_REACH, Balance.FIRE_RESPONSE_FULL, Balance.FIRE_RESPONSE_MOST, healthFunding, 100, map.ambulanceCover)
    }

    /**
     * Cover from [stations] into [out]: by road once they [drive], within
     * [full] to [most] seconds, else round each within [reach] tiles; each
     * as strong as its funding, staff and age allow, times [share] percent.
     */
    private fun cover(stations: List<Building>, drive: Boolean, reach: Int, full: Int, most: Int, funding: Int, share: Int, out: ByteArray) {
        out.fill(0)
        val each = ByteArray(map.size)
        for (b in stations) {
            val strong = strengthOf(b, funding) * share / 100
            if (strong <= 0) continue
            if (drive) responseCover(listOf(b), full, most, strong, each)
            else Effects.cover(map, listOf(b), reach * strong / 100, each)
            for (i in 0 until map.size) if ((each[i].toInt() and 0xff) > (out[i].toInt() and 0xff)) out[i] = each[i]
        }
    }

    /**
     * The telephone across the town, each month. Each exchange serves what's
     * within reach, nearest first, as far as its lines go round, and reaches
     * half as far with no trunk line out of town. Broadband comes from an
     * exchange with fibre out, faster still beside a fibre line, and masts
     * give a phone to anyone near one that's joined up.
     */
    private fun updateComms() {
        val m = map
        val w = m.width
        m.comms.fill(0)
        val exchanges = buildings.values.filter { it.type == BuildingType.EXCHANGE && it.underway == 0 && it.outage == 0 }.sortedBy { it.id }
        val towers = buildings.values.filter { it.type == BuildingType.CELL_TOWER && it.underway == 0 && it.outage == 0 }.sortedBy { it.id }
        if (exchanges.isEmpty() && towers.isEmpty()) {
            stats.withPhone = 0
            stats.withBroadband = 0
            return
        }
        val out = linkedOut(fibreOnly = false)
        val fibreOut = if (year >= Balance.BROADBAND_YEAR) linkedOut(fibreOnly = true) else null
        fun touches(b: Building, reached: BooleanArray): Boolean {
            for (y in b.y - 1..b.y + b.type.height) for (x in b.x - 1..b.x + b.type.width) {
                if (!m.inside(x, y)) continue
                // The footprint and the tiles along its four sides, leaving out the corners.
                val corner = (x == b.x - 1 || x == b.x + b.type.width) && (y == b.y - 1 || y == b.y + b.type.height)
                if (!corner && reached[m.index(x, y)]) return true
            }
            return false
        }
        val lines = when {
            year >= Balance.DIGITAL_YEAR -> Balance.DIGITAL_LINES
            year >= Balance.AUTOMATIC_YEAR -> Balance.AUTOMATIC_LINES
            else -> Balance.EXCHANGE_LINES
        }
        class Exchange(val b: Building, val reach: Int, val fibre: Boolean, var left: Int)
        val serving = exchanges.map { b ->
            val linked = touches(b, out)
            val room = lines * strengthOf(b, 100) / 100
            b.room = room
            b.served = 0
            Exchange(b, if (linked) Balance.PHONE_REACH else Balance.PHONE_REACH / 2, linked && fibreOut != null && touches(b, fibreOut), room)
        }
        // The land within each exchange's and mast's reach, for the map view.
        fun cover(cx: Int, cy: Int, reach: Int, level: Int) {
            for (y in maxOf(0, cy - reach)..minOf(m.height - 1, cy + reach)) for (x in maxOf(0, cx - reach)..minOf(w - 1, cx + reach)) {
                if (abs(x - cx) + abs(y - cy) > reach) continue
                val i = y * w + x
                if (m.comms[i] < level) m.comms[i] = level.toByte()
            }
        }
        for (e in serving) {
            val cx = e.b.x + e.b.type.width / 2
            val cy = e.b.y
            cover(cx, cy, e.reach, Phone.SERVICE_PHONE)
            if (e.fibre) cover(cx, cy, Balance.DSL_REACH, Phone.SERVICE_BROADBAND)
        }
        val linkedTowers = if (year >= 1985 || everything) towers.filter { touches(it, out) } else emptyList()
        for (t in linkedTowers) cover(t.x, t.y, Balance.TOWER_REACH, Phone.SERVICE_PHONE)
        // Fast service beside fibre that runs out of town.
        val fast = BooleanArray(m.size)
        if (fibreOut != null && year >= Balance.FAST_YEAR) {
            for (i in 0 until m.size) if (fibreOut[i] && m.phone[i] == Phone.FIBRE) {
                val x = i % w
                val y = i / w
                for (yy in maxOf(0, y - Balance.FAST_REACH)..minOf(m.height - 1, y + Balance.FAST_REACH)) for (xx in maxOf(0, x - Balance.FAST_REACH)..minOf(w - 1, x + Balance.FAST_REACH)) {
                    if (abs(xx - x) + abs(yy - y) <= Balance.FAST_REACH) fast[yy * w + xx] = true
                }
            }
        }
        // Who gets a line: every home and business, nearest an exchange first, while its lines last.
        val customers = buildings.values.filter { b ->
            b.underway == 0 && (b.people?.let { !it.empty } ?: (b.type.zone != Zone.NONE))
        }.sortedBy { it.id }
        class Ask(val b: Building, val e: Exchange, val distance: Int)
        val asks = ArrayList<Ask>()
        for (b in customers) for (e in serving) {
            val d = abs(b.x - (e.b.x + e.b.type.width / 2)) + abs(b.y - e.b.y)
            if (d <= e.reach) asks += Ask(b, e, d)
        }
        asks.sortWith(compareBy<Ask> { it.distance }.thenBy { it.b.id })
        val level = HashMap<Int, Int>()
        for (a in asks) {
            if (a.b.id in level) continue
            val need = if (a.b.people != null) 1 else maxOf(1, a.b.type.capacity / Balance.JOBS_PER_LINE)
            if (a.e.left < need) continue
            a.e.left -= need
            a.e.b.served += need
            val i = m.index(a.b.x, a.b.y)
            level[a.b.id] = when {
                fast[i] -> Phone.SERVICE_FAST
                a.e.fibre && a.distance <= Balance.DSL_REACH -> Phone.SERVICE_BROADBAND
                else -> Phone.SERVICE_PHONE
            }
        }
        // A mast gives anyone near it a phone, lines or none.
        for (b in customers) {
            if (b.id in level) continue
            if (linkedTowers.any { abs(it.x - b.x) + abs(it.y - b.y) <= Balance.TOWER_REACH }) level[b.id] = Phone.SERVICE_PHONE
        }
        // A building has what it got, whatever the land round it has.
        var phones = 0
        var broadband = 0
        for (b in customers) {
            val got = level[b.id] ?: 0
            if (got >= Phone.SERVICE_PHONE) phones++
            if (got >= Phone.SERVICE_BROADBAND) broadband++
            for (y in b.y until b.y + b.type.height) for (x in b.x until b.x + b.type.width) if (m.inside(x, y)) m.comms[m.index(x, y)] = got.toByte()
        }
        stats.withPhone = if (customers.isEmpty()) 0 else phones * 100 / customers.size
        stats.withBroadband = if (customers.isEmpty()) 0 else broadband * 100 / customers.size
    }

    /**
     * The tiles a trunk line joins to the edge of the map, through lines in
     * use and the exchanges between them; with [fibreOnly], through fibre.
     */
    private fun linkedOut(fibreOnly: Boolean): BooleanArray {
        val m = map
        val reached = BooleanArray(m.size)
        fun carries(i: Int): Boolean {
            val b = buildings[m.building[i]]
            if (b != null) return b.type == BuildingType.EXCHANGE && b.underway == 0 && b.outage == 0
            val kind = m.phone[i]
            if (kind == Phone.NONE || m.out(i, Broken.PHONE)) return false
            return !fibreOnly || kind == Phone.FIBRE
        }
        val queue = ArrayDeque<Int>()
        for (i in 0 until m.size) {
            val x = i % m.width
            val y = i / m.width
            if ((x == 0 || y == 0 || x == m.width - 1 || y == m.height - 1) && carries(i)) {
                reached[i] = true
                queue.addLast(i)
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
                if (reached[j] || !carries(j)) continue
                reached[j] = true
                queue.addLast(j)
            }
        }
        return reached
    }

    /**
     * The way from each lot off the road to its road. A house on a quiet lot
     * gets a path that winds past its neighbours to the street, of whatever
     * the street's made of; a denser block, a back lane behind its front row
     * that the lots beyond it come out onto. Shops, offices and works take
     * deliveries, so theirs is a lane however dense the block.
     */
    internal fun updatePathways() {
        val m = map
        val w = m.width
        val next = ShortArray(m.size)
        fun bit(dx: Int, dy: Int) = when {
            dy < 0 -> 1
            dx > 0 -> 2
            dy > 0 -> 4
            else -> 8
        }
        // A path or lane can cross yards, but not water, track or a road it isn't meeting.
        fun open(x: Int, y: Int): Boolean {
            if (!m.inside(x, y)) return false
            val i = m.index(x, y)
            return m.terrain[i] != Terrain.WATER && m.rail[i] == Rail.NONE && m.road[i] == Road.NONE
        }
        fun road(x: Int, y: Int) = m.inside(x, y) && m.road[m.index(x, y)] != Road.NONE
        for (b in buildings.values.sortedBy { it.id }) {
            if (b.underway > 0) continue
            val t = b.type
            if (t.zone == Zone.NONE || t.zone == Zone.FARMLAND) continue
            val a = access[m.index(b.x, b.y)]
            if (a < 0) continue
            val ax = a % w
            val ay = a / w
            // A building on more than one lot sets out from its lot nearest the road, and needs no way if one's on it.
            val bx = ax.coerceIn(b.x, b.x + t.width - 1)
            val by = ay.coerceIn(b.y, b.y + t.height - 1)
            val start = m.index(bx, by)
            if (abs(ax - bx) + abs(ay - by) <= 1) continue
            val business = t.zone != Zone.RESIDENTIAL
            val surface = (when (RoadType.of(m.road[a])) {
                RoadType.DIRT -> 0
                RoadType.GRAVEL, RoadType.LANE -> 1
                else -> 2
            } shl 8) or (if (business) WIDE_WAY else 0)
            val dense = business || Density.rank(m.density[start]) >= Density.rank(Density.MEDIUM)
            // The way, a tile and its marks at a time, kept only if it gets there.
            val marks = ArrayList<Pair<Int, Int>>()
            var x = bx
            var y = by
            var reached = false
            while (true) {
                val i = y * w + x
                val rx = ax - x
                val ry = ay - y
                val (sx, sy) = if (abs(ry) >= abs(rx)) 0 to (if (ry > 0) 1 else -1) else (if (rx > 0) 1 else -1) to 0
                if (dense && abs(rx) + abs(ry) == 2) {
                    // A back lane along this row's edge facing the road, out to whichever end meets a road sooner.
                    val lane = (bit(sx, sy) shl 4) or surface
                    val ways = listOf(sy to sx, -sy to -sx).map { (px, py) ->
                        val run = ArrayList<Int>()
                        var cx = x
                        var cy = y
                        var ok = false
                        for (k in 0 until LANE_REACH) {
                            run += cy * w + cx
                            // The lane's corner reaches the road along the cross street, or the lot in front gives way.
                            if (road(cx + px, cy + py)) { ok = true; break }
                            if (!open(cx + px, cy + py) || !open(cx + px + sx, cy + py + sy) && !road(cx + px + sx, cy + py + sy)) break
                            cx += px
                            cy += py
                        }
                        if (ok) run else null
                    }
                    val best = ways.filterNotNull().minByOrNull { it.size }
                    if (best != null) {
                        for (j in best) marks += j to lane
                        reached = true
                        break
                    }
                }
                marks += i to (bit(sx, sy) or surface)
                x += sx
                y += sy
                val j = y * w + x
                if (road(x, y)) { reached = true; break }
                if (!open(x, y)) break
                marks += j to (bit(-sx, -sy) or surface)
                if (abs(ax - x) + abs(ay - y) == 0) { reached = true; break }
            }
            if (!reached) continue
            for ((j, v) in marks) next[j] = (next[j].toInt() or v).toShort()
        }
        for (i in 0 until m.size) if (next[i] != m.pathway[i]) {
            m.pathway[i] = next[i]
            townChanges += i
        }
    }

    /** How many of [b]'s workers work from home today: some of the educated, with broadband, from 2000. */
    private fun workFromHome(b: Building): Int {
        val h = b.people ?: return 0
        if (year < Balance.WFH_YEAR) return 0
        val share = when (map.comms[map.index(b.x, b.y)].toInt()) {
            Phone.SERVICE_FAST -> Balance.WFH_FAST
            Phone.SERVICE_BROADBAND -> Balance.WFH_BROADBAND
            else -> 0
        }
        // Rounded by the home and the month, so a town of homes with one such worker each still sends the right share.
        return (h.workersAt(Education.EDUCATED) * share + (b.id * 37 + monthNow * 11).mod(100)) / 100
    }

    /** The colleges, for offices to be near. */
    private val colleges get() = buildings.values.filter { it.type == BuildingType.COLLEGE && it.underway == 0 }

    /**
     * Percent of last month's arrests that stuck: heard in court and the
     * guilty held their time. Low, and crime grows and rackets feed.
     */
    var justice = 100
        private set

    /** People in the cells and the jails. */
    var prisoners = 0
        private set

    /** The month's crime: rackets first, from what went unpunished, then each kind, then what came of it. */
    private fun updateCrime() {
        if (year >= Balance.RACKETS_YEAR) {
            // Detectives at headquarters work the whole town; a second headquarters adds nothing.
            val hq = buildings.values.filter { it.type == BuildingType.POLICE_HQ }.maxOfOrNull { strengthOf(it, policeFunding) } ?: 0
            Effects.rackets(map, justice, Balance.DETECTIVES * hq / 100)
        } else {
            map.rackets.fill(0)
        }
        Effects.crime(
            map, { i -> buildings[map.building[i]]?.people?.size ?: 0 },
            { i -> buildings[map.building[i]]?.type?.let { t -> if (t.zone == Zone.COMMERCIAL) t.capacity / (t.width * t.height) else 0 } ?: 0 },
            { i -> map.building[i] != 0 }, stats.unemployment, justice,
        )
        courts()
    }

    /**
     * Arrests, the courts and the cells: the offences where people are, the
     * share of them the police catch, the cases the stations and courthouses
     * can hear, and the room in the cells and jails for those found guilty.
     */
    private fun courts() {
        val m = map
        var offences = 0L
        var caught = 0L
        for (b in buildings.values) {
            val p = b.people ?: continue
            if (p.size == 0) continue
            val i = m.index(b.x, b.y)
            val o = p.size.toLong() * (m.crime[i].toInt() and 0xff)
            offences += o
            // And a crime's reported sooner.
            val called = if (m.comms[i] >= Phone.SERVICE_PHONE) 100 + Balance.CALL_ARRESTS else 100
            caught += min(o, o * (m.policeCover[i].toInt() and 0xff) / 255 * called / 100)
        }
        val s = stats
        s.offences = (offences / 255 / Balance.OFFENCE_SHARE).toInt()
        val per = 255L * Balance.OFFENCE_SHARE * 100
        val arrests = ((caught * Balance.ARREST_SHARE + per / 2) / per).toInt()
        s.arrests = arrests
        // What each place can hear and hold, as its funding, staff and age allow.
        val places = buildings.values.filter { it.type.justice && it.underway == 0 }.sortedBy { it.id }
        fun hears(b: Building) = when {
            b.type.patrols -> Balance.LOCKUP_CASES
            b.type == BuildingType.COURTHOUSE -> Balance.COURT_CASES
            else -> 0
        } * strengthOf(b, policeFunding) / 100
        fun holds(b: Building) = when {
            b.type.patrols -> Balance.CELLS
            b.type == BuildingType.JAIL -> Balance.JAIL_PLACES
            else -> 0
        } * strengthOf(b, policeFunding) / 100
        val canHear = places.sumOf { hears(it) }
        val room = places.sumOf { holds(it) }
        val month = Justice.month(arrests, canHear, room, prisoners)
        val heard = month.heard
        val wanting = month.wanting
        prisoners = month.prisoners
        justice = month.justice
        s.heard = heard
        s.prisoners = prisoners
        s.cells = room
        s.justice = justice
        // Each place's share of the cases and the prisoners, for inspect.
        for (b in places) {
            when {
                b.type == BuildingType.COURTHOUSE -> {
                    b.room = hears(b)
                    b.served = if (canHear == 0) 0 else (arrests.toLong() * b.room / canHear).toInt()
                }
                b.type == BuildingType.JAIL -> {
                    b.room = holds(b)
                    b.served = if (room == 0) 0 else (wanting.toLong() * b.room / room).toInt()
                }
                b.type.patrols -> b.served = (arrests.toLong() * (m.policeCover[m.index(b.x, b.y)].toInt() and 0xff) / 255 / places.count { it.type.patrols }).toInt()
            }
        }
    }

    /** How well [b] works, in percent: for its age, fully until its expected life and less past it, and for what it needs. */
    fun condition(b: Building): Int {
        val fit = fit(b)
        if (b.type.life == 0) return fit
        val wear = Ageing.wear(monthNow - b.built, b.type.life)
        return (if (wear <= 100) 100 else max(Balance.WORN_SERVICE, 100 - (wear - 100) / 2)) * fit / 100
    }

    /** Whether [b] has [need] where it stands: the line, the main or the service reaching it. */
    fun reaches(b: Building, need: Need): Boolean {
        val i = map.index(b.x, b.y)
        return when (need) {
            Need.POWER -> map.powered[i]
            Need.WATER -> map.watered[i] && map.sewered[i]
            Need.PHONE -> map.comms[i] >= Phone.SERVICE_PHONE
            Need.BROADBAND -> map.comms[i] >= Phone.SERVICE_BROADBAND
        }
    }

    /** Whether [b] was built or renovated since [year], so it's fitted for what came in then. */
    fun fitted(b: Building, year: Int): Boolean = b.built >= Ageing.monthOf(year, 0)

    /** What [b] needs now and hasn't got, and whether each is for want of renovating: the service is there but it isn't fitted for it. */
    fun unmet(b: Building): List<Pair<Need, Boolean>> = Needs.of(b.type).filter { (_, from) -> year >= from }.mapNotNull { (need, from) ->
        when {
            !reaches(b, need) -> need to false
            !fitted(b, from) -> need to true
            else -> null
        }
    }

    /** Whether [b] does its work at all: half or better for what it needs. */
    fun working(b: Building): Boolean = fit(b) >= Needs.WORKING

    /** Whether what buildings need counts. Only tests about something else turn it off. */
    internal var needsApply = true

    /** How well [b] works for what it needs, in percent. */
    fun fit(b: Building): Int {
        if (!needsApply || b.type.zone != Zone.NONE || b.underway > 0) return 100
        val lost = unmet(b).sumOf { it.first.cost }
        return max(Needs.LEAST, 100 - lost)
    }

    /** [b]'s strength: its kind's, for its age, nothing while it's shut. */
    private fun strengthOf(b: Building, funding: Int): Int =
        if (b.outage > 0 || b.underway > 0) 0 else strength(b.type, funding) * condition(b) / 100

    /**
     * How strong a service is, in percent: its funding (40% at none) and the
     * share of its staff the town can find, by the schooling the work needs.
     */
    internal fun strength(type: BuildingType, funding: Int): Int {
        val funded = 40 + 60 * funding.coerceIn(0, 100) / 100
        return funded * staffed(type) / 100
    }

    /** The share of [type]'s staff the town has, in percent, by the schooling its work needs. */
    fun staffed(type: BuildingType): Int {
        val skills = Demography.jobSkills(type)
        var share = 0
        for (k in 0 until Education.LEVELS) share += skills[k] * (100 - skillShortage[k])
        return (share / 100).coerceIn(Balance.LEAST_STAFF, 100)
    }

    /** Cover by road from [stations]: full within [full] seconds' drive, none past [most], both stretched by [strength]. */
    private fun responseCover(stations: List<Building>, full: Int, most: Int, strength: Int, out: ByteArray) {
        if (stations.isEmpty()) {
            out.fill(0)
            return
        }
        out.fill(0)
        // Fewer crews are slower to get out, but they still drive at the speed of the roads.
        val stretch = 50 + strength / 2
        val f = full * stretch / 100
        val l = max(f + 1, most * stretch / 100)
        val best = IntArray(map.size) { Int.MAX_VALUE }
        for (b in stations.sortedBy { it.id }) {
            val node = accessOf(b)
            if (node < 0) continue
            val times = traffic.travelTimes(node, l)
            for (i in 0 until map.size) if (times[i] in 0 until best[i]) best[i] = times[i]
        }
        for (i in 0 until map.size) {
            val road = access[i]
            if (road < 0) continue
            val t = best[road]
            if (t == Int.MAX_VALUE) continue
            out[i] = (if (t <= f) 255 else 255 * (l - t) / (l - f)).coerceIn(0, 255).toByte()
        }
    }

    /**
     * Now and then a home, shop or works catches fire, far less often near a
     * fire station. Power stations and services don't, until disasters come.
     */
    private fun startFires() {
        val era = fireRisk(year)
        for (b in buildings.values.toList()) {
            if (b.type.zone == Zone.NONE || b.burning > 0 || b.underway > 0) continue
            val base = if (b.type.zone == Zone.INDUSTRIAL) Balance.FIRE_CHANCE_INDUSTRY else Balance.FIRE_CHANCE
            // Wooden first rungs catch readily; solid brick and stone less so.
            val built = when {
                b.type.stage == 1 -> Balance.WOODEN_FIRE
                b.type.heritage -> Balance.SOLID_FIRE
                else -> 100
            }
            val chance = base * era / 100 * built / 100
            val cover = map.fireCover[map.index(b.x, b.y)].toInt() and 0xff
            if (rng.nextInt(10_000) < chance * (255 - cover * 85 / 100) / 255) ignite(b)
        }
    }

    /**
     * How likely fires are in [year], in percent of 1900's: gas, oil lamps and
     * coal stoves give way to electric light and building codes.
     */
    private fun fireRisk(year: Int): Int = when {
        year <= 1910 -> 100
        year >= 1980 -> 40
        else -> 100 - (year - 1910) * 60 / 70
    }

    /**
     * How well the fire halls can fight a fire on a tile, 0 to 255: their cover,
     * and more where there's mains water for the hydrants.
     */
    internal fun fireCoverAt(i: Int): Int {
        var cover = map.fireCover[i].toInt() and 0xff
        if (cover > 0 && map.watered[i]) cover = min(255, cover + Balance.HYDRANT_COVER)
        // A fire's called in sooner where there's a phone.
        if (cover > 0 && map.comms[i] >= Phone.SERVICE_PHONE) cover = min(255, cover + Balance.CALL_COVER)
        return cover
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
     * out, the building is saved as likely as the fire halls' cover there makes
     * it, sure to be with full cover. One that isn't burns down a stage, or is
     * lost if it's the first, wooden rung.
     */
    private fun burnDay() {
        val burning = buildings.values.filter { it.burning > 0 }
        for (b in burning) {
            val i = map.index(b.x, b.y)
            // A tall building's fire needs ladders too.
            // Without them the hall can do a third as much.
            val fire = fireCoverAt(i)
            val cover = if (Density.rank(b.type.density) >= Density.rank(Density.HIGH)) min(fire, max(map.ladderCover[i].toInt() and 0xff, fire / 3)) else fire
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
            if (rng.nextInt(Balance.FIRE_SAVED) < cover) {
                stamp(b)
                events += CityEvent(EventKind.FireSaved, b.x, b.y, b.type)
            } else if (b.type.previous != null && b.type.stage > 1) {
                events += CityEvent(EventKind.FireDamaged, b.x, b.y, b.type)
                shrink(b)
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
        shippingDirty = true
        bridgesDirty = true
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
            // No lot is reached from a highway or its ramps.
            val here = RoadType.of(m.road[i])
            if (here?.limited == true || here?.ramp == true) continue
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
        updateBridges()
        updatePorts()
        updateWater()
        updatePower()
        updateTransit()
    }

    // ---- bridges -----------------------------------------------------------------

    private var bridgesDirty = true

    /** Each bridge's tiles, from one bank to the other. */
    private var bridgeRuns: List<IntArray> = emptyList()

    /** How many water tiles the bridge on each tile spans. */
    private val spanAt = IntArray(map.size)

    /** What a toll costs to cross, in cents. */
    var tollRate = Balance.TOLL_CENTS
        private set

    fun setTollRate(cents: Int) {
        tollRate = cents.coerceIn(0, Balance.TOLL_MOST)
        bridgeState()
    }

    /** Whether any bridge charges a toll. */
    val anyTolls: Boolean get() = bridgeRuns.any { run -> map.bridge[run[0]].toInt() and Bridge.TOLL != 0 }

    /** The map with its bridges worked out: their spans, and the approaches to high ones. */
    private fun bridges(): CityMap {
        if (bridgesDirty) updateBridges()
        return map
    }

    /** How many water tiles the bridge on tile [i] spans, 0 if there's none. */
    fun span(i: Int): Int {
        bridges()
        return spanAt[i]
    }

    /**
     * Finds each bridge from bank to bank, which way it runs (old ones didn't
     * say), and the land leading up to the high ones, then what that means
     * for the traffic.
     */
    private fun updateBridges() {
        bridgesDirty = false
        val m = map
        m.approach.fill(0)
        spanAt.fill(0)
        val runs = ArrayList<IntArray>()
        val seen = BooleanArray(m.size)
        for (i in 0 until m.size) {
            if (seen[i] || !m.bridged(i)) {
                if (!m.bridged(i) && m.bridge[i].toInt() != 0) m.bridge[i] = 0
                continue
            }
            val rail = m.rail[i] != Rail.NONE
            fun on(x: Int, y: Int) = m.inside(x, y) && m.terrain[m.index(x, y)] == Terrain.WATER &&
                if (rail) m.rail[m.index(x, y)] != Rail.NONE else m.road[m.index(x, y)] != Road.NONE
            fun carries(x: Int, y: Int) = m.inside(x, y) && if (rail) m.rail[m.index(x, y)] != Rail.NONE else m.road[m.index(x, y)] != Road.NONE
            val x = i % m.width
            val y = i / m.width
            val ew = carries(x - 1, y) || carries(x + 1, y)
            val dx = if (ew) 1 else 0
            val dy = if (ew) 0 else 1
            var sx = x
            var sy = y
            while (on(sx - dx, sy - dy)) { sx -= dx; sy -= dy }
            val tiles = ArrayList<Int>()
            var tx = sx
            var ty = sy
            while (on(tx, ty)) {
                tiles += m.index(tx, ty)
                tx += dx
                ty += dy
            }
            for (j in tiles) {
                seen[j] = true
                spanAt[j] = tiles.size
                m.bridge[j] = ((m.bridge[j].toInt() and Bridge.ACROSS.inv()) or (if (ew) Bridge.ACROSS else 0)).toByte()
            }
            runs += tiles.toIntArray()
            // The straight land up to a high bridge at each end.
            val reach = m.bridgeKind(i)?.approach ?: 0
            for (k in 1..reach) {
                for ((ax, ay) in listOf(sx - dx * k to sy - dy * k, tx - dx + dx * k to ty - dy + dy * k)) {
                    if (carries(ax, ay) && m.terrain[m.index(ax, ay)] != Terrain.WATER) m.approach[m.index(ax, ay)] = (if (ew) Bridge.ACROSS else 1).toByte()
                }
            }
        }
        bridgeRuns = runs
        bridgeState()
    }

    /** How worn the bridge on tile [i] is, in percent of its life. */
    fun bridgeWear(i: Int): Int {
        val m = map
        val life = m.bridgeKind(i)?.life ?: RoadType.of(m.road[i])?.life ?: Balance.TRACK_LIFE
        val laid = if (m.road[i] != Road.NONE) m.roadLaid[i] else m.railLaid[i]
        return Ageing.wear(monthNow - laid, life)
    }

    /** Whether the bridge on tile [i] turns trucks away: too light a kind, or posted as worn. */
    fun bridgeLight(i: Int): Boolean {
        val m = map
        val kind = m.bridgeKind(i)
        val light = kind?.heavy == false || kind == null && RoadType.of(m.road[i])?.let { it == RoadType.DIRT || it == RoadType.GRAVEL || it == RoadType.LANE } == true
        return light || bridgeWear(i) >= Balance.POSTED_WEAR
    }

    /**
     * What the bridges are like this month: the worn ones posted against
     * trucks or shut as unsafe, which charge tolls, and how much the trucks
     * that crossed have worn them.
     */
    private fun bridgeState() {
        val m = map
        val heavy = BooleanArray(m.size)
        val toll = BooleanArray(m.size)
        var railShut = false
        for (run in bridgeRuns) for (i in run) {
            heavy[i] = bridgeLight(i)
            toll[i] = m.bridge[i].toInt() and Bridge.TOLL != 0
            val unsafe = bridgeWear(i) >= Balance.UNSAFE_WEAR
            val was = m.bridgeShut[i].toInt() and 0xff
            if (unsafe && was != Balance.SHUT_UNSAFE) {
                m.bridgeShut[i] = Balance.SHUT_UNSAFE.toByte()
                railShut = railShut || m.rail[i] != Rail.NONE
            } else if (!unsafe && was == Balance.SHUT_UNSAFE) {
                m.bridgeShut[i] = 0
                railShut = railShut || m.rail[i] != Rail.NONE
            }
        }
        if (railShut) railChanged = true
        traffic.setBridges(heavy, toll, tollRate * Balance.TOLL_SECONDS_PER_CENT)
    }

    /** Monthly: trucks wear the bridges they cross, a month's more wear for each so many loads. */
    private fun truckWear() {
        val m = map
        for (run in bridgeRuns) for (i in run) {
            val months = traffic.lastTrucks[i] / Balance.TRUCK_WEAR_LOADS
            if (months == 0) continue
            if (m.road[i] != Road.NONE) m.roadLaid[i] = max(0, m.roadLaid[i] - months).toShort()
            else m.railLaid[i] = max(0, m.railLaid[i] - months).toShort()
        }
    }

    /** Daily: long high bridges shut in a gale, and open again when it's been over a while. */
    private fun bridgeWeather() {
        val m = map
        val gale = weather.windSpeed >= Weather.GALE
        for (run in bridgeRuns) {
            val kind = m.bridgeKind(run[0])
            val shuts = gale && kind != null && kind.shutsInGale(run.size)
            if (shuts && (m.bridgeShut[run[0]].toInt() and 0xff) == 0) events += CityEvent(EventKind.BridgeShut, run[0] % m.width, run[0] / m.width, null)
            for (i in run) {
                val days = m.bridgeShut[i].toInt() and 0xff
                if (days == Balance.SHUT_UNSAFE) continue
                val now = if (shuts) Balance.GALE_SHUT_DAYS else max(0, days - 1)
                if (now != days) {
                    m.bridgeShut[i] = now.toByte()
                    if (m.rail[i] != Rail.NONE && (now == 0) != (days == 0)) railChanged = true
                    if (now == 0 || days == 0) networksDirty = true
                }
            }
        }
    }

    // ---- tunnels -----------------------------------------------------------------

    /**
     * Each tunnel, as the tiles it runs under: a tunnel is one run of tiles
     * joined under ground, both bores of a divided road together.
     */
    private fun tunnels(): List<IntArray> {
        val m = map
        val seen = BooleanArray(m.size)
        val out = ArrayList<IntArray>()
        val queue = IntArray(m.size)
        for (start in 0 until m.size) {
            if (seen[start] || !m.tunnelled(start)) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            seen[start] = true
            while (head < tail) {
                val i = queue[head++]
                for (k in 0 until 4) {
                    val nx = i % m.width + DX[k]
                    val ny = i / m.width + DY[k]
                    if (!m.inside(nx, ny)) continue
                    val j = m.index(nx, ny)
                    if (seen[j] || !m.tunnelled(j)) continue
                    seen[j] = true
                    queue[tail++] = j
                }
            }
            out += queue.copyOf(tail)
        }
        return out
    }

    /** Whether the pumps of the tunnel on [tiles] have power: anything powered on or beside it. */
    fun pumped(tiles: IntArray): Boolean {
        val m = map
        for (i in tiles) {
            val x = i % m.width
            val y = i / m.width
            for (dy in -1..1) for (dx in -1..1) if (m.inside(x + dx, y + dy) && m.powered[m.index(x + dx, y + dy)]) return true
        }
        return false
    }

    /** Whether the tunnel under tile [i] has its pumps running. */
    fun pumpedAt(i: Int): Boolean = tunnels().firstOrNull { i in it }?.let { pumped(it) } ?: false

    /**
     * Tunnels with no power for their pumps flood: in a downpour, or (with
     * [underWaterOnly]) those under a river or lake, which seep all the time.
     * They're shut until they're pumped out and mended.
     */
    private fun floodTunnels(underWaterOnly: Boolean) {
        val m = map
        var flooded = -1
        for (tunnel in tunnels()) {
            if (pumped(tunnel)) continue
            if (underWaterOnly && tunnel.none { m.terrain[it] == Terrain.WATER }) continue
            if (tunnel.all { m.tunnelShut(it) }) continue
            for (i in tunnel) {
                m.broken[i] = (m.broken[i].toInt() or Broken.LOW).toShort()
                m.mending[i] = max(m.mending[i].toInt() and 0xff, Balance.MEND_TUNNEL).toByte()
                mendingTiles += i
                if (m.lowRail[i].toInt() != 0) railChanged = true
            }
            networksDirty = true
            flooded = tunnel[0]
        }
        if (flooded >= 0) events += CityEvent(EventKind.TunnelFlooded, flooded % m.width, flooded / m.width, null)
    }

    // ---- climate -----------------------------------------------------------------

    /** Whether tile [i] is in a district with cool roofs and paving, once they've come in. */
    private fun coolRoof(i: Int): Boolean = (everything || year >= Balance.COOL_ROOF_YEAR) && districtAt(i)?.coolRoofs == true

    /** Whether tile [i] is in a district with green roofs, once they've come in. */
    private fun greenRoof(i: Int): Boolean = (everything || year >= Balance.GREEN_ROOF_YEAR) && districtAt(i)?.greenRoofs == true

    /** All the carbon the town's put out, in tonnes. */
    var carbonTotal = 0L
        private set

    /**
     * How much warmer than it used to be, in tenths of a degree: the world's
     * warming from 1980, and a little more for the town's own carbon.
     */
    val warming: Int
        get() = max(0, (year - Balance.WARMING_FROM) * Balance.WARMING_PER_DECADE / 10) + townWarming

    /** The town's own share of the warming, in tenths of a degree. */
    val townWarming: Int get() = min(Balance.TOWN_WARMING_MOST.toLong(), carbonTotal / Balance.CARBON_PER_TENTH).toInt()

    /** Heat waves and floods so far this year, and last year's. */
    var heatWavesThisYear = 0
        private set
    var floodsThisYear = 0
        private set
    var heatWavesLastYear = 0
        private set
    var floodsLastYear = 0
        private set

    /**
     * The town's carbon this month, in tonnes: what its power stations burn,
     * its traffic, its heavy works, and heating its homes in the cold. Where
     * it comes from goes into [CityMap.carbon] for the map.
     */
    private fun carbon() {
        val m = map
        val s = stats
        m.carbon.fill(0)
        var power = 0L
        var works = 0L
        // On the map by the square root, so the small sources show beside the big ones.
        fun shade(tonnes: Long) = min(255, (kotlin.math.sqrt(tonnes.toDouble()) * Balance.CARBON_MAP_SCALE).toInt())
        fun mark(b: Building, tonnes: Long) {
            val v = shade(tonnes)
            forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { m.carbon[it] = max(m.carbon[it].toInt() and 0xff, v).toByte() }
        }
        for (b in buildings.values) {
            if (b.underway > 0) continue
            val perMwh = Generation.carbon(b.type)
            if (perMwh > 0) {
                val t = stationOutput(b).toLong() * Balance.HOURS_A_MONTH / 1_000_000 * perMwh / 100
                power += t
                mark(b, t)
            } else if (b.type.zone == Zone.INDUSTRIAL && b.type.pollution > 0) {
                val t = b.type.pollution.toLong() * Balance.WORKS_CARBON * (if (districtAt(m.index(b.x, b.y))?.cleanWorks == true) Balance.CLEAN_WORKS_SHARE else 100) / 100
                works += t
                mark(b, t)
            }
        }
        // Traffic: vehicles over every tile, by how dirty the cars are this year.
        var vehicleTiles = 0L
        for (i in 0 until m.size) if (traffic.lastVolume[i] > 0) {
            vehicleTiles += traffic.lastVolume[i]
            val t = traffic.lastVolume[i].toLong() * Fumes.level(year) * Balance.TRAFFIC_CARBON / 100_000
            m.carbon[i] = max(m.carbon[i].toInt() and 0xff, shade(t * Balance.ROAD_CARBON_SHOW)).toByte()
        }
        val traffic = vehicleTiles * Fumes.level(year) * Balance.TRAFFIC_CARBON / 100_000
        // Heating homes when it's cold, less as houses and heating get better.
        val cold = max(0, Balance.HEATING_BELOW - (climate.temperature[month] + warming / 10))
        val better = when {
            year < 1970 -> 100
            year < 2010 -> 70
            else -> 40
        }
        val heating = s.population.toLong() * cold * Balance.HEATING_CARBON * better / 100 / 100
        s.carbon = power + traffic + works + heating
        s.carbonPower = power
        s.carbonTraffic = traffic
        s.carbonWorks = works
        s.carbonHeating = heating
        carbonTotal += s.carbon
    }

    // ---- airports ----------------------------------------------------------------

    private val airports get() = buildings.values.filter { it.type.airport && it.underway == 0 && it.outage == 0 && accessOf(it) >= 0 && working(it) }

    /** The airports with planes coming and going, for drawing them. */
    fun airportsShown(): List<Building> = airports

    /** The biggest working airport a road reaches, by [BuildingType.airTier], 0 for none. */
    var airTier = 0
        private set

    /**
     * The planes' noise, worked out each month: loudest at the end of each
     * runway and fading over the airport's reach. Land loses value under it,
     * and homes appeal.
     */
    private fun updateAirports() {
        val m = map
        val here = airports
        airTier = here.maxOfOrNull { it.type.airTier } ?: 0
        m.noise.fill(0)
        for (b in here) {
            val tier = b.type.airTier
            val reach = Balance.AIR_NOISE_REACH[tier]
            val most = Balance.AIR_NOISE[tier]
            // The runway runs east to west along the middle; planes come in low off each end.
            val y = b.y + b.type.height / 2
            for (end in listOf(b.x - 1, b.x + b.type.width)) {
                val dx = if (end < b.x) -1 else 1
                for (k in 0 until reach * 2) {
                    val x = end + dx * k
                    val fade = most * (reach * 2 - k) / (reach * 2)
                    for (w in -reach / 2..reach / 2) {
                        if (!m.inside(x, y + w)) continue
                        val i = m.index(x, y + w)
                        m.noise[i] = max(m.noise[i].toInt() and 0xff, fade * (reach - abs(w)) / reach).toByte()
                    }
                }
            }
            // And all round it, less.
            forRect(max(0, b.x - reach), max(0, b.y - reach), min(m.width - 1, b.x + b.type.width - 1 + reach), min(m.height - 1, b.y + b.type.height - 1 + reach)) { i ->
                m.noise[i] = max(m.noise[i].toInt() and 0xff, most / 2).toByte()
            }
        }
    }

    // ---- visitors ----------------------------------------------------------------

    /**
     * Visitors this month: as many as the town draws, with its size, parks and
     * heritage, as far as the ways in can bring them. A share stays in the
     * hotels, the nearest parks filling first, and they all spend in the shops.
     */
    private fun tourism() {
        val s = stats
        var draw = Balance.VISITORS_BASE + Balance.VISITORS_PER_RESIDENT * s.population
        val hotels = ArrayList<Building>()
        for (b in buildings.values) {
            if (b.underway > 0) continue
            if (b.type == BuildingType.PARK) draw += Balance.PARK_DRAW
            if (b.type == BuildingType.HOTEL) hotels += b
            if (isHeritage(b)) draw += Balance.HERITAGE_DRAW
        }
        // Fewer come in hard times, or to a town known for its crime.
        draw = draw * Tourism.share(year) / 100.0 * Economy.market(year, month) / 100.0 * (100 - min(50, s.crime * 50 / 128)) / 100.0
        val ways = IntArray(Tourism.MODES)
        if (connected) ways[Tourism.ROAD] = Balance.ROAD_VISITORS + Balance.ROAD_VISITORS_BY_CAR * Cars.share(year, Wealth.MIDDLE) / 100
        ways[Tourism.RAIL] = railway.stops.indices.count { railway.buildings[it].type.station && railway.linked(it) } * Balance.RAIL_VISITORS
        ways[Tourism.SEA] = linkedPorts.sumOf { Balance.SEA_VISITORS[it.type.portTier] * fit(it) / 100 }
        ways[Tourism.AIR] = airports.sumOf { Balance.AIR_VISITORS[it.type.airTier] * fit(it) / 100 }
        val room = ways.sum()
        val visitors = if (room == 0) 0 else min(draw.toInt(), room)
        s.visitors = visitors
        for (k in 0 until Tourism.MODES) s.visitorsBy[k] = if (room == 0) 0 else visitors * ways[k] / room
        // Hotels with parks near them fill first.
        var staying = visitors * Balance.STAY_SHARE / 100
        var rooms = 0
        val parks = hotels.associate { h -> h.id to parksNear(h, Balance.HOTEL_PARKS) }
        for (h in hotels.sortedWith(compareByDescending<Building> { parks[it.id] }.thenBy { it.id })) {
            h.room = h.type.capacity * Balance.ROOMS_PER_JOB
            h.served = min(staying, h.room)
            staying -= h.served
            rooms += h.room
        }
        s.rooms = rooms
        s.guests = hotels.sumOf { it.served }
        s.spending += ((s.guests * Balance.GUEST_SPEND + (visitors - s.guests) * Balance.TRIPPER_SPEND) / 100)
    }

    /** Park tiles within [reach] of building [b]. */
    private fun parksNear(b: Building, reach: Int): Int {
        var n = 0
        forRect(max(0, b.x - reach), max(0, b.y - reach), min(map.width - 1, b.x + b.type.width - 1 + reach), min(map.height - 1, b.y + b.type.height - 1 + reach)) { i ->
            if (buildings[map.building[i]]?.type == BuildingType.PARK) n++
        }
        return n
    }

    // ---- ports -------------------------------------------------------------------

    private val shipping = Shipping(map)
    private var shippingDirty = true

    /** The biggest port ships can reach, by [BuildingType.portTier], 0 for none. */
    var seaTier = 0
        private set

    /** The ports ships reach. */
    private var linkedPorts: List<Building> = emptyList()

    /** The ships each port had last month, for drawing them. */
    var shipRoutes: List<ShipRoute> = emptyList()
        private set

    /** The water as ships see it now. */
    private fun ships(): Shipping {
        if (shippingDirty) {
            shippingDirty = false
            shipping.update()
        }
        return shipping
    }

    private val ports get() = buildings.values.filter { it.type.port && it.underway == 0 }

    /** Which ports ships can reach, and the freight they take. */
    private fun updatePorts() {
        val linked = if (buildings.values.any { it.type.port }) ports.filter { working(it) && ships().reaches(Port.berth(map, it)) } else emptyList()
        linkedPorts = linked
        seaTier = linked.maxOfOrNull { it.type.portTier } ?: 0
        traffic.setPorts(IntArray(linked.size) { accessOf(linked[it]) })
    }

    /** Whether ships can reach port [b]. */
    fun portLinked(b: Building): Boolean = ships().reaches(Port.berth(map, b))

    /** Loads through port [b] last month. */
    fun portLoads(b: Building): Int = accessOf(b).let { if (it < 0) 0 else traffic.lastPortFreight[it] }

    /**
     * Whether putting something across the water on [tiles] would leave a
     * port ships reach now with no way out to the edge.
     */
    private fun cutsOffPort(tiles: Collection<Int>): Boolean {
        val here = ports
        if (here.isEmpty() || tiles.none { map.terrain[it] == Terrain.WATER && ships().passable(it) }) return false
        val reached = here.map { Port.berth(map, it) }.filter { ships().reaches(it) }
        if (reached.isEmpty()) return false
        val test = Shipping(map)
        test.update(tiles.toSet())
        return reached.any { !test.reaches(it) }
    }

    /** The ships that came to each port last month: the way in, and the kind, by its trade and the year. */
    private fun updateShips() {
        val routes = ArrayList<ShipRoute>()
        for (b in ports) {
            val berth = Port.berth(map, b)
            if (!ships().reaches(berth)) continue
            val path = ships().routeTo(berth)
            val loads = portLoads(b)
            val ships = (loads + Balance.SHIP_LOAD - 1) / Balance.SHIP_LOAD
            val kind = when {
                b.type.portTier == 3 -> ShipRoute.CONTAINER
                stats.goodsImported[Good.OIL.ordinal] + stats.goodsImported[Good.FUEL.ordinal] > stats.goodsImported[Good.COAL.ordinal] && year >= 1920 -> ShipRoute.TANKER
                stats.goodsImported[Good.COAL.ordinal] > 0 -> ShipRoute.COLLIER
                else -> ShipRoute.STEAMER
            }
            routes += ShipRoute(path, kind, max(1, ships))
            if (Balance.SEA_VISITORS[b.type.portTier] > 0 && stats.visitorsBy[Tourism.SEA] > 0) routes += ShipRoute(path, ShipRoute.LINER, 1)
        }
        shipRoutes = routes
        // Each ship through a lifting bridge holds the traffic a while.
        val wait = IntArray(map.size)
        for (route in routes) for (i in route.tiles) if (map.bridged(i) && map.clearance(i) == Bridge.OPENS) {
            wait[i] = min(Balance.LIFT_MAX, wait[i] + route.ships * Balance.LIFT_DELAY)
        }
        traffic.setLifts(wait)
    }

    // ---- power -------------------------------------------------------------------

    private val grid = PowerGrid(map)

    /** Works out who has power: the stations' output against the buildings' peak demand, network by network. */
    private fun updatePower() {
        val perPerson = Electricity.perPerson(year)
        // Trams, trolleybuses and the subway draw at their depots, garages and stations, shared between them.
        val traction = buildings.values.count { it.type == BuildingType.TRAM_DEPOT || it.type == BuildingType.SUBWAY_STATION || it.type == BuildingType.BUS_GARAGE }
        val tractionEach = if (traction == 0) 0 else traffic.electricRiders() * Balance.TRACTION_W / traction
        // A heat wave in the air-conditioned years pushes the peak up.
        val peak = Electricity.peak(year, month, climate, warming) + if (heatWaveDays > 0 && year >= 1960) Balance.HEAT_WAVE_PEAK else 0
        batteryCharge = charge(perPerson, tractionEach)
        grid.update(buildings.values, { b -> draw(b, perPerson, tractionEach) }, { b -> available(b) }, peak)
        stats.powerCapacity = grid.capacity / 1000
        stats.powerDemand = grid.demand / 1000
        stats.powerShort = grid.short / 1000
    }

    /** How full the batteries go into the evening, in percent. */
    private var batteryCharge = 0

    /**
     * What the day's wind and sun leave over the town's average use, kept in
     * the batteries for the evening, in percent of what they hold.
     */
    private fun charge(perPerson: Int, tractionEach: Int): Int {
        val batteries = buildings.values.filter { it.type == BuildingType.BATTERY && it.underway == 0 && it.outage == 0 }
        if (batteries.isEmpty()) return 0
        val w = weather
        var made = 0L
        for (b in buildings.values) {
            if (b.underway > 0 || b.outage > 0) continue
            val full = Generation.capacity(b.type).toLong()
            made += when (b.type) {
                BuildingType.WIND_FARM -> full * Generation.windShare(w.windSpeed) / 100
                BuildingType.SOLAR_FARM -> full * Generation.solarDay(month, w.cloud) / 100
                BuildingType.OFFSHORE_WIND -> full * Generation.offshoreShare(w.windSpeed) / 100
                // The tide runs half the day on average; the river all of it.
                BuildingType.TIDAL_TURBINE -> full / 2
                BuildingType.RIVER_TURBINE -> full * (50 + river / 2) / 100
                else -> 0L
            }
        }
        val use = buildings.values.sumOf { draw(it, perPerson, tractionEach).toLong() }
        val room = batteries.sumOf { Generation.capacity(it.type).toLong() }
        return ((made - use).coerceAtLeast(0) * 100 / room).coerceIn(0, 100).toInt()
    }

    /** What [b] draws at the month's average, in watts. */
    private fun draw(b: Building, perPerson: Int, tractionEach: Int): Int {
        if (b.underway > 0 || Generation.station(b.type) || b.type == BuildingType.SUBSTATION) return 0
        val t = b.type
        return when (t.zone) {
            Zone.RESIDENTIAL -> (b.people?.size ?: 0) * perPerson * (if (coolRoof(map.index(b.x, b.y)) || greenRoof(map.index(b.x, b.y))) 100 - Balance.COOL_ROOF_POWER else 100) / 100
            Zone.COMMERCIAL -> t.capacity * perPerson * 2
            Zone.INDUSTRIAL -> t.capacity * perPerson * 4
            else -> t.capacity * perPerson * 2 +
                if (t == BuildingType.TRAM_DEPOT || t == BuildingType.SUBWAY_STATION || t == BuildingType.BUS_GARAGE) tractionEach else 0
        }
    }

    /** What a station can make now, in watts: nothing broken down or under water; hydro with the river. */
    private fun available(b: Building): Int {
        if (!Generation.station(b.type) || b.underway > 0 || b.outage > 0 || flooded(b)) return 0
        val full = Generation.capacity(b.type)
        val w = weather
        return when (b.type) {
            BuildingType.HYDRO_PLANT -> (full.toLong() * (50 + river / 2) / 100).toInt()
            // The weather's: the wind, and the evening sun through the cloud.
            BuildingType.WIND_FARM -> (full.toLong() * Generation.windShare(w.windSpeed) / 100).toInt()
            BuildingType.SOLAR_FARM -> (full.toLong() * Generation.solarPeak(month, w.cloud) / 100).toInt()
            BuildingType.RIVER_TURBINE -> (full.toLong() * (50 + river / 2) / 100).toInt()
            BuildingType.TIDAL_TURBINE -> (full.toLong() * Generation.tideAtPeak(day) / 100).toInt()
            BuildingType.OFFSHORE_WIND -> (full.toLong() * Generation.offshoreShare(w.windSpeed) / 100).toInt()
            // What the day left spare to keep.
            BuildingType.BATTERY -> (full.toLong() * batteryCharge / 100).toInt()
            else -> full
        }
    }

    /** What a station's making, in watts, as of the last time the grid was worked out. */
    fun stationOutput(b: Building): Int = grid.output[b.id] ?: 0

    /** What the line on tile [i] carries at the peak, in kilowatts. */
    fun lineLoad(i: Int): Int = grid.load[i]

    /** What a station could make now, in watts. */
    fun stationAvailable(b: Building): Int = available(b)

    /** What a building draws at the month's average, in watts. */
    fun buildingDraw(b: Building): Int = draw(b, Electricity.perPerson(year), 0)

    /** Whether the town can string high-voltage lines yet. */
    fun allowsHighLines(): Boolean = everything || (year >= Balance.HIGH_LINE_YEAR && era >= Era.of(Balance.HIGH_LINE_YEAR))

    // ---- transit -------------------------------------------------------------------

    private val transit = TransitNetwork(map)

    /** Tram, bus and subway networks: what's served by a working depot, garage or station with power. */
    private fun updateTransit() {
        val depots = ArrayList<Int>()
        val garages = ArrayList<Int>()
        val poweredGarages = ArrayList<Int>()
        val stations = ArrayList<Pair<Int, Int>>()
        for (b in buildings.values) {
            if (b.underway > 0 || b.outage > 0) continue
            val powered = map.powered[map.index(b.x, b.y)]
            when (b.type) {
                BuildingType.TRAM_DEPOT -> if (powered) depots += besideTram(b.type, b.x, b.y)
                BuildingType.BUS_GARAGE -> accessOf(b).let {
                    if (it >= 0) garages += it
                    // Trolleybuses need the garage to have power and the wire to reach its road.
                    if (it >= 0 && powered) poweredGarages += it
                }
                BuildingType.SUBWAY_STATION -> if (powered) accessOf(b).let { if (it >= 0) stations += it to map.index(b.x, b.y) }
                else -> {}
            }
        }
        transit.update(lines, depots, garages, poweredGarages, stations) { mode, net -> traffic.ridersOn(mode, net) }
        traffic.useTransit(transit)
    }

    /** The districts, in the order they were made. */
    val districts = ArrayList<District>()
    private var nextDistrictId = 1

    /** The district tile [i] is in, or null. */
    fun districtAt(i: Int): District? {
        val id = map.district[i].toInt() and 0xff
        return if (id == 0) null else districts.firstOrNull { it.id == id }
    }

    /** What [b] pays in tax, in percent: the town's rate for its zone, moved by its district's. */
    private fun taxOf(b: Building, base: Int): Int {
        val d = districtAt(map.index(b.x, b.y)) ?: return base
        return (base + d.tax[District.taxIndex(b.type.zone)]).coerceIn(0, 20)
    }

    /** A district's figures, counted now. */
    fun districtFigures(id: Int): DistrictFigures {
        var people = 0
        var jobs = 0
        var value = 0L
        var crime = 0L
        var pollution = 0L
        var tiles = 0
        val seen = HashSet<Int>()
        for (i in 0 until map.size) {
            if (map.district[i].toInt() and 0xff != id) continue
            tiles++
            value += map.landValue[i].toInt() and 0xff
            crime += map.crime[i].toInt() and 0xff
            pollution += map.pollution[i].toInt() and 0xff
            val b = buildings[map.building[i]] ?: continue
            if (!seen.add(b.id) || b.underway > 0) continue
            if (b.people != null) people += b.people!!.size else jobs += b.type.capacity
        }
        val n = maxOf(1, tiles)
        return DistrictFigures(people, jobs, (value / n).toInt(), (crime / n).toInt(), (pollution / n).toInt(), tiles)
    }

    /** The planned transit lines, in the order they were made. */
    val lines = ArrayList<TransitLine>()
    private var nextLineId = 1

    /** Every line as last worked out, for drawing its vehicles. */
    fun lineStates(): Collection<LineState> = transit.lines.values

    /** How line [id] is doing, as last worked out, or null if there's no such line. */
    fun lineState(id: Int): LineState? = transit.lines[id]

    /** Last month's riders boarding line [id]. */
    fun lineRiders(id: Int): Int = transit.lines[id]?.let { traffic.ridersOn(it.mode.ordinal, id) } ?: 0

    /** The lines calling at the stop on tile [i]. */
    fun linesAt(i: Int): List<TransitLine> = lines.filter { i in it.stops }

    /** The tiles a line through [stops] would run over, or null if it can't be made. For drawing one as it's planned. */
    fun routeFor(stops: List<Int>, tram: Boolean): IntArray? = if (stops.size < 2) null else transit.route(stops, tram)

    private fun vehiclePrice(tram: Boolean) = if (tram) Balance.TRAM_PRICE else Balance.BUS_PRICE

    /** Enough vehicles for a line through [stops] to come every few minutes. */
    fun suggestedVehicles(stops: List<Int>, tram: Boolean): Int {
        val route = routeFor(stops, tram) ?: return 2
        var trip = 0
        for (t in route) trip += if (tram) Balance.TRAM_TIME else (RoadType.of(map.road[t])?.time ?: Balance.WALK_TIME) + Balance.BUS_STOPPING
        return (trip / (Balance.AIMED_WAIT * 2)).coerceIn(2, if (tram) Balance.DEPOT_HOLDS else Balance.GARAGE_HOLDS)
    }

    /**
     * Lines for a town planned before there were lines: its stops, a few at
     * a time, nearest to nearest along the track or roads.
     */
    private fun autoLines() {
        for (tram in listOf(true, false)) {
            val kind = if (tram) Stop.TRAM else Stop.BUS
            val stops = (0 until map.size).filter { map.stop[it].toInt() and kind != 0 }.toMutableList()
            while (stops.isNotEmpty()) {
                var at = stops.removeAt(0)
                val run = arrayListOf(at)
                while (run.size < Balance.AUTO_LINE_STOPS && stops.isNotEmpty()) {
                    val here = at
                    val next = stops.minByOrNull { kotlin.math.abs(it % map.width - here % map.width) + kotlin.math.abs(it / map.width - here / map.width) }!!
                    if (transit.route(listOf(here, next), tram) == null) break
                    stops.remove(next)
                    run += next
                    at = next
                }
                if (run.size >= 2) lines += TransitLine(nextLineId++, tram, run.toIntArray(), maxOf(2, run.size / 2))
            }
        }
    }

    /** Which tram, bus and subway network each tile's on, -1 for none with a service. For drawing and inspect. */
    fun tramNetwork(i: Int): Int = transit.tram[i]
    fun busNetwork(i: Int): Int = transit.bus[i]
    fun trolleyNetwork(i: Int): Int = transit.trolley[i]

    /** Whether the town can string wire for trolleybuses yet. */
    fun allowsTrolleybuses(): Boolean = everything || (year >= Balance.TROLLEYBUS_YEAR && era >= Era.of(Balance.TROLLEYBUS_YEAR))
    fun subwayNetwork(i: Int): Int = transit.subway[i]

    /** Last month's riders getting on or off at a stop or station's road tile. */
    fun stopRiders(i: Int): Int = traffic.lastStopRiders[i]

    /** Last month's riders on each tile of bus route, tram track and tunnel. */
    fun busRiders(i: Int): Int = traffic.lastBusVolume[i]
    fun trolleyRiders(i: Int): Int = traffic.lastTrolleyVolume[i]
    fun tramRiders(i: Int): Int = traffic.lastTramVolume[i]
    fun subwayRiders(i: Int): Int = traffic.lastSubwayVolume[i]

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
        val waterPipes = working(m.waterPipe, Broken.WATER)
        val sewerPipes = working(m.sewerPipe, Broken.SEWER)

        // Pressure: steps along the mains from a source, or from a tower the water reached.
        val nearTower = BooleanArray(m.size)
        val steps = IntArray(m.size) { -1 }
        val queue = ArrayDeque<Int>()
        for (b in all) {
            if (b.type != BuildingType.WATER_TOWER && !b.type.waterSource || b.outage > 0) continue
            forPipesNear(b, waterPipes) { i ->
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
                if (waterPipes[j].toInt() == 0) continue
                val s = if (nearTower[j]) 0 else steps[i] + 1
                if (s > Balance.PRESSURE_REACH || (steps[j] in 0..s)) continue
                steps[j] = s
                queue.addLast(j)
            }
        }

        // Which network each main is on, and each network's supply.
        val net = components(waterPipes)
        val supply = HashMap<Int, Int>()
        var total = 0
        for (b in all) {
            if (!b.type.waterSource) continue
            var n = -1
            forPipesNear(b, waterPipes) { i -> if (n < 0) n = net[i] }
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
            forPipesNear(b, waterPipes) { i -> if (steps[i] in 0 until bestSteps) { bestSteps = steps[i]; best = i } }
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
        val sewers = components(sewerPipes)
        val outfalls = HashMap<Int, MutableList<Building>>()
        for (b in all) {
            if (!b.type.outfall) continue
            var n = -1
            forPipesNear(b, sewerPipes) { i -> if (n < 0) n = sewers[i] }
            if (n >= 0) outfalls.getOrPut(n) { ArrayList() } += b
        }
        val flow = HashMap<Int, Int>()
        for (b in all) {
            if (b.type.capacity == 0) continue
            var n = -1
            forPipesNear(b, sewerPipes) { i -> if (n < 0 && outfalls.containsKey(sewers[i])) n = sewers[i] }
            if (n < 0) continue
            forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { m.sewered[it] = true }
            flow[n] = (flow[n] ?: 0) + b.type.capacity
        }
        sewage.clear()
        for ((n, list) in outfalls) {
            val each = (flow[n] ?: 0) / list.size
            // Works clean what they can; broken down, they let it all through.
            for (b in list) sewage[m.index(b.x, b.y)] = if (b.outage > 0) each else each * b.type.fouls / 100
        }
    }

    /** What a source supplies: a pumping station less if its water is foul, a well field less if its ground is grimy. */
    private fun sourceSupply(b: Building): Int {
        val m = map
        if (flooded(b) || b.outage > 0) return 0
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
        // Works by the water tip their waste in it.
        for (b in buildings.values) {
            if (b.type.zone != Zone.INDUSTRIAL || b.underway > 0) continue
            var water = -1
            forRect(b.x - 2, b.y - 2, b.x + b.type.width + 1, b.y + b.type.height + 1) { j -> if (water < 0 && map.terrain[j] == Terrain.WATER) water = j }
            if (water >= 0) spreadFoul(water, b.type.capacity * Balance.WORKS_FOUL, target)
        }
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
        // A river carries it downstream twice as far, and hardly at all against the flow; a lake holds it round about.
        val f = flow()
        val origin = queue.filter { f[it] >= 0 }.minOfOrNull { f[it] } ?: -1
        val reach = Balance.FOUL_REACH * Balance.DOWNSTREAM_REACH
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            into[i] += strength * (reach + 1 - min(steps[i], reach)) / (reach + 1)
            if (steps[i] >= reach) continue
            val x = i % m.width
            val y = i / m.width
            for (k in 0 until 4) {
                val nx = x + DX[k]
                val ny = y + DY[k]
                if (!m.inside(nx, ny)) continue
                val j = m.index(nx, ny)
                if (m.terrain[j] != Terrain.WATER || steps[j] >= 0) continue
                if (origin >= 0 && f[j] >= 0 && f[j] < origin - Balance.UPSTREAM) continue
                steps[j] = steps[i] + if (f[i] >= 0 && f[j] > f[i]) 1 else 2
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
        floodTunnels(underWaterOnly = false)
        // A green roof holds some of the rain.
        val hard = IntArray(m.size) {
            if (m.terrain[it] == Terrain.WATER) 0
            else Stormwater.hardness(m, it) * (if (m.building[it] != 0 && greenRoof(it)) 100 - Balance.GREEN_ROOF_RAIN else 100) / 100
        }
        val runoff = IntArray(m.size) { if (m.terrain[it] == Terrain.WATER) 0 else amount * hard[it] / 100 }
        val all = buildings.values.toList()
        val stormPipes = working(m.stormPipe, Broken.STORM)
        val sewerPipes = working(m.sewerPipe, Broken.SEWER)

        // Storm drains: what each network drains into, and how much room it has.
        val drains = components(stormPipes)
        val room = HashMap<Int, Int>()
        val ponds = all.filter { it.type == BuildingType.STORM_POND }
        val pondRoom = IntArray(ponds.size) { Balance.POND_HOLDS }
        for (b in all) {
            if (b.type != BuildingType.STORM_OUTFALL && b.type != BuildingType.STORM_POND) continue
            var n = -1
            forPipesNear(b, stormPipes) { i -> if (n < 0) n = drains[i] }
            if (n < 0) continue
            room[n] = if (b.type == BuildingType.STORM_OUTFALL) Int.MAX_VALUE else (room[n] ?: 0).let { if (it == Int.MAX_VALUE) it else it + Balance.POND_HOLDS }
        }
        val r = Balance.PIPE_REACH
        if (room.isNotEmpty()) for (i in 0 until m.size) {
            if (runoff[i] == 0) continue
            val x = i % m.width
            val y = i / m.width
            var n = -1
            forRect(x - r, y - r, x + r, y + r) { j -> if (n < 0 && stormPipes[j].toInt() != 0 && room.containsKey(drains[j])) n = drains[j] }
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
            val sewers = components(sewerPipes)
            val outfallOf = HashMap<Int, Int>()
            for (b in all) {
                if (!b.type.outfall) continue
                forPipesNear(b, sewerPipes) { i -> outfallOf.getOrPut(sewers[i]) { m.index(b.x, b.y) } }
            }
            val overflow = HashMap<Int, Int>()
            for (i in 0 until m.size) {
                if (runoff[i] == 0 || !m.sewered[i]) continue
                val x = i % m.width
                val y = i / m.width
                var at = -1
                forRect(x - r, y - r, x + r, y + r) { j -> if (at < 0 && sewerPipes[j].toInt() != 0) outfallOf[sewers[j]]?.let { at = it } }
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
        if (worst >= 0) {
            events += CityEvent(EventKind.Flooding, worst % m.width, worst / m.width, null)
            floodsThisYear++
        }
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
            floodsThisYear++
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
            if ((b.type.zone == Zone.COMMERCIAL || b.type.zone == Zone.INDUSTRIAL || b.type.zone == Zone.OFFICE) && flooded(b)) b.closedDays++
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
            it.underway == 0 && ((it.type.needsWater && !map.watered[i]) || (it.type.needsSewer && !map.sewered[i]))
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
        // Rent control keeps homes within reach of the poor and the middling, however dear the land.
        if (districtAt(i)?.rentControl == true) {
            return if (wealthGap[Wealth.POOR] >= wealthGap[Wealth.MIDDLE]) Wealth.POOR else Wealth.MIDDLE
        }
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
        if (b.type.zone != Zone.RESIDENTIAL || b.underway > 0) {
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
    /**
     * Shares out the places at each building of [type] among the [homes]
     * within its reach, nearest first. A place takes in more than it has
     * room for, up to [Balance.OVERFILL] percent, and then each gets as
     * much as the room makes of the crowd: a crowded school teaches each
     * child less. Funding and staff set the room and the reach.
     */
    private fun allot(type: BuildingType, places: Int, reach: Int, funding: Int, homes: List<Building>, need: (Building) -> Int): HashMap<Int, Int> {
        val got = HashMap<Int, Int>()
        val strong = strength(type, funding)
        val r = reach * strong / 100
        for (place in buildings.values.sortedBy { it.id }) {
            if (place.type != type || place.underway > 0 || place.outage > 0) continue
            val good = condition(place)
            val room = places * strong / 100 * good / 100
            var left = room * Balance.OVERFILL / 100
            val cx = place.x + place.type.width / 2
            val cy = place.y + place.type.height / 2
            val near = homes.filter { abs(it.x - cx) + abs(it.y - cy) <= r }
                .sortedWith(compareBy<Building>({ abs(it.x - cx) + abs(it.y - cy) }, { it.id }))
            val taken = HashMap<Int, Int>()
            for (b in near) {
                if (left == 0) break
                val want = need(b) - (got[b.id] ?: 0) - (taken[b.id] ?: 0)
                if (want <= 0) continue
                val t = min(left, want)
                taken[b.id] = t
                left -= t
            }
            val all = taken.values.sum()
            place.served = all
            place.room = room
            // Crowded, each gets the share the room makes of those taken in.
            for ((id, t) in taken) got[id] = (got[id] ?: 0) + if (all > room) t * room / all else t
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
        val nursing = allot(BuildingType.NURSING_HOME, Balance.NURSING_PLACES, Balance.NURSING_REACH, healthFunding, homes) { it.people!!.elderly }
        val cooling = allot(BuildingType.COOLING_CENTRE, Balance.COOLING_PLACES, Balance.COOLING_REACH, healthFunding, homes) { it.people!!.elderly }
        val college = allot(BuildingType.COLLEGE, Balance.COLLEGE_PLACES, Balance.COLLEGE_REACH, schoolFunding, homes) { it.people!!.children / Balance.TEENS }
        val libraries = SummedArea(m.width, m.height) { j ->
            val b = buildings[m.building[j]]
            if (b != null && b.type == BuildingType.LIBRARY && b.x == j % m.width && b.y == j / m.width && b.outage == 0 && b.underway == 0) 1 else 0
        }
        val parks = SummedArea(m.width, m.height) { if (m.buildingType[it].toInt() - 1 == BuildingType.PARK.ordinal) 1 else 0 }
        val fouled = if (m.brownfield.any { it.toInt() != 0 }) SummedArea(m.width, m.height) { m.brownfield[it].toInt() } else null
        val dumpsNear = SummedArea(m.width, m.height) { if (m.buildingType[it].toInt() - 1 == BuildingType.DUMP.ordinal) 1 else 0 }
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
            // A library nearby teaches some more, at school and after.
            val library = if (libraries.around(b.x, b.y, Balance.LIBRARY_REACH) > 0) Balance.LIBRARY_SCHOOLING * strength(BuildingType.LIBRARY, schoolFunding) / 100 else 0
            val atSchool = if (kids == 0) 0 else min(100, (pupils[b.id] ?: 0) * 100 / kids + library)
            h.schooling = towards(h.schooling, atSchool, Balance.SCHOOLING_PACE)
            val older = kids / Balance.TEENS
            val atHighSchool = if (older == 0) 0 else min(100, (teens[b.id] ?: 0) * 100 / older + library)
            h.highSchooling = towards(h.highSchooling, atHighSchool, Balance.SCHOOLING_PACE)

            // Health, towards what the place gives it.
            val careShare = if (h.size == 0) 0 else min(100, ((clinic[b.id] ?: 0) + (hospital[b.id] ?: 0)) * 100 / h.size)
            var target = Balance.HEALTH_BASE + Balance.CARE_HEALTH * careShare / 100 + Balance.WEALTH_HEALTH * h.wealth
            if (m.watered[i]) target += Balance.MAINS_HEALTH
            if (m.sewered[i]) target += Balance.MAINS_HEALTH
            if (parks.around(b.x, b.y, 4) > 0) target += Balance.PARK_HEALTH
            // An ambulance that can get there in time.
            val ambulance = m.ambulanceCover[i].toInt() and 0xff
            target += Balance.AMBULANCE_HEALTH * ambulance / 255
            target -= (m.pollution[i].toInt() and 0xff) / Balance.POLLUTION_HEALTH + m.grimeLevel(i) * Balance.GRIME_HEALTH
            if (b.type == BuildingType.TENEMENT) target -= Balance.CROWDING_HEALTH
            // Smog, fouled land or a dump next door, and garbage nobody takes.
            target -= stats.smog / Balance.SMOG_HEALTH
            if (fouled != null && fouled.around(b.x, b.y, 2) > 0) target -= Balance.CONTAMINATED_HEALTH
            if (dumpsNear.around(b.x, b.y, 3) > 0) target -= Balance.DUMP_HEALTH
            if (b.uncollected) target -= Balance.UNCOLLECTED_HEALTH
            h.health = towards(h.health, target.coerceIn(5, 100), Balance.HEALTH_PACE)

            // Born, growing up, growing old, dying.
            val factor = Demography.healthFactor(h.health)
            val born = flow(h.adults, Demography.births(year, h.wealth))
            val grown = min(h.children, flow(h.children, Demography.GROWING_UP))
            val aged = min(h.adults, flow(h.adults, Demography.GROWING_OLD))
            val adultDied = min(h.adults - aged, flow(h.adults, Demography.adultDeaths(year) * factor / 100 * (100 - Balance.AMBULANCE_SAVES * ambulance / 255) / 100))
            // Ambulances save some of the grown and old; a nursing home some more of the old in it.
            val saved = 100 - Balance.AMBULANCE_SAVES * ambulance / 255
            val nursed = if (h.elderly == 0) 0 else min(100, (nursing[b.id] ?: 0) * 100 / h.elderly)
            var elderDied = min(h.elderly, flow(h.elderly, Demography.elderlyDeaths(year) * factor / 100 * saved / 100 * (100 - Balance.NURSING_SAVES * nursed / 100) / 100))
            var childDied = min(h.children - grown, flow(h.children, Demography.childDeaths(year) * factor / 100))
            // A heat wave takes the elderly in the hottest homes, the less so with a doctor.
            if (heatWaveDays > 0) {
                val heat = m.heat[i].toInt() and 0xff
                val careless = 100 - careShare
                // A cooling centre nearby takes in the old and frail.
                val cooled = if (h.elderly == 0) 0 else min(100, (cooling[b.id] ?: 0) * 100 / h.elderly) * Balance.COOLING_SAVES / 100
                elderDied = min(h.elderly, elderDied + flow(h.elderly, heat / 10 * Balance.HEAT_DEATHS * careless / 100 * (100 - cooled) / 100))
                if (heat >= Balance.HOT_HOME) h.health = max(5, h.health - Balance.HEAT_HEALTH)
            }
            // An epidemic strikes a home by how crowded and poorly served it is.
            if (epidemicMonths > 0) {
                val crowd = when (b.type) {
                    BuildingType.TENEMENT, BuildingType.APARTMENTS, BuildingType.APARTMENT_COURT -> 150
                    BuildingType.ROW_HOUSES -> 120
                    else -> 100
                }
                var chance = epidemicStrength * crowd / 100 * (100 - careShare) / 100
                if (m.watered[i]) chance = chance * 70 / 100
                if (m.sewered[i]) chance = chance * 70 / 100
                if (rng.nextInt(100) < chance) {
                    h.health = max(5, h.health - Balance.EPIDEMIC_HEALTH)
                    childDied = min(h.children - grown, childDied + flow(h.children, Balance.EPIDEMIC_CHILD_DEATHS))
                    elderDied = min(h.elderly, elderDied + flow(h.elderly, Balance.EPIDEMIC_ELDERLY_DEATHS))
                }
            }
            removeAdults(h, aged + adultDied)
            // Each child grown up has had the schooling the home's children get, as far as chance goes.
            // College takes some of those who'd have stopped at school on to be educated.
            val atCollege = if (older == 0) 0 else min(100, (college[b.id] ?: 0) * 100 / older)
            repeat(grown) {
                val level = when {
                    rng.nextInt(100) >= h.schooling -> Education.UNSCHOOLED
                    rng.nextInt(100) >= h.highSchooling -> if (rng.nextInt(100) < atCollege) Education.EDUCATED else Education.SCHOOLED
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
    private val quota = IntArray(Zone.COUNT)

    private fun growDay() {
        val left = daysIn(month, year) - day + 1
        for (zone in 1 until Zone.COUNT) {
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

    /**
     * Builds up the best of a few lots a rung: a new building on an empty lot,
     * or the one there pulled down and something bigger put up in its place.
     * Returns the room for people or jobs it'll have, which is what the
     * month's growth is counted in: a rebuilding takes its people away for a
     * while, so a town rebuilds only a few at a time.
     */
    private fun growOnce(zone: Byte): Int {
        val lots = zoneLots(zone)
        if (lots.isEmpty()) return 0
        var best = -1
        var bestScore = Int.MIN_VALUE
        var bestOptions = emptyList<BuildingType>()
        repeat(Balance.CANDIDATES) {
            val i = lots[rng.nextInt(lots.size)]
            if (!nearRoad[i]) return@repeat
            val b = buildings[map.building[i]]
            if (b != null && (b.age < Balance.REBUILD_DAYS || b.underway > 0 || b.burning > 0)) return@repeat
            // A protected district keeps its heritage.
            if (b != null && districtAt(i)?.heritage == true && isHeritage(b)) return@repeat
            // No one to build on to an empty home.
            if (b?.people?.empty == true) return@repeat
            val pull = attraction(i, zone)
            val options = choices(b, i, zone, pull)
            if (options.isEmpty()) return@repeat
            // An empty lot before pulling something down; on farmland, the lot for what the town's short of.
            val score = pull + rng.nextInt(10) + (if (b == null) 4 else 0) + if (zone == Zone.FARMLAND) shortOf(options[0]) else 0
            if (score > bestScore) {
                bestScore = score
                best = i
                bestOptions = options
            }
        }
        if (best < 0) return 0
        // The rung's usual building, now and then the other choice where it's allowed: a bank among the shops.
        val type = if (bestOptions.size == 1 || rng.nextInt(4) != 0) bestOptions[0] else bestOptions[1 + rng.nextInt(bestOptions.size - 1)]
        val b = buildings[map.building[best]]
        val added = when {
            type.large -> assemble(type, best, b)
            b == null -> {
                // A woodlot is the woods.
                if (type != BuildingType.WOODLOT) clearTrees(best)
                addBuilding(type, best % map.width, best / map.width, rng.nextInt(1000), underway = type.buildDays)
                networksChanged()
                type.capacity
            }
            // A building on more than one lot gives way to one on a single lot; the rest stand empty to grow again.
            b.type.large -> {
                forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { townChanges += it }
                removeBuilding(b)
                addBuilding(type, best % map.width, best / map.width, rng.nextInt(1000), underway = type.buildDays)
                networksChanged()
                type.capacity
            }
            else -> {
                rebuild(b, type)
                type.capacity
            }
        }
        townChanges += best
        return added
    }

    /**
     * What could go up on lot [i] next, in place of [b] if it's there: the next
     * rung's buildings that the zone's density, the year, the utilities, the
     * lot's appeal [pull] and its land value allow, and that the town has the
     * people to staff.
     */
    private fun choices(b: Building?, i: Int, zone: Byte, pull: Int): List<BuildingType> {
        val rung = when {
            b == null -> BuildingType.rung(zone, 1)
            // Rezoned for something else, it waits to come down (see [replaceNonconforming]).
            b.type.zone != zone -> return emptyList()
            // Rural land zoned for more: its like on the town's own ladder.
            b.type.density == Density.RURAL && heightAt(i) != Density.RURAL -> BuildingType.rung(zone, b.type.stage)
            else -> b.type.next
        }
        if (rung.isEmpty() || map.brownfield[i].toInt() != 0) return emptyList()
        val value = map.landValue[i].toInt() and 0xff
        // Rural lots grow rural buildings and nothing else does; the densest that's let in comes first.
        val height = heightAt(i)
        val rural = height == Density.RURAL
        return rung.filter { t ->
            (t.density == Density.RURAL) == rural && Density.rank(t.density) <= Density.rank(height) && t.year <= year && pull >= t.appeal &&
                // A tower needs ladders that reach it.
                (t.density != Density.TOWER || (map.ladderCover[i].toInt() and 0xff) >= Balance.TOWER_LADDER) &&
                // No heavy industry where the district won't have it.
                (t.zone != Zone.INDUSTRIAL || t.stage <= Balance.LIGHT_INDUSTRY || districtAt(i)?.lightIndustry != true) &&
                // Industry and farms go where they're let; homes and shops go up where the land's dear enough to pay for them.
                (zone == Zone.INDUSTRIAL || zone == Zone.FARMLAND || value >= t.value) &&
                // Farms, woodlots and mines by what's under the lot.
                (zone != Zone.FARMLAND || Land.fits(t, map.terrain[i], map.resource[i])) &&
                (!t.needsPower || map.powered[i]) && (!t.needsWater || map.watered[i]) && (!t.needsSewer || map.sewered[i]) &&
                // Businesses can't grow into what the town hasn't the people to staff.
                (zone == Zone.RESIDENTIAL || !skillsShort(t)) &&
                (!t.large || assemblyAt(t, i, b) >= 0)
        }.sortedByDescending { Density.rank(it.density) }
    }

    /**
     * The top left lot of a block of [t]'s size taking in lot [i], where every
     * lot is zoned for it and holds nothing that can't come down; -1 if there's none.
     */
    private fun assemblyAt(t: BuildingType, i: Int, replacing: Building? = null): Int {
        val x = i % map.width
        val y = i / map.width
        for (dy in 0 until t.height) for (dx in 0 until t.width) {
            val ax = x - dx
            val ay = y - dy
            if (ax < 0 || ay < 0 || ax + t.width > map.width || ay + t.height > map.height) continue
            var ok = true
            forRect(ax, ay, ax + t.width - 1, ay + t.height - 1) { j ->
                val d = map.density[j]
                if (map.zone[j] != t.zone || Density.rank(d) < Density.rank(t.density) || (d == Density.RURAL) != (t.density == Density.RURAL) ||
                    map.brownfield[j].toInt() != 0
                ) ok = false
                // Another big building is in the way, unless it's the one this replaces and it fits inside.
                val there = buildings[map.building[j]]
                val inside = there != null && there.id == replacing?.id && there.x >= ax && there.y >= ay &&
                    there.x + there.type.width <= ax + t.width && there.y + there.type.height <= ay + t.height
                if (there != null && (there.type.large && !inside || there.underway > 0 || there.burning > 0)) ok = false
            }
            if (ok) return map.index(ax, ay)
        }
        return -1
    }

    /** Clears the lots for [t] around lot [i] and starts it going up. Returns the room it'll have. */
    private fun assemble(t: BuildingType, i: Int, replacing: Building? = null): Int {
        val at = assemblyAt(t, i, replacing)
        val ax = at % map.width
        val ay = at / map.width
        forRect(ax, ay, ax + t.width - 1, ay + t.height - 1) { j ->
            buildings[map.building[j]]?.let { removeBuilding(it) }
            clearTrees(j)
            townChanges += j
        }
        addBuilding(t, ax, ay, rng.nextInt(1000), underway = t.buildDays)
        networksChanged()
        return t.capacity
    }

    /** Pulls [b] down and starts [type] going up on its lot. Its people move out; a bigger works may make something else. */
    private fun rebuild(b: Building, type: BuildingType) {
        b.people?.let { departures += it.size }
        b.people = null
        if (type.zone == Zone.INDUSTRIAL) b.kind = chooseKind(type.capacity)
        b.type = type
        b.age = 0
        b.underway = type.buildDays
        sites += b.id
        stamp(b)
    }

    /** Buildings going up, by id. */
    private val sites = LinkedHashSet<Int>()

    /**
     * Every site moves on a day, every other day while it's freezing. Those
     * finished take in their people or open for work.
     */
    private fun buildDay() {
        if (sites.isEmpty()) return
        if (weather.temperature <= 0 && day % 2 == 0) return
        var done: ArrayList<Building>? = null
        for (id in sites) {
            val b = buildings[id] ?: continue
            val before = sitePhase(b)
            b.underway--
            if (b.underway <= 0) {
                b.underway = 0
                (done ?: ArrayList<Building>().also { done = it }) += b
            } else if (sitePhase(b) != before) {
                stamp(b)
                forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { townChanges += it }
            }
        }
        // In order of id, so a loaded town finishes them in the same order as one that wasn't saved.
        for (b in done?.sortedBy { it.id } ?: return) {
            sites.remove(b.id)
            b.age = 0
            b.built = monthNow
            stamp(b)
            fitHousehold(b)
            forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { townChanges += it }
        }
    }

    /** Shrinks the least attractive building a stage, or clears its lot. Returns the capacity it took away. */
    private fun shrinkOnce(zone: Byte): Int {
        val lots = zoneLots(zone)
        var worst = -1
        var worstScore = Int.MAX_VALUE
        repeat(Balance.CANDIDATES) {
            if (lots.isEmpty()) return@repeat
            val i = lots[rng.nextInt(lots.size)]
            val b = buildings[map.building[i]] ?: return@repeat
            if (b.underway > 0) return@repeat
            val empty = b.people?.empty == true
            val score = attraction(i, zone) - if (empty) Balance.EMPTY_SHRINK else 0
            if (score < worstScore) {
                worstScore = score
                worst = i
            }
        }
        if (worst < 0) return 0
        return shrink(buildings[map.building[worst]]!!)
    }

    /**
     * Brings a building down a rung, or clears its lot at the bottom. A site is
     * given up. A building on more than one lot goes back to one on its first.
     */
    private fun shrink(b: Building): Int {
        // What the zoning no longer allows comes right down.
        val previous = if (conforms(b)) b.type.previous else null
        val removed: Int
        when {
            previous == null || b.underway > 0 -> {
                removed = b.type.capacity
                // Works closing for good leave their land fouled.
                if ((b.type.zone == Zone.INDUSTRIAL || b.type == BuildingType.MINE || b.type == BuildingType.COLLIERY) &&
                    b.underway == 0 && year >= Balance.BROWNFIELD_FROM
                ) {
                    forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { map.brownfield[it] = 1 }
                }
                removeBuilding(b)
                networksChanged()
                forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { townChanges += it }
                return removed
            }
            b.type.large -> {
                removed = b.type.capacity - previous.capacity
                removeBuilding(b)
                forRect(b.x, b.y, b.x + b.type.width - 1, b.y + b.type.height - 1) { townChanges += it }
                addBuilding(previous, b.x, b.y, b.variant)
                networksChanged()
                return removed
            }
            else -> {
                removed = b.type.capacity - previous.capacity
                b.type = previous
                b.age = 0
                stamp(b)
                fitHousehold(b)
            }
        }
        townChanges += map.index(b.x, b.y)
        return removed
    }

    /** Buildings that need power and have lost it come down a stage now and then. */
    private fun powerCuts() {
        val out = buildings.values.filter { it.underway == 0 && it.type.needsPower && !map.powered[map.index(it.x, it.y)] }
        for (b in out) if (rng.nextInt(3) == 0) shrink(b)
    }

    private val lotCache = arrayOfNulls<IntArray>(Zone.COUNT)

    /** Set when the player changes the map, since only the player changes zones. */
    private var zonesChanged = true

    /** Every tile zoned [zone], worked out again after the player changes the map. */
    private fun zoneLots(zone: Byte): IntArray {
        if (zonesChanged) {
            zonesChanged = false
            for (z in 1 until Zone.COUNT) lotCache[z] = null
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
        val comms = m.comms[i].toInt()
        // A business pays the racketeers, or goes elsewhere.
        val shakedown = (m.rackets[i].toInt() and 0xff) / Balance.RACKETS_APPEAL
        val pollution = m.pollution[i].toInt() and 0xff
        var score = if (m.powered[i]) 10 else 0
        when (zone) {
            Zone.RESIDENTIAL -> {
                var industry = false
                around(x, y, 2) { j, _ -> if (buildings[m.building[j]]?.type?.zone == Zone.INDUSTRIAL) industry = true }
                score += 35 + value / 3 - crime / 5 - pollution / 3 - (if (industry) 10 else 0) - (m.noise[i].toInt() and 0xff) / 2
                if (greenRoof(i)) score += Balance.GREEN_ROOF_APPEAL
                score -= commutePenalty(m.commute[i].toInt() and 0xff)
                // Mains water and the sewer are a draw at first and expected later; a well in grimy ground is never wanted.
                score += amenity(m.watered[i], Balance.MAINS_APPEAL, Balance.MAINS_FADES, Balance.MAINS_EXPECTED_BY, Balance.MAINS_EXPECTED)
                if (!m.watered[i] && m.grimeLevel(i) >= 2) score -= Balance.BAD_WELL
                score += amenity(m.sewered[i], Balance.SEWER_APPEAL, Balance.SEWER_FADES, Balance.SEWER_EXPECTED_BY, Balance.SEWER_EXPECTED)
                score += amenity(m.powered[i], 0, Balance.POWER_FADES, Balance.POWER_EXPECTED_BY, Balance.POWER_EXPECTED)
                // A phone in the house, and in time the internet.
                score += amenity(comms >= Phone.SERVICE_PHONE, Balance.PHONE_APPEAL, 1930, 1960, Balance.PHONE_NEEDED)
                score += amenity(comms >= Phone.SERVICE_BROADBAND, Balance.BROADBAND_APPEAL, 1995, 2010, Balance.BROADBAND_NEEDED)
                // The well off ask more of a place; the poor put up with more.
                val home = buildings[m.building[i]]?.people
                when (home?.wealth ?: chooseWealth(i)) {
                    Wealth.WELL_OFF -> score += value / 6 - pollution / 4 - crime / 6
                    Wealth.POOR -> score += pollution / 6
                }
                // Smog, summer heat in a paved district, and garbage piling up.
                score -= stats.smog / Balance.SMOG_APPEAL
                if (month in 5..7) score -= (m.heat[i].toInt() and 0xff) / Balance.HEAT_APPEAL
                if (buildings[m.building[i]]?.uncollected == true) score -= Balance.UNCOLLECTED_APPEAL
                // A home getting shabby with age, unless it's old enough to be heritage.
                buildings[m.building[i]]?.let { score += ageAppeal(it) }
                // People leave unhealthy homes.
                if (home != null && !home.empty && home.health < Balance.UNHEALTHY) score -= (Balance.UNHEALTHY - home.health) / 2
                // Nobody wants to live where the town's been clearing homes.
                score -= (m.upset[i].toInt() and 0xff) / Balance.UPSET_APPEAL
            }
            Zone.COMMERCIAL -> {
                var people = 0
                around(x, y, 6) { j, _ ->
                    val b = buildings[m.building[j]]
                    people += b?.people?.size ?: 0
                }
                score += 28 + value / 4 + min(people / 8, 30) - crime / 6 - pollution / 5 - shakedown
                score += amenity(comms >= Phone.SERVICE_PHONE, Balance.PHONE_APPEAL, 1910, 1950, Balance.PHONE_NEEDED)
                buildings[m.building[i]]?.let { score += ageAppeal(it) }
                // Passing trade.
                if (access[i] >= 0) score += min(Balance.PASSING_TRADE, traffic.lastFootfall[access[i]] / Balance.TRIPS_PER_PASSING_POINT)
                // Customers come to a town where the traffic moves.
                score += flowAppeal()
                // Stock from the town's own works and farms, or none at all if nothing can get in.
                buildings[m.building[i]]?.let { score += it.local * Balance.LOCAL_APPEAL / 100 }
                if (access[i] >= 0 && traffic.freightStuck[access[i]]) score -= Balance.FREIGHT_STUCK
            }
            Zone.OFFICE -> {
                // Dear land in the busy middle of town, close to the shops, clean and safe.
                var shops = 0
                around(x, y, 6) { j, _ -> if (buildings[m.building[j]]?.type?.zone == Zone.COMMERCIAL) shops++ }
                score += 24 + value / 3 + min(shops, 15) - crime / 5 - pollution / 4 + flowAppeal() - shakedown
                // Offices live on the telephone, and later the internet.
                score += amenity(comms >= Phone.SERVICE_PHONE, Balance.PHONE_APPEAL, 1905, 1940, Balance.PHONE_NEEDED)
                score += amenity(comms >= Phone.SERVICE_BROADBAND, Balance.BROADBAND_APPEAL, 1995, 2000, Balance.BROADBAND_NEEDED)
                // A college nearby, for the people and the ideas.
                if (colleges.any { abs(it.x - x) + abs(it.y - y) <= Balance.COLLEGE_REACH }) score += Balance.COLLEGE_OFFICES
                buildings[m.building[i]]?.let { score += ageAppeal(it) }
                if (access[i] >= 0) score += min(Balance.PASSING_TRADE, traffic.lastFootfall[access[i]] / Balance.TRIPS_PER_PASSING_POINT)
            }
            Zone.INDUSTRIAL -> {
                var water = false
                around(x, y, 3) { j, _ -> if (m.terrain[j] == Terrain.WATER) water = true }
                score += 50 + (if (water) 5 else 0) - crime / 8 - shakedown
                score += amenity(comms >= Phone.SERVICE_PHONE, Balance.PHONE_APPEAL, 1910, 1950, Balance.PHONE_NEEDED)
                if (access[i] >= 0 && traffic.freightStuck[access[i]]) score -= Balance.FREIGHT_STUCK
                if (nearTerminal[i]) score += Balance.TERMINAL_APPEAL
                // A works that gets what it needs in town does better.
                buildings[m.building[i]]?.let { score += it.local * Balance.LOCAL_APPEAL / 100 }
            }
            Zone.FARMLAND -> {
                score += Balance.FARMLAND_APPEAL - crime / 8
                if (access[i] >= 0 && traffic.freightStuck[access[i]]) score -= Balance.FREIGHT_STUCK
                // Selling in town rather than sending it all away.
                buildings[m.building[i]]?.let { score += it.local * Balance.LOCAL_APPEAL / 100 }
            }
        }
        val stigma = if (zone == Zone.RESIDENTIAL) (m.floodMemory[i].toInt() and 0xff) / Balance.STIGMA_APPEAL else 0
        // A district's lower taxes draw, its higher ones put off; works find a pollution limit dear.
        districtAt(i)?.let {
            score -= it.tax[District.taxIndex(zone)] * Balance.DISTRICT_TAX_APPEAL
            if (it.cleanWorks && zone == Zone.INDUSTRIAL) score -= Balance.CLEAN_WORKS_APPEAL
        }
        return score - floodPenalty(i) - stigma
    }

    /**
     * What a building's age does for it: past [Balance.WORN_YEARS] it gets
     * shabby, but from [Balance.HERITAGE_FROM] a solid one put up before
     * [Balance.HERITAGE_BEFORE] is heritage, and wanted for it.
     */
    private fun ageAppeal(b: Building): Int {
        if (isHeritage(b)) return Balance.HERITAGE_APPEAL
        val years = (monthNow - b.built) / 12
        return if (years <= Balance.WORN_YEARS) 0 else -min(Balance.WORN_APPEAL, (years - Balance.WORN_YEARS) * Balance.WORN_APPEAL / Balance.WORN_YEARS)
    }

    /** Whether [b] counts as heritage now. */
    fun isHeritage(b: Building): Boolean =
        year >= Balance.HERITAGE_FROM && b.type.heritage && b.underway == 0 && 1900 + b.built / 12 < Balance.HERITAGE_BEFORE

    /** How much a long commute puts people off, from the commute layer's half minutes. */
    private fun commutePenalty(c: Int): Int = when (c) {
        0 -> 0
        255 -> Balance.NO_COMMUTE
        else -> min(Balance.LONG_COMMUTE, max(0, (c - 1) / 2 - Balance.FINE_COMMUTE) / 2)
    }

    /** Appeal a lot loses while it stands in floodwater. */
    private fun floodPenalty(i: Int): Int = if ((map.flood[i].toInt() and 0xff) >= Balance.FLOODED) Balance.FLOOD_APPEAL else 0

    /** How much a tile soaks up pollution: 2 for park or woods, 1 for street trees. */
    private fun greenWeight(i: Int): Int = when {
        map.terrain[i] == Terrain.TREES || map.buildingType[i].toInt() - 1 == BuildingType.PARK.ordinal -> 2
        map.streetTrees[i].toInt() != 0 -> 1
        else -> 0
    }

    /** [amount] of pollution from ([sx], [sy]) reaching tile [j]: less of it if a belt of park or woods lies between. */
    private fun pastBelt(sx: Int, sy: Int, j: Int, amount: Int): Int {
        val tx = j % map.width
        val ty = j / map.width
        val steps = max(abs(tx - sx), abs(ty - sy))
        for (k in 1 until steps) {
            val x = sx + (tx - sx) * k / steps
            val y = sy + (ty - sy) * k / steps
            val i = map.index(x, y)
            if (map.terrain[i] == Terrain.TREES || map.buildingType[i].toInt() - 1 == BuildingType.PARK.ordinal) return amount * Balance.BELT_PASSES / 100
        }
        return amount
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

    /** Pollution spreads from each building that makes it, and from busy roads, fading with distance. */
    private fun updatePollution() {
        val m = map
        val field = IntArray(m.size)
        for (b in buildings.values) {
            // A power station smokes by what it's making, never less than when it's idling.
            val p = if (b.type == BuildingType.INCINERATOR) {
                Balance.INCINERATOR_FUMES * maxOf(Balance.IDLE_FUMES, (incinerated[b.id] ?: 0) * 100 / Balance.INCINERATOR_TAKES) / 100
            } else if (Generation.fumes(b.type) > 0 && b.outage == 0) {
                val load = (stationOutput(b).toLong() * 100 / Generation.capacity(b.type)).toInt()
                // Scrubbers let out only some of it.
                Generation.fumes(b.type) * maxOf(Balance.IDLE_FUMES, load) / 100 * (if (b.scrubbed) Balance.SCRUBBED_SHARE else 100) / 100
            } else if ((b.type.zone == Zone.INDUSTRIAL || b.type.zone == Zone.FARMLAND) && districtAt(m.index(b.x, b.y))?.cleanWorks == true) {
                // Works under a pollution limit give off less.
                b.type.pollution * Balance.CLEAN_WORKS_SHARE / 100
            } else b.type.pollution
            if (p == 0 || b.underway > 0) continue
            val cx = b.x + b.type.width / 2
            val cy = b.y + b.type.height / 2
            around(cx, cy, POLLUTION_REACH) { j, d -> field[j] += pastBelt(cx, cy, j, p * 4 * (POLLUTION_REACH + 1 - d) / (POLLUTION_REACH + 1)) }
        }
        // Fumes from last month's traffic, along the road and a little either side. Buses count for several cars.
        val fumes = Fumes.level(year)
        for (i in 0 until m.size) {
            if (m.road[i] == Road.NONE) continue
            val vehicles = traffic.lastVolume[i] + traffic.lastBusVolume[i] * Balance.BUS_FUMES / Traffic.BUS_RIDERS
            // Traffic crawling and waiting gives off more for the distance.
            val idling = 100 + min(Balance.IDLE_MOST, (m.congestion[i].toInt() and 0xff) * Balance.IDLE_MOST / 255) + traffic.junctionWait(i, RoadType.of(m.road[i])!!) * Balance.IDLE_PER_WAIT
            val p = vehicles * fumes / 100 * Balance.FUMES_PER_HUNDRED / 100 * idling / 100
            if (p == 0) continue
            around(i % m.width, i / m.width, Balance.FUMES_REACH) { j, d -> field[j] += pastBelt(i % m.width, i / m.width, j, p * (Balance.FUMES_REACH + 1 - d) / (Balance.FUMES_REACH + 1)) }
        }
        // Parks, woods and street trees nearby take some of it up.
        val sink = SummedArea(m.width, m.height) { j -> greenWeight(j) }
        val r = Balance.GREEN_SINK_REACH
        for (i in 0 until m.size) {
            if (field[i] == 0) continue
            val taken = min(Balance.GREEN_SINK_MOST, sink.around(i % m.width, i / m.width, r) * Balance.GREEN_SINK / 2)
            m.pollution[i] = min(255, field[i] * (100 - taken) / 100).toByte()
        }
        for (i in 0 until m.size) if (field[i] == 0) m.pollution[i] = 0
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
        var farmJobs = 0
        var officeJobs = 0
        var otherJobs = 0
        var health = 0L
        var spending = 0L
        s.children = 0; s.adults = 0; s.elderly = 0; s.workers = 0; s.emptyHomes = 0; s.emptyRoom = 0
        s.workersBy.fill(0); s.byWealth.fill(0)
        val jobsBy = LongArray(Education.LEVELS)
        s.sites = 0; s.homesComing = 0; s.shopJobsComing = 0; s.industryJobsComing = 0; s.farmJobsComing = 0; s.officeJobsComing = 0
        for (b in buildings.values) {
            val c = b.type.capacity
            if (b.underway > 0) {
                s.sites++
                when (b.type.zone) {
                    Zone.RESIDENTIAL -> s.homesComing += c
                    Zone.COMMERCIAL -> s.shopJobsComing += c
                    Zone.OFFICE -> s.officeJobsComing += c
                    Zone.INDUSTRIAL -> s.industryJobsComing += c
                    Zone.FARMLAND -> s.farmJobsComing += c
                }
                continue
            }
            when (b.type.zone) {
                Zone.RESIDENTIAL -> residents += b.people?.size ?: 0
                Zone.COMMERCIAL -> if (b.type.office) officeJobs += c else shopJobs += c
                Zone.OFFICE -> officeJobs += c
                Zone.INDUSTRIAL -> industryJobs += c
                Zone.FARMLAND -> farmJobs += c
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
        var onMains = 0
        var onSewer = 0
        var powered = 0
        var buildingsCount = 0
        s.downtown = 0
        s.highSchools = 0
        for (b in buildings.values) {
            val i = map.index(b.x, b.y)
            val people = b.people?.size ?: 0
            if (map.watered[i]) onMains += people
            if (map.sewered[i]) onSewer += people
            if (b.type != BuildingType.PARK) {
                buildingsCount++
                if (map.powered[i]) powered++
            }
            if ((b.type.zone == Zone.COMMERCIAL || b.type.zone == Zone.OFFICE) && Density.rank(b.type.density) >= Density.rank(Density.HIGH) && b.underway == 0) s.downtown++
            if (b.type == BuildingType.HIGH_SCHOOL) s.highSchools++
        }
        s.onMains = if (residents == 0) 0 else onMains * 100 / residents
        s.onSewer = if (residents == 0) 0 else onSewer * 100 / residents
        s.powered = if (buildingsCount == 0) 0 else powered * 100 / buildingsCount
        var land = 0
        var built = 0
        for (i in 0 until map.size) {
            if (!nearRoad[i] || map.terrain[i] == Terrain.WATER || map.road[i] != Road.NONE || map.rail[i] != Rail.NONE) continue
            land++
            if (map.building[i] != 0) built++
        }
        s.landBuilt = if (land == 0) 0 else built * 100 / land
        s.keptUp = keptUp()
        s.shopJobs = shopJobs
        s.industryJobs = industryJobs
        s.farmJobs = farmJobs
        s.officeJobs = officeJobs
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
            (if (linkedTerminals.isNotEmpty()) Balance.TERMINAL_EXPORTS else if (railFreight) Balance.RAIL_EXPORTS else 1.0) *
            Balance.PORT_EXPORTS[seaTier] * (if (portByRail()) Balance.PORT_RAIL_EXPORTS else 1.0) * Economy.market(year, month) / 100.0
        val jobs = s.shopJobs + s.industryJobs + s.farmJobs + s.officeJobs + s.otherJobs
        // What the town brings in that it could make: from the land, and from the works.
        var fromLand = 0.0
        var fromWorks = 0.0
        for (g in Good.entries) {
            if (g.fromLand) fromLand += s.goodsImported[g.ordinal] / Balance.LOADS_PER_FARM_JOB
            else fromWorks += s.goodsImported[g.ordinal] / Balance.LOADS_PER_WORKS_JOB
        }
        // What's going up already counts against demand.
        val industryGap = market + fromWorks - s.industryJobs
        s.industryDemand = taxed(industryGap - s.industryJobsComing, industrialTax)
        val farmGap = market * Balance.FARM_MARKET + fromLand - s.farmJobs
        s.farmDemand = taxed(farmGap - s.farmJobsComing, industrialTax)
        // Office work grows with the town and with the century.
        val perHundred = Balance.OFFICES_1900 + (Balance.OFFICES_2000 - Balance.OFFICES_1900) * (years / 100.0).coerceIn(0.0, 1.0)
        val officeGap = Balance.OFFICE_BASE + s.population * perHundred / 100.0 + airports.sumOf { Balance.AIR_OFFICES[it.type.airTier] * fit(it) / 100 } - s.officeJobs
        s.officeDemand = taxed(officeGap - s.officeJobsComing, commercialTax)
        // The shops answer what people spend, more the better off they are.
        val shopGap = s.spending / Balance.RESIDENTS_PER_SHOP_JOB - s.shopJobs.toDouble()
        s.commercialDemand = taxed(shopGap - s.shopJobsComing, commercialTax)
        val settlers = (Balance.SETTLERS + Balance.SETTLERS_PER_RESIDENT * s.population) * (if (railPassengers) Balance.RAIL_SETTLERS else 1.0) * Balance.AIR_SETTLERS[airTier] *
            (100 - displacedCut()) / 100.0
        // Homes for the people the jobs need, children and the elderly with them.
        val workersPerResident = if (s.population == 0) Balance.LABOUR_SHARE else (s.workers.toDouble() / s.population).coerceIn(0.25, 0.6)
        val seekers = jobs / workersPerResident + settlers - s.population
        s.homeSeekers = taxed(seekers, residentialTax)
        // The empty homes take what they can of it before anyone builds.
        val homeGap = seekers - s.emptyRoom
        s.residentialDemand = taxed(homeGap - s.homesComing, residentialTax)
        // A town with too much comes down; one with enough on the way only stops building.
        quota[Zone.RESIDENTIAL.toInt()] = cap(growOrShrink(taxed(homeGap, residentialTax), s.residentialDemand), s.population)
        quota[Zone.COMMERCIAL.toInt()] = cap(growOrShrink(taxed(shopGap, commercialTax), s.commercialDemand), s.shopJobs)
        quota[Zone.INDUSTRIAL.toInt()] = cap(growOrShrink(taxed(industryGap, industrialTax), s.industryDemand), s.industryJobs)
        quota[Zone.FARMLAND.toInt()] = cap(growOrShrink(taxed(farmGap, industrialTax), s.farmDemand), s.farmJobs)
        quota[Zone.OFFICE.toInt()] = cap(growOrShrink(taxed(officeGap, commercialTax), s.officeDemand), s.officeJobs)
    }

    /** What a zone does this month: shrink by [standing] if what stands is already too much, else grow by [coming], or not at all. */
    private fun growOrShrink(standing: Int, coming: Int): Int = if (standing < 0) standing else max(0, coming)

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
        var offices = 0.0
        var works = 0.0
        var police = 0
        var fire = 0
        var parks = 0
        var plants = 0.0
        var stations = 0
        var yards = 0
        var terminals = 0
        var waterworks = 0.0
        var schools = 0.0
        var care = 0.0
        var fireExtra = 0.0
        var phoneUpkeep = 0.0
        var policeExtra = 0.0
        val days = daysIn(if (month == 0) 11 else month - 1, year).toDouble()
        for (b in buildings.values) {
            when (b.type) {
                BuildingType.SCHOOL -> schools += Balance.SCHOOL_UPKEEP
                BuildingType.HIGH_SCHOOL -> schools += Balance.HIGH_SCHOOL_UPKEEP
                BuildingType.CLINIC -> care += Balance.CLINIC_UPKEEP
                BuildingType.COOLING_CENTRE -> care += Balance.COOLING_UPKEEP
                BuildingType.HOSPITAL -> care += Balance.HOSPITAL_UPKEEP
                BuildingType.NURSING_HOME -> care += Balance.NURSING_UPKEEP
                BuildingType.AMBULANCE_STATION -> care += Balance.AMBULANCE_UPKEEP
                BuildingType.LIBRARY -> schools += Balance.LIBRARY_UPKEEP
                BuildingType.COLLEGE -> schools += Balance.COLLEGE_UPKEEP
                BuildingType.VOLUNTEER_HALL -> fireExtra += Balance.VOLUNTEER_UPKEEP
                BuildingType.EXCHANGE -> phoneUpkeep += Balance.EXCHANGE_UPKEEP
                BuildingType.CELL_TOWER -> phoneUpkeep += Balance.MAST_UPKEEP
                BuildingType.LADDER_COMPANY -> fireExtra += Balance.LADDER_UPKEEP
                BuildingType.POLICE_HQ -> policeExtra += Balance.HQ_UPKEEP
                BuildingType.COURTHOUSE -> policeExtra += Balance.COURT_UPKEEP
                BuildingType.JAIL -> policeExtra += Balance.JAIL_UPKEEP
                else -> {}
            }
            waterworks += when (b.type) {
                BuildingType.PUMPING_STATION -> Balance.PUMP_UPKEEP
                BuildingType.WELL_FIELD -> Balance.WELL_UPKEEP
                BuildingType.WATER_TOWER -> Balance.TOWER_UPKEEP
                BuildingType.OUTFALL -> Balance.OUTFALL_UPKEEP
                BuildingType.SEWAGE_WORKS -> Balance.SEWAGE_WORKS_UPKEEP
                BuildingType.TREATMENT_PLANT -> Balance.TREATMENT_UPKEEP
                BuildingType.STORM_POND -> Balance.POND_UPKEEP
                BuildingType.STORM_OUTFALL -> Balance.STORM_OUTFALL_UPKEEP
                else -> 0.0
            }
            // A site pays nothing until it's built.
            if (b.underway > 0) continue
            // Shops and works pay nothing for the days they were shut by floods.
            val open = 1.0 - min(b.closedDays.toDouble(), days) / days
            b.closedDays = 0
            val worth = (0.5 + (map.landValue[map.index(b.x, b.y)].toInt() and 0xff) / 200.0) * open
            when {
                b.type.zone == Zone.RESIDENTIAL -> homes += b.type.capacity * worth * Demography.TAX_BY_WEALTH[b.people?.wealth ?: Wealth.MIDDLE] / 100.0 * taxOf(b, residentialTax) *
                    (if (districtAt(map.index(b.x, b.y))?.rentControl == true) Balance.RENT_CONTROL_TAX / 100.0 else 1.0)
                b.type.office -> offices += b.type.capacity * worth * taxOf(b, commercialTax)
                b.type.zone == Zone.COMMERCIAL -> {
                    // A shop that has to bring in what it sells makes less, and one that can't get stock makes little.
                    val margin = if (shortOfStock(b)) Balance.NO_STOCK else 100 - (100 - b.local) * Balance.IMPORT_DRAG / 100
                    shops += b.type.capacity * worth * margin / 100.0 * taxOf(b, commercialTax)
                }
                b.type.zone == Zone.INDUSTRIAL || b.type.zone == Zone.FARMLAND -> works += b.type.capacity * worth * taxOf(b, industrialTax)
                b.type == BuildingType.POLICE_STATION -> police++
                b.type == BuildingType.FIRE_STATION -> fire++
                b.type == BuildingType.PARK -> parks++
                Generation.station(b.type) || b.type == BuildingType.SUBSTATION -> plants += when (b.type) {
                    BuildingType.OIL_PLANT -> Balance.OIL_PLANT_UPKEEP
                    BuildingType.GAS_PLANT -> Balance.GAS_PLANT_UPKEEP
                    BuildingType.HYDRO_PLANT -> Balance.HYDRO_PLANT_UPKEEP
                    BuildingType.WIND_FARM -> Balance.WIND_UPKEEP
                    BuildingType.SOLAR_FARM -> Balance.SOLAR_UPKEEP
                    BuildingType.BATTERY -> Balance.BATTERY_UPKEEP
                    BuildingType.RIVER_TURBINE -> Balance.RIVER_TURBINE_UPKEEP
                    BuildingType.TIDAL_TURBINE -> Balance.TIDAL_UPKEEP
                    BuildingType.OFFSHORE_WIND -> Balance.OFFSHORE_UPKEEP
                    BuildingType.NUCLEAR_PLANT -> Balance.NUCLEAR_PLANT_UPKEEP
                    BuildingType.SUBSTATION -> Balance.SUBSTATION_UPKEEP
                    else -> Balance.PLANT_UPKEEP
                } + fuelCost(b) + if (b.scrubbed) Balance.SCRUBBER_UPKEEP else 0.0
                b.type.station -> stations++
                b.type.terminal -> terminals++
                b.type.yard -> yards++
            }
        }
        // Each building's tax is in its total already, its district's rate and all.
        s.residentialIncome = (homes * Balance.RESIDENT_TAX).roundToLong()
        s.commercialIncome = (shops * Balance.JOB_TAX).roundToLong()
        s.officeIncome = (offices * Balance.JOB_TAX * Balance.OFFICE_TAX).roundToLong()
        s.industrialIncome = (works * Balance.JOB_TAX).roundToLong()
        var roads = 0.0
        var lines = 0
        var highLines = 0
        var junctions = 0.0
        var track = 0.0
        var cables = 0.0
        var phoneLines = 0.0
        for (i in 0 until map.size) {
            val bridge = if (map.terrain[i] == Terrain.WATER) map.bridgeKind(i)?.upkeep ?: Balance.BRIDGE_UPKEEP else 1.0
            val road = RoadType.of(map.road[i])
            if (road != null) roads += road.upkeep * bridge
            if (map.phone[i] == Phone.COPPER) phoneLines += Balance.COPPER_UPKEEP
            else if (map.phone[i] == Phone.FIBRE) phoneLines += Balance.FIBRE_UPKEEP
            if (map.cable(i)) cables += (if (map.power[i] == Power.HIGH) Balance.HIGH_CABLE_UPKEEP else Balance.CABLE_UPKEEP)
            else if (map.power[i] == Power.LINE) lines++
            else if (map.power[i] == Power.HIGH) highLines++
            junctions += Junction.upkeep(map.control[i])
            if (map.rail[i] != Rail.NONE) track += Balance.RAIL_UPKEEP * bridge
            // Tunnels: the fans and pumps.
            if (map.lowRoad[i].toInt() != 0) roads += Balance.ROAD_TUNNEL_UPKEEP
            if (map.lowRail[i].toInt() != 0) track += Balance.RAIL_TUNNEL_UPKEEP
            waterworks += (map.waterPipe[i] + map.sewerPipe[i] + map.stormPipe[i] + map.bank[i]) * Balance.PIPE_UPKEEP
        }
        s.waterUpkeep = waterworks.roundToLong()
        s.phoneUpkeep = (phoneUpkeep + phoneLines).roundToLong()
        s.roadUpkeep = (roads + lines * Balance.LINE_UPKEEP + highLines * Balance.HIGH_LINE_UPKEEP + cables + junctions).roundToLong()
        s.railUpkeep = (track + stations * Balance.STATION_UPKEEP + yards * Balance.YARD_UPKEEP + terminals * Balance.TERMINAL_UPKEEP).roundToLong()
        s.powerUpkeep = plants.roundToLong()
        s.policeUpkeep = ((police * Balance.POLICE_UPKEEP + policeExtra) * policeFunding / 100).roundToLong()
        s.fireUpkeep = ((fire * Balance.FIRE_UPKEEP + fireExtra) * fireFunding / 100).roundToLong()
        s.parkUpkeep = (parks * Balance.PARK_UPKEEP * parkFunding / 100).roundToLong()
        // Rides from stops with free fares bring in nothing.
        s.fareIncome = (max(0, traffic.boardings() - traffic.lastFreeBoardings) * Balance.FARE).roundToLong()
        // Dues on the loads through the ports, and on visitors off the ships.
        s.portLoads = traffic.lastPortFreight.sum()
        // Air freight: what the airports can take of the goods sent away.
        s.airLoads = min(airports.sumOf { Balance.AIR_CARGO[it.type.airTier] * fit(it) / 100 }, s.goodsExported.sum())
        s.duesIncome = (s.portLoads * Balance.PORT_DUE + s.visitorsBy[Tourism.SEA] * Balance.SEA_VISITOR_DUE +
            s.visitorsBy[Tourism.AIR] * Balance.LANDING_FEE + s.airLoads * Balance.AIR_CARGO_FEE).roundToLong()
        s.tolls = traffic.lastTolls
        s.tollIncome = s.tolls.toLong() * tollRate / 100
        s.portUpkeep = (ports.sumOf { Balance.PORT_UPKEEP[it.type.portTier] } +
            buildings.values.filter { it.type.airport }.sumOf { Balance.AIR_UPKEEP[it.type.airTier] }).roundToLong()
        s.income = s.residentialIncome + s.commercialIncome + s.industrialIncome + s.officeIncome + s.fareIncome + s.duesIncome + s.tollIncome
        var tramTiles = 0
        var wires = 0
        var tunnels = 0
        var stops = 0
        for (i in 0 until map.size) {
            tramTiles += map.tram[i]
            wires += map.wire[i]
            tunnels += map.subway[i]
            if (map.stop[i].toInt() != 0) stops++
        }
        var streetTrees = 0
        for (i in 0 until map.size) streetTrees += map.streetTrees[i]
        var garbage = streetTrees * Balance.STREET_TREE_UPKEEP
        for (b in buildings.values) garbage += when (b.type) {
            BuildingType.DUMP -> Balance.DUMP_UPKEEP
            BuildingType.INCINERATOR -> Balance.INCINERATOR_UPKEEP
            BuildingType.RECYCLING -> Balance.RECYCLING_UPKEEP
            else -> 0.0
        }
        // Cool and green roofs, kept up on every building in their districts.
        for (b in buildings.values) {
            if (b.underway > 0) continue
            val i = map.index(b.x, b.y)
            if (greenRoof(i)) garbage += Balance.GREEN_ROOF_UPKEEP * b.type.width * b.type.height
            else if (coolRoof(i)) garbage += Balance.COOL_ROOF_UPKEEP * b.type.width * b.type.height
        }
        s.environmentUpkeep = garbage.roundToLong()
        var transitWorks = 0.0
        for (b in buildings.values) transitWorks += when (b.type) {
            BuildingType.TRAM_DEPOT -> Balance.DEPOT_UPKEEP
            BuildingType.BUS_GARAGE -> Balance.GARAGE_UPKEEP
            BuildingType.SUBWAY_STATION -> Balance.SUBWAY_STATION_UPKEEP
            else -> 0.0
        }
        var vehicles = 0.0
        for (line in this.lines) vehicles += line.vehicles * if (line.tram) Balance.TRAM_VEHICLE_UPKEEP else Balance.BUS_VEHICLE_UPKEEP
        var lanes = 0
        for (i in 0 until map.size) lanes += map.lane[i]
        s.transitUpkeep = (tramTiles * Balance.TRAM_TRACK_UPKEEP + wires * Balance.WIRE_UPKEEP + tunnels * Balance.TUNNEL_UPKEEP + stops * Balance.STOP_UPKEEP +
            transitWorks + vehicles + lanes * Balance.LANE_UPKEEP).roundToLong()
        s.floodCost = floodBill
        floodBill = 0
        s.repairCost = repairBill
        repairBill = 0
        s.disasterCost = disasterBill
        disasterBill = 0
        s.schoolUpkeep = (schools * schoolFunding / 100).roundToLong()
        s.healthUpkeep = (care * healthFunding / 100).roundToLong()
        s.upkeep = s.roadUpkeep + s.railUpkeep + s.waterUpkeep + s.powerUpkeep + s.policeUpkeep + s.fireUpkeep + s.parkUpkeep + s.floodCost + s.schoolUpkeep + s.healthUpkeep + s.repairCost + s.transitUpkeep + s.environmentUpkeep + s.disasterCost + s.phoneUpkeep + s.portUpkeep
        funds += s.income - s.upkeep
    }

    /** This month's numbers into the history. */
    private fun record() {
        var crime = 0L
        var rackets = 0L
        var pollution = 0L
        var value = 0L
        var built = 0
        var land = 0
        for (i in 0 until map.size) {
            if (map.building[i] != 0) {
                crime += map.crime[i].toInt() and 0xff
                rackets += map.rackets[i].toInt() and 0xff
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
        s.rackets = if (built == 0) 0 else (rackets / built).toInt()
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
        // The first four zones; farmland's since version 10.
        for (k in 0 until 4) w.int(quota[k])
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
        // Since version 6.
        w.layer(map.density)
        val going = buildings.values.filter { it.underway > 0 }
        w.count(going.size)
        for (b in going) { w.int(b.id); w.int(b.underway) }
        for (v in intArrayOf(s.sites, s.homesComing, s.shopJobsComing, s.industryJobsComing)) w.int(v)
        // Since version 7.
        w.int(era.ordinal)
        for (v in intArrayOf(s.onMains, s.onSewer, s.powered, s.downtown, s.highSchools, s.landBuilt)) w.int(v)
        for (a in arrayOf(map.roadLaid, map.waterLaid, map.sewerLaid, map.stormLaid, map.railLaid)) w.shorts(a)
        w.shorts(map.broken); w.layer(map.mending)
        w.count(buildings.size)
        for (b in buildings.values) { w.int(b.id); w.int(b.built); w.int(b.outage) }
        w.long(repairBill); w.long(s.repairCost)
        w.layer(map.brownfield)
        // Since version 8.
        w.layer(map.tram); w.layer(map.wire); w.layer(map.subway); w.layer(map.stop)
        for (a in arrayOf(map.tramLaid, map.wireLaid, map.subwayLaid)) w.shorts(a)
        traffic.writeTransit(w)
        w.long(s.fareIncome); w.long(s.transitUpkeep)
        for (v in s.byMode) w.int(v)
        // Since version 9.
        w.layer(map.streetTrees); w.layer(map.heat)
        val garbage = buildings.values.filter { it.fill > 0 || it.uncollected }
        w.count(garbage.size)
        for (b in garbage) { w.int(b.id); w.int(b.fill); w.bool(b.uncollected) }
        w.count(incinerated.size)
        for ((id, v) in incinerated.entries.sortedBy { it.key }) { w.int(id); w.int(v) }
        w.long(s.environmentUpkeep); w.long(s.disasterCost)
        for (v in intArrayOf(s.smog, s.waste, s.wasteCollected, s.dumpRoom)) w.int(v)
        w.long(s.powerCapacity); w.long(s.powerDemand); w.long(s.powerShort)
        w.bool(quakes); w.int(reliefFunding); w.long(disasterBill)
        for (v in intArrayOf(snowedIn, heatWaveDays, epidemicMonths, epidemicStrength)) w.int(v)
        w.bool(hadFlu)
        // Since version 10.
        w.layer(map.resource)
        val trading = buildings.values.filter { it.kind >= 0 || it.local != 0 }
        w.count(trading.size)
        for (b in trading) { w.int(b.id); w.int(b.kind); w.int(b.local) }
        for (v in kindPull) w.long(v.toRawBits())
        for (v in intArrayOf(s.farmJobs, s.farmJobsComing, s.farmDemand, quota[Zone.FARMLAND.toInt()])) w.int(v)
        for (a in arrayOf(s.goodsMade, s.goodsSold, s.goodsExported, s.goodsImported)) for (v in a) w.int(v)
        traffic.writeGoods(w)
        // Since version 11.
        w.int(quota[Zone.OFFICE.toInt()])
        for (v in intArrayOf(s.officeJobs, s.officeJobsComing, s.officeDemand)) w.int(v)
        w.long(s.officeIncome)
        // Since version 12.
        w.layer(map.junction); w.layer(map.control)
        // Since version 13.
        traffic.writeFlow(w); w.int(s.flow)
        // Since version 14.
        w.layer(map.lane)
        w.int(nextLineId)
        w.count(lines.size)
        for (line in lines) {
            w.int(line.id); w.bool(line.tram); w.int(line.vehicles)
            w.count(line.stops.size)
            for (t in line.stops) w.int(t)
        }
        // Since version 15.
        w.layer(map.district)
        w.int(nextDistrictId)
        w.count(districts.size)
        for (d in districts) d.writeTo(w)
        // Since version 17.
        val scrubbed = buildings.values.filter { it.scrubbed }
        w.count(scrubbed.size)
        for (b in scrubbed) w.int(b.id)
        // Since version 18: the cover of the services that came with it, worked out each month from the roads as they were.
        w.layer(map.ladderCover)
        w.layer(map.ambulanceCover)
        // Since version 19: crime by kind, and justice.
        w.layer(map.theft)
        w.layer(map.vice)
        w.layer(map.rackets)
        w.int(justice)
        w.int(prisoners)
        for (v in intArrayOf(stats.rackets, stats.offences, stats.arrests, stats.heard, stats.prisoners, stats.cells, stats.justice)) w.int(v)
        // Since version 20: power cable underground, and when lines went up.
        w.layer(map.buried)
        w.shorts(map.powerLaid)
        // Since version 21: the telephone.
        w.layer(map.phone)
        w.shorts(map.phoneLaid)
        w.layer(map.comms)
        for (v in longArrayOf(stats.phoneUpkeep, stats.withPhone.toLong(), stats.withBroadband.toLong(), stats.workingFromHome.toLong())) w.long(v)
        // Since version 22: what each power line carried, so the load's there on loading.
        val loaded = (0 until map.size).filter { grid.load[it] != 0 }
        w.count(loaded.size)
        for (i in loaded) { w.int(i); w.int(grid.load[i]) }
        // Since version 23: ports, ships and visitors.
        traffic.writePorts(w)
        w.count(shipRoutes.size)
        for (route in shipRoutes) {
            w.count(route.tiles.size)
            for (i in route.tiles) w.int(i)
            w.int(route.kind); w.int(route.ships)
        }
        for (v in longArrayOf(s.portUpkeep, s.duesIncome, s.tollIncome)) w.long(v)
        for (v in intArrayOf(s.portLoads, s.visitors, s.guests, s.rooms, *s.visitorsBy)) w.int(v)
        val hotels = buildings.values.filter { it.type == BuildingType.HOTEL && it.room > 0 }
        w.count(hotels.size)
        for (b in hotels) { w.int(b.id); w.int(b.served); w.int(b.room) }
        // Since version 24: kinds of bridge, tolls and closures.
        w.layer(map.bridge)
        w.layer(map.bridgeShut)
        w.int(tollRate)
        w.int(s.tolls)
        traffic.writeBridges(w)
        // And tunnels, and where trains go under ground.
        for (a in arrayOf(map.lowRoad, map.lowHeading, map.lowRail, map.portal)) w.layer(a)
        w.shorts(map.lowLaid)
        w.count(trainRoutes.size)
        for (t in trainRoutes) {
            val under = t.hidden.indices.filter { t.hidden[it] }
            w.count(under.size)
            for (k in under) w.int(k)
        }
        // Since version 25: air freight.
        w.int(s.airLoads)
        // Since version 26: the climate.
        w.int(climate.ordinal)
        // Since version 27: carbon, and this year's heat waves and floods.
        w.long(carbonTotal)
        for (v in longArrayOf(s.carbon, s.carbonPower, s.carbonTraffic, s.carbonWorks, s.carbonHeating)) w.long(v)
        for (v in intArrayOf(heatWavesThisYear, floodsThisYear, heatWavesLastYear, floodsLastYear)) w.int(v)
        // Since version 29: the neighbours' upset at clearings, and the people forced out.
        w.layer(map.upset)
        w.int(displaced)
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
            for (k in 0 until 4) c.quota[k] = r.int()
            val s = c.stats
            s.population = r.int(); s.workers = r.int(); s.shopJobs = r.int(); s.industryJobs = r.int()
            s.otherJobs = r.int(); s.unemployment = r.int(); s.residentialDemand = r.int(); s.commercialDemand = r.int()
            s.industryDemand = r.int(); s.crime = r.int(); s.pollution = r.int(); s.landValue = r.int()
            s.residentialIncome = r.long(); s.commercialIncome = r.long(); s.industrialIncome = r.long(); s.income = r.long()
            s.roadUpkeep = r.long(); s.powerUpkeep = r.long(); s.policeUpkeep = r.long(); s.fireUpkeep = r.long()
            s.parkUpkeep = r.long(); s.upkeep = r.long()
            c.weather.readFrom(r)
            c.history.readFrom(r, if (version >= 27) Series.entries.size else 8)
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
            }
            if (version >= 6) {
                r.layer(m.density)
                repeat(r.count()) {
                    val b = c.buildings[r.int()] ?: throw SaveError("a site for a building that isn't there")
                    b.underway = r.int()
                    c.sites += b.id
                    c.stamp(b)
                }
                s.sites = r.int(); s.homesComing = r.int(); s.shopJobsComing = r.int(); s.industryJobsComing = r.int()
            } else {
                // Before densities every zone built as high as medium does now.
                for (i in 0 until m.size) if (m.zone[i] != Zone.NONE) m.density[i] = Density.MEDIUM
            }
            if (version < 5) {
                // Older towns had no people as such, only room for them: each home is filled as newcomers would fill it.
                for (b in c.buildings.values.sortedBy { it.id }) c.fitHousehold(b)
                c.arrivals = 0
                c.census()
            }
            // Worked out now rather than on the first day, so a city loaded paused shows its power and water.
            c.updateNetworks()
            if (version >= 7) {
                c.era = Era.entries.getOrNull(r.int()) ?: throw SaveError("an era this version doesn't know")
                s.onMains = r.int(); s.onSewer = r.int(); s.powered = r.int(); s.downtown = r.int(); s.highSchools = r.int(); s.landBuilt = r.int()
                for (a in arrayOf(m.roadLaid, m.waterLaid, m.sewerLaid, m.stormLaid, m.railLaid)) r.shorts(a)
                r.shorts(m.broken); r.layer(m.mending)
                for (i in 0 until m.size) if (m.mendingDays(i) > 0) c.mendingTiles += i
                repeat(r.count()) {
                    val b = c.buildings[r.int()] ?: throw SaveError("the age of a building that isn't there")
                    b.built = r.int()
                    b.outage = r.int()
                    if (b.outage > 0) c.outages += b.id
                }
                c.repairBill = r.long(); s.repairCost = r.long()
                // Not saved; they follow from what was.
                s.keptUp = c.keptUp()
                s.greenTrips = c.greenTrips()
                r.layer(m.brownfield)
                if (version >= 8) {
                    r.layer(m.tram); r.layer(m.wire); r.layer(m.subway); r.layer(m.stop)
                    for (a in arrayOf(m.tramLaid, m.wireLaid, m.subwayLaid)) r.shorts(a)
                    c.traffic.readTransit(r)
                    s.fareIncome = r.long(); s.transitUpkeep = r.long()
                    for (k in s.byMode.indices) s.byMode[k] = r.int()
                }
                if (version >= 9) {
                    r.layer(m.streetTrees); r.layer(m.heat)
                    repeat(r.count()) {
                        val b = c.buildings[r.int()] ?: throw SaveError("garbage at a building that isn't there")
                        b.fill = r.int(); b.uncollected = r.bool()
                    }
                    repeat(r.count()) { c.incinerated[r.int()] = r.int() }
                    s.environmentUpkeep = r.long(); s.disasterCost = r.long()
                    s.smog = r.int(); s.waste = r.int(); s.wasteCollected = r.int(); s.dumpRoom = r.int()
                    s.powerCapacity = r.long(); s.powerDemand = r.long(); s.powerShort = r.long()
                    c.quakes = r.bool(); c.reliefFunding = r.int(); c.disasterBill = r.long()
                    c.snowedIn = r.int(); c.heatWaveDays = r.int(); c.epidemicMonths = r.int(); c.epidemicStrength = r.int()
                    c.hadFlu = r.bool()
                    c.traffic.snowedIn = c.snowedIn > 0
                }
                if (version >= 10) {
                    r.layer(m.resource)
                    repeat(r.count()) {
                        val b = c.buildings[r.int()] ?: throw SaveError("goods at a building that isn't there")
                        b.kind = r.int()
                        b.local = r.int()
                    }
                    for (k in c.kindPull.indices) c.kindPull[k] = Double.fromBits(r.long())
                    s.farmJobs = r.int(); s.farmJobsComing = r.int(); s.farmDemand = r.int(); c.quota[Zone.FARMLAND.toInt()] = r.int()
                    for (a in arrayOf(s.goodsMade, s.goodsSold, s.goodsExported, s.goodsImported)) for (k in a.indices) a[k] = r.int()
                    c.traffic.readGoods(r)
                }
                if (version >= 11) {
                    c.quota[Zone.OFFICE.toInt()] = r.int()
                    s.officeJobs = r.int(); s.officeJobsComing = r.int(); s.officeDemand = r.int()
                    s.officeIncome = r.long()
                }
                if (version >= 12) {
                    r.layer(m.junction); r.layer(m.control)
                } else {
                    // Before junctions: the town's controls for last month's traffic.
                    c.updateJunctions()
                }
                if (version >= 13) {
                    c.traffic.readFlow(r); s.flow = r.int()
                }
                if (version >= 14) {
                    r.layer(m.lane)
                    c.nextLineId = r.int()
                    repeat(r.count()) {
                        val id = r.int()
                        val tram = r.bool()
                        val vehicles = r.int()
                        val stops = IntArray(r.count()) { r.int() }
                        c.lines += TransitLine(id, tram, stops, vehicles)
                    }
                    c.updateTransit()
                }
                if (version >= 15) {
                    r.layer(m.district)
                    c.nextDistrictId = r.int()
                    repeat(r.count()) { c.districts += District.readFrom(r, version) }
                    c.districtTraffic()
                }
                if (version >= 17) {
                    repeat(r.count()) { c.buildings[r.int()]?.scrubbed = true }
                }
                if (version >= 18) {
                    // Version 18 saved police and fire cover twice.
                    if (version == 18) {
                        r.layer(m.policeCover)
                        r.layer(m.fireCover)
                    }
                    r.layer(m.ladderCover)
                    r.layer(m.ambulanceCover)
                }
                if (version >= 19) {
                    r.layer(m.theft)
                    r.layer(m.vice)
                    r.layer(m.rackets)
                    c.justice = r.int()
                    c.prisoners = r.int()
                    val s = c.stats
                    s.rackets = r.int(); s.offences = r.int(); s.arrests = r.int(); s.heard = r.int()
                    s.prisoners = r.int(); s.cells = r.int(); s.justice = r.int()
                }
                if (version >= 20) {
                    r.layer(m.buried)
                    r.shorts(m.powerLaid)
                } else {
                    // Lines from before they kept their age: up half the town's life ago.
                    val guess = (c.monthNow / 2).toShort()
                    for (i in 0 until m.size) if (m.power[i] != Power.NONE) m.powerLaid[i] = guess
                }
                if (version >= 21) {
                    r.layer(m.phone)
                    r.shorts(m.phoneLaid)
                    r.layer(m.comms)
                    val s = c.stats
                    s.phoneUpkeep = r.long(); s.withPhone = r.long().toInt(); s.withBroadband = r.long().toInt(); s.workingFromHome = r.long().toInt()
                }
                val savedLoad = if (version >= 22) IntArray(m.size).also { a -> repeat(r.count()) { a[r.int()] = r.int() } } else null
                if (version >= 23) {
                    c.traffic.readPorts(r)
                    val routes = ArrayList<ShipRoute>()
                    repeat(r.count()) {
                        val tiles = IntArray(r.count()) { r.int().also { i -> if (i !in 0 until m.size) throw SaveError("a ship off the map") } }
                        routes += ShipRoute(tiles, r.int(), r.int())
                    }
                    c.shipRoutes = routes
                    val s = c.stats
                    s.portUpkeep = r.long(); s.duesIncome = r.long(); s.tollIncome = r.long()
                    s.portLoads = r.int(); s.visitors = r.int(); s.guests = r.int(); s.rooms = r.int()
                    for (k in 0 until Tourism.MODES) s.visitorsBy[k] = r.int()
                    repeat(r.count()) {
                        val b = c.buildings[r.int()]
                        val served = r.int()
                        val room = r.int()
                        if (b != null) { b.served = served; b.room = room }
                    }
                }
                if (version >= 24) {
                    r.layer(m.bridge)
                    r.layer(m.bridgeShut)
                    c.tollRate = r.int().coerceIn(0, Balance.TOLL_MOST)
                    c.stats.tolls = r.int()
                    c.traffic.readBridges(r)
                    for (a in arrayOf(m.lowRoad, m.lowHeading, m.lowRail, m.portal)) r.layer(a)
                    r.shorts(m.lowLaid)
                    if (r.count() != c.trainRoutes.size) throw SaveError("the trains don't add up")
                    for (t in c.trainRoutes) repeat(r.count()) { r.int().let { k -> if (k in t.hidden.indices) t.hidden[k] = true } }
                }
                if (version >= 25) c.stats.airLoads = r.int()
                if (version >= 26) c.weather.climate = Climate.entries.getOrElse(r.int()) { Climate.TEMPERATE }
                if (version >= 27) {
                    c.carbonTotal = r.long()
                    val s = c.stats
                    s.carbon = r.long(); s.carbonPower = r.long(); s.carbonTraffic = r.long(); s.carbonWorks = r.long(); s.carbonHeating = r.long()
                    c.heatWavesThisYear = r.int(); c.floodsThisYear = r.int(); c.heatWavesLastYear = r.int(); c.floodsLastYear = r.int()
                }
                if (version >= 29) {
                    r.layer(m.upset)
                    c.displaced = r.int()
                }
                c.updateNetworks()
                c.markContainerTrains()
                c.updateAirports()
                savedLoad?.copyInto(c.grid.load)
                c.updatePathways()
            } else {
                // Before ageing nothing kept its age: count everything as laid half the town's life ago.
                val guess = (c.monthNow / 2).toShort()
                for (i in 0 until m.size) {
                    if (m.road[i] != Road.NONE) m.roadLaid[i] = guess
                    if (m.waterPipe[i].toInt() != 0) m.waterLaid[i] = guess
                    if (m.sewerPipe[i].toInt() != 0) m.sewerLaid[i] = guess
                    if (m.stormPipe[i].toInt() != 0) m.stormLaid[i] = guess
                    if (m.rail[i] != Rail.NONE) m.railLaid[i] = guess
                }
                for (b in c.buildings.values) b.built = guess.toInt()
                // Towns from before eras are in whichever era they've earned by now.
                c.census()
                while (true) {
                    val next = c.era.next ?: break
                    if (c.year < next.year || c.goals(next).any { !it.met }) break
                    c.era = next
                }
            }
            // Before the cover was saved, a loaded town had none till the month turned.
            if (version < 18) c.updateServices()
            if (version < 14) {
                // Before lines: the stops the depots and garages served, made into lines.
                c.autoLines()
                c.updateTransit()
            }
            if (version < 10) {
                // Before goods: seams in the ground away from the town, and its works making a bit of everything.
                TerrainGen.resources(m, c.seed) { i -> m.building[i] != 0 || m.zone[i] != Zone.NONE || m.road[i] != Road.NONE }
                for (b in c.buildings.values) if (b.type.zone == Zone.INDUSTRIAL) b.kind = b.id % WorksKind.entries.size
            }
            return c
        }

        const val DEFAULT_SIZE = 128
        const val START_YEAR = 1900
        const val START_FUNDS = 20_000L

        /** How many actions can be undone. */
        const val MAX_UNDO = 100

        /** What [roadCost] says when a tile's road stays as it is, or can't be built. */
        private const val NO_CHANGE = -1L

        /** Where no kind of bridge fits the water. */
        private const val NO_BRIDGE = -1
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

    /** For the eras' milestones: percent of people on mains water and on the sewer, percent of buildings with power, high density shops and offices, high schools, and percent of the land along the roads built on. */
    var onMains = 0
    var onSewer = 0
    var powered = 0
    var downtown = 0
    var highSchools = 0
    var landBuilt = 0

    /** For the Future: percent of roads, pipes and track within their expected life, and of commutes made other than by car. */
    var keptUp = 0
    var greenTrips = 0

    /** Buildings going up, and the room for people and jobs they'll bring. */
    var sites = 0
    var homesComing = 0
    var shopJobsComing = 0
    var industryJobsComing = 0
    var farmJobsComing = 0
    var officeJobsComing = 0

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
    var farmJobs = 0
    var officeJobs = 0
    var otherJobs = 0
    /** Percent of workers without a job, or without a way to get to one. */
    var unemployment = 0

    /** The average commute in minutes. */
    var commute = 0
    var residentialDemand = 0
    var commercialDemand = 0
    var industryDemand = 0
    var farmDemand = 0
    var officeDemand = 0

    /** How freely last month's traffic moved, in percent: 100 is as quick as clear roads with nothing to stop for. */
    var flow = 100

    /** Last month's taxes from office work. */
    var officeIncome = 0L

    /**
     * Last month's goods, in loads by [Good]: made in town, taken by buyers in
     * town, sent out of it, and brought in for buyers short of them.
     */
    val goodsMade = IntArray(Good.COUNT)
    val goodsSold = IntArray(Good.COUNT)
    val goodsExported = IntArray(Good.COUNT)
    val goodsImported = IntArray(Good.COUNT)

    /** What last month's goods sent out of town fetched, and what was brought in cost, in dollars. */
    val exportValue: Long get() = Good.entries.sumOf { (goodsExported[it.ordinal] * it.price * Balance.LOAD_VALUE).toLong() }
    val importValue: Long get() = Good.entries.sumOf { (goodsImported[it.ordinal] * it.importPrice * Balance.LOAD_VALUE).toLong() }

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

    /** Last month's mending of what broke. */
    var repairCost = 0L

    /** Last month's clean-up after earthquakes, accidents and storms. */
    var disasterCost = 0L

    /** Last month's garbage service and street trees. */
    var environmentUpkeep = 0L

    /** Smog over the town, 0 to 255; last month's garbage in tonnes and the share taken away; room left in the dumps, in tonnes. */
    var smog = 0
    var waste = 0
    var wasteCollected = 100
    var dumpRoom = 0

    /** The grid as last worked out, in kilowatts: what its stations can make, its peak demand, and what it couldn't meet. */
    var powerCapacity = 0L
    var powerDemand = 0L
    var powerShort = 0L

    /** Last month's fares, and the upkeep of the tram track, tunnels, stops, depots, garages and stations. */
    var fareIncome = 0L
    var transitUpkeep = 0L

    /** The telephone: exchanges, masts and lines, a month. */
    var phoneUpkeep = 0L

    /** Ports: their upkeep, the dues and tolls the town took in, and the loads through its ports last month. */
    var portUpkeep = 0L
    var duesIncome = 0L
    var tollIncome = 0L
    var portLoads = 0

    /** Vehicles that paid a toll last month. */
    var tolls = 0

    /** Loads sent away by air last month. */
    var airLoads = 0

    /** The town's carbon last month in tonnes, and where it came from. */
    var carbon = 0L
    var carbonPower = 0L
    var carbonTraffic = 0L
    var carbonWorks = 0L
    var carbonHeating = 0L

    /** Visitors in town on an average day last month, by how they came ([Tourism]); those with hotel rooms, and the rooms. */
    var visitors = 0
    val visitorsBy = IntArray(Tourism.MODES)
    var guests = 0
    var rooms = 0

    /** Percent of homes and businesses with a phone, and with broadband; workers at home on any day. */
    var withPhone = 0
    var withBroadband = 0
    var workingFromHome = 0

    /** Last month's commutes by [Mode]. */
    val byMode = IntArray(Mode.entries.size)
    var upkeep = 0L

    /** Averages: crime and pollution where there are buildings, land value over all the land. 0 to 255. */
    var crime = 0

    /** Rackets, on average where there are buildings, 0 to 255. */
    var rackets = 0

    /** Last month's offences and arrests, the cases heard, and the prisoners held of the room in the cells and jails. */
    var offences = 0
    var arrests = 0
    var heard = 0
    var prisoners = 0
    var cells = 0

    /** Percent of the arrests that stuck. */
    var justice = 100
    var pollution = 0
    var landValue = 0

    val jobs get() = shopJobs + industryJobs + farmJobs + officeJobs + otherJobs
}

/** A line a train ran last month: the track from end to end, and whether it carried passengers or freight, and how many. */
class TrainRoute(val tiles: IntArray, val passengers: Boolean, val load: Int, val hidden: BooleanArray = BooleanArray(tiles.size)) {
    /** A train of containers, from a freight terminal. */
    var containers = false
}


enum class EventKind { ForcedOut, JobsLost, FireStarted, FireSaved, FireDamaged, BuildingLost, Flooding, RiverFlood, Sickness, EraArrived, Smog, DumpFull, Gale, Blizzard, HeatWave, IndustrialAccident, NuclearAccident, Earthquake, Epidemic, EpidemicOver, MainBurst, SewerCollapsed, TrackBroken, BrokeDown, TramTrackBroken, WireDown, TunnelShut, TunnelFlooded, BridgeShut }

/** Something that happened at [x], [y], to a building of [type] if it's about one, and how many it touched if that's told. */
class CityEvent(val kind: EventKind, val x: Int, val y: Int, val type: BuildingType?, val era: Era? = null, val count: Int = 0)

/** What the graphs can show. */
enum class Series { Population, Jobs, Funds, Income, Upkeep, Crime, Pollution, LandValue, Carbon }

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
            s.crime.toLong(), s.pollution.toLong(), s.landValue.toLong(), s.carbon,
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

    /** [series] is how many series the save kept: older ones have fewer, and the rest start at nothing. */
    internal fun readFrom(r: SaveReader, series: Int = Series.entries.size) {
        val n = r.count()
        count = 0
        next = 0
        repeat(n) {
            val y = r.int()
            val m = r.int()
            val values = LongArray(data.size) { if (it < series) r.long() else 0L }
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
