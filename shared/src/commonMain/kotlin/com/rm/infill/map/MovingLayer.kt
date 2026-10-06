package com.rm.infill.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.rm.infill.sim.CityMap
import kotlin.math.floor

/**
 * What moves on the roads and track, drawn onto a layer of its own so it
 * can sit in the buildings' shadows and go behind the buildings in front
 * of it. Between [begin] and [end] everything drawn goes on the layer;
 * [mark] notes where each thing is, so [end] only darkens and cuts away on
 * the tiles that have something on them.
 */
internal class MovingLayer(private val map: CityMap, private val renderer: MapRenderer) {
    private val marked = BooleanArray(map.size)
    private val tiles = ArrayList<Int>()

    fun begin(scope: DrawScope) {
        scope.drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, scope.size), Paint()) }
    }

    /** Notes something at [x], [y] tiles and the tiles it reaches into. Never hides it: [end] does that by the pixel. */
    fun mark(x: Float, y: Float): Boolean {
        for (ty in floor(y - REACH).toInt()..floor(y + REACH).toInt()) for (tx in floor(x - REACH).toInt()..floor(x + REACH).toInt()) {
            if (!map.inside(tx, ty)) continue
            val i = map.index(tx, ty)
            if (!marked[i]) {
                marked[i] = true
                tiles += i
            }
        }
        return false
    }

    /**
     * Darkens what's in shadow by [darkness], cuts away what's behind a
     * building unless [roofs] are drawn over it anyway, and puts the layer
     * down on the map.
     */
    fun end(scope: DrawScope, camera: Camera, darkness: Float, roofs: Boolean) = with(scope) {
        val t = camera.tilePx
        val dark = Color.Black.copy(alpha = darkness)
        val cells = MapRenderer.SHADE_CELLS
        val cell = t / cells
        for (i in tiles) {
            marked[i] = false
            val tx = i % map.width
            val ty = i / map.width
            val corner = camera.tileToScreen(tx.toFloat(), ty.toFloat(), size)
            // The shadow, in runs of cells along each row.
            val bits = renderer.shadeOf(i)
            if (darkness > 0f && bits != 0L) {
                for (cy in 0 until cells) {
                    var cx = 0
                    while (cx < cells) {
                        if (bits and (1L shl (cy * cells + cx)) == 0L) {
                            cx++
                            continue
                        }
                        val start = cx
                        while (cx < cells && bits and (1L shl (cy * cells + cx)) != 0L) cx++
                        drawRect(dark, Offset(corner.x + start * cell, corner.y + cy * cell), Size((cx - start) * cell, cell), blendMode = BlendMode.SrcAtop)
                    }
                }
            }
            // What's behind the buildings in front, cut away.
            if (!roofs) {
                val q = MapRenderer.QUARTERS
                for (k in 0 until q) {
                    val from = renderer.coverOf(i, k)
                    if (from >= MapRenderer.FULL) continue
                    val top = corner.y + t * from / MapRenderer.FULL
                    drawRect(Color.Black, Offset(corner.x + t * k / q, top), Size(t / q, corner.y + t - top), blendMode = BlendMode.Clear)
                }
            }
        }
        tiles.clear()
        drawIntoCanvas { it.restore() }
    }

    private companion object {
        /** How far from its middle anything moving reaches, in tiles. */
        const val REACH = 0.4f
    }
}
