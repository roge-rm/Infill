package com.rm.infill.sim

/**
 * One city: its map, its money and the date. The UI reads it and the player's
 * tools change it through its functions.
 */
class City(
    val seed: Long,
    width: Int = DEFAULT_SIZE,
    height: Int = DEFAULT_SIZE,
    terrain: TerrainOptions = TerrainOptions(),
) {
    val map = CityMap(width, height).also { TerrainGen.generate(it, seed, terrain) }
    val rng = Rng(seed)

    /** Whole dollars. */
    var funds: Long = START_FUNDS
        private set

    var year: Int = START_YEAR
        private set

    /** 0 is January. */
    var month: Int = 0
        private set

    companion object {
        const val DEFAULT_SIZE = 128
        const val START_YEAR = 1900
        const val START_FUNDS = 20_000L
    }
}
