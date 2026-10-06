@file:OptIn(UnsafeWasmMemoryApi::class)

package com.rm.infill.map

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

/**
 * Skia's own way in copies pixels a byte at a time, one call each, which takes
 * about 60 ms for a chunk. Instead the pixels are laid out in Kotlin's memory
 * as RGBA, and copied into the bitmap Skia allocated in one go.
 */
internal actual fun imageBitmapOf(pixels: IntArray, width: Int, height: Int): ImageBitmap {
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL))
    val target = bitmap.peekPixels()!!.addr
    val bytes = pixels.size * 4
    withScopedMemoryAllocator { allocator ->
        val buffer = allocator.allocate(bytes)
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = p ushr 24
            // ARGB to the bytes R, G, B, A, with the alpha multiplied in as Skia wants it.
            val rgba = if (a == 255 || a == 0) {
                if (a == 0) 0 else (p and -0xff0100) or ((p shr 16) and 0xff) or ((p and 0xff) shl 16)
            } else {
                val r = ((p shr 16) and 0xff) * a / 255
                val g = ((p shr 8) and 0xff) * a / 255
                val b = (p and 0xff) * a / 255
                (a shl 24) or (b shl 16) or (g shl 8) or r
            }
            (buffer + i * 4).storeInt(rgba)
        }
        copyIntoSkia(loadedWasm, buffer.address.toInt(), target, bytes)
    }
    bitmap.notifyPixelsChanged()
    bitmap.setImmutable()
    return bitmap.asComposeImageBitmap()
}

/** Copies [bytes] from Kotlin's memory at [from] into Skia's at [to]. */
private fun copyIntoSkia(skia: JsAny, from: Int, to: Int, bytes: Int): Unit =
    js("new Uint8Array(skia._.memory.buffer, to, bytes).set(new Uint8Array(wasmExports.memory.buffer, from, bytes))")

/** The browser has one thread, so chunks are baked between frames, a few milliseconds at a time. */
internal actual val bakeDispatcher: CoroutineDispatcher = Dispatchers.Default

internal actual fun newSurface(atlas: TileAtlas, level: Int, size: Int, roofs: Boolean): BakeSurface = PixelSurface(atlas, level, size, roofs)

internal actual val cacheScale: Int = 2

internal actual val bakeBudgetMs: Long? = 6L

/** Browsers have no edge swipes to keep off. */
internal actual fun androidx.compose.ui.Modifier.keepEdgeSwipesOff(): androidx.compose.ui.Modifier = this
