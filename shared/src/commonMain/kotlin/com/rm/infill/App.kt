package com.rm.infill

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.Density
import com.rm.infill.platform.AUTOSAVE
import com.rm.infill.platform.BackButton
import com.rm.infill.platform.Settings
import com.rm.infill.platform.ThemeChoice
import com.rm.infill.platform.platform
import com.rm.infill.platform.saveFileName
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import com.rm.infill.map.MapGestures
import com.rm.infill.res.Res
import com.rm.infill.res.money
import com.rm.infill.res.not_enough_money
import com.rm.infill.res.nothing_to_undo
import com.rm.infill.sim.Problem
import com.rm.infill.ui.InspectPanel
import com.rm.infill.ui.MessageChip
import com.rm.infill.ui.Preview
import com.rm.infill.ui.ToolDrag
import com.rm.infill.ui.ZoneKind
import com.rm.infill.ui.OptionPicker
import com.rm.infill.ui.PowerKind
import com.rm.infill.ui.ServiceKind
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
import com.rm.infill.sim.City
import com.rm.infill.ui.CityPanel
import com.rm.infill.ui.KeyAction
import com.rm.infill.ui.KeyInput
import com.rm.infill.ui.ScreenShape
import com.rm.infill.ui.StatusStrip
import com.rm.infill.ui.Tool
import com.rm.infill.ui.ToolBar
import com.rm.infill.ui.screenLayout
import com.rm.infill.ui.theme.InfillTheme

/** The whole app, the same on every platform: the start screen, a new city, and the game. */
@Composable
fun App() {
    val settings = remember { Settings(platform) }
    val dark = when (settings.theme) {
        ThemeChoice.Auto -> isSystemInDarkTheme()
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }
    InfillTheme(dark) {
        // The size setting scales everything drawn in dp and sp at once.
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density * settings.uiScale, base.fontScale)) {
            Screens(settings)
        }
    }
}

private enum class Screen { Start, New, Game }

@Composable
private fun Screens(settings: Settings) {
    var screen by remember { mutableStateOf(Screen.Start) }
    var game by remember { mutableStateOf<GameState?>(null) }
    var loadOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val notice = remember { mutableStateOf<Message?>(null) }
    var savesChanged by remember { mutableIntStateOf(0) }
    val saves = remember(savesChanged, loadOpen, screen) {
        platform.saves().mapNotNull { f -> platform.readSave(f)?.let { SaveGame.summary(it) }?.let { f to it } }
            .sortedBy { if (it.first == AUTOSAVE) 0 else 1 }
    }
    val lastSave = saves.firstOrNull { it.first == AUTOSAVE }?.second

    fun autosave() {
        game?.let { platform.writeSave(AUTOSAVE, SaveGame.write(it.city)) }
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
        game = GameState(city)
        loadOpen = false
        menuOpen = false
        screen = Screen.Game
    }

    // Put away or hidden: the game saves itself.
    val current by rememberUpdatedState(game)
    LaunchedEffect(Unit) {
        platform.onHidden { current?.let { platform.writeSave(AUTOSAVE, SaveGame.write(it.city)) } }
    }

    when (screen) {
        Screen.Start -> StartScreen(
            lastSave,
            onContinue = { load(AUTOSAVE) },
            onNew = { screen = Screen.New },
            onLoad = { loadOpen = true },
            onSettings = { settingsOpen = true },
        )
        Screen.New -> NewCityScreen(
            onStart = { name, seed, options ->
                game = GameState(City(seed, terrain = options).also { it.name = name })
                screen = Screen.Game
            },
            onBack = { screen = if (game != null) Screen.Game else Screen.Start },
        )
        Screen.Game -> game?.let { g ->
            key(g) {
                GameScreen(
                    g, settings, notice, windowOpen = menuOpen || loadOpen || settingsOpen,
                    onMenu = { menuOpen = true }, onNewMonth = { autosave() },
                )
            }
        }
    }
    if (menuOpen) {
        MenuWindow(
            onSave = {
                game?.let {
                    platform.writeSave(saveFileName(it.city.name), SaveGame.write(it.city))
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
        )
    }
    if (loadOpen) {
        LoadWindow(saves, ::load, { platform.deleteSave(it); savesChanged++ }, { loadOpen = false })
    }
    if (settingsOpen) SettingsWindow(settings) { settingsOpen = false }
    // Android's back button: close what's open, or step back a screen. From the start screen it leaves.
    BackButton(enabled = menuOpen || loadOpen || settingsOpen || screen == Screen.New) {
        when {
            settingsOpen -> settingsOpen = false
            loadOpen -> loadOpen = false
            menuOpen -> menuOpen = false
            screen == Screen.New -> screen = if (game != null) Screen.Game else Screen.Start
        }
    }
    if (screen != Screen.Game) {
        notice.value?.let { m ->
            LaunchedEffect(m) { delay(MESSAGE_MS); notice.value = null }
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp), contentAlignment = Alignment.TopCenter) {
                MessageChip(stringResource(m.text), null)
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
    onNewMonth: () -> Unit,
) {
    run {
        val city = game.city
        val density = LocalDensity.current.density
        val camera = remember(density) {
            Camera(city.map.width, city.map.height, MIN_TILE_DP * density, MAX_TILE_DP * density, START_TILE_DP * density)
        }
        var tool by remember { mutableStateOf(Tool.Inspect) }
        var zoneKind by remember { mutableStateOf(ZoneKind.Residential) }
        var powerKind by remember { mutableStateOf(PowerKind.Line) }
        var speed by remember { mutableIntStateOf(1) }
        var drag by remember { mutableStateOf<ToolDrag?>(null) }
        var inspected by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        var message by notice
        var serviceKind by remember { mutableStateOf(ServiceKind.Police) }
        var overlay by remember { mutableStateOf(Overlay.None) }
        var choosingOverlay by remember { mutableStateOf(false) }
        var budgetOpen by remember { mutableStateOf(false) }
        var graphsOpen by remember { mutableStateOf(false) }
        var paused by remember { mutableStateOf(true) }
        val keys = remember { KeyInput() }
        keys.bindings = settings.keys
        val focus = remember { FocusRequester() }
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
        else WeatherLook.of(w.cloud, w.precipitation, w.intensity, w.fog, w.windDirection, w.windSpeed)
        // Snow on the ground decides the winter look; a winter month without it is bare.
        val seasonal = Seasons.lookFor(city.month)
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

        // Time runs at the chosen speed while the game isn't paused, a few days a frame at most.
        LaunchedEffect(paused, speed) {
            if (paused) return@LaunchedEffect
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val monthDays = City.daysIn(city.month, city.year)
                var progress = dayProgress + ((now - last) / 1e9 * monthDays / SECONDS_PER_MONTH * SPEEDS[speed]).toFloat()
                last = now
                val days = min(progress.toInt(), MAX_DAYS_PER_FRAME)
                progress = if (progress.toInt() > MAX_DAYS_PER_FRAME) 0f else progress - days
                dayProgress = progress
                val monthBefore = city.month
                game.tick(days) { e ->
                    val text = when (e.kind) {
                        EventKind.FireStarted -> Res.string.event_fire
                        EventKind.BuildingLost -> Res.string.event_lost
                        EventKind.FireSaved -> Res.string.event_saved
                    }
                    message = Message(text, buildingName(e.type), e.x, e.y)
                }
                if (city.month != monthBefore) onNewMonth()
            }
        }

        LaunchedEffect(message) {
            if (message != null) {
                delay(MESSAGE_MS)
                message = null
            }
        }

        // What the drag would do, worked out again as it moves.
        val preview = remember(drag, tool, zoneKind, powerKind, serviceKind, game.revision) {
            drag?.let { d -> d.action(tool, zoneKind, powerKind, serviceKind, city.map)?.let { Preview(it, city.plan(it), d.x1, d.y1) } }
        }
        val costText = preview?.let {
            if (it.plan.problem == Problem.NotEnoughMoney) stringResource(Res.string.not_enough_money)
            else stringResource(Res.string.money, groupThousands(it.plan.cost))
        } ?: ""

        fun pick(t: Tool) {
            // Picking the zone tool again moves on to the next kind of zone.
            if (t == Tool.Zone && tool == Tool.Zone) zoneKind = ZoneKind.entries[(zoneKind.ordinal + 1) % ZoneKind.entries.size]
            if (t == Tool.Power && tool == Tool.Power) powerKind = PowerKind.entries[(powerKind.ordinal + 1) % PowerKind.entries.size]
            if (t == Tool.Services && tool == Tool.Services) serviceKind = ServiceKind.entries[(serviceKind.ordinal + 1) % ServiceKind.entries.size]
            tool = t
            drag = null
            if (t != Tool.Inspect) inspected = null
        }

        fun tell(problem: Problem?) {
            message = when (problem) {
                Problem.NotEnoughMoney -> Message(Res.string.not_enough_money)
                Problem.TownBuiltThere -> Message(Res.string.town_built_there)
                Problem.Blocked -> Message(Res.string.blocked)
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
            when {
                budgetOpen || graphsOpen -> { budgetOpen = false; graphsOpen = false }
                drag != null -> drag = null
                choosingOverlay -> choosingOverlay = false
                inspected != null -> inspected = null
                tool == Tool.Inspect -> onMenu()
                else -> pick(Tool.Inspect)
            }
        }
        // Android picks the back handler added last, not the one declared last, so this
        // one stands aside while one of the app's windows is open over the game.
        BackButton(enabled = !windowOpen) { back() }

        val gestures = MapGestures(
            toolActive = tool != Tool.Inspect,
            onToolDown = { x, y -> drag = ToolDrag(x, y, x, y) },
            onToolMove = { x, y -> drag = drag?.to(x, y) },
            onToolUp = {
                val d = drag
                drag = null
                val action = d?.action(tool, zoneKind, powerKind, serviceKind, city.map)
                if (action != null) tell(game.apply(action).problem)
            },
            onToolCancel = { drag = null },
            onTap = { x, y -> if (tool == Tool.Inspect) inspected = x to y },
        )

        // Pans and zooms while a key is held, at the same speed whatever the frame rate.
        LaunchedEffect(keys.heldVersion) {
            if (keys.held.isEmpty()) return@LaunchedEffect
            var last = withFrameNanos { it }
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
            }
        }
        LaunchedEffect(Unit) { focus.requestFocus() }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .onFocusChanged { if (!it.hasFocus) keys.releaseAll() }
                .focusable()
                .onPreviewKeyEvent { event ->
                    keys.onKey(event) { action ->
                        when (action) {
                            KeyAction.ToolInspect -> pick(Tool.Inspect)
                            KeyAction.ToolBulldoze -> pick(Tool.Bulldoze)
                            KeyAction.ToolRoad -> pick(Tool.Road)
                            KeyAction.ToolZone -> pick(Tool.Zone)
                            KeyAction.ToolPower -> pick(Tool.Power)
                            KeyAction.ToolServices -> pick(Tool.Services)
                            KeyAction.Budget -> budgetOpen = !budgetOpen
                            KeyAction.Graphs -> graphsOpen = !graphsOpen
                            KeyAction.NextOverlay -> overlay = Overlay.entries[(overlay.ordinal + 1) % Overlay.entries.size]
                            KeyAction.Speed1 -> { speed = 0; paused = false }
                            KeyAction.Speed2 -> { speed = 1; paused = false }
                            KeyAction.Speed3 -> { speed = 2; paused = false }
                            KeyAction.Pause -> paused = !paused
                            KeyAction.Undo -> undo()
                            KeyAction.Redo -> redo()
                            // Esc lets go of a drag, then closes the inspector, then puts the tool down.
                            KeyAction.Back -> back()
                            KeyAction.DevSeasonBack -> lookOverride = (look + Atlas.LOOKS - 1) % Atlas.LOOKS
                            KeyAction.DevSeasonNext -> lookOverride = (look + 1) % Atlas.LOOKS
                            KeyAction.DevHourBack -> hourShift -= 24f / Sky.STEPS
                            KeyAction.DevHourNext -> hourShift += 24f / Sky.STEPS
                            KeyAction.DevFire -> inspected?.let { (x, y) -> city.startFireAt(x, y); game.tick(0) }
                            KeyAction.DevWeather -> weatherOverride = if (weatherOverride + 1 >= DEV_WEATHER.size) -1 else weatherOverride + 1
                            KeyAction.DevGraphics -> settings.graphics = GraphicsLevel.entries[(settings.graphics.ordinal + 1) % GraphicsLevel.entries.size]
                            else -> {}
                        }
                    }
                },
        ) {
            val layout = screenLayout(maxWidth, maxHeight)
            viewSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            MapView(
                game, atlas, camera, look, shadowStep, sun, tint, weather, !paused, graphics, gestures, preview, costText, overlay,
                Modifier.fillMaxSize(),
            )

            val safe = WindowInsets.safeDrawing
            val gap = if (layout.compact) 6.dp else 10.dp
            val sideTools = layout.large || layout.shape == ScreenShape.Wide
            val compactTools = layout.compact || layout.short || layout.narrow
            val twoLines = layout.narrow && layout.shape == ScreenShape.Tall
            StatusStrip(
                game, onMenu, paused, { paused = !paused }, speed, { speed = (speed + 1) % SPEEDS.size },
                overlay != Overlay.None || choosingOverlay, { choosingOverlay = !choosingOverlay },
                { budgetOpen = true }, { graphsOpen = true },
                Sky.sun(sunStep, month).strength == 0f, layout.compact || layout.narrow,
                twoLines = twoLines, onUndo = ::undo, onRedo = ::redo,
                Modifier
                    // Beside the tools rather than above them when they run down the side.
                    .align(if (sideTools && !layout.large) Alignment.TopCenter else Alignment.TopStart)
                    .windowInsetsPadding(safe.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(gap),
            )
            message?.let { m ->
                MessageChip(
                    when {
                        m.arg != null -> stringResource(m.text, stringResource(m.arg))
                        m.name != null -> stringResource(m.text, m.name)
                        else -> stringResource(m.text)
                    },
                    if (m.x >= 0) ({ camera.centreOn(m.x, m.y) }) else null,
                    Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(safe.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(top = gap + MESSAGE_DROP.dp),
                )
            }
            if (sideTools) {
                ToolBar(
                    tool, ::pick, game.canUndo, game.canRedo, ::undo, ::redo, vertical = true, compact = compactTools,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .windowInsetsPadding(safe.only(WindowInsetsSides.Start))
                        .padding(gap),
                )
            }
            // Along the bottom: what's being inspected, the kinds of zone, and on an upright phone the tools.
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(safe.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(gap),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                inspected?.let { (x, y) -> InspectPanel(game, x, y, onClose = { inspected = null }) }
                if (tool == Tool.Zone) {
                    OptionPicker(ZoneKind.entries, zoneKind, { it.title }, { zoneColour(it.zone) }, { zoneKind = it }, compactTools)
                }
                if (tool == Tool.Power) {
                    OptionPicker(PowerKind.entries, powerKind, { it.title }, { null }, { powerKind = it }, compactTools)
                }
                if (tool == Tool.Services) {
                    OptionPicker(ServiceKind.entries, serviceKind, { it.title }, { null }, { serviceKind = it }, compactTools)
                }
                // The map views stay up while one is showing, so its name is on screen.
                if (choosingOverlay || overlay != Overlay.None) {
                    OptionPicker(
                        Overlay.entries, overlay, { it.title }, { if (it == Overlay.None) null else it.high.copy(alpha = 1f) },
                        { overlay = it; if (it == Overlay.None) choosingOverlay = false }, compactTools,
                    )
                }
                if (!sideTools) {
                    ToolBar(tool, ::pick, game.canUndo, game.canRedo, ::undo, ::redo, vertical = false, compact = compactTools, withHistory = !twoLines)
                }
            }
            if (layout.large) {
                CityPanel(
                    game,
                    Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(safe.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                        .padding(gap)
                        .width(PANEL_WIDTH.dp),
                )
            }
            if (budgetOpen) BudgetWindow(game) { budgetOpen = false }
            if (graphsOpen) GraphsWindow(game) { graphsOpen = false }
        }
    }
}

/** A tile on screen, in dp: the whole range, and where a new city starts. */
private const val MIN_TILE_DP = 3f
private const val MAX_TILE_DP = 96f
private const val START_TILE_DP = 24f

/** How fast the keys pan, in dp a second, and zoom, as a factor a second. */
private const val KEY_PAN_DP = 600f
private const val KEY_ZOOM = 1.5f

private const val PANEL_WIDTH = 240

/** A message for the top of the screen, with a building's name in it and a tile to go to if it's about a place. */
private data class Message(
    val text: StringResource,
    val arg: StringResource? = null,
    val x: Int = -1,
    val y: Int = -1,
    val name: String? = null,
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
private val SPEEDS = doubleArrayOf(0.5, 1.0, 4.0)
private const val MAX_DAYS_PER_FRAME = 4

/** How long a message stays, and how far below the top bar it sits. */
private const val MESSAGE_MS = 2500L
private const val MESSAGE_DROP = 56

/** How often the light changes: this many times an hour of the game's day. */
private const val LIGHT_STEPS_PER_HOUR = 8f
