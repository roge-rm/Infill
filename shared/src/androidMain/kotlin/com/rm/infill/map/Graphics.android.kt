package com.rm.infill.map

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual fun imageBitmapOf(pixels: IntArray, width: Int, height: Int): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

internal actual val bakeDispatcher: CoroutineDispatcher = Dispatchers.Default

internal actual fun newSurface(atlas: TileAtlas, level: Int, size: Int): BakeSurface = PixelSurface(atlas, level, size)

internal actual val cacheScale: Int = 1

internal actual val bakeBudgetMs: Long? = null
