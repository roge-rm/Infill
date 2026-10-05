package com.rm.infill.sim

/**
 * What runs along one edge of a town, tile by tile from its top or left end:
 * the land, roads, track, lines and mains. A neighbour shows it past its own
 * edge, so you can see where to meet it.
 */
class Border(val length: Int) {
    val terrain = ByteArray(length)
    val road = ByteArray(length)
    val rail = ByteArray(length)
    val power = ByteArray(length)
    val water = ByteArray(length)
    val sewer = ByteArray(length)
    val phone = ByteArray(length)

    /** What drifts over: the pollution and noise along the edge, and how foul the water there is. */
    val pollution = ByteArray(length)
    val noise = ByteArray(length)
    val foul = ByteArray(length)

    private val layers get() = arrayOf(terrain, road, rail, power, water, sewer, phone)

    internal fun writeTo(w: SaveWriter) {
        for (l in layers) w.layer(l)
        for (l in arrayOf(pollution, noise, foul)) w.layer(l)
    }

    /** Read from a region file of [version]: what drifts over since version 7. */
    internal fun readFrom(r: SaveReader, version: Int) {
        for (l in layers) r.layer(l)
        if (version >= 7) for (l in arrayOf(pollution, noise, foul)) r.layer(l)
    }

    companion object {
        /** The edges: north, east, south and west. */
        const val NORTH = 0
        const val EAST = 1
        const val SOUTH = 2
        const val WEST = 3

        /** The edge facing [edge]: a town's north edge meets its northern neighbour's south. */
        fun facing(edge: Int) = (edge + 2) % 4

        /** The [k]th tile along [edge] of [map]. */
        fun tile(map: CityMap, edge: Int, k: Int): Int = when (edge) {
            NORTH -> map.index(k, 0)
            EAST -> map.index(map.width - 1, k)
            SOUTH -> map.index(k, map.height - 1)
            else -> map.index(0, k)
        }

        /** What runs along [edge] of [map]. */
        fun of(map: CityMap, edge: Int): Border {
            val length = if (edge == NORTH || edge == SOUTH) map.width else map.height
            val b = Border(length)
            for (k in 0 until length) {
                val i = tile(map, edge, k)
                b.terrain[k] = map.terrain[i]
                b.road[k] = map.road[i]
                b.rail[k] = map.rail[i]
                b.power[k] = map.power[i]
                b.water[k] = map.waterPipe[i]
                b.sewer[k] = map.sewerPipe[i]
                b.phone[k] = map.phone[i]
                b.pollution[k] = map.pollution[i]
                b.noise[k] = map.noise[i]
                b.foul[k] = map.foul[i]
            }
            return b
        }
    }
}

/**
 * A town in a region as it was last left: its name, the save it's kept in,
 * its date and people, its four borders, and a small picture of it for the
 * region map (by [Region.look]).
 */
class RegionTown(
    val name: String,
    val file: String,
    val year: Int,
    val month: Int,
    val population: Int,
    val borders: Array<Border>,
    val picture: ByteArray,
    /**
     * Its workers with no work, and its jobs no one's doing, as it was left,
     * less what neighbours have taken up since by the ledger.
     */
    var idle: Int = 0,
    var vacant: Int = 0,
    /** Shop jobs' worth of spending it's short of shops for, or has shops to spare for; loads of each good it has spare or is short of. */
    var shopsShort: Int = 0,
    var shopsSpare: Int = 0,
    val goodsSpare: IntArray = IntArray(Good.COUNT),
    val goodsShort: IntArray = IntArray(Good.COUNT),
    /**
     * Power in kilowatts and water in people's worth it has spare or is short
     * of, the room in its dumps and the garbage it can't take away, in tonnes,
     * and its smog.
     */
    var powerSpare: Int = 0,
    var powerShort: Int = 0,
    var waterSpare: Int = 0,
    var waterShort: Int = 0,
    var dumpRoom: Int = 0,
    var garbageShort: Int = 0,
    var smog: Int = 0,
)

/**
 * What a neighbour has along the shared edge, and what it has to spare for
 * this town: its workers with no work and jobs no one's doing, counting what
 * this town already takes by the ledger.
 */
class Neighbour(
    val border: Border,
    val idle: Int,
    val vacant: Int,
    val shopsShort: Int = 0,
    val shopsSpare: Int = 0,
    val goodsSpare: IntArray = IntArray(Good.COUNT),
    val goodsShort: IntArray = IntArray(Good.COUNT),
    val powerSpare: Int = 0,
    val powerShort: Int = 0,
    val waterSpare: Int = 0,
    val waterShort: Int = 0,
    val dumpRoom: Int = 0,
    val garbageShort: Int = 0,
    val smog: Int = 0,
)

/** What crosses between two towns in a region, kept in its [Ledger]. */
enum class Flow {
    /** People living in the first town working in the second. */
    COMMUTERS,

    /** Shop jobs' worth of the first town's spending in the second's shops. */
    SHOPPING,

    /** Kilowatts at the peak from the first town's stations to the second, water for so many people, and tonnes of garbage. */
    POWER,
    WATER,
    GARBAGE,
    ;

    companion object {
        /** Loads of good [g] from the first town to the second, as a kind in the [Ledger]. */
        fun goods(g: Int) = entries.size + g

        /** How many kinds there are. */
        val KINDS = entries.size + Good.COUNT
    }
}

/**
 * What crosses each border in a region, from one town to the next: one
 * amount each way for each [Flow], seen the same from both sides, so
 * nothing's counted twice. Each town sets the amounts on its own borders as
 * it's played, against what its neighbours had to spare.
 */
class Ledger {
    private val amounts = HashMap<Int, IntArray>()

    private fun key(from: Int, to: Int) = from * 256 + to

    fun get(from: Int, to: Int, flow: Flow): Int = get(from, to, flow.ordinal)

    fun set(from: Int, to: Int, flow: Flow, amount: Int) = set(from, to, flow.ordinal, amount)

    /** By kind: a [Flow]'s number, or [Flow.goods] for a good. */
    fun get(from: Int, to: Int, kind: Int): Int = amounts[key(from, to)]?.get(kind) ?: 0

    fun set(from: Int, to: Int, kind: Int, amount: Int) {
        amounts.getOrPut(key(from, to)) { IntArray(Flow.KINDS) }[kind] = amount
    }

    internal fun writeTo(w: SaveWriter) {
        val kept = amounts.filterValues { a -> a.any { it != 0 } }
        w.count(kept.size)
        w.count(Flow.KINDS)
        for ((k, a) in kept) {
            w.int(k)
            for (v in a) w.int(v)
        }
    }

    internal fun readFrom(r: SaveReader) {
        val entries = r.count()
        val n = r.count()
        // An older ledger had fewer flows before its goods: commuters alone, then commuters and shopping.
        val flows = if (n <= 1) n else n - Good.COUNT
        repeat(entries) {
            val k = r.int()
            val a = IntArray(Flow.KINDS)
            for (f in 0 until n) {
                val v = r.int()
                val kind = if (f < flows) f else Flow.goods(f - flows)
                if (kind < a.size) a[kind] = v
            }
            amounts[k] = a
        }
    }
}

/**
 * A region: [size] by [size] squares, each with room for a town of [side] by
 * [side] tiles, both chosen when it's made. Its land is made in one piece and
 * cut up, so lakes, rivers, coast and woods carry on across the borders. Only
 * the town being played moves on; the others stay as they were left.
 */
class Region(val name: String, val seed: Long, val land: TerrainOptions, val size: Int = 3, val side: Int = City.DEFAULT_SIZE) {
    val towns = arrayOfNulls<RegionTown>(size * size)
    val ledger = Ledger()

    private var whole: CityMap? = null

    /** The region's land, made once. */
    fun whole(): CityMap = whole ?: CityMap(size * side, size * side).also {
        TerrainGen.generate(it, seed, land, size)
        whole = it
    }

    /** A new town founded on [square], named [townName], in the region kept in [regionFile]: its land cut from the region's. */
    fun found(square: Int, townName: String, regionFile: String): City {
        val c = City(seed + 7919L * (square + 1), side, side, land)
        c.name = townName
        val all = whole()
        val x0 = (square % size) * side
        val y0 = (square / size) * side
        for (y in 0 until side) for (x in 0 until side) {
            val i = c.map.index(x, y)
            val j = all.index(x0 + x, y0 + y)
            c.map.terrain[i] = all.terrain[j]
            c.map.resource[i] = all.resource[j]
        }
        c.region = regionFile
        c.square = square
        c.landChanged()
        return c
    }

    /** The square next to [square] past [edge], or -1 off the region. */
    fun neighbour(square: Int, edge: Int): Int {
        val x = square % size + if (edge == Border.EAST) 1 else if (edge == Border.WEST) -1 else 0
        val y = square / size + if (edge == Border.SOUTH) 1 else if (edge == Border.NORTH) -1 else 0
        return if (x in 0 until size && y in 0 until size) y * size + x else -1
    }

    /** [city]'s neighbours on each edge, as they were left: what they have along the shared edge, and to spare. */
    fun neighboursOf(city: City): Array<Neighbour?> = Array(4) { edge ->
        val n = neighbour(city.square, edge)
        val t = if (n < 0) null else towns[n]
        // What it has to spare, plus what this town already takes of it, since that's this town's to decide again.
        val sq = city.square
        if (t == null) null
        else Neighbour(
            t.borders[Border.facing(edge)],
            t.idle + ledger.get(n, sq, Flow.COMMUTERS),
            t.vacant + ledger.get(sq, n, Flow.COMMUTERS),
            t.shopsShort + ledger.get(n, sq, Flow.SHOPPING),
            t.shopsSpare + ledger.get(sq, n, Flow.SHOPPING),
            IntArray(Good.COUNT) { g -> t.goodsSpare[g] + ledger.get(n, sq, Flow.goods(g)) },
            IntArray(Good.COUNT) { g -> t.goodsShort[g] + ledger.get(sq, n, Flow.goods(g)) },
            t.powerSpare + ledger.get(n, sq, Flow.POWER),
            t.powerShort + ledger.get(sq, n, Flow.POWER),
            t.waterSpare + ledger.get(n, sq, Flow.WATER),
            t.waterShort + ledger.get(sq, n, Flow.WATER),
            t.dumpRoom + ledger.get(sq, n, Flow.GARBAGE),
            t.garbageShort + ledger.get(n, sq, Flow.GARBAGE),
            t.smog,
        )
    }

    /** What [city]'s neighbours have along the edges they share with it, as they were left. */
    fun bordersOf(city: City): Array<Border?> = neighboursOf(city).map { it?.border }.toTypedArray()

    /** The file a town on [square] of the region kept in [regionFile] is saved in. */
    fun townFile(regionFile: String, square: Int) = "$regionFile-$square"

    /**
     * Notes down [city], kept in [file], as it is now, for the region map and
     * its neighbours: what crosses each of its borders goes in the ledger, and
     * each neighbour's spare is put right for what's changed.
     */
    fun record(city: City, file: String) {
        val sq = city.square
        for (edge in 0 until 4) {
            val n = neighbour(sq, edge)
            val t = if (n < 0) null else towns[n]
            if (t == null) continue
            val out = city.edgeOut[edge]
            val inn = city.edgeIn[edge]
            t.vacant = (t.vacant + ledger.get(sq, n, Flow.COMMUTERS) - out).coerceAtLeast(0)
            t.idle = (t.idle + ledger.get(n, sq, Flow.COMMUTERS) - inn).coerceAtLeast(0)
            ledger.set(sq, n, Flow.COMMUTERS, out)
            ledger.set(n, sq, Flow.COMMUTERS, inn)
            // Shopping and goods the same way: what this town takes up comes off the neighbour's spare.
            val shopOut = city.edgeShopOut[edge]
            val shopIn = city.edgeShopIn[edge]
            t.shopsSpare = (t.shopsSpare + ledger.get(sq, n, Flow.SHOPPING) - shopOut).coerceAtLeast(0)
            t.shopsShort = (t.shopsShort + ledger.get(n, sq, Flow.SHOPPING) - shopIn).coerceAtLeast(0)
            ledger.set(sq, n, Flow.SHOPPING, shopOut)
            ledger.set(n, sq, Flow.SHOPPING, shopIn)
            for (g in 0 until Good.COUNT) {
                val gOut = city.edgeGoodsOut[edge][g]
                val gIn = city.edgeGoodsIn[edge][g]
                t.goodsShort[g] = (t.goodsShort[g] + ledger.get(sq, n, Flow.goods(g)) - gOut).coerceAtLeast(0)
                t.goodsSpare[g] = (t.goodsSpare[g] + ledger.get(n, sq, Flow.goods(g)) - gIn).coerceAtLeast(0)
                ledger.set(sq, n, Flow.goods(g), gOut)
                ledger.set(n, sq, Flow.goods(g), gIn)
            }
            // Power and water sold one way or the other, and garbage sent to the other's dumps.
            val pOut = city.edgePowerOut[edge]
            val pIn = city.edgePowerIn[edge]
            t.powerShort = (t.powerShort + ledger.get(sq, n, Flow.POWER) - pOut).coerceAtLeast(0)
            t.powerSpare = (t.powerSpare + ledger.get(n, sq, Flow.POWER) - pIn).coerceAtLeast(0)
            ledger.set(sq, n, Flow.POWER, pOut)
            ledger.set(n, sq, Flow.POWER, pIn)
            val wOut = city.edgeWaterOut[edge]
            val wIn = city.edgeWaterIn[edge]
            t.waterShort = (t.waterShort + ledger.get(sq, n, Flow.WATER) - wOut).coerceAtLeast(0)
            t.waterSpare = (t.waterSpare + ledger.get(n, sq, Flow.WATER) - wIn).coerceAtLeast(0)
            ledger.set(sq, n, Flow.WATER, wOut)
            ledger.set(n, sq, Flow.WATER, wIn)
            val gaOut = city.edgeGarbageOut[edge]
            val gaIn = city.edgeGarbageIn[edge]
            t.dumpRoom = (t.dumpRoom + ledger.get(sq, n, Flow.GARBAGE) - gaOut).coerceAtLeast(0)
            t.garbageShort = (t.garbageShort + ledger.get(n, sq, Flow.GARBAGE) - gaIn).coerceAtLeast(0)
            ledger.set(sq, n, Flow.GARBAGE, gaOut)
            ledger.set(n, sq, Flow.GARBAGE, gaIn)
        }
        towns[city.square] = RegionTown(
            city.name, file, city.year, city.month, city.stats.population,
            Array(4) { Border.of(city.map, it) }, look(city.map), city.stats.idle, city.stats.vacant,
            city.stats.shopsShort, city.stats.shopsSpare,
            IntArray(Good.COUNT) { (city.stats.goodsExported[it] - city.stats.toNeighbours[it]).coerceAtLeast(0) },
            IntArray(Good.COUNT) { (city.stats.goodsImported[it] - city.stats.fromNeighbours[it]).coerceAtLeast(0) },
            city.spare.power, city.short.power, city.spare.water, city.short.water, city.spare.garbage, city.short.garbage, city.stats.smog,
        )
    }

    fun write(): ByteArray {
        val w = SaveWriter()
        w.int(MAGIC)
        w.int(VERSION)
        w.string(name)
        w.long(seed)
        w.int(land.water); w.int(land.trees); w.bool(land.river); w.bool(land.quakes); w.int(land.climate.ordinal)
        // Since version 2: the grid and the size of a town; since 3, the sea.
        w.int(size); w.int(side)
        w.int(land.sea.ordinal)
        // Since version 8: the sides chosen for the sea.
        w.int(land.seaSides)
        for (t in towns) {
            w.bool(t != null)
            if (t == null) continue
            w.string(t.name); w.string(t.file); w.int(t.year); w.int(t.month); w.int(t.population)
            for (b in t.borders) b.writeTo(w)
            w.layer(t.picture)
            // Since version 4: its workers and jobs to spare; since 6, shops and goods.
            w.int(t.idle); w.int(t.vacant)
            w.int(t.shopsShort); w.int(t.shopsSpare)
            w.count(Good.COUNT)
            for (g in 0 until Good.COUNT) { w.int(t.goodsSpare[g]); w.int(t.goodsShort[g]) }
            // Since version 7: power, water, dumps and smog.
            for (v in intArrayOf(t.powerSpare, t.powerShort, t.waterSpare, t.waterShort, t.dumpRoom, t.garbageShort, t.smog)) w.int(v)
        }
        // Since version 5: the ledger.
        ledger.writeTo(w)
        return w.bytes()
    }

    companion object {
        /** The grids a region can be, and the sizes of a town, in tiles a side, to choose from. */
        val GRIDS = listOf(2, 3, 4)
        val SIDES = listOf(64, 96, 128, 192, 256)

        private const val MAGIC = 0x494E5247 // "INRG"
        private const val VERSION = 8

        fun read(bytes: ByteArray): Region {
            val r = SaveReader(bytes)
            try {
                if (r.int() != MAGIC) throw SaveError("not a region")
                val version = r.int()
                if (version > VERSION) throw SaveError("saved by a newer version")
                val name = r.string()
                val seed = r.long()
                val land = TerrainOptions(r.int(), r.int(), r.bool(), r.bool(), Climate.entries.getOrElse(r.int()) { Climate.TEMPERATE })
                val grid = if (version >= 2) r.int() else 3
                val side = if (version >= 2) r.int() else City.DEFAULT_SIZE
                val sea = if (version >= 3) Sea.entries.getOrElse(r.int()) { Sea.NONE } else Sea.NONE
                if (grid !in GRIDS || side !in SIDES) throw SaveError("a region of a size it can't be")
                val sides = if (version >= 8) r.int() else 0
                val region = Region(name, seed, land.copy(sea = sea, seaSides = sides), grid, side)
                for (k in region.towns.indices) {
                    if (!r.bool()) continue
                    val townName = r.string(); val file = r.string(); val year = r.int(); val month = r.int(); val people = r.int()
                    val borders = Array(4) { Border(side).also { b -> b.readFrom(r, version) } }
                    val picture = ByteArray(side * side).also { r.layer(it) }
                    val idle = if (version >= 4) r.int() else 0
                    val vacant = if (version >= 4) r.int() else 0
                    val t = RegionTown(townName, file, year, month, people, borders, picture, idle, vacant)
                    if (version >= 6) {
                        t.shopsShort = r.int(); t.shopsSpare = r.int()
                        repeat(r.count()) { g ->
                            val spare = r.int()
                            val short = r.int()
                            if (g < Good.COUNT) { t.goodsSpare[g] = spare; t.goodsShort[g] = short }
                        }
                    }
                    if (version >= 7) {
                        t.powerSpare = r.int(); t.powerShort = r.int(); t.waterSpare = r.int(); t.waterShort = r.int()
                        t.dumpRoom = r.int(); t.garbageShort = r.int(); t.smog = r.int()
                    }
                    region.towns[k] = t
                }
                if (version >= 5) region.ledger.readFrom(r)
                return region
            } catch (e: IndexOutOfBoundsException) {
                throw SaveError("the file is cut short")
            }
        }

        /** Whether [bytes] are a region rather than a town. */
        fun isRegion(bytes: ByteArray): Boolean = bytes.size >= 4 && SaveReader(bytes).int() == MAGIC

        /** What a tile looks like on the region map: water, woods, open land, road, track, or built on or zoned for each zone. */
        const val LOOK_LAND: Byte = 0
        const val LOOK_WATER: Byte = 1
        const val LOOK_TREES: Byte = 2
        const val LOOK_ROAD: Byte = 3
        const val LOOK_RAIL: Byte = 4

        /** Zoned for a zone: this plus the zone; built on, this plus the zone. */
        const val LOOK_ZONED: Byte = 10
        const val LOOK_BUILT: Byte = 20

        /** A picture of [map] for the region map, a byte a tile. */
        fun look(map: CityMap): ByteArray = ByteArray(map.size) { i ->
            when {
                map.road[i] != Road.NONE -> LOOK_ROAD
                map.rail[i] != Rail.NONE -> LOOK_RAIL
                map.building[i] != 0 -> (LOOK_BUILT + map.zone[i]).toByte()
                map.zone[i] != Zone.NONE -> (LOOK_ZONED + map.zone[i]).toByte()
                map.terrain[i] == Terrain.WATER -> LOOK_WATER
                map.terrain[i] == Terrain.TREES -> LOOK_TREES
                else -> LOOK_LAND
            }
        }
    }
}
