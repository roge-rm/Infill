package com.rm.infill.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.BlendMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PauseIcon(paused: Boolean, colour: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        if (paused) {
            // Showing play, since pressing it plays.
            val p = Path().apply {
                moveTo(8 * u, 5 * u); lineTo(19 * u, 12 * u); lineTo(8 * u, 19 * u); close()
            }
            drawPath(p, colour)
        } else {
            drawRect(colour, Offset(7 * u, 5 * u), Size(3.5f * u, 14 * u))
            drawRect(colour, Offset(13.5f * u, 5 * u), Size(3.5f * u, 14 * u))
        }
    }
}

/** A curved arrow back, or forward when [redo]. */
@Composable
fun UndoIcon(redo: Boolean, colour: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        withTransform({ if (redo) scale(-1f, 1f, center) }) {
            val arc = Path().apply {
                moveTo(7 * u, 10 * u)
                lineTo(15 * u, 10 * u)
                cubicTo(22 * u, 10 * u, 22 * u, 20 * u, 15 * u, 20 * u)
                lineTo(10 * u, 20 * u)
            }
            drawPath(arc, colour, style = Stroke(2.4f * u, cap = StrokeCap.Round))
            val head = Path().apply {
                moveTo(3 * u, 10 * u); lineTo(9 * u, 5 * u); lineTo(9 * u, 15 * u); close()
            }
            drawPath(head, colour)
        }
    }
}

/** One, two or three chevrons for how fast the game runs. */
@Composable
fun SpeedIcon(speed: Int, colour: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val count = speed + 1
        val width = 5f * u
        val start = 12 * u - (count * width) / 2 + 1 * u
        for (k in 0 until count) {
            val x = start + k * width
            val p = Path().apply {
                moveTo(x, 6 * u); lineTo(x + 4 * u, 12 * u); lineTo(x, 18 * u)
            }
            drawPath(p, colour, style = Stroke(2.2f * u, cap = StrokeCap.Round))
        }
    }
}

/** What the sky is doing, as the top strip shows it. */
enum class Sky { Clear, Night, Partly, Cloudy, Rain, Snow, Fog }

/** A small picture of the sky. */
@Composable
fun WeatherIcon(sky: Sky, colour: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        fun cloud(cx: Float, cy: Float, c: Color) {
            drawCircle(c, 4.5f * u, Offset(cx - 3.5f * u, cy + 1 * u))
            drawCircle(c, 6f * u, Offset(cx + 1 * u, cy - 1 * u))
            drawCircle(c, 4f * u, Offset(cx + 6f * u, cy + 1.5f * u))
            drawRect(c, Offset(cx - 3.5f * u, cy + 1.5f * u), Size(9.5f * u, 4f * u))
        }
        when (sky) {
            Sky.Clear -> {
                drawCircle(SUN, 5 * u, Offset(12 * u, 12 * u))
                for (k in 0 until 8) {
                    val a = k * PI.toFloat() / 4
                    drawLine(SUN, Offset(12 * u + cos(a) * 7.5f * u, 12 * u + sin(a) * 7.5f * u), Offset(12 * u + cos(a) * 10 * u, 12 * u + sin(a) * 10 * u), 1.8f * u, StrokeCap.Round)
                }
            }
            Sky.Night -> {
                drawCircle(colour, 7 * u, Offset(12 * u, 12 * u))
                drawCircle(Color.Transparent, 6 * u, Offset(15 * u, 9 * u), blendMode = BlendMode.Clear)
            }
            Sky.Partly -> {
                drawCircle(SUN, 5 * u, Offset(15 * u, 8 * u))
                cloud(10 * u, 14 * u, colour)
            }
            Sky.Cloudy -> cloud(11 * u, 11 * u, colour)
            Sky.Rain -> {
                cloud(11 * u, 8 * u, colour)
                for (x in listOf(7f, 12f, 17f)) drawLine(RAIN_DROP, Offset(x * u, 17 * u), Offset((x - 1.5f) * u, 21 * u), 1.8f * u, StrokeCap.Round)
            }
            Sky.Snow -> {
                cloud(11 * u, 8 * u, colour)
                for (x in listOf(7f, 12f, 17f)) drawCircle(colour, 1.4f * u, Offset(x * u, 19 * u))
            }
            Sky.Fog -> for (k in 0 until 4) {
                val y = (6 + k * 4) * u
                drawLine(colour, Offset((4 + k % 2 * 2) * u, y), Offset((20 - k % 2 * 2) * u, y), 2f * u, StrokeCap.Round)
            }
        }
    }
}

private val SUN = Color(0xFFF2B233)
private val RAIN_DROP = Color(0xFF5A8FCC)

/** A head and shoulders, for the population. */
@Composable
fun PersonIcon(colour: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        drawCircle(colour, 5 * u, Offset(12 * u, 7 * u))
        drawRoundRect(colour, Offset(3 * u, 14 * u), Size(18 * u, 9 * u), CornerRadius(8 * u))
    }
}

/** A small civic building with a pediment and columns. */
private fun DrawScope.civic(u: Float, c: Color) {
    val roof = Path().apply { moveTo(3 * u, 9 * u); lineTo(12 * u, 3 * u); lineTo(21 * u, 9 * u); close() }
    drawPath(roof, c)
    for (x in listOf(5f, 10f, 15f)) drawRect(c, Offset(x * u, 10.5f * u), Size(3 * u, 8 * u))
    drawRect(c, Offset(3 * u, 19 * u), Size(18 * u, 2.5f * u))
}

/** Three lines, for the menu. */
@Composable
fun MenuIcon(colour: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        for (y in listOf(6f, 12f, 18f)) drawLine(colour, Offset(4 * u, y * u), Offset(20 * u, y * u), 2.4f * u, StrokeCap.Round)
    }
}

/** Stacked sheets, for the map views. */
@Composable
fun LayersIcon(colour: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        for (k in 0..2) {
            val y = (6 + k * 5) * u
            val p = Path().apply { moveTo(12 * u, y - 3 * u); lineTo(21 * u, y + 1.5f * u); lineTo(12 * u, y + 6 * u); lineTo(3 * u, y + 1.5f * u); close() }
            if (k == 0) drawPath(p, colour) else drawPath(p, colour, style = Stroke(1.6f * u))
        }
    }
}

private fun DrawScope.bolt(u: Float, c: Color) {
    val p = Path().apply {
        moveTo(13 * u, 2 * u); lineTo(5 * u, 13 * u); lineTo(11 * u, 13 * u)
        lineTo(9 * u, 22 * u); lineTo(19 * u, 10 * u); lineTo(13 * u, 10 * u); close()
    }
    drawPath(p, c)
}

private fun DrawScope.inspect(u: Float, c: Color) {
    drawCircle(c, 6.5f * u, Offset(10 * u, 10 * u), style = Stroke(2.5f * u))
    drawLine(c, Offset(15 * u, 15 * u), Offset(20 * u, 20 * u), 3f * u, StrokeCap.Round)
}

private fun DrawScope.bulldoze(u: Float, c: Color) {
    // Tracks, body, cab and the blade out front.
    drawRoundRect(c, Offset(6 * u, 16 * u), Size(15 * u, 4 * u), CornerRadius(2 * u), style = Stroke(1.8f * u))
    drawRect(c, Offset(8 * u, 11 * u), Size(11 * u, 4 * u))
    drawRect(c, Offset(13 * u, 6 * u), Size(5 * u, 5 * u), style = Stroke(1.8f * u))
    drawLine(c, Offset(3 * u, 10 * u), Offset(3 * u, 20 * u), 2.2f * u, StrokeCap.Round)
    drawLine(c, Offset(3 * u, 14 * u), Offset(8 * u, 13 * u), 1.8f * u)
}

private fun DrawScope.road(u: Float, c: Color) {
    drawLine(c, Offset(6 * u, 3 * u), Offset(6 * u, 21 * u), 2.2f * u)
    drawLine(c, Offset(18 * u, 3 * u), Offset(18 * u, 21 * u), 2.2f * u)
    drawLine(
        c, Offset(12 * u, 4 * u), Offset(12 * u, 21 * u), 1.8f * u,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * u, 3 * u)),
    )
}

private fun DrawScope.drop(u: Float, c: Color) {
    val path = Path().apply {
        moveTo(12 * u, 3 * u)
        cubicTo(12 * u, 3 * u, 5 * u, 11 * u, 5 * u, 15 * u)
        cubicTo(5 * u, 19 * u, 8 * u, 21.5f * u, 12 * u, 21.5f * u)
        cubicTo(16 * u, 21.5f * u, 19 * u, 19 * u, 19 * u, 15 * u)
        cubicTo(19 * u, 11 * u, 12 * u, 3 * u, 12 * u, 3 * u)
        close()
    }
    drawPath(path, c)
}

/** A tram from the front: its body, windscreen, pole up to the wire, and wheels. */
private fun DrawScope.tram(u: Float, c: Color) {
    drawLine(c, Offset(12 * u, 2 * u), Offset(12 * u, 6 * u), 1.6f * u)
    drawLine(c, Offset(7 * u, 2 * u), Offset(17 * u, 2 * u), 1.4f * u)
    drawRoundRect(c, Offset(5 * u, 6 * u), Size(14 * u, 13 * u), androidx.compose.ui.geometry.CornerRadius(2.5f * u))
    drawRect(c.copy(alpha = 0.35f), Offset(7 * u, 8 * u), Size(10 * u, 5 * u))
    drawCircle(c, 1.6f * u, Offset(8 * u, 21 * u))
    drawCircle(c, 1.6f * u, Offset(16 * u, 21 * u))
}

private fun DrawScope.districtIcon(u: Float, c: Color) {
    // A map pin over a dashed boundary.
    drawRect(c, Offset(3 * u, 9 * u), Size(18 * u, 12 * u), style = Stroke(1.6f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * u, 2 * u))))
    drawCircle(c, 4 * u, Offset(12 * u, 7 * u))
    val tip = Path().apply { moveTo(8.5f * u, 8.5f * u); lineTo(12 * u, 15 * u); lineTo(15.5f * u, 8.5f * u); close() }
    drawPath(tip, c)
    drawCircle(c.copy(alpha = 0.35f), 1.6f * u, Offset(12 * u, 7 * u))
}

private fun DrawScope.lights(u: Float, c: Color) {
    drawRoundRect(c, Offset(8 * u, 2 * u), Size(8 * u, 17 * u), androidx.compose.ui.geometry.CornerRadius(2.5f * u), style = Stroke(1.8f * u))
    for (k in 0 until 3) drawCircle(c, 1.9f * u, Offset(12 * u, (5.5f + k * 5f) * u))
    drawLine(c, Offset(12 * u, 19 * u), Offset(12 * u, 23 * u), 1.8f * u)
}

private fun DrawScope.rail(u: Float, c: Color) {
    drawLine(c, Offset(8 * u, 3 * u), Offset(8 * u, 21 * u), 1.8f * u)
    drawLine(c, Offset(16 * u, 3 * u), Offset(16 * u, 21 * u), 1.8f * u)
    for (k in 0 until 5) {
        val y = (4.5f + k * 3.8f) * u
        drawLine(c, Offset(5 * u, y), Offset(19 * u, y), 1.6f * u, cap = StrokeCap.Round)
    }
}

private fun DrawScope.zone(u: Float, c: Color) {
    drawRect(
        c, Offset(3 * u, 3 * u), Size(18 * u, 18 * u),
        style = Stroke(1.6f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5f * u, 2 * u))),
    )
    for (i in 0..1) for (j in 0..1) {
        drawRect(c, Offset((6.5f + i * 6) * u, (6.5f + j * 6) * u), Size(4.5f * u, 4.5f * u))
    }
}

/** Small drawings for the choices that have no sprite of their own, and for the map views. */
enum class Glyph {
    Inspect, Bulldoze, Road, Rail, Drop, Zone, Bolt, Civic, Tram, Lights, District, Utilities,
    Plus, Erase, List, Remove, Renew, Pipe, Bank, Tunnel, Route, Auto, Scrubber, Low, Medium, High, Rural, Tower,
    Smoke, Cuffs, Star, Flame, Car, Rain, Cap, Cross, Coins, Hourglass, Heat, Bin, Mountain, Crate,
    Arrows, Target, Pylon, Coin, Diamond, Tree, Bus, Manhole, Ladder, Ambulance, Sack, Glass, Hat,
    Person, Briefcase, Wrench, Calendar, Snow, Gavel, Tag, Check, Warn, Building, Cable, Phone, Mast,
    Anchor, Ship, Suitcase, Bridge, Plane, Speaker, Book, Ball, Mask, Bike,
}

/** The drawing for [tool]. */
fun toolGlyph(tool: Tool): Glyph = when (tool) {
    Tool.Inspect -> Glyph.Inspect
    Tool.Bulldoze -> Glyph.Bulldoze
    Tool.Road -> Glyph.Road
    Tool.Rail -> Glyph.Rail
    Tool.Water -> Glyph.Drop
    Tool.Phone -> Glyph.Phone
    Tool.Zone -> Glyph.Zone
    Tool.Power -> Glyph.Bolt
    Tool.Services -> Glyph.Civic
    Tool.Leisure -> Glyph.Tree
    Tool.Transit -> Glyph.Tram
    Tool.Traffic -> Glyph.Lights
    Tool.Port -> Glyph.Anchor
    Tool.Air -> Glyph.Plane
    Tool.Districts -> Glyph.District
}

/** The drawing for a kind of service. */
fun serviceGlyph(group: ServiceGroup): Glyph = when (group) {
    ServiceGroup.Police -> Glyph.Star
    ServiceGroup.Fire -> Glyph.Flame
    ServiceGroup.Health -> Glyph.Cross
    ServiceGroup.Schools -> Glyph.Cap
    ServiceGroup.Civic -> Glyph.Civic
    ServiceGroup.Parks -> Glyph.Tree
    ServiceGroup.Sport -> Glyph.Ball
    ServiceGroup.Culture -> Glyph.Mask
    ServiceGroup.Landmarks -> Glyph.Star
    ServiceGroup.Waste -> Glyph.Bin
}

/** [g] on a 24 unit square, [u] a unit. */
fun DrawScope.glyph(g: Glyph, u: Float, c: Color) {
    val w = 2f * u
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float = w) = drawLine(c, Offset(x0 * u, y0 * u), Offset(x1 * u, y1 * u), width, StrokeCap.Round)
    fun path(vararg p: Float): Path = Path().apply {
        moveTo(p[0] * u, p[1] * u)
        for (k in 2 until p.size step 2) lineTo(p[k] * u, p[k + 1] * u)
        close()
    }
    fun cloud(cx: Float, cy: Float) {
        drawCircle(c, 3.5f * u, Offset((cx - 4) * u, (cy + 1) * u))
        drawCircle(c, 4.5f * u, Offset(cx * u, (cy - 1) * u))
        drawCircle(c, 3.5f * u, Offset((cx + 4) * u, (cy + 1) * u))
        drawRect(c, Offset((cx - 4) * u, cy * u), Size(8 * u, 4.5f * u))
    }
    when (g) {
        Glyph.Inspect -> inspect(u, c)
        Glyph.Bulldoze -> bulldoze(u, c)
        Glyph.Road -> road(u, c)
        Glyph.Rail -> rail(u, c)
        Glyph.Drop -> drop(u, c)
        Glyph.Zone -> zone(u, c)
        Glyph.Bolt -> bolt(u, c)
        Glyph.Civic -> civic(u, c)
        Glyph.Tram -> tram(u, c)
        Glyph.Lights -> lights(u, c)
        Glyph.District -> districtIcon(u, c)
        Glyph.Utilities -> {
            // A bolt beside a drop.
            withTransform({ translate(-4.5f * u, 0f); scale(0.8f, 0.8f, Offset(12 * u, 12 * u)) }) { bolt(u, c) }
            withTransform({ translate(5f * u, 3f * u); scale(0.62f, 0.62f, Offset(12 * u, 12 * u)) }) { drop(u, c) }
        }
        Glyph.Plus -> { line(12f, 5f, 12f, 19f, 3f * u); line(5f, 12f, 19f, 12f, 3f * u) }
        Glyph.Erase -> {
            drawCircle(c, 7.5f * u, Offset(12 * u, 12 * u), style = Stroke(2.4f * u))
            line(6.7f, 17.3f, 17.3f, 6.7f, 2.4f * u)
        }
        Glyph.List -> for (k in 0..2) {
            drawCircle(c, 1.6f * u, Offset(5 * u, (6.5f + k * 5.5f) * u))
            line(9f, 6.5f + k * 5.5f, 20f, 6.5f + k * 5.5f)
        }
        Glyph.Remove -> { line(6f, 6f, 18f, 18f, 3f * u); line(18f, 6f, 6f, 18f, 3f * u) }
        Glyph.Renew -> {
            drawArc(c, 200f, 280f, false, Offset(5 * u, 5 * u), Size(14 * u, 14 * u), style = Stroke(2.4f * u, cap = StrokeCap.Round))
            drawPath(path(15f, 3f, 21f, 6.5f, 15f, 10f), c)
        }
        Glyph.Pipe -> {
            drawRect(c, Offset(2 * u, 9 * u), Size(20 * u, 6 * u))
            for (x in listOf(7f, 17f)) drawRect(c, Offset((x - 1.2f) * u, 7.5f * u), Size(2.4f * u, 9 * u))
        }
        Glyph.Bank -> {
            drawPath(path(3f, 17f, 7f, 7f, 17f, 7f, 21f, 17f), c)
            line(2f, 20.5f, 22f, 20.5f, 1.6f * u)
        }
        Glyph.Tunnel -> {
            drawArc(c, 180f, 180f, false, Offset(5 * u, 6 * u), Size(14 * u, 14 * u), style = Stroke(2.6f * u))
            line(5f, 13f, 5f, 20f, 2.6f * u); line(19f, 13f, 19f, 20f, 2.6f * u)
            line(2f, 20.5f, 22f, 20.5f, 1.6f * u)
        }
        Glyph.Route -> {
            val pts = listOf(4f to 18f, 10f to 8f, 15f to 15f, 20f to 5f)
            for (k in 0 until pts.size - 1) line(pts[k].first, pts[k].second, pts[k + 1].first, pts[k + 1].second, 1.8f * u)
            for ((x, y) in pts) drawCircle(c, 2.4f * u, Offset(x * u, y * u))
        }
        Glyph.Auto -> {
            // A small cog.
            for (k in 0 until 8) {
                val a = k * PI.toFloat() / 4
                line(12 + 6.5f * cos(a), 12 + 6.5f * sin(a), 12 + 9f * cos(a), 12 + 9f * sin(a), 2.6f * u)
            }
            drawCircle(c, 5.5f * u, Offset(12 * u, 12 * u), style = Stroke(2.6f * u))
        }
        Glyph.Scrubber -> {
            drawRect(c, Offset(9 * u, 8 * u), Size(6 * u, 14 * u))
            for (y in listOf(4.5f, 2f)) line(7f, y, 17f, y, 1.4f * u)
            drawRect(c, Offset(7 * u, 6 * u), Size(10 * u, 2.4f * u))
        }
        Glyph.Low, Glyph.Medium, Glyph.High -> {
            val n = g.ordinal - Glyph.Low.ordinal + 1
            for (k in 0 until n) drawRect(c, Offset((4 + k * 6) * u, (20 - 5 - k * 5) * u), Size(4.5f * u, (5 + k * 5) * u))
            line(2f, 20.5f, 22f, 20.5f, 1.4f * u)
        }
        Glyph.Rural -> {
            // A low house with a roof, and a tree well off from it.
            drawRect(c, Offset(3 * u, 14 * u), Size(7 * u, 6 * u))
            val roof = Path().apply { moveTo(2 * u, 14.5f * u); lineTo(6.5f * u, 10 * u); lineTo(11 * u, 14.5f * u); close() }
            drawPath(roof, c)
            drawCircle(c, 3.5f * u, Offset(18 * u, 12 * u))
            line(18f, 15f, 18f, 20f, 1.6f * u)
            line(2f, 20.5f, 22f, 20.5f, 1.4f * u)
        }
        Glyph.Tower -> {
            // The three blocks of high density beside a tower with a mast.
            for (k in 0 until 3) drawRect(c, Offset((2 + k * 4.5f) * u, (17 - k * 4) * u), Size(3.5f * u, (3 + k * 4) * u))
            drawRect(c, Offset(15.5f * u, 4 * u), Size(6 * u, 16 * u))
            line(18.5f, 1f, 18.5f, 4f, 1.2f * u)
            line(2f, 20.5f, 22f, 20.5f, 1.4f * u)
        }
        Glyph.Smoke -> { cloud(12f, 10f); drawCircle(c, 2.4f * u, Offset(7 * u, 19 * u)); drawCircle(c, 1.6f * u, Offset(4 * u, 22 * u)) }
        Glyph.Cuffs -> {
            drawCircle(c, 4.5f * u, Offset(7 * u, 15 * u), style = Stroke(2.2f * u))
            drawCircle(c, 4.5f * u, Offset(17 * u, 15 * u), style = Stroke(2.2f * u))
            line(10f, 9f, 14f, 9f, 1.8f * u); line(9f, 11f, 10f, 9f, 1.8f * u); line(15f, 11f, 14f, 9f, 1.8f * u)
        }
        Glyph.Star -> {
            val p = Path()
            for (k in 0 until 10) {
                val r = if (k % 2 == 0) 10f else 4.2f
                val a = -PI.toFloat() / 2 + k * PI.toFloat() / 5
                val x = (12 + r * cos(a)) * u
                val y = (13 + r * sin(a)) * u
                if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            drawPath(p, c)
        }
        Glyph.Flame -> {
            val p = Path().apply {
                moveTo(12 * u, 2 * u)
                cubicTo(14 * u, 7 * u, 20 * u, 10 * u, 19 * u, 16 * u)
                cubicTo(18 * u, 20 * u, 15 * u, 22 * u, 12 * u, 22 * u)
                cubicTo(8 * u, 22 * u, 5 * u, 19 * u, 5 * u, 15 * u)
                cubicTo(5 * u, 11 * u, 9 * u, 10 * u, 9 * u, 6 * u)
                cubicTo(11 * u, 8 * u, 11 * u, 10 * u, 12 * u, 12 * u)
                cubicTo(13 * u, 9 * u, 13 * u, 5 * u, 12 * u, 2 * u)
                close()
            }
            drawPath(p, c)
        }
        Glyph.Car -> {
            drawRoundRect(c, Offset(2 * u, 11 * u), Size(20 * u, 6 * u), CornerRadius(1.5f * u))
            drawPath(path(6f, 11.5f, 8.5f, 6f, 15.5f, 6f, 18f, 11.5f), c)
            drawCircle(c, 2.5f * u, Offset(7 * u, 18.5f * u))
            drawCircle(c, 2.5f * u, Offset(17 * u, 18.5f * u))
        }
        Glyph.Rain -> {
            cloud(12f, 8f)
            for (x in listOf(7f, 12f, 17f)) line(x, 16f, x - 1.5f, 21f, 1.8f * u)
        }
        Glyph.Cap -> {
            drawPath(path(12f, 4f, 23f, 9f, 12f, 14f, 1f, 9f), c)
            drawPath(path(6f, 11f, 18f, 11f, 18f, 17f, 12f, 19f, 6f, 17f), c)
            line(20f, 10f, 20f, 17f, 1.4f * u)
        }
        Glyph.Cross -> {
            drawRect(c, Offset(9 * u, 3 * u), Size(6 * u, 18 * u))
            drawRect(c, Offset(3 * u, 9 * u), Size(18 * u, 6 * u))
        }
        Glyph.Coins -> for (k in 0..3) {
            drawOval(c, Offset(5 * u, (16 - k * 3.6f) * u), Size(14 * u, 5 * u))
            drawOval(c.copy(alpha = 0.4f), Offset(5 * u, (16 - k * 3.6f) * u), Size(14 * u, 5 * u), style = Stroke(0.8f * u))
        }
        Glyph.Coin -> {
            drawCircle(c, 9 * u, Offset(12 * u, 12 * u), style = Stroke(2f * u))
            line(12f, 6f, 12f, 18f, 1.6f * u)
            drawArc(c, 100f, 250f, false, Offset(8.5f * u, 7.5f * u), Size(7 * u, 4.5f * u), style = Stroke(1.8f * u))
            drawArc(c, 280f, 250f, false, Offset(8.5f * u, 12 * u), Size(7 * u, 4.5f * u), style = Stroke(1.8f * u))
        }
        Glyph.Hourglass -> {
            line(5f, 3f, 19f, 3f); line(5f, 21f, 19f, 21f)
            drawPath(path(7f, 4f, 17f, 4f, 12f, 12f), c)
            drawPath(path(12f, 12f, 17f, 20f, 7f, 20f), c, style = Stroke(1.8f * u))
            drawPath(path(12f, 15f, 15f, 20f, 9f, 20f), c)
        }
        Glyph.Heat -> {
            drawRoundRect(c, Offset(9.5f * u, 2 * u), Size(5 * u, 14 * u), CornerRadius(2.5f * u), style = Stroke(1.8f * u))
            drawCircle(c, 4.5f * u, Offset(12 * u, 18 * u))
            line(12f, 8f, 12f, 16f, 2.2f * u)
        }
        Glyph.Bin -> {
            drawPath(path(5f, 8f, 19f, 8f, 17f, 22f, 7f, 22f), c)
            drawRect(c, Offset(3 * u, 5 * u), Size(18 * u, 2 * u))
            drawRect(c, Offset(9.5f * u, 3 * u), Size(5 * u, 2 * u))
        }
        Glyph.Mountain -> {
            drawPath(path(1f, 20f, 9f, 6f, 17f, 20f), c)
            drawPath(path(10f, 20f, 16f, 10f, 23f, 20f), c)
        }
        Glyph.Plane -> {
            // Seen from above, nose up.
            drawPath(path(12f, 2f, 13.5f, 4f, 13.5f, 10f, 22f, 14f, 22f, 16f, 13.5f, 14f, 13.5f, 19f, 16f, 21f, 16f, 22.5f, 12f, 21.5f, 8f, 22.5f, 8f, 21f, 10.5f, 19f, 10.5f, 14f, 2f, 16f, 2f, 14f, 10.5f, 10f, 10.5f, 4f), c)
        }
        Glyph.Bridge -> {
            // An arch over the water, the deck across its top.
            drawRect(c, Offset(2 * u, 8 * u), Size(20 * u, 2.4f * u))
            val arch = Path().apply { moveTo(3 * u, 20 * u); quadraticTo(12 * u, 4 * u, 21 * u, 20 * u) }
            drawPath(arch, c, style = Stroke(2.2f * u))
            for (x in listOf(7f, 12f, 17f)) line(x, 10f, x, if (x == 12f) 12f else 14f, 1.6f * u)
            drawRect(c.copy(alpha = 0.4f), Offset(1 * u, 20 * u), Size(22 * u, 2 * u))
        }
        Glyph.Anchor -> {
            drawCircle(c, 2.2f * u, Offset(12 * u, 4.5f * u), style = Stroke(1.8f * u))
            line(12f, 7f, 12f, 21f, 2.2f * u)
            line(7f, 10f, 17f, 10f, 2f * u)
            val p = Path().apply {
                moveTo(4 * u, 14 * u); quadraticTo(5 * u, 21 * u, 12 * u, 21 * u); quadraticTo(19 * u, 21 * u, 20 * u, 14 * u)
            }
            drawPath(p, c, style = Stroke(2.2f * u))
            drawPath(path(2f, 15f, 6f, 13f, 5.5f, 17f), c)
            drawPath(path(22f, 15f, 18f, 13f, 18.5f, 17f), c)
        }
        Glyph.Ship -> {
            drawPath(path(2f, 14f, 22f, 14f, 19f, 20f, 5f, 20f), c)
            drawRect(c, Offset(7 * u, 9 * u), Size(10 * u, 5 * u))
            drawRect(c, Offset(13 * u, 4 * u), Size(3 * u, 5 * u))
        }
        Glyph.Suitcase -> {
            drawRoundRect(c, Offset(3 * u, 8 * u), Size(18 * u, 13 * u), CornerRadius(2 * u))
            drawRect(c, Offset(9 * u, 4 * u), Size(6 * u, 4 * u), style = Stroke(1.8f * u))
            drawRect(c.copy(alpha = 0.35f), Offset(8 * u, 8 * u), Size(1.5f * u, 13 * u))
            drawRect(c.copy(alpha = 0.35f), Offset(14.5f * u, 8 * u), Size(1.5f * u, 13 * u))
        }
        Glyph.Crate -> {
            drawRect(c, Offset(4 * u, 4 * u), Size(16 * u, 16 * u), style = Stroke(2.2f * u))
            line(4f, 4f, 20f, 20f, 1.8f * u); line(20f, 4f, 4f, 20f, 1.8f * u)
        }
        Glyph.Arrows -> {
            line(3f, 8f, 19f, 8f); drawPath(path(21f, 8f, 16f, 4f, 16f, 12f), c)
            line(5f, 16f, 21f, 16f); drawPath(path(3f, 16f, 8f, 12f, 8f, 20f), c)
        }
        Glyph.Ball -> {
            // A football: a ring, a patch in the middle and seams out from it.
            drawCircle(c, 9 * u, Offset(12 * u, 12 * u), style = Stroke(1.8f * u))
            drawPath(path(12f, 8.5f, 15.3f, 11f, 14f, 15f, 10f, 15f, 8.7f, 11f), c)
            line(12f, 8.5f, 12f, 3.5f, 1.4f * u); line(15.3f, 11f, 20f, 9.5f, 1.4f * u); line(8.7f, 11f, 4f, 9.5f, 1.4f * u)
            line(14f, 15f, 17f, 19.5f, 1.4f * u); line(10f, 15f, 7f, 19.5f, 1.4f * u)
        }
        Glyph.Mask -> {
            // The theatre's two masks: a frowning one behind, a smiling one in front, eyes and mouths cut through.
            fun mask(x: Float, y: Float, smile: Boolean): Path = Path().apply {
                fillType = PathFillType.EvenOdd
                // The face: broad at the brow, round at the chin.
                moveTo(x * u, y * u)
                lineTo((x + 12) * u, y * u)
                quadraticTo((x + 12.5f) * u, (y + 9) * u, (x + 6) * u, (y + 14) * u)
                quadraticTo((x - 0.5f) * u, (y + 9) * u, x * u, y * u)
                close()
                addOval(Rect(Offset((x + 2) * u, (y + 3) * u), Size(3f * u, 2.2f * u)))
                addOval(Rect(Offset((x + 7) * u, (y + 3) * u), Size(3f * u, 2.2f * u)))
                // The mouth, curved up for the smile and down for the frown.
                val my = if (smile) y + 8 else y + 9.5f
                moveTo((x + 3) * u, my * u)
                quadraticTo((x + 6) * u, (if (smile) my + 3.5f else my - 2.5f) * u, (x + 9) * u, my * u)
                quadraticTo((x + 6) * u, (if (smile) my + 1.8f else my - 1f) * u, (x + 3) * u, my * u)
                close()
            }
            drawPath(mask(10f, 2f, smile = false), c.copy(alpha = 0.55f))
            drawPath(mask(2f, 7f, smile = true), c)
        }
        Glyph.Target -> {
            drawCircle(c, 9 * u, Offset(12 * u, 12 * u), style = Stroke(1.8f * u))
            drawCircle(c, 5 * u, Offset(12 * u, 12 * u), style = Stroke(1.8f * u))
            drawCircle(c, 1.8f * u, Offset(12 * u, 12 * u))
        }
        Glyph.Bus -> {
            drawRoundRect(c, Offset(2 * u, 6 * u), Size(20 * u, 12 * u), CornerRadius(2.5f * u))
            for (k in 0 until 4) drawRect(c.copy(alpha = 0.35f), Offset((4 + k * 4.5f) * u, 8 * u), Size(3.2f * u, 4 * u))
            drawCircle(c, 2.4f * u, Offset(7 * u, 19 * u))
            drawCircle(c, 2.4f * u, Offset(17 * u, 19 * u))
        }
        Glyph.Bike -> {
            // Two wheels, the frame between them and the handlebars.
            val line = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawCircle(c, 4.5f * u, Offset(6 * u, 15 * u), style = line)
            drawCircle(c, 4.5f * u, Offset(18 * u, 15 * u), style = line)
            val frame = Path().apply {
                moveTo(6 * u, 15 * u); lineTo(10 * u, 8 * u); lineTo(16 * u, 8 * u); lineTo(12 * u, 15 * u); close()
                moveTo(16 * u, 8 * u); lineTo(18 * u, 15 * u)
                moveTo(15 * u, 5.5f * u); lineTo(17.5f * u, 5.5f * u); moveTo(16 * u, 5.5f * u); lineTo(16 * u, 8 * u)
                moveTo(9 * u, 6.5f * u); lineTo(11.5f * u, 6.5f * u)
            }
            drawPath(frame, c, style = line)
        }
        Glyph.Book -> {
            // An open book: two pages from the spine.
            val left = Path().apply {
                moveTo(12 * u, 7 * u); quadraticTo(8 * u, 4.5f * u, 3 * u, 5 * u); lineTo(3 * u, 18 * u)
                quadraticTo(8 * u, 17.5f * u, 12 * u, 20 * u); close()
            }
            val right = Path().apply {
                moveTo(12 * u, 7 * u); quadraticTo(16 * u, 4.5f * u, 21 * u, 5 * u); lineTo(21 * u, 18 * u)
                quadraticTo(16 * u, 17.5f * u, 12 * u, 20 * u); close()
            }
            drawPath(left, c, style = Stroke(1.8f * u, join = StrokeJoin.Round))
            drawPath(right, c, style = Stroke(1.8f * u, join = StrokeJoin.Round))
        }
        Glyph.Speaker -> {
            // A speaker and two waves coming off it.
            val p = Path().apply {
                moveTo(3 * u, 9 * u); lineTo(7 * u, 9 * u); lineTo(12 * u, 4.5f * u); lineTo(12 * u, 19.5f * u)
                lineTo(7 * u, 15 * u); lineTo(3 * u, 15 * u); close()
            }
            drawPath(p, c)
            drawArc(c, -50f, 100f, false, Offset(10 * u, 8 * u), Size(8 * u, 8 * u), style = Stroke(2 * u, cap = StrokeCap.Round))
            drawArc(c, -50f, 100f, false, Offset(9 * u, 4.5f * u), Size(13 * u, 15 * u), style = Stroke(2 * u, cap = StrokeCap.Round))
        }
        Glyph.Phone -> {
            // An old handset.
            val p = Path().apply {
                moveTo(5 * u, 4 * u); lineTo(9 * u, 4 * u); lineTo(10 * u, 8 * u); lineTo(8 * u, 10 * u)
                quadraticTo(10 * u, 14 * u, 14 * u, 16 * u)
                lineTo(16 * u, 14 * u); lineTo(20 * u, 15 * u); lineTo(20 * u, 19 * u)
                quadraticTo(10 * u, 20 * u, 5 * u, 4 * u)
                close()
            }
            drawPath(p, c)
        }
        Glyph.Mast -> {
            line(12f, 4f, 7f, 22f, 1.8f * u); line(12f, 4f, 17f, 22f, 1.8f * u)
            line(9f, 14f, 15f, 14f, 1.6f * u); line(8f, 18f, 16f, 18f, 1.6f * u)
            drawArc(c, 200f, 140f, false, Offset(5 * u, 0f), Size(14 * u, 10 * u), style = Stroke(1.6f * u))
            drawArc(c, 200f, 140f, false, Offset(1 * u, -3 * u), Size(22 * u, 15 * u), style = Stroke(1.6f * u))
        }
        Glyph.Cable -> {
            // The ground, and a cable running under it.
            drawRect(c.copy(alpha = 0.45f), Offset(2 * u, 5 * u), Size(20 * u, 3 * u))
            for (x in listOf(5f, 11f, 17f)) line(x, 9f, x - 2f, 12f, 1.2f * u)
            line(2f, 17f, 22f, 17f, 3.4f * u)
            drawCircle(c, 2.6f * u, Offset(5 * u, 17 * u))
        }
        Glyph.Person -> {
            drawCircle(c, 4.5f * u, Offset(12 * u, 7 * u))
            drawRoundRect(c, Offset(4 * u, 13 * u), Size(16 * u, 9 * u), CornerRadius(7 * u))
        }
        Glyph.Briefcase -> {
            drawRoundRect(c, Offset(3 * u, 8 * u), Size(18 * u, 12 * u), CornerRadius(2 * u))
            drawRect(c, Offset(9 * u, 4.5f * u), Size(6 * u, 2 * u))
            line(9f, 5f, 9f, 8f, 1.8f * u); line(15f, 5f, 15f, 8f, 1.8f * u)
            drawRect(c.copy(alpha = 0.35f), Offset(3 * u, 12.5f * u), Size(18 * u, 1.6f * u))
        }
        Glyph.Wrench -> {
            line(6f, 18f, 15f, 9f, 3.2f * u)
            drawCircle(c, 5 * u, Offset(16.5f * u, 7.5f * u), style = Stroke(2.6f * u))
        }
        Glyph.Calendar -> {
            drawRoundRect(c, Offset(3 * u, 5 * u), Size(18 * u, 16 * u), CornerRadius(2 * u), style = Stroke(2 * u))
            drawRect(c, Offset(3 * u, 5 * u), Size(18 * u, 5 * u))
            line(8f, 3f, 8f, 7f, 2f * u); line(16f, 3f, 16f, 7f, 2f * u)
            for (k in 0..2) drawCircle(c, 1.2f * u, Offset((7.5f + k * 4.5f) * u, 15 * u))
        }
        Glyph.Snow -> for (k in 0 until 3) {
            val a = k * PI.toFloat() / 3
            line(12 - 9 * cos(a), 12 - 9 * sin(a), 12 + 9 * cos(a), 12 + 9 * sin(a), 2f * u)
        }
        Glyph.Gavel -> {
            withTransform({ rotate(-40f, Offset(12 * u, 12 * u)) }) {
                drawRoundRect(c, Offset(6 * u, 3 * u), Size(12 * u, 6 * u), CornerRadius(1.5f * u))
                drawRect(c, Offset(11 * u, 9 * u), Size(2.2f * u, 11 * u))
            }
            line(3f, 21f, 13f, 21f, 2.2f * u)
        }
        Glyph.Tag -> {
            drawPath(path(3f, 12f, 11f, 4f, 20f, 4f, 20f, 13f, 12f, 21f), c)
            drawCircle(c.copy(alpha = 0.35f), 1.8f * u, Offset(16 * u, 8 * u))
        }
        Glyph.Check -> {
            line(5f, 13f, 10f, 18f, 3f * u); line(10f, 18f, 19f, 7f, 3f * u)
        }
        Glyph.Warn -> {
            drawPath(path(12f, 3f, 22f, 20f, 2f, 20f), c)
            drawRect(c.copy(alpha = 0.4f), Offset(11 * u, 8.5f * u), Size(2 * u, 6 * u))
            drawCircle(c.copy(alpha = 0.4f), 1.2f * u, Offset(12 * u, 17 * u))
        }
        Glyph.Building -> {
            drawRect(c, Offset(5 * u, 4 * u), Size(14 * u, 17 * u))
            for (r in 0..2) for (k in 0..1) drawRect(c.copy(alpha = 0.35f), Offset((7.5f + k * 5.5f) * u, (6.5f + r * 4.5f) * u), Size(3 * u, 2.5f * u))
            line(3f, 21f, 21f, 21f, 1.6f * u)
        }
        Glyph.Sack -> {
            // A swag bag, tied at the neck.
            drawOval(c, Offset(4 * u, 8 * u), Size(16 * u, 14 * u))
            drawPath(path(9f, 9f, 15f, 9f, 17f, 4f, 7f, 4f), c)
            line(8f, 9f, 16f, 9f, 1.6f * u)
        }
        Glyph.Glass -> {
            // A cocktail glass.
            drawPath(path(4f, 4f, 20f, 4f, 12f, 13f), c)
            line(12f, 13f, 12f, 20f, 2f * u)
            line(7f, 21f, 17f, 21f, 2.2f * u)
        }
        Glyph.Hat -> {
            // A fedora.
            drawOval(c, Offset(2 * u, 14 * u), Size(20 * u, 6 * u))
            drawPath(path(6f, 16f, 7f, 7f, 12f, 9f, 17f, 7f, 18f, 16f), c)
            line(7f, 13f, 17f, 13f, 1.6f * u)
        }
        Glyph.Ladder -> {
            line(7f, 2f, 9f, 22f, 2.2f * u); line(17f, 2f, 15f, 22f, 2.2f * u)
            for (k in 0 until 5) line(7.4f + k * 0.1f, 5f + k * 4f, 16.6f - k * 0.1f, 5f + k * 4f, 1.8f * u)
        }
        Glyph.Ambulance -> {
            drawRoundRect(c, Offset(2 * u, 7 * u), Size(20 * u, 11 * u), CornerRadius(2 * u))
            drawCircle(c, 2.4f * u, Offset(7 * u, 19 * u))
            drawCircle(c, 2.4f * u, Offset(17 * u, 19 * u))
            drawRect(c.copy(alpha = 0.35f), Offset(10.5f * u, 8.5f * u), Size(3 * u, 8 * u))
            drawRect(c.copy(alpha = 0.35f), Offset(8 * u, 11 * u), Size(8 * u, 3 * u))
            drawRect(c, Offset(10 * u, 4 * u), Size(4 * u, 3 * u))
        }
        Glyph.Manhole -> {
            drawCircle(c, 9 * u, Offset(12 * u, 12 * u), style = Stroke(2.2f * u))
            for (y in listOf(8f, 12f, 16f)) line(6.5f, y, 17.5f, y, 1.8f * u)
        }
        Glyph.Tree -> {
            drawCircle(c, 6 * u, Offset(12 * u, 9 * u))
            drawCircle(c, 4.5f * u, Offset(7.5f * u, 12.5f * u))
            drawCircle(c, 4.5f * u, Offset(16.5f * u, 12.5f * u))
            drawRect(c, Offset(10.8f * u, 14 * u), Size(2.4f * u, 8 * u))
        }
        Glyph.Diamond -> drawPath(path(12f, 4f, 18f, 12f, 12f, 20f, 6f, 12f), c, style = Stroke(2.4f * u))
        Glyph.Pylon -> {
            line(12f, 2f, 5f, 22f, 1.8f * u); line(12f, 2f, 19f, 22f, 1.8f * u)
            line(4f, 7f, 20f, 7f, 1.8f * u); line(6f, 12f, 18f, 12f, 1.8f * u)
            line(8.5f, 12f, 15.5f, 18f, 1.4f * u); line(15.5f, 12f, 8.5f, 18f, 1.4f * u)
        }
    }
}

/** [g] in [colour], for a toolbar button or a choice. */
@Composable
fun GlyphIcon(g: Glyph, colour: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { glyph(g, size.minDimension / 24f, colour) }
}
