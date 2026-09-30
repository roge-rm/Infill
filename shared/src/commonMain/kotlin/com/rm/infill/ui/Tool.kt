package com.rm.infill.ui

import com.rm.infill.res.Res
import com.rm.infill.res.tool_bulldoze
import com.rm.infill.res.tool_inspect
import com.rm.infill.res.tool_road
import com.rm.infill.res.tool_zone
import com.rm.infill.res.zone_commercial
import com.rm.infill.res.zone_industrial
import com.rm.infill.res.zone_residential
import com.rm.infill.sim.Action
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.Plan
import com.rm.infill.sim.Zone
import org.jetbrains.compose.resources.StringResource

/** What a tap or a drag on the map does. */
enum class Tool(val title: StringResource) {
    Inspect(Res.string.tool_inspect),
    Bulldoze(Res.string.tool_bulldoze),
    Road(Res.string.tool_road),
    Zone(Res.string.tool_zone),
}

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

    fun action(tool: Tool, zone: ZoneKind, map: CityMap): Action? = when (tool) {
        Tool.Inspect -> null
        Tool.Road -> Action.BuildRoad(Action.roadPath(map, x0, y0, x1, y1, acrossFirst ?: true))
        Tool.Zone -> Action.PlaceZone(x0, y0, x1, y1, zone.zone)
        Tool.Bulldoze -> Action.Bulldoze(x0, y0, x1, y1)
    }
}

/** A drag's action and what it would do, for drawing over the map. */
class Preview(val action: Action, val plan: Plan, val endX: Int, val endY: Int) {
    val blocked: Set<Int> = plan.blocked.toHashSet()
}
