package com.rm.infill.sim

/** What a service vehicle is out to. */
enum class CalloutKind { FIRE, AMBULANCE, POLICE, GARBAGE }

/**
 * One service vehicle's trip, for the map to show. It goes along [route],
 * road tile by road tile, stopping at each of [stops] (places in [route]).
 * A fire engine waits at its stop while the fire on tile [at] burns.
 * [urgent] is with lights on, out to its first stop. Numbered in the order
 * they're made, so the map can tell which are new.
 */
class Callout(val id: Int, val kind: CalloutKind, val route: IntArray, val stops: IntArray, val at: Int, val urgent: Boolean, internal val made: Int)

/** Shortest ways over the tiles [open] lets through, in tiles: along the roads for the callouts, over the water for the ferries. */
internal class Routes(private val map: CityMap, private val open: (Int) -> Boolean) {
    private val from = IntArray(map.size)
    private val dist = IntArray(map.size)
    private val seen = IntArray(map.size)
    private val queue = IntArray(map.size)
    private var stamp = 0

    /** The tiles from [start] to the nearest one where [end] is true, both included, or null if there's none within [limit] tiles. */
    fun route(start: Int, limit: Int, end: (Int) -> Boolean): IntArray? {
        if (start < 0 || !open(start)) return null
        stamp++
        var head = 0
        var tail = 0
        queue[tail++] = start
        seen[start] = stamp
        from[start] = -1
        dist[start] = 0
        val w = map.width
        while (head < tail) {
            val a = queue[head++]
            if (end(a)) {
                var n = 0
                var k = a
                while (k >= 0) { n++; k = from[k] }
                val out = IntArray(n)
                k = a
                for (j in n - 1 downTo 0) { out[j] = k; k = from[k] }
                return out
            }
            if (dist[a] >= limit) continue
            val x = a % w
            val y = a / w
            for (h in 1..4) {
                val nx = x + Heading.DX[h]
                val ny = y + Heading.DY[h]
                if (!map.inside(nx, ny)) continue
                val b = ny * w + nx
                if (seen[b] == stamp || !open(b)) continue
                seen[b] = stamp
                from[b] = a
                dist[b] = dist[a] + 1
                queue[tail++] = b
            }
        }
        return null
    }

    /** From [start] through each of [via] in turn, as one route, with the places it stops at; null if a leg can't be driven. */
    fun round(start: Int, via: List<Int>, limit: Int): Pair<IntArray, IntArray>? {
        val tiles = ArrayList<Int>()
        val stops = ArrayList<Int>()
        tiles += start
        var at = start
        for (v in via) {
            if (v == at) {
                stops += tiles.size - 1
                continue
            }
            val leg = route(at, limit) { it == v } ?: return null
            for (j in 1 until leg.size) tiles += leg[j]
            stops += tiles.size - 1
            at = v
        }
        // It ends up at the last place without stopping there.
        stops.removeAt(stops.lastIndex)
        return tiles.toIntArray() to stops.toIntArray()
    }
}
