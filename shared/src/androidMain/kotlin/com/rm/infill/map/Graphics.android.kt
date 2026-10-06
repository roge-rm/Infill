package com.rm.infill.map

import android.graphics.Bitmap
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual fun imageBitmapOf(pixels: IntArray, width: Int, height: Int): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

internal actual val bakeDispatcher: CoroutineDispatcher = Dispatchers.Default

internal actual fun newSurface(atlas: TileAtlas, level: Int, size: Int, roofs: Boolean): BakeSurface = PixelSurface(atlas, level, size, roofs)

internal actual val cacheScale: Int = 1

internal actual val bakeBudgetMs: Long? = null

/** Android only honours 200 dp of this on each edge, so it's a band that tall through the middle. */
internal actual fun Modifier.keepEdgeSwipesOff(): Modifier = composed {
    val band = with(LocalDensity.current) { 200.dp.toPx() }
    systemGestureExclusion { coordinates ->
        val h = coordinates.size.height.toFloat()
        Rect(0f, (h - band) / 2f, coordinates.size.width.toFloat(), (h + band) / 2f)
    }
}
