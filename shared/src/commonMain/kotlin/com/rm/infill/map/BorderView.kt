package com.rm.infill.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.rm.infill.sim.Border
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.Power
import com.rm.infill.sim.Rail
import com.rm.infill.sim.Road
import com.rm.infill.sim.Terrain
import kotlin.math.max

/**
 * Past each edge with a town on the other side, a strip of the neighbour's
 * land as it was left: its water and woods, and its roads, track, lines and
 * mains running up to the border, so you can see where to meet them.
 */
internal fun DrawScope.drawNeighbours(neighbours: Array<Border?>, map: CityMap, camera: Camera, atlas: TileAtlas?, look: Int) {
    if (neighbours.all { it == null }) return
    val t = camera.tilePx
    // The ground, woods and water as the season has them, from the tiles themselves.
    val base = look * Atlas.PER_LOOK
    val landArgb = atlas?.let { average(it, base + Atlas.GRASS) }
    val land = landArgb?.let { Color(it) } ?: LAND
    val trees = atlas?.let { Color(average(it, base + Atlas.TREE, landArgb)) } ?: TREES
    val water = atlas?.let { Color(average(it, base + Atlas.WATER)) } ?: WATER
    for (edge in 0 until 4) {
        val b = neighbours[edge] ?: continue
        for (k in 0 until b.length) {
            // The tile just past this edge, and the way into the neighbour.
            val (x, y) = when (edge) {
                Border.NORTH -> k to -1
                Border.EAST -> map.width to k
                Border.SOUTH -> k to map.height
                else -> -1 to k
            }
            for (d in 0 until DEPTH) {
                val (tx, ty) = when (edge) {
                    Border.NORTH -> x to y - d
                    Border.EAST -> x + d to y
                    Border.SOUTH -> x to y + d
                    else -> x - d to y
                }
                val at = camera.tileToScreen(tx.toFloat(), ty.toFloat(), size)
                if (at.x > size.width || at.y > size.height || at.x + t < 0 || at.y + t < 0) continue
                // Fading out the further from the border.
                val fade = 1f - d.toFloat() / DEPTH * 0.85f
                val ground = when (b.terrain[k]) {
                    Terrain.WATER -> water
                    Terrain.TREES -> trees
                    else -> land
                }
                drawRect(ground, at, Size(t, t), alpha = LAND_ALPHA * fade)
                val across = edge == Border.NORTH || edge == Border.SOUTH
                fun bar(colour: Color, share: Float, offset: Float = 0.5f) {
                    val w = max(1f, t * share)
                    if (across) drawRect(colour, Offset(at.x + t * offset - w / 2, at.y), Size(w, t), alpha = fade)
                    else drawRect(colour, Offset(at.x, at.y + t * offset - w / 2), Size(t, w), alpha = fade)
                }
                if (b.road[k] != Road.NONE) bar(ROAD, 0.6f)
                if (b.rail[k] != Rail.NONE) {
                    bar(BALLAST, 0.45f)
                    bar(RAIL, 0.06f, 0.4f)
                    bar(RAIL, 0.06f, 0.6f)
                }
                if (b.power[k] != Power.NONE) bar(if (b.power[k] == Power.HIGH) PYLON else POLE, 0.05f, 0.2f)
                if (b.water[k].toInt() != 0) bar(PIPE, 0.05f, 0.8f)
                if (b.phone[k].toInt() != 0) bar(PHONE, 0.04f, 0.88f)
            }
        }
    }
}

/**
 * The average colour of a sprite's opaque pixels in [atlas], at its smallest
 * size; [under] shows through where it's see-through, as the ground under a tree.
 */
private fun average(atlas: TileAtlas, index: Int, under: Int? = null): Int {
    val level = atlas.levels.size - 1
    val src = atlas.pixels[level]
    val width = atlas.levels[level].width
    val r = index * 5
    val sx = Atlas.rects[r] shr level
    val sy = Atlas.rects[r + 1] shr level
    val w = max(1, Atlas.rects[r + 2] shr level)
    val h = max(1, Atlas.rects[r + 3] shr level)
    var red = 0L
    var green = 0L
    var blue = 0L
    var n = 0
    for (y in sy until sy + h) for (x in sx until sx + w) {
        var p = src[y * width + x]
        if ((p ushr 24) < 128) p = under ?: continue
        red += (p shr 16) and 0xff
        green += (p shr 8) and 0xff
        blue += p and 0xff
        n++
    }
    if (n == 0) return 0xFF808080.toInt()
    return (0xFF shl 24) or ((red / n).toInt() shl 16) or ((green / n).toInt() shl 8) or (blue / n).toInt()
}

/** How many tiles of a neighbour show past the edge. */
private const val DEPTH = 6
private const val LAND_ALPHA = 0.9f

private val LAND = Color(0xFF6E9450)
private val WATER = Color(0xFF3A6FB0)
private val TREES = Color(0xFF2F5A2A)
private val ROAD = Color(0xFF7A7872)
private val BALLAST = Color(0xFF8F8674)
private val RAIL = Color(0xFF4A4038)
private val POLE = Color(0xFF3A2E24)
private val PYLON = Color(0xFF2A2A2E)
private val PIPE = Color(0xFF3C78D7)
private val PHONE = Color(0xFFD08A2A)
