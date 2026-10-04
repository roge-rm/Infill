package com.rm.infill.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.rm.infill.sim.City
import com.rm.infill.sim.Generation
import com.rm.infill.sim.Ordinance
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * How hard each building with stacks is going, by the map index of its top
 * left tile: 1 to 100, plus [CLEAN] when its smoke is cleaned up. A station
 * by what it's making, works and the rest while they're open. Looks over
 * every building, so it's run under the town's lock.
 */
internal fun plumesOf(city: City): Map<Int, Int> {
    val out = HashMap<Int, Int>()
    val cleanTown = city.has(Ordinance.SMOKE_ABATEMENT) || city.has(Ordinance.CLEAN_AIR_ACT)
    for (b in city.allBuildings) {
        val t = b.type
        if (b.underway > 0 || b.burning > 0 || !BuildingSprites.smokes(t.ordinal)) continue
        val going = when {
            Generation.station(t) -> {
                val made = city.stationOutput(b)
                if (made <= 0) 0 else max(LEAST, min(100, (made.toLong() * 100 / Generation.capacity(t)).toInt()))
            }
            b.closedDays > 0 -> 0
            else -> 100
        }
        if (going > 0) out[city.map.index(b.x, b.y)] = going + if (b.scrubbed || cleanTown) CLEAN else 0
    }
    return out
}

/**
 * Smoke and steam rising from the stacks on screen, leaning with the wind and
 * thinning as it goes, [puffs] at a time over each. The renderer says which
 * buildings in each chunk have stacks; [going] says how hard each is working.
 */
internal fun DrawScope.drawPlumes(
    renderer: MapRenderer,
    map: com.rm.infill.sim.CityMap,
    camera: Camera,
    going: Map<Int, Int>,
    look: WeatherLook,
    time: Float,
    puffs: Int,
) {
    if (going.isEmpty() || puffs <= 0) return
    val chunk = MapRenderer.CHUNK
    val topLeft = camera.screenToTile(Offset.Zero, size)
    val bottomRight = camera.screenToTile(Offset(size.width, size.height), size)
    // Chunks further down too, as tall stacks there reach up onto the screen.
    val cx0 = max(0, floor(topLeft.x / chunk).toInt())
    val cy0 = max(0, floor(topLeft.y / chunk).toInt())
    val cx1 = min((map.width - 1) / chunk, floor(bottomRight.x / chunk).toInt())
    val cy1 = min((map.height - 1) / chunk, floor((bottomRight.y + MapRenderer.SPRITE_ROWS) / chunk).toInt())
    val t = camera.tilePx
    if (t < MIN_TILE_PX) return
    val windX = look.windX * look.wind
    val windY = look.windY * look.wind
    for (cy in cy0..cy1) for (cx in cx0..cx1) {
        val stacks = renderer.plumes(cx, cy)
        for (k in stacks.indices step 2) {
            val i = stacks[k]
            val g = going[i] ?: continue
            val strength = (g % CLEAN) / 100f
            val clean = g >= CLEAN
            val sprite = stacks[k + 1]
            val ax = i % map.width
            val ay = i / map.width
            val first = Atlas.plumeStart[sprite]
            for (p in first until first + Atlas.plumeCount[sprite]) {
                var kind = Atlas.plumes[p * 3]
                if (kind == SOOT && clean) kind = SMOKE
                val top = camera.tileToScreen(ax + Atlas.plumes[p * 3 + 1] / 32f, ay + Atlas.plumes[p * 3 + 2] / 32f, size)
                plume(top, kind, strength, t, time, i * 7 + p, puffs, windX, windY)
            }
        }
    }
}

/** One stack's puffs, from [top] on screen, each rising and growing over its life and fading out. */
private fun DrawScope.plume(top: Offset, kind: Int, strength: Float, t: Float, time: Float, seed: Int, puffs: Int, windX: Float, windY: Float) {
    val colour = COLOURS[kind]
    val cloud = kind == CLOUD
    val size = if (cloud) 2.2f else 1f
    val alpha = ALPHAS[kind] * (0.6f + 0.4f * strength)
    // Faster and higher the harder it's going.
    val rate = 0.16f + 0.1f * strength
    val height = t * (if (cloud) 2.6f else 2.2f) * (0.6f + 0.4f * strength)
    for (k in 0 until puffs) {
        val age = (time * rate + k / puffs.toFloat() + unit(seed)) % 1f
        val lean = age * age * t * 1.6f
        val wobble = (unit(seed + k * 13) - 0.5f) * t * 0.08f
        val r = t * size * (0.05f + age * 0.2f)
        val at = Offset(top.x + windX * lean + wobble, top.y - age * height + windY * lean * 0.5f - r * 0.5f)
        val a = alpha * (1f - age) * min(1f, age * 6f)
        // A shaded underside, so a puff keeps its shape over snow and pale roofs.
        drawCircle(SHADES[kind], r, Offset(at.x + r * 0.2f, at.y + r * 0.25f), alpha = a * 0.6f)
        drawCircle(colour, r * 0.9f, at, alpha = a)
    }
}

/** A steady number from 0 to 1 for a seed. */
private fun unit(seed: Int): Float = ((seed * 1103515245 + 12345) ushr 8 and 0xffff) / 65536f

/** The kinds of stack, as tools/gen_tiles.py writes them. */
private const val SOOT = 0
private const val SMOKE = 1
private const val CLOUD = 3

/** Added to how hard a building's going when its smoke is cleaned up, so soot shows grey. */
private const val CLEAN = 1000

/** The least a station that's making anything at all shows. */
private const val LEAST = 15

/** With tiles smaller than this on screen, in pixels, the puffs would be too small to see. */
private const val MIN_TILE_PX = 6f

private val COLOURS = arrayOf(Color(0xFF3E4044), Color(0xFF9EA2A8), Color(0xFFF2F4F6), Color(0xFFF4F6F8))
private val SHADES = arrayOf(Color(0xFF2A2C30), Color(0xFF6E737A), Color(0xFFA9B1BA), Color(0xFFA9B1BA))
private val ALPHAS = floatArrayOf(0.85f, 0.75f, 0.6f, 0.9f)
