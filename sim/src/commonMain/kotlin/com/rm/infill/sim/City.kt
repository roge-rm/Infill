package com.rm.infill.sim

/**
 * One city: its map, its money and the date. The UI reads it and changes it
 * only through [plan] and [apply].
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

    /** What [action] would do now, without doing it. */
    fun plan(action: Action): Plan {
        val changes = ArrayList<Int>()
        val blocked = ArrayList<Int>()
        var cost = 0L
        val m = map
        when (action) {
            is Action.BuildRoad -> for (i in action.tiles) {
                when {
                    !inMap(i) -> {}
                    m.terrain[i] == Terrain.WATER -> blocked += i
                    m.road[i] != Road.NONE -> {}
                    else -> {
                        changes += i
                        cost += Prices.DIRT_ROAD + if (m.terrain[i] == Terrain.TREES) Prices.CLEAR_TREES else 0
                    }
                }
            }
            is Action.PlaceZone -> forRect(action.x0, action.y0, action.x1, action.y1) { i ->
                when {
                    m.terrain[i] == Terrain.WATER || m.road[i] != Road.NONE -> blocked += i
                    m.zone[i] == action.zone -> {}
                    else -> {
                        changes += i
                        cost += Prices.ZONE
                    }
                }
            }
            is Action.Bulldoze -> forRect(action.x0, action.y0, action.x1, action.y1) { i ->
                var c = 0L
                if (m.road[i] != Road.NONE) c += Prices.REMOVE_ROAD
                if (m.terrain[i] == Terrain.TREES) c += Prices.CLEAR_TREES
                if (c > 0 || m.zone[i] != Zone.NONE) {
                    changes += i
                    cost += c
                }
            }
        }
        val problem = when {
            changes.isEmpty() -> Problem.NothingToDo
            cost > funds -> Problem.NotEnoughMoney
            else -> null
        }
        return Plan(cost, changes.toIntArray(), blocked.toIntArray(), problem)
    }

    /** Does [action] if it can, all of it or none of it. The plan says what happened. */
    fun apply(action: Action): Plan {
        val plan = plan(action)
        if (!plan.ok) return plan
        val m = map
        for (i in plan.changes) {
            when (action) {
                is Action.BuildRoad -> {
                    m.road[i] = Road.DIRT
                    m.zone[i] = Zone.NONE
                    if (m.terrain[i] == Terrain.TREES) m.terrain[i] = Terrain.GRASS
                }
                is Action.PlaceZone -> m.zone[i] = action.zone
                is Action.Bulldoze -> {
                    m.road[i] = Road.NONE
                    m.zone[i] = Zone.NONE
                    if (m.terrain[i] == Terrain.TREES) m.terrain[i] = Terrain.GRASS
                }
            }
        }
        funds -= plan.cost
        return plan
    }

    private fun inMap(i: Int) = i in 0 until map.size

    private inline fun forRect(x0: Int, y0: Int, x1: Int, y1: Int, action: (Int) -> Unit) {
        val left = maxOf(0, minOf(x0, x1))
        val right = minOf(map.width - 1, maxOf(x0, x1))
        val top = maxOf(0, minOf(y0, y1))
        val bottom = minOf(map.height - 1, maxOf(y0, y1))
        for (y in top..bottom) for (x in left..right) action(map.index(x, y))
    }

    companion object {
        const val DEFAULT_SIZE = 128
        const val START_YEAR = 1900
        const val START_FUNDS = 20_000L
    }
}
