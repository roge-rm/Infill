package com.rm.infill.sim

/**
 * Every kind of road. [id] is what's stored on the map, [price] what a tile
 * costs to build and [upkeep] a month. [time] is the seconds it takes to
 * cross a tile when the road is clear, and [capacity] the trips a month a
 * tile carries before it slows down. [width] 2 roads are two one-way
 * carriageways side by side. [year] is when it can be built, once eras hold
 * roads back.
 */
enum class RoadType(
    val id: Byte,
    val price: Long,
    val upkeep: Double,
    val time: Int,
    val capacity: Int,
    val oneWay: Boolean = false,
    val width: Int = 1,
    val year: Int = 1900,
    val bridges: Boolean = true,
    /** How many years its surface is expected to last. */
    val life: Int = 25,
) {
    DIRT(1, 10, 0.4, 40, 300, life = 10),
    GRAVEL(2, 16, 0.5, 30, 440, life = 15),
    LANE(3, 6, 0.2, 45, 120, bridges = false, life = 15),
    STREET(4, 30, 0.8, 20, 800),
    ONE_WAY_STREET(5, 30, 0.8, 18, 900, oneWay = true),
    AVENUE(6, 60, 1.6, 15, 1600, year = 1910),
    ONE_WAY_AVENUE(7, 60, 1.6, 13, 1800, oneWay = true, year = 1910),
    BOULEVARD(8, 45, 1.0, 12, 1400, oneWay = true, width = 2, year = 1920, life = 30),
    ;

    companion object {
        private val byId = arrayOfNulls<RoadType>(16).also { a -> entries.forEach { a[it.id.toInt()] = it } }

        fun of(id: Byte): RoadType? = if (id <= 0) null else byId.getOrNull(id.toInt())
    }
}

/** The road on a tile, if any, by its [RoadType.id]. */
object Road {
    const val NONE: Byte = 0
    const val DIRT: Byte = 1
}

/** Which way a one-way road runs, stored per tile: 0 both ways, then north, east, south, west. */
object Heading {
    const val BOTH: Byte = 0
    const val NORTH: Byte = 1
    const val EAST: Byte = 2
    const val SOUTH: Byte = 3
    const val WEST: Byte = 4

    val DX = intArrayOf(0, 0, 1, 0, -1)
    val DY = intArrayOf(0, -1, 0, 1, 0)

    fun opposite(h: Int) = if (h == 0) 0 else (h + 1) % 4 + 1

    /** The heading of a step from one tile to the next. */
    fun of(dx: Int, dy: Int): Byte = when {
        dy < 0 -> NORTH
        dx > 0 -> EAST
        dy > 0 -> SOUTH
        dx < 0 -> WEST
        else -> BOTH
    }
}
