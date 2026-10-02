package com.rm.infill.ui

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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
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
    }
}

@Composable
fun summaryLine(s: SaveSummary): String {
    val months = stringArrayResource(Res.array.month_short)
    return stringResource(Res.string.save_line, months.getOrElse(s.month) { "" }, s.year, groupThousands(s.population.toLong()))
}

/** The map for a new city: its name, which map, how much water and woods, and a river or not, with a picture of it. */
@Composable
fun NewCityScreen(onStart: (name: String, seed: Long, options: TerrainOptions) -> Unit, onBack: () -> Unit) {
    val c = Infill.colors
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
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(seed, water, trees, river, climate) {
        // A moment's wait, so holding a button doesn't make a map for every step.
        delay(120)
        val m = CityMap(128, 128)
        TerrainGen.generate(m, seed, TerrainOptions(water, trees, river, climate = climate))
        preview = terrainImage(m)
    }
    Page {
        Text(stringResource(Res.string.new_city), color = c.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(c.button)) {
            preview?.let { Image(it, null, Modifier.fillMaxSize(), filterQuality = FilterQuality.None) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                name, { typed = it.take(30) }, label = { Text(stringResource(Res.string.city_name)) }, singleLine = true,
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
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.climate), color = c.text, fontSize = 15.sp)
            Chips(Climate.entries, climate, { stringResource(climateName(it)) }) { climate = it }
        }
        NumberRow(Res.string.water, water, 10, 0..100) { water = it }
        NumberRow(Res.string.woods, trees, 10, 0..100) { trees = it }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.river), color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Chips(listOf(true, false), river, { stringResource(if (it) Res.string.yes else Res.string.no) }) { river = it }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.earthquakes), color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Chips(listOf(true, false), quakes, { stringResource(if (it) Res.string.yes else Res.string.no) }) { quakes = it }
        }
        BigButton(stringResource(Res.string.start), primary = true) {
            onStart(name.ifBlank { TownNames.make(seed) }, seed, TerrainOptions(water, trees, river, quakes, climate))
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

/** A labelled number with buttons to step it within [range]. */
@Composable
fun NumberRow(label: StringResource, value: Int, step: Int, range: IntRange, set: (Int) -> Unit) {
    val c = Infill.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Chips(listOf(-1, 1), 0, { if (it < 0) "−" else "+" }) { set((value + it * step).coerceIn(range)) }
        Text(stringResource(Res.string.percent, value), color = c.text, fontSize = 15.sp, modifier = Modifier.widthIn(min = 52.dp).padding(start = 8.dp))
    }
}

/** A row of choices with the chosen one lit. */
@Composable
fun <T> Chips(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    val c = Infill.colors
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    .clickable(role = Role.Button) { onSelect(o) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/** The saved cities, newest first, to load or delete. */
@Composable
fun LoadWindow(saves: List<Pair<String, SaveSummary>>, onLoad: (String) -> Unit, onDelete: (String) -> Unit, onClose: () -> Unit) {
    val c = Infill.colors
    var deleting by remember { mutableStateOf<String?>(null) }
    Window(Res.string.load, onClose, Glyph.List) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (saves.isEmpty()) Text(stringResource(Res.string.no_saves), color = c.textDim, fontSize = 14.sp)
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

/** The game's menu: save, load, start again, settings, or back to the main screen. */
@Composable
fun MenuWindow(onSave: () -> Unit, onLoad: () -> Unit, onNew: () -> Unit, onSettings: () -> Unit, onMain: () -> Unit, onClose: () -> Unit) {
    Window(Res.string.menu, onClose, Glyph.List) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton(stringResource(Res.string.save), primary = true, glyph = Glyph.Check, onClick = onSave)
            BigButton(stringResource(Res.string.load), glyph = Glyph.List, onClick = onLoad)
            BigButton(stringResource(Res.string.new_city), glyph = Glyph.Plus, onClick = onNew)
            BigButton(stringResource(Res.string.settings), glyph = Glyph.Auto, onClick = onSettings)
            BigButton(stringResource(Res.string.main_screen), glyph = Glyph.Building, onClick = onMain)
        }
    }
}

private val ACTION_NAMES: Map<KeyAction, StringResource> = mapOf(
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
    KeyAction.Pause to Res.string.pause,
    KeyAction.Budget to Res.string.budget,
    KeyAction.Graphs to Res.string.graphs,
    KeyAction.People to Res.string.people,
    KeyAction.NextOverlay to Res.string.overlay,
    KeyAction.Back to Res.string.key_back,
)

@Composable
private fun SettingHead(title: StringResource, glyph: Glyph) {
    val c = Infill.colors
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GlyphIcon(glyph, c.accent, Modifier.size(15.dp))
        Text(stringResource(title).uppercase(), color = c.textDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
    }
}

/** Graphics, theme, the size of controls and text, and the keys. */
@Composable
fun SettingsWindow(settings: Settings, onClose: () -> Unit) {
    val c = Infill.colors
    var capturing by remember { mutableStateOf<KeyAction?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(capturing) { if (capturing != null) focus.requestFocus() }
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
        Window(Res.string.settings, onClose, Glyph.Auto) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                SettingHead(Res.string.disasters, Glyph.Warn)
                Chips(listOf(0, 1, 2), settings.disasters, {
                    stringResource(listOf(Res.string.disasters_off, Res.string.disasters_fewer, Res.string.disasters_normal)[it])
                }) { settings.disasters = it }
                SettingHead(Res.string.ui_size, Glyph.Zone)
                Chips(Settings.SCALES, settings.uiScale, { "${(it * 100).toInt()}%" }) { settings.uiScale = it }
                SettingHead(Res.string.keys, Glyph.List)
                for ((action, title) in ACTION_NAMES) {
                    val keys = settings.keys.filterValues { it == action }.keys.map { keyName(it) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(title), color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(
                            if (capturing == action) stringResource(Res.string.press_a_key) else keys.joinToString("  ").ifEmpty { "—" },
                            color = if (capturing == action) c.onAccent else c.text,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (capturing == action) c.accent else c.button)
                                .clickable(role = Role.Button) { capturing = if (capturing == action) null else action }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                BigButton(stringResource(Res.string.reset_keys)) { settings.resetKeys() }
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
