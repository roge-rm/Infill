package com.rm.infill.sim

import kotlin.math.max

/**
 * A sample of last month's trips for the map: each leg that went by car,
 * truck, on foot or by bike, with the tiles it crossed from start to end.
 * Picked in proportion to trips, so busy routes turn up more. Never changed
 * once built.
 */
class TripSample private constructor(
    private val kinds: IntArray,
    private val weights: IntArray,
    /** Where each route's tiles start in [tiles], one more at the end. */
    private val offsets: IntArray,
    private val tiles: IntArray,
    /** All last month's trips on these kinds of leg, sampled or not. */
    val trips: Long,
) {
    val size: Int get() = kinds.size

    /** [CAR], [TRUCK], [WALKER] or [CYCLIST]. */
    fun kind(i: Int): Int = kinds[i]

    /** How often [pick] should land on route [i]: its trips, or times drawn in a large sample. */
    fun weight(i: Int): Int = weights[i]

    /** How many tiles route [i] crosses. */
    fun length(i: Int): Int = offsets[i + 1] - offsets[i]

    /** The [j]th tile of route [i], counting from its start. */
    fun tile(i: Int, j: Int): Int = tileOf(tiles[offsets[i] + j])

    /** Whether the [j]th tile of route [i] is driven in a tunnel, out of sight. */
    fun under(i: Int, j: Int): Boolean = tiles[offsets[i] + j] < 0

    /** All the routes' trips added up. */
    val totalWeight: Long = weights.fold(0L) { a, w -> a + w }

    private val cumulative: LongArray = run {
        var sum = 0L
        LongArray(weights.size) { sum += weights[it]; sum }
    }

    /** The route that [roll], from 0 up to [totalWeight], falls on: busier routes take more of the range. */
    fun pick(roll: Long): Int {
        var lo = 0
        var hi = size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (cumulative[mid] > roll) hi = mid else lo = mid + 1
        }
        return lo
    }

    companion object {
        const val CAR = 0
        const val TRUCK = 1
        const val WALKER = 2
        const val CYCLIST = 3

        /** Most routes in a sample. */
        const val MAX = 3000

        /** A leg's tile from how it's stored, where a tunnel's is -1 - tile. */
        internal fun tileOf(stored: Int) = if (stored < 0) -1 - stored else stored

        val EMPTY = TripSample(IntArray(0), IntArray(0), IntArray(1), IntArray(0), 0L)

        /**
         * From [legs], laid out [kind, start, end, trips, tiles, tile...], a
         * tunnel's tile as -1 - tile. Keeps all if there are few, each
         * weighted by its trips. Else draws [MAX]
         * times with repeats in proportion to trips and keeps each route drawn,
         * weighted by how many times it came up. [salt] seeds the drawing, which
         * has its own generator and leaves the sim's alone. [all] is every
         * trip, counting legs left out of [legs]; by default the legs' own.
         */
        internal fun build(legs: IntList, salt: Int, all: Long = -1L): TripSample {
            val at = ArrayList<Int>()
            var legSum = 0L
            var k = 0
            while (k < legs.size) {
                at.add(k)
                legSum += legs[k + 3]
                k += 5 + legs[k + 4]
            }
            val trips = if (all >= 0) all else legSum
            if (at.isEmpty()) return EMPTY
            var chosen: List<Int> = at
            var counts: IntArray? = null
            if (at.size > MAX) {
                var seed = salt.toLong() * 6364136223846793005L + 1442695040888963407L
                fun next(): Double {
                    seed = seed * 6364136223846793005L + 1442695040888963407L
                    return (seed ushr 11).toDouble() / (1L shl 53).toDouble()
                }
                val cum = DoubleArray(at.size)
                var sum = 0.0
                for (i in at.indices) { sum += max(1, legs[at[i] + 3]); cum[i] = sum }
                val drawn = IntArray(at.size)
                repeat(MAX) {
                    val r = next() * sum
                    var lo = 0
                    var hi = at.size - 1
                    while (lo < hi) {
                        val mid = (lo + hi) ushr 1
                        if (cum[mid] > r) hi = mid else lo = mid + 1
                    }
                    drawn[lo]++
                }
                val keep = at.indices.filter { drawn[it] > 0 }
                chosen = keep.map { at[it] }
                counts = IntArray(keep.size) { drawn[keep[it]] }
            }
            val total = chosen.sumOf { legs[it + 4] }
            val kinds = IntArray(chosen.size)
            val weights = IntArray(chosen.size)
            val offsets = IntArray(chosen.size + 1)
            val tiles = IntArray(total)
            var o = 0
            for ((i, p) in chosen.withIndex()) {
                kinds[i] = legs[p]
                weights[i] = counts?.get(i) ?: legs[p + 3]
                offsets[i] = o
                for (j in 0 until legs[p + 4]) tiles[o++] = legs[p + 5 + j]
            }
            offsets[chosen.size] = o
            return TripSample(kinds, weights, offsets, tiles, trips)
        }
    }
}
