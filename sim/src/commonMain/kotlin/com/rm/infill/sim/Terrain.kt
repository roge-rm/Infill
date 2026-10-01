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

    /** The zones that grow, in order. */
    const val COUNT = 5
}

/** What's in the ground, for farms and mines: good soil, iron ore, or a coal seam. Stored as a byte. */
object Resource {
    const val NONE: Byte = 0
    const val FERTILE: Byte = 1
    const val ORE: Byte = 2
    const val COAL: Byte = 3
}
