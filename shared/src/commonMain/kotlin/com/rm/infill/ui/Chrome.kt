package com.rm.infill.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rm.infill.res.Res
import com.rm.infill.res.date
import com.rm.infill.res.funds
import com.rm.infill.res.money
import com.rm.infill.res.month_short
import com.rm.infill.res.pause
import com.rm.infill.res.paused
import com.rm.infill.res.play
import com.rm.infill.res.population
import com.rm.infill.res.year
import com.rm.infill.sim.City
import com.rm.infill.ui.theme.Infill
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

private val BarShape = RoundedCornerShape(12.dp)

/** A bar or panel floating over the map. */
@Composable
fun ChromeBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Infill.colors
    Box(
        modifier
            .clip(BarShape)
            .background(c.chrome)
            .border(1.dp, c.chromeEdge, BarShape),
    ) { content() }
}

/** Pause, the date and the money, along the top. */
@Composable
fun StatusStrip(city: City, paused: Boolean, onPause: () -> Unit, compact: Boolean, modifier: Modifier = Modifier) {
    val c = Infill.colors
    val months = stringArrayResource(Res.array.month_short)
    val textSize = if (compact) 13.sp else 15.sp
    ChromeBox(modifier) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            val label = stringResource(if (paused) Res.string.play else Res.string.pause)
            SquareButton(selected = paused, size = if (compact) 36.dp else 40.dp, description = label, onClick = onPause) { tint ->
                PauseIcon(paused, tint, Modifier.size(22.dp))
            }
            Text(
                stringResource(Res.string.date, months.getOrElse(city.month) { "" }, city.year),
                color = c.text, fontSize = textSize, fontWeight = FontWeight.SemiBold,
            )
            Text(stringResource(Res.string.money, groupThousands(city.funds)), color = c.text, fontSize = textSize)
            if (paused) Text(stringResource(Res.string.paused), color = c.textDim, fontSize = textSize)
            Spacer(Modifier.width(2.dp))
        }
    }
}

/** The tools, in a row along the bottom or a column down the side. */
@Composable
fun ToolBar(selected: Tool, onSelect: (Tool) -> Unit, vertical: Boolean, compact: Boolean, modifier: Modifier = Modifier) {
    val buttons = @Composable {
        for (tool in Tool.entries) {
            ToolButton(tool, tool == selected, compact) { onSelect(tool) }
        }
    }
    ChromeBox(modifier) {
        if (vertical) {
            Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { buttons() }
        } else {
            Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { buttons() }
        }
    }
}

@Composable
private fun ToolButton(tool: Tool, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val c = Infill.colors
    val title = stringResource(tool.title)
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .clip(shape)
            .background(if (selected) c.accent else c.button)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = if (compact) 4.dp else 6.dp)
            .width(if (compact) 44.dp else 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ToolIcon(tool, if (selected) c.onAccent else c.text, Modifier.size(if (compact) 24.dp else 28.dp))
        Text(title, color = if (selected) c.onAccent else c.textDim, fontSize = if (compact) 10.sp else 11.sp, maxLines = 1)
    }
}

@Composable
private fun SquareButton(
    selected: Boolean,
    size: Dp,
    description: String,
    onClick: () -> Unit,
    content: @Composable (tint: androidx.compose.ui.graphics.Color) -> Unit,
) {
    val c = Infill.colors
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) c.accent else c.button)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content(if (selected) c.onAccent else c.text) }
}

/** The city at a glance, down the side on a tablet. */
@Composable
fun CityPanel(city: City, modifier: Modifier = Modifier) {
    val c = Infill.colors
    ChromeBox(modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelLine(stringResource(Res.string.population), "0")
            PanelLine(stringResource(Res.string.funds), stringResource(Res.string.money, groupThousands(city.funds)))
            PanelLine(stringResource(Res.string.year), city.year.toString())
        }
    }
}

@Composable
private fun PanelLine(name: String, value: String) {
    val c = Infill.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, color = c.textDim, fontSize = 14.sp)
        Text(value, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 20000 as 20,000. */
fun groupThousands(n: Long): String {
    val digits = kotlin.math.abs(n).toString()
    val out = StringBuilder()
    digits.forEachIndexed { i, ch ->
        if (i > 0 && (digits.length - i) % 3 == 0) out.append(',')
        out.append(ch)
    }
    return if (n < 0) "-$out" else out.toString()
}
