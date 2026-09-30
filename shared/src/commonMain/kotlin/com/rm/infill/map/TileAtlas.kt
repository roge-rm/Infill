package com.rm.infill.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import com.rm.infill.res.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/** The tile atlas at 32, 16 and 8 px a tile, in that order. */
class TileAtlas(val levels: List<ImageBitmap>) {
    /** The same as ARGB pixels, for baking by hand. Read the first time they're wanted. */
    val pixels: List<IntArray> by lazy {
        levels.map { image -> IntArray(image.width * image.height).also { image.readPixels(it) } }
    }
}

/** Loads the atlases. Null until they're ready. */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun rememberTileAtlas(): TileAtlas? = produceState<TileAtlas?>(null) {
    value = TileAtlas(listOf(32, 16, 8).map { Res.readBytes("files/atlas_$it.png").decodeToImageBitmap() })
}.value
