package com.rm.infill.sim

/** Something the player does to the map. Every change goes through [City.plan] and [City.apply]. */
sealed interface Action {
    /** A road along [tiles], given as map indices in order. */
    data class BuildRoad(val tiles: IntArray) : Action {
        override fun equals(other: Any?) = other is BuildRoad && tiles.contentEquals(other.tiles)
        override fun hashCode() = tiles.contentHashCode()
    }

    /** Zones every tile from [x0], [y0] to [x1], [y1] as [zone]. */
    data class PlaceZone(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val zone: Byte) : Action

    /** Clears roads, zones and trees from [x0], [y0] to [x1], [y1]. */
    data class Bulldoze(val x0: Int, val y0: Int, val x1: Int, val y1: Int) : Action

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

/** Why an action can't go ahead. */
enum class Problem { NotEnoughMoney, NothingToDo }

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
    const val DIRT_ROAD = 10L
    const val CLEAR_TREES = 5L
    const val ZONE = 5L
    const val REMOVE_ROAD = 2L
}
