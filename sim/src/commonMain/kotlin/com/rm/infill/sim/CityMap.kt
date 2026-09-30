package com.rm.infill.sim

/**
 * The map as a set of layers, one array per layer, indexed by
 * `y * width + x`. Every layer the simulation adds lives here as another
 * array so the whole map can be saved and hashed in one pass.
 */
class CityMap(val width: Int, val height: Int) {
    val size = width * height

    val terrain = ByteArray(size)
    val road = ByteArray(size)

    /** Which way traffic runs on a one-way road tile ([Heading]), 0 on a road both ways. */
    val roadHeading = ByteArray(size)
    val zone = ByteArray(size)
    val power = ByteArray(size)

    /** The id of the building on each tile, 0 for none. */
    val building = IntArray(size)

    /**
     * The building's type (its ordinal plus one, 0 for none) and variant on each
     * tile, kept in step with the city's buildings. The map is drawn from these,
     * on another thread, so it never has to look at the buildings themselves.
     */
    val buildingType = ByteArray(size)
    val buildingVariant = ByteArray(size)

    /** Pollution, 0 to 255, worked out each month. Not saved; it follows from the buildings. */
    val pollution = ByteArray(size)

    /**
     * Soot and dirt left by pollution, 0 to 255. It follows the pollution slowly,
     * up over months and down over years, and it's what shows on the map.
     */
    val grime = ByteArray(size)

    /** How grimy a tile looks, 0 to 3. The map changes only when this does. */
    fun grimeLevel(i: Int): Int = (grime[i].toInt() and 0xff) / 64

    /** What land is worth, 0 to 255, worked out each month. */
    val landValue = ByteArray(size)

    /** Crime, 0 to 255, worked out each month. */
    val crime = ByteArray(size)

    /** How well police and fire stations reach each tile, 0 to 255. */
    val policeCover = ByteArray(size)
    val fireCover = ByteArray(size)

    /** Days left burning on each tile of a building on fire, 0 for none. The map draws the flames from it. */
    val fire = ByteArray(size)

    /** How full each road tile was last month: 128 is at capacity, 255 twice it or more. */
    val congestion = ByteArray(size)

    /**
     * How long the commute is from each home, 1 to 254 in steps of half a
     * minute, 255 if no job could be reached, 0 if there's no home here.
     */
    val commute = ByteArray(size)

    /** Whether each tile has power, worked out whenever power lines or buildings change. */
    val powered = BooleanArray(size)

    fun index(x: Int, y: Int) = y * width + x

    fun inside(x: Int, y: Int) = x in 0 until width && y in 0 until height

    fun terrainAt(x: Int, y: Int): Byte = terrain[index(x, y)]
    fun roadAt(x: Int, y: Int): Byte = road[index(x, y)]
    fun zoneAt(x: Int, y: Int): Byte = zone[index(x, y)]
    fun powerAt(x: Int, y: Int): Byte = power[index(x, y)]
    fun buildingAt(x: Int, y: Int): Int = building[index(x, y)]

    /** Everything on a tile that the player or the town can change, packed into one number for undo. */
    fun tileState(i: Int): Long =
        (terrain[i].toLong() and 0xff) or ((road[i].toLong() and 0xff) shl 8) or ((zone[i].toLong() and 0xff) shl 16) or
            ((power[i].toLong() and 0xff) shl 24) or ((roadHeading[i].toLong() and 0x0f) shl 28) or (building[i].toLong() shl 32)

    fun setTileState(i: Int, state: Long) {
        terrain[i] = state.toByte()
        road[i] = (state shr 8).toByte()
        zone[i] = (state shr 16).toByte()
        power[i] = ((state shr 24) and 0x0f).toByte()
        roadHeading[i] = ((state shr 28) and 0x0f).toByte()
        building[i] = (state ushr 32).toInt()
    }

    /** FNV-1a over every layer. Two maps with the same hash are the same map. */
    fun hash(): Long {
        var h = FNV_OFFSET
        h = mix(h, width.toLong())
        h = mix(h, height.toLong())
        for (layer in arrayOf(terrain, road, roadHeading, zone, power, grime, fire)) for (b in layer) h = mix(h, b.toLong())
        for (b in building) h = mix(mix(h, b.toLong()), (b ushr 8).toLong())
        return h
    }

    private fun mix(h: Long, v: Long): Long = (h xor (v and 0xff)) * FNV_PRIME

    private companion object {
        const val FNV_OFFSET = -0x340d631b7bdddcdbL
        const val FNV_PRIME = 0x100000001b3L
    }
}
