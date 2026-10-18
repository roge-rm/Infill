package com.rm.infill.map

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual fun imageBitmapOf(pixels: IntArray, width: Int, height: Int): ImageBitmap =
    BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
        setRGB(0, 0, width, height, pixels, 0, width)
    }.toComposeImageBitmap()

internal actual val bakeDispatcher: CoroutineDispatcher = Dispatchers.Default
internal actual fun newSurface(atlas: TileAtlas, level: Int, size: Int, roofs: Boolean): BakeSurface = PixelSurface(atlas, level, size, roofs)
internal actual val cacheScale: Int = 2
internal actual val bakeBudgetMs: Long? = null
internal actual fun Modifier.keepEdgeSwipesOff(): Modifier = this
