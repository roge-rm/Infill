package com.rm.infill.sim

/** What an ordinance is about, for grouping them in the window. */
enum class Topic { SAFETY, HEALTH, HOUSING, MORALS, ENVIRONMENT, TRAFFIC, WASTE, ENERGY, CULTURE }

/**
 * A town-wide law. It can be passed from [from], and history ends it after
 * [until] if it has an end. While it's in force it costs [perThousand]
 * dollars a month for every thousand people, and some bring money in (see
 * [City.ordinanceIncome]). What each does is read where it does it, with
 * [City.has].
 */
enum class Ordinance(val topic: Topic, val from: Int, val until: Int? = null, val perThousand: Int) {
    /** Fewer fires start, and they spread less. */
    BUILDING_CODE(Topic.SAFETY, 1900, perThousand = 15),

    /** Milk and water inspected: healthier people, fewer children die. */
    PUBLIC_HEALTH_ACT(Topic.HEALTH, 1900, perThousand = 20),

    /** Works let out less smoke, and like the town a little less for it. */
    SMOKE_ABATEMENT(Topic.ENVIRONMENT, 1905, perThousand = 10),

    /** Licensed drinking: some vice cut, and fees from the shops. Nothing while prohibition's in force. */
    LIQUOR_LICENCES(Topic.MORALS, 1900, perThousand = 2),

    /** Shops shut on Sundays: less vice, less trade. */
    SUNDAY_CLOSING(Topic.MORALS, 1900, until = 1985, perThousand = 0),

    /** The young off the streets at night: less theft, quieter shops. */
    YOUTH_CURFEW(Topic.SAFETY, 1900, until = 1970, perThousand = 8),

    /** Rules for crowded tenements, so they're no longer unhealthy. */
    TENEMENT_ACT(Topic.HEALTH, 1910, perThousand = 10),

    /** Homes the town owns and lets to the poor: they aren't priced out, however dear the land gets. */
    SOCIAL_HOUSING(Topic.HOUSING, 1945, perThousand = 30),

    /** Clocks forward in summer: a lower evening peak for power. */
    DAYLIGHT_SAVING(Topic.ENERGY, 1918, perThousand = 0),

    /** No drink sold: much less vice, but the rackets thrive. Repealed in 1933. */
    PROHIBITION(Topic.MORALS, 1920, until = 1933, perThousand = 15),

    /** Slower traffic, quieter streets, and fines. */
    SPEED_LIMITS(Topic.TRAFFIC, 1920, perThousand = 5),

    /** Paid parking downtown: a few less drive, and meters pay. */
    PARKING_METERS(Topic.TRAFFIC, 1935, perThousand = 5),

    /** A meal at school: children learn faster and are healthier. */
    SCHOOL_MEALS(Topic.HEALTH, 1940, perThousand = 25),

    /** Fluoride in the mains: healthier people on mains water. */
    FLUORIDATION(Topic.HEALTH, 1950, perThousand = 6),

    /** Dogs licensed: a little money, and parks people enjoy more. */
    DOG_LICENCES(Topic.CULTURE, 1950, perThousand = 2),

    /** Quiet at night: noise cut, shops and nightlife a little less wanted. */
    NOISE_BYLAW(Topic.ENVIRONMENT, 1960, perThousand = 6),

    /** Power stations let out a quarter less smoke, and pay more for cleaner fuel. */
    CLEAN_AIR_ACT(Topic.ENVIRONMENT, 1970, perThousand = 20),

    /** A deposit on bottles: less garbage. */
    BOTTLE_DEPOSIT(Topic.WASTE, 1970, perThousand = 4),

    /** A share of building money spent on art: more culture, more visitors. */
    PERCENT_FOR_ART(Topic.CULTURE, 1970, perThousand = 10),

    /** Trees protected: woods soak up more pollution. */
    TREE_PROTECTION(Topic.ENVIRONMENT, 1975, perThousand = 4),

    /** Insulation and efficient lights: less power used. */
    ENERGY_CODE(Topic.ENERGY, 1980, perThousand = 8),

    /** Recycling collected from the curb: half as much again recycled, where there's somewhere to take it. */
    CURBSIDE_RECYCLING(Topic.WASTE, 1990, perThousand = 30),

    /** Bars and clubs open late: more custom and culture, more vice, and fees. */
    LATE_LICENCES(Topic.MORALS, 1990, perThousand = 0),

    /** A plan for heat waves: the old and frail checked on and taken somewhere cool, so fewer die. */
    HEAT_PLAN(Topic.HEALTH, 2000, perThousand = 10),

    /** No smoking indoors: healthier people, a little less trade. */
    SMOKING_BAN(Topic.HEALTH, 2005, perThousand = 3),

    /** A charge to drive into town: fewer cars, a little less trade, and money in. */
    CONGESTION_CHARGE(Topic.TRAFFIC, 2005, perThousand = 10),

    /** A price on carbon: fossil fuel dearer, and money in for every tonne. */
    CARBON_PRICE(Topic.ENERGY, 2020, perThousand = 0),
    ;

    /** What it costs a month for [people], rounded up to a whole dollar so a small town still pays something. */
    fun cost(people: Int): Long = (perThousand.toLong() * people + 999) / 1000

    /** Whether it can be passed, or stay in force, in [year]. */
    fun inYear(year: Int): Boolean = year >= from && (until == null || year <= until)
}
