package com.rm.infill.sim

/** What's broken on a tile, as bits: each kind of pipe, the road surface and the track, and works under way there. */
object Broken {
    const val WATER = 1
    const val SEWER = 2
    const val STORM = 4
    const val ROAD = 8
    const val RAIL = 16

    /** Not a failure but renewal: the crews are relaying what's marked, or waiting their turn to. */
    const val WORKS = 32

    /** The pipes that shut the road above them while they're dug up. */
    const val DUG = WATER or SEWER or STORM
}

/**
 * What a water main, sewer or storm drain is made of, by its number in the
 * pipe layer: its expected [life] in years, its price in percent of the pipe's,
 * the [year] it's first laid and, for wood, the era it's no longer laid in.
 */
enum class Material(val pipe: Pipe, val id: Byte, val life: Int, val price: Int, val year: Int, val gone: Era? = null) {
    CAST_IRON(Pipe.WATER, 1, 50, 100, 1900),
    WOOD(Pipe.WATER, 2, 20, 50, 1900, gone = Era.MOTOR),
    DUCTILE_IRON(Pipe.WATER, 3, 70, 120, 1940),
    PLASTIC_MAIN(Pipe.WATER, 4, 90, 110, 1970),
    BRICK(Pipe.SEWER, 1, 60, 100, 1900),
    CONCRETE_SEWER(Pipe.SEWER, 2, 75, 110, 1940),
    PLASTIC_SEWER(Pipe.SEWER, 3, 90, 100, 1970),
    CLAY_DRAIN(Pipe.STORM, 1, 60, 100, 1900),
    CONCRETE_DRAIN(Pipe.STORM, 2, 75, 110, 1940),
    PLASTIC_DRAIN(Pipe.STORM, 3, 90, 100, 1970),
    ;

    /** What a tile of it costs to lay. */
    val cost: Long get() = pipe.price * price / 100

    companion object {
        fun of(pipe: Pipe, id: Byte): Material? = entries.firstOrNull { it.pipe == pipe && it.id == id }
    }
}

/**
 * How things wear out. Anything built has an expected life in years. The
 * chance it fails in a month grows with the square of its age: next to
 * nothing while it's young, [Balance.FAIL_AT_LIFE] millionths a month at its
 * expected life, four times that at twice its life. Most of it goes in the
 * end, but some keeps going long after.
 */
object Ageing {
    /** Months from January 1900, the way the map keeps when things were laid. */
    fun monthOf(year: Int, month: Int): Int = (year - 1900) * 12 + month

    /** The chance in millionths that something [age] months old, expected to last [life] years, fails this month. */
    fun failChance(age: Int, life: Int): Int {
        if (life <= 0 || age <= 0) return 0
        val l = life * 12L
        // Nothing much before a quarter of its life.
        if (age * 4L < l) return 0
        return minOf(Balance.FAIL_MOST.toLong(), Balance.FAIL_AT_LIFE * age.toLong() * age / (l * l)).toInt()
    }

    /** How worn something is, in percent of its expected life, over 100 when it's past it. */
    fun wear(age: Int, life: Int): Int = if (life <= 0) 0 else (age * 100L / (life * 12L)).toInt()
}
