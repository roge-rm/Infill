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

    /** Monthly upkeep of a tile of track, a station and a freight yard. */
    const val RAIL_UPKEEP = 0.6
    const val STATION_UPKEEP = 25.0
    const val YARD_UPKEEP = 40.0

    /** Seconds for a train to cross a tile, and the wait for one at a station. */
    const val RAIL_TIME = 6
    const val RAIL_WAIT = 300

    /** Seconds a road trip loses at a level crossing. */
    const val CROSSING_DELAY = 20

    /** Passengers or loads a day that make a train's worth, for how busy a line looks. */
    const val TRAIN_LOAD = 8

    /** The outside market with a freight yard on a line to the edge, and settlers with a station on one. */
    const val RAIL_EXPORTS = 1.3
    const val RAIL_SETTLERS = 1.5

    /** Monthly upkeep of a tile of pipe, and of each part of the waterworks. */
    const val PIPE_UPKEEP = 0.15
    const val PUMP_UPKEEP = 30.0
    const val WELL_UPKEEP = 10.0
    const val TOWER_UPKEEP = 8.0
    const val OUTFALL_UPKEEP = 5.0

    /** People a pumping station and a well field supply, residents and workers alike. */
    const val PUMP_SUPPLY = 3_000
    const val WELL_SUPPLY = 800

    /** How far water goes along the mains from a source or a tower before the pressure's too low, in tiles. */
    const val PRESSURE_REACH = 30

    /** How near a building has to be to a main or a sewer to be on it, in tiles: as far as a road reaches. */
    const val PIPE_REACH = 2

    /** How far sewage spreads through the water from an outfall, in tiles, and how much there is for every hundred people. */
    const val FOUL_REACH = 20
    const val FOUL_PER_HUNDRED = 6

    /** Fire cover mains water adds, for the hydrants. */
    const val HYDRANT_COVER = 90

    /** Monthly upkeep of a storm pond and a storm outfall. */
    const val POND_UPKEEP = 4.0
    const val STORM_OUTFALL_UPKEEP = 3.0

    /**
     * Stormwater. Rain runs off each tile by its share of hard surface, and
     * snowmelt this many times over for each point of snow cover lost. The soft
     * ground around soaks up [ABSORB] percent of the rain that falls on it; what
     * neither soaks up nor drains away within [FLOOD_AREA] tiles stands as a
     * flood, [FLOOD_SCALE] levels for each unit left on a tile.
     */
    const val MELT_RUNOFF = 1

    /** Rain or melt less than this soaks in everywhere. */
    const val DOWNPOUR = 25
    const val ABSORB = 120
    const val FLOOD_AREA = 2
    const val FLOOD_SCALE = 3

    /**
     * The ground: each wet spell soaks it by this share of the rain and melt, in
     * percent, and a dry spell dries it this much, more in warm weather. Soaked
     * ground takes in only [SOAKED_SOAK] percent of what dry ground would, and
     * frozen ground [FROZEN_SOAK].
     */
    const val GROUND_WETS = 30
    const val GROUND_DRIES = 25
    const val GROUND_DRIES_WARM = 40
    const val SOAKED_SOAK = 15
    const val FROZEN_SOAK = 20

    /**
     * What runs off over the ground to somewhere lower, even from paving, in a
     * downpour: this much a tile when the land round about is dry, down to
     * [RUNS_AWAY_SOAKED] when it's soaked.
     */
    const val RUNS_AWAY_DRY = 45
    const val RUNS_AWAY_SOAKED = 15

    /**
     * The rivers: they rise by this share of the rain and melt, in percent, from
     * dry ground and from soaked, fall this much a day, and past [BANKFULL]
     * spill onto the land beside them, a tile deeper for every [SPILL_STEP] over.
     */
    const val RIVER_RISE_DRY = 15
    const val RIVER_RISE_SOAKED = 55
    const val RIVER_FALL = 3
    const val BANKFULL = 70
    const val SPILL_STEP = 15
    const val SPILL_MOST = 3

    /** What a storm pond holds in one downpour, and how far round it it catches the rain without drains. */
    const val POND_HOLDS = 4_000
    const val POND_REACH = 3

    /** The share of runoff the sewers take where there are no storm drains, in percent. It all goes out at the outfalls. */
    const val SEWER_TAKES = 50

    /**
     * After a flood: the clean-up bill for each flooded building (times its
     * stage), road tile and track tile; mud left as grime, this share of the
     * flood level in percent; and the memory of it, fading this much a month,
     * which costs land value (a point for every [STIGMA_VALUE]) and a home's
     * appeal (one for every [STIGMA_APPEAL]).
     */
    const val CLEANUP_BUILDING = 10L
    const val CLEANUP_ROAD = 3L
    const val CLEANUP_TRACK = 4L
    const val MUD = 40
    const val STIGMA_FADE = 3
    const val STIGMA_VALUE = 6
    const val STIGMA_APPEAL = 12

    /**
     * Sickness: a flooded home on a well or a septic tank loses people one time
     * in [SICK_CHANCE]; one on the sewer when the sewers overflowed, one in
     * [SICK_CHANCE_SEWER].
     */
    const val SICK_CHANCE = 4
    const val SICK_CHANCE_SEWER = 10

    /**
     * Flood levels: from [FLOODED] a place is flooded, shops and works shut, and
     * power and pumping stations stop; from [FLOOD_DAMAGE] the water's deep,
     * roads and track are closed and a building can be damaged, one time in
     * [FLOOD_DAMAGE_CHANCE].
     */
    const val FLOODED = 64
    const val FLOOD_DAMAGE = 128
    const val FLOOD_DAMAGE_CHANCE = 5

    /** How much a flood goes down each day, what it costs a place's appeal, and how much slower it makes a road. */
    const val FLOOD_DRAIN = 20
    const val FLOOD_APPEAL = 15
    const val FLOOD_SLOW = 3

    /** Appeal of mains water and the sewer to a home, and what a well in grime costs it. */
    const val MAINS_APPEAL = 5
    const val SEWER_APPEAL = 3
    const val BAD_WELL = 10

    /**
     * Schools: pupils a school and a high school take, fully funded, and how far
     * children come to them. Children of high school age are a third of them.
     */
    const val SCHOOL_PLACES = 240
    const val HIGH_SCHOOL_PLACES = 400
    const val SCHOOL_REACH = 12
    const val HIGH_SCHOOL_REACH = 18
    const val TEENS = 3

    /** Months for children's schooling to catch up with how many of them have a place: about three years. */
    const val SCHOOLING_PACE = 36

    /** Health care: people a clinic and a hospital look after, fully funded, and how far they come. */
    const val CLINIC_CARES = 1_500
    const val HOSPITAL_CARES = 12_000
    const val CLINIC_REACH = 8
    const val HOSPITAL_REACH = 22

    /** Monthly upkeep of a school, high school, clinic and hospital, fully funded. */
    const val SCHOOL_UPKEEP = 30.0
    const val HIGH_SCHOOL_UPKEEP = 60.0
    const val CLINIC_UPKEEP = 15.0
    const val HOSPITAL_UPKEEP = 90.0

    /**
     * Health: where it settles, from a starting [HEALTH_BASE], and how fast it
     * gets there, a [HEALTH_PACE]th of the gap a month. Care adds up to
     * [CARE_HEALTH], mains water and the sewer [MAINS_HEALTH] each, wealth
     * [WEALTH_HEALTH] a class, a park nearby [PARK_HEALTH]; pollution takes a
     * point for every [POLLUTION_HEALTH], grime [GRIME_HEALTH] a step, a
     * tenement's crowding [CROWDING_HEALTH], and a sickness [SICK_HEALTH] at once.
     */
    const val HEALTH_BASE = 50
    const val HEALTH_PACE = 6
    const val CARE_HEALTH = 25
    const val MAINS_HEALTH = 6
    const val WEALTH_HEALTH = 5
    const val PARK_HEALTH = 4
    const val POLLUTION_HEALTH = 5
    const val GRIME_HEALTH = 4
    const val CROWDING_HEALTH = 8
    const val SICK_HEALTH = 20

    /** Health below which people start to move away. */
    const val UNHEALTHY = 40

    /** Land value up to which homes are built poor, and from which they're built well off. */
    const val POOR_BELOW = 70
    const val WELL_OFF_FROM = 120

    /** Share of a skill's jobs going unfilled from which businesses wanting it can't grow, in percent. */
    const val SKILL_SHORT = 25

    // An empty home's chance of selling each month, in percent: SALE_BASE with
    // no one looking, SALE_PER_DEMAND more for every percent of the town that's
    // looking for a home, between SALE_LEAST and SALE_MOST.
    const val SALE_BASE = 12
    const val SALE_PER_DEMAND = 6
    const val SALE_LEAST = 3
    const val SALE_MOST = 85

    /** How much less an empty home holds on when the town has too many homes. */
    const val EMPTY_SHRINK = 30

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
