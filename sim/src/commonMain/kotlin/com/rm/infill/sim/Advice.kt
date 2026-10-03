package com.rm.infill.sim

/** What's holding the town back, for the status line: the worst first. */
enum class AdviceKind {
    /** Money's run out. */
    DEBT,

    /** Not enough power for what the town draws. */
    POWER_SHORT,

    /** Not enough water for what the town uses. */
    WATER_SHORT,

    /** No way for newcomers to get in: no road to the edge, station on a line out, port or airport. */
    NO_WAY_IN,

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

/** Where a zone's demand comes from, for the Demand window. */
enum class DemandSource {
    /** Homes: the workers the town's jobs need, with their families. */
    WORKERS_NEEDED,
    /** Homes: people who come anyway, more with a railway or an airport. */
    SETTLERS,
    /** Homes: the people already living here. */
    LIVING_HERE,
    /** Homes: room in homes standing empty. */
    EMPTY_HOMES,
    /** Homes, shops, works, offices and farms already going up. */
    GOING_UP,
    /** Shops: what people here spend. */
    SPENDING,
    /** Shops: shoppers coming from next door. */
    SHOPPERS_IN,
    /** Shops: shoppers going next door. */
    SHOPPERS_OUT,
    /** The jobs of this kind already here. */
    JOBS_HERE,
    /** Works and farms: what the outside world buys. */
    MARKET,
    /** Works and farms: goods the town brings in that it could make itself. */
    BROUGHT_IN,
    /** Offices: the town's size and the century. */
    TOWN_SIZE,
    /** Offices: work the airports bring. */
    AIRPORTS,
    /** What the tax rate adds or takes off. */
    TAX,
}

/** One part of a zone's demand, in people for homes and jobs for the rest. */
class DemandPart(val source: DemandSource, val amount: Int)

/** A zone's demand, [total] once the [tax] rate's done its part, and what it's made of. */
class ZoneDemand(val zone: Byte, val parts: List<DemandPart>, val total: Int, val tax: Int)
