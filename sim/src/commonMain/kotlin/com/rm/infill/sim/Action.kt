package com.rm.infill.sim

/** Something the player does to the map. Every change goes through [City.plan] and [City.apply]. */
sealed interface Action {
    /**
     * A road of [type] along [tiles], given as map indices in order. One-way
     * roads run the way it was drawn. A two-wide road is drawn along its
     * right-hand carriageway, and stops at the first turn.
     */
    data class BuildRoad(val tiles: IntArray, val type: RoadType = RoadType.DIRT) : Action {
        override fun equals(other: Any?) = other is BuildRoad && type == other.type && tiles.contentEquals(other.tiles)
        override fun hashCode() = tiles.contentHashCode() * 31 + type.hashCode()
    }

    /** Zones every tile from [x0], [y0] to [x1], [y1] as [zone]. */
    data class PlaceZone(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val zone: Byte) : Action

    /** Clears roads, zones, power lines, buildings and trees from [x0], [y0] to [x1], [y1]. */
    data class Bulldoze(val x0: Int, val y0: Int, val x1: Int, val y1: Int) : Action

    /** A power line along [tiles], given as map indices in order. */
    data class BuildPowerLine(val tiles: IntArray) : Action {
        override fun equals(other: Any?) = other is BuildPowerLine && tiles.contentEquals(other.tiles)
        override fun hashCode() = tiles.contentHashCode()
    }

    /** Railway track along [tiles], given as map indices in order. */
    data class BuildRail(val tiles: IntArray) : Action {
        override fun equals(other: Any?) = other is BuildRail && tiles.contentEquals(other.tiles)
        override fun hashCode() = tiles.contentHashCode()
    }

    /** A building the player places, like a power station, with its top left corner at [x], [y]. */
    data class PlaceBuilding(val type: BuildingType, val x: Int, val y: Int) : Action

    /** Parks on every free tile from [x0], [y0] to [x1], [y1]. Trees stay and become part of them. */
    data class PlaceParks(val x0: Int, val y0: Int, val x1: Int, val y1: Int) : Action

    companion object {
        /**
         * The tiles of a road dragged from one tile to another: along one axis and
         * then the other, like an L. [acrossFirst] goes east or west first.
         */
        fun roadPath(map: CityMap, x0: Int, y0: Int, x1: Int, y1: Int, acrossFirst: Boolean): IntArray {
            val out = ArrayList<Int>()
            var x = x0
            var y = y0
            out += map.index(x, y)
            fun stepX() { while (x != x1) { x += if (x1 > x) 1 else -1; out += map.index(x, y) } }
            fun stepY() { while (y != y1) { y += if (y1 > y) 1 else -1; out += map.index(x, y) } }
            if (acrossFirst) { stepX(); stepY() } else { stepY(); stepX() }
            return out.toIntArray()
        }
    }
}

/** Why an action, an undo or a redo can't go ahead. */
enum class Problem { NotEnoughMoney, NothingToDo, Blocked, TownBuiltThere, NeedsTrack }

/**
 * What an action would do: its [cost], the tiles it [changes] and the ones it
 * has to leave alone because they're [blocked]. [problem] is set if it can't go
 * ahead at all.
 */
class Plan(val cost: Long, val changes: IntArray, val blocked: IntArray, val problem: Problem?) {
    val ok get() = problem == null
}

/** What things cost, in the dollars of 1900. */
object Prices {
    /** A road over water costs this many times as much. */
    const val BRIDGE = 6
    const val CLEAR_TREES = 5L
    const val ZONE = 5L
    const val REMOVE_ROAD = 2L
    const val POWER_LINE = 5L
    const val REMOVE_LINE = 1L
    const val DEMOLISH = 15L
    const val COAL_PLANT = 3_000L
    const val POLICE_STATION = 1_500L
    const val FIRE_STATION = 1_800L
    const val PARK = 60L
    const val RAIL = 40L
    const val REMOVE_RAIL = 3L
    const val STATION = 1_200L
    const val FREIGHT_YARD = 2_500L

    /** What it costs to put up a building the player places. */
    fun of(type: BuildingType): Long = when (type) {
        BuildingType.COAL_PLANT -> COAL_PLANT
        BuildingType.POLICE_STATION -> POLICE_STATION
        BuildingType.FIRE_STATION -> FIRE_STATION
        BuildingType.PARK -> PARK
        BuildingType.STATION, BuildingType.STATION_NS -> STATION
        BuildingType.FREIGHT_YARD, BuildingType.FREIGHT_YARD_NS -> FREIGHT_YARD
        else -> 0L
    }
}
