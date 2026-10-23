package com.rm.infill.map

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * What a chunk is baked onto. The renderer decides what goes where and a
 * surface does the drawing. [PixelSurface] is the one there is: plain pixel
 * work is quicker than the platform's canvas on both the phone and the browser.
 */
internal interface BakeSurface {
    /** A sprite with no see-through pixels, with its tile's top left corner at [dx], [dy]. */
    fun copy(index: Int, dx: Int, dy: Int)

    /** A sprite drawn over what's there, by its alpha. */
    fun blend(index: Int, dx: Int, dy: Int)

    /** A rectangle of solid RGB [colour] laid over what's there, [alpha] out of 255. */
    fun fill(x: Int, y: Int, w: Int, h: Int, colour: Int, alpha: Int)

    /** Starts the shadows, which come out [alpha] dark however many overlap. */
    fun beginShadows(alpha: Float)
    fun shadowLine(ax: Float, ay: Float, bx: Float, by: Float, half: Float)

    /** An oval centred on [cx], [cy], [a] long along the unit direction [ux], [uy] and [b] across it. */
    fun shadowOval(cx: Float, cy: Float, ux: Float, uy: Float, a: Float, b: Float)
    /** A convex polygon, given as x, y pairs going round. */
    fun shadowPolygon(points: FloatArray)
    fun endShadows()

    /** Which pixels the last shadows fell on, row by row, or null if there were none. */
    fun shadowMask(): BooleanArray?

    /**
     * Sprites blended from now on put what's above pixel row [y] onto the
     * roofs, if this surface keeps them; [NO_ROOFS] to stop.
     */
    fun raiseAbove(y: Int)

    fun finish(): ImageBitmap

    /** What went up onto the roofs, or null if this surface doesn't keep them. */
    fun finishRoofs(): ImageBitmap?

    /** Starts a layer of lit windows. From now on what's blended solid hides the lights behind it. */
    fun beginLights()

    /** Hides the lights behind what's solid in the sprite with [index] at [dx], [dy], without drawing it. */
    fun cover(index: Int, dx: Int, dy: Int)

    /**
     * Lights the windows in [panes] (from [WindowLights.panes]) of the sprite
     * drawn at [dx], [dy] with look-free [index]: a pane is lit when its hash
     * with [seed] falls under [share] out of 256, in one of two [colours] (RGB).
     */
    fun light(panes: IntArray, index: Int, dx: Int, dy: Int, seed: Int, share: Int, colours: IntArray)

    /**
     * A soft round pool of street light centred on [x], [y] in pixels of this
     * surface, which may be outside it, [radius] pixels across from the middle,
     * [peak] out of 255 in the middle, in [colour] (RGB). Where pools meet, the stronger shows.
     */
    fun pool(x: Float, y: Float, radius: Float, peak: Int, colour: Int)

    /**
     * The lit windows with a soft glow round them, or null if none were lit or
     * there's no layer. [margin] pixels round the edge are left off, there only to glow into what's kept.
     */
    fun finishLights(margin: Int = 0): ImageBitmap?

    companion object {
        const val NO_ROOFS = Int.MIN_VALUE
    }
}

/** The surface this platform bakes on best, keeping the [roofs] apart if asked. */
internal expect fun newSurface(atlas: TileAtlas, level: Int, size: Int, roofs: Boolean = false): BakeSurface

/** Pixels in an IntArray, turned into a bitmap at the end. Safe on any thread. */
internal class PixelSurface(atlas: TileAtlas, private val level: Int, private val size: Int, keepRoofs: Boolean = false) : BakeSurface {
    private val out = IntArray(size * size)

    /** The parts of sprites above [split], kept apart to draw over what moves; see [raiseAbove]. */
    private val roofs = if (keepRoofs) IntArray(size * size) else null
    private var split = BakeSurface.NO_ROOFS

    override fun raiseAbove(y: Int) {
        split = y
    }
    private val atlas = atlas
    private val srcWidth = atlas.width(level)

    /** Each look's pixels, taken from the atlas the first time this surface draws in it. */
    private val looks = arrayOfNulls<IntArray>(Atlas.LOOKS)

    private fun source(index: Int): IntArray {
        val look = index / Atlas.PER_LOOK
        return looks[look] ?: atlas.pixels(level, look).also { looks[look] = it }
    }
    private var mask: BooleanArray? = null
    private var shadowKeep = 256

    override fun copy(index: Int, dx: Int, dy: Int) {
        val r = index * 5
        val sx = Atlas.rects[r] shr level
        val sy = Atlas.rects[r + 1] shr level
        val w = Atlas.rects[r + 2] shr level
        val h = Atlas.rects[r + 3] shr level
        val top = dy - (Atlas.rects[r + 4] shr level)
        val src = source(index)
        val x0 = max(0, dx)
        val x1 = min(size, dx + w)
        if (x0 >= x1) return
        for (row in 0 until h) {
            val y = top + row
            if (y < 0 || y >= size) continue
            val from = (sy + row) * srcWidth + sx + (x0 - dx)
            src.copyInto(out, y * size + x0, from, from + (x1 - x0))
        }
    }

    override fun blend(index: Int, dx: Int, dy: Int) {
        val r = index * 5
        val sx = Atlas.rects[r] shr level
        val sy = Atlas.rects[r + 1] shr level
        val w = Atlas.rects[r + 2] shr level
        val h = Atlas.rects[r + 3] shr level
        val top = dy - (Atlas.rects[r + 4] shr level)
        val src = source(index)
        val lights = lit
        for (row in 0 until h) {
            val y = top + row
            if (y < 0 || y >= size) continue
            val from = (sy + row) * srcWidth + sx
            val to = y * size
            val up = roofs != null && y < split
            for (col in 0 until w) {
                val x = dx + col
                if (x < 0 || x >= size) continue
                val p = src[from + col]
                val a = p ushr 24
                if (a == 0) continue
                if (lights != null && a >= SOLID) lights[to + x] = 0
                if (up) {
                    over(roofs!!, to + x, p, a)
                    continue
                }
                if (a == 255) {
                    out[to + x] = p
                    continue
                }
                val q = out[to + x]
                val inv = 255 - a
                val rr = (((p shr 16) and 0xff) * a + ((q shr 16) and 0xff) * inv) / 255
                val gg = (((p shr 8) and 0xff) * a + ((q shr 8) and 0xff) * inv) / 255
                val bb = ((p and 0xff) * a + (q and 0xff) * inv) / 255
                out[to + x] = (-0x1000000) or (rr shl 16) or (gg shl 8) or bb
            }
        }
    }

    override fun fill(x: Int, y: Int, w: Int, h: Int, colour: Int, alpha: Int) {
        val inv = 255 - alpha
        val cr = ((colour shr 16) and 0xff) * alpha
        val cg = ((colour shr 8) and 0xff) * alpha
        val cb = (colour and 0xff) * alpha
        for (row in max(0, y) until min(size, y + h)) {
            for (col in max(0, x) until min(size, x + w)) {
                val i = row * size + col
                val q = out[i]
                val rr = (cr + ((q shr 16) and 0xff) * inv) / 255
                val gg = (cg + ((q shr 8) and 0xff) * inv) / 255
                val bb = (cb + (q and 0xff) * inv) / 255
                out[i] = (q and -0x1000000) or (rr shl 16) or (gg shl 8) or bb
            }
        }
    }

    override fun beginShadows(alpha: Float) {
        mask = BooleanArray(size * size)
        shadowKeep = (256 * (1f - alpha)).toInt()
    }

    override fun shadowLine(ax: Float, ay: Float, bx: Float, by: Float, half: Float) {
        val m = mask ?: return
        val left = max(0, floor(min(ax, bx) - half).toInt())
        val right = min(size - 1, ceil(max(ax, bx) + half).toInt())
        val top = max(0, floor(min(ay, by) - half).toInt())
        val bottom = min(size - 1, ceil(max(ay, by) + half).toInt())
        val vx = bx - ax
        val vy = by - ay
        val len2 = max(1e-4f, vx * vx + vy * vy)
        for (y in top..bottom) {
            val py = y + 0.5f - ay
            for (x in left..right) {
                val px = x + 0.5f - ax
                val t = ((px * vx + py * vy) / len2).coerceIn(0f, 1f)
                val ex = px - t * vx
                val ey = py - t * vy
                if (ex * ex + ey * ey <= half * half) m[y * size + x] = true
            }
        }
    }

    override fun shadowOval(cx: Float, cy: Float, ux: Float, uy: Float, a: Float, b: Float) {
        val m = mask ?: return
        val reach = max(a, b)
        val left = max(0, floor(cx - reach).toInt())
        val right = min(size - 1, ceil(cx + reach).toInt())
        val top = max(0, floor(cy - reach).toInt())
        val bottom = min(size - 1, ceil(cy + reach).toInt())
        val ia = 1f / (a * a)
        val ib = 1f / (b * b)
        for (y in top..bottom) {
            val py = y + 0.5f - cy
            for (x in left..right) {
                val px = x + 0.5f - cx
                val along = px * ux + py * uy
                val across = py * ux - px * uy
                if (along * along * ia + across * across * ib <= 1f) m[y * size + x] = true
            }
        }
    }

    override fun shadowPolygon(points: FloatArray) {
        val m = mask ?: return
        val n = points.size / 2
        if (n < 3) return
        var top = Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (k in 0 until n) {
            top = min(top, points[k * 2 + 1])
            bottom = max(bottom, points[k * 2 + 1])
        }
        for (y in max(0, floor(top).toInt())..min(size - 1, ceil(bottom).toInt())) {
            val py = y + 0.5f
            var left = Float.MAX_VALUE
            var right = -Float.MAX_VALUE
            for (k in 0 until n) {
                val ax = points[k * 2]
                val ay = points[k * 2 + 1]
                val bx = points[((k + 1) % n) * 2]
                val by = points[((k + 1) % n) * 2 + 1]
                if ((ay <= py && by > py) || (by <= py && ay > py)) {
                    val x = ax + (py - ay) / (by - ay) * (bx - ax)
                    left = min(left, x)
                    right = max(right, x)
                }
            }
            if (left > right) continue
            for (x in max(0, (left + 0.5f).toInt())..min(size - 1, (right - 0.5f).toInt())) m[y * size + x] = true
        }
    }

    override fun endShadows() {
        val m = mask ?: return
        val keep = shadowKeep
        for (i in out.indices) {
            if (!m[i]) continue
            val p = out[i]
            val rr = ((p shr 16) and 0xff) * keep shr 8
            val gg = ((p shr 8) and 0xff) * keep shr 8
            val bb = (p and 0xff) * keep shr 8
            out[i] = (p and -0x1000000) or (rr shl 16) or (gg shl 8) or bb
        }
        lastMask = m
        mask = null
    }

    private var lastMask: BooleanArray? = null

    override fun shadowMask(): BooleanArray? = lastMask

    override fun finish(): ImageBitmap = imageBitmapOf(out, size, size)

    override fun finishRoofs(): ImageBitmap? = roofs?.let { imageBitmapOf(it, size, size) }

    /** The lit windows, as ARGB, while there's a layer of them. */
    private var lit: IntArray? = null

    override fun beginLights() {
        lit = IntArray(size * size)
    }

    override fun cover(index: Int, dx: Int, dy: Int) {
        val lights = lit ?: return
        val r = index * 5
        val sx = Atlas.rects[r] shr level
        val sy = Atlas.rects[r + 1] shr level
        val w = Atlas.rects[r + 2] shr level
        val h = Atlas.rects[r + 3] shr level
        val top = dy - (Atlas.rects[r + 4] shr level)
        val src = source(index)
        val x0 = max(0, dx)
        val x1 = min(size, dx + w)
        for (row in 0 until h) {
            val y = top + row
            if (y < 0 || y >= size) continue
            val from = (sy + row) * srcWidth + sx - dx
            val to = y * size
            for (x in x0 until x1) if (src[from + x] ushr 24 >= SOLID) lights[to + x] = 0
        }
    }

    override fun light(panes: IntArray, index: Int, dx: Int, dy: Int, seed: Int, share: Int, colours: IntArray) {
        val into = lit ?: return
        val top = dy - (Atlas.rects[index * 5 + 4] shr level)
        // Coarser sheets have less of each pixel as glass, so they're lit up a little more.
        val boost = 4 + level * 2
        var k = 0
        while (k < panes.size) {
            val at = panes[k]
            val v = panes[k + 1]
            k += 2
            val pane = v ushr 8
            val h = MapRenderer.tileHash(seed, pane)
            if (h and 0xff >= share) continue
            val x = dx + at % WindowLights.ROW
            val y = top + at / WindowLights.ROW
            if (x < 0 || x >= size || y < 0 || y >= size) continue
            val a = min(255, (v and 0xff) * boost / 4)
            into[y * size + x] = (a shl 24) or colours[(h shr 8) and 1]
        }
    }

    override fun pool(x: Float, y: Float, radius: Float, peak: Int, colour: Int) {
        val into = lit ?: return
        val left = max(0, floor(x - radius).toInt())
        val right = min(size - 1, ceil(x + radius).toInt())
        val top = max(0, floor(y - radius).toInt())
        val bottom = min(size - 1, ceil(y + radius).toInt())
        val r2 = radius * radius
        for (py in top..bottom) for (px in left..right) {
            val ddx = px + 0.5f - x
            val ddy = py + 0.5f - y
            val f = 1f - (ddx * ddx + ddy * ddy) / r2
            if (f <= 0f) continue
            val a = (peak * f * f).toInt()
            if (a < 4) continue
            val k = py * size + px
            if (into[k] ushr 24 < a) into[k] = (a shl 24) or colour
        }
    }

    override fun finishLights(margin: Int): ImageBitmap? {
        val from = lit ?: return null
        lit = null
        if (from.all { it == 0 }) return null
        if (level == MapRenderer.LEVELS - 1) return imageBitmapOf(crop(from, margin), size - 2 * margin, size - 2 * margin)
        // A faint glow on the pixels round each lit one, taken from the brightest beside it.
        val glow = from.copyOf()
        for (y in 0 until size) for (x in 0 until size) {
            if (from[y * size + x] != 0) continue
            var best = 0
            for (ny in max(0, y - 1)..min(size - 1, y + 1)) for (nx in max(0, x - 1)..min(size - 1, x + 1)) {
                val q = from[ny * size + nx]
                if (q ushr 24 > best ushr 24) best = q
            }
            if (best != 0) glow[y * size + x] = (((best ushr 24) * GLOW / 255) shl 24) or (best and 0xffffff)
        }
        return imageBitmapOf(crop(glow, margin), size - 2 * margin, size - 2 * margin)
    }

    /** [pixels] without [margin] pixels round the edge. */
    private fun crop(pixels: IntArray, margin: Int): IntArray {
        if (margin == 0) return pixels
        val inner = size - 2 * margin
        val out = IntArray(inner * inner)
        for (y in 0 until inner) pixels.copyInto(out, y * inner, (y + margin) * size + margin, (y + margin) * size + margin + inner)
        return out
    }

    /** [p], [a] out of 255 solid, laid over pixel [k] of [into], which may be see-through. */
    private fun over(into: IntArray, k: Int, p: Int, a: Int) {
        if (a == 255) {
            into[k] = p
            return
        }
        val q = into[k]
        val qa = (q ushr 24) * (255 - a) / 255
        val oa = a + qa
        if (oa == 0) return
        val rr = (((p shr 16) and 0xff) * a + ((q shr 16) and 0xff) * qa) / oa
        val gg = (((p shr 8) and 0xff) * a + ((q shr 8) and 0xff) * qa) / oa
        val bb = ((p and 0xff) * a + (q and 0xff) * qa) / oa
        into[k] = (oa shl 24) or (rr shl 16) or (gg shl 8) or bb
    }

    private companion object {
        /** A sprite pixel at least this solid hides the lit windows behind it. */
        const val SOLID = 128

        /** How strong the glow round a lit window is, out of 255 of the window's. */
        const val GLOW = 80
    }
}
