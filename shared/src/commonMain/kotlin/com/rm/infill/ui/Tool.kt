package com.rm.infill.ui

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
import com.rm.infill.res.tool_zone
import com.rm.infill.res.zone_commercial
import com.rm.infill.res.zone_industrial
import com.rm.infill.res.zone_residential
import com.rm.infill.res.road_avenue
import com.rm.infill.res.road_boulevard
import com.rm.infill.res.road_dirt
import com.rm.infill.res.road_gravel
import com.rm.infill.res.road_lane
import com.rm.infill.res.road_one_way_avenue
import com.rm.infill.res.road_one_way_street
import com.rm.infill.res.road_street
import androidx.compose.ui.graphics.Color
import com.rm.infill.sim.Action
import com.rm.infill.sim.BuildingType
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.Pipe
import com.rm.infill.sim.Plan
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
}

/** What the services tool puts down. Parks are dragged out; stations go where the finger ends up. */
enum class ServiceKind(val title: StringResource, val type: BuildingType, val year: Int = 1900) {
    Police(Res.string.police_station, BuildingType.POLICE_STATION),
    Fire(Res.string.fire_station, BuildingType.FIRE_STATION),
    Park(Res.string.park, BuildingType.PARK),
    School(Res.string.school, BuildingType.SCHOOL),
    HighSchool(Res.string.high_school, BuildingType.HIGH_SCHOOL, year = 1910),
    Clinic(Res.string.clinic, BuildingType.CLINIC),
    Hospital(Res.string.hospital, BuildingType.HOSPITAL),
}

/** The services that can be built in [year]. */
fun servicesIn(year: Int): List<ServiceKind> = ServiceKind.entries.filter { it.year <= year }

/** What the rail tool puts down. Track is dragged; stations and yards go where the finger ends up. */
enum class RailKind(val title: StringResource) {
    Track(Res.string.rail_track),
    Station(Res.string.station),
    Yard(Res.string.freight_yard),
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
enum class WaterKind(val title: StringResource, val pipe: Pipe? = null, val building: BuildingType? = null, val bank: Boolean = false) {
    Main(Res.string.water_main, pipe = Pipe.WATER),
    Sewer(Res.string.sewer, pipe = Pipe.SEWER),
    Drain(Res.string.storm_drain, pipe = Pipe.STORM),
    Pump(Res.string.pumping_station, building = BuildingType.PUMPING_STATION),
    Wells(Res.string.well_field, building = BuildingType.WELL_FIELD),
    Tower(Res.string.water_tower, building = BuildingType.WATER_TOWER),
    Outfall(Res.string.sewer_outfall, building = BuildingType.OUTFALL),
    Pond(Res.string.storm_pond, building = BuildingType.STORM_POND),
    StormOutfall(Res.string.storm_outfall, building = BuildingType.STORM_OUTFALL),
    Bank(Res.string.embankment, bank = true),
    Remove(Res.string.remove_pipes),
}

/** What the power tool puts down. */
enum class PowerKind(val title: StringResource) {
    Line(Res.string.power_line),
    Plant(Res.string.coal_plant),
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
}

/** A dot of the road's colour, for the picker. */
fun roadColour(t: RoadType): Color = when (t) {
    RoadType.DIRT -> Color(0xFFA88A5C)
    RoadType.GRAVEL -> Color(0xFFB3A893)
    RoadType.LANE -> Color(0xFFC9A978)
    RoadType.STREET, RoadType.ONE_WAY_STREET -> Color(0xFF8A8780)
    RoadType.AVENUE, RoadType.ONE_WAY_AVENUE, RoadType.BOULEVARD -> Color(0xFF6E6B65)
}

/** The roads that can be built in [year]. */
fun roadsIn(year: Int): List<RoadType> = RoadType.entries.filter { it.year <= year }

/** The kinds of zone, in the order the picker shows them. */
enum class ZoneKind(val zone: Byte, val title: StringResource) {
    Residential(Zone.RESIDENTIAL, Res.string.zone_residential),
    Commercial(Zone.COMMERCIAL, Res.string.zone_commercial),
    Industrial(Zone.INDUSTRIAL, Res.string.zone_industrial),
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
        tool: Tool, zone: ZoneKind, power: PowerKind, service: ServiceKind, road: RoadType, roadPipes: Boolean,
        rail: RailKind, water: WaterKind, map: CityMap,
    ): Action? = when (tool) {
        Tool.Services -> if (service == ServiceKind.Park) Action.PlaceParks(x0, y0, x1, y1)
        else Action.PlaceBuilding(service.type, x1, y1)
        Tool.Inspect -> null
        Tool.Road -> {
            val across = acrossFirst ?: true
            // A two-wide road only goes straight, along whichever way the drag went first.
            if (road.width == 2) {
                Action.BuildRoad(Action.roadPath(map, x0, y0, if (across) x1 else x0, if (across) y0 else y1, across), road, roadPipes)
            } else {
                Action.BuildRoad(Action.roadPath(map, x0, y0, x1, y1, across), road, roadPipes)
            }
        }
        Tool.Rail -> when (rail) {
            RailKind.Track -> Action.BuildRail(Action.roadPath(map, x0, y0, x1, y1, acrossFirst ?: true))
            RailKind.Station -> Action.PlaceBuilding(railBuilding(map, BuildingType.STATION, BuildingType.STATION_NS, x1, y1), x1, y1)
            RailKind.Yard -> Action.PlaceBuilding(railBuilding(map, BuildingType.FREIGHT_YARD, BuildingType.FREIGHT_YARD_NS, x1, y1), x1, y1)
        }
        Tool.Water -> when {
            water.pipe != null -> Action.BuildPipe(Action.roadPath(map, x0, y0, x1, y1, acrossFirst ?: true), water.pipe)
            water.building != null -> Action.PlaceBuilding(water.building, x1, y1)
            water.bank -> Action.BuildBank(Action.roadPath(map, x0, y0, x1, y1, acrossFirst ?: true))
            else -> Action.RemovePipes(x0, y0, x1, y1)
        }
        Tool.Zone -> Action.PlaceZone(x0, y0, x1, y1, zone.zone)
        Tool.Bulldoze -> Action.Bulldoze(x0, y0, x1, y1)
        Tool.Power -> when (power) {
            PowerKind.Line -> Action.BuildPowerLine(Action.roadPath(map, x0, y0, x1, y1, acrossFirst ?: true))
            // A building goes where the finger ends up, with that tile its top left.
            PowerKind.Plant -> Action.PlaceBuilding(BuildingType.COAL_PLANT, x1, y1)
        }
    }
}

/** A drag's action and what it would do, for drawing over the map. */
class Preview(val action: Action, val plan: Plan, val endX: Int, val endY: Int) {
    val blocked: Set<Int> = plan.blocked.toHashSet()
}
