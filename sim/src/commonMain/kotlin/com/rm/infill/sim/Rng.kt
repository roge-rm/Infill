package com.rm.infill.sim

/**
 * The city's random numbers. Everything random in the simulation comes from
 * one of these, made from the city's seed, so the same seed and the same
 * player actions always give the same city on every platform.
 *
 * SplitMix64. Only Long arithmetic, which is the same on the JVM, Android and
 * WebAssembly.
 */
class Rng(seed: Long) {
    var state: Long = seed
        internal set

    fun nextLong(): Long {
        state += GOLDEN
        var z = state
        z = (z xor (z ushr 30)) * MIX1
        z = (z xor (z ushr 27)) * MIX2
        return z xor (z ushr 31)
    }

    /** A number from 0 until [bound]. [bound] must be above 0. */
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        // 31 random bits times the bound fits in a Long, and the top bits are
        // an even spread over 0 until bound.
        return (((nextLong() ushr 33) * bound) ushr 31).toInt()
    }

    /** True [percent] times out of a hundred. */
    fun chance(percent: Int): Boolean = nextInt(100) < percent

    private companion object {
        const val GOLDEN = -0x61c8864680b583ebL
        const val MIX1 = -0x40a7b892e31b1a47L
        const val MIX2 = -0x6b2fb644ecceee15L
    }
}
