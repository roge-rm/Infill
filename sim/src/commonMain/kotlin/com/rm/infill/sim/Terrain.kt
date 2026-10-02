package com.rm.infill.sim

/** What the ground is on a tile, before anything is built on it. Stored as a byte. */
object Terrain {
    const val GRASS: Byte = 0
    const val WATER: Byte = 1
    const val TREES: Byte = 2
    const val DIRT: Byte = 3
}

/** What a tile is zoned for. */
object Zone {
    const val NONE: Byte = 0
    const val RESIDENTIAL: Byte = 1
    const val COMMERCIAL: Byte = 2
    const val INDUSTRIAL: Byte = 3

    /** Farms, woodlots and mines on the outskirts, by what's under each lot. */
    const val FARMLAND: Byte = 4

    /** Office work, wanting schooled people and dear land near the centre. */
    const val OFFICE: Byte = 5

    /** Homes over shops, from the streetcar age: each building has a household and jobs. */
    const val MIXED: Byte = 6

    /** The zones that grow, in order. */
    const val COUNT = 7
}

/** What's in the ground, for farms, mines and wells: good soil, iron ore, a coal seam or an oil field. Stored as a byte. */
object Resource {
    const val NONE: Byte = 0
    const val FERTILE: Byte = 1
    const val ORE: Byte = 2
    const val COAL: Byte = 3
    const val OIL: Byte = 4
}
