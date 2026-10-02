package com.rm.infill.ui

import com.rm.infill.res.freight_terminal
import com.rm.infill.res.bulldoze_tunnel
import com.rm.infill.res.container_port
import com.rm.infill.res.docks
import com.rm.infill.res.wharf
import com.rm.infill.res.tool_port
import com.rm.infill.res.tool_districts
import com.rm.infill.res.services_police
import com.rm.infill.res.transit_trams
import com.rm.infill.res.transit_buses
import com.rm.infill.res.transit_subway
import com.rm.infill.res.water_supply
import com.rm.infill.res.water_sewers
import com.rm.infill.res.water_storm
import com.rm.infill.res.services_fire
import com.rm.infill.res.services_health
import com.rm.infill.res.services_schools
import com.rm.infill.res.services_parks
import com.rm.infill.res.services_waste
import com.rm.infill.res.group_transport
import com.rm.infill.res.group_utilities
import com.rm.infill.res.group_zones
import com.rm.infill.res.tram_line
import com.rm.infill.res.bus_line
import com.rm.infill.res.bus_lane
import com.rm.infill.res.lines
import com.rm.infill.res.tool_traffic
import com.rm.infill.res.junction_auto
import com.rm.infill.res.junction_free
import com.rm.infill.res.junction_stop
import com.rm.infill.res.junction_lights
import com.rm.infill.res.junction_roundabout
import com.rm.infill.res.junction_interchange
import com.rm.infill.res.zone_office
import com.rm.infill.res.zone_farmland
import com.rm.infill.res.Res
import com.rm.infill.res.tool_bulldoze
import com.rm.infill.res.tool_inspect
import com.rm.infill.res.tool_power
import com.rm.infill.res.tool_services
import com.rm.infill.res.police_station
import com.rm.infill.res.fire_station
import com.rm.infill.res.park
import com.rm.infill.res.school
import com.rm.infill.res.high_school
import com.rm.infill.res.clinic
import com.rm.infill.res.hospital
import com.rm.infill.res.volunteer_hall
import com.rm.infill.res.ladder_company
import com.rm.infill.res.ambulance_station
import com.rm.infill.res.nursing_home
import com.rm.infill.res.library
import com.rm.infill.res.college
import com.rm.infill.res.police_hq
import com.rm.infill.res.courthouse
import com.rm.infill.res.jail
import com.rm.infill.res.tool_road
import com.rm.infill.res.tool_rail
import com.rm.infill.res.tool_water
import com.rm.infill.res.water_main
import com.rm.infill.res.sewer
import com.rm.infill.res.storm_drain
import com.rm.infill.res.pumping_station
import com.rm.infill.res.well_field
import com.rm.infill.res.water_tower
import com.rm.infill.res.sewer_outfall
import com.rm.infill.res.storm_pond
import com.rm.infill.res.storm_outfall
import com.rm.infill.res.remove_pipes
import com.rm.infill.res.embankment
import com.rm.infill.res.rail_track
import com.rm.infill.res.station
import com.rm.infill.res.freight_yard
import com.rm.infill.res.power_line
import com.rm.infill.res.coal_plant
import com.rm.infill.res.oil_plant
import com.rm.infill.res.scrubbers
import com.rm.infill.res.gas_plant
import com.rm.infill.res.hydro_plant
import com.rm.infill.res.nuclear_plant
import com.rm.infill.res.substation
import com.rm.infill.res.high_line
import com.rm.infill.res.tool_phone
import com.rm.infill.res.exchange
import com.rm.infill.res.wind_farm
import com.rm.infill.res.solar_farm
import com.rm.infill.res.battery
import com.rm.infill.res.river_turbine
import com.rm.infill.res.tidal_turbine
import com.rm.infill.res.offshore_wind
import com.rm.infill.res.cell_tower
import com.rm.infill.res.copper_line
import com.rm.infill.res.copper_duct
import com.rm.infill.res.fibre_line
import com.rm.infill.res.fibre_duct
import com.rm.infill.res.remove_phone
import com.rm.infill.res.power_cable
import com.rm.infill.res.high_cable
import com.rm.infill.res.dump
import com.rm.infill.res.incinerator
import com.rm.infill.res.recycling
import com.rm.infill.res.street_trees
import com.rm.infill.res.tool_zone
import com.rm.infill.res.zone_commercial
import com.rm.infill.res.zone_industrial
import com.rm.infill.res.zone_residential
import com.rm.infill.res.road_avenue
import com.rm.infill.res.road_highway
import com.rm.infill.res.road_ramp
import com.rm.infill.res.road_boulevard
import com.rm.infill.res.road_dirt
import com.rm.infill.res.road_gravel
import com.rm.infill.res.road_lane
import com.rm.infill.res.road_one_way_avenue
import com.rm.infill.res.road_one_way_street
import com.rm.infill.res.road_street
import androidx.compose.ui.graphics.Color
import com.rm.infill.sim.BridgeKind
import com.rm.infill.sim.Action
import com.rm.infill.sim.BuildingType
import com.rm.infill.sim.City
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.Junction
import com.rm.infill.sim.NEW_DISTRICT
import com.rm.infill.sim.Stop
import com.rm.infill.res.tool_transit
import com.rm.infill.res.tram_track
import com.rm.infill.res.tram_stop
import com.rm.infill.res.tram_depot
import com.rm.infill.res.bus_stop
import com.rm.infill.res.bus_garage
import com.rm.infill.res.subway
import com.rm.infill.res.trolley_wire
import com.rm.infill.res.bulldoze_clear
import com.rm.infill.res.bulldoze_renew
import com.rm.infill.res.subway_station
import com.rm.infill.res.remove_transit
import com.rm.infill.sim.Material
import com.rm.infill.res.material_wood
import com.rm.infill.res.sewage_works
import com.rm.infill.res.treatment_plant
import com.rm.infill.sim.Density
import com.rm.infill.res.density_low
import com.rm.infill.res.density_medium
import com.rm.infill.res.density_high
import com.rm.infill.sim.Pipe
import com.rm.infill.sim.Plan
import com.rm.infill.sim.Port
import com.rm.infill.sim.Rail
import com.rm.infill.sim.RoadType
import com.rm.infill.sim.Zone
import org.jetbrains.compose.resources.StringResource

/** What a tap or a drag on the map does. */
enum class Tool(val title: StringResource) {
    Inspect(Res.string.tool_inspect),
    Bulldoze(Res.string.tool_bulldoze),
    Road(Res.string.tool_road),
    Rail(Res.string.tool_rail),
    Zone(Res.string.tool_zone),
    Power(Res.string.tool_power),
    Water(Res.string.tool_water),
    Services(Res.string.tool_services),
    Transit(Res.string.tool_transit),
    Traffic(Res.string.tool_traffic),
    Districts(Res.string.tool_districts),
    Phone(Res.string.tool_phone),
    Port(Res.string.tool_port),
}

/**
 * The toolbar's buttons. A button with more than one tool shows them as tabs
 * over their choices, so the bar fits across an upright phone.
 */
enum class ToolGroup(val title: StringResource, val tools: List<Tool>) {
    Inspect(Res.string.tool_inspect, listOf(Tool.Inspect)),
    Bulldoze(Res.string.tool_bulldoze, listOf(Tool.Bulldoze)),
    Zones(Res.string.group_zones, listOf(Tool.Zone, Tool.Districts)),
    Transport(Res.string.group_transport, listOf(Tool.Road, Tool.Rail, Tool.Transit, Tool.Traffic, Tool.Port)),
    Utilities(Res.string.group_utilities, listOf(Tool.Power, Tool.Water, Tool.Phone)),
    Services(Res.string.tool_services, listOf(Tool.Services)),
}

/** The button [tool] is under. */
val Tool.group: ToolGroup get() = ToolGroup.entries.first { this in it.tools }

/** The districts tool's choice that opens the list rather than painting. */
const val DISTRICT_LIST = -2

/** What the traffic tool sets at the crossings dragged over. */
enum class JunctionKind(val title: StringResource, val control: Byte) {
    Lights(Res.string.junction_lights, Junction.LIGHTS),
    Roundabout(Res.string.junction_roundabout, Junction.ROUNDABOUT),
    Interchange(Res.string.junction_interchange, Junction.INTERCHANGE),
    Stop(Res.string.junction_stop, Junction.STOP),
    Auto(Res.string.junction_auto, Junction.AUTO),
}

/** The controls [city] can put up in its year. */
fun junctionKindsIn(city: City): List<JunctionKind> = JunctionKind.entries.filter { city.everything || city.year >= Junction.year(it.control) }

fun junctionName(control: Byte): StringResource = when (control) {
    Junction.STOP -> Res.string.junction_stop
    Junction.LIGHTS -> Res.string.junction_lights
    Junction.ROUNDABOUT -> Res.string.junction_roundabout
    Junction.INTERCHANGE -> Res.string.junction_interchange
    else -> Res.string.junction_free
}

/**
 * What the transit tool puts down. Track and tunnels are dragged, as is
 * taking them up; stops, depots, garages and stations go where the finger ends up.
 */
enum class TransitKind(
    val title: StringResource,
    val building: BuildingType? = null,
    val stop: Int = 0,
    val needs: BuildingType? = null,
    /** Overhead wire for trolleybuses, which has its own year. */
    val wire: Boolean = false,
    /** A lane kept for buses and trams, dragged along a road. */
    val lane: Boolean = false,
    /** A line planned by tapping its stops in order: 1 for buses, 2 for trams. */
    val line: Int = 0,
    /** Opens the list of lines. */
    val list: Boolean = false,
) {
    TramTrack(Res.string.tram_track),
    TramStop(Res.string.tram_stop, stop = Stop.TRAM),
    Depot(Res.string.tram_depot, building = BuildingType.TRAM_DEPOT),
    BusStop(Res.string.bus_stop, stop = Stop.BUS, needs = BuildingType.BUS_GARAGE),
    Garage(Res.string.bus_garage, building = BuildingType.BUS_GARAGE),
    Wire(Res.string.trolley_wire, wire = true),
    Subway(Res.string.subway, needs = BuildingType.SUBWAY_STATION),
    Station(Res.string.subway_station, building = BuildingType.SUBWAY_STATION),
    TramLine(Res.string.tram_line, line = 2),
    BusLine(Res.string.bus_line, needs = BuildingType.BUS_GARAGE, line = 1),
    Lane(Res.string.bus_lane, lane = true),
    Lines(Res.string.lines, list = true),
    Remove(Res.string.remove_transit),
}

/** The kinds of transit, each a tab of its own beside the roads. */
enum class TransitGroup(val title: StringResource) {
    Trams(Res.string.transit_trams),
    Buses(Res.string.transit_buses),
    Subway(Res.string.transit_subway),
}

/** The tabs [this] shows in: a lane serves trams and buses both, and taking things up and the list of lines are in each they apply to. */
val TransitKind.groups: List<TransitGroup> get() = when (this) {
    TransitKind.TramTrack, TransitKind.TramStop, TransitKind.Depot, TransitKind.TramLine -> listOf(TransitGroup.Trams)
    TransitKind.BusStop, TransitKind.Garage, TransitKind.Wire, TransitKind.BusLine -> listOf(TransitGroup.Buses)
    TransitKind.Subway, TransitKind.Station -> listOf(TransitGroup.Subway)
    TransitKind.Lane, TransitKind.Lines -> listOf(TransitGroup.Trams, TransitGroup.Buses)
    TransitKind.Remove -> TransitGroup.entries
}

/** What the transit tool offers [city] in its era. */
fun transitKindsIn(city: City): List<TransitKind> =
    TransitKind.entries.filter { k -> if (k.wire) city.allowsTrolleybuses() else (k.building ?: k.needs)?.let { city.allows(it) } ?: true }

/** The kinds of service, each a tab of its own in the services tray. */
enum class ServiceGroup(val title: StringResource) {
    Police(Res.string.services_police),
    Fire(Res.string.services_fire),
    Health(Res.string.services_health),
    Schools(Res.string.services_schools),
    Parks(Res.string.services_parks),
    Waste(Res.string.services_waste),
}

/** What the services tool puts down. Parks are dragged out, as are street trees along roads; stations go where the finger ends up. */
enum class ServiceKind(val title: StringResource, val type: BuildingType?, val group: ServiceGroup) {
    Police(Res.string.police_station, BuildingType.POLICE_STATION, ServiceGroup.Police),
    PoliceHq(Res.string.police_hq, BuildingType.POLICE_HQ, ServiceGroup.Police),
    Courthouse(Res.string.courthouse, BuildingType.COURTHOUSE, ServiceGroup.Police),
    Jail(Res.string.jail, BuildingType.JAIL, ServiceGroup.Police),
    Fire(Res.string.fire_station, BuildingType.FIRE_STATION, ServiceGroup.Fire),
    Volunteers(Res.string.volunteer_hall, BuildingType.VOLUNTEER_HALL, ServiceGroup.Fire),
    Ladders(Res.string.ladder_company, BuildingType.LADDER_COMPANY, ServiceGroup.Fire),
    Park(Res.string.park, BuildingType.PARK, ServiceGroup.Parks),
    StreetTrees(Res.string.street_trees, null, ServiceGroup.Parks),
    School(Res.string.school, BuildingType.SCHOOL, ServiceGroup.Schools),
    HighSchool(Res.string.high_school, BuildingType.HIGH_SCHOOL, ServiceGroup.Schools),
    Library(Res.string.library, BuildingType.LIBRARY, ServiceGroup.Schools),
    College(Res.string.college, BuildingType.COLLEGE, ServiceGroup.Schools),
    Clinic(Res.string.clinic, BuildingType.CLINIC, ServiceGroup.Health),
    Hospital(Res.string.hospital, BuildingType.HOSPITAL, ServiceGroup.Health),
    Ambulance(Res.string.ambulance_station, BuildingType.AMBULANCE_STATION, ServiceGroup.Health),
    Nursing(Res.string.nursing_home, BuildingType.NURSING_HOME, ServiceGroup.Health),
    Dump(Res.string.dump, BuildingType.DUMP, ServiceGroup.Waste),
    Incinerator(Res.string.incinerator, BuildingType.INCINERATOR, ServiceGroup.Waste),
    Recycling(Res.string.recycling, BuildingType.RECYCLING, ServiceGroup.Waste),
}

/** The services [city] can build in its era. */
fun servicesIn(city: City): List<ServiceKind> = ServiceKind.entries.filter { it.type == null || city.allows(it.type) }

/** What the rail tool puts down. Track is dragged; stations and yards go where the finger ends up. */
enum class RailKind(val title: StringResource) {
    Track(Res.string.rail_track),
    Station(Res.string.station),
    Yard(Res.string.freight_yard),
    Terminal(Res.string.freight_terminal),
}

/** What the rail tool can put down in [city]'s era. */
fun railKindsIn(city: City): List<RailKind> = RailKind.entries.filter { it != RailKind.Terminal || city.allows(BuildingType.FREIGHT_TERMINAL) }

/** What the ports tool puts down: each kind of port, lying east to west or north to south. */
enum class PortKind(val title: StringResource, val eastWest: BuildingType, val northSouth: BuildingType) {
    Wharf(Res.string.wharf, BuildingType.WHARF, BuildingType.WHARF_NS),
    Docks(Res.string.docks, BuildingType.DOCKS, BuildingType.DOCKS_NS),
    Container(Res.string.container_port, BuildingType.CONTAINER_PORT, BuildingType.CONTAINER_PORT_NS),
}

/** The ports [city] can build in its era. */
fun portKindsIn(city: City): List<PortKind> = PortKind.entries.filter { city.allows(it.eastWest) }

/** A port with its top left on [x], [y], lying whichever way has water along it, east to west if neither does. */
fun portBuilding(map: CityMap, kind: PortKind, x: Int, y: Int): BuildingType = when {
    Port.waterSide(map, kind.eastWest, x, y) != 0 -> kind.eastWest
    Port.waterSide(map, kind.northSouth, x, y) != 0 -> kind.northSouth
    else -> kind.eastWest
}

/**
 * A station or yard with its top left on [x], [y], lying whichever way has
 * track alongside, east to west if neither does.
 */
fun railBuilding(map: CityMap, eastWest: BuildingType, northSouth: BuildingType, x: Int, y: Int): BuildingType = when {
    Rail.trackSide(map, eastWest, x, y) != 0 -> eastWest
    Rail.trackSide(map, northSouth, x, y) != 0 -> northSouth
    else -> eastWest
}

/**
 * What the water tool puts down: pipes are dragged, as is taking them up;
 * buildings go where the finger ends up.
 */
enum class WaterKind(
    val title: StringResource,
    val pipe: Pipe? = null,
    val building: BuildingType? = null,
    val bank: Boolean = false,
    /** A particular kind of pipe; otherwise the best the town lays now. */
    val material: Material? = null,
) {
    Main(Res.string.water_main, pipe = Pipe.WATER),
    Wood(Res.string.material_wood, pipe = Pipe.WATER, material = Material.WOOD),
    Sewer(Res.string.sewer, pipe = Pipe.SEWER),
    Drain(Res.string.storm_drain, pipe = Pipe.STORM),
    Pump(Res.string.pumping_station, building = BuildingType.PUMPING_STATION),
    Wells(Res.string.well_field, building = BuildingType.WELL_FIELD),
    Tower(Res.string.water_tower, building = BuildingType.WATER_TOWER),
    Outfall(Res.string.sewer_outfall, building = BuildingType.OUTFALL),
    Works(Res.string.sewage_works, building = BuildingType.SEWAGE_WORKS),
    Treatment(Res.string.treatment_plant, building = BuildingType.TREATMENT_PLANT),
    Pond(Res.string.storm_pond, building = BuildingType.STORM_POND),
    StormOutfall(Res.string.storm_outfall, building = BuildingType.STORM_OUTFALL),
    Bank(Res.string.embankment, bank = true),
    Remove(Res.string.remove_pipes),
}

/** Clean water, sewage and storm water, each a tab of its own beside power. */
enum class WaterGroup(val title: StringResource) {
    Supply(Res.string.water_supply),
    Sewers(Res.string.water_sewers),
    Storm(Res.string.water_storm),
}

/** The tabs [this] shows in; taking pipes up is in all of them. */
val WaterKind.groups: List<WaterGroup> get() = when (this) {
    WaterKind.Main, WaterKind.Wood, WaterKind.Pump, WaterKind.Wells, WaterKind.Tower, WaterKind.Treatment -> listOf(WaterGroup.Supply)
    WaterKind.Sewer, WaterKind.Outfall, WaterKind.Works -> listOf(WaterGroup.Sewers)
    WaterKind.Drain, WaterKind.Pond, WaterKind.StormOutfall, WaterKind.Bank -> listOf(WaterGroup.Storm)
    WaterKind.Remove -> WaterGroup.entries
}

/** What the water tool offers [city] now: wooden mains only while they're still laid, sewage works once it has them. */
fun waterKindsIn(city: City): List<WaterKind> =
    WaterKind.entries.filter { (it.material == null || city.allows(it.material)) && (it.building == null || city.allows(it.building)) }

/** What the bulldozer does: clears everything, or relays what's worn. */
enum class BulldozeKind(val title: StringResource) {
    Clear(Res.string.bulldoze_clear),
    Renew(Res.string.bulldoze_renew),
    Tunnel(Res.string.bulldoze_tunnel),
}

/** What the phone tool puts down: exchanges and masts go where the finger ends up, lines are dragged, as is taking them up. */
enum class PhoneKind(val title: StringResource, val building: BuildingType? = null, val line: Boolean = false, val fibre: Boolean = false, val duct: Boolean = false) {
    Exchange(Res.string.exchange, building = BuildingType.EXCHANGE),
    Copper(Res.string.copper_line, line = true),
    CopperDuct(Res.string.copper_duct, line = true, duct = true),
    Fibre(Res.string.fibre_line, line = true, fibre = true),
    FibreDuct(Res.string.fibre_duct, line = true, fibre = true, duct = true),
    Tower(Res.string.cell_tower, building = BuildingType.CELL_TOWER),
    Remove(Res.string.remove_phone),
}

/** What the phone tool offers [city] in its era. */
fun phoneKindsIn(city: City): List<PhoneKind> =
    PhoneKind.entries.filter { (!it.fibre || city.allowsFibre()) && (it.building == null || city.allows(it.building)) }

/** What the power tool puts down: lines are dragged, stations go where the finger ends up. */
enum class PowerKind(
    val title: StringResource,
    val building: BuildingType? = null,
    val high: Boolean = false,
    val scrubbers: Boolean = false,
    /** Laid underground rather than strung on poles. */
    val buried: Boolean = false,
) {
    Line(Res.string.power_line),
    High(Res.string.high_line, high = true),
    Cable(Res.string.power_cable, buried = true),
    HighCable(Res.string.high_cable, high = true, buried = true),
    Substation(Res.string.substation, BuildingType.SUBSTATION),
    Coal(Res.string.coal_plant, BuildingType.COAL_PLANT),
    Oil(Res.string.oil_plant, BuildingType.OIL_PLANT),
    Gas(Res.string.gas_plant, BuildingType.GAS_PLANT),
    Hydro(Res.string.hydro_plant, BuildingType.HYDRO_PLANT),
    Nuclear(Res.string.nuclear_plant, BuildingType.NUCLEAR_PLANT),
    Wind(Res.string.wind_farm, BuildingType.WIND_FARM),
    Solar(Res.string.solar_farm, BuildingType.SOLAR_FARM),
    Battery(Res.string.battery, BuildingType.BATTERY),
    RiverTurbine(Res.string.river_turbine, BuildingType.RIVER_TURBINE),
    Tidal(Res.string.tidal_turbine, BuildingType.TIDAL_TURBINE),
    Offshore(Res.string.offshore_wind, BuildingType.OFFSHORE_WIND),
    /** Fitted to the coal or oil station tapped. */
    Scrubbers(Res.string.scrubbers, scrubbers = true),
}

/** What the power tool offers [city] in its era. */
fun powerKindsIn(city: City): List<PowerKind> =
    PowerKind.entries.filter {
        when {
            it.high && it.buried -> city.allowsHighLines() && city.allowsHighCable()
            it.high -> city.allowsHighLines()
            it.scrubbers -> city.allowsScrubbers()
            else -> it.building?.let { b -> city.allows(b) } ?: true
        }
    }

fun roadName(t: RoadType): StringResource = when (t) {
    RoadType.DIRT -> Res.string.road_dirt
    RoadType.GRAVEL -> Res.string.road_gravel
    RoadType.LANE -> Res.string.road_lane
    RoadType.STREET -> Res.string.road_street
    RoadType.ONE_WAY_STREET -> Res.string.road_one_way_street
    RoadType.AVENUE -> Res.string.road_avenue
    RoadType.ONE_WAY_AVENUE -> Res.string.road_one_way_avenue
    RoadType.BOULEVARD -> Res.string.road_boulevard
    RoadType.HIGHWAY -> Res.string.road_highway
    RoadType.RAMP -> Res.string.road_ramp
}

/** The roads [city] can build in its era. */
fun roadsIn(city: City): List<RoadType> = RoadType.entries.filter { !it.ramp && city.allows(it) }

/** How dense a zone may build, in the order the picker shows them. */
enum class DensityKind(val density: Byte, val title: StringResource) {
    Low(Density.LOW, Res.string.density_low),
    Medium(Density.MEDIUM, Res.string.density_medium),
    High(Density.HIGH, Res.string.density_high),
}

/** The kinds of zone, in the order the picker shows them. */
enum class ZoneKind(val zone: Byte, val title: StringResource) {
    Residential(Zone.RESIDENTIAL, Res.string.zone_residential),
    Commercial(Zone.COMMERCIAL, Res.string.zone_commercial),
    Industrial(Zone.INDUSTRIAL, Res.string.zone_industrial),
    Office(Zone.OFFICE, Res.string.zone_office),
    Farmland(Zone.FARMLAND, Res.string.zone_farmland),
}

/**
 * A drag with a tool, from one tile to another. [acrossFirst] is which way a
 * road goes first, settled by the first move off the starting tile.
 */
data class ToolDrag(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val acrossFirst: Boolean? = null) {
    fun to(x: Int, y: Int): ToolDrag {
        if (x == x1 && y == y1) return this
        val across = acrossFirst ?: if (x == x0 && y == y0) null else kotlin.math.abs(x - x0) >= kotlin.math.abs(y - y0)
        return copy(x1 = x, y1 = y, acrossFirst = across)
    }

    fun action(
        tool: Tool, zone: ZoneKind, density: DensityKind, bulldoze: BulldozeKind, power: PowerKind, service: ServiceKind, road: RoadType, roadPipes: Boolean,
        rail: RailKind, water: WaterKind, transit: TransitKind, map: CityMap, junction: JunctionKind = JunctionKind.Lights,
        district: Int = NEW_DISTRICT, phone: PhoneKind = PhoneKind.Copper, port: PortKind = PortKind.Wharf, bridge: BridgeKind? = null, tunnel: Boolean = false,
    ): Action? = when (tool) {
        Tool.Port -> Action.PlaceBuilding(portBuilding(map, port, x1, y1), x1, y1)
        Tool.Phone -> when {
            phone.building != null -> Action.PlaceBuilding(phone.building, x1, y1)
            phone.line -> Action.BuildPhoneLine(Action.roadPath(map, x0, y0, x1, y1, acrossFirst ?: true), phone.fibre, phone.duct)
            else -> Action.RemovePhone(x0, y0, x1, y1)
        }
        Tool.Districts -> if (district == DISTRICT_LIST) null else Action.PaintDistrict(x0, y0, x1, y1, district)
        Tool.Traffic -> Action.SetJunction(Action.roadPath(map, x0, y0, x1, y1, acrossFirst ?: true), junction.control)
        Tool.Transit -> when {
            transit.building != null -> Action.PlaceBuilding(transit.building, x1, y1)
            transit.stop != 0 -> Action.PlaceStop(x1, y1, transit.stop)
            transit == TransitKind.TramTrack -> Action.BuildTram(Action.roadPath(map, x0, y0, x1, y1, acrossFirst ?: true))
            transit.wire -> Action.BuildWire(Action.roadPath(map, x0, y0, x1, y1, acrossFirst ?: true))
            transit.lane -> Action.BuildLane(Action.roadPath(map, x0, y0, x1, y1, acrossFirst ?: true))
            // Lines are made a stop at a time, and the list is a window.
            transit.line != 0 || transit.list -> null
            transit == TransitKind.Subway -> Action.BuildSubway(Action.roadPath(map, x0, y0, x1, y1, acrossFirst ?: true))
            else -> Action.RemoveTransit(x0, y0, x1, y1)
        }
        Tool.Services -> when {
            service == ServiceKind.Park -> Action.PlaceParks(x0, y0, x1, y1)
            service.type == null -> Action.PlantStreetTrees(Action.roadPath(map, x0, y0, x1, y1, acrossFirst ?: true))
            else -> Action.PlaceBuilding(service.type, x1, y1)
        }
        Tool.Inspect -> null
        Tool.Road -> {
            val across = acrossFirst ?: true
            // A two-wide road only goes straight, along whichever way the drag went first.
            if (road.width == 2) {
                Action.BuildRoad(Action.roadPath(map, x0, y0, if (across) x1 else x0, if (across) y0 else y1, across), road, roadPipes)
            } else {
                Action.BuildRoad(Action.roadPath(map, x0, y0, x1, y1, across), road, roadPipes && !tunnel, bridge, tunnel)
            }
        }
        Tool.Rail -> when (rail) {
            RailKind.Track -> Action.BuildRail(Action.roadPath(map, x0, y0, x1, y1, acrossFirst ?: true), bridge?.takeIf { it.rail }, tunnel)
            RailKind.Station -> Action.PlaceBuilding(railBuilding(map, BuildingType.STATION, BuildingType.STATION_NS, x1, y1), x1, y1)
            RailKind.Yard -> Action.PlaceBuilding(railBuilding(map, BuildingType.FREIGHT_YARD, BuildingType.FREIGHT_YARD_NS, x1, y1), x1, y1)
            RailKind.Terminal -> Action.PlaceBuilding(railBuilding(map, BuildingType.FREIGHT_TERMINAL, BuildingType.FREIGHT_TERMINAL_NS, x1, y1), x1, y1)
        }
        Tool.Water -> when {
            water.pipe != null -> Action.BuildPipe(Action.roadPath(map, x0, y0, x1, y1, acrossFirst ?: true), water.pipe, water.material)
            water.building != null -> Action.PlaceBuilding(water.building, x1, y1)
            water.bank -> Action.BuildBank(Action.roadPath(map, x0, y0, x1, y1, acrossFirst ?: true))
            else -> Action.RemovePipes(x0, y0, x1, y1)
        }
        Tool.Zone -> Action.PlaceZone(x0, y0, x1, y1, zone.zone, if (zone == ZoneKind.Farmland) Density.LOW else density.density)
        Tool.Bulldoze -> when (bulldoze) {
            BulldozeKind.Renew -> Action.RenewArea(x0, y0, x1, y1)
            BulldozeKind.Tunnel -> Action.RemoveTunnel(x0, y0, x1, y1)
            BulldozeKind.Clear -> Action.Bulldoze(x0, y0, x1, y1)
        }
        // A building goes where the finger ends up, with that tile its top left.
        Tool.Power -> if (power.scrubbers) Action.FitScrubbers(x1, y1) else power.building?.let { Action.PlaceBuilding(it, x1, y1) }
            ?: Action.BuildPowerLine(Action.roadPath(map, x0, y0, x1, y1, acrossFirst ?: true), power.high, power.buried)
    }
}

/** A drag's action and what it would do, for drawing over the map. */
class Preview(val action: Action, val plan: Plan, val endX: Int, val endY: Int) {
    val blocked: Set<Int> = plan.blocked.toHashSet()
}
