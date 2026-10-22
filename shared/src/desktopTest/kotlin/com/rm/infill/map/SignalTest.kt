package com.rm.infill.map

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignalTest {
    @Test
    fun eachWayGetsGreenButNeverBothAtOnce() {
        for (tile in listOf(0, 1, 77, 4095)) {
            var down = false
            var across = false
            var amber = false
            var t = 0f
            while (t < 40f) {
                val a = signal(tile, vertical = true, time = t)
                val b = signal(tile, vertical = false, time = t)
                assertFalse(a != LIGHT_RED && b != LIGHT_RED, "both ways open at $tile, $t")
                down = down || a == LIGHT_GREEN
                across = across || b == LIGHT_GREEN
                amber = amber || a == LIGHT_AMBER
                t += 0.1f
            }
            assertTrue(down && across && amber)
        }
    }

    @Test
    fun neighboursStartApart() {
        val starts = (0 until 8).map { tile -> (0 until 200).first { signal(tile, true, it * 0.1f) != signal(tile, true, it * 0.1f + 0.1f) } }
        assertTrue(starts.toSet().size > 1)
    }

    @Test
    fun roundARoundaboutMeetsTheRoadEitherSideWithNoJumps() {
        val w = 10
        val here = 55
        val ways = listOf(here - w, here + 1, here + w, here - 1)
        val a = FloatArray(3)
        val b = FloatArray(3)
        for (prev in ways) for (next in ways) {
            pointRoundInto(prev, here, next, 0f, w, 0.15f, a)
            pointThroughInto(prev, here, next, 0f, w, 0.15f, b)
            assertTrue(kotlin.math.abs(a[0] - b[0]) < 1e-3f && kotlin.math.abs(a[1] - b[1]) < 1e-3f, "in from $prev")
            pointRoundInto(prev, here, next, 1f, w, 0.15f, a)
            pointThroughInto(prev, here, next, 1f, w, 0.15f, b)
            assertTrue(kotlin.math.abs(a[0] - b[0]) < 1e-3f && kotlin.math.abs(a[1] - b[1]) < 1e-3f, "out to $next")
            var x = Float.NaN
            var y = Float.NaN
            for (k in 0..200) {
                pointRoundInto(prev, here, next, k / 200f, w, 0.15f, a)
                if (!x.isNaN()) assertTrue(kotlin.math.hypot(a[0] - x, a[1] - y) < 0.05f, "a jump from $prev to $next at $k")
                x = a[0]
                y = a[1]
            }
        }
    }
}
