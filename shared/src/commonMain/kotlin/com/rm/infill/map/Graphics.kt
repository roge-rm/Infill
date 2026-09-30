package com.rm.infill.map

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineDispatcher

/** How much the map drawing asks of the device. */
enum class GraphicsLevel { Low, Medium, High }

/** What each level turns on. */
class Graphics(val level: GraphicsLevel) {
    /** Shadows under trees and buildings. */
    val shadows = level != GraphicsLevel.Low

    /** Shadows follow the sun. Otherwise they stay as they are mid morning. */
    val movingShadows = level == GraphicsLevel.High

    /** Cloud shadows drifting over the map, and banks in fog. */
    val cloudShadows = level != GraphicsLevel.Low

    /** How much rain and snow is drawn, as a share of all of it. */
    val particles = when (level) {
        GraphicsLevel.Low -> 0f
        GraphicsLevel.Medium -> 0.5f
        GraphicsLevel.High -> 1f
    }

    /** The sharpest atlas used: 0 is 32 px a tile, 1 is 16. */
    val sharpest = if (level == GraphicsLevel.Low) 1 else 0

    /**
     * Room for baked chunks: the screen and a ring around it, the whole map at
     * the coarser levels, and a second set while the sun or the season changes.
     * A chunk is 1 MB at 32 px, 256 kB at 16 and 64 kB at 8.
     */
    val cacheBytes = when (level) {
        GraphicsLevel.Low -> 40L shl 20
        GraphicsLevel.Medium -> 96L shl 20
        GraphicsLevel.High -> 160L shl 20
    } * cacheScale

    fun sunStep(step: Int) = if (movingShadows) step else FIXED_STEP

    private companion object {
        /** About half past ten. */
        const val FIXED_STEP = 14
    }
}

/** How much more room for chunks this platform has. Browsers have plenty. */
internal expect val cacheScale: Int

/**
 * On a platform with one thread, how long a frame may spend baking before it
 * lets the screen draw. Null where baking has a thread of its own.
 */
internal expect val bakeBudgetMs: Long?

/** A bitmap from ARGB pixels, with the alpha not multiplied in. */
internal expect fun imageBitmapOf(pixels: IntArray, width: Int, height: Int): ImageBitmap

/** Where chunks are baked: a background thread where there is one. */
internal expect val bakeDispatcher: CoroutineDispatcher

/**
 * Keeps the system's edge swipes (back, on Android) off a band down the middle
 * of the map, so a tool dragged near the edge isn't taken for one.
 */
internal expect fun androidx.compose.ui.Modifier.keepEdgeSwipesOff(): androidx.compose.ui.Modifier
