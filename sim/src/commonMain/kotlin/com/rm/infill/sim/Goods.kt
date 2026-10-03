package com.rm.infill.sim

/**
 * What's made and carried, in loads a month: food, timber, ore, coal and
 * crude oil from the land; lumber, metal and fuel oil from the mills,
 * foundries and refineries; and goods from the factories. [price] is what a
 * load fetches; one brought in costs more.
 */
enum class Good(val price: Double, val fromLand: Boolean = false) {
    FOOD(1.0, fromLand = true),
    TIMBER(0.8, fromLand = true),
    ORE(1.0, fromLand = true),
    COAL(1.2, fromLand = true),
    OIL(1.4, fromLand = true),
    LUMBER(1.5),
    METAL(2.0),
    FUEL(2.0),
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
enum class WorksKind(val output: Good, val rate: Int, val inputs: List<Pair<Good, Int>>, val year: Int = 1900) {
    SAWMILL(Good.LUMBER, 40, listOf(Good.TIMBER to 40)),
    FOUNDRY(Good.METAL, 30, listOf(Good.ORE to 30, Good.COAL to 20)),
    FACTORY(Good.GOODS, 30, listOf(Good.LUMBER to 15, Good.METAL to 15)),
    REFINERY(Good.FUEL, 40, listOf(Good.OIL to 40), year = Balance.REFINERY_YEAR),
}

/** What farms, woodlots and mines take from the land, and where each can go. */
object Land {
    /** What [t] makes, in loads a month for each hundred jobs, or null if it isn't a land works. */
    fun output(t: BuildingType): Pair<Good, Int>? = when (t) {
        BuildingType.FARM, BuildingType.MARKET_GARDEN, BuildingType.ORCHARD_DEEP, BuildingType.ORCHARD_WIDE -> Good.FOOD to 100
        BuildingType.WOODLOT -> Good.TIMBER to 100
        BuildingType.MINE -> Good.ORE to 100
        BuildingType.COLLIERY -> Good.COAL to 100
        BuildingType.OIL_WELL -> Good.OIL to 100
        else -> null
    }

    fun seam(resource: Byte) = resource == Resource.ORE || resource == Resource.COAL || resource == Resource.OIL

    /** Whether [t] can go on a lot with this [terrain] and [resource] under it. */
    fun fits(t: BuildingType, terrain: Byte, resource: Byte): Boolean = when (t) {
        BuildingType.MINE -> resource == Resource.ORE
        BuildingType.COLLIERY -> resource == Resource.COAL
        BuildingType.OIL_WELL -> resource == Resource.OIL
        // A seam is for mining; elsewhere, the woods for timber and open land for farming.
        BuildingType.WOODLOT -> terrain == Terrain.TREES && !seam(resource)
        BuildingType.FARM, BuildingType.MARKET_GARDEN, BuildingType.ORCHARD_DEEP, BuildingType.ORCHARD_WIDE -> terrain != Terrain.TREES && terrain != Terrain.WATER && !seam(resource)
        else -> true
    }
}
