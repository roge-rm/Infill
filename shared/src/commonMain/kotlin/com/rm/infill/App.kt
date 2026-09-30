package com.rm.infill

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
import com.rm.infill.map.Atlas
import com.rm.infill.map.Camera
import com.rm.infill.map.Graphics
import com.rm.infill.map.GraphicsLevel
import com.rm.infill.map.MapView
import com.rm.infill.map.Seasons
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

/** The whole game, the same on every platform. */
@Composable
fun App() {
    InfillTheme {
        val city = remember { City(seed = 1900) }
        val density = LocalDensity.current.density
        val camera = remember(density) {
            Camera(city.map.width, city.map.height, MIN_TILE_DP * density, MAX_TILE_DP * density, START_TILE_DP * density)
        }
        var tool by remember { mutableStateOf(Tool.Inspect) }
        var paused by remember { mutableStateOf(true) }
        val keys = remember { KeyInput() }
        val focus = remember { FocusRequester() }
        var viewSize by remember { mutableStateOf(Size.Zero) }
        val atlas = rememberTileAtlas()

        // The day runs while the game does. Shadows move a step at a time and the light a little more often.
        var hour by remember { mutableFloatStateOf(START_HOUR) }
        LaunchedEffect(paused) {
            while (!paused) {
                delay(DAY_TICK_MS)
                hour = (hour + 24f * DAY_TICK_MS / 1000f / DAY_SECONDS) % 24f
            }
        }
        var lookOverride by remember { mutableIntStateOf(-1) }
        val look = if (lookOverride >= 0) lookOverride else Seasons.lookFor(city.month)
        val month = if (lookOverride >= 0) Seasons.monthOf(lookOverride) else city.month
        val sunStep by remember { derivedStateOf { Sky.step(hour) } }
        val lightStep by remember { derivedStateOf { (hour * LIGHT_STEPS_PER_HOUR).toInt() } }
        var graphicsLevel by remember { mutableStateOf(GraphicsLevel.High) }
        val graphics = remember(graphicsLevel) { Graphics(graphicsLevel) }
        val shadowStep = graphics.sunStep(sunStep)
        val sun = remember(shadowStep, month) { Sky.sun(shadowStep, month) }
        val tint = remember(lightStep, month) { Sky.tint(lightStep / LIGHT_STEPS_PER_HOUR, month) }

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
                            KeyAction.ToolInspect -> tool = Tool.Inspect
                            KeyAction.ToolBulldoze -> tool = Tool.Bulldoze
                            KeyAction.ToolRoad -> tool = Tool.Road
                            KeyAction.ToolZone -> tool = Tool.Zone
                            KeyAction.Pause -> paused = !paused
                            KeyAction.Back -> tool = Tool.Inspect
                            KeyAction.DevSeasonBack -> lookOverride = (look + Atlas.LOOKS - 1) % Atlas.LOOKS
                            KeyAction.DevSeasonNext -> lookOverride = (look + 1) % Atlas.LOOKS
                            KeyAction.DevHourBack -> hour = (hour + 24f - 24f / Sky.STEPS) % 24f
                            KeyAction.DevHourNext -> hour = (hour + 24f / Sky.STEPS) % 24f
                            KeyAction.DevGraphics -> graphicsLevel = GraphicsLevel.entries[(graphicsLevel.ordinal + 1) % GraphicsLevel.entries.size]
                            else -> {}
                        }
                    }
                },
        ) {
            val layout = screenLayout(maxWidth, maxHeight)
            viewSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            MapView(city.map, atlas, camera, look, shadowStep, sun, tint, graphics, Modifier.fillMaxSize())

            val safe = WindowInsets.safeDrawing
            val gap = if (layout.compact) 6.dp else 10.dp
            val sideTools = layout.large || layout.shape == ScreenShape.Wide
            StatusStrip(
                city, paused, { paused = !paused }, layout.compact,
                Modifier
                    // Beside the tools rather than above them when they run down the side.
                    .align(if (sideTools && !layout.large) Alignment.TopCenter else Alignment.TopStart)
                    .windowInsetsPadding(safe.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(gap),
            )
            ToolBar(
                tool, { tool = it }, vertical = sideTools, compact = layout.compact || layout.short,
                modifier = if (sideTools) {
                    Modifier
                        .align(Alignment.CenterStart)
                        .windowInsetsPadding(safe.only(WindowInsetsSides.Start))
                        .padding(gap)
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(safe.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        .padding(gap)
                },
            )
            if (layout.large) {
                CityPanel(
                    city,
                    Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(safe.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                        .padding(gap)
                        .width(PANEL_WIDTH.dp),
                )
            }
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

/** A day lasts this many seconds of play, starting mid morning. */
private const val DAY_SECONDS = 360f
private const val START_HOUR = 9.5f
private const val DAY_TICK_MS = 250L
private const val LIGHT_STEPS_PER_HOUR = 8f
