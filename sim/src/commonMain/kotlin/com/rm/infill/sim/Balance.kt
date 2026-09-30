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
    const val LINE_UPKEEP = 0.1
    const val PLANT_UPKEEP = 60.0
    const val POLICE_UPKEEP = 40.0
    const val FIRE_UPKEEP = 45.0
    const val PARK_UPKEEP = 0.5

    /** How far a fully funded station reaches, in tiles. Less money, less reach, down to 40% of it. */
    const val POLICE_REACH = 14
    const val FIRE_REACH = 12

    /** Chance in ten thousand each month that a building catches fire with no fire station near. */
    const val FIRE_CHANCE = 30
    const val FIRE_CHANCE_INDUSTRY = 80

    /** Chance in a hundred each day that a fire spreads to a building next to it. */
    const val FIRE_SPREAD = 4

    /** How many days a fire burns, and up to how many more: a minute or two at normal speed. */
    const val FIRE_DAYS = 3
    const val FIRE_DAYS_MORE = 4

    /** The weather changes every this many days: a few spells in each month's day and night. */
    const val WEATHER_DAYS = 5

    /** Fire cover at which a burning building is saved, damaged, rather than lost. */
    const val FIRE_SAVED = 80

    /** Growth a month can't go past, as a share of what the zone has, with a floor for small towns. */
    const val GROWTH_SHARE = 0.2
    const val GROWTH_FLOOR = 60

    /** Road upkeep over water is this many times as much. */
    const val BRIDGE_UPKEEP = 3.0

    /** Shopping trips a month: one for this many residents, and how many a shop job serves. */
    const val RESIDENTS_PER_SHOPPER = 4
    const val SHOPPERS_PER_SHOP_JOB = 8

    /** Loads of freight a month for every ten jobs at a works. */
    const val FREIGHT_PER_TEN_JOBS = 3

    /** Longest trip anyone makes to work or the shops, and for freight, in seconds. */
    const val LONGEST_TRIP = 90 * 60
    const val LONGEST_FREIGHT = 3 * 3_600

    /**
     * What a commute does to a home's appeal: nothing up to [FINE_COMMUTE]
     * minutes, then a point for every two minutes more, up to [LONG_COMMUTE].
     * No job in reach at all costs [NO_COMMUTE].
     */
    const val FINE_COMMUTE = 15
    const val LONG_COMMUTE = 15
    const val NO_COMMUTE = 15

    /** A point of appeal to a shop for this many trips a month past its door, up to [PASSING_TRADE]. */
    const val TRIPS_PER_PASSING_POINT = 40
    const val PASSING_TRADE = 10

    /** What it costs a works' appeal when its freight can't get out. */
    const val FREIGHT_STUCK = 15

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
    const val SETTLE_DAYS = 20
}
