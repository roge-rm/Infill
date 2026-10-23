package com.rm.infill.map

import com.rm.infill.sim.Balance

/** The street lamps' pools of light at night, by era: gas, electric bulbs, sodium and LED. */
internal object StreetLamps {
    const val ERAS = 4

    /** The era of lamps in [year], from [Balance.LAMP_YEARS]. */
    fun era(year: Int): Int = Balance.LAMP_YEARS.count { year >= it }

    /** Each era's light, as RGB. */
    val COLOURS = intArrayOf(0xFFB25A, 0xFFD58C, 0xFF9C38, 0xE6EEFF)

    /** How strong the middle of a pool is in each era, out of 255, kept under the lit windows. */
    val PEAK = intArrayOf(70, 92, 112, 100)

    /** A pool's radius in 32nds of a tile, in each era: gas lamps light less of the street. */
    val RADIUS = intArrayOf(14, 17, 19, 18)

    /** How much of [PEAK] shows at each level, out of 4, so pools stay faint zoomed out. */
    val LEVEL_SHARE = intArrayOf(4, 3, 2)

    /** One lamp in this many, by tile hash, is out. */
    const val OUT = 9
}
