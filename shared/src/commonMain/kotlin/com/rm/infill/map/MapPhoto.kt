package com.rm.infill.map

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer

/** The map as it was last drawn, without the buttons and windows over it, kept for a picture of the town. */
class MapPhoto {
    internal var layer: GraphicsLayer? = null

    /** The map now, or null before it's been drawn. */
    suspend fun take(): ImageBitmap? = layer?.takeIf { it.size.width > 0 }?.toImageBitmap()
}
