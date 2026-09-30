package com.rm.infill.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** The toolbar's icons, drawn on a 24 unit square so they scale with the button. */
@Composable
fun ToolIcon(tool: Tool, colour: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        when (tool) {
            Tool.Inspect -> inspect(u, colour)
            Tool.Bulldoze -> bulldoze(u, colour)
            Tool.Road -> road(u, colour)
            Tool.Zone -> zone(u, colour)
        }
    }
}

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

private fun DrawScope.zone(u: Float, c: Color) {
    drawRect(
        c, Offset(3 * u, 3 * u), Size(18 * u, 18 * u),
        style = Stroke(1.6f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5f * u, 2 * u))),
    )
    for (i in 0..1) for (j in 0..1) {
        drawRect(c, Offset((6.5f + i * 6) * u, (6.5f + j * 6) * u), Size(4.5f * u, 4.5f * u))
    }
}
