package com.rm.infill.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.rm.infill.sim.CityMap
import com.rm.infill.ui.theme.Infill
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The map. Drag or two fingers to pan, pinch or the mouse wheel to zoom.
 * [look] is the season, [sunStep] and [sun] where the sun is, and [tint] the
 * light of the hour, multiplied over everything.
 */
@Composable
fun MapView(
    map: CityMap,
    atlas: TileAtlas?,
    camera: Camera,
    look: Int,
    sunStep: Int,
    sun: Sun,
    tint: Color,
    graphics: Graphics,
    modifier: Modifier = Modifier,
) {
    val page = Infill.colors.page
    val renderer = remember(map, atlas, graphics) { atlas?.let { MapRenderer(map, it, graphics) } }
    var redraw by remember { mutableIntStateOf(0) }
    // Bakes what the last frame asked for, in its order. Where baking shares the
    // screen's thread it stops for a frame whenever it has used its share.
    LaunchedEffect(renderer) {
        val r = renderer ?: return@LaunchedEffect
        r.requests.collect {
            var started = TimeSource.Monotonic.markNow()
            while (true) {
                val request = r.nextRequest() ?: break
                val edits = r.edits
                val image = withContext(bakeDispatcher) { r.bake(request) }
                if (r.store(request, image, edits)) redraw++
                val budget = bakeBudgetMs
                if (budget != null && started.elapsedNow().inWholeMilliseconds >= budget) {
                    withFrameNanos { }
                    started = TimeSource.Monotonic.markNow()
                }
            }
        }
    }
    Canvas(
        modifier
            .pointerInput(camera) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    camera.panBy(pan.x, pan.y)
                    if (zoom != 1f) camera.zoomBy(zoom, centroid, Size(size.width.toFloat(), size.height.toFloat()))
                }
            }
            .pointerInput(camera) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll) continue
                        val change = event.changes.first()
                        val notches = change.scrollDelta.y
                        if (notches != 0f) {
                            camera.zoomBy(WHEEL_STEP.pow(-notches), change.position, Size(size.width.toFloat(), size.height.toFloat()))
                            change.consume()
                        }
                    }
                }
            },
    ) {
        redraw // Drawn again when a chunk has been baked.
        drawRect(page)
        if (renderer == null) return@Canvas
        val level = renderer.levelFor(camera.tilePx)
        val chunk = MapRenderer.CHUNK
        val topLeft = camera.screenToTile(Offset.Zero, size)
        val bottomRight = camera.screenToTile(Offset(size.width, size.height), size)
        // A row further down, since sprites there reach up onto the screen.
        val cx0 = floor(topLeft.x / chunk).toInt()
        val cy0 = floor(topLeft.y / chunk).toInt()
        val cx1 = floor(bottomRight.x / chunk).toInt()
        val cy1 = floor((bottomRight.y + 1) / chunk).toInt()
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
        if (tint != Color.White) drawRect(tint, blendMode = BlendMode.Multiply)
    }
}

/** Each notch of the wheel zooms by this much. */
private const val WHEEL_STEP = 1.15f
