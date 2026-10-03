package com.rm.infill.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import com.rm.infill.res.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * The tile atlas at 32, 16 and 8 px a tile, a sheet for each look. The sheets
 * are kept packed, and a look is only unpacked to pixels when something's
 * drawn in it, so a phone holds the season it's showing and not all six.
 */
@OptIn(ExperimentalResourceApi::class)
class TileAtlas(
    /** The packed sheets: [level][look]. */
    private val packed: List<List<ByteArray>>,
    /** The 32 px summer sheet, for the pictures in the trays. */
    val icons: ImageBitmap,
) {
    /** How many sizes there are, biggest first. */
    val levels: Int get() = packed.size

    /** How wide a sheet is at [level]. */
    fun width(level: Int): Int = Atlas.WIDTH shr level

    private val slots = Array(packed.size) { level -> Array(Atlas.LOOKS) { look -> slot(level, look) } }

    /** When each look was last asked for, to let go of the one longest unused. */
    private val used = Array(packed.size) { LongArray(Atlas.LOOKS) }
    private var clock = 0L

    private fun slot(level: Int, look: Int): Lazy<IntArray> = lazy {
        val image = packed[level][look].decodeToImageBitmap()
        IntArray(image.width * image.height).also {
            image.readPixels(it)
            letGo(level, look)
        }
    }

    /**
     * The ARGB pixels of [look] at [level], for baking by hand. A surface
     * that's still drawing with a look let go keeps its own hold on it.
     */
    fun pixels(level: Int, look: Int): IntArray {
        used[level][look] = ++clock
        return slots[level][look].value
    }

    /** Keeps at most [KEPT] looks unpacked at [level], letting go of the longest unused besides [look]. */
    private fun letGo(level: Int, look: Int) {
        val row = slots[level]
        val open = (0 until Atlas.LOOKS).filter { it != look && row[it].isInitialized() }
        if (open.size < KEPT) return
        val oldest = open.minBy { used[level][it] }
        row[oldest] = slot(level, oldest)
    }

    private companion object {
        /** The season, the bare look for trees killed by grime, and the next season while it comes in. */
        const val KEPT = 3
    }
}

/** Loads the atlases. Null until they're ready. */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun rememberTileAtlas(): TileAtlas? = produceState<TileAtlas?>(null) {
    val names = listOf("spring", "summer", "autumn", "bare", "snow", "dry")
    val packed = listOf(32, 16, 8).map { size -> names.map { Res.readBytes("files/atlas_${size}_$it.png") } }
    value = withContext(Dispatchers.Default) { TileAtlas(packed, packed[0][Atlas.SUMMER].decodeToImageBitmap()) }
}.value
