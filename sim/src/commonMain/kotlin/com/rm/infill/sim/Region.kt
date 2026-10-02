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

    private val layers get() = arrayOf(terrain, road, rail, power, water, sewer, phone)

    internal fun writeTo(w: SaveWriter) {
        for (l in layers) w.layer(l)
    }

    internal fun readFrom(r: SaveReader) {
        for (l in layers) r.layer(l)
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
    /** Its workers with no work, and its jobs no one's doing, as it was left: what a neighbour's commuters can take up. */
    val idle: Int = 0,
    val vacant: Int = 0,
)

/** What a neighbour has along the shared edge, and the workers and jobs it has to spare. */
class Neighbour(val border: Border, val idle: Int, val vacant: Int)

/**
 * A region: [size] by [size] squares, each with room for a town of [side] by
 * [side] tiles, both chosen when it's made. Its land is made in one piece and
 * cut up, so lakes, rivers, coast and woods carry on across the borders. Only
 * the town being played moves on; the others stay as they were left.
 */
class Region(val name: String, val seed: Long, val land: TerrainOptions, val size: Int = 3, val side: Int = City.DEFAULT_SIZE) {
    val towns = arrayOfNulls<RegionTown>(size * size)

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
        if (t == null) null else Neighbour(t.borders[Border.facing(edge)], t.idle, t.vacant)
    }

    /** What [city]'s neighbours have along the edges they share with it, as they were left. */
    fun bordersOf(city: City): Array<Border?> = neighboursOf(city).map { it?.border }.toTypedArray()

    /** The file a town on [square] of the region kept in [regionFile] is saved in. */
    fun townFile(regionFile: String, square: Int) = "$regionFile-$square"

    /** Notes down [city], kept in [file], as it is now, for the region map and its neighbours. */
    fun record(city: City, file: String) {
        towns[city.square] = RegionTown(
            city.name, file, city.year, city.month, city.stats.population,
            Array(4) { Border.of(city.map, it) }, look(city.map), city.stats.idle, city.stats.vacant,
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
        for (t in towns) {
            w.bool(t != null)
            if (t == null) continue
            w.string(t.name); w.string(t.file); w.int(t.year); w.int(t.month); w.int(t.population)
            for (b in t.borders) b.writeTo(w)
            w.layer(t.picture)
            // Since version 4: its workers and jobs to spare.
            w.int(t.idle); w.int(t.vacant)
        }
        return w.bytes()
    }

    companion object {
        /** The grids a region can be, and the sizes of a town, in tiles a side, to choose from. */
        val GRIDS = listOf(2, 3, 4)
        val SIDES = listOf(64, 96, 128, 192, 256)

        private const val MAGIC = 0x494E5247 // "INRG"
        private const val VERSION = 4

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
                val region = Region(name, seed, land.copy(sea = sea), grid, side)
                for (k in region.towns.indices) {
                    if (!r.bool()) continue
                    val townName = r.string(); val file = r.string(); val year = r.int(); val month = r.int(); val people = r.int()
                    val borders = Array(4) { Border(side).also { b -> b.readFrom(r) } }
                    val picture = ByteArray(side * side).also { r.layer(it) }
                    val idle = if (version >= 4) r.int() else 0
                    val vacant = if (version >= 4) r.int() else 0
                    region.towns[k] = RegionTown(townName, file, year, month, people, borders, picture, idle, vacant)
                }
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
