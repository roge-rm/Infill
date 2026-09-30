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
}
