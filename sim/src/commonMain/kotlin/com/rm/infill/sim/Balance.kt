package com.rm.infill.sim

/** The numbers the simulation is tuned with, in one place. Money is in the dollars of 1900. */
object Balance {
    /** Share of residents who work. */
    const val LABOUR_SHARE = 0.45

    /** Residents per shop job the town wants. */
    const val RESIDENTS_PER_SHOP_JOB = 7.0

    /** Settlers who'd come anyway, before there are jobs to draw them, and more as the town gets known. */
    const val SETTLERS = 30
    const val SETTLERS_PER_RESIDENT = 0.02

    /** The outside market for the town's goods, in jobs: a base, a share of the town's size, and growth a year. */
    const val EXPORT_BASE = 40.0
    const val EXPORT_PER_RESIDENT = 0.32
    const val EXPORT_GROWTH = 0.02

    /** How much of that market a town with no road to the edge of the map can reach. */
    const val UNCONNECTED_EXPORTS = 0.4

    /** Tax rates start here, in percent, and demand is neutral at them. */
    const val DEFAULT_TAX = 7

    /** How much each point of tax above or below the default moves demand. */
    const val TAX_DEMAND = 0.08

    /** Monthly tax, per resident or job, at 1% tax. */
    const val RESIDENT_TAX = 0.06
    const val JOB_TAX = 0.08

    /** Monthly upkeep. */
    const val ROAD_UPKEEP = 0.4
    const val LINE_UPKEEP = 0.1
    const val PLANT_UPKEEP = 60.0

    /** Growth a month can't go past, as a share of what the zone has, with a floor for small towns. */
    const val GROWTH_SHARE = 0.05
    const val GROWTH_FLOOR = 12

    /** How far a lot can be from a road and still grow, in tiles. */
    const val ROAD_REACH = 2

    /** How many lots are looked at for each thing that grows, picking the best. */
    const val CANDIDATES = 8

    /**
     * How attractive a lot has to be for each stage, by zone (residential,
     * commercial, industrial): the busier buildings only go up in the best spots.
     */
    val STAGE_ATTRACTION = arrayOf(
        intArrayOf(0, 0, 55, 63, 70),
        intArrayOf(0, 0, 50, 60, 68),
        intArrayOf(0, 0, 55, 58, 62),
    )

    /** Days a building waits after growing before it grows again. */
    const val SETTLE_DAYS = 60
}
