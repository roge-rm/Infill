package com.rm.infill.ui

import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.rm.infill.res.chronicle
import com.rm.infill.res.money_of_1900
import com.rm.infill.res.money_of_the_day
import com.rm.infill.res.money_shown
import com.rm.infill.res.ordinances
import com.rm.infill.res.tool_leisure
import com.rm.infill.res.climate_dry
import com.rm.infill.res.climate_coastal
import com.rm.infill.res.climate_northern
import com.rm.infill.res.climate_temperate
import com.rm.infill.res.climate
import com.rm.infill.res.app_icon
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import kotlin.math.roundToInt
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rm.infill.map.GraphicsLevel
import com.rm.infill.map.imageBitmapOf
import com.rm.infill.platform.AUTOSAVE
import com.rm.infill.platform.Settings
import com.rm.infill.platform.ThemeChoice
import com.rm.infill.platform.ToolSide
import com.rm.infill.res.tool_districts
import com.rm.infill.res.tool_traffic
import com.rm.infill.res.disasters
import com.rm.infill.res.disasters_off
import com.rm.infill.res.disasters_fewer
import com.rm.infill.res.disasters_normal
import com.rm.infill.res.earthquakes
import com.rm.infill.res.*
import com.rm.infill.sim.Climate
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.TownNames
import com.rm.infill.sim.SaveSummary
import com.rm.infill.sim.Terrain
import com.rm.infill.sim.TerrainGen
import com.rm.infill.sim.TerrainOptions
import com.rm.infill.sim.Resource
import com.rm.infill.ui.theme.Infill
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.rm.infill.sim.City
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import kotlin.random.Random

/** A wide button for the screens and menus. [primary] is the one most likely wanted. */
@Composable
fun BigButton(text: String, primary: Boolean = false, enabled: Boolean = true, glyph: Glyph? = null, onClick: () -> Unit) {
    val c = Infill.colors
    val tint = if (primary) c.onAccent else if (enabled) c.text else c.textDim
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) c.accent else c.button)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        glyph?.let { GlyphIcon(it, tint, Modifier.size(20.dp)) }
        Text(text, color = tint, fontSize = 16.sp, fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** A full screen page on the plain background, its content in a column that scrolls and keeps clear of the cutout. */
@Composable
fun Page(background: ImageBitmap? = null, content: @Composable () -> Unit) {
    val c = Infill.colors
    Box(Modifier.fillMaxSize().background(c.page)) {
        if (background != null) {
            Image(
                background, null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.None,
                alpha = 0.35f,
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}

/** The first screen: carry on, start a new city, load one, or change settings. */
@Composable
fun StartScreen(
    lastSave: SaveSummary?,
    onContinue: () -> Unit,
    onNew: () -> Unit,
    onLoad: () -> Unit,
    onSettings: () -> Unit,
) {
    val c = Infill.colors
    val backdrop = remember { terrainImage(TerrainGen.let { CityMap(96, 160).also { m -> it.generate(m, 1900) } }) }
    Page(backdrop) {
        Row(Modifier.padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(painterResource(Res.drawable.app_icon), null, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
            Text(stringResource(Res.string.app_name), color = c.text, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        }
        if (lastSave != null) {
            BigButton(stringResource(Res.string.continue_town, lastSave.name), primary = true, onClick = onContinue)
            Text(summaryLine(lastSave), color = c.textDim, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        }
        BigButton(stringResource(Res.string.new_city), primary = lastSave == null, onClick = onNew)
        BigButton(stringResource(Res.string.load), onClick = onLoad)
        BigButton(stringResource(Res.string.settings), onClick = onSettings)
        LocalHelp.current?.let { help -> BigButton(stringResource(Res.string.help)) { help(null) } }
    }
}

@Composable
fun summaryLine(s: SaveSummary): String {
    val months = stringArrayResource(Res.array.month_short)
    return pluralStringResource(Res.plurals.save_line, s.population, stringResource(Res.string.date, months.getOrElse(s.month) { "" }, s.year), groupThousands(s.population.toLong()))
}

/**
 * The map for a new city, or a new region of nine: its name, which map, how
 * much water and woods, and a river or not, with a picture of it.
 */
@Composable
fun NewCityScreen(onStart: (name: String, seed: Long, options: TerrainOptions, grid: Int, side: Int) -> Unit, onBack: () -> Unit) {
    val c = Infill.colors
    var region by remember { mutableStateOf(false) }
    // A town's size in tiles a side, and for a region how many towns across.
    var side by remember { mutableStateOf(City.DEFAULT_SIZE) }
    var grid by remember { mutableStateOf(3) }
    var seed by remember { mutableStateOf(Random.nextLong(1, 1_000_000)) }
    // The name comes from the map number until one is typed in or asked for.
    var typed by remember { mutableStateOf<String?>(null) }
    var rolled by remember { mutableStateOf<Long?>(null) }
    val name = typed ?: TownNames.make(rolled ?: seed)
    var water by remember { mutableStateOf(30) }
    var trees by remember { mutableStateOf(40) }
    var river by remember { mutableStateOf(true) }
    var quakes by remember { mutableStateOf(false) }
    var climate by remember { mutableStateOf(Climate.TEMPERATE) }
    var sea by remember { mutableStateOf(com.rm.infill.sim.Sea.NONE) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(seed, water, trees, river, climate, region, side, grid, sea) {
        // A moment's wait, so holding a button doesn't make a map for every step.
        delay(120)
        val across = if (region) side * grid else side
        // Away from the screen, since the biggest region's land takes a moment.
        preview = withContext(Dispatchers.Default) {
            val m = CityMap(across, across)
            TerrainGen.generate(m, seed, TerrainOptions(water, trees, river, climate = climate, sea = sea))
            terrainImage(m)
        }
    }
    Page {
        Text(stringResource(Res.string.new_city), color = c.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Chips(listOf(false, true), region, { stringResource(if (it) Res.string.kind_region else Res.string.kind_town) }) { region = it }
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(c.button)) {
            preview?.let { Image(it, null, Modifier.fillMaxSize(), filterQuality = FilterQuality.None) }
            // A region's squares.
            if (region) androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val n = grid
                for (k in 1 until n) {
                    val a = size.width * k / n
                    drawLine(androidx.compose.ui.graphics.Color(0xCCF2EEE4), androidx.compose.ui.geometry.Offset(a, 0f), androidx.compose.ui.geometry.Offset(a, size.height), 2.dp.toPx())
                    drawLine(androidx.compose.ui.graphics.Color(0xCCF2EEE4), androidx.compose.ui.geometry.Offset(0f, a), androidx.compose.ui.geometry.Offset(size.width, a), 2.dp.toPx())
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                name, { typed = it.take(30) }, label = { Text(stringResource(if (region) Res.string.region_name else Res.string.city_name)) }, singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.widthIn(max = 160.dp)) {
                BigButton(stringResource(Res.string.another_name)) {
                    typed = null
                    rolled = Random.nextLong(1, Long.MAX_VALUE)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                seed.toString(), { v -> v.filter { it.isDigit() }.take(12).toLongOrNull()?.let { seed = it } },
                label = { Text(stringResource(Res.string.seed)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.widthIn(max = 160.dp)) { BigButton(stringResource(Res.string.another_map)) { seed = Random.nextLong(1, 1_000_000) } }
        }
        StepSlider(Res.string.town_size, com.rm.infill.sim.Region.SIDES, side, { stringResource(Res.string.tiles_a_side, it) }) { side = it }
        if (region) StepSlider(Res.string.region_grid, com.rm.infill.sim.Region.GRIDS, grid, { stringResource(Res.string.grid_of, it, it) }) { grid = it }
        StepSlider(Res.string.sea, com.rm.infill.sim.Sea.entries, sea, { stringResource(seaName(it)) }) { sea = it }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.climate), color = c.text, fontSize = 15.sp)
            Chips(Climate.entries, climate, { stringResource(climateName(it)) }) { climate = it }
        }
        StepSlider(Res.string.water, (0..100 step 10).toList(), water, { stringResource(Res.string.percent, it) }) { water = it }
        StepSlider(Res.string.woods, (0..100 step 10).toList(), trees, { stringResource(Res.string.percent, it) }) { trees = it }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.river), color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Chips(listOf(true, false), river, { stringResource(if (it) Res.string.yes else Res.string.no) }) { river = it }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.earthquakes), color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Chips(listOf(true, false), quakes, { stringResource(if (it) Res.string.yes else Res.string.no) }) { quakes = it }
        }
        BigButton(stringResource(Res.string.start), primary = true) {
            onStart(name.ifBlank { TownNames.make(seed) }, seed, TerrainOptions(water, trees, river, quakes, climate, sea), if (region) grid else 0, side)
        }
        BigButton(stringResource(Res.string.back), onClick = onBack)
    }
}

/** What a climate's called. */
fun climateName(c: Climate) = when (c) {
    Climate.TEMPERATE -> Res.string.climate_temperate
    Climate.NORTHERN -> Res.string.climate_northern
    Climate.COASTAL -> Res.string.climate_coastal
    Climate.DRY -> Res.string.climate_dry
}

/** A labelled slider that stops at each of [options], the one chosen named beside the label. */
@Composable
fun <T> StepSlider(label: StringResource, options: List<T>, value: T, name: @Composable (T) -> String, set: (T) -> Unit) {
    val c = Infill.colors
    val at = options.indexOf(value).coerceAtLeast(0)
    val title = stringResource(label)
    val shown = name(value)
    Column {
        // The slider reads out its name and value itself, so this line is for the eyes only.
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(label), color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(name(value), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = at.toFloat(),
            onValueChange = { v -> options.getOrNull(v.roundToInt())?.let { if (it != value) set(it) } },
            valueRange = 0f..(options.size - 1).toFloat(),
            steps = (options.size - 2).coerceAtLeast(0),
            modifier = Modifier.semantics { contentDescription = title; stateDescription = shown },
            colors = SliderDefaults.colors(
                thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.button,
                activeTickColor = c.onAccent, inactiveTickColor = c.textDim,
            ),
        )
    }
}

/** What each kind of sea is called. */
fun seaName(sea: com.rm.infill.sim.Sea) = when (sea) {
    com.rm.infill.sim.Sea.NONE -> Res.string.sea_none
    com.rm.infill.sim.Sea.ONE_SIDE -> Res.string.sea_one_side
    com.rm.infill.sim.Sea.TWO_SIDES -> Res.string.sea_two_sides
    com.rm.infill.sim.Sea.THREE_SIDES -> Res.string.sea_three_sides
    com.rm.infill.sim.Sea.ISLAND -> Res.string.sea_island
    com.rm.infill.sim.Sea.ISLANDS -> Res.string.sea_islands
}

/** A row of choices with the chosen one lit, wrapping when they don't fit. */
@Composable
fun <T> Chips(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    val c = Infill.colors
    // One of a set, read out as chosen or not.
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (o in options) {
            val on = o == selected
            Text(
                label(o),
                color = if (on) c.onAccent else c.text,
                fontSize = 14.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) c.accent else c.button)
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(o) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/** The saved regions and cities, the autosave first, to load or delete. */
@Composable
fun LoadWindow(
    saves: List<Pair<String, SaveSummary>>,
    onLoad: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
    /** Regions, by file, name, how many towns and room for how many: opening one shows its map. */
    regions: List<RegionRow> = emptyList(),
    onRegion: (String) -> Unit = {},
) {
    val c = Infill.colors
    var deleting by remember { mutableStateOf<String?>(null) }
    Window(Res.string.load, onClose, Glyph.List) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (saves.isEmpty() && regions.isEmpty()) Text(stringResource(Res.string.no_saves), color = c.textDim, fontSize = 14.sp)
            for ((file, name, towns, room) in regions) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.button)
                        .clickable(role = Role.Button) { onRegion(file) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(c.accent), contentAlignment = Alignment.Center) {
                        GlyphIcon(Glyph.District, c.onAccent, Modifier.size(18.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(Res.string.region_towns, towns, room), color = c.textDim, fontSize = 13.sp)
                    }
                    ActionButton(ActionItem(Glyph.Remove, stringResource(Res.string.delete), confirm = true) { onDelete(file) })
                }
            }
            for ((file, s) in saves) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.button)
                        .clickable(role = Role.Button) { onLoad(file) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // A town, or the autosave of one.
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(c.accent), contentAlignment = Alignment.Center) {
                        GlyphIcon(if (file == AUTOSAVE) Glyph.Renew else Glyph.Building, c.onAccent, Modifier.size(18.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (file == AUTOSAVE) stringResource(Res.string.autosave_of, s.name) else s.name,
                            color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            GlyphIcon(Glyph.Person, c.textDim, Modifier.size(12.dp))
                            Text(summaryLine(s), color = c.textDim, fontSize = 13.sp)
                        }
                    }
                    ActionButton(ActionItem(Glyph.Remove, stringResource(Res.string.delete), confirm = true) { onDelete(file) })
                }
            }
        }
    }
}

/** A region in the load list: its file, name, how many towns and room for how many. */
data class RegionRow(val file: String, val name: String, val towns: Int, val room: Int)

/** The game's menu: save, load, start again, settings, or back to the main screen. */
@Composable
fun MenuWindow(
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    onMain: () -> Unit,
    onClose: () -> Unit,
    /** Back to the region's map, for a town in one. */
    onRegion: (() -> Unit)? = null,
) {
    Window(Res.string.menu, onClose, Glyph.List) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton(stringResource(Res.string.save), primary = true, glyph = Glyph.Check, onClick = onSave)
            if (onRegion != null) BigButton(stringResource(Res.string.menu_region), glyph = Glyph.District, onClick = onRegion)
            BigButton(stringResource(Res.string.load), glyph = Glyph.List, onClick = onLoad)
            BigButton(stringResource(Res.string.new_city), glyph = Glyph.Plus, onClick = onNew)
            BigButton(stringResource(Res.string.settings), glyph = Glyph.Auto, onClick = onSettings)
            LocalHelp.current?.let { help -> BigButton(stringResource(Res.string.help), glyph = Glyph.Book) { help(null) } }
            BigButton(stringResource(Res.string.main_screen), glyph = Glyph.Building, onClick = onMain)
        }
    }
}

private val ACTION_NAMES: Map<KeyAction, StringResource> = mapOf(
    KeyAction.CursorUp to Res.string.key_cursor_up,
    KeyAction.CursorDown to Res.string.key_cursor_down,
    KeyAction.CursorLeft to Res.string.key_cursor_left,
    KeyAction.CursorRight to Res.string.key_cursor_right,
    KeyAction.Use to Res.string.key_use,
    KeyAction.PanUp to Res.string.key_pan_up,
    KeyAction.PanDown to Res.string.key_pan_down,
    KeyAction.PanLeft to Res.string.key_pan_left,
    KeyAction.PanRight to Res.string.key_pan_right,
    KeyAction.ZoomIn to Res.string.key_zoom_in,
    KeyAction.ZoomOut to Res.string.key_zoom_out,
    KeyAction.ToolInspect to Res.string.tool_inspect,
    KeyAction.ToolBulldoze to Res.string.tool_bulldoze,
    KeyAction.ToolRoad to Res.string.tool_road,
    KeyAction.ToolRail to Res.string.tool_rail,
    KeyAction.ToolZone to Res.string.tool_zone,
    KeyAction.ToolPower to Res.string.tool_power,
    KeyAction.ToolWater to Res.string.tool_water,
    KeyAction.ToolServices to Res.string.tool_services,
    KeyAction.ToolTransit to Res.string.tool_transit,
    KeyAction.ToolTraffic to Res.string.tool_traffic,
    KeyAction.ToolDistricts to Res.string.tool_districts,
    KeyAction.ToolPhone to Res.string.tool_phone,
    KeyAction.ToolPorts to Res.string.tool_port,
    KeyAction.ToolAir to Res.string.tool_air,
    KeyAction.ToolLeisure to Res.string.tool_leisure,
    KeyAction.Ordinances to Res.string.ordinances,
    KeyAction.Chronicle to Res.string.chronicle,
    KeyAction.Tools to Res.string.key_tools,
    KeyAction.PrevChoice to Res.string.key_prev_choice,
    KeyAction.NextChoice to Res.string.key_next_choice,
    KeyAction.PrevTab to Res.string.key_prev_tab,
    KeyAction.NextTab to Res.string.key_next_tab,
    KeyAction.Pause to Res.string.pause,
    KeyAction.Budget to Res.string.budget,
    KeyAction.Graphs to Res.string.graphs,
    KeyAction.People to Res.string.people,
    KeyAction.Demand to Res.string.demand,
    KeyAction.NextOverlay to Res.string.overlay,
    KeyAction.Back to Res.string.key_back,
    KeyAction.Menu to Res.string.menu,
)

@Composable
private fun SettingHead(title: StringResource, glyph: Glyph) {
    val c = Infill.colors
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GlyphIcon(glyph, c.accent, Modifier.size(15.dp))
        val words = stringResource(title)
        Text(
            words.uppercase(), color = c.textDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp,
            modifier = Modifier.semantics { heading(); contentDescription = words },
        )
    }
}

/** How wide the controller's column is in the keys, to fit its longest name. */
private val PAD_CHIP = 116.dp

/** The settings' tabs. */
private enum class SettingsTab { Display, Sound, Game, Keys }

/** Graphics, theme, the size of controls and text, sound, disasters and the keys, a tab each. */
@Composable
fun SettingsWindow(settings: Settings, onClose: () -> Unit) {
    val c = Infill.colors
    var tab by remember { mutableStateOf(SettingsTab.Display) }
    var capturing by remember { mutableStateOf<KeyAction?>(null) }
    // Waiting for a controller's button rather than a key.
    var capturingPad by remember { mutableStateOf<KeyAction?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(capturing) { if (capturing != null) focus.requestFocus() }
    PadListener(enabled = capturingPad != null) { b, pressed ->
        val action = capturingPad
        if (pressed && action != null && b.bindable) {
            settings.bindPad(action, b)
            capturingPad = null
        }
        true
    }
    Box(
        Modifier
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                val action = capturing ?: return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyDown) {
                    settings.bind(action, e.key)
                    capturing = null
                }
                true
            },
    ) {
        val tabs = listOf(
            TrayTab(SettingsTab.Display, stringResource(Res.string.settings_display), Glyph.Mountain),
            TrayTab(SettingsTab.Sound, stringResource(Res.string.sound), Glyph.Speaker),
            TrayTab(SettingsTab.Game, stringResource(Res.string.settings_game), Glyph.Warn),
            TrayTab(SettingsTab.Keys, stringResource(Res.string.keys), Glyph.List),
        )
        Window(Res.string.settings, onClose, Glyph.Auto, help = "settings-and-sound", top = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (t in tabs) TabButton(t, t.key == tab, stacked = false, Modifier.weight(1f)) { tab = t.key as SettingsTab }
            }
        }) {
            // As tall as the tallest short tab, so the tabs stay put when moving between them.
            Column(Modifier.heightIn(min = 340.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tab) {
                    SettingsTab.Display -> {
                        SettingHead(Res.string.graphics, Glyph.Mountain)
                        Chips(GraphicsLevel.entries, settings.graphics, {
                            stringResource(
                                when (it) {
                                    GraphicsLevel.Low -> Res.string.graphics_low
                                    GraphicsLevel.Medium -> Res.string.graphics_medium
                                    GraphicsLevel.High -> Res.string.graphics_high
                                },
                            )
                        }) { settings.graphics = it }
                        SettingHead(Res.string.theme, Glyph.Lights)
                        Chips(ThemeChoice.entries, settings.theme, {
                            stringResource(
                                when (it) {
                                    ThemeChoice.Auto -> Res.string.theme_auto
                                    ThemeChoice.Light -> Res.string.theme_light
                                    ThemeChoice.Dark -> Res.string.theme_dark
                                },
                            )
                        }) { settings.theme = it }
                        SettingHead(Res.string.tool_side, Glyph.Arrows)
                        Chips(ToolSide.entries, settings.toolSide, {
                            stringResource(
                                when (it) {
                                    ToolSide.Auto -> Res.string.tool_side_auto
                                    ToolSide.Left -> Res.string.tool_side_left
                                    ToolSide.Right -> Res.string.tool_side_right
                                },
                            )
                        }) { settings.toolSide = it }
                        SettingHead(Res.string.ui_size, Glyph.Zone)
                        Chips(Settings.SCALES, settings.uiScale, { stringResource(Res.string.percent, (it * 100).toInt()) }) { settings.uiScale = it }
                        // Only once there's more than one language to pick.
                        if (Settings.LANGUAGES.size > 1) {
                            SettingHead(Res.string.language, Glyph.Book)
                            // Each language by its own name, so it can be found whatever's showing.
                            Chips(listOf("") + Settings.LANGUAGES.keys, settings.language, {
                                if (it.isEmpty()) stringResource(Res.string.language_auto) else Settings.LANGUAGES.getValue(it)
                            }) { settings.language = it }
                        }
                    }
                    SettingsTab.Sound -> {
                        val levels = (0..100 step 10).toList()
                        @Composable
                        fun level(v: Int) = if (v == 0) stringResource(Res.string.volume_off) else stringResource(Res.string.percent, v)
                        StepSlider(Res.string.volume_master, levels, settings.master / 10 * 10, { level(it) }) { settings.master = it }
                        StepSlider(Res.string.volume_town, levels, settings.townVolume / 10 * 10, { level(it) }) { settings.townVolume = it }
                        StepSlider(Res.string.volume_effects, levels, settings.effectsVolume / 10 * 10, { level(it) }) { settings.effectsVolume = it }
                        StepSlider(Res.string.volume_music, levels, settings.musicVolume / 10 * 10, { level(it) }) { settings.musicVolume = it }
                    }
                    SettingsTab.Game -> {
                        SettingHead(Res.string.disasters, Glyph.Warn)
                        Chips(listOf(0, 1, 2), settings.disasters, {
                            stringResource(listOf(Res.string.disasters_off, Res.string.disasters_fewer, Res.string.disasters_normal)[it])
                        }) { settings.disasters = it }
                        SettingHead(Res.string.money_shown, Glyph.Coins)
                        Chips(listOf(false, true), settings.dayDollars, {
                            stringResource(if (it) Res.string.money_of_the_day else Res.string.money_of_1900)
                        }) { settings.dayDollars = it }
                    }
                    SettingsTab.Keys -> {
                        // The controller's buttons show once one's been used.
                        val pad = Pad.seen
                        @Composable
                        fun chip(text: String, waiting: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) = Text(
                            text,
                            color = if (waiting) c.onAccent else c.text,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (waiting) c.accent else c.button)
                                .clickable(role = Role.Button, onClick = onClick)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                        if (pad) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Spacer(Modifier.weight(1f))
                                Text(stringResource(Res.string.keys), color = c.textDim, fontSize = 12.sp)
                                Text(stringResource(Res.string.controller), color = c.textDim, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.width(PAD_CHIP))
                            }
                        }
                        for ((action, title) in ACTION_NAMES) {
                            val keys = settings.keys.filterValues { it == action }.keys.map { keyName(it) }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(title), color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                chip(
                                    if (capturing == action) stringResource(Res.string.press_a_key) else keys.joinToString("  ").ifEmpty { "—" },
                                    capturing == action,
                                ) { capturingPad = null; capturing = if (capturing == action) null else action }
                                if (pad) {
                                    val button = settings.pad.entries.firstOrNull { it.value == action }?.key
                                    chip(
                                        if (capturingPad == action) stringResource(Res.string.press_a_button) else button?.let { padName(it) } ?: "—",
                                        capturingPad == action,
                                        Modifier.width(PAD_CHIP),
                                    ) { capturing = null; capturingPad = if (capturingPad == action) null else action }
                                }
                            }
                        }
                        BigButton(stringResource(Res.string.reset_keys)) { settings.resetKeys() }
                    }
                }
            }
        }
    }
}

/** A map drawn one pixel a tile, for previews and the start screen. */
fun terrainImage(m: CityMap): ImageBitmap {
    val pixels = IntArray(m.size) { i ->
        when (m.terrain[i]) {
            Terrain.WATER -> 0xFF3A6FB0.toInt()
            Terrain.TREES -> 0xFF2F6B2A.toInt()
            // Seams show in the preview, rusty for ore and black for coal.
            else -> when (m.resource[i]) {
                Resource.ORE -> 0xFF9A4E36.toInt()
                Resource.COAL -> 0xFF2E2E34.toInt()
                Resource.OIL -> 0xFF5A3A6A.toInt()
                else -> 0xFF5A9A3C.toInt()
            }
        }
    }
    return imageBitmapOf(pixels, m.width, m.height)
}
