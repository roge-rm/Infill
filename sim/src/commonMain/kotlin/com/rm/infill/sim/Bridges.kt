package com.rm.infill.sim

/**
 * The kinds of bridge, each from its year, over a span of water tiles from
 * [minSpan] to [maxSpan]: how much room it leaves ships ([Bridge.LOW],
 * [Bridge.HIGH], or [Bridge.OPENS] for one that lifts or swings), whether it
 * takes heavy trucks, how many straight tiles of approach a high one needs at
 * each end, from what span it shuts in a gale (0 never), and what it costs and
 * lasts against a road on land: [price] times as much to build, [upkeep] times
 * as much to keep, [life] years.
 */
enum class BridgeKind(
    val id: Int, val year: Int, val minSpan: Int, val maxSpan: Int, val clearance: Int, val heavy: Boolean,
    val approach: Int, val galeSpan: Int, val price: Int, val upkeep: Double, val life: Int, val rail: Boolean,
) {
    TRESTLE(1, 1900, 1, 4, Bridge.LOW, false, 0, 0, 5, 3.0, 30, true),
    SWING(2, 1900, 2, 3, Bridge.OPENS, true, 0, 0, 9, 5.0, 50, true),
    TRUSS(3, 1900, 1, 8, Bridge.HIGH, true, 2, 6, 10, 4.0, 60, true),
    LIFT(4, 1920, 1, 4, Bridge.OPENS, true, 0, 0, 10, 5.0, 50, true),
    CONCRETE(5, 1930, 1, 6, Bridge.LOW, true, 0, 0, 6, 2.5, 70, true),
    SUSPENSION(6, 1930, 5, 40, Bridge.HIGH, true, 3, 1, 16, 5.0, 80, false),
    CABLE_STAYED(7, 1975, 4, 20, Bridge.HIGH, true, 3, 1, 13, 4.0, 75, false),
    ;

    /** Whether it fits a crossing [span] tiles long. */
    fun spans(span: Int) = span in minSpan..maxSpan

    /** Whether it shuts in a gale over a crossing [span] tiles long. */
    fun shutsInGale(span: Int) = galeSpan in 1..span

    companion object {
        fun of(id: Int): BridgeKind? = entries.firstOrNull { it.id == id }
    }
}

/** What's kept for a bridge on each of its tiles, in [CityMap.bridge]. */
object Bridge {
    // How much room a bridge leaves ships.
    const val LOW = 0
    const val HIGH = 1
    const val OPENS = 2

    /** The [BridgeKind] id, 0 for a plain bridge from before there were kinds. */
    const val KIND = 0x0f

    /** Shut by the town. */
    const val SHUT = 0x10

    /** Drivers pay a toll to cross. */
    const val TOLL = 0x20

    /** A tile of land leading up to a high bridge, which nothing joins from the side. */
    const val APPROACH = 0x40

    /** The bridge runs east to west (else north to south). */
    const val ACROSS = 0x80
}
