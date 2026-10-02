package com.rm.infill.sim

/** What's holding the town back, for the status line: the worst first. */
enum class AdviceKind {
    /** Money's run out. */
    DEBT,

    /** Not enough power for what the town draws. */
    POWER_SHORT,

    /** Not enough water for what the town uses. */
    WATER_SHORT,

    /** A zone is wanted and there's none of it to grow on. */
    ZONE_MORE,

    /** A zone is wanted, its lots are there, but nothing can grow on them, and why. */
    NO_ROAD,
    NO_POWER,
    NO_WATER,
    NO_SEWER,
    NO_STAFF,
    UNAPPEALING,

    /** Garbage isn't being taken away. */
    GARBAGE,
}

/** One piece of advice: what, which zone it's about if any, and a place to look at (-1 if none). */
class Advice(val kind: AdviceKind, val zone: Byte = Zone.NONE, val x: Int = -1, val y: Int = -1)
