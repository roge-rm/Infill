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
    fun endShadows()

    fun finish(): ImageBitmap
}

/** The surface this platform bakes on best. */
internal expect fun newSurface(atlas: TileAtlas, level: Int, size: Int): BakeSurface

/** Pixels in an IntArray, turned into a bitmap at the end. Safe on any thread. */
internal class PixelSurface(atlas: TileAtlas, private val level: Int, private val size: Int) : BakeSurface {
    private val out = IntArray(size * size)
    private val src = atlas.pixels[level]
    private val srcWidth = atlas.levels[level].width
    private var mask: BooleanArray? = null
    private var shadowKeep = 256

    override fun copy(index: Int, dx: Int, dy: Int) {
        val r = index * 5
        val sx = Atlas.rects[r] shr level
        val sy = Atlas.rects[r + 1] shr level
        val w = Atlas.rects[r + 2] shr level
        val h = Atlas.rects[r + 3] shr level
        val top = dy - (Atlas.rects[r + 4] shr level)
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
        for (row in 0 until h) {
            val y = top + row
            if (y < 0 || y >= size) continue
            val from = (sy + row) * srcWidth + sx
            val to = y * size
            for (col in 0 until w) {
                val x = dx + col
                if (x < 0 || x >= size) continue
                val p = src[from + col]
                val a = p ushr 24
                if (a == 0) continue
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
        mask = null
    }

    override fun finish(): ImageBitmap = imageBitmapOf(out, size, size)
}
