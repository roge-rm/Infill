package com.rm.infill.sim

/**
 * Where roads meet. Each crossing has a control that sets how long traffic
 * waits there: free at a quiet one, stop signs, lights from the 1920s, a
 * roundabout, or an interchange from the 1950s that lets the busiest road
 * pass over. The player picks one, or leaves the town to put up stop signs
 * and then lights as the traffic grows.
 */
object Junction {
    // What a crossing has: [NONE] where it isn't one.
    const val NONE: Byte = 0
    const val FREE: Byte = 1
    const val STOP: Byte = 2
    const val LIGHTS: Byte = 3
    const val ROUNDABOUT: Byte = 4
    const val INTERCHANGE: Byte = 5

    /** The player's choice where it's [AUTO]: left to the town. */
    const val AUTO: Byte = 0

    /** Seconds a car waits at a quiet crossing of each control, by control. */
    private val WAIT = intArrayOf(0, 1, 6, 10, 3, 0)

    /** How much traffic each control handles before it backs up, in percent of the road's capacity. */
    private val HANDLES = intArrayOf(100, 60, 80, 160, 130, 400)

    fun year(control: Byte): Int = when (control) {
        LIGHTS -> 1920
        INTERCHANGE -> 1950
        ROUNDABOUT -> 1905
        else -> 1900
    }

    fun price(control: Byte): Long = when (control) {
        LIGHTS -> 400L
        ROUNDABOUT -> 1_200L
        INTERCHANGE -> 25_000L
        else -> 50L
    }

    fun upkeep(control: Byte): Double = when (control) {
        LIGHTS -> 2.0
        ROUNDABOUT -> 0.5
        INTERCHANGE -> 15.0
        else -> 0.0
    }

    /**
     * Seconds a car waits to get through a crossing with [control] on a road
     * of [capacity], with [load] vehicles a month through it: the quiet wait,
     * and more as it fills, up to [Balance.JUNCTION_MOST].
     */
    fun wait(control: Byte, capacity: Int, load: Int): Int {
        if (control == NONE) return 0
        val handles = capacity * HANDLES[control.toInt()] / 100
        val ratio = if (handles <= 0) 0 else load * 100 / handles
        return WAIT[control.toInt()] + minOf(Balance.JUNCTION_MOST, ratio * ratio * Balance.JUNCTION_SLOPE / 10_000)
    }

    /** Whether tile [i] is a crossing: a road with roads on three or four sides. */
    fun at(map: CityMap, i: Int): Boolean {
        if (map.road[i] == Road.NONE) return false
        val x = i % map.width
        val y = i / map.width
        var n = 0
        for (h in 1..4) {
            val nx = x + Heading.DX[h]
            val ny = y + Heading.DY[h]
            if (map.inside(nx, ny) && map.road[map.index(nx, ny)] != Road.NONE) n++
        }
        return n >= 3
    }
}
