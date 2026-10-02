package com.rm.infill.sim

/**
 * The town's eras. Each arrives on the later of its [year] and the town
 * meeting its milestone, and brings the tools and buildings of its day. The
 * world doesn't wait: inventions, the outside economy and what people expect
 * follow the calendar whatever era the town is in.
 */
enum class Era(val year: Int) {
    TOWNSHIP(1900),
    STREETCAR(1910),
    MOTOR(1940),
    RENEWAL(1970),
    INFILL(2000),
    FUTURE(2030),
    ;

    val next: Era? get() = entries.getOrNull(ordinal + 1)

    companion object {
        /** The era that something first built in [year] belongs to. */
        fun of(year: Int): Era = entries.last { it.year <= year }
    }
}

/** Something the town has to have for an era: [have] of [need]. */
class Goal(val kind: GoalKind, val have: Int, val need: Int) {
    val met get() = have >= need
}

enum class GoalKind {
    /** People living in the town. */
    People,

    /** Mains water anywhere, or a railway station: 1 or 0. */
    MainsOrStation,

    /** Percent of people on mains water, and on the sewer. */
    OnMains,
    OnSewer,

    /** Percent of buildings with power. */
    Powered,

    /** Shops or offices at high density: how many. */
    Downtown,

    /** High schools: how many. */
    HighSchool,

    /** Percent of the land along the roads built on. */
    LandBuilt,

    /** Percent of roads, pipes and track still within their expected life. */
    KeptUp,

    /** Percent of last month's commutes made other than by car. */
    GreenTrips,

    /** How freely last month's traffic moved, in percent. */
    Flow,

    /** How close the town's carbon a person is to [Balance.FUTURE_CARBON], in percent: 100 there or under. */
    LowCarbon,
}

/**
 * The outside world's appetite for the town's goods, by year: steady growth
 * through the 1900s, a wartime boom, the 1920s, the slump of the 1930s,
 * another war, the long post-war boom, and factories closing from the 1970s.
 */
object Economy {
    private val years = intArrayOf(1900, 1913, 1916, 1919, 1929, 1932, 1938, 1943, 1946, 1955, 1970, 1975, 1985, 1995, 2008, 2010, 2020)
    private val factors = intArrayOf(100, 100, 125, 100, 120, 75, 85, 130, 105, 125, 125, 95, 80, 95, 100, 85, 95)

    /** The outside market in [year], in percent of normal times. */
    fun market(year: Int, month: Int = 0): Int {
        val t = year * 12 + month
        if (t <= years.first() * 12) return factors.first()
        for (k in 1 until years.size) {
            val end = years[k] * 12
            if (t <= end) {
                val start = years[k - 1] * 12
                return factors[k - 1] + (factors[k] - factors[k - 1]) * (t - start) / (end - start)
            }
        }
        return factors.last()
    }
}
