package com.rm.infill.sim

/**
 * Every kind of building. Zoned ones grow through four stages on a 1 by 1 lot;
 * [capacity] is residents for homes and jobs for everything else. Stage 1 needs
 * no power, since plenty of homes in 1900 had none, and later stages do.
 */
enum class BuildingType(
    val zone: Byte,
    val stage: Int,
    val capacity: Int,
    val width: Int = 1,
    val height: Int = 1,
    /** Pollution it gives off, spread over the tiles around it. */
    val pollution: Int = 0,
) {
    COTTAGE(Zone.RESIDENTIAL, 1, 5),
    HOUSE(Zone.RESIDENTIAL, 2, 9),
    LARGE_HOUSE(Zone.RESIDENTIAL, 3, 14),
    TENEMENT(Zone.RESIDENTIAL, 4, 32),

    GENERAL_STORE(Zone.COMMERCIAL, 1, 3),
    SHOP(Zone.COMMERCIAL, 2, 6),
    BANK(Zone.COMMERCIAL, 3, 10),
    HOTEL(Zone.COMMERCIAL, 4, 16),

    WORKSHOP(Zone.INDUSTRIAL, 1, 6, pollution = 4),
    MILL(Zone.INDUSTRIAL, 2, 12, pollution = 10),
    WAREHOUSE(Zone.INDUSTRIAL, 3, 16, pollution = 6),
    FACTORY(Zone.INDUSTRIAL, 4, 30, pollution = 18),

    COAL_PLANT(Zone.NONE, 0, 8, width = 2, height = 2, pollution = 30),

    POLICE_STATION(Zone.NONE, 0, 10, width = 2, height = 1),
    FIRE_STATION(Zone.NONE, 0, 12, width = 2, height = 2),
    PARK(Zone.NONE, 0, 0),
    ;

    /** A building the city runs rather than one that grows on zoned land. */
    val service get() = this == POLICE_STATION || this == FIRE_STATION || this == PARK

    val needsPower get() = stage >= 2

    /** The next stage up in the same zone, or null at the top. */
    val next: BuildingType? get() = entries.firstOrNull { it.zone == zone && zone != Zone.NONE && it.stage == stage + 1 }

    /** The stage below, or null if it's the first. */
    val previous: BuildingType? get() = entries.firstOrNull { it.zone == zone && zone != Zone.NONE && it.stage == stage - 1 }

    companion object {
        fun firstFor(zone: Byte): BuildingType = entries.first { it.zone == zone && it.stage == 1 }
    }
}

/** One building on the map. [x], [y] is its top left tile. [variant] picks how it looks. */
class Building(val id: Int, var type: BuildingType, val x: Int, val y: Int, val variant: Int) {
    /** Days since it was built or last grew. */
    var age = 0

    /** Days left of a fire, 0 when it isn't burning. */
    var burning = 0
}

/** The power line on a tile, if any. */
object Power {
    const val NONE: Byte = 0
    const val LINE: Byte = 1
}
