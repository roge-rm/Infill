package com.rm.infill.sim

/** How much people travel for pleasure, by the year. */
object Tourism {
    private val years = intArrayOf(1900, 1920, 1950, 1970, 2000, 2030)
    private val shares = intArrayOf(40, 55, 80, 110, 140, 160)

    /** How many come in [year], in percent of the middle of the century. */
    fun share(year: Int): Int {
        if (year <= years.first()) return shares.first()
        for (k in 1 until years.size) if (year <= years[k]) {
            return shares[k - 1] + (shares[k] - shares[k - 1]) * (year - years[k - 1]) / (years[k] - years[k - 1])
        }
        return shares.last()
    }

    // The ways visitors come.
    const val ROAD = 0
    const val RAIL = 1
    const val SEA = 2
    const val AIR = 3
    const val MODES = 4
}
