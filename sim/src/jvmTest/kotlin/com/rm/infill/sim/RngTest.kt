package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RngTest {
    @Test
    fun sameSeedSameNumbers() {
        val a = Rng(1900)
        val b = Rng(1900)
        repeat(1000) { assertEquals(a.nextLong(), b.nextLong()) }
    }

    @Test
    fun differentSeedsDiffer() {
        assertNotEquals(Rng(1).nextLong(), Rng(2).nextLong())
    }

    /** The first numbers are fixed, so a change to the generator shows up here before it changes every city. */
    @Test
    fun knownSequence() {
        val r = Rng(0)
        assertEquals(-2152535657050944081L, r.nextLong())
        assertEquals(7960286522194355700L, r.nextLong())
    }

    @Test
    fun nextIntStaysInBoundsAndSpreads() {
        val r = Rng(42)
        val counts = IntArray(10)
        repeat(100_000) {
            val n = r.nextInt(10)
            assertTrue(n in 0 until 10)
            counts[n]++
        }
        // Each bucket within 5% of a tenth.
        for (c in counts) assertTrue(c in 9_500..10_500, "bucket held $c")
    }
}
