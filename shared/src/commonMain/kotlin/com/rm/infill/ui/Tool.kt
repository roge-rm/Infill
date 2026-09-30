package com.rm.infill.ui

import com.rm.infill.res.Res
import com.rm.infill.res.tool_bulldoze
import com.rm.infill.res.tool_inspect
import com.rm.infill.res.tool_power
import com.rm.infill.res.tool_services
import com.rm.infill.res.police_station
import com.rm.infill.res.fire_station
import com.rm.infill.res.park
import com.rm.infill.res.tool_road
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
import com.rm.infill.sim.Plan
import com.rm.infill.sim.RoadType
import com.rm.infill.sim.Zone
import org.jetbrains.compose.resources.StringResource

/** What a tap or a drag on the map does. */
enum class Tool(val title: StringResource) {
    Inspect(Res.string.tool_inspect),
    Bulldoze(Res.string.tool_bulldoze),
    Road(Res.string.tool_road),
    Zone(Res.string.tool_zone),
    Power(Res.string.tool_power),
    Services(Res.string.tool_services),
}

/** What the services tool puts down. Parks are dragged out; stations go where the finger ends up. */
enum class ServiceKind(val title: StringResource, val type: BuildingType) {
    Police(Res.string.police_station, BuildingType.POLICE_STATION),
    Fire(Res.string.fire_station, BuildingType.FIRE_STATION),
    Park(Res.string.park, BuildingType.PARK),
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

    fun action(tool: Tool, zone: ZoneKind, power: PowerKind, service: ServiceKind, road: RoadType, map: CityMap): Action? = when (tool) {
        Tool.Services -> if (service == ServiceKind.Park) Action.PlaceParks(x0, y0, x1, y1)
        else Action.PlaceBuilding(service.type, x1, y1)
        Tool.Inspect -> null
        Tool.Road -> {
            val across = acrossFirst ?: true
            // A two-wide road only goes straight, along whichever way the drag went first.
            if (road.width == 2) {
                Action.BuildRoad(Action.roadPath(map, x0, y0, if (across) x1 else x0, if (across) y0 else y1, across), road)
            } else {
                Action.BuildRoad(Action.roadPath(map, x0, y0, x1, y1, across), road)
            }
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
