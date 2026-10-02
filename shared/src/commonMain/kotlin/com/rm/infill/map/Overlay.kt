package com.rm.infill.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.rm.infill.res.overlay_carbon
import com.rm.infill.res.overlay_noise
import com.rm.infill.res.overlay_visitors
import com.rm.infill.res.Res
import com.rm.infill.res.overlay_crime
import com.rm.infill.res.overlay_fire
import com.rm.infill.res.overlay_ladders
import com.rm.infill.res.overlay_comms
import com.rm.infill.res.overlay_theft
import com.rm.infill.res.overlay_vice
import com.rm.infill.res.overlay_rackets
import com.rm.infill.res.overlay_ambulance
import com.rm.infill.res.overlay_land_value
import com.rm.infill.res.overlay_none
import com.rm.infill.res.overlay_police
import com.rm.infill.res.overlay_pollution
import com.rm.infill.res.overlay_power
import com.rm.infill.res.overlay_traffic
import com.rm.infill.res.overlay_rail
import com.rm.infill.res.overlay_water
import com.rm.infill.res.overlay_runoff
import com.rm.infill.res.overlay_schooling
import com.rm.infill.res.overlay_health
import com.rm.infill.res.overlay_wealth
import com.rm.infill.res.overlay_age
import com.rm.infill.res.overlay_transit
import com.rm.infill.res.overlay_heat
import com.rm.infill.res.overlay_garbage
import com.rm.infill.res.overlay_land
import com.rm.infill.res.overlay_goods
import com.rm.infill.res.overlay_junctions
import com.rm.infill.res.overlay_trips
import com.rm.infill.res.overlay_line_load
import com.rm.infill.res.overlay_reach
import com.rm.infill.sim.Resource
import com.rm.infill.sim.Household
import com.rm.infill.sim.Balance
import com.rm.infill.sim.CityMap
import com.rm.infill.sim.Stormwater
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
    /** Each power line by what it carries: green when light, red when it's past what a line is rated for. */
    LineLoad(Res.string.overlay_line_load, Color(0xFF4CAF50), Color(0xFFD8302F)),
    Police(Res.string.overlay_police, Color(0x001E5AC8), Color(0xFF1E5AC8)),
    Fire(Res.string.overlay_fire, Color(0x00E67E22), Color(0xFFE67E22)),
    /** Crime by kind: theft, vice, and the rackets that grow where justice fails. */
    Theft(Res.string.overlay_theft, Color(0x00C0392B), Color(0xFFC0392B)),
    Vice(Res.string.overlay_vice, Color(0x008E44AD), Color(0xFF8E44AD)),
    Rackets(Res.string.overlay_rackets, Color(0x002A2A30), Color(0xFF2A2A30)),
    /** Where a ladder company gets to in time, which a tall building's fire needs. */
    Ladders(Res.string.overlay_ladders, Color(0x00C0392B), Color(0xFFC0392B)),
    /** Where an ambulance gets to in time. */
    Ambulance(Res.string.overlay_ambulance, Color(0x002FA8A0), Color(0xFF2FA8A0)),
    Traffic(Res.string.overlay_traffic, Color(0xFFFFF1B8), Color(0xFFD8302F)),
    Railway(Res.string.overlay_rail, Color(0xFFE6E1F5), Color(0xFF5B3FB5)),
    /** The telephone: none, a phone, broadband, fast. */
    Comms(Res.string.overlay_comms, Color(0x40D84343), Color(0xFF2F6FD8)),
    Water(Res.string.overlay_water, Color(0xFFD84343), Color(0xFF3F8FD8)),
    Runoff(Res.string.overlay_runoff, Color(0xFFB8DDA8), Color(0xFF7A3B2E)),
    Schooling(Res.string.overlay_schooling, Color(0xFFE8D6B0), Color(0xFF2E5FA8)),
    Health(Res.string.overlay_health, Color(0xFFC0392B), Color(0xFF3FA85A)),
    Wealth(Res.string.overlay_wealth, Color(0xFF8A6A4A), Color(0xFFE0B83A)),
    Age(Res.string.overlay_age, Color(0xFF4CAF50), Color(0xFFC0392B)),
    Transit(Res.string.overlay_transit, Color(0xFFD8E8F5), Color(0xFF2F5F9A)),
    Heat(Res.string.overlay_heat, Color(0xFF6FA8C8), Color(0xFFD8402F)),
    Garbage(Res.string.overlay_garbage, Color(0xFF4CAF50), Color(0xFF8A5A2A)),
    Land(Res.string.overlay_land, Color(0xFF6FA848), Color(0xFF2E2E34)),
    /** What brings visitors and where they stay: parks, heritage and the ways in at full strength, hotels by how full they are. */
    Visitors(Res.string.overlay_visitors, Color(0xFFE6F2F0), Color(0xFF16A2A2)),
    /** Where the town's carbon comes from: stations, works and busy roads. */
    Carbon(Res.string.overlay_carbon, Color(0x40A060C0), Color(0xFF6A2A8C)),
    /** The planes' noise around the airports. */
    Noise(Res.string.overlay_noise, Color(0x00D84343), Color(0xFFD84343)),
    Goods(Res.string.overlay_goods, Color(0xFFD84343), Color(0xFF4CAF50)),
    Junctions(Res.string.overlay_junctions, Color(0xFF4CAF50), Color(0xFFD8302F)),
    /** For the road being inspected: where the vehicles on it came from and went, and the roads between. */
    Trips(Res.string.overlay_trips, Color(0x80C8B4F0), Color(0xFF5B2FA8)),
    /** From the road being inspected: how long it takes to drive everywhere, green near to red a quarter of an hour away. */
    Reach(Res.string.overlay_reach, Color(0xFF3FA85A), Color(0xFFD8302F)),
}

/** The crime views run up to the worst on the map, though never past this far. */
private const val CRIME_FLOOR = 24

/** The layer a crime view shows, or null. */
private fun crimeLayer(overlay: Overlay, map: CityMap): ByteArray? = when (overlay) {
    Overlay.Crime -> map.crime
    Overlay.Theft -> map.theft
    Overlay.Vice -> map.vice
    Overlay.Rackets -> map.rackets
    else -> null
}

/**
 * The overlay as a bitmap of one pixel a tile, drawn smoothly over the map.
 * Land value covers all land; the rest show only where they are.
 */
internal fun overlayImage(
    overlay: Overlay, map: CityMap, homeAt: (Int) -> Household?, wearAt: (Int) -> Int, uncollectedAt: (Int) -> Boolean,
    localAt: (Int) -> Int, waitAt: (Int) -> Int, focus: IntArray?, loadAt: (Int) -> Int, visitorAt: (Int) -> Int = { -1 }, ridersAt: (Int) -> Int,
): ImageBitmap? {
    if (overlay == Overlay.None) return null
    val most = if (overlay == Overlay.Trips) maxOf(1, focus?.maxOrNull() ?: 1) else 1
    // Crime rarely runs past a third of the scale: its views show it against the worst there is.
    val worst = crimeLayer(overlay, map)?.let { layer -> maxOf(CRIME_FLOOR, layer.maxOf { it.toInt() and 0xff }) } ?: 255
    val pixels = IntArray(map.size)
    for (i in 0 until map.size) {
        // Water shows nothing, except bridges for traffic.
        if (map.terrain[i] == Terrain.WATER && (overlay != Overlay.Traffic || map.road[i].toInt() == 0) &&
            (overlay != Overlay.Railway || map.rail[i].toInt() == 0)
        ) continue
        val v: Int = when (overlay) {
            Overlay.LandValue -> map.landValue[i].toInt() and 0xff
            Overlay.Pollution -> map.pollution[i].toInt() and 0xff
            Overlay.Crime -> (map.crime[i].toInt() and 0xff) * 255 / worst
            Overlay.Police -> map.policeCover[i].toInt() and 0xff
            Overlay.Fire -> map.fireCover[i].toInt() and 0xff
            Overlay.Ladders -> map.ladderCover[i].toInt() and 0xff
            Overlay.Comms -> {
                // Where there's anything to have it.
                if (map.building[i] == 0 && map.zone[i] == Zone.NONE && map.comms[i].toInt() == 0) continue
                map.comms[i] * 85
            }
            Overlay.Theft -> (map.theft[i].toInt() and 0xff) * 255 / worst
            Overlay.Vice -> (map.vice[i].toInt() and 0xff) * 255 / worst
            Overlay.Rackets -> (map.rackets[i].toInt() and 0xff) * 255 / worst
            Overlay.Ambulance -> map.ambulanceCover[i].toInt() and 0xff
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
            Overlay.Water -> {
                // Only what can have it: buildings and zones.
                if (map.building[i] == 0 && map.zone[i] == Zone.NONE) continue
                if (map.watered[i]) 255 else 0
            }
            Overlay.Runoff -> {
                // How much of the rain runs off, and less of it where a storm drain's near.
                val hard = Stormwater.hardness(map, i) * 255 / 100
                if (drained(map, i)) hard / 3 else hard
            }
            // Homes only, and not those standing empty.
            Overlay.Schooling -> {
                val h = homeAt(i)?.takeIf { !it.empty } ?: continue
                // The children's schooling where there are children, else what the grown-ups had.
                if (h.children > 0) (h.schooling * 2 + h.highSchooling) * 255 / 300
                else if (h.adults == 0) continue
                else (h.schooled[1] + h.schooled[2] * 2) * 255 / (h.adults * 2)
            }
            Overlay.Health -> (homeAt(i)?.takeIf { !it.empty } ?: continue).health * 255 / 100
            Overlay.Wealth -> (homeAt(i)?.takeIf { !it.empty } ?: continue).wealth * 127
            // Green when new to red at its expected life and past it.
            Overlay.Age -> wearAt(i).takeIf { it >= 0 }?.let { min(255, it * 255 / 100) } ?: continue
            // Riders on the buses, trams and subway, pale when few and deep when many.
            Overlay.Transit -> ridersAt(i).takeIf { it > 0 }?.let { min(255, 40 + it / 8) } ?: continue
            // Cool where it's green and wet, hot where it's paved and roofed.
            Overlay.Heat -> map.heat[i].toInt() and 0xff
            // Buildings: green where garbage is taken away, brown where it piles up.
            Overlay.Garbage -> {
                if (map.building[i] == 0) continue
                if (uncollectedAt(i)) 255 else 0
            }
            // What's in the ground: good soil, ore and coal; the rest shows nothing.
            Overlay.Land -> {
                val colour = when (map.resource[i]) {
                    Resource.FERTILE -> 0x9A7A5230.toInt()
                    Resource.ORE -> 0xE0A04A30.toInt()
                    Resource.COAL -> 0xE02A2A30.toInt()
                    Resource.OIL -> 0xE06A3A8A.toInt()
                    else -> continue
                }
                pixels[i] = colour
                continue
            }
            // Works, farms, mines and coal stations: red where it all comes from or goes out of town, green where it's the town's own.
            Overlay.Visitors -> visitorAt(i).takeIf { it >= 0 } ?: continue
            Overlay.Noise -> min(255, (map.noise[i].toInt() and 0xff) * 255 / 40)
            Overlay.Carbon -> map.carbon[i].toInt() and 0xff
            Overlay.Goods -> localAt(i).takeIf { it >= 0 }?.let { it * 255 / 100 } ?: continue
            // Each crossing, green when it's quick to get through and red when traffic backs up: a minute or more.
            Overlay.Junctions -> {
                if (map.control[i].toInt() == 0) continue
                min(255, waitAt(i) * 255 / 60)
            }
            Overlay.LineLoad -> {
                if (map.power[i].toInt() == 0) continue
                min(255, loadAt(i) * 255 / Balance.LINE_RATING)
            }
            Overlay.Trips -> {
                val v = focus?.get(i) ?: continue
                if (v <= 0) continue
                maxOf(40, v * 255 / most)
            }
            Overlay.Reach -> {
                val v = focus?.get(i) ?: continue
                if (v < 0) continue
                min(255, v * 255 / QUARTER_HOUR)
            }
            Overlay.None -> 0
        }
        val everywhere = overlay == Overlay.LandValue || overlay == Overlay.Power || overlay == Overlay.Traffic ||
            overlay == Overlay.Railway || overlay == Overlay.Water || overlay == Overlay.Runoff ||
            overlay == Overlay.Schooling || overlay == Overlay.Health || overlay == Overlay.Wealth || overlay == Overlay.Age ||
            overlay == Overlay.Heat || overlay == Overlay.Garbage || overlay == Overlay.Goods || overlay == Overlay.Junctions || overlay == Overlay.Reach || overlay == Overlay.LineLoad || overlay == Overlay.Visitors
        if (!everywhere && v == 0) continue
        pixels[i] = mix(overlay.low, overlay.high, v / 255f)
    }
    return imageBitmapOf(pixels, map.width, map.height)
}

/** Whether a storm drain runs within reach of tile [i]. */
private fun drained(map: CityMap, i: Int): Boolean {
    val x = i % map.width
    val y = i / map.width
    val r = Balance.PIPE_REACH
    for (ty in y - r..y + r) for (tx in x - r..x + r) {
        if (map.inside(tx, ty) && map.stormPipe[map.index(tx, ty)].toInt() != 0) return true
    }
    return false
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

/** Seconds of driving the Reach view runs green to red over. */
private const val QUARTER_HOUR = 900
