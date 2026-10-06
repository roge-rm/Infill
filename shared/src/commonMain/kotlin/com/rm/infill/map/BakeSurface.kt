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
}
