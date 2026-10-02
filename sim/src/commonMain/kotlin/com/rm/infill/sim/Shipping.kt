package com.rm.infill.sim

/** Where a port meets the water. */
object Port {
    /**
     * The side of a port of [type] at [x], [y] with water all the way along
     * it (a [Heading], north to west), or 0 if neither long side has.
     */
    fun waterSide(map: CityMap, type: BuildingType, x: Int, y: Int): Int {
        fun water(tx: Int, ty: Int) = map.inside(tx, ty) && map.terrain[map.index(tx, ty)] == Terrain.WATER
        if (type.width >= type.height) {
            if ((x until x + type.width).all { water(it, y - 1) }) return Heading.NORTH.toInt()
            if ((x until x + type.width).all { water(it, y + type.height) }) return Heading.SOUTH.toInt()
        } else {
            if ((y until y + type.height).all { water(x - 1, it) }) return Heading.WEST.toInt()
            if ((y until y + type.height).all { water(x + type.width, it) }) return Heading.EAST.toInt()
        }
        return 0
    }

    /** The water tile beside the middle of a port of [type] at [x], [y], where its ships tie up, or -1. */
    fun berth(map: CityMap, type: BuildingType, x: Int, y: Int): Int = when (waterSide(map, type, x, y)) {
        Heading.NORTH.toInt() -> map.index(x + type.width / 2, y - 1)
        Heading.SOUTH.toInt() -> map.index(x + type.width / 2, y + type.height)
        Heading.WEST.toInt() -> map.index(x - 1, y + type.height / 2)
        Heading.EAST.toInt() -> map.index(x + type.width, y + type.height / 2)
        else -> -1
    }

    fun berth(map: CityMap, b: Building) = berth(map, b.type, b.x, b.y)
}

/**
 * The water as ships see it: for each tile, how far it is from the open
 * water at the edge of the map and the next tile on the way there. Ships
 * keep to the middle of a channel where they can, and can't pass under a
 * low bridge or through anything standing in the water.
 */
internal class Shipping(private val map: CityMap) {
    /** The cost of the way out to the edge from each tile, -1 where no ship can get out. */
    val cost = IntArray(map.size) { -1 }

    /** The next tile on the way out, -1 at the edge or where there's no way. */
    val next = IntArray(map.size) { -1 }

    /** Works it all out again, with [blocked] tiles closed as well as everything that's in the way. */
    fun update(blocked: Set<Int> = emptySet()) {
        cost.fill(-1)
        next.fill(-1)
        val heap = MinHeap(map.size)
        for (i in 0 until map.size) {
            if (!passable(i) || i in blocked) continue
            val x = i % map.width
            val y = i / map.width
            if (x == 0 || y == 0 || x == map.width - 1 || y == map.height - 1) {
                cost[i] = 0
                heap.push(i, 0)
            }
        }
        while (heap.size > 0) {
            val d = heap.peekKey()
            val i = heap.pop()
            if (d > cost[i]) continue
            val x = i % map.width
            val y = i / map.width
            for (h in 1..4) {
                val nx = x + Heading.DX[h]
                val ny = y + Heading.DY[h]
                if (!map.inside(nx, ny)) continue
                val j = map.index(nx, ny)
                if (!passable(j) || j in blocked) continue
                val c = d + step(nx, ny)
                if (cost[j] < 0 || c < cost[j]) {
                    cost[j] = c
                    next[j] = i
                    heap.push(j, c)
                }
            }
        }
    }

    /** Whether a ship can get from [i] out to the edge. */
    fun reaches(i: Int) = i in 0 until map.size && cost[i] >= 0

    /** The tiles from the edge of the map in to [i], or an empty list if there's no way. */
    fun routeTo(i: Int): IntArray {
        if (!reaches(i)) return IntArray(0)
        val out = ArrayList<Int>()
        var at = i
        while (at >= 0) {
            out += at
            at = next[at]
        }
        out.reverse()
        return out.toIntArray()
    }

    /** Open water a ship can sail through: nothing standing in it and no bridge over it. */
    fun passable(i: Int): Boolean =
        map.terrain[i] == Terrain.WATER && map.building[i] == 0 && map.road[i] == Road.NONE && map.rail[i] == Rail.NONE

    /** What a step onto a tile costs: more beside the shore, so ships keep to the middle. */
    private fun step(x: Int, y: Int): Int {
        var land = 0
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (map.inside(nx, ny) && map.terrain[map.index(nx, ny)] != Terrain.WATER) land++
        }
        return Balance.SHIP_STEP + land * Balance.SHIP_SHORE
    }
}

/** A plain binary heap of tiles by cost, smallest first. */
internal class MinHeap(capacity: Int) {
    private var keys = IntArray(capacity)
    private var values = IntArray(capacity)
    var size = 0
        private set

    fun push(value: Int, key: Int) {
        if (size == keys.size) {
            keys = keys.copyOf(size * 2)
            values = values.copyOf(size * 2)
        }
        var k = size++
        while (k > 0) {
            val parent = (k - 1) / 2
            if (keys[parent] <= key) break
            keys[k] = keys[parent]
            values[k] = values[parent]
            k = parent
        }
        keys[k] = key
        values[k] = value
    }

    fun peekKey() = keys[0]

    fun pop(): Int {
        val top = values[0]
        size--
        if (size > 0) {
            val key = keys[size]
            val value = values[size]
            var k = 0
            while (true) {
                var c = 2 * k + 1
                if (c >= size) break
                if (c + 1 < size && keys[c + 1] < keys[c]) c++
                if (keys[c] >= key) break
                keys[k] = keys[c]
                values[k] = values[c]
                k = c
            }
            keys[k] = key
            values[k] = value
        }
        return top
    }
}

/** A port's ships last month, for drawing them: the way in from the edge, what kind they were, and how many. */
class ShipRoute(val tiles: IntArray, val kind: Int, val ships: Int) {
    companion object {
        // Kinds of ship.
        const val STEAMER = 0
        const val COLLIER = 1
        const val TANKER = 2
        const val LINER = 3
        const val CONTAINER = 4
        const val COUNT = 5
    }
}
