package com.rm.infill.ui

import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlin.math.ceil
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
import com.rm.infill.res.speed_fastest
import com.rm.infill.res.budget
import com.rm.infill.res.demand_high
import com.rm.infill.res.demand_none
import com.rm.infill.res.demand_over
import com.rm.infill.res.demand_some
import com.rm.infill.res.era
import com.rm.infill.res.name_colon_value
import com.rm.infill.res.people
import com.rm.infill.res.speed_fast
import com.rm.infill.res.speed_normal
import com.rm.infill.res.speed_slow
import com.rm.infill.res.state_off
import com.rm.infill.res.state_on
import com.rm.infill.res.list_join
import com.rm.infill.res.name_value
import com.rm.infill.res.date
import com.rm.infill.res.money_owed
import androidx.compose.ui.graphics.Color
import com.rm.infill.res.demand
import com.rm.infill.res.income
import com.rm.infill.res.jobs_label
import com.rm.infill.res.speed
import com.rm.infill.res.overlay
import com.rm.infill.res.menu
import com.rm.infill.res.upkeep
import com.rm.infill.res.funds
import com.rm.infill.res.money
import com.rm.infill.res.month_short
import com.rm.infill.res.pause
import com.rm.infill.res.paused
import com.rm.infill.res.play
import com.rm.infill.res.population
import com.rm.infill.res.redo
import com.rm.infill.res.undo
import com.rm.infill.res.year
import com.rm.infill.GameState
import com.rm.infill.sim.Balance
import com.rm.infill.sim.Zone
import com.rm.infill.sim.Precipitation
import com.rm.infill.res.temperature
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.max
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

/** Pause, the speed, the date, the money, the population and demand, along the top. */
@Composable
fun StatusStrip(
    game: GameState,
    onMenu: () -> Unit,
    paused: Boolean,
    onPause: () -> Unit,
    speed: Int,
    onSpeed: () -> Unit,
    overlayOn: Boolean,
    onOverlay: () -> Unit,
    onBudget: () -> Unit,
    onPeople: () -> Unit,
    onEra: () -> Unit,
    night: Boolean,
    compact: Boolean,
    twoLines: Boolean,
    /** Undo and redo up here, when the toolbar has no room for them. */
    withHistory: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
    /** Cameras the strip runs round, as stretches across the window in pixels: the buttons keep clear of them. */
    cameras: List<ClosedFloatingPointRange<Float>> = emptyList(),
    /** Opens the Demand window, from the demand bars. */
    onDemand: () -> Unit = {},
) {
    val c = Infill.colors
    game.revision
    val city = game.city
    val months = stringArrayResource(Res.array.month_short)
    val textSize = if (compact) 13.sp else 15.sp
    val button = if (compact) 36.dp else 40.dp
    val gap = if (compact) 8.dp else 10.dp
    val buttons = @Composable {
        SquareButton(selected = false, size = button, description = stringResource(Res.string.menu), onClick = onMenu) { tint ->
            MenuIcon(tint, Modifier.size(22.dp))
        }
        val label = stringResource(if (paused) Res.string.play else Res.string.pause)
        SquareButton(selected = paused, size = button, description = label, onClick = onPause) { tint ->
            PauseIcon(paused, tint, Modifier.size(22.dp))
        }
        val speedName = stringResource(listOf(Res.string.speed_slow, Res.string.speed_normal, Res.string.speed_fast, Res.string.speed_fastest)[speed.coerceIn(0, 3)])
        SquareButton(selected = false, size = button, description = stringResource(Res.string.speed), onClick = onSpeed, state = speedName) { tint ->
            SpeedIcon(speed, tint, Modifier.size(22.dp))
        }
        val viewState = stringResource(if (overlayOn) Res.string.state_on else Res.string.state_off)
        SquareButton(selected = overlayOn, size = button, description = stringResource(Res.string.overlay), onClick = onOverlay, state = viewState) { tint ->
            LayersIcon(tint, Modifier.size(22.dp))
        }
        Text(
            stringResource(Res.string.date, months.getOrElse(city.month) { "" }, city.year),
            color = c.text, fontSize = textSize, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClickLabel = stringResource(Res.string.era), role = Role.Button, onClick = onEra).padding(2.dp),
        )
    }
    val readings = @Composable {
        val w = city.weather
        WeatherReading(skyOf(w.fog, w.precipitation, w.cloud, night), w.temperature, compact && !twoLines, textSize)
        Text(
            moneyText(city.funds), color = if (city.funds < 0) Color(0xFFD84343) else c.text, fontSize = textSize,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClickLabel = stringResource(Res.string.budget), role = Role.Button, onClick = onBudget).padding(2.dp),
        )
        Box(Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClickLabel = stringResource(Res.string.people), role = Role.Button, onClick = onPeople).padding(2.dp)) {
            PersonCount(city.stats.population, textSize)
        }
        val st = city.stats
        // Homes over shops are wanted as far as both homes and shops are.
        val mixed = if (!city.allowsZone(Zone.MIXED)) null
        else if (st.residentialDemand > 0 && st.commercialDemand > 0) minOf(st.residentialDemand, st.commercialDemand * Balance.MIXED_PEOPLE_PER_JOB)
        else minOf(0, minOf(st.residentialDemand, st.commercialDemand * Balance.MIXED_PEOPLE_PER_JOB))
        DemandBars(
            st.residentialDemand, st.commercialDemand, st.industryDemand, if (city.allowsZone(Zone.OFFICE)) st.officeDemand else null, st.farmDemand, mixed, st.population + st.jobs,
            Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClickLabel = stringResource(Res.string.demand), role = Role.Button, onClick = onDemand).padding(2.dp).padding(end = 2.dp),
        )
    }
    ChromeBox(modifier) {
        // On an upright phone the readings go on a second line under the buttons.
        if (twoLines) {
            // Across the whole width: the buttons on the left and the date on the right, then the readings spread
            // out under them with undo and redo at the end.
            Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ClearOf(cameras, gap, Modifier.fillMaxWidth(), pinLast = true) { buttons() }
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    readings()
                    // Undo and redo live up here on an upright phone, so the toolbar keeps its labels.
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        HistoryButton(false, game.canUndo, 32.dp, onUndo)
                        HistoryButton(true, game.canRedo, 32.dp, onRedo)
                    }
                }
            }
        } else {
            Row(
                Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                buttons()
                readings()
                if (withHistory) {
                    HistoryButton(false, game.canUndo, button, onUndo)
                    HistoryButton(true, game.canRedo, button, onRedo)
                }
            }
        }
    }
}

/**
 * A row of [content] spaced by [spacing], each moved along past any of the
 * [cameras] (stretches across the window, in pixels) it would sit over. With
 * [pinLast], the last one goes at the far end, or as near it as the cameras let it.
 */
@Composable
private fun ClearOf(
    cameras: List<ClosedFloatingPointRange<Float>>,
    spacing: Dp,
    modifier: Modifier = Modifier,
    pinLast: Boolean = false,
    content: @Composable () -> Unit,
) {
    var left by remember { mutableFloatStateOf(0f) }
    val sorted = remember(cameras) { cameras.sortedBy { it.start } }
    Layout(content, modifier.onGloballyPositioned { left = it.positionInWindow().x }) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val space = spacing.roundToPx()
        val xs = IntArray(placeables.size)
        var x = 0
        for ((k, p) in placeables.withIndex()) {
            for (cam in sorted) {
                val from = cam.start - left - space
                val to = cam.endInclusive - left + space
                if (x < to && x + p.width > from) x = ceil(to).toInt()
            }
            xs[k] = x
            x += p.width + space
        }
        if (pinLast && placeables.isNotEmpty() && constraints.hasBoundedWidth) {
            val k = placeables.size - 1
            val w = placeables[k].width
            var end = constraints.maxWidth - w
            // Back from the far end past any camera, but never back over the one before.
            for (cam in sorted.reversed()) {
                val from = cam.start - left - space
                val to = cam.endInclusive - left + space
                if (end < to && end + w > from) end = (from - w).toInt()
            }
            if (end > xs[k]) xs[k] = end
            x = maxOf(x, xs[k] + w + space)
        }
        val width = (if (pinLast && constraints.hasBoundedWidth) constraints.maxWidth else x - space).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) { placeables.forEachIndexed { k, p -> p.place(xs[k], (height - p.height) / 2) } }
    }
}

/** What the sky is doing, from the day's weather. */
private fun skyOf(fog: Boolean, precipitation: Precipitation, cloud: Int, night: Boolean): Sky = when {
    fog -> Sky.Fog
    precipitation == Precipitation.Snow -> Sky.Snow
    precipitation == Precipitation.Rain -> Sky.Rain
    cloud >= 70 -> Sky.Cloudy
    night -> Sky.Night
    cloud >= 35 -> Sky.Partly
    else -> Sky.Clear
}

/**
 * A picture of the sky and the temperature in °C. On a small screen, just the
 * picture. Handed the values, since the weather itself changes in place.
 */
@Composable
private fun WeatherReading(sky: Sky, degrees: Int, compact: Boolean, textSize: androidx.compose.ui.unit.TextUnit) {
    val c = Infill.colors
    val temperature = stringResource(Res.string.temperature, if (degrees < 0) "\u2212${-degrees}" else "$degrees")
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = temperature },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        WeatherIcon(sky, c.textDim, Modifier.size(20.dp))
        if (!compact) Text(temperature, color = c.text, fontSize = textSize)
    }
}

@Composable
private fun PersonCount(count: Int, size: androidx.compose.ui.unit.TextUnit) {
    val c = Infill.colors
    val label = stringResource(Res.string.name_value, stringResource(Res.string.population), groupThousands(count.toLong()))
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        PersonIcon(c.textDim, Modifier.size(14.dp))
        Text(groupThousands(count.toLong()), color = c.text, fontSize = size)
    }
}

/**
 * Demand for each zone as small bars, mixed use once it's come in, up for
 * more wanted and down for too much, scaled to the size of the town ([townSize]
 * is its people and jobs). Numbers rather than the stats themselves, which
 * change in place and so wouldn't be seen to change.
 */
@Composable
fun DemandBars(residential: Int, commercial: Int, industrial: Int, office: Int?, farmland: Int, mixed: Int?, townSize: Int, modifier: Modifier = Modifier) {
    val c = Infill.colors
    val scale = max(20f, 0.06f * townSize)
    val zones = listOfNotNull(Zone.RESIDENTIAL, Zone.COMMERCIAL, Zone.INDUSTRIAL, if (office != null) Zone.OFFICE else null, Zone.FARMLAND, if (mixed != null) Zone.MIXED else null)
    val values = listOfNotNull(residential, commercial, industrial, office, farmland, mixed).map { (it / scale).coerceIn(-1f, 1f) }
    val colours = zones.map { zoneColour(it) }
    // Read out as each zone and how wanted it is.
    val names = zones.map { z -> stringResource(ZoneKind.entries.first { it.zone == z }.title) }
    val words = values.map {
        stringResource(
            when {
                it > 0.5f -> Res.string.demand_high
                it > 0.1f -> Res.string.demand_some
                it < -0.1f -> Res.string.demand_over
                else -> Res.string.demand_none
            },
        )
    }
    val label = stringResource(Res.string.name_colon_value, stringResource(Res.string.demand), listText(names.zip(words) { n, w -> stringResource(Res.string.name_colon_value, n, w) }))
    Canvas(modifier.size(width = (8 * values.size).dp, height = 28.dp).semantics { contentDescription = label }) {
        val bar = size.width / values.size
        val mid = size.height / 2f
        drawLine(c.chromeEdge, Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
        for (k in values.indices) {
            val h = values[k] * (mid - 1.dp.toPx())
            val left = k * bar + 1.dp.toPx()
            val w = bar - 2.dp.toPx()
            if (h > 0f) drawRect(colours[k], Offset(left, mid - h), Size(w, h))
            else if (h < 0f) drawRect(colours[k].copy(alpha = 0.5f), Offset(left, mid), Size(w, -h))
        }
    }
}
/**
 * The tool buttons, in a row along the bottom or a column down the side, with
 * undo and redo at the end. The row shares out the width rather than scrolling.
 */
@Composable
fun ToolBar(
    selected: ToolGroup,
    onSelect: (ToolGroup) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    vertical: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    withHistory: Boolean = true,
    focus: FocusRequester? = null,
) {
    val c = Infill.colors
    val side = if (compact) 36.dp else 40.dp
    // The chosen tool's button takes the focus when asked. Every button has a
    // requester, so choosing another tool doesn't rebuild a button and lose the focus.
    val others = remember { FocusRequester() }
    fun Modifier.chosen(group: ToolGroup) = focusRequester(if (focus != null && group == selected) focus else others)
    val divider = @Composable {
        Box(
            Modifier
                .padding(if (vertical) 0.dp else 2.dp, if (vertical) 2.dp else 0.dp)
                .background(c.chromeEdge)
                .then(if (vertical) Modifier.height(1.dp).width(side) else Modifier.width(1.dp).height(side)),
        )
    }
    if (vertical) {
        ChromeBox(modifier) {
            // Scrolls only when a very short screen can't fit the whole rail.
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                for (group in ToolGroup.entries) {
                    ToolButton(group, group == selected, compact, Modifier.width(if (compact) 56.dp else 64.dp).chosen(group)) { onSelect(group) }
                }
                if (withHistory) {
                    divider()
                    HistoryButton(false, canUndo, side, onUndo)
                    HistoryButton(true, canRedo, side, onRedo)
                }
            }
        }
    } else {
        ChromeBox(modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (group in ToolGroup.entries) {
                    ToolButton(group, group == selected, compact, Modifier.weight(1f).chosen(group)) { onSelect(group) }
                }
                if (withHistory) {
                    divider()
                    HistoryButton(false, canUndo, side, onUndo)
                    HistoryButton(true, canRedo, side, onRedo)
                }
            }
        }
    }
}

/** Undo, or redo when [redo]. Dimmed when there's nothing to do. */
@Composable
fun HistoryButton(redo: Boolean, enabled: Boolean, side: Dp, onClick: () -> Unit) {
    val c = Infill.colors
    val label = stringResource(if (redo) Res.string.redo else Res.string.undo)
    Box(
        Modifier
            .size(side)
            .clip(RoundedCornerShape(8.dp))
            .background(c.button)
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        UndoIcon(redo, if (enabled) c.text else c.textDim.copy(alpha = 0.4f), Modifier.size(22.dp))
    }
}

@Composable
private fun ToolButton(group: ToolGroup, selected: Boolean, compact: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Infill.colors
    val title = stringResource(group.title)
    val shape = RoundedCornerShape(8.dp)
    val tint = if (selected) c.onAccent else c.textDim
    Column(
        modifier
            .clip(shape)
            .background(if (selected) c.accent else c.button)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = if (compact) 4.dp else 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlyphIcon(groupGlyph(group), if (selected) c.onAccent else c.text, Modifier.size(if (compact) 24.dp else 28.dp))
        // Long names shrink to fit a narrow button rather than being cut off.
        BasicText(
            title, maxLines = 1, style = TextStyle(color = tint, textAlign = TextAlign.Center),
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = if (compact) 10.sp else 11.sp),
        )
    }
}

private fun groupGlyph(group: ToolGroup): Glyph = when (group) {
    ToolGroup.Inspect -> Glyph.Inspect
    ToolGroup.Bulldoze -> Glyph.Bulldoze
    ToolGroup.Zones -> Glyph.Zone
    ToolGroup.Transport -> Glyph.Road
    ToolGroup.Utilities -> Glyph.Utilities
    ToolGroup.Services -> Glyph.Civic
}

@Composable
private fun SquareButton(
    selected: Boolean,
    size: Dp,
    description: String,
    onClick: () -> Unit,
    /** How it's set, for screen readers, where the drawing shows it: the speed. */
    state: String? = null,
    content: @Composable (tint: androidx.compose.ui.graphics.Color) -> Unit,
) {
    val c = Infill.colors
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) c.accent else c.button)
            .semantics {
                contentDescription = description
                state?.let { stateDescription = it }
            }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content(if (selected) c.onAccent else c.text) }
}

/** The city at a glance, down the side on a tablet. */
@Composable
fun CityPanel(game: GameState, modifier: Modifier = Modifier) {
    game.revision
    val city = game.city
    val s = city.stats
    ChromeBox(modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            StatGrid(
                listOf(
                    StatItem(Glyph.Person, stringResource(Res.string.population), groupThousands(s.population.toLong())),
                    StatItem(Glyph.Briefcase, stringResource(Res.string.jobs_label), groupThousands(s.jobs.toLong())),
                    StatItem(Glyph.Coins, stringResource(Res.string.funds), moneyText(city.funds)),
                    StatItem(Glyph.Calendar, stringResource(Res.string.year), city.year.toString()),
                    StatItem(Glyph.Coin, stringResource(Res.string.income), moneyText(s.income)),
                    StatItem(Glyph.Wrench, stringResource(Res.string.upkeep), moneyText(s.upkeep)),
                ),
            )
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

/** An amount of money as $20,000, or −$861 when it's owed. */
@Composable
fun moneyText(amount: Long): String =
    if (amount < 0) stringResource(Res.string.money_owed, groupThousands(-amount)) else stringResource(Res.string.money, groupThousands(amount))

/**
 * How numbers are written in the language being shown: the mark between
 * thousands and the decimal mark. Set from the strings at the top of the
 * app, so plain functions can use them too.
 */
object Numbers {
    var group = ","
    var decimal = "."
}

/** 20000 as 20,000, or as the language groups thousands. */
fun groupThousands(n: Long): String {
    val digits = kotlin.math.abs(n).toString()
    val out = StringBuilder()
    digits.forEachIndexed { i, ch ->
        if (i > 0 && (digits.length - i) % 3 == 0) out.append(Numbers.group)
        out.append(ch)
    }
    return if (n < 0) "\u2212$out" else out.toString()
}

/** Things listed one after another: "trams, buses, trains", as the language lists them. */
@Composable
fun listText(items: List<String>): String {
    val join = stringResource(Res.string.list_join, "\u0000", "\u0001")
    return items.reduceOrNull { a, b -> join.replace("\u0000", a).replace("\u0001", b) } ?: ""
}

/** [tenths] tenths as 2.5, with the language's decimal mark. */
fun tenths(tenths: Int): String {
    val sign = if (tenths < 0) "\u2212" else ""
    val t = kotlin.math.abs(tenths)
    return "$sign${groupThousands((t / 10).toLong())}${Numbers.decimal}${t % 10}"
}
