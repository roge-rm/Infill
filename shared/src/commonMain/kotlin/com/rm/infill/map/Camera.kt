package com.rm.infill.map

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * Where the view is looking. The centre is in tiles and the zoom is how many
 * screen pixels a tile takes, kept between [minTilePx] and [maxTilePx].
 */
@Stable
class Camera(
    private val mapWidth: Int,
    private val mapHeight: Int,
    private val minTilePx: Float,
    private val maxTilePx: Float,
    startTilePx: Float,
) {
    var centreX by mutableFloatStateOf(mapWidth / 2f)
        private set
    var centreY by mutableFloatStateOf(mapHeight / 2f)
        private set
    var tilePx by mutableFloatStateOf(startTilePx.coerceIn(minTilePx, maxTilePx))
        private set

    /** Moves the map with a finger: [dx] and [dy] are screen pixels. */
    fun panBy(dx: Float, dy: Float) {
        centreX = (centreX - dx / tilePx).coerceIn(0f, mapWidth.toFloat())
        centreY = (centreY - dy / tilePx).coerceIn(0f, mapHeight.toFloat())
    }

    /** Zooms by [factor] and keeps the tile under [focus] where it is on screen. */
    fun zoomBy(factor: Float, focus: Offset, view: Size) {
        val before = screenToTile(focus, view)
        tilePx = (tilePx * factor).coerceIn(minTilePx, maxTilePx)
        val after = screenToTile(focus, view)
        centreX = (centreX + before.x - after.x).coerceIn(0f, mapWidth.toFloat())
        centreY = (centreY + before.y - after.y).coerceIn(0f, mapHeight.toFloat())
    }

    /** A screen point in tile coordinates. The tile is the whole part. */
    fun screenToTile(p: Offset, view: Size) = Offset(
        centreX + (p.x - view.width / 2f) / tilePx,
        centreY + (p.y - view.height / 2f) / tilePx,
    )

    /** The top left corner of a tile on screen. */
    fun tileToScreen(x: Float, y: Float, view: Size) = Offset(
        (x - centreX) * tilePx + view.width / 2f,
        (y - centreY) * tilePx + view.height / 2f,
    )
}
