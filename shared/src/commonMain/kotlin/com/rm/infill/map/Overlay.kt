package com.rm.infill.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.rm.infill.res.Res
import com.rm.infill.res.overlay_crime
import com.rm.infill.res.overlay_fire
import com.rm.infill.res.overlay_land_value
import com.rm.infill.res.overlay_none
import com.rm.infill.res.overlay_police
import com.rm.infill.res.overlay_pollution
import com.rm.infill.res.overlay_power
import com.rm.infill.res.overlay_traffic
import com.rm.infill.res.overlay_rail
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.Terrain
import com.rm.infill.sim.Zone
import org.jetbrains.compose.resources.StringResource
import kotlin.math.min
import kotlin.math.roundToInt

/** What can be shown over the map, and the colours it goes from and to. */
enum class Overlay(val title: StringResource, val low: Color, val high: Color) {
    None(Res.string.overlay_none, Color.Transparent, Color.Transparent),
    LandValue(Res.string.overlay_land_value, Color(0xFF3B2C7A), Color(0xFFE8E04A)),
    Pollution(Res.string.overlay_pollution, Color(0x00A0522D), Color(0xFF6B3A1E)),
    Crime(Res.string.overlay_crime, Color(0x00D32F2F), Color(0xFFB71C1C)),
    Power(Res.string.overlay_power, Color(0xFFD84343), Color(0xFF4CAF50)),
    Police(Res.string.overlay_police, Color(0x001E5AC8), Color(0xFF1E5AC8)),
    Fire(Res.string.overlay_fire, Color(0x00E67E22), Color(0xFFE67E22)),
    Traffic(Res.string.overlay_traffic, Color(0xFFFFF1B8), Color(0xFFD8302F)),
    Railway(Res.string.overlay_rail, Color(0xFFE6E1F5), Color(0xFF5B3FB5)),
}

/**
 * The overlay as a bitmap of one pixel a tile, drawn smoothly over the map.
 * Land value covers all land; the rest show only where they are.
 */
internal fun overlayImage(overlay: Overlay, map: CityMap): ImageBitmap? {
    if (overlay == Overlay.None) return null
    val pixels = IntArray(map.size)
    for (i in 0 until map.size) {
        // Water shows nothing, except bridges for traffic.
        if (map.terrain[i] == Terrain.WATER && (overlay != Overlay.Traffic || map.road[i].toInt() == 0) &&
            (overlay != Overlay.Railway || map.rail[i].toInt() == 0)
        ) continue
        val v: Int = when (overlay) {
            Overlay.LandValue -> map.landValue[i].toInt() and 0xff
            Overlay.Pollution -> map.pollution[i].toInt() and 0xff
            Overlay.Crime -> map.crime[i].toInt() and 0xff
            Overlay.Police -> map.policeCover[i].toInt() and 0xff
            Overlay.Fire -> map.fireCover[i].toInt() and 0xff
            Overlay.Power -> {
                // Only what wants power: buildings, zones and lines.
                if (map.building[i] == 0 && map.zone[i] == Zone.NONE && map.power[i].toInt() == 0) continue
                if (map.powered[i]) 255 else 0
            }
            Overlay.Traffic -> {
                // Every road, pale when it's clear and red when it's full.
                if (map.road[i].toInt() == 0) continue
                min(255, (map.congestion[i].toInt() and 0xff) * 2)
            }
            Overlay.Railway -> {
                // Every track tile, pale when trains carry little and deep when they're full.
                if (map.rail[i].toInt() == 0) continue
                map.railBusy[i].toInt() and 0xff
            }
            Overlay.None -> 0
        }
        if (overlay != Overlay.LandValue && overlay != Overlay.Power && overlay != Overlay.Traffic && overlay != Overlay.Railway && v == 0) continue
        pixels[i] = mix(overlay.low, overlay.high, v / 255f)
    }
    return imageBitmapOf(pixels, map.width, map.height)
}

/** ARGB between two colours. */
private fun mix(a: Color, b: Color, t: Float): Int {
    val alpha = a.alpha + (b.alpha - a.alpha) * t
    fun ch(x: Float, y: Float) = ((x + (y - x) * t) * 255).roundToInt().coerceIn(0, 255)
    val al = (alpha * 255).roundToInt().coerceIn(0, 255)
    return (al shl 24) or (ch(a.red, b.red) shl 16) or (ch(a.green, b.green) shl 8) or ch(a.blue, b.blue)
}

/** Draws the overlay's bitmap stretched over the whole map. */
internal fun DrawScope.drawOverlay(image: ImageBitmap, map: CityMap, camera: Camera, overlay: Overlay) {
    val a = camera.tileToScreen(0f, 0f, size)
    val b = camera.tileToScreen(map.width.toFloat(), map.height.toFloat(), size)
    drawImage(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(a.x.roundToInt(), a.y.roundToInt()),
        dstSize = IntSize((b.x - a.x).roundToInt(), (b.y - a.y).roundToInt()),
        // Land value covers everything, so it's lighter to let the map show through.
        alpha = if (overlay == Overlay.LandValue) 0.45f else OVERLAY_ALPHA,
        filterQuality = FilterQuality.Low,
    )
}

private const val OVERLAY_ALPHA = 0.62f
