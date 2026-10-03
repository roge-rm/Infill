package com.rm.infill.sound

import com.rm.infill.sim.Building
import com.rm.infill.sim.BuildingType
import com.rm.infill.sim.City
import com.rm.infill.sim.Generation
import com.rm.infill.sim.Precipitation
import com.rm.infill.sim.RoadType
import com.rm.infill.sim.Terrain
import com.rm.infill.sim.Zone
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The town's sound, from where the player's looking. What makes sound is
 * added up by chunk now and then ([survey]), and a few times a second
 * [frame] turns the chunks around the camera into the synth's held sounds,
 * each panned to where most of it is, and the odd one-shot: a horn, a tram's
 * bell, hammering on a building site, thunder.
 */
class TownSound(private val city: City) {
    /** What makes each kind of sound. */
    enum class Source { TRAFFIC, HIGHWAY, TRAMS, WORKS, SHOPS, TREES, SEA, LAKE, HARBOUR, SITES, FIRES, AIRPORT, JAMS }

    private val map = city.map
    private val across = (map.width + CHUNK - 1) / CHUNK
    private val down = (map.height + CHUNK - 1) / CHUNK
    private val levels = Array(Source.entries.size) { FloatArray(across * down) }
    private var openWater: BooleanArray? = null
    private var waterCount = -1

    /** Adds up what makes sound in each chunk. Once a game day or so is plenty. */
    fun survey() {
        for (l in levels) l.fill(0f)
        val m = map
        val open = openWater()
        fun add(s: Source, i: Int, amount: Float) {
            val c = (i % m.width) / CHUNK + (i / m.width) / CHUNK * across
            levels[s.ordinal][c] += amount
        }
        for (i in 0 until m.size) {
            val road = RoadType.of(m.road[i])
            if (road != null) {
                val v = city.roadVolume(i).toFloat()
                if (v > 0) add(if (road == RoadType.HIGHWAY) Source.HIGHWAY else Source.TRAFFIC, i, v)
                val jam = (m.congestion[i].toInt() and 0xff) - JAMMED
                if (jam > 0) add(Source.JAMS, i, jam.toFloat())
            }
            val trams = city.tramRiders(i)
            if (trams > 0) add(Source.TRAMS, i, trams.toFloat())
            when (m.terrain[i]) {
                Terrain.TREES -> add(Source.TREES, i, 1f)
                Terrain.WATER -> if (shore(i)) add(if (open[i]) Source.SEA else Source.LAKE, i, 1f)
                else -> {}
            }
        }
        for (b in city.allBuildings) {
            val i = m.index(b.x, b.y)
            val t = b.type
            when {
                b.burning > 0 -> add(Source.FIRES, i, b.burning.toFloat())
                b.underway > 0 -> add(Source.SITES, i, 1f)
                Generation.station(t) -> add(Source.WORKS, i, STATION_WORKS)
                t.zone == Zone.INDUSTRIAL -> add(Source.WORKS, i, t.capacity.toFloat())
                t.zone == Zone.COMMERCIAL -> add(Source.SHOPS, i, t.capacity.toFloat())
                t.zone == Zone.MIXED -> add(Source.SHOPS, i, t.jobs.toFloat())
                t.port -> add(Source.HARBOUR, i, 1f)
                t.airport -> add(Source.AIRPORT, i, t.airTier.toFloat())
                t == BuildingType.PARK -> add(Source.TREES, i, PARK_TREES)
            }
        }
    }

    /** A water tile beside land. */
    private fun shore(i: Int): Boolean {
        val m = map
        val x = i % m.width
        val y = i / m.width
        for ((dx, dy) in NEXT) {
            val nx = x + dx
            val ny = y + dy
            if (m.inside(nx, ny) && m.terrain[m.index(nx, ny)] != Terrain.WATER) return true
        }
        return false
    }

    /** Water that's part of a big body reaching the map's edge: the sea, more or less. Worked out again when the water changes. */
    private fun openWater(): BooleanArray {
        val m = map
        val count = m.terrain.count { it == Terrain.WATER }
        openWater?.let { if (count == waterCount) return it }
        waterCount = count
        val out = BooleanArray(m.size)
        val seen = BooleanArray(m.size)
        val queue = IntArray(m.size)
        for (start in 0 until m.size) {
            if (seen[start] || m.terrain[start] != Terrain.WATER) continue
            var head = 0
            var tail = 0
            var edge = false
            queue[tail++] = start
            seen[start] = true
            while (head < tail) {
                val i = queue[head++]
                val x = i % m.width
                val y = i / m.width
                if (x == 0 || y == 0 || x == m.width - 1 || y == m.height - 1) edge = true
                for ((dx, dy) in NEXT) {
                    val nx = x + dx
                    val ny = y + dy
                    if (!m.inside(nx, ny)) continue
                    val j = m.index(nx, ny)
                    if (seen[j] || m.terrain[j] != Terrain.WATER) continue
                    seen[j] = true
                    queue[tail++] = j
                }
            }
            if (edge && tail >= m.size / SEA_SHARE) for (k in 0 until tail) out[queue[k]] = true
        }
        openWater = out
        return out
    }

    /** Where the player's looking: the middle of the view and how far it reaches each way, in tiles, and how close in (0 far out, 1 right in). */
    class Listener(val x: Float, val y: Float, val halfWidth: Float, val halfHeight: Float, val closeness: Float)

    /** Something moving that makes its own sound, such as a train, at [x], [y] in tiles. */
    class Mover(val key: Int, val recipe: Int, val x: Float, val y: Float, val p1: Float, val p2: Float, val loudness: Float = 1f)

    /** A one-shot to fire now, or [delay] seconds from now. */
    class Shot(val recipe: Int, val params: FloatArray, val delay: Float = 0f)

    /** The held sounds this frame, laid out as the synth takes them, and the one-shots. */
    class Frame(capacity: Int = MAX_HELD) {
        var count = 0
        val keys = IntArray(capacity)
        val recipes = IntArray(capacity)
        val flags = IntArray(capacity)
        val params = FloatArray(capacity * SharedParams.COUNT)
        val shots = ArrayList<Shot>()

        /** How loud the sound with [key] is, or 0 if it isn't playing. */
        fun loudness(key: Int): Float {
            for (k in 0 until count) if (keys[k] == key) return params[k * SharedParams.COUNT + 0] * gain(k)
            return 0f
        }

        fun gain(k: Int): Float = params[k * SharedParams.COUNT + SharedParams.GAIN]

        internal fun clear() {
            count = 0
            params.fill(0f)
            shots.clear()
        }

        internal fun add(key: Int, recipe: Int, gain: Float, pan: Float, lowpass: Float, vararg p: Float, pitch: Float = 0f) {
            if (count >= keys.size || gain < QUIET) return
            keys[count] = key
            recipes[count] = recipe
            val o = count * SharedParams.COUNT
            for ((j, v) in p.withIndex()) params[o + j] = v
            params[o + SharedParams.GAIN] = gain
            params[o + SharedParams.PAN] = pan.coerceIn(-1f, 1f)
            params[o + SharedParams.LOWPASS] = lowpass
            params[o + SharedParams.PITCH] = pitch
            count++
        }
    }

    private val random = kotlin.random.Random(city.seed)
    private var flyover = 0f
    private var flyoverLength = 0f
    private var flyoverWay = 1f
    private var nextFlyover = 20f

    /** How much of [source] is heard and where: its loudness before scaling, and the pan of where most of it is. */
    private fun heard(source: Source, at: Listener, panOut: FloatArray): Float {
        val l = levels[source.ordinal]
        val reach = max(at.halfWidth, at.halfHeight).coerceAtLeast(4f)
        var total = 0f
        var pan = 0f
        // Only chunks within a few reaches count: the rest are too far off to hear.
        val cx0 = ((at.x - reach * 3) / CHUNK).toInt().coerceAtLeast(0)
        val cx1 = ((at.x + reach * 3) / CHUNK).toInt().coerceAtMost(across - 1)
        val cy0 = ((at.y - reach * 3) / CHUNK).toInt().coerceAtLeast(0)
        val cy1 = ((at.y + reach * 3) / CHUNK).toInt().coerceAtMost(down - 1)
        for (cy in cy0..cy1) for (cx in cx0..cx1) {
            val v = l[cx + cy * across]
            if (v <= 0f) continue
            val dx = (cx + 0.5f) * CHUNK - at.x
            val dy = (cy + 0.5f) * CHUNK - at.y
            val d = sqrt(dx * dx + dy * dy) / reach
            val w = v / (1f + d * d * 2f)
            total += w
            pan += w * (dx / max(1f, at.halfWidth)).coerceIn(-1f, 1f)
        }
        panOut[0] = if (total > 0f) pan / total * PAN_WIDTH else 0f
        // A wider view takes in more of the town, but all of it further off: about as loud either way.
        return total * CHUNK / reach
    }

    /** From nothing to nearly full as [amount] passes [ref]. */
    private fun level(amount: Float, ref: Float) = 1f - exp(-amount / ref)

    /**
     * This moment's sound into [out]: [hour] of the day, [dt] seconds since the
     * last frame for the one-shots' odds, and [movers] such as trains, which
     * the map works out as it draws them.
     */
    fun frame(at: Listener, hour: Float, dt: Float, movers: List<Mover>, out: Frame) {
        out.clear()
        val year = city.year
        val month = city.month
        val w = city.weather
        val near = at.closeness.coerceIn(0f, 1f)
        // Far out, the town is a blur and loses its top; close in, every part of it is clear.
        val detail = 0.45f + 0.55f * near
        val snow = w.precipitation == Precipitation.Snow
        val air = (3000f + 15000f * near) * if (snow) 0.35f else 1f
        val muffle = if (snow) 0.7f else 1f
        val day = daylight(hour)
        val pan = FloatArray(1)
        fun place(key: Int, recipe: Int, source: Source, ref: Float, scale: Float, vararg p: Float) {
            val amount = heard(source, at, pan)
            if (amount <= 0f) return
            val loud = level(amount, ref) * scale * detail * muffle
            out.add(key, recipe, loud, pan[0], air, 1f, *p)
        }

        // The streets: hooves and carts giving way to engines, then quieter electric cars.
        val motors = ((year - MOTORS_FROM) / (MOTORS_BY - MOTORS_FROM).toFloat()).coerceIn(0f, 1f)
        val electric = ((year - ELECTRIC_FROM) / (ELECTRIC_BY - ELECTRIC_FROM).toFloat()).coerceIn(0f, 1f)
        val busy = 0.3f + 0.7f * day
        place(KEY_TRAFFIC, Recipes.TRAFFIC, Source.TRAFFIC, TRAFFIC_REF, busy, motors, electric, 0f)
        place(KEY_HIGHWAY, Recipes.TRAFFIC, Source.HIGHWAY, HIGHWAY_REF, 0.5f + 0.5f * day, 1f, electric, 1f)
        place(KEY_TRAMS, Recipes.TRAM, Source.TRAMS, TRAMS_REF, busy)
        place(KEY_WORKS, Recipes.COMPLEX, Source.WORKS, WORKS_REF, 1f, 1f - day)
        place(KEY_SHOPS, Recipes.CROWD, Source.SHOPS, SHOPS_REF, 0.15f + 0.85f * day)
        place(KEY_HARBOUR, Recipes.PORT, Source.HARBOUR, HARBOUR_REF, 1f)
        place(KEY_SEA, Recipes.SURF, Source.SEA, SEA_REF, 1f)
        place(KEY_LAKE, Recipes.SEA, Source.LAKE, LAKE_REF, 0.4f, 0.1f, 0f)

        // Birds by day, a chorus at dawn in spring and summer; crickets on warm summer nights.
        val warm = month in 3..8
        if (day > 0.2f) {
            val dawn = if (warm && hour in 4.5f..8f) 1f - abs(hour - 6f) / 2f else 0f
            val season = if (warm) 1f else if (month in 2..9) 0.5f else 0.2f
            place(KEY_BIRDS, Recipes.BIRDS, Source.TREES, TREES_REF, BIRDS_LEVEL * day * season, (0.2f + 0.8f * dawn).coerceIn(0f, 1f))
        } else if (month in 5..8 && w.temperature >= CRICKETS_WARM) {
            place(KEY_CRICKETS, Recipes.CRICKETS, Source.TREES, TREES_REF, 1f - day)
        }

        // A fire burning, and the fire engines coming: a bell, then a siren.
        val fire = heard(Source.FIRES, at, pan)
        if (fire > 0f) {
            out.add(KEY_FIRE, Recipes.FIRE, level(fire, FIRE_REF) * detail, pan[0], air, 1f)
            val kind = when {
                year < SIREN_WAIL_FROM -> 0f
                year < SIREN_ELECTRONIC_FROM -> 1f
                else -> 2f
            }
            out.add(KEY_SIREN, Recipes.SIREN, 0.5f * detail, pan[0], air, 1f, kind)
        }

        // Aircraft from the airport passing over now and then.
        val airport = heard(Source.AIRPORT, at, pan)
        if (flyover > 0f) {
            flyover -= dt
            val u = 1f - flyover / flyoverLength
            val swell = 1f - abs(u * 2f - 1f)
            val jet = year >= JETS_FROM
            val pitch = 1.12f - 0.24f * u
            if (jet) out.add(KEY_PLANE, Recipes.JET, swell * swell * 0.8f * detail, flyoverWay * (u * 2f - 1f), air, 0.8f, 0.7f, 0.5f, pitch = pitch)
            else out.add(KEY_PLANE, Recipes.PROP, swell * swell * 0.8f * detail, flyoverWay * (u * 2f - 1f), air, 0.8f, 70f, 0.7f, pitch = pitch)
        } else if (airport > 0f) {
            nextFlyover -= dt
            if (nextFlyover <= 0f) {
                flyoverLength = 9f + 5f * random.nextFloat()
                flyover = flyoverLength
                flyoverWay = if (random.nextBoolean()) 1f else -1f
                nextFlyover = (25f + 50f * random.nextFloat()) / level(airport, AIRPORT_REF).coerceAtLeast(0.2f)
            }
        }

        // Trains and such, where the map has them.
        for (mv in movers) {
            val dx = mv.x - at.x
            val dy = mv.y - at.y
            val reach = max(at.halfWidth, at.halfHeight).coerceAtLeast(4f)
            val d = sqrt(dx * dx + dy * dy) / reach
            val loud = mv.loudness * detail * muffle / (1f + d * d * 2f)
            out.add(mv.key, mv.recipe, loud, (dx / max(1f, at.halfWidth)).coerceIn(-1f, 1f) * PAN_WIDTH, air, 1f, mv.p1, mv.p2)
        }

        // The weather, all round.
        val wind = (w.windSpeed / 85f).coerceIn(0f, 1f)
        out.add(KEY_WIND, Recipes.WIND, 0.15f + 0.85f * wind * wind, 0f, 0f, 1f, 0.3f + 0.7f * wind, wind)
        if (w.precipitation == Precipitation.Rain) out.add(KEY_RAIN, Recipes.RAIN, (w.intensity / 100f).coerceIn(0.1f, 1f), 0f, 0f, 1f, 0f)

        shots(at, dt, motors, day, out)
    }

    /** The odd one-shot, more often the more there is of what makes it. */
    private fun shots(at: Listener, dt: Float, motors: Float, day: Float, out: Frame) {
        val pan = FloatArray(1)
        val near = 0.45f + 0.55f * at.closeness.coerceIn(0f, 1f)
        fun chance(perSecond: Float) = random.nextFloat() < perSecond * dt
        fun spread(p: Float) = (p + (random.nextFloat() - 0.5f) * 0.6f).coerceIn(-1f, 1f)
        fun shot(recipe: Int, gain: Float, p: Float, vararg own: Float, delay: Float = 0f) {
            val params = FloatArray(SharedParams.COUNT)
            for ((j, v) in own.withIndex()) params[j] = v
            params[SharedParams.GAIN] = gain
            params[SharedParams.PAN] = p
            out.shots += Shot(recipe, params, delay)
        }
        // Horns where traffic's jammed: a bulb's honk in the early years, a car's or a truck's later.
        val jams = level(heard(Source.JAMS, at, pan), JAMS_REF)
        if (jams > 0f && chance(jams * HORNS_A_SECOND * (0.3f + 0.7f * day))) {
            val kind = if (motors < 0.5f) 0f else if (random.nextFloat() < 0.2f) 2f else 1f
            shot(Recipes.HORN, 0.5f * near, spread(pan[0]), kind, random.nextFloat())
        }
        val trams = level(heard(Source.TRAMS, at, pan), TRAMS_REF)
        if (trams > 0f && chance(trams * BELLS_A_SECOND)) shot(Recipes.BELL, 0.6f * near, spread(pan[0]), 0.7f + 0.3f * random.nextFloat())
        val harbour = level(heard(Source.HARBOUR, at, pan), HARBOUR_REF)
        if (harbour > 0f && chance(harbour * SHIP_HORNS_A_SECOND)) shot(Recipes.WHISTLE, 0.5f * near, spread(pan[0]), 2f, random.nextFloat())
        // Hammering on building sites by day: a few blows, wood or steel.
        val sites = level(heard(Source.SITES, at, pan), SITES_REF)
        if (sites > 0f && day > 0.5f && chance(sites * HAMMERING_A_SECOND)) {
            val steel = random.nextFloat() < 0.3f
            val p = spread(pan[0])
            val blows = 2 + random.nextInt(4)
            for (k in 0 until blows) {
                shot(Recipes.IMPACT, 0.5f * near, p, 0.4f + 0.3f * random.nextFloat(), if (steel) Materials.METAL.toFloat() else Materials.WOOD.toFloat(), 0.15f, delay = k * (0.35f + 0.1f * random.nextFloat()))
            }
        }
        // Thunder in a downpour.
        val w = city.weather
        if (w.precipitation == Precipitation.Rain && w.intensity >= THUNDER_RAIN && chance(THUNDER_A_SECOND)) {
            shot(Recipes.THUNDER, 1f, (random.nextFloat() - 0.5f) * 1.4f, random.nextFloat())
        }
    }

    companion object {
        /** Tiles a side of a chunk. */
        const val CHUNK = 8
        const val MAX_HELD = 32
        private const val QUIET = 0.01f
        private const val PAN_WIDTH = 0.8f
        private val NEXT = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        /** Open water is at least this share of the map, as one over. */
        private const val SEA_SHARE = 12
        private const val STATION_WORKS = 300f
        private const val PARK_TREES = 6f
        /** Congestion over this is a jam. */
        private const val JAMMED = 150

        // How much of each is heard as fairly loud.
        private const val TRAFFIC_REF = 600f
        private const val HIGHWAY_REF = 1500f
        private const val TRAMS_REF = 600f
        private const val WORKS_REF = 80f
        private const val SHOPS_REF = 40f
        private const val TREES_REF = 80f

        /** Birdsong cuts through the rest, so it's kept well under them. */
        private const val BIRDS_LEVEL = 0.4f
        private const val SEA_REF = 25f
        private const val LAKE_REF = 15f
        private const val HARBOUR_REF = 0.7f
        private const val FIRE_REF = 10f
        private const val AIRPORT_REF = 1f
        private const val SITES_REF = 4f
        private const val JAMS_REF = 200f

        // How often the one-shots come, a second, at their most.
        private const val HORNS_A_SECOND = 0.5f
        private const val BELLS_A_SECOND = 0.15f
        private const val SHIP_HORNS_A_SECOND = 0.02f
        private const val HAMMERING_A_SECOND = 0.25f
        private const val THUNDER_A_SECOND = 0.04f
        private const val THUNDER_RAIN = 70

        private const val MOTORS_FROM = 1905
        private const val MOTORS_BY = 1930
        private const val ELECTRIC_FROM = 2020
        private const val ELECTRIC_BY = 2045
        private const val SIREN_WAIL_FROM = 1925
        private const val SIREN_ELECTRONIC_FROM = 1970
        private const val JETS_FROM = 1958
        private const val CRICKETS_WARM = 14

        const val KEY_TRAFFIC = 1
        const val KEY_HIGHWAY = 2
        const val KEY_TRAMS = 3
        const val KEY_WORKS = 4
        const val KEY_SHOPS = 5
        const val KEY_HARBOUR = 6
        const val KEY_SEA = 7
        const val KEY_LAKE = 8
        const val KEY_BIRDS = 9
        const val KEY_CRICKETS = 10
        const val KEY_FIRE = 11
        const val KEY_SIREN = 12
        const val KEY_PLANE = 13
        const val KEY_WIND = 14
        const val KEY_RAIN = 15
        /** Trains and the like start here, a key each. */
        const val KEY_MOVERS = 100

        /** How light it is at [hour]: 0 at night to 1 by day, with dawn and dusk between. */
        fun daylight(hour: Float): Float = when {
            hour < 5f || hour >= 21f -> 0f
            hour < 7f -> (hour - 5f) / 2f
            hour < 19f -> 1f
            else -> (21f - hour) / 2f
        }
    }
}
