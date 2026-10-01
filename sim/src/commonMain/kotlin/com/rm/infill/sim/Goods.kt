package com.rm.infill.sim

/**
 * What's made and carried, in loads a month: food, timber, ore and coal from
 * the land, lumber and metal from the mills and foundries, and goods from the
 * factories. [price] is what a load fetches; one brought in costs more.
 */
enum class Good(val price: Double) {
    FOOD(1.0),
    TIMBER(0.8),
    ORE(1.0),
    COAL(1.2),
    LUMBER(1.5),
    METAL(2.0),
    GOODS(3.0),
    ;

    /** What a load costs brought in from outside. */
    val importPrice get() = price * Balance.IMPORT_MARKUP

    companion object {
        val COUNT = entries.size
    }
}

/**
 * What a works on industrial land makes and from what, in loads a month for
 * each hundred jobs. Each one's kind is settled when it's first built, by
 * what the town is short of.
 */
enum class WorksKind(val output: Good, val rate: Int, val inputs: List<Pair<Good, Int>>) {
    SAWMILL(Good.LUMBER, 40, listOf(Good.TIMBER to 40)),
    FOUNDRY(Good.METAL, 30, listOf(Good.ORE to 30, Good.COAL to 20)),
    FACTORY(Good.GOODS, 30, listOf(Good.LUMBER to 15, Good.METAL to 15)),
}

/** What farms, woodlots and mines take from the land, and where each can go. */
object Land {
    /** What [t] makes, in loads a month for each hundred jobs, or null if it isn't a land works. */
    fun output(t: BuildingType): Pair<Good, Int>? = when (t) {
        BuildingType.FARM -> Good.FOOD to 100
        BuildingType.WOODLOT -> Good.TIMBER to 100
        BuildingType.MINE -> Good.ORE to 100
        BuildingType.COLLIERY -> Good.COAL to 100
        else -> null
    }

    fun seam(resource: Byte) = resource == Resource.ORE || resource == Resource.COAL

    /** Whether [t] can go on a lot with this [terrain] and [resource] under it. */
    fun fits(t: BuildingType, terrain: Byte, resource: Byte): Boolean = when (t) {
        BuildingType.MINE -> resource == Resource.ORE
        BuildingType.COLLIERY -> resource == Resource.COAL
        // A seam is for mining; elsewhere, the woods for timber and open land for farming.
        BuildingType.WOODLOT -> terrain == Terrain.TREES && !seam(resource)
        BuildingType.FARM -> terrain != Terrain.TREES && terrain != Terrain.WATER && !seam(resource)
        else -> true
    }
}
