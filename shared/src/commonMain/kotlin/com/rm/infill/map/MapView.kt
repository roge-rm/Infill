package com.rm.infill.map

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer

import com.rm.infill.sim.Concern
import com.rm.infill.res.Res
import com.rm.infill.res.district_mood_map
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import com.rm.infill.platform.platform
import com.rm.infill.sim.Specs
import com.rm.infill.sim.Balance
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.ln
import kotlinx.coroutines.delay
import com.rm.infill.sound.TownSound
import com.rm.infill.audio.AudioEngine
import com.rm.infill.sim.BuildingType
import com.rm.infill.sim.Zone
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
    /** How fast the game's going, 1 at normal speed, for what travels across the map. */
    pace: Float = 1f,
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
    /** The hour of the game's day, for the town's sound. */
    hour: Float = 12f,
    /** The keyboard's cursor, if keys are being used to play. */
    cursor: Pair<Int, Int>? = null,
    /** Kept up to date with the map as drawn, for a picture of the town. */
    photo: MapPhoto? = null,
    modifier: Modifier = Modifier,
) {
    val photoLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    photo?.layer = photoLayer
    val map = game.city.map
    val page = Infill.colors.page
    val cursorColour = Infill.colors.accent
    val renderer = remember(map, atlas, graphics) { atlas?.let { MapRenderer(map, it, graphics) } }
    val measurer = rememberTextMeasurer()
    var redraw by remember { mutableIntStateOf(0) }
    var hoverX by remember { mutableIntStateOf(-1) }
    var hoverY by remember { mutableIntStateOf(-1) }
    val g by rememberUpdatedState(gestures)
    // Made away from the screen's thread, so loading a town doesn't wait on them; no clouds until then.
    val clouds by produceState<CloudTextures?>(null) { value = withContext(Dispatchers.Default) { CloudTextures.make() } }

    // Rain, snow, clouds and traffic move while the game runs.
    // Two clocks: real seconds for what falls and flickers (rain, snow, fires),
    // and seconds at the game's speed for what travels (clouds, traffic, trains,
    // ships and planes), so those go faster when the game does.
    var weatherTime by remember { mutableFloatStateOf(0f) }
    var travelTime by remember { mutableFloatStateOf(0f) }
    val services = remember(game.city) { ServiceTrips() }
    val paceNow by rememberUpdatedState(pace)
    val fires = game.city.burningNow > 0
    // How hard each building with stacks is going, and where people sleep rough, looked over every couple of seconds while the town's free.
    var plumes by remember(game.city) { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var rough by remember(game.city) { mutableStateOf(IntArray(0)) }
    LaunchedEffect(game.city, graphics.plumes) {
        while (true) {
            game.tryLocked {
                plumes = if (graphics.plumes > 0) plumesOf(game.city) else emptyMap()
                rough = roughSpots(game.city)
            }
            delay(SURVEY_EVERY_MS)
        }
    }
    val traffic = graphics.vehicles > 0 && game.city.stats.population > 0
    val trains = graphics.trains > 0 && game.city.trainRoutes.isNotEmpty()
    val ships = graphics.trains > 0 && game.city.shipRoutes.isNotEmpty()
    val ferries = graphics.trains > 0 && game.city.ferryRoutes.isNotEmpty()
    val planes = graphics.trains > 0 && game.city.airTier > 0
    val animate = running && (fires || plumes.isNotEmpty() || traffic || trains || ships || ferries || planes || weather.moving && (graphics.particles > 0f || graphics.cloudShadows))
    // Worked out through the town's traffic, which the sim changes as it goes: only when the town's free,
    // keeping the last lot meanwhile.
    val lastFocus = remember { arrayOfNulls<IntArray>(1) }
    val focusData = remember(overlay, focus, game.revision) {
        when {
            focus < 0 -> null
            overlay == Overlay.Trips -> game.tryLocked { game.city.tripsThrough(focus) } ?: lastFocus[0]
            overlay == Overlay.Reach -> game.tryLocked { game.city.travelTimes(focus) } ?: lastFocus[0]
            else -> null
        }.also { lastFocus[0] = it }
    }
    // Each district's name, mood and what its people mind most, for the Mood view; worked out under the town's lock.
    val concernNames = Concern.entries.map { stringResource(com.rm.infill.ui.concernName(it)).lowercase() }
    val moodTemplate = stringResource(Res.string.district_mood_map)
    val lastMoods = remember { arrayOfNulls<List<Triple<Int, String, Int>>>(1) }
    val moodLabels = remember(overlay, game.revision) {
        if (overlay != Overlay.Mood) emptyList()
        else (game.tryLocked {
            game.city.districts.mapNotNull { d ->
                val f = game.city.districtFigures(d.id)
                val worst = f.worst ?: return@mapNotNull null
                Triple(d.id, moodTemplate.replace("%1\$s", d.name).replace("%2\$d", f.mood.toString()).replace("%3\$s", concernNames[worst.ordinal]), f.mood)
            }
        } ?: lastMoods[0] ?: emptyList()).also { lastMoods[0] = it }
    }
    val overlayImage = remember(overlay, focusData, game.revision) {
        overlayImage(overlay, map, { game.city.building(map.building[it])?.people }, { game.city.wearAt(it) }, { game.city.building(map.building[it])?.uncollected == true }, { i ->
            game.city.building(map.building[i])?.takeIf { it.kind >= 0 || it.type.zone == Zone.FARMLAND || it.type.zone == Zone.COMMERCIAL && !it.type.office ||
                it.type.root == BuildingType.COAL_PLANT || it.type.root == BuildingType.OIL_PLANT }?.local ?: -1
        }, { game.city.junctionWait(it) }, focusData, { game.city.lineLoad(it) }, visitorAt = { i ->
            val b = game.city.building(map.building[i])
            when {
                b == null -> -1
                b.type.hotel -> if (b.room == 0) 0 else maxOf(30, b.served * 255 / b.room)
                b.type == BuildingType.PARK || game.city.isHeritage(b) || (Specs.of(b.type)?.draw ?: 0.0) > 0 -> 255
                b.type.station || b.type.port -> 200
                else -> -1
            }
        }, leisureAt = { game.city.leisureAt(it) }, growthAt = { i ->
            // Under the town's lock, as it looks at the buildings the sim may be changing.
            // A lot built as far as it's let isn't held back, so it shows nothing.
            if (map.zone[i] == Zone.NONE) -1
            else game.tryLocked {
                when (game.city.whyNotAt(i)) {
                    null -> 0
                    com.rm.infill.sim.AdviceKind.ZONE_MORE -> -1
                    else -> 255
                }
            } ?: -1
        }, moodAt = { i ->
            // Under the town's lock, as it looks at the buildings the sim may be changing.
            if (map.building[i] == 0) -1 else game.tryLocked { game.city.moodAt(i) } ?: -1
        }, heritageAt = { i ->
            val b = game.city.building(map.building[i])
            when {
                b == null || b.type.zone == Zone.NONE -> -1
                game.city.isHeritage(b) -> 255
                b.type.heritage -> 60
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
            val seconds = (now - last) / 1e9f
            weatherTime += seconds
            travelTime += seconds * paceNow
            last = now
        }
    }

    // The town's sound, from where the camera is, a few times a second. What
    // makes sound is looked over again when the town changes, at most every
    // couple of seconds; the trains are placed where they're drawn.
    val viewBox = remember { FloatArray(2) }
    val hourNow by rememberUpdatedState(hour)
    val dpPerPx = 1f / LocalDensity.current.density
    LaunchedEffect(game.city) {
        val city = game.city
        val sound = TownSound(city)
        val frame = TownSound.Frame()
        var surveyed = -1
        var lastSurvey = TimeSource.Monotonic.markNow()
        var last = TimeSource.Monotonic.markNow()
        val seeds = kotlin.random.Random(city.seed)
        try {
            while (true) {
                delay(SOUND_EVERY_MS)
                val dt = last.elapsedNow().inWholeMilliseconds / 1000f
                last = TimeSource.Monotonic.markNow()
                if (game.revision != surveyed && (surveyed < 0 || lastSurvey.elapsedNow().inWholeMilliseconds >= SURVEY_EVERY_MS)) {
                    surveyed = game.revision
                    lastSurvey = TimeSource.Monotonic.markNow()
                    // Over the whole town, so only when the sim's not working on it; it's tried again shortly.
                    if (game.tryLocked { sound.survey() } == null) surveyed = -2
                }
                val view = Size(viewBox[0], viewBox[1])
                if (view.width <= 0f) continue
                val topLeft = camera.screenToTile(Offset.Zero, view)
                val bottomRight = camera.screenToTile(Offset(view.width, view.height), view)
                val tileDp = camera.tilePx * dpPerPx
                val closeness = (ln(tileDp / FAR_TILE_DP) / ln(NEAR_TILE_DP / FAR_TILE_DP)).coerceIn(0f, 1f)
                val listener = TownSound.Listener(
                    camera.centreX, camera.centreY, (bottomRight.x - topLeft.x) / 2f, (bottomRight.y - topLeft.y) / 2f, closeness,
                )
                val kind = when {
                    city.year < Balance.STEAM_TRAINS_UNTIL -> 0f
                    city.year < ELECTRIC_TRAINS_FROM -> 1f
                    else -> 2f
                }
                val trains = trainSounds(city.trainRoutes, map.width, travelTime, graphics.trains, kind)
                sound.frame(listener, hourNow, dt, trains, frame)
                AudioEngine.scene(frame.count, frame.keys, frame.recipes, frame.flags, frame.params)
                for (shot in frame.shots) AudioEngine.event(shot.recipe, shot.params, shot.delay, seeds.nextInt())
            }
        } finally {
            AudioEngine.scene(0, IntArray(0), IntArray(0), IntArray(0), FloatArray(0))
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
        modifier.keepEdgeSwipesOff().then(
            if (photo == null) Modifier else Modifier.drawWithContent {
                photoLayer.record { this@drawWithContent.drawContent() }
                drawLayer(photoLayer)
            },
        ).pointerInput(camera) {
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
                            // In notches, however the platform counts them; a hard flick zooms a few steps, never the whole way.
                            val notches = (change.scrollDelta.y / platform.scrollPerNotch).coerceIn(-WHEEL_MOST, WHEEL_MOST)
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
        viewBox[0] = size.width
        viewBox[1] = size.height
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
        drawNeighbours(game.city.neighbours, map, camera, atlas, look)
        drawFloods(map, camera)
        drawWorks(map, camera)
        if (planes) drawPlanes(game.city.airportsShown(), camera, travelTime, jets = game.city.year >= Balance.JET_YEAR)
        val raised = if (ships) drawShips(game.city.shipRoutes, map, camera, travelTime, graphics.trains, graphics.smoke && game.city.year < Balance.STEAM_UNTIL) else emptySet()
        // Road traffic waits for a train at a crossing, and for a bridge that's open for a ship.
        val stopped = (if (trains) drawTrains(game.city.trainRoutes, map, camera, travelTime, graphics.trains, graphics.smoke, steam = game.city.year < Balance.STEAM_TRAINS_UNTIL) else emptySet()) + raised
        if (ferries) drawFerries(game.city.ferryRoutes, map, camera, travelTime)
        if (traffic) {
            drawPeople(game.city, map, camera, game.city.year, travelTime)
            drawVehicles(map, camera, game.city.year, travelTime, graphics.vehicles, stopped)
            drawTransit(map, camera, travelTime, game.city.lineStates())
            services.update(game.city.callouts, map, travelTime, game.city.year)
            with(services) { draw(map, camera, game.city.year, weatherTime) }
        }
        drawRough(rough, map.width, camera)
        drawPlumes(renderer, map, camera, plumes, weather, weatherTime, graphics.plumes)
        if (fires) drawFires(map, camera, weather, weatherTime)
        drawWeather(weather, camera, clouds, travelTime, weatherTime, sun.strength, graphics)
        // Modulate rather than Multiply: the same for an opaque tint, and Android before 10 has only this one.
        if (tint != Color.White) drawRect(tint, blendMode = BlendMode.Modulate)
        if (underground) drawUnderground(map, camera, game.city.monthNow)
        overlayImage?.let { drawOverlay(it, map, camera, overlay) }
        if (districts.isNotEmpty()) drawDistricts(districts, map, camera, measurer)
        else if (overlay == Overlay.Mood) drawDistrictMoods(moodLabels, map, camera, measurer)
        if (lines.isNotEmpty()) drawLines(lines, map, camera)
        if (preview != null) drawPreview(preview, map, camera, measurer, costText)
        else if (gestures.toolActive && hoverX >= 0) drawHover(hoverX, hoverY, camera)
        cursor?.let { (x, y) -> drawCursor(x, y, camera, cursorColour) }
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
private const val WHEEL_MOST = 3f

/** How often the town's sound is worked out, and how often what makes it is looked over again at most. */
private const val SOUND_EVERY_MS = 100L
private const val SURVEY_EVERY_MS = 2000L

/** Tile sizes, in dp, that count as far out and right in for the town's sound. */
private const val FAR_TILE_DP = 6f
private const val NEAR_TILE_DP = 64f

/** From when trains are electric, after diesel. */
private const val ELECTRIC_TRAINS_FROM = 1995
