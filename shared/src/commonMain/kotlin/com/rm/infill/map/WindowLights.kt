package com.rm.infill.map

import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Where the buildings' windows are, from the lights sheets (files/atlas_<size>_lights.png,
 * written by tools/gen_tiles.py), and which of them are lit at each stage of the night.
 * One sheet serves every look, since the sprites sit in the same places.
 */
@OptIn(ExperimentalResourceApi::class)
class WindowLights(
    /** The packed sheets, biggest first. */
    private val packed: List<ByteArray>,
) {
    /** For each level, each look-free sprite's glass as pairs: column plus row times [ROW], then pane times 256 plus how much is glass. */
    private val levels = Array(packed.size) { level -> lazy { unpack(level) } }

    /** The glass in look-free [sprite] at [level], or null if it has none. Safe on the bake thread. */
    fun panes(level: Int, sprite: Int): IntArray? = levels[level].value[sprite]

    private fun unpack(level: Int): Array<IntArray?> {
        val image: ImageBitmap = packed[level].decodeToImageBitmap()
        val width = image.width
        val pixels = IntArray(width * image.height)
        image.readPixels(pixels)
        val found = ArrayList<Int>()
        return Array(Atlas.PER_LOOK) { sprite ->
            val r = sprite * 5
            val sx = Atlas.rects[r] shr level
            val sy = Atlas.rects[r + 1] shr level
            val w = Atlas.rects[r + 2] shr level
            val h = Atlas.rects[r + 3] shr level
            found.clear()
            for (row in 0 until h) for (col in 0 until w) {
                val p = pixels[(sy + row) * width + sx + col]
                if (p ushr 24 == 0) continue
                found += col + row * ROW
                found += (((p shr 16) and 0xff) shl 8) or ((p shr 8) and 0xff)
            }
            if (found.isEmpty()) null else found.toIntArray()
        }
    }

    companion object {
        const val ROW = 4096

        /** How many stages a night has: see [stage]. */
        const val STAGES = 6

        /**
         * The stage of the night at [hour]: the evening, late evening, around
         * midnight, small hours, before dawn and the morning. Chunks are baked
         * again at each, so there are only a few.
         */
        fun stage(hour: Float): Int = when {
            hour >= 16f && hour < 20f -> 0
            hour >= 20f && hour < 23f -> 1
            hour >= 23f || hour < 1f -> 2
            hour < 4f -> 3
            hour < 6f -> 4
            else -> 5
        }

        /** Out of 256, the share of windows lit at each stage, in homes and at work. */
        val HOME_SHARE = intArrayOf(200, 165, 90, 18, 45, 130)
        val WORK_SHARE = intArrayOf(150, 55, 25, 12, 16, 80)

        /** Lamplight in homes, two tones of it, and cooler light at work. */
        val HOME_COLOURS = intArrayOf(0xFFC45C, 0xFFD88A)
        val WORK_COLOURS = intArrayOf(0xF4F0D2, 0xFFE6A8)
    }
}
