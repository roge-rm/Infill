package com.rm.infill

import com.rm.infill.platform.simDispatcher
import com.rm.infill.sim.AdviceKind
import com.rm.infill.sim.Advice
import com.rm.infill.ui.listText
import com.rm.infill.ui.tileSummary
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.Density
import com.rm.infill.ui.airChoices
import com.rm.infill.ui.AirKind
import com.rm.infill.res.dig_tunnel
import com.rm.infill.ui.railKindsIn
import com.rm.infill.ui.bridgeName
import com.rm.infill.ui.bridgeChoices
import com.rm.infill.ui.TrayCycle
import com.rm.infill.sim.BridgeKind
import com.rm.infill.res.event_bridge_shut
import com.rm.infill.res.event_tunnel_flooded
import com.rm.infill.res.label_bridge_kind
import com.rm.infill.platform.AUTOSAVE
import com.rm.infill.platform.BackButton
import com.rm.infill.platform.Escape
import com.rm.infill.ui.LocalHelp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import com.rm.infill.ui.LocalKeyboardPlay
import com.rm.infill.ui.HelpWindow
import com.rm.infill.platform.Settings
import com.rm.infill.platform.ThemeChoice
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.ui.platform.LocalLayoutDirection
import com.rm.infill.platform.ToolSide
import com.rm.infill.platform.cameraCutouts
import com.rm.infill.platform.platform
import com.rm.infill.platform.saveFileName
import com.rm.infill.res.cuts_off_port
import com.rm.infill.res.no_sea_route
import com.rm.infill.res.load_failed
import com.rm.infill.res.saved
import com.rm.infill.sim.SaveError
import com.rm.infill.sim.SaveGame
import com.rm.infill.ui.LoadWindow
import com.rm.infill.ui.MenuWindow
import com.rm.infill.ui.NewCityScreen
import com.rm.infill.ui.SettingsWindow
import com.rm.infill.ui.StartScreen
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import com.rm.infill.ui.toolTabKeys
import com.rm.infill.ui.airKindsIn
import com.rm.infill.ui.portKindsIn
import com.rm.infill.ui.phoneKindsIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import com.rm.infill.map.MapGestures
import com.rm.infill.res.Res
import com.rm.infill.res.paused_hint
import com.rm.infill.res.play
import com.rm.infill.res.cursor_far_down
import com.rm.infill.res.cursor_far_left
import com.rm.infill.res.cursor_far_right
import com.rm.infill.res.cursor_far_up
import com.rm.infill.res.key_cursor_down
import com.rm.infill.res.key_cursor_left
import com.rm.infill.res.key_cursor_right
import com.rm.infill.res.key_cursor_up
import com.rm.infill.res.key_use
import com.rm.infill.res.let_go
import com.rm.infill.res.list_join
import com.rm.infill.res.map_no_cursor
import com.rm.infill.res.name_colon_value
import com.rm.infill.res.the_map
import com.rm.infill.res.money
import com.rm.infill.res.not_enough_money
import com.rm.infill.res.needs_track
import com.rm.infill.res.needs_water
import com.rm.infill.res.event_flooding
import com.rm.infill.res.event_river_flood
import com.rm.infill.res.event_sickness
import com.rm.infill.res.event_main_burst
import com.rm.infill.res.event_sewer_collapsed
import com.rm.infill.res.event_track_broken
import com.rm.infill.res.event_broke_down
import com.rm.infill.ui.waterKindsIn
import com.rm.infill.ui.TransitKind
import com.rm.infill.ui.BulldozeKind
import com.rm.infill.res.event_tram_track_broken
import com.rm.infill.res.event_wire_down
import com.rm.infill.res.event_tunnel_shut
import com.rm.infill.res.no_route
import com.rm.infill.res.new_district
import com.rm.infill.res.erase_district
import com.rm.infill.res.districts
import com.rm.infill.res.event_fire_damage
import com.rm.infill.res.event_forced_out
import com.rm.infill.res.event_jobs_lost
import com.rm.infill.res.event_smog
import com.rm.infill.res.event_dump_full
import com.rm.infill.res.event_gale
import com.rm.infill.res.event_blizzard
import com.rm.infill.res.event_heat_wave
import com.rm.infill.res.event_industrial_accident
import com.rm.infill.res.event_nuclear_accident
import com.rm.infill.res.event_earthquake
import com.rm.infill.res.event_epidemic
import com.rm.infill.res.event_epidemic_over
import com.rm.infill.ui.transitKindsIn
import com.rm.infill.res.needs_tram_track
import com.rm.infill.res.needs_tunnel
import com.rm.infill.res.road_with_pipes
import com.rm.infill.res.nothing_to_undo
import com.rm.infill.sim.Problem
import com.rm.infill.ui.InspectPanel
import com.rm.infill.ui.MessageChip
import com.rm.infill.ui.Preview
import com.rm.infill.ui.ToolDrag
import com.rm.infill.ui.ZoneKind
import com.rm.infill.ui.AdviceLine
import com.rm.infill.ui.DensityKind
import com.rm.infill.ui.PowerKind
import com.rm.infill.ui.RailKind
import com.rm.infill.ui.PortKind
import com.rm.infill.ui.portChoices
import com.rm.infill.ui.WaterKind
import com.rm.infill.ui.roadName
import com.rm.infill.ui.roadsIn
import com.rm.infill.sim.RoadType
import com.rm.infill.ui.ServiceKind
import com.rm.infill.ui.servicesIn
import com.rm.infill.ui.powerKindsIn
import com.rm.infill.sim.NEW_DISTRICT
import com.rm.infill.ui.lineColour
import com.rm.infill.ui.DISTRICT_LIST
import com.rm.infill.ui.DistrictsWindow
import com.rm.infill.sim.Action
import com.rm.infill.sim.Stop
import com.rm.infill.ui.LinesWindow
import com.rm.infill.ui.LineDraftBar
import com.rm.infill.ui.JunctionKind
import com.rm.infill.ui.junctionKindsIn
import com.rm.infill.ui.PeopleWindow
import com.rm.infill.ui.DemandWindow
import com.rm.infill.ui.EraWindow
import com.rm.infill.sim.Era
import com.rm.infill.ui.BudgetWindow
import com.rm.infill.ui.GraphsWindow
import com.rm.infill.ui.buildingName
import com.rm.infill.map.Overlay
import com.rm.infill.sim.EventKind
import com.rm.infill.res.event_fire
import com.rm.infill.res.event_lost
import com.rm.infill.res.event_saved
import com.rm.infill.ui.zoneColour
import com.rm.infill.res.blocked
import com.rm.infill.res.town_built_there
import kotlin.math.min
import com.rm.infill.ui.groupThousands
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.PluralStringResource
import com.rm.infill.map.Atlas
import com.rm.infill.map.Camera
import com.rm.infill.map.Graphics
import com.rm.infill.map.GraphicsLevel
import com.rm.infill.map.MapView
import com.rm.infill.map.Seasons
import com.rm.infill.map.SHADOW_KEEP
import com.rm.infill.map.WeatherLook
import com.rm.infill.map.weatherTint
import com.rm.infill.sim.Precipitation
import com.rm.infill.sim.Weather
import com.rm.infill.map.Sky
import com.rm.infill.map.rememberTileAtlas
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import com.rm.infill.sim.City
import com.rm.infill.ui.RegionScreen
import com.rm.infill.ui.RegionRow
import com.rm.infill.sim.Region
import com.rm.infill.sim.CityEvent
import com.rm.infill.ui.KeyAction
import com.rm.infill.ui.KeyInput
import com.rm.infill.ui.Pad
import com.rm.infill.ui.InLanguage
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.rm.infill.ui.PadButton
import com.rm.infill.ui.PadListener
import com.rm.infill.ui.ScreenShape
import com.rm.infill.ui.StatusStrip
import com.rm.infill.ui.Tool
import com.rm.infill.ui.ToolBar
import com.rm.infill.ui.ToolGroup
import com.rm.infill.ui.group
import com.rm.infill.ui.Choice
import com.rm.infill.ui.ChoiceIcon
import com.rm.infill.ui.ChoiceTray
import com.rm.infill.ui.Glyph
import com.rm.infill.ui.Legend
import com.rm.infill.ui.TrayToggle
import com.rm.infill.ui.densityGlyph
import com.rm.infill.ui.densitiesFor
import com.rm.infill.ui.within
import com.rm.infill.ui.zoneChoices
import com.rm.infill.ui.roadChoices
import com.rm.infill.ui.railChoices
import com.rm.infill.ui.transitChoices
import com.rm.infill.ui.junctionChoices
import com.rm.infill.ui.powerChoices
import com.rm.infill.ui.waterChoices
import com.rm.infill.ui.serviceChoices
import com.rm.infill.ui.bulldozeChoices
import com.rm.infill.ui.overlayChoices
import com.rm.infill.ui.ServiceGroup
import com.rm.infill.ui.serviceTabs
import com.rm.infill.ui.toolTabs
import com.rm.infill.ui.LocalAtlas
import androidx.compose.runtime.CompositionLocalProvider
import com.rm.infill.ui.TransitGroup
import com.rm.infill.ui.PhoneKind
import com.rm.infill.ui.phoneChoices
import com.rm.infill.res.overlay as overlayTitle
import com.rm.infill.ui.WaterGroup
import com.rm.infill.ui.ViewGroup
import com.rm.infill.ui.viewGroup
import com.rm.infill.ui.viewTabs
import com.rm.infill.ui.groups
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import kotlin.math.max
import com.rm.infill.ui.screenLayout
import com.rm.infill.ui.theme.InfillTheme
import com.rm.infill.audio.AudioEngine
import com.rm.infill.audio.Music
import com.rm.infill.audio.Sounds
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/** The whole app, the same on every platform: the start screen, a new city, and the game. */
@Composable
fun App() {
    val settings = remember { Settings(platform) }
    val dark = when (settings.theme) {
        ThemeChoice.Auto -> isSystemInDarkTheme()
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }
    InLanguage(settings.language) {
        InfillTheme(dark) {
            // The size setting scales everything drawn in dp and sp at once.
            val base = LocalDensity.current
            // The help opens over everything, at a section or the contents, from wherever asks for it.
            var help by remember { mutableStateOf<HelpAt?>(null) }
            // Played from the keyboard since a key went down, until the next touch or click.
            var keyboardPlay by remember { mutableStateOf(false) }
            CompositionLocalProvider(
                // A new one when the language changes, so every string is looked up again, in place.
                LocalDensity provides remember(base, settings.uiScale, settings.language) { ScaledDensity(base.density * settings.uiScale, base.fontScale) },
                LocalHelp provides { section -> help = HelpAt(section) },
                LocalKeyboardPlay provides keyboardPlay,
            ) {
                PadMoves(settings) { keyboardPlay = true }
                Box(
                    Modifier.fillMaxSize()
                        .onPreviewKeyEvent { e ->
                            keyboardPlay = true
                            // Esc closes what's on top, wherever the keys are.
                            e.key == Key.Escape && e.type == KeyEventType.KeyDown && Escape.press()
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val e = awaitPointerEvent(PointerEventPass.Initial)
                                    if (e.type == PointerEventType.Press) keyboardPlay = false
                                }
                            }
                        },
                ) {
                    Screens(settings)
                    help?.let { h ->
                        key(h) { HelpWindow({ help = null }, h.section) }
                    }
                }
            }
        }
    }
}

/**
 * A controller away from the map: the ways move from button to button, held
 * ones again and again, Use presses and Back does what Esc does. [onUse] is
 * called on every button and stick, since the controller is in play.
 */
@Composable
private fun PadMoves(settings: Settings, onUse: () -> Unit) {
    val focusManager = LocalFocusManager.current
    val inputMode = LocalInputModeManager.current
    val scope = rememberCoroutineScope()
    var repeating by remember { mutableStateOf<Job?>(null) }
    val use = rememberUpdatedState(onUse)
    DisposableEffect(Unit) {
        Pad.onUse = {
            use.value()
            inputMode.requestInputMode(InputMode.Keyboard)
        }
        onDispose { Pad.onUse = {} }
    }
    fun step(way: FocusDirection) {
        // With nothing focused yet, the first press finds something.
        if (!focusManager.moveFocus(way)) focusManager.moveFocus(FocusDirection.Next)
    }
    PadListener { b, pressed ->
        val action = settings.pad[b]
        val way = when {
            b == PadButton.StickUp || action == KeyAction.CursorUp -> FocusDirection.Up
            b == PadButton.StickDown || action == KeyAction.CursorDown -> FocusDirection.Down
            b == PadButton.StickLeft || action == KeyAction.CursorLeft -> FocusDirection.Left
            b == PadButton.StickRight || action == KeyAction.CursorRight -> FocusDirection.Right
            else -> null
        }
        if (way != null) {
            repeating?.cancel()
            repeating = if (!pressed) null else scope.launch {
                step(way)
                delay(PAD_REPEAT_DELAY)
                while (true) {
                    step(way)
                    delay(PAD_REPEAT_EVERY)
                }
            }
            return@PadListener true
        }
        if (!pressed) return@PadListener false
        when (action) {
            KeyAction.Use -> Pad.pressFocused()
            KeyAction.Back -> Escape.press()
            else -> return@PadListener false
        }
        true
    }
}

/** A way held on the controller moves again after this long, then this often, in milliseconds. */
private const val PAD_REPEAT_DELAY = 400L
private const val PAD_REPEAT_EVERY = 120L

/**
 * The screen's density with the size setting applied. It's a plain class, so
 * a new one is a change even at the same size: the strings are looked up
 * through the density, and that's how a new language reaches them all
 * without starting the screens again.
 */
private class ScaledDensity(override val density: Float, override val fontScale: Float) : Density

private enum class Screen { Start, New, Game, Region }

/** The help opened at [section], or at the contents; a new one each time so it starts again. */
private class HelpAt(val section: String?)

@Composable
private fun Screens(settings: Settings) {
    var screen by remember { mutableStateOf(Screen.Start) }
    var game by remember { mutableStateOf<GameState?>(null) }
    var loadOpen by remember { mutableStateOf(false) }
    // The region open, and the file it's kept in.
    var region by remember { mutableStateOf<Region?>(null) }
    var regionFile by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val notice = remember { mutableStateOf<Message?>(null) }
    var savesChanged by remember { mutableIntStateOf(0) }
    // Regions, and the files of the towns in them, which are opened from the region rather than listed as towns.
    val regionsAndTowns = remember(savesChanged, loadOpen, screen) {
        val towns = HashSet<String>()
        val rows = platform.saves().mapNotNull { f ->
            platform.readSave(f)?.takeIf { Region.isRegion(it) }?.let { bytes ->
                runCatching { Region.read(bytes) }.getOrNull()?.let { r ->
                    r.towns.forEach { t -> t?.let { towns += it.file } }
                    RegionRow(f, r.name, r.towns.count { it != null }, r.towns.size)
                }
            }
        }
        rows to towns
    }
    val regions = regionsAndTowns.first
    val saves = remember(savesChanged, loadOpen, screen, regionsAndTowns) {
        platform.saves().filter { it !in regionsAndTowns.second }
            .mapNotNull { f -> platform.readSave(f)?.let { SaveGame.summary(it) }?.let { f to it } }
            .sortedBy { if (it.first == AUTOSAVE) 0 else 1 }
    }
    val lastSave = saves.firstOrNull { it.first == AUTOSAVE }?.second

    /** The region [file], read, or null if it can't be. */
    fun readRegion(file: String): Region? = platform.readSave(file)?.let { runCatching { Region.read(it) }.getOrNull() }

    /** A town in a region is saved in its own file too, and noted in the region for its neighbours and the region's map. */
    fun saveToRegion(game: GameState) {
        val city = game.city
        val file = city.region ?: return
        val r = (if (regionFile == file) region else null) ?: readRegion(file) ?: return
        val townFile = r.townFile(file, city.square)
        platform.writeSave(townFile, game.saveBytes())
        game.locked { r.record(city, townFile) }
        platform.writeSave(file, r.write())
        region = r
        regionFile = file
    }

    fun autosave() {
        game?.let {
            platform.writeSave(AUTOSAVE, it.saveBytes())
            saveToRegion(it)
        }
    }

    /** The autosave at the turn of each month, written out on the sim's thread so the screen doesn't stop for it. */
    suspend fun autosaveAway() {
        val g = game ?: return
        val file = g.city.region
        val r = if (file == null) null else (if (regionFile == file) region else null) ?: readRegion(file)
        withContext(simDispatcher) {
            val bytes = g.saveBytes()
            platform.writeSave(AUTOSAVE, bytes)
            if (file != null && r != null) {
                val townFile = r.townFile(file, g.city.square)
                platform.writeSave(townFile, bytes)
                g.locked { r.record(g.city, townFile) }
                platform.writeSave(file, r.write())
            }
        }
        if (file != null && r != null) {
            region = r
            regionFile = file
        }
    }

    /** Shows the region [file]. */
    fun openRegion(file: String) {
        val r = readRegion(file)
        if (r == null) {
            notice.value = Message(Res.string.load_failed)
            return
        }
        region = r
        regionFile = file
        loadOpen = false
        menuOpen = false
        screen = Screen.Region
    }

    /** Plays [city], with its neighbours' borders if it's in a region. */
    fun play(city: City) {
        city.region?.let { file ->
            readRegion(file)?.let { r ->
                region = r
                regionFile = file
                city.neighbourSpare = r.neighboursOf(city)
                city.neighbours = city.neighbourSpare.map { it?.border }.toTypedArray()
                // What its neighbours have agreed since it was last played counts from now.
                city.meetNeighbours()
            }
        }
        game = GameState(city)
        loadOpen = false
        menuOpen = false
        screen = Screen.Game
    }

    fun load(file: String) {
        val bytes = platform.readSave(file)
        val city = try {
            bytes?.let { SaveGame.read(it) }
        } catch (e: SaveError) {
            null
        }
        if (city == null) {
            notice.value = Message(Res.string.load_failed)
            return
        }
        play(city)
    }

    // Put away or hidden: the game saves itself and goes quiet.
    val current by rememberUpdatedState(game)
    val music = remember { Music() }
    LaunchedEffect(Unit) {
        platform.onHidden {
            current?.let { platform.writeSave(AUTOSAVE, it.saveBytes()) }
            AudioEngine.pause(true)
            music.pause(true)
        }
        platform.onShown {
            AudioEngine.pause(false)
            music.pause(false)
        }
    }

    // The town's era's music, while there's a town.
    LaunchedEffect(Unit) { music.run({ current?.city?.era }, { settings.busGains[3] }) }

    // The sound: started once, off the main thread since opening the output
    // can take a while, with more voices on a device that can take them.
    val gains = settings.busGains
    var soundStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val voices = when (settings.graphics) {
            GraphicsLevel.Low -> 16
            GraphicsLevel.Medium -> 24
            GraphicsLevel.High -> 32
        }
        withContext(Dispatchers.Default) { AudioEngine.start(voices) }
        soundStarted = true
    }
    LaunchedEffect(soundStarted, gains.toList()) { if (soundStarted) AudioEngine.busGains(gains) }

    when (screen) {
        Screen.Start -> StartScreen(
            lastSave,
            onContinue = { load(AUTOSAVE) },
            onNew = { screen = Screen.New },
            onLoad = { loadOpen = true },
            onSettings = { settingsOpen = true },
        )
        Screen.New -> NewCityScreen(
            onStart = { name, seed, options, grid, side ->
                if (grid > 0) {
                    val file = "region-" + saveFileName(name)
                    platform.writeSave(file, Region(name, seed, options, grid, side).write())
                    savesChanged++
                    openRegion(file)
                } else {
                    game = GameState(City(seed, side, side, options).also { it.name = name })
                    screen = Screen.Game
                }
            },
            onBack = { screen = if (game != null) Screen.Game else Screen.Start },
        )
        Screen.Region -> region?.let { r ->
            RegionScreen(
                r,
                onPlay = { square ->
                    val t = r.towns[square] ?: return@RegionScreen
                    load(t.file)
                },
                onFound = { square, name ->
                    val file = regionFile ?: return@RegionScreen
                    val city = r.found(square, name, file)
                    // Not playing yet, so nothing else has it: a state of its own just for the save.
                    saveToRegion(GameState(city))
                    savesChanged++
                    play(city)
                },
                onBack = { screen = if (game != null) Screen.Game else Screen.Start },
            )
        }
        Screen.Game -> game?.let { g ->
            key(g) {
                GameScreen(
                    g, settings, notice, windowOpen = menuOpen || loadOpen || settingsOpen,
                    onMenu = { menuOpen = true }, onNewMonth = { autosaveAway() },
                )
            }
        }
    }
    if (menuOpen) {
        MenuWindow(
            onSave = {
                game?.let {
                    // A town in a region is kept in the region; one on its own under its name.
                    if (it.city.region != null) saveToRegion(it)
                    else platform.writeSave(saveFileName(it.city.name), it.saveBytes())
                    notice.value = Message(Res.string.saved, name = it.city.name)
                    savesChanged++
                }
                menuOpen = false
            },
            onLoad = { loadOpen = true },
            onNew = { autosave(); menuOpen = false; screen = Screen.New },
            onSettings = { settingsOpen = true },
            onMain = { autosave(); menuOpen = false; screen = Screen.Start },
            onClose = { menuOpen = false },
            onRegion = game?.city?.region?.let { file ->
                {
                    autosave()
                    game = null
                    savesChanged++
                    openRegion(file)
                }
            },
        )
    }
    if (loadOpen) {
        LoadWindow(saves, ::load, { file ->
            // A region goes with its towns.
            readRegion(file)?.towns?.forEach { t -> t?.let { platform.deleteSave(it.file) } }
            platform.deleteSave(file)
            savesChanged++
        }, { loadOpen = false }, regions, ::openRegion)
    }
    if (settingsOpen) SettingsWindow(settings) { settingsOpen = false }
    // Android's back button: close what's open, or step back a screen. From the start screen it leaves.
    BackButton(enabled = menuOpen || loadOpen || settingsOpen || screen == Screen.New || screen == Screen.Region) {
        when {
            settingsOpen -> settingsOpen = false
            loadOpen -> loadOpen = false
            menuOpen -> menuOpen = false
            screen == Screen.New || screen == Screen.Region -> screen = if (game != null) Screen.Game else Screen.Start
        }
    }
    if (screen != Screen.Game) {
        notice.value?.let { m ->
            LaunchedEffect(m) { delay(MESSAGE_MS); notice.value = null }
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp), contentAlignment = Alignment.TopCenter) {
                m.text?.let { MessageChip(stringResource(it), null) }
            }
        }
    }
}

/** A city being played: the map, the bars and panels over it, and time running. */
@Composable
private fun GameScreen(
    game: GameState,
    settings: Settings,
    notice: MutableState<Message?>,
    windowOpen: Boolean,
    onMenu: () -> Unit,
    onNewMonth: suspend () -> Unit,
) {
    run {
        val city = game.city
        // How often disasters come is a player's setting, kept with the settings.
        city.disasterLevel = settings.disasters
        val density = LocalDensity.current.density
        val camera = remember(density) {
            Camera(city.map.width, city.map.height, MIN_TILE_DP * density, MAX_TILE_DP * density, START_TILE_DP * density)
        }
        var tool by remember { mutableStateOf(Tool.Inspect) }
        var zoneKind by remember { mutableStateOf(ZoneKind.Residential) }
        var densityKind by remember { mutableStateOf(DensityKind.Medium) }
        var bulldozeKind by remember { mutableStateOf(BulldozeKind.Clear) }
        var powerKind by remember { mutableStateOf(PowerKind.Line) }
        var phoneKind by remember { mutableStateOf(PhoneKind.Exchange) }
        var roadKind by remember { mutableStateOf(RoadType.DIRT) }
        var railKind by remember { mutableStateOf(RailKind.Track) }
        var portKind by remember { mutableStateOf(PortKind.Wharf) }
        var airKind by remember { mutableStateOf(AirKind.Airfield) }
        var bridgeKind by remember { mutableStateOf<BridgeKind?>(null) }
        var tunnelling by remember { mutableStateOf(false) }
        var waterKind by remember { mutableStateOf(WaterKind.Main) }
        var transitKind by remember { mutableStateOf(TransitKind.TramTrack) }
        var junctionKind by remember { mutableStateOf(JunctionKind.Lights) }
        // The stops of a line being planned, in order, and whether the list of lines is open.
        var lineDraft by remember { mutableStateOf(listOf<Int>()) }
        var linesOpen by remember { mutableStateOf(false) }
        // Which district the districts tool paints into: a new one, none (erase), or one by id.
        var districtChoice by remember { mutableIntStateOf(NEW_DISTRICT) }
        var districtsOpen by remember { mutableStateOf(false) }
        var roadPipes by remember { mutableStateOf(false) }
        var speed by remember { mutableIntStateOf(1) }
        var drag by remember { mutableStateOf<ToolDrag?>(null) }
        var inspected by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        // The keyboard's cursor on the map, there once a key has moved it, gone at the next touch or click.
        var cursor by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        var message by notice
        var serviceKind by remember { mutableStateOf(ServiceKind.Police) }
        var overlay by remember { mutableStateOf(Overlay.None) }
        var choosingOverlay by remember { mutableStateOf(false) }
        var trayFolded by remember { mutableStateOf(false) }
        val lastTool = remember { mutableStateMapOf<ToolGroup, Tool>() }
        val lastService = remember { mutableStateMapOf<ServiceGroup, ServiceKind>() }
        var leisureKind by remember { mutableStateOf(ServiceKind.Park) }
        val lastTransit = remember { mutableStateMapOf<TransitGroup, TransitKind>() }
        val lastWater = remember { mutableStateMapOf<WaterGroup, WaterKind>() }
        var transitTab by remember { mutableStateOf(TransitGroup.Trams) }
        var waterTab by remember { mutableStateOf(WaterGroup.Supply) }
        var viewTab by remember { mutableStateOf(ViewGroup.Town) }
        var stripSize by remember { mutableStateOf(IntSize.Zero) }
        var budgetOpen by remember { mutableStateOf(false) }
        var graphsOpen by remember { mutableStateOf(false) }
        var peopleOpen by remember { mutableStateOf(false) }
        var demandOpen by remember { mutableStateOf(false) }
        var eraShown by remember { mutableStateOf<Era?>(null) }
        var paused by remember { mutableStateOf(true) }
        val keys = remember { KeyInput() }
        keys.bindings = settings.keys
        val focus = remember { FocusRequester() }
        // The tool button that's chosen, which a controller's Y takes the focus to.
        val toolsFocus = remember { FocusRequester() }
        var mapFocused by remember { mutableStateOf(false) }
        // A button, a tray or a window over the map has the focus.
        var overFocused by remember { mutableStateOf(false) }
        var viewSize by remember { mutableStateOf(Size.Zero) }
        val atlas = rememberTileAtlas()

        // How far the town is through the current game day, 0 to 1, between the sim's whole days.
        var dayProgress by remember { mutableFloatStateOf(0f) }
        var hourShift by remember { mutableFloatStateOf(0f) }
        // Each month is one day and night, starting at dawn on the first. Shadows move a step
        // at a time and the light a little more often, so the screen isn't redrawn every frame for it.
        val hour by remember {
            derivedStateOf {
                game.revision
                val days = City.daysIn(city.month, city.year)
                (Sky.hourAt((city.day - 1 + dayProgress) / days, city.month) + hourShift).mod(24f)
            }
        }
        var lookOverride by remember { mutableIntStateOf(-1) }
        game.revision
        val w = city.weather
        var weatherOverride by remember { mutableIntStateOf(-1) }
        val weather = if (weatherOverride >= 0) DEV_WEATHER[weatherOverride]
        else WeatherLook.of(w.cloud, w.precipitation, w.intensity, w.fog, w.windDirection, w.windSpeed, city.stats.smog)
        // Snow on the ground decides the winter look; a winter month without it is bare.
        val seasonal = Seasons.lookFor(city.month, city.climate)
        val look = when {
            lookOverride >= 0 -> lookOverride
            w.snowCover >= Weather.SNOW_LOOK -> Atlas.SNOW
            seasonal == Atlas.SNOW -> Atlas.BARE
            else -> seasonal
        }
        val month = if (lookOverride >= 0) Seasons.monthOf(lookOverride) else city.month
        val sunStep by remember { derivedStateOf { Sky.step(hour) } }
        val lightStep by remember { derivedStateOf { (hour * LIGHT_STEPS_PER_HOUR).toInt() } }
        val graphics = remember(settings.graphics) { Graphics(settings.graphics) }
        // Chunks are baked for a sun step and how clear the sky is, four levels of it.
        val clear = weather.clearness
        val shadowStep = graphics.sunStep(sunStep) * 4 + clear
        val sun = remember(shadowStep, month) {
            Sky.sun(shadowStep / 4, month).let { it.copy(strength = it.strength * SHADOW_KEEP[clear]) }
        }
        val skyTint = remember(lightStep, month) { Sky.tint(lightStep / LIGHT_STEPS_PER_HOUR, month) }
        val tint = remember(skyTint, weather) { weatherTint(skyTint, weather) }

        /** Tells of what's happened: a message, or a new era, which stops the clock. */
        fun showEvent(e: CityEvent) {
            Sounds.event(e.kind)
            if (e.kind == EventKind.EraArrived) {
                // A new era stops the clock and says what it brings.
                eraShown = e.era
                paused = true
                return
            }
            message = when (e.kind) {
                EventKind.FireStarted -> Message(Res.string.event_fire, e.type?.let { buildingName(it) }, e.x, e.y)
                EventKind.BuildingLost -> Message(Res.string.event_lost, e.type?.let { buildingName(it) }, e.x, e.y)
                EventKind.FireSaved -> Message(Res.string.event_saved, e.type?.let { buildingName(it) }, e.x, e.y)
                EventKind.FireDamaged -> Message(Res.string.event_fire_damage, e.type?.let { buildingName(it) }, e.x, e.y)
                EventKind.ForcedOut -> Message(null, x = e.x, y = e.y, counted = Res.plurals.event_forced_out, count = e.count)
                EventKind.JobsLost -> Message(null, x = e.x, y = e.y, counted = Res.plurals.event_jobs_lost, count = e.count)
                EventKind.Flooding -> Message(Res.string.event_flooding, x = e.x, y = e.y)
                EventKind.RiverFlood -> Message(Res.string.event_river_flood, x = e.x, y = e.y)
                EventKind.Sickness -> Message(Res.string.event_sickness, x = e.x, y = e.y)
                EventKind.MainBurst -> Message(Res.string.event_main_burst, x = e.x, y = e.y)
                EventKind.SewerCollapsed -> Message(Res.string.event_sewer_collapsed, x = e.x, y = e.y)
                EventKind.TrackBroken -> Message(Res.string.event_track_broken, x = e.x, y = e.y)
                EventKind.BrokeDown -> Message(Res.string.event_broke_down, e.type?.let { buildingName(it) }, e.x, e.y)
                EventKind.TramTrackBroken -> Message(Res.string.event_tram_track_broken, x = e.x, y = e.y)
                EventKind.WireDown -> Message(Res.string.event_wire_down, x = e.x, y = e.y)
                EventKind.TunnelShut -> Message(Res.string.event_tunnel_shut, x = e.x, y = e.y)
                EventKind.TunnelFlooded -> Message(Res.string.event_tunnel_flooded, x = e.x, y = e.y)
                EventKind.BridgeShut -> Message(Res.string.event_bridge_shut, x = e.x, y = e.y)
                EventKind.Smog -> Message(Res.string.event_smog)
                EventKind.DumpFull -> Message(Res.string.event_dump_full, x = e.x, y = e.y)
                EventKind.Gale -> Message(Res.string.event_gale, x = e.x, y = e.y)
                EventKind.Blizzard -> Message(Res.string.event_blizzard)
                EventKind.HeatWave -> Message(Res.string.event_heat_wave)
                EventKind.IndustrialAccident -> Message(Res.string.event_industrial_accident, e.type?.let { buildingName(it) }, e.x, e.y)
                EventKind.NuclearAccident -> Message(Res.string.event_nuclear_accident, x = e.x, y = e.y)
                EventKind.Earthquake -> Message(Res.string.event_earthquake, x = e.x, y = e.y)
                EventKind.Epidemic -> Message(Res.string.event_epidemic)
                EventKind.EpidemicOver -> Message(Res.string.event_epidemic_over)
                EventKind.EraArrived -> null
            }
        }

        // Time runs at the chosen speed while the game isn't paused, a few days a frame at most.
        LaunchedEffect(paused, speed) {
            if (paused) return@LaunchedEffect
            var last = withFrameNanos { it }
            var owed = 0
            var working: Job? = null
            while (true) {
                val now = withFrameNanos { it }
                val monthDays = City.daysIn(city.month, city.year)
                var progress = dayProgress + ((now - last) / 1e9 * monthDays / SECONDS_PER_MONTH * SPEEDS[speed]).toFloat()
                last = now
                val days = min(progress.toInt(), MAX_DAYS_PER_FRAME)
                progress = if (progress.toInt() > MAX_DAYS_PER_FRAME) 0f else progress - days
                dayProgress = progress
                // The days are worked out on the sim's own thread; the screen carries on meanwhile, and
                // what comes due while it's busy goes with the next lot.
                owed = min(owed + days, MAX_DAYS_OWED)
                if (owed > 0 && working?.isActive != true) {
                    val lot = owed
                    owed = 0
                    working = launch { if (game.advance(lot, ::showEvent)) onNewMonth() }
                }
            }
        }

        LaunchedEffect(message) {
            if (message != null) {
                delay(MESSAGE_MS)
                message = null
            }
        }

        // What the drag would do, worked out again as it moves.
        val preview = remember(drag, tool, zoneKind, densityKind, bulldozeKind, powerKind, serviceKind, leisureKind, roadKind, roadPipes, railKind, waterKind, transitKind, phoneKind, portKind, bridgeKind, tunnelling, airKind, game.revision) {
            drag?.let { d ->
                d.action(tool, zoneKind, densityKind.within(zoneKind, city), bulldozeKind, powerKind, if (tool == Tool.Leisure) leisureKind else serviceKind, roadKind, roadPipes, railKind, waterKind, transitKind, city.map, junctionKind, districtChoice, phoneKind, portKind, bridgeKind, tunnelling, airKind, city::newest)?.let { Preview(it, game.plan(it), d.x1, d.y1) }
            }
        }
        val costText = preview?.let {
            if (it.plan.problem == Problem.NotEnoughMoney) stringResource(Res.string.not_enough_money)
            else if (it.plan.problem == Problem.NeedsTrack) stringResource(Res.string.needs_track)
            else if (it.plan.problem == Problem.NeedsWater) stringResource(Res.string.needs_water)
            else if (it.plan.problem == Problem.NeedsTramTrack) stringResource(Res.string.needs_tram_track)
            else if (it.plan.problem == Problem.NeedsTunnel) stringResource(Res.string.needs_tunnel)
            else if (it.plan.problem == Problem.NoSeaRoute) stringResource(Res.string.no_sea_route)
            else if (it.plan.problem == Problem.CutsOffPort) stringResource(Res.string.cuts_off_port)
            else stringResource(Res.string.money, groupThousands(it.plan.cost))
        } ?: ""

        /** Steps the tool's kind on by [by], round to the start after the last: the next road, zone, service and so on. */
        fun stepKind(by: Int) {
            fun <T> step(kinds: List<T>, now: T): T = if (kinds.isEmpty()) now else kinds[((kinds.indexOf(now) + by) % kinds.size + kinds.size) % kinds.size]
            when (tool) {
                Tool.Zone -> zoneKind = step(ZoneKind.entries.filter { city.allowsZone(it.zone) }, zoneKind)
                Tool.Road -> roadKind = step(roadsIn(city), roadKind)
                Tool.Rail -> railKind = step(railKindsIn(city), railKind)
                Tool.Water -> waterKind = step(waterKindsIn(city), waterKind)
                Tool.Power -> powerKind = step(powerKindsIn(city), powerKind)
                Tool.Bulldoze -> bulldozeKind = step(BulldozeKind.entries, bulldozeKind)
                Tool.Traffic -> junctionKind = step(junctionKindsIn(city), junctionKind)
                Tool.Transit -> {
                    // The list of lines opens a window, so the key steps past it.
                    transitKind = step(transitKindsIn(city).filter { !it.list }, transitKind)
                    lineDraft = emptyList()
                }
                Tool.Services -> serviceKind = step(servicesIn(city), serviceKind)
                Tool.Leisure -> leisureKind = step(servicesIn(city, leisure = true), leisureKind)
                Tool.Phone -> phoneKind = step(phoneKindsIn(city), phoneKind)
                Tool.Port -> portKind = step(portKindsIn(city), portKind)
                Tool.Air -> airKind = step(airKindsIn(city), airKind)
                Tool.Districts -> districtChoice = step(listOf(NEW_DISTRICT, 0) + city.districts.map { it.id }, districtChoice)
                Tool.Inspect -> {}
            }
        }

        fun pick(t: Tool) {
            // Picking the same tool again moves on to its next kind.
            if (t == tool) stepKind(1)
            tool = t
            lastTool[t.group] = t
            trayFolded = false
            choosingOverlay = false
            drag = null
            if (t != Tool.Inspect) inspected = null
        }

        /** Opens one of the tray's tabs: another tool on the same button, or a kind of transit or water. */
        fun openTab(t: Any) {
            when (t) {
                is Tool -> if (t != tool) pick(t)
                is TransitGroup -> {
                    if (tool != Tool.Transit) pick(Tool.Transit)
                    transitTab = t
                    val kind = lastTransit[t] ?: transitKindsIn(city).first { t in it.groups && !it.list && it != TransitKind.Remove }
                    if (kind != transitKind) lineDraft = emptyList()
                    transitKind = kind
                }
                is WaterGroup -> {
                    if (tool != Tool.Water) pick(Tool.Water)
                    waterTab = t
                    waterKind = lastWater[t] ?: waterKindsIn(city).first { t in it.groups && it != WaterKind.Remove }
                }
            }
        }

        /** Steps through the tray's tabs by [by], round to the first after the last. */
        fun stepTab(by: Int) {
            // The services' tabs are kinds of service.
            if (tool == Tool.Services || tool == Tool.Leisure) {
                val leisure = tool == Tool.Leisure
                val now = if (leisure) leisureKind else serviceKind
                val groups = servicesIn(city, leisure).map { it.group }.distinct()
                if (groups.size < 2) return
                val g = groups[((groups.indexOf(now.group) + by) % groups.size + groups.size) % groups.size]
                val next = lastService[g] ?: servicesIn(city, leisure).first { it.group == g }
                if (leisure) leisureKind = next else serviceKind = next
                return
            }
            val tabs = toolTabKeys(tool, city)
            if (tabs.size < 2) return
            val transitNow = if (transitTab in transitKind.groups) transitTab else transitKind.groups.first()
            val waterNow = if (waterTab in waterKind.groups) waterTab else waterKind.groups.first()
            val now = tabs.indexOf(when (tool) {
                Tool.Transit -> transitNow
                Tool.Water -> waterNow
                else -> tool
            }).coerceAtLeast(0)
            openTab(tabs[((now + by) % tabs.size + tabs.size) % tabs.size])
        }

        // A toolbar button: its tool used last, or again on the open one to fold or open its choices.
        fun pickGroup(g: ToolGroup) {
            // Pressed again, it puts the tool away and goes back to looking.
            if (tool.group == g) {
                if (choosingOverlay) choosingOverlay = false else if (g != ToolGroup.Inspect) pick(Tool.Inspect)
                return
            }
            pick(lastTool[g]?.takeIf { it != Tool.Districts || city.allowsDistricts() } ?: g.tools.first())
        }

        fun tell(problem: Problem?) {
            message = when (problem) {
                Problem.NotEnoughMoney -> Message(Res.string.not_enough_money)
                Problem.TownBuiltThere -> Message(Res.string.town_built_there)
                Problem.Blocked -> Message(Res.string.blocked)
                Problem.NeedsTrack -> Message(Res.string.needs_track)
                Problem.NeedsWater -> Message(Res.string.needs_water)
                Problem.NeedsTramTrack -> Message(Res.string.needs_tram_track)
                Problem.NeedsTunnel -> Message(Res.string.needs_tunnel)
                Problem.NoRoute -> Message(Res.string.no_route)
                Problem.NoSeaRoute -> Message(Res.string.no_sea_route)
                Problem.CutsOffPort -> Message(Res.string.cuts_off_port)
                else -> message
            }
        }

        fun undo() {
            drag = null
            val plan = game.undo()
            if (plan == null) message = Message(Res.string.nothing_to_undo) else tell(plan.problem)
        }

        fun redo() {
            drag = null
            tell(game.redo()?.problem)
        }

        // Esc and the back button: let go of a drag, close what's open, put the tool down, then the menu.
        fun back() {
            // From the tool buttons or a tray, back to the map and no further.
            val fromTools = overFocused
            // The keys come back to the map from wherever they were.
            runCatching { focus.requestFocus() }
            when {
                fromTools && drag == null && inspected == null && !choosingOverlay && !budgetOpen && !graphsOpen && !peopleOpen && !demandOpen &&
                    !linesOpen && !districtsOpen && eraShown == null -> {}
                budgetOpen || graphsOpen || peopleOpen || demandOpen || linesOpen || districtsOpen || eraShown != null -> {
                    budgetOpen = false; graphsOpen = false; peopleOpen = false; demandOpen = false; linesOpen = false; districtsOpen = false; eraShown = null
                }
                drag != null -> drag = null
                choosingOverlay -> choosingOverlay = false
                inspected != null -> inspected = null
                tool == Tool.Inspect -> onMenu()
                else -> pick(Tool.Inspect)
            }
        }
        // Android picks the back handler that was added last, so this
        // one stands aside while one of the app's windows is open over the game.
        BackButton(enabled = !windowOpen) { back() }

        fun actionOf(d: ToolDrag) = d.action(
            tool, zoneKind, densityKind.within(zoneKind, city), bulldozeKind, powerKind, if (tool == Tool.Leisure) leisureKind else serviceKind, roadKind, roadPipes, railKind, waterKind, transitKind, city.map,
            junctionKind, districtChoice, phoneKind, portKind, bridgeKind, tunnelling, airKind, city::newest,
        )

        /** Lets go of the drag: does what it's for. */
        fun toolUp() {
            val d = drag
            drag = null
            // Planning a line: each stop of its kind tapped joins it, in order.
            if (d != null && tool == Tool.Transit && transitKind.line != 0) {
                val i = city.map.index(d.x1, d.y1)
                val kind = if (transitKind.line == 2) Stop.TRAM else Stop.BUS
                if (city.map.stop[i].toInt() and kind != 0 && lineDraft.lastOrNull() != i) lineDraft = lineDraft + i
            }
            val action = d?.let(::actionOf)
            if (action != null) {
                val made = action is Action.PaintDistrict && action.id == NEW_DISTRICT
                val plan = game.apply(action)
                tell(plan.problem)
                // Heard where it is on screen, more or less.
                val x = if (plan.changes.isEmpty()) camera.centreX else plan.changes.sumOf { it % city.map.width }.toFloat() / plan.changes.size
                val across = viewSize.width / camera.tilePx / 2f
                Sounds.action(action, plan, city, if (across > 0f) (x - camera.centreX) / across else 0f)
                // What it did, such as people forced out by a clearing, is told at once.
                game.takeEvents(::showEvent)
                // Out of the way once something's built, to see it.
                if (plan.ok) trayFolded = true
                // Once made, go on painting into the new district.
                if (made && plan.ok) city.districts.lastOrNull()?.let { districtChoice = it.id }
            }
        }

        val gestures = MapGestures(
            toolActive = tool != Tool.Inspect,
            onToolDown = { x, y -> cursor = null; drag = ToolDrag(x, y, x, y) },
            onToolMove = { x, y -> drag = drag?.to(x, y) },
            onToolUp = ::toolUp,
            onToolCancel = { drag = null },
            onTap = { x, y -> cursor = null; if (tool == Tool.Inspect) inspected = x to y },
        )

        /** Pans just enough to keep tile [x], [y] a few tiles in from the edges of the view. */
        fun keepInView(x: Int, y: Int) {
            if (viewSize.width <= 0f) return
            val p = camera.tileToScreen(x + 0.5f, y + 0.5f, viewSize)
            val mx = minOf(camera.tilePx * CURSOR_MARGIN, viewSize.width / 3f)
            val my = minOf(camera.tilePx * CURSOR_MARGIN, viewSize.height / 3f)
            val dx = when {
                p.x < mx -> mx - p.x
                p.x > viewSize.width - mx -> viewSize.width - mx - p.x
                else -> 0f
            }
            val dy = when {
                p.y < my -> my - p.y
                p.y > viewSize.height - my -> viewSize.height - my - p.y
                else -> 0f
            }
            if (dx != 0f || dy != 0f) camera.panBy(dx, dy)
        }

        /** Moves the cursor [dx], [dy] tiles, starting it in the middle of the view, and a drag's far end with it. */
        fun moveCursor(dx: Int, dy: Int) {
            val m = city.map
            val (cx, cy) = cursor ?: (camera.centreX.toInt() to camera.centreY.toInt())
            val x = (cx + dx).coerceIn(0, m.width - 1)
            val y = (cy + dy).coerceIn(0, m.height - 1)
            cursor = x to y
            drag = drag?.to(x, y)
            keepInView(x, y)
        }

        /**
         * The tool at the cursor: inspects, or starts a drag and then finishes
         * it where the cursor's been moved to. A building, a stop or anything
         * else that goes on one tile goes in at once.
         */
        fun useAtCursor() {
            val at = cursor
            if (at == null) {
                moveCursor(0, 0)
                return
            }
            val (x, y) = at
            if (tool == Tool.Inspect) {
                inspected = x to y
                return
            }
            if (drag != null) {
                toolUp()
                return
            }
            drag = ToolDrag(x, y, x, y)
            val one = actionOf(ToolDrag(x, y, x, y))
            if (one == null || one is Action.PlaceBuilding || one is Action.PlaceStop || one is Action.FitScrubbers) toolUp()
        }

        // Pans and zooms while a key is held, at the same speed whatever the frame rate.
        LaunchedEffect(keys.heldVersion) {
            if (keys.held.isEmpty()) return@LaunchedEffect
            var last = withFrameNanos { it }
            var held = 0f
            var sinceStep = 0f
            while (true) {
                val now = withFrameNanos { it }
                val seconds = (now - last) / 1e9f
                last = now
                val step = KEY_PAN_DP * density * seconds
                var dx = 0f
                var dy = 0f
                if (KeyAction.PanUp in keys.held) dy += step
                if (KeyAction.PanDown in keys.held) dy -= step
                if (KeyAction.PanLeft in keys.held) dx += step
                if (KeyAction.PanRight in keys.held) dx -= step
                if (dx != 0f || dy != 0f) camera.panBy(dx, dy)
                val centre = Offset(viewSize.width / 2f, viewSize.height / 2f)
                if (KeyAction.ZoomIn in keys.held) camera.zoomBy(1f + KEY_ZOOM * seconds, centre, viewSize)
                if (KeyAction.ZoomOut in keys.held) camera.zoomBy(1f / (1f + KEY_ZOOM * seconds), centre, viewSize)
                // A cursor key held down: after a moment, a tile at a time and quicker.
                held += seconds
                if (held >= CURSOR_DELAY) {
                    sinceStep += seconds
                    while (sinceStep >= CURSOR_EVERY) {
                        sinceStep -= CURSOR_EVERY
                        var cx = 0
                        var cy = 0
                        if (KeyAction.CursorUp in keys.held) cy--
                        if (KeyAction.CursorDown in keys.held) cy++
                        if (KeyAction.CursorLeft in keys.held) cx--
                        if (KeyAction.CursorRight in keys.held) cx++
                        if (cx != 0 || cy != 0) moveCursor(cx, cy)
                    }
                }
            }
        }
        /** What a key or a controller's button does on the map. */
        fun doAction(action: KeyAction) {
            if (action.dev && !platform.devKeys) return
        when (action) {
            KeyAction.CursorUp -> moveCursor(0, -1)
            KeyAction.CursorDown -> moveCursor(0, 1)
            KeyAction.CursorLeft -> moveCursor(-1, 0)
            KeyAction.CursorRight -> moveCursor(1, 0)
            KeyAction.Use -> useAtCursor()
            KeyAction.ToolPhone -> pick(Tool.Phone)
            KeyAction.ToolPorts -> pick(Tool.Port)
            KeyAction.ToolAir -> pick(Tool.Air)
            KeyAction.PrevChoice -> stepKind(-1)
            KeyAction.NextChoice -> stepKind(1)
            KeyAction.PrevTab -> stepTab(-1)
            KeyAction.NextTab -> stepTab(1)
            KeyAction.ToolInspect -> pick(Tool.Inspect)
            KeyAction.ToolBulldoze -> pick(Tool.Bulldoze)
            KeyAction.ToolRoad -> pick(Tool.Road)
            KeyAction.ToolRail -> pick(Tool.Rail)
            KeyAction.ToolWater -> pick(Tool.Water)
            KeyAction.ToolZone -> pick(Tool.Zone)
            KeyAction.ToolPower -> pick(Tool.Power)
            KeyAction.ToolServices -> pick(Tool.Services)
            KeyAction.ToolLeisure -> pick(Tool.Leisure)
            KeyAction.ToolTransit -> pick(Tool.Transit)
            KeyAction.ToolTraffic -> pick(Tool.Traffic)
            KeyAction.ToolDistricts -> if (city.allowsDistricts()) pick(Tool.Districts)
            KeyAction.Budget -> budgetOpen = !budgetOpen
            KeyAction.Graphs -> graphsOpen = !graphsOpen
            KeyAction.People -> peopleOpen = !peopleOpen
            KeyAction.Demand -> demandOpen = !demandOpen
            KeyAction.NextOverlay -> overlay = Overlay.entries[(overlay.ordinal + 1) % Overlay.entries.size]
            KeyAction.Speed1 -> { speed = 0; paused = false }
            KeyAction.Speed2 -> { speed = 1; paused = false }
            KeyAction.Speed3 -> { speed = 2; paused = false }
            KeyAction.Speed4 -> { speed = 3; paused = false }
            KeyAction.Pause -> paused = !paused
            KeyAction.Undo -> undo()
            KeyAction.Redo -> redo()
            // Esc lets go of a drag, then closes the inspector, then puts the tool down.
            KeyAction.Back -> back()
            KeyAction.Tools -> runCatching { toolsFocus.requestFocus() }
            KeyAction.Menu -> onMenu()
            KeyAction.DevSeasonBack -> lookOverride = (look + Atlas.LOOKS - 1) % Atlas.LOOKS
            KeyAction.DevSeasonNext -> lookOverride = (look + 1) % Atlas.LOOKS
            KeyAction.DevHourBack -> hourShift -= 24f / Sky.STEPS
            KeyAction.DevHourNext -> hourShift += 24f / Sky.STEPS
            KeyAction.DevFire -> inspected?.let { (x, y) -> game.locked { city.startFireAt(x, y) } }
            KeyAction.DevWeather -> weatherOverride = if (weatherOverride + 1 >= DEV_WEATHER.size) -1 else weatherOverride + 1
            KeyAction.DevGraphics -> settings.graphics = GraphicsLevel.entries[(settings.graphics.ordinal + 1) % GraphicsLevel.entries.size]
            else -> {}
        }
        }

        // The keys come back to the map whenever a window or panel over it closes, which takes the focus with it.
        val anyOpen = windowOpen || budgetOpen || graphsOpen || peopleOpen || demandOpen || linesOpen || districtsOpen || eraShown != null || inspected != null
        LaunchedEffect(anyOpen) { if (!anyOpen) focus.requestFocus() }

        // A controller's buttons do on the map what they're set to, as keys do. Away from the map the
        // ways, Use and Back move about the buttons instead, and the rest still work while nothing's open.
        PadListener { b, pressed ->
            if (!pressed) return@PadListener keys.onPad(b, false, settings.pad) {}
            if (!b.bindable) return@PadListener mapFocused
            val free = !windowOpen && !anyOpen
            if (!mapFocused && !overFocused && free) runCatching { focus.requestFocus() }
            else if (!mapFocused) {
                val action = settings.pad[b]
                val moving = action == KeyAction.Use || action == KeyAction.Back || action in CURSOR_ACTIONS
                if (action == null || moving || !free) return@PadListener false
            }
            keys.onPad(b, true, settings.pad) { doAction(it) }
        }
        // The sticks: the left one moves the cursor, quicker the further it's pushed, and the right one pans.
        LaunchedEffect(Unit) {
            snapshotFlow { Pad.moving && mapFocused }.collectLatest { on ->
                if (!on) return@collectLatest
                var last = withFrameNanos { it }
                var fx = 0f
                var fy = 0f
                while (true) {
                    val now = withFrameNanos { it }
                    val seconds = ((now - last) / 1e9f).coerceAtMost(0.1f)
                    last = now
                    val r = Pad.right
                    val step = KEY_PAN_DP * density * seconds
                    if (r != Offset.Zero) camera.panBy(-r.x * step, -r.y * step)
                    val l = Pad.left
                    if (l == Offset.Zero) {
                        fx = 0f
                        fy = 0f
                    } else {
                        val tiles = maxOf(STICK_TILES, STICK_DP * density / camera.tilePx) * seconds
                        fx += l.x * tiles
                        fy += l.y * tiles
                        val sx = fx.toInt()
                        val sy = fy.toInt()
                        if (sx != 0 || sy != 0) {
                            fx -= sx
                            fy -= sy
                            moveCursor(sx, sy)
                        }
                    }
                }
            }
        }

        // For screen readers the map is one thing, read out at the cursor. Its
        // actions move the cursor and use the tool there, as the arrows and
        // Enter do, and what's at the cursor is said each time it moves.
        val mapSaid = stringResource(Res.string.name_colon_value, stringResource(Res.string.the_map), stringResource(tool.title))
        val useLabel = stringResource(Res.string.key_use)
        val here = cursor?.let { (x, y) ->
            val summary = tileSummary(game, x, y)
            if (drag != null && costText.isNotEmpty()) stringResource(Res.string.list_join, summary, costText) else summary
        } ?: stringResource(Res.string.map_no_cursor)
        var cursorSaid by remember { mutableStateOf("") }
        // Only when the cursor or the tool moves on, so a busy town doesn't talk over itself.
        LaunchedEffect(cursor, drag, tool) { cursorSaid = here }
        val far = CURSOR_FAR
        val ways = listOf(
            stringResource(Res.string.key_cursor_up) to (0 to -1), stringResource(Res.string.key_cursor_down) to (0 to 1),
            stringResource(Res.string.key_cursor_left) to (-1 to 0), stringResource(Res.string.key_cursor_right) to (1 to 0),
            stringResource(Res.string.cursor_far_up, far) to (0 to -far), stringResource(Res.string.cursor_far_down, far) to (0 to far),
            stringResource(Res.string.cursor_far_left, far) to (-far to 0), stringResource(Res.string.cursor_far_right, far) to (far to 0),
        )
        val letGo = stringResource(Res.string.let_go)
        val mapActions = ways.map { (label, d) -> CustomAccessibilityAction(label) { moveCursor(d.first, d.second); true } } +
            listOf(CustomAccessibilityAction(useLabel) { useAtCursor(); true }) +
            (if (drag != null) listOf(CustomAccessibilityAction(letGo) { drag = null; true }) else emptyList())

        // Panels draw buildings in their own art.
        CompositionLocalProvider(LocalAtlas provides atlas) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .onFocusChanged {
                    mapFocused = it.isFocused
                    overFocused = it.hasFocus && !it.isFocused
                    if (!it.hasFocus) keys.releaseAll()
                }
                .focusable()
                .onPreviewKeyEvent { event ->
                    // With a button, a tray or a window focused, Tab, Enter and the arrows are theirs.
                    if (!mapFocused) return@onPreviewKeyEvent false
                    keys.onKey(event) { action ->
                        doAction(action)
                    }
                },
        ) {
            val layout = screenLayout(maxWidth, maxHeight)
            val screenHeight = maxHeight
            viewSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            MapView(
                game, atlas, camera, look, shadowStep, sun, tint, weather, !paused, SPEEDS[speed].toFloat(), graphics, gestures, preview, costText, overlay,
                underground = tool == Tool.Water || tunnelling && (tool == Tool.Road || tool == Tool.Rail && railKind == RailKind.Track) ||
                    tool == Tool.Bulldoze && bulldozeKind == BulldozeKind.Tunnel || (tool == Tool.Transit && (transitKind == TransitKind.Subway || transitKind == TransitKind.Station)) ||
                    (tool == Tool.Power && powerKind.buried) || (tool == Tool.Phone && phoneKind.duct),
                focus = inspected?.let { (x, y) -> city.map.index(x, y) } ?: -1,
                districts = if (tool != Tool.Districts) emptyList() else { game.revision; city.districts.map { it.id to it.name } },
                lines = if (tool != Tool.Transit) emptyList() else {
                    game.revision
                    val drawn = city.lines.mapNotNull { line -> city.lineState(line.id)?.takeIf { it.route.isNotEmpty() }?.let { line.id to it.route } }
                    val draft = city.routeFor(lineDraft, transitKind.line == 2)
                    if (draft != null) drawn + (0 to draft) else drawn
                },
                hour = hour,
                cursor = cursor,
                modifier = Modifier.fillMaxSize().semantics {
                    contentDescription = mapSaid
                    stateDescription = cursorSaid
                    liveRegion = LiveRegionMode.Polite
                    // A double tap uses the tool at the cursor, where a tap would land in the middle of the screen.
                    onClick(label = useLabel) { useAtCursor(); true }
                    customActions = mapActions
                },
            )

            val safe = WindowInsets.safeDrawing
            val gap = if (layout.compact) 6.dp else 10.dp
            val sideTools = layout.large || layout.shape == ScreenShape.Wide
            val compactTools = layout.compact || layout.short || layout.narrow
            // Every upright phone: one line can't hold the buttons and the readings without crowding them.
            val twoLines = layout.shape == ScreenShape.Tall && !layout.large
            // A phone on its side hasn't the height for undo and redo down the rail, so they go along the top.
            val historyOnTop = twoLines || (sideTools && layout.short)
            // On an upright phone with its camera in the top edge, the strip goes up round it: if each camera is small
            // enough to leave room for the buttons, and short enough to stay within the strip's first row.
            val windowPx = with(LocalDensity.current) { maxWidth.toPx() }
            val rowPx = with(LocalDensity.current) { CAMERA_ROW.dp.toPx() }
            val deepestPx = with(LocalDensity.current) { (CAMERA_ROW + CAMERA_DEEPER).dp.toPx() }
            val topCameras = cameraCutouts().filter { it.top <= 0f && it.height > 0f }
            val cameras = topCameras.let { top ->
                if (twoLines && top.isNotEmpty() && top.all { it.width <= windowPx * CAMERA_SHARE && it.bottom <= deepestPx }) top.map { it.left..it.right } else emptyList()
            }
            // A camera deeper than the first row takes the strip down with it, so it still sits level with the row.
            val cameraDrop = with(LocalDensity.current) {
                if (cameras.isEmpty()) 0.dp else ((topCameras.maxOf { it.bottom } - rowPx).coerceAtLeast(0f)).toDp()
            }
            StatusStrip(
                game, onMenu, paused, { paused = !paused }, speed, { speed = (speed + 1) % SPEEDS.size },
                overlay != Overlay.None || choosingOverlay, { if (overlay != Overlay.None) viewTab = viewGroup(overlay); choosingOverlay = !choosingOverlay },
                { budgetOpen = true }, { peopleOpen = true }, { eraShown = city.era },
                Sky.sun(sunStep, month).strength == 0f, layout.compact || layout.narrow,
                twoLines = twoLines, withHistory = historyOnTop, onUndo = ::undo, onRedo = ::redo,
                if (cameras.isNotEmpty()) {
                    // Up into the band the camera takes, across the screen, the camera sitting on it with the buttons clear of it.
                    Modifier
                        .align(Alignment.TopCenter)
                        .onSizeChanged { stripSize = it }
                        .windowInsetsPadding(safe.only(WindowInsetsSides.Horizontal))
                        .padding(start = gap, end = gap, top = CAMERA_GAP.dp + cameraDrop)
                        .fillMaxWidth()
                } else {
                    Modifier
                        .align(Alignment.TopCenter)
                        .onSizeChanged { stripSize = it }
                        .windowInsetsPadding(safe.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(gap)
                        // Two lines take the width, so their ends line up.
                        .then(if (twoLines) Modifier.fillMaxWidth() else Modifier)
                },
                cameras = cameras,
                onDemand = { demandOpen = true },
            )
            // Under the strip, however many lines it takes: what's holding the town back, then any message.
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = with(LocalDensity.current) { stripSize.height.toDp() })
                    .windowInsetsPadding(safe.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = gap),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                game.revision
                // A new town waits, paused, until play is pressed; this says so, and starts it.
                if (paused && city.stats.population == 0) {
                    MessageChip(stringResource(Res.string.paused_hint), { paused = false }, clickLabel = stringResource(Res.string.play))
                }
                // No way in shows the moment it's so and goes the moment a road's out, without waiting for the month to end.
                val advice = city.advice.filter { it.kind != AdviceKind.NO_WAY_IN }.let { rest ->
                    if (city.needsWayIn()) listOf(Advice(AdviceKind.NO_WAY_IN)) + rest else rest
                }
                AdviceLine(advice, { x, y -> camera.centreOn(x, y) })
                message?.let { m ->
                    MessageChip(
                        when {
                            m.counted != null -> pluralStringResource(m.counted, m.count, groupThousands(m.count.toLong()))
                            m.text == null -> ""
                            m.arg != null -> stringResource(m.text, stringResource(m.arg))
                            m.name != null -> stringResource(m.text, m.name)
                            else -> stringResource(m.text)
                        },
                        if (m.x >= 0) ({ camera.centreOn(m.x, m.y) }) else null,
                    )
                }
            }
            // Down the side away from the camera, or the side the player picked; on the right with no camera on either side.
            val cutout = WindowInsets.displayCutout
            val dir = LocalLayoutDirection.current
            val px = LocalDensity.current
            val toolsRight = when (settings.toolSide) {
                ToolSide.Left -> false
                ToolSide.Right -> true
                ToolSide.Auto -> when {
                    cutout.getLeft(px, dir) > 0 -> true
                    cutout.getRight(px, dir) > 0 -> false
                    else -> true
                }
            }
            if (sideTools) {
                ToolBar(
                    tool.group, ::pickGroup, game.canUndo, game.canRedo, ::undo, ::redo, vertical = true, compact = compactTools,
                    modifier = Modifier
                        .align(if (toolsRight) Alignment.CenterEnd else Alignment.CenterStart)
                        .windowInsetsPadding(safe.only(if (toolsRight) WindowInsetsSides.End else WindowInsetsSides.Start))
                        .padding(gap),
                    withHistory = !historyOnTop,
                    focus = toolsFocus,
                )
            }
            // Along the bottom: what's being inspected, the chosen tool's choices, and on an upright phone the tools.
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(safe.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(gap),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                inspected?.let { (x, y) ->
                    InspectPanel(
                        game, x, y, onClose = { inspected = null },
                        onAction = { action ->
                            val plan = game.apply(action)
                            tell(plan.problem)
                            Sounds.action(action, plan, city)
                            game.takeEvents(::showEvent)
                        },
                        onLines = { linesOpen = true },
                        maxHeight = screenHeight * INSPECT_SHARE,
                        modifier = Modifier.widthIn(max = TRAY_WIDTH.dp).fillMaxWidth(),
                    )
                }
                if (tool == Tool.Transit && transitKind.line != 0) {
                    val tram = transitKind.line == 2
                    LineDraftBar(lineDraft.size, tram, { lineDraft = emptyList() }) {
                        tell(game.apply(Action.AddLine(tram, lineDraft.toIntArray(), city.suggestedVehicles(lineDraft, tram))).problem)
                        lineDraft = emptyList()
                    }
                }
                val trayWidth = Modifier.widthIn(max = TRAY_WIDTH.dp)
                // The map views: their tiles while choosing, then a line naming the one showing.
                if (choosingOverlay || overlay != Overlay.None) {
                    // Folded, the line names the view showing, so its kind's tiles are the ones looked at.
                    val group = if (choosingOverlay) viewTab else viewGroup(overlay)
                    ChoiceTray(
                        atlas, overlayChoices(group), overlay, { overlay = it; choosingOverlay = false },
                        folded = !choosingOverlay, onFold = { choosingOverlay = !it }, modifier = trayWidth,
                        tabs = viewTabs(), tab = group, onTab = { if (it is ViewGroup) viewTab = it }, blank = stringResource(Res.string.overlayTitle),
                        onClose = if (overlay == Overlay.None) null else ({ overlay = Overlay.None; choosingOverlay = false }),
                    ) { if (overlay != Overlay.None) Legend(overlay.low.copy(alpha = max(overlay.low.alpha, 0.35f)), overlay.high.copy(alpha = 1f)) }
                }
                if (!choosingOverlay) {
                    val tabs = toolTabs(tool, city)
                    val fold = { f: Boolean -> trayFolded = f }
                    // The tab open: a key stepping on to a kind in another tab takes the tab with it.
                    val transitNow = if (transitTab in transitKind.groups) transitTab else transitKind.groups.first()
                    val waterNow = if (waterTab in waterKind.groups) waterTab else waterKind.groups.first()
                    val onTab = { t: Any -> openTab(t) }
                    when (tool) {
                        Tool.Inspect -> {}
                        Tool.Zone -> ChoiceTray(atlas, zoneChoices(city, densityKind), zoneKind, { zoneKind = it }, trayFolded, fold, trayWidth, tabs, tool, onTab) {
                            val chosen = densityKind.within(zoneKind, city)
                            for (d in densitiesFor(zoneKind, city)) {
                                TrayToggle(densityGlyph(d), stringResource(d.title), d == chosen) { densityKind = d }
                            }
                        }
                        Tool.Districts -> {
                            game.revision
                            // A district chosen that's since gone falls back to making a new one.
                            if (districtChoice > 0 && city.districts.none { it.id == districtChoice }) districtChoice = NEW_DISTRICT
                            val choices = listOf(
                                Choice(NEW_DISTRICT, stringResource(Res.string.new_district), ChoiceIcon(glyph = Glyph.Plus)),
                                Choice(0, stringResource(Res.string.erase_district), ChoiceIcon(glyph = Glyph.Erase)),
                            ) + city.districts.map { d ->
                                Choice(d.id, d.name, ChoiceIcon(back = lineColour(d.id), letters = d.name.take(2).uppercase()))
                            } + Choice(DISTRICT_LIST, stringResource(Res.string.districts), ChoiceIcon(glyph = Glyph.List))
                            ChoiceTray(
                                atlas, choices, districtChoice, { if (it == DISTRICT_LIST) districtsOpen = true else districtChoice = it },
                                trayFolded, fold, trayWidth, tabs, tool, onTab,
                            )
                        }
                        Tool.Road -> ChoiceTray(atlas, roadChoices(city), roadKind, { roadKind = it }, trayFolded, fold, trayWidth, tabs, tool, onTab) {
                            if (roadKind.bridges) {
                                val kinds = bridgeChoices(city, rail = false)
                                TrayCycle(Glyph.Bridge, stringResource(Res.string.label_bridge_kind), stringResource(bridgeName(bridgeKind))) {
                                    bridgeKind = kinds[(kinds.indexOf(bridgeKind) + 1) % kinds.size]
                                }
                            }
                            if (city.allowsTunnel(rail = false)) TrayToggle(Glyph.Tunnel, stringResource(Res.string.dig_tunnel), tunnelling) { tunnelling = !tunnelling }
                            TrayToggle(Glyph.Pipe, stringResource(Res.string.road_with_pipes), roadPipes) { roadPipes = !roadPipes }
                        }
                        Tool.Rail -> ChoiceTray(atlas, railChoices(city), railKind, { railKind = it }, trayFolded, fold, trayWidth, tabs, tool, onTab) {
                            if (railKind == RailKind.Track) {
                                val kinds = bridgeChoices(city, rail = true)
                                val shown = bridgeKind?.takeIf { it.rail }
                                TrayCycle(Glyph.Bridge, stringResource(Res.string.label_bridge_kind), stringResource(bridgeName(shown))) {
                                    bridgeKind = kinds[(kinds.indexOf(shown) + 1) % kinds.size]
                                }
                                TrayToggle(Glyph.Tunnel, stringResource(Res.string.dig_tunnel), tunnelling) { tunnelling = !tunnelling }
                            }
                        }
                        Tool.Port -> ChoiceTray(atlas, portChoices(city), portKind, { portKind = it }, trayFolded, fold, trayWidth, tabs, tool, onTab)
                        Tool.Air -> ChoiceTray(atlas, airChoices(city), airKind, { airKind = it }, trayFolded, fold, trayWidth, tabs, tool, onTab)
                        Tool.Transit -> ChoiceTray(atlas, transitChoices(city, transitNow), transitKind, {
                            if (it.list) linesOpen = true
                            else {
                                if (it != transitKind) lineDraft = emptyList()
                                transitKind = it
                                lastTransit[transitNow] = it
                            }
                        }, trayFolded, fold, trayWidth, tabs, transitNow, onTab)
                        Tool.Traffic -> ChoiceTray(atlas, junctionChoices(city), junctionKind, { junctionKind = it }, trayFolded, fold, trayWidth, tabs, tool, onTab)
                        Tool.Power -> ChoiceTray(atlas, powerChoices(city), powerKind, { powerKind = it }, trayFolded, fold, trayWidth, tabs, tool, onTab)
                        Tool.Phone -> ChoiceTray(atlas, phoneChoices(city), phoneKind, { phoneKind = it }, trayFolded, fold, trayWidth, tabs, tool, onTab)
                        Tool.Water -> ChoiceTray(
                            atlas, waterChoices(city, waterNow), waterKind, { waterKind = it; lastWater[waterNow] = it },
                            trayFolded, fold, trayWidth, tabs, waterNow, onTab,
                        )
                        // One tab for each kind of service, each opening on the one used last.
                        Tool.Services -> ChoiceTray(
                            atlas, serviceChoices(city, serviceKind.group), serviceKind, { serviceKind = it; lastService[it.group] = it },
                            trayFolded, fold, trayWidth, serviceTabs(city), serviceKind.group,
                            { g -> if (g is ServiceGroup) serviceKind = lastService[g] ?: servicesIn(city).first { it.group == g } },
                        )
                        // Parks, sport and culture, the same way.
                        Tool.Leisure -> ChoiceTray(
                            atlas, serviceChoices(city, leisureKind.group), leisureKind, { leisureKind = it; lastService[it.group] = it },
                            trayFolded, fold, trayWidth, serviceTabs(city, leisure = true), leisureKind.group,
                            { g -> if (g is ServiceGroup) leisureKind = lastService[g] ?: servicesIn(city, leisure = true).first { it.group == g } },
                        )
                        Tool.Bulldoze -> ChoiceTray(atlas, bulldozeChoices(), bulldozeKind, { bulldozeKind = it }, trayFolded, fold, trayWidth, tabs, tool, onTab)
                    }
                }
                if (!sideTools) {
                    ToolBar(tool.group, ::pickGroup, game.canUndo, game.canRedo, ::undo, ::redo, vertical = false, compact = compactTools, modifier = trayWidth, withHistory = !historyOnTop, focus = toolsFocus)
                }
            }
            if (budgetOpen) BudgetWindow(game) { budgetOpen = false }
            if (linesOpen) LinesWindow(game) { linesOpen = false }
            if (districtsOpen) DistrictsWindow(game) { districtsOpen = false }
            if (graphsOpen) GraphsWindow(game) { graphsOpen = false }
            eraShown?.let { EraWindow(game, it) { eraShown = null } }
            if (peopleOpen) PeopleWindow(game, { peopleOpen = false; graphsOpen = true }) { peopleOpen = false }
            if (demandOpen) DemandWindow(game) { demandOpen = false }
        }
        }
    }
}

/** A tile on screen, in dp: the whole range, and where a new city starts. */
/** The left stick pushed all the way moves the cursor this many tiles a second, or this far on the screen when zoomed out. */
private const val STICK_TILES = 8f
private const val STICK_DP = 500f

/** How far a screen reader's long cursor steps go, in tiles. */
private const val CURSOR_FAR = 8

private val CURSOR_ACTIONS = setOf(KeyAction.CursorUp, KeyAction.CursorDown, KeyAction.CursorLeft, KeyAction.CursorRight)

/** Tiles the keyboard's cursor keeps from the edge of the view, and how its keys repeat: after a moment, then this often. */
private const val CURSOR_MARGIN = 3f
private const val CURSOR_DELAY = 0.35f
private const val CURSOR_EVERY = 0.06f

private const val MIN_TILE_DP = 3f
private const val MAX_TILE_DP = 96f
private const val START_TILE_DP = 24f

/** How fast the keys pan, in dp a second, and zoom, as a factor a second. */
private const val KEY_PAN_DP = 600f
private const val KEY_ZOOM = 1.5f


/** The widest the toolbar and its tray go along the bottom. */
private const val TRAY_WIDTH = 520

/** The most of the screen's height the inspect card's figures take before they scroll. */
private const val INSPECT_SHARE = 0.4f

/** A message for the top of the screen, with a building's name in it and a tile to go to if it's about a place. */
private data class Message(
    /** Null when it's [counted] instead. */
    val text: StringResource?,
    val arg: StringResource? = null,
    val x: Int = -1,
    val y: Int = -1,
    val name: String? = null,
    /** Said with a count instead, as the language says that many. */
    val counted: PluralStringResource? = null,
    val count: Int = 0,
)

/** Weather the W key steps through while the looks are being made: clear, cloudy, rain, snow, fog. */
private val DEV_WEATHER = listOf(
    WeatherLook.of(5, Precipitation.None, 0, false, 250, 30),
    WeatherLook.of(60, Precipitation.None, 0, false, 250, 50),
    WeatherLook.of(90, Precipitation.Rain, 70, false, 250, 60),
    WeatherLook.of(85, Precipitation.Snow, 60, false, 300, 40),
    WeatherLook.of(55, Precipitation.None, 0, true, 250, 10),
)

/**
 * A month is one day and night, and lasts this long at normal speed. Slow is
 * half as fast and fast four times. No more than a few days run in one frame.
 */
private const val SECONDS_PER_MONTH = 600.0
private val SPEEDS = doubleArrayOf(0.5, 1.0, 4.0, 10.0)
private const val MAX_DAYS_PER_FRAME = 4

/** The most days kept waiting while the sim works out a long day, such as the turn of the month. */
private const val MAX_DAYS_OWED = 31

/**
 * Round a camera in the top edge: the gap above the strip, the deepest a
 * camera can reach and still sit within its first row, and the widest it can
 * be, as a share of the screen, and leave room for the buttons.
 */
private const val CAMERA_GAP = 6
private const val CAMERA_ROW = 54

/** How much deeper than the first row a camera can be, in dp, with the strip moved down to meet it. */
private const val CAMERA_DEEPER = 24
private const val CAMERA_SHARE = 0.3f

/** How long a message stays. */
private const val MESSAGE_MS = 2500L

/** How often the light changes: this many times an hour of the game's day. */
private const val LIGHT_STEPS_PER_HOUR = 8f
