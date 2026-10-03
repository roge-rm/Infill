package com.rm.infill.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import com.rm.infill.res.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/** The tile atlas at 32, 16 and 8 px a tile, in that order. */
class TileAtlas(val levels: List<ImageBitmap>) {
    /**
     * The same as ARGB pixels, for baking by hand. Each size is read the first
     * time it's wanted, so the sizes the zoom never uses take no memory.
     */
    val pixels: List<IntArray> = levels.map { image -> LazyPixels(image) }.let { lazies ->
        object : AbstractList<IntArray>() {
            override val size = lazies.size
            override fun get(index: Int): IntArray = lazies[index].value
        }
    }

    private class LazyPixels(private val image: ImageBitmap) {
        val value: IntArray by lazy { IntArray(image.width * image.height).also { image.readPixels(it) } }
    }
}

/** Loads the atlases. Null until they're ready. */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun rememberTileAtlas(): TileAtlas? = produceState<TileAtlas?>(null) {
    value = TileAtlas(listOf(32, 16, 8).map { Res.readBytes("files/atlas_$it.png").decodeToImageBitmap() })
}.value
