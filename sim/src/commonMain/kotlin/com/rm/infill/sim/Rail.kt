package com.rm.infill.sim

/** The railway on a tile, if any. */
object Rail {
    const val NONE: Byte = 0
    const val TRACK: Byte = 1

    /**
     * The side of a station or freight yard at [x], [y] that has track all the
     * way along it (a [Heading], north to west), or 0 if neither long side does.
     */
    fun trackSide(map: CityMap, type: BuildingType, x: Int, y: Int): Int {
        fun track(tx: Int, ty: Int) = map.inside(tx, ty) && map.rail[map.index(tx, ty)] == TRACK
        if (type.width >= type.height) {
            if ((x until x + type.width).all { track(it, y - 1) }) return Heading.NORTH.toInt()
            if ((x until x + type.width).all { track(it, y + type.height) }) return Heading.SOUTH.toInt()
        } else {
            if ((y until y + type.height).all { track(x - 1, it) }) return Heading.WEST.toInt()
            if ((y until y + type.height).all { track(x + type.width, it) }) return Heading.EAST.toInt()
        }
        return 0
    }

    /** The track tile beside the middle of a station or yard, where its trains stop. */
    fun stop(map: CityMap, b: Building): Int {
        val t = b.type
        return when (trackSide(map, t, b.x, b.y)) {
            Heading.NORTH.toInt() -> map.index(b.x + t.width / 2, b.y - 1)
            Heading.SOUTH.toInt() -> map.index(b.x + t.width / 2, b.y + t.height)
            Heading.WEST.toInt() -> map.index(b.x - 1, b.y + t.height / 2)
            Heading.EAST.toInt() -> map.index(b.x + t.width, b.y + t.height / 2)
            else -> -1
        }
    }
}

/**
 * The railway as the trains see it: which track joins up, which stops are on
 * each line, and whether a line reaches the edge of the map. Worked out
 * again whenever track or buildings change.
 */
internal class RailNetwork(private val map: CityMap) {
    /** Which line each track tile is on, -1 for none; the track in tunnels from [CityMap.size] up. */
    val line = IntArray(2 * map.size) { -1 }

    /** Lines with track at the edge of the map, so trains come and go from outside. */
    var linked = BooleanArray(0)
        private set

    /** The track tile trains stop at for each stop, and the stop's building. */
    var stops = IntArray(0)
        private set
    var buildings = emptyList<Building>()
        private set

    /** Seconds by train between each pair of stops, -1 if no line joins them. */
    var times = emptyArray<IntArray>()
        private set

    /** For each stop, the steps to the nearest track at the edge of the map, or -1. */
    var toEdge = IntArray(0)
        private set

    /** Track under deep floodwater is closed until it drains, and broken track until it's mended. */
    private fun deep(i: Int) = (map.flood[i].toInt() and 0xff) >= Balance.FLOOD_DAMAGE || map.out(i, Broken.RAIL) ||
        map.terrain[i] == Terrain.WATER && map.bridgeClosed(i)

    private val n = map.size

    /**
     * The track as places a train can be: each tile's track on the surface,
     * and from [n] up the track in a tunnel under it, the two joined at the
     * portals. Calls [visit] with each place a step on from [node].
     */
    private inline fun next(node: Int, visit: (Int) -> Unit) {
        val low = node >= n
        val i = if (low) node - n else node
        val x = i % map.width
        val y = i / map.width
        for (h in 1..4) {
            val nx = x + Heading.DX[h]
            val ny = y + Heading.DY[h]
            if (!map.inside(nx, ny)) continue
            val j = map.index(nx, ny)
            if (low) {
                if (map.portal[i].toInt() == h) {
                    if (map.rail[j] == Rail.TRACK && !deep(j)) visit(j)
                } else if (map.lowRail[j].toInt() != 0 && !map.tunnelShut(j) && Tunnel.goes(map.lowHeading[i].toInt(), h) && Tunnel.goes(map.lowHeading[j].toInt(), h)) {
                    visit(n + j)
                }
            } else {
                if (map.rail[j] == Rail.TRACK && !deep(j)) visit(j)
                if (map.lowRail[j].toInt() != 0 && map.portal[j].toInt() == Heading.opposite(h) && !map.tunnelShut(j)) visit(n + j)
            }
        }
    }

    private fun edge(node: Int): Boolean {
        if (node >= n) return false
        val x = node % map.width
        val y = node / map.width
        return x == 0 || y == 0 || x == map.width - 1 || y == map.height - 1
    }

    fun update(stations: List<Building>) {
        line.fill(-1)
        var lines = 0
        val linkedList = ArrayList<Boolean>()
        val queue = IntArray(2 * n)
        for (start in 0 until n) {
            if (map.rail[start] != Rail.TRACK || deep(start) || line[start] >= 0) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            line[start] = lines
            var edge = false
            while (head < tail) {
                val i = queue[head++]
                if (edge(i)) edge = true
                next(i) { j ->
                    if (line[j] < 0) {
                        line[j] = lines
                        queue[tail++] = j
                    }
                }
            }
            linkedList += edge
            lines++
        }
        linked = linkedList.toBooleanArray()
        val kept = stations.filter { Rail.stop(map, it) >= 0 }
        buildings = kept
        stops = IntArray(kept.size) { Rail.stop(map, kept[it]) }
        times = Array(kept.size) { IntArray(kept.size) { -1 } }
        toEdge = IntArray(kept.size) { -1 }
        for (a in kept.indices) {
            val steps = steps(stops[a])
            for (b in kept.indices) if (steps[stops[b]] >= 0) times[a][b] = steps[stops[b]] * Balance.RAIL_TIME
            var nearest = -1
            for (i in 0 until n) {
                if (steps[i] < 0) continue
                if (edge(i) && (nearest < 0 || steps[i] < nearest)) nearest = steps[i]
            }
            toEdge[a] = nearest
        }
    }

    /** Whether the stop's line reaches the edge of the map. */
    fun linked(stop: Int): Boolean = line[stops[stop]].let { it >= 0 && linked[it] }

    /** Steps along the track from [from] to every place, the tunnels from [CityMap.size] up; -1 where it doesn't reach. */
    fun steps(from: Int): IntArray {
        val steps = IntArray(2 * n) { -1 }
        val queue = IntArray(2 * n)
        var head = 0
        var tail = 0
        steps[from] = 0
        queue[tail++] = from
        while (head < tail) {
            val i = queue[head++]
            next(i) { j ->
                if (steps[j] < 0) {
                    steps[j] = steps[i] + 1
                    queue[tail++] = j
                }
            }
        }
        return steps
    }

    /** The track from [from] back along [steps] to where they were counted from, one place at a time, tunnels from [CityMap.size] up. */
    fun pathBack(steps: IntArray, from: Int): IntArray {
        if (steps[from] < 0) return IntArray(0)
        val out = IntArray(steps[from] + 1)
        var at = from
        out[0] = at
        for (k in 1 until out.size) {
            // The first place one step nearer, north to west, so it's always the same way.
            var back = -1
            next(at) { j -> if (back < 0 && steps[j] == steps[at] - 1) back = j }
            at = back
            out[k] = at
        }
        return out
    }
}
