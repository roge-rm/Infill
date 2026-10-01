package com.rm.infill.map

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rm.infill.sim.Action
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.Problem
import com.rm.infill.ui.Preview

/** The tiles a drag would change, the ones it can't, and what it costs. */
internal fun DrawScope.drawPreview(p: Preview, map: CityMap, camera: Camera, measurer: TextMeasurer, costText: String) {
    val t = camera.tilePx
    val tile = Size(t, t)
    fun at(i: Int) = camera.tileToScreen((i % map.width).toFloat(), (i / map.width).toFloat(), size)
    when (val a = p.action) {
        is Action.BuildRoad -> {
            // The drag faintly, then what it changes, which for a boulevard is both carriageways.
            for (i in a.tiles) drawRect(ROAD_DRAG, at(i), tile)
            for (i in p.plan.changes) drawRect(ROAD_FILL, at(i), tile)
            for (i in p.blocked) drawRect(BLOCKED, at(i), tile)
        }
        is Action.PlaceZone -> {
            val rgb = MapRenderer.ZONE_COLOURS[a.zone.toInt()]
            rect(a.x0, a.y0, a.x1, a.y1, camera, Color(0x55000000 or rgb), Color(0xE6000000.toInt() or rgb))
            for (i in p.blocked) drawRect(BLOCKED, at(i), tile)
        }
        is Action.Bulldoze -> rect(a.x0, a.y0, a.x1, a.y1, camera, BULLDOZE_FILL, BULLDOZE_EDGE)
        is Action.RenewArea -> {
            // The area faintly, and the tiles that'll be relaid.
            rect(a.x0, a.y0, a.x1, a.y1, camera, RENEW_FILL, RENEW_EDGE)
            for (i in p.plan.changes) drawRect(RENEW_TILE, at(i), tile)
        }
        is Action.PlaceParks -> {
            rect(a.x0, a.y0, a.x1, a.y1, camera, PARK_FILL, PARK_EDGE)
            for (i in p.blocked) drawRect(BLOCKED, at(i), tile)
        }
        is Action.PlantStreetTrees -> for (i in a.tiles) {
            drawRect(if (i in p.blocked) BLOCKED else PARK_FILL, at(i), tile)
        }
        is Action.BuildPowerLine -> for (i in a.tiles) {
            drawRect(if (i in p.blocked) BLOCKED else LINE_FILL, at(i), tile)
        }
        is Action.BuildRail -> for (i in a.tiles) {
            drawRect(if (i in p.blocked) BLOCKED else RAIL_FILL, at(i), tile)
        }
        is Action.BuildPipe -> for (i in a.tiles) {
            drawRect(if (i in p.blocked) BLOCKED else PIPE_FILL, at(i), tile)
        }
        is Action.RemovePipes -> rect(a.x0, a.y0, a.x1, a.y1, camera, BULLDOZE_FILL, BULLDOZE_EDGE)
        is Action.BuildBank -> for (i in a.tiles) {
            drawRect(if (i in p.blocked) BLOCKED else BANK_FILL, at(i), tile)
        }
        is Action.BuildTram -> for (i in a.tiles) {
            drawRect(if (i in p.blocked) BLOCKED else RAIL_FILL, at(i), tile)
        }
        is Action.BuildWire -> for (i in a.tiles) {
            drawRect(if (i in p.blocked) BLOCKED else LINE_FILL, at(i), tile)
        }
        is Action.BuildSubway -> for (i in a.tiles) {
            drawRect(if (i in p.blocked) BLOCKED else PIPE_FILL, at(i), tile)
        }
        is Action.PlaceStop -> {
            val ok = p.blocked.isEmpty()
            rect(a.x, a.y, a.x, a.y, camera, if (ok) PLACE_FILL else BLOCKED, if (ok) PLACE_EDGE else BULLDOZE_EDGE)
        }
        is Action.RemoveTransit -> rect(a.x0, a.y0, a.x1, a.y1, camera, BULLDOZE_FILL, BULLDOZE_EDGE)
        is Action.PlaceBuilding -> {
            val ok = p.plan.problem != Problem.Blocked && p.plan.problem != Problem.NeedsTrack && p.plan.problem != Problem.NeedsWater &&
                p.plan.problem != Problem.NeedsTramTrack && p.plan.problem != Problem.NeedsTunnel
            rect(a.x, a.y, a.x + a.type.width - 1, a.y + a.type.height - 1, camera, if (ok) PLACE_FILL else BLOCKED, if (ok) PLACE_EDGE else BULLDOZE_EDGE)
        }
    }
    if (p.plan.problem == Problem.NothingToDo || p.plan.problem == Problem.Blocked) return
    // The cost, just above and right of where the drag is.
    val style = TextStyle(
        color = if (p.plan.problem == Problem.NotEnoughMoney) Color(0xFFFF8A80) else Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val layout = measurer.measure(costText, style)
    val corner = camera.tileToScreen(p.endX + 1f, p.endY.toFloat(), size)
    val pad = 6f * density
    val box = Size(layout.size.width + 2 * pad, layout.size.height + pad)
    val topLeft = Offset(
        (corner.x + pad).coerceIn(0f, size.width - box.width),
        (corner.y - box.height - pad).coerceIn(0f, size.height - box.height),
    )
    drawRoundRect(LABEL, topLeft, box, CornerRadius(6f * density))
    drawText(layout, topLeft = Offset(topLeft.x + pad, topLeft.y + pad / 2))
}

/** A frame round the tile under the mouse, so it's clear where a tool will land. */
internal fun DrawScope.drawHover(x: Int, y: Int, camera: Camera) {
    val t = camera.tilePx
    drawRect(HOVER, camera.tileToScreen(x.toFloat(), y.toFloat(), size), Size(t, t), style = Stroke(maxOf(1f, 1.5f * density)))
}

private fun DrawScope.rect(x0: Int, y0: Int, x1: Int, y1: Int, camera: Camera, fill: Color, edge: Color) {
    val a = camera.tileToScreen(minOf(x0, x1).toFloat(), minOf(y0, y1).toFloat(), size)
    val b = camera.tileToScreen(maxOf(x0, x1) + 1f, maxOf(y0, y1) + 1f, size)
    val s = Size(b.x - a.x, b.y - a.y)
    drawRect(fill, a, s)
    drawRect(edge, a, s, style = Stroke(maxOf(1f, 2f * density)))
}

private val ROAD_FILL = Color(0x66FFFFFF)
private val ROAD_DRAG = Color(0x26FFFFFF)
private val RAIL_FILL = Color(0x668E7CC3)
private val PIPE_FILL = Color(0x664FA3E0)
private val BANK_FILL = Color(0x66A0B060)
private val BLOCKED = Color(0x80E53935)
private val BULLDOZE_FILL = Color(0x40E53935)
private val RENEW_FILL = Color(0x2240A0E0)
private val RENEW_EDGE = Color(0xCC40A0E0)
private val RENEW_TILE = Color(0x6640A0E0)
private val BULLDOZE_EDGE = Color(0xE6E53935)
private val HOVER = Color(0xCCFFFFFF)
private val LINE_FILL = Color(0x66FFD54F)
private val PARK_FILL = Color(0x4D6AAE4A)
private val PARK_EDGE = Color(0xE66AAE4A)
private val PLACE_FILL = Color(0x4DFFFFFF)
private val PLACE_EDGE = Color(0xE6FFFFFF)
private val LABEL = Color(0xD91C1F24)
