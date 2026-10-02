package com.rm.infill.map

import com.rm.infill.sim.Balance
import com.rm.infill.sim.BuildingType
import com.rm.infill.sim.Zone
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.rm.infill.GameState
import com.rm.infill.ui.Preview
import com.rm.infill.ui.theme.Infill
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/** What the map does with a finger or the mouse, besides moving the view. Tiles are given as x, y. */
class MapGestures(
    /** A tool that works by dragging is picked, so one finger uses it and two move the view. */
    val toolActive: Boolean,
    val onToolDown: (Int, Int) -> Unit,
    val onToolMove: (Int, Int) -> Unit,
    val onToolUp: () -> Unit,
    val onToolCancel: () -> Unit,
    val onTap: (Int, Int) -> Unit,
)

/**
 * The map. [look] is the season, [sunStep] and [sun] where the sun is, and
 * [tint] the light of the hour, multiplied over everything. [preview] is drawn
 * over it while a tool is being dragged.
 */
@Composable
fun MapView(
    game: GameState,
    atlas: TileAtlas?,
    camera: Camera,
    look: Int,
    sunStep: Int,
    sun: Sun,
    tint: Color,
    weather: WeatherLook,
    running: Boolean,
    graphics: Graphics,
    gestures: MapGestures,
    preview: Preview?,
    costText: String,
    overlay: Overlay,
    underground: Boolean = false,
    /** The map tile being inspected, for the views that follow one road; -1 if none. */
    focus: Int = -1,
    /** Districts to draw over the map, by id and name, while they're being painted. */
    districts: List<Pair<Int, String>> = emptyList(),
    /** Transit lines to draw over the map, by id and the tiles each runs over; 0 for one being planned. */
    lines: List<Pair<Int, IntArray>> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val map = game.city.map
    val page = Infill.colors.page
    val renderer = remember(map, atlas, graphics) { atlas?.let { MapRenderer(map, it, graphics) } }
    val measurer = rememberTextMeasurer()
    var redraw by remember { mutableIntStateOf(0) }
    var hoverX by remember { mutableIntStateOf(-1) }
    var hoverY by remember { mutableIntStateOf(-1) }
    val g by rememberUpdatedState(gestures)
    val clouds = remember { CloudTextures.make() }

    // Rain, snow, clouds and traffic move while the game runs.
    var weatherTime by remember { mutableFloatStateOf(0f) }
    val fires = game.city.burningNow > 0
    val traffic = graphics.vehicles > 0 && game.city.stats.population > 0
    val trains = graphics.trains > 0 && game.city.trainRoutes.isNotEmpty()
    val ships = graphics.trains > 0 && game.city.shipRoutes.isNotEmpty()
    val animate = running && (fires || traffic || trains || ships || weather.moving && (graphics.particles > 0f || graphics.cloudShadows))
    val focusData = remember(overlay, focus, game.revision) {
        when {
            focus < 0 -> null
            overlay == Overlay.Trips -> game.city.tripsThrough(focus)
            overlay == Overlay.Reach -> game.city.travelTimes(focus)
            else -> null
        }
    }
    val overlayImage = remember(overlay, focusData, game.revision) {
        overlayImage(overlay, map, { game.city.building(map.building[it])?.people }, { game.city.wearAt(it) }, { game.city.building(map.building[it])?.uncollected == true }, { i ->
            game.city.building(map.building[i])?.takeIf { it.kind >= 0 || it.type.zone == Zone.FARMLAND || it.type.zone == Zone.COMMERCIAL && !it.type.office ||
                it.type == BuildingType.COAL_PLANT || it.type == BuildingType.OIL_PLANT }?.local ?: -1
        }, { game.city.junctionWait(it) }, focusData, { game.city.lineLoad(it) }, visitorAt = { i ->
            val b = game.city.building(map.building[i])
            when {
                b == null -> -1
                b.type == BuildingType.HOTEL -> if (b.room == 0) 0 else maxOf(30, b.served * 255 / b.room)
                b.type == BuildingType.PARK || game.city.isHeritage(b) -> 255
                b.type.station || b.type.port -> 200
                else -> -1
            }
        }) {
            game.city.tramRiders(it) + game.city.busRiders(it) + game.city.trolleyRiders(it) + game.city.subwayRiders(it)
        }
    }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            weatherTime += (now - last) / 1e9f
            last = now
        }
    }

    // Bakes what the last frame asked for, in its order. Where baking shares the
    // screen's thread it stops for a frame whenever it has used its share.
    LaunchedEffect(renderer) {
        val r = renderer ?: return@LaunchedEffect
        r.requests.collect {
            var started = TimeSource.Monotonic.markNow()
            while (true) {
                val request = r.nextRequest() ?: break
                val image = withContext(bakeDispatcher) { r.bake(request) }
                if (r.store(request, image)) redraw++
                val budget = bakeBudgetMs
                if (budget != null && started.elapsedNow().inWholeMilliseconds >= budget) {
                    withFrameNanos { }
                    started = TimeSource.Monotonic.markNow()
                }
            }
        }
    }

    Canvas(
        modifier.keepEdgeSwipesOff().pointerInput(camera) {
            fun view() = Size(size.width.toFloat(), size.height.toFloat())
            fun tileAt(p: Offset): Pair<Int, Int> {
                val t = camera.screenToTile(p, view())
                return floor(t.x).toInt().coerceIn(0, map.width - 1) to floor(t.y).toInt().coerceIn(0, map.height - 1)
            }
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: continue
                    when (event.type) {
                        PointerEventType.Scroll -> {
                            val notches = change.scrollDelta.y
                            if (notches != 0f) camera.zoomBy(WHEEL_STEP.pow(-notches), change.position, view())
                            change.consume()
                        }
                        PointerEventType.Move -> if (change.type == PointerType.Mouse && !change.pressed) {
                            val (x, y) = tileAt(change.position)
                            hoverX = x
                            hoverY = y
                        }
                        PointerEventType.Exit -> hoverX = -1
                        PointerEventType.Press -> gesture(event, camera, ::view, ::tileAt, { g })
                        else -> {}
                    }
                }
            }
        },
    ) {
        redraw // Drawn again when a chunk the screen was waiting on has been baked.
        game.revision // and when the city changes.
        drawRect(page)
        if (renderer == null) return@Canvas
        game.takeChanges { i -> renderer.changed(i % map.width, i / map.width) }
        val level = renderer.levelFor(camera.tilePx)
        val chunk = MapRenderer.CHUNK
        val topLeft = camera.screenToTile(Offset.Zero, size)
        val bottomRight = camera.screenToTile(Offset(size.width, size.height), size)
        // Rows further down too, since sprites there reach up onto the screen.
        val cx0 = floor(topLeft.x / chunk).toInt()
        val cy0 = floor(topLeft.y / chunk).toInt()
        val cx1 = floor(bottomRight.x / chunk).toInt()
        val cy1 = floor((bottomRight.y + MapRenderer.SPRITE_ROWS) / chunk).toInt()
        renderer.plan(level, cx0, cy0, cx1, cy1, look, sunStep, sun, camera.tilePx)
        if (!renderer.ready) return@Canvas
        for (cy in max(0, cy0)..min(cy1, (map.height - 1) / chunk)) {
            for (cx in max(0, cx0)..min(cx1, (map.width - 1) / chunk)) {
                val image = renderer.image(cx, cy, level) ?: continue
                // Whole pixels from each edge, so neighbouring chunks meet without a seam.
                val a = camera.tileToScreen((cx * chunk).toFloat(), (cy * chunk).toFloat(), size)
                val b = camera.tileToScreen(((cx + 1) * chunk).toFloat(), ((cy + 1) * chunk).toFloat(), size)
                val left = a.x.roundToInt()
                val top = a.y.roundToInt()
                drawImage(
                    image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(left, top),
                    dstSize = IntSize(b.x.roundToInt() - left, b.y.roundToInt() - top),
                    filterQuality = FilterQuality.None,
                )
            }
        }
        drawFloods(map, camera)
        drawWorks(map, camera)
        val raised = if (ships) drawShips(game.city.shipRoutes, map, camera, weatherTime, graphics.trains, graphics.smoke && game.city.year < Balance.STEAM_UNTIL) else emptySet()
        // Road traffic waits for a train at a crossing, and for a bridge that's open for a ship.
        val stopped = (if (trains) drawTrains(game.city.trainRoutes, map, camera, weatherTime, graphics.trains, graphics.smoke) else emptySet()) + raised
        if (traffic) {
            drawVehicles(map, camera, game.city.year, weatherTime, graphics.vehicles, stopped)
            drawTransit(map, camera, weatherTime, game.city.lineStates())
        }
        if (fires) drawFires(map, camera, weather, weatherTime)
        drawWeather(weather, camera, clouds, weatherTime, sun.strength, graphics)
        // Modulate rather than Multiply: the same for an opaque tint, and Android before 10 has only this one.
        if (tint != Color.White) drawRect(tint, blendMode = BlendMode.Modulate)
        if (underground) drawUnderground(map, camera, game.city.monthNow)
        overlayImage?.let { drawOverlay(it, map, camera, overlay) }
        if (districts.isNotEmpty()) drawDistricts(districts, map, camera, measurer)
        if (lines.isNotEmpty()) drawLines(lines, map, camera)
        if (preview != null) drawPreview(preview, map, camera, measurer, costText)
        else if (gestures.toolActive && hoverX >= 0) drawHover(hoverX, hoverY, camera)
    }
}

/**
 * One press through to the last finger or button coming up. With a tool picked,
 * one finger or the left button uses it and a second finger turns it into a
 * pan and pinch. Otherwise, and with the right or middle button, it moves the
 * view, and a press that doesn't move is a tap.
 */
private suspend fun AwaitPointerEventScope.gesture(
    first: PointerEvent,
    camera: Camera,
    view: () -> Size,
    tileAt: (Offset) -> Pair<Int, Int>,
    gestures: () -> MapGestures,
) {
    val start = first.changes.first()
    val panButton = first.buttons.isSecondaryPressed || first.buttons.isTertiaryPressed
    var tool = gestures().toolActive && !panButton
    var moved = false
    if (tool) tileAt(start.position).let { (x, y) -> gestures().onToolDown(x, y) }
    start.consume()
    while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.filter { it.pressed }
        if (pressed.isEmpty()) break
        if (pressed.size >= 2 && tool) {
            gestures().onToolCancel()
            tool = false
            moved = true
        }
        val lead = pressed.first()
        if ((lead.position - start.position).getDistance() > viewConfiguration.touchSlop) moved = true
        if (tool) {
            tileAt(lead.position).let { (x, y) -> gestures().onToolMove(x, y) }
        } else {
            camera.panBy(event.calculatePan().x, event.calculatePan().y)
            val zoom = event.calculateZoom()
            val centroid = event.calculateCentroid()
            if (zoom != 1f && centroid.isSpecified) camera.zoomBy(zoom, centroid, view())
        }
        event.changes.forEach { it.consume() }
    }
    if (tool) gestures().onToolUp()
    else if (!moved && !panButton) tileAt(start.position).let { (x, y) -> gestures().onTap(x, y) }
}

/** Each notch of the wheel zooms by this much. */
private const val WHEEL_STEP = 1.15f
