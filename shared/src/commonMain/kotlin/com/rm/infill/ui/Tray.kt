package com.rm.infill.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rm.infill.map.Atlas
import com.rm.infill.map.BuildingSprites
import com.rm.infill.map.Overlay
import com.rm.infill.map.TileAtlas
import com.rm.infill.res.Res
import com.rm.infill.res.choice_building
import com.rm.infill.res.close_view
import com.rm.infill.res.fold_choices
import com.rm.infill.res.per_tile
import com.rm.infill.res.views_town
import com.rm.infill.res.group_utilities
import com.rm.infill.res.group_transport
import com.rm.infill.res.tool_services
import com.rm.infill.res.unfold_choices
import com.rm.infill.sim.BuildingType
import com.rm.infill.sim.City
import com.rm.infill.sim.Pipe
import com.rm.infill.sim.Prices
import com.rm.infill.sim.RoadType
import com.rm.infill.ui.theme.Infill
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.math.min

/**
 * How a choice looks: atlas [sprites] drawn one over another, on a [back]
 * colour that runs to [backTo], with a [glyph] or a few [letters] on top.
 */
class ChoiceIcon(
    val sprites: IntArray = IntArray(0),
    val back: Color? = null,
    val backTo: Color? = null,
    val glyph: Glyph? = null,
    val glyphColour: Color? = null,
    val letters: String? = null,
)

/** A tab over a tray's tiles: another tool on the same button, or a kind of service. */
class TrayTab(val key: Any, val title: String, val glyph: Glyph)

/** One of a tool's choices, as the tray shows it. */
class Choice<T>(val value: T, val name: String, val icon: ChoiceIcon, val detail: String? = null)

/**
 * A tool's choices as a grid of tiles that wraps rather than scrolls, under a
 * line naming the one picked. [tabs] split them up, [tab] being the one open.
 * Folded, it's only the line, which opens it again.
 */
@Composable
fun <T> ChoiceTray(
    atlas: TileAtlas?,
    choices: List<Choice<T>>,
    selected: T,
    onChoose: (T) -> Unit,
    folded: Boolean,
    onFold: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<TrayTab> = emptyList(),
    tab: Any? = null,
    onTab: (Any) -> Unit = {},
    onClose: (() -> Unit)? = null,
    /** What the line says while nothing's picked. */
    blank: String = "",
    extras: @Composable RowScope.() -> Unit = {},
) {
    val c = Infill.colors
    val current = choices.firstOrNull { it.value == selected }
    ChromeBox(modifier) {
        Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!folded && tabs.size > 1) {
                // A few side by side with their names; more stack the name under the drawing to fit.
                val stacked = tabs.size > STACK_TABS
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (t in tabs) TabButton(t, t.key == tab, stacked, Modifier.weight(1f)) { onTab(t.key) }
                }
            }
            val foldLabel = stringResource(if (folded) Res.string.unfold_choices else Res.string.fold_choices)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClickLabel = foldLabel) { onFold(!folded) }
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (folded && current != null) ChoiceTile(atlas, current.icon, current.name, true, 32.dp, null)
                Column(Modifier.weight(1f)) {
                    Text(
                        current?.name ?: blank, color = if (current == null) c.textDim else c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    current?.detail?.let { Text(it, color = c.textDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                extras()
                onClose?.let {
                    val label = stringResource(Res.string.close_view)
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(c.button)
                            .semantics { contentDescription = label }.clickable(role = Role.Button, onClick = it),
                        contentAlignment = Alignment.Center,
                    ) { GlyphIcon(Glyph.Remove, c.text, Modifier.size(16.dp)) }
                }
                Chevron(up = folded, colour = c.textDim, modifier = Modifier.size(20.dp))
            }
            if (!folded) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // As many tiles as fit across, stretched to fill the row.
                    val gap = 4.dp
                    val across = max(1, ((maxWidth + gap) / (TILE + gap)).toInt())
                    // Rounded down to whole pixels, so the last tile doesn't spill onto a row of its own.
                    val density = LocalDensity.current
                    val tile = with(density) { min(((maxWidth - gap * (across - 1)) / across).toPx().toInt().toFloat(), TILE_MOST.toPx()).toDp() }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalArrangement = Arrangement.spacedBy(gap),
                        maxItemsInEachRow = across,
                    ) {
                        for (choice in choices) {
                            ChoiceTile(atlas, choice.icon, choice.name, choice.value == selected, tile) { onChoose(choice.value) }
                        }
                    }
                }
            }
        }
    }
}

/** The smallest a tile goes before a row takes one fewer, and the largest on a wide screen. */
private val TILE = 46.dp
private val TILE_MOST = 60.dp

/** A small button that's on or off, for the line over the tiles: density, pipes under a road. */
@Composable
fun TrayToggle(glyph: Glyph, label: String, on: Boolean, onClick: () -> Unit) {
    val c = Infill.colors
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) c.accent else c.button)
            .semantics { contentDescription = label; selected = on }
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, if (on) c.onAccent else c.text, Modifier.size(20.dp)) }
}

/** A colour running from [low] to [high], for the line of a map view. */
@Composable
fun Legend(low: Color, high: Color, modifier: Modifier = Modifier) {
    val c = Infill.colors
    Canvas(modifier.size(width = 48.dp, height = 12.dp)) {
        drawRoundRect(c.button, cornerRadius = CornerRadius(3.dp.toPx()))
        drawRoundRect(Brush.horizontalGradient(listOf(low, high)), cornerRadius = CornerRadius(3.dp.toPx()))
    }
}

@Composable
private fun Chevron(up: Boolean, colour: Color, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val (a, b) = if (up) 15f to 9f else 9f to 15f
        drawLine(colour, Offset(6 * u, a * u), Offset(12 * u, b * u), 2.4f * u, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(colour, Offset(12 * u, b * u), Offset(18 * u, a * u), 2.4f * u, androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

/** More tabs than this and each puts its name under its drawing. */
private const val STACK_TABS = 4

@Composable
private fun TabButton(tab: TrayTab, on: Boolean, stacked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Infill.colors
    val tint = if (on) c.onAccent else c.text
    val base = modifier
        .clip(RoundedCornerShape(8.dp))
        .background(if (on) c.accent else c.button)
        .semantics(mergeDescendants = true) { selected = on }
        .clickable(role = Role.Tab, onClick = onClick)
    if (stacked) {
        Column(base.padding(horizontal = 2.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GlyphIcon(tab.glyph, tint, Modifier.size(20.dp))
            BasicText(
                tab.title, maxLines = 1, style = TextStyle(color = tint, textAlign = TextAlign.Center),
                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp),
            )
        }
    } else {
        Row(
            base.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            GlyphIcon(tab.glyph, tint, Modifier.size(18.dp))
            Text(tab.title, color = tint, fontSize = 12.sp, maxLines = 1, softWrap = false)
        }
    }
}

/** A choice's tile; with no [onClick] it's only a picture. */
@Composable
fun ChoiceTile(atlas: TileAtlas?, icon: ChoiceIcon, name: String, on: Boolean, side: Dp, onClick: (() -> Unit)?) {
    val c = Infill.colors
    val textColour = c.text
    Box(
        Modifier
            .size(side)
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) c.accent else c.button)
            .then(
                if (onClick == null) Modifier
                else Modifier.semantics { contentDescription = name; selected = on }.clickable(role = Role.Tab, onClick = onClick),
            )
            .padding(if (side < 40.dp) 2.dp else 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) { drawChoice(atlas, icon, textColour) }
        icon.letters?.let { Text(it, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
    }
}

private fun DrawScope.drawChoice(atlas: TileAtlas?, icon: ChoiceIcon, textColour: Color) {
    val corner = CornerRadius(4.dp.toPx())
    icon.back?.let { back ->
        if (icon.backTo != null) drawRoundRect(Brush.linearGradient(listOf(back, icon.backTo), Offset.Zero, Offset(size.width, size.height)), cornerRadius = corner)
        else drawRoundRect(back, cornerRadius = corner)
    }
    val image = atlas?.levels?.firstOrNull()
    if (image != null && icon.sprites.isNotEmpty()) {
        // All the sprites at one scale, set by the biggest, centred.
        var most = 1
        for (s in icon.sprites) {
            val r = (Atlas.SUMMER * Atlas.PER_LOOK + s) * 5
            most = max(most, max(Atlas.rects[r + 2], Atlas.rects[r + 3]))
        }
        val scale = min(size.width, size.height) / most
        for (s in icon.sprites) {
            val r = (Atlas.SUMMER * Atlas.PER_LOOK + s) * 5
            val w = Atlas.rects[r + 2]
            val h = Atlas.rects[r + 3]
            val dw = (w * scale).toInt()
            val dh = (h * scale).toInt()
            drawImage(
                image,
                srcOffset = IntOffset(Atlas.rects[r], Atlas.rects[r + 1]), srcSize = IntSize(w, h),
                dstOffset = IntOffset(((size.width - dw) / 2).toInt(), ((size.height - dh) / 2).toInt()), dstSize = IntSize(dw, dh),
                filterQuality = FilterQuality.None,
            )
        }
    }
    val g = icon.glyph ?: return
    // Over a picture, smaller in the corner with a shadow; on its own, the whole tile.
    val over = icon.sprites.isNotEmpty() || icon.back != null
    val colour = icon.glyphColour ?: if (over) Color.White else textColour
    if (icon.sprites.isNotEmpty()) {
        val s = size.minDimension * 0.55f
        val u = s / 24f
        translate(size.width - s, size.height - s) {
            drawCircle(Color(0xCC1E1E22), s / 2, Offset(s / 2, s / 2))
            translate(s * 0.15f, s * 0.15f) { glyph(g, u * 0.7f, colour) }
        }
    } else {
        val u = size.minDimension / 24f
        if (over) translate(0.8f * u, 0.8f * u) { glyph(g, u, Color(0x80000000)) }
        glyph(g, u, colour)
    }
}

/** One, two or three blocks for how dense a zone may build. */
fun densityGlyph(d: DensityKind): Glyph = when (d) {
    DensityKind.Low -> Glyph.Low
    DensityKind.Medium -> Glyph.Medium
    DensityKind.High -> Glyph.High
}

// What each tool offers, as tiles.

private fun building(type: BuildingType): ChoiceIcon = ChoiceIcon(intArrayOf(BuildingSprites.sprite(type.ordinal, 0)))

/** A building's price and size. */
@Composable
fun buildingDetail(type: BuildingType): String? {
    val price = Prices.of(type)
    return if (price <= 0) null else stringResource(Res.string.choice_building, moneyText(price), type.width, type.height)
}

@Composable
private fun perTile(price: Long): String = stringResource(Res.string.per_tile, moneyText(price))

/** A straight piece running east and west: its neighbours are east (2) and west (8). */
private const val ACROSS = 10
private const val CROSSROADS = 15

@Composable
fun roadChoices(city: City): List<Choice<RoadType>> = roadsIn(city).map { t ->
    val icon = when (t) {
        RoadType.DIRT -> ChoiceIcon(intArrayOf(Atlas.ROAD_DIRT + ACROSS))
        RoadType.GRAVEL -> ChoiceIcon(intArrayOf(Atlas.ROAD_GRAVEL + ACROSS))
        RoadType.LANE -> ChoiceIcon(intArrayOf(Atlas.ROAD_LANE + ACROSS))
        RoadType.STREET -> ChoiceIcon(intArrayOf(Atlas.ROAD_STREET + ACROSS))
        RoadType.ONE_WAY_STREET -> ChoiceIcon(intArrayOf(Atlas.ROAD_STREET + ACROSS, Atlas.ARROW + 1))
        RoadType.AVENUE -> ChoiceIcon(intArrayOf(Atlas.ROAD_AVENUE + ACROSS))
        RoadType.ONE_WAY_AVENUE -> ChoiceIcon(intArrayOf(Atlas.ROAD_AVENUE + ACROSS, Atlas.ARROW + 1))
        // One carriageway, with its half of the median.
        RoadType.BOULEVARD -> ChoiceIcon(intArrayOf(Atlas.ROAD_AVENUE + ACROSS, Atlas.MEDIAN))
    }
    Choice(t, stringResource(roadName(t)), icon, perTile(t.price))
}

@Composable
fun railChoices(): List<Choice<RailKind>> = RailKind.entries.map { k ->
    when (k) {
        RailKind.Track -> Choice(k, stringResource(k.title), ChoiceIcon(intArrayOf(Atlas.GRASS, Atlas.TRACK + ACROSS)), perTile(Prices.RAIL))
        RailKind.Station -> Choice(k, stringResource(k.title), building(BuildingType.STATION), buildingDetail(BuildingType.STATION))
        RailKind.Yard -> Choice(k, stringResource(k.title), building(BuildingType.FREIGHT_YARD), buildingDetail(BuildingType.FREIGHT_YARD))
    }
}

private val REMOVE_RED = Color(0xFFD84343)

@Composable
fun transitChoices(city: City, group: TransitGroup): List<Choice<TransitKind>> = transitKindsIn(city).filter { group in it.groups }.map { k ->
    val street = Atlas.ROAD_STREET + ACROSS
    val icon = when {
        k.building != null -> building(k.building)
        k == TransitKind.TramTrack -> ChoiceIcon(intArrayOf(street, Atlas.TRAMWAY + ACROSS))
        k == TransitKind.TramStop -> ChoiceIcon(intArrayOf(street, Atlas.TRAMWAY + ACROSS, Atlas.TRAM_STOP))
        k == TransitKind.BusStop -> ChoiceIcon(intArrayOf(street, Atlas.BUS_STOP))
        k.wire -> ChoiceIcon(intArrayOf(street, Atlas.TROLLEY_WIRE + ACROSS))
        k.lane -> ChoiceIcon(intArrayOf(street), glyph = Glyph.Diamond)
        k == TransitKind.TramLine -> ChoiceIcon(glyph = Glyph.Route, glyphColour = Color(0xFFD8302F))
        k == TransitKind.BusLine -> ChoiceIcon(glyph = Glyph.Route, glyphColour = Color(0xFF2FA85A))
        k.list -> ChoiceIcon(glyph = Glyph.List)
        k == TransitKind.Subway -> ChoiceIcon(glyph = Glyph.Tunnel)
        else -> ChoiceIcon(glyph = Glyph.Remove, glyphColour = REMOVE_RED)
    }
    val detail = when {
        k.building != null -> buildingDetail(k.building)
        k == TransitKind.TramTrack -> perTile(Prices.TRAM_TRACK)
        k.stop != 0 -> moneyText(Prices.STOP)
        k.wire -> perTile(Prices.WIRE)
        k == TransitKind.Subway -> perTile(Prices.TUNNEL)
        else -> null
    }
    Choice(k, stringResource(k.title), icon, detail)
}

@Composable
fun junctionChoices(city: City): List<Choice<JunctionKind>> = junctionKindsIn(city).map { k ->
    val cross = Atlas.ROAD_STREET + CROSSROADS
    val icon = when (k) {
        JunctionKind.Stop -> ChoiceIcon(intArrayOf(cross, Atlas.JUNCTION))
        JunctionKind.Lights -> ChoiceIcon(intArrayOf(cross, Atlas.JUNCTION + 1))
        JunctionKind.Interchange -> ChoiceIcon(intArrayOf(cross, Atlas.JUNCTION + 2))
        JunctionKind.Roundabout -> ChoiceIcon(intArrayOf(Atlas.ROUNDABOUT + CROSSROADS))
        JunctionKind.Auto -> ChoiceIcon(glyph = Glyph.Auto)
    }
    Choice(k, stringResource(k.title), icon)
}

private val PIPE_COLOURS = mapOf(Pipe.WATER to Color(0xFF4FA3E0), Pipe.SEWER to Color(0xFFB0824A), Pipe.STORM to Color(0xFFB8BCC2))
private val WOOD_PIPE = Color(0xFF6E4326)

@Composable
fun waterChoices(city: City, group: WaterGroup): List<Choice<WaterKind>> = waterKindsIn(city).filter { group in it.groups }.map { k ->
    val icon = when {
        k.building != null -> building(k.building)
        k.pipe != null -> ChoiceIcon(glyph = Glyph.Pipe, glyphColour = if (k.material != null) WOOD_PIPE else PIPE_COLOURS.getValue(k.pipe))
        k.bank -> ChoiceIcon(glyph = Glyph.Bank)
        else -> ChoiceIcon(glyph = Glyph.Remove, glyphColour = REMOVE_RED)
    }
    val detail = when {
        k.building != null -> buildingDetail(k.building)
        k.pipe != null && k.material == null -> perTile(k.pipe.price)
        k.bank -> perTile(Prices.BANK)
        else -> null
    }
    Choice(k, stringResource(k.title), icon, detail)
}

@Composable
fun powerChoices(city: City): List<Choice<PowerKind>> = powerKindsIn(city).map { k ->
    val icon = when {
        k.building != null -> building(k.building)
        k.scrubbers -> ChoiceIcon(glyph = Glyph.Scrubber)
        k.high -> ChoiceIcon(intArrayOf(Atlas.GRASS, Atlas.HV_LINE + ACROSS))
        else -> ChoiceIcon(intArrayOf(Atlas.GRASS, Atlas.POWER_LINE + ACROSS))
    }
    val detail = when {
        k.building != null -> buildingDetail(k.building)
        k.scrubbers -> null
        k.high -> perTile(Prices.HIGH_LINE)
        else -> perTile(Prices.POWER_LINE)
    }
    Choice(k, stringResource(k.title), icon, detail)
}

/** The kinds of service [city] has something of to build, as tabs. */
@Composable
fun serviceTabs(city: City): List<TrayTab> {
    val open = servicesIn(city).map { it.group }.toSet()
    return ServiceGroup.entries.filter { it in open }.map { TrayTab(it, stringResource(it.title), serviceGlyph(it)) }
}

/**
 * The tabs over [tool]'s tray: the tools sharing its button, with transit
 * and water each split into their kinds, those [city] has something of.
 */
@Composable
fun toolTabs(tool: Tool, city: City): List<TrayTab> = tool.group.tools.flatMap { t ->
    when (t) {
        Tool.Transit -> {
            val open = transitKindsIn(city).filter { it != TransitKind.Remove && !it.list }.flatMap { it.groups }.toSet()
            TransitGroup.entries.filter { it in open }.map { TrayTab(it, stringResource(it.title), transitGlyph(it)) }
        }
        Tool.Water -> {
            val open = waterKindsIn(city).filter { it != WaterKind.Remove }.flatMap { it.groups }.toSet()
            WaterGroup.entries.filter { it in open }.map { TrayTab(it, stringResource(it.title), waterGlyph(it)) }
        }
        else -> listOf(TrayTab(t, stringResource(t.title), toolGlyph(t)))
    }
}

private fun transitGlyph(g: TransitGroup): Glyph = when (g) {
    TransitGroup.Trams -> Glyph.Tram
    TransitGroup.Buses -> Glyph.Bus
    TransitGroup.Subway -> Glyph.Tunnel
}

private fun waterGlyph(g: WaterGroup): Glyph = when (g) {
    WaterGroup.Supply -> Glyph.Drop
    WaterGroup.Sewers -> Glyph.Manhole
    WaterGroup.Storm -> Glyph.Rain
}

/** What [city] can build of one kind of service. */
@Composable
fun serviceChoices(city: City, group: ServiceGroup): List<Choice<ServiceKind>> = servicesIn(city).filter { it.group == group }.map { k ->
    when {
        k.type == null -> Choice(k, stringResource(k.title), ChoiceIcon(intArrayOf(Atlas.ROAD_STREET + ACROSS, Atlas.STREET_TREES)), perTile(Prices.STREET_TREE))
        k.type == BuildingType.PARK -> Choice(k, stringResource(k.title), building(k.type), perTile(Prices.PARK))
        else -> Choice(k, stringResource(k.title), building(k.type), buildingDetail(k.type))
    }
}

@Composable
fun bulldozeChoices(): List<Choice<BulldozeKind>> = BulldozeKind.entries.map { k ->
    Choice(k, stringResource(k.title), ChoiceIcon(glyph = if (k == BulldozeKind.Renew) Glyph.Renew else Glyph.Bulldoze))
}

@Composable
fun zoneChoices(): List<Choice<ZoneKind>> = ZoneKind.entries.map { k ->
    val sample = when (k) {
        ZoneKind.Residential -> BuildingType.HOUSE
        ZoneKind.Commercial -> BuildingType.SHOP
        ZoneKind.Industrial -> BuildingType.WORKSHOP
        ZoneKind.Office -> BuildingType.OFFICES
        ZoneKind.Farmland -> BuildingType.FARM
    }
    Choice(k, stringResource(k.title), ChoiceIcon(intArrayOf(BuildingSprites.sprite(sample.ordinal, 0)), back = zoneColour(k.zone)), perTile(Prices.ZONE))
}

/** The map views by what they're about, each a tab. */
enum class ViewGroup { Town, Utilities, Services, Transport }

fun viewGroup(o: Overlay): ViewGroup = when (o) {
    Overlay.None, Overlay.LandValue, Overlay.Wealth, Overlay.Age, Overlay.Pollution, Overlay.Heat, Overlay.Land -> ViewGroup.Town
    Overlay.Power, Overlay.LineLoad, Overlay.Water, Overlay.Runoff, Overlay.Garbage -> ViewGroup.Utilities
    Overlay.Crime, Overlay.Theft, Overlay.Vice, Overlay.Rackets, Overlay.Police, Overlay.Fire, Overlay.Ladders, Overlay.Ambulance, Overlay.Schooling, Overlay.Health -> ViewGroup.Services
    Overlay.Traffic, Overlay.Junctions, Overlay.Trips, Overlay.Reach, Overlay.Transit, Overlay.Railway, Overlay.Goods -> ViewGroup.Transport
}

@Composable
fun viewTabs(): List<TrayTab> = ViewGroup.entries.map { g ->
    when (g) {
        ViewGroup.Town -> TrayTab(g, stringResource(Res.string.views_town), Glyph.Coin)
        ViewGroup.Utilities -> TrayTab(g, stringResource(Res.string.group_utilities), Glyph.Utilities)
        ViewGroup.Services -> TrayTab(g, stringResource(Res.string.tool_services), Glyph.Civic)
        ViewGroup.Transport -> TrayTab(g, stringResource(Res.string.group_transport), Glyph.Road)
    }
}

/** The map views of one kind, each on its colours with a drawing of what it shows. Closing one is the button on the line. */
@Composable
fun overlayChoices(group: ViewGroup): List<Choice<Overlay>> = Overlay.entries.filter { it != Overlay.None && viewGroup(it) == group }.map { o ->
    val g = when (o) {
        Overlay.None -> null
        Overlay.LandValue -> Glyph.Coin
        Overlay.Pollution -> Glyph.Smoke
        Overlay.Crime -> Glyph.Cuffs
        Overlay.Power -> Glyph.Bolt
        Overlay.LineLoad -> Glyph.Pylon
        Overlay.Police -> Glyph.Star
        Overlay.Fire -> Glyph.Flame
        Overlay.Ladders -> Glyph.Ladder
        Overlay.Theft -> Glyph.Sack
        Overlay.Vice -> Glyph.Glass
        Overlay.Rackets -> Glyph.Hat
        Overlay.Ambulance -> Glyph.Ambulance
        Overlay.Traffic -> Glyph.Car
        Overlay.Railway -> Glyph.Rail
        Overlay.Water -> Glyph.Drop
        Overlay.Runoff -> Glyph.Rain
        Overlay.Schooling -> Glyph.Cap
        Overlay.Health -> Glyph.Cross
        Overlay.Wealth -> Glyph.Coins
        Overlay.Age -> Glyph.Hourglass
        Overlay.Transit -> Glyph.Tram
        Overlay.Heat -> Glyph.Heat
        Overlay.Garbage -> Glyph.Bin
        Overlay.Land -> Glyph.Mountain
        Overlay.Goods -> Glyph.Crate
        Overlay.Junctions -> Glyph.Lights
        Overlay.Trips -> Glyph.Arrows
        Overlay.Reach -> Glyph.Target
    }
    val icon = ChoiceIcon(back = o.low.copy(alpha = max(o.low.alpha, 0.35f)), backTo = o.high.copy(alpha = 1f), glyph = g)
    Choice(o, stringResource(o.title), icon)
}
