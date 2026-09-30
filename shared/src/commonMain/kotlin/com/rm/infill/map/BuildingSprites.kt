package com.rm.infill.map

import com.rm.infill.sim.BuildingType

/** Which atlas sprites draw each kind of building, and how many variants there are. */
internal object BuildingSprites {
    private val first = IntArray(BuildingType.entries.size)
    private val count = IntArray(BuildingType.entries.size)

    init {
        for (t in BuildingType.entries) {
            val (f, n) = when (t) {
                BuildingType.COTTAGE -> Atlas.COTTAGE to Atlas.COTTAGE_COUNT
                BuildingType.HOUSE -> Atlas.HOUSE to Atlas.HOUSE_COUNT
                BuildingType.LARGE_HOUSE -> Atlas.LARGE_HOUSE to Atlas.LARGE_HOUSE_COUNT
                BuildingType.TENEMENT -> Atlas.TENEMENT to Atlas.TENEMENT_COUNT
                BuildingType.GENERAL_STORE -> Atlas.GENERAL_STORE to Atlas.GENERAL_STORE_COUNT
                BuildingType.SHOP -> Atlas.SHOP to Atlas.SHOP_COUNT
                BuildingType.BANK -> Atlas.BANK to Atlas.BANK_COUNT
                BuildingType.HOTEL -> Atlas.HOTEL to Atlas.HOTEL_COUNT
                BuildingType.WORKSHOP -> Atlas.WORKSHOP to Atlas.WORKSHOP_COUNT
                BuildingType.MILL -> Atlas.MILL to Atlas.MILL_COUNT
                BuildingType.WAREHOUSE -> Atlas.WAREHOUSE to Atlas.WAREHOUSE_COUNT
                BuildingType.FACTORY -> Atlas.FACTORY to Atlas.FACTORY_COUNT
                BuildingType.COAL_PLANT -> Atlas.COAL_PLANT to Atlas.COAL_PLANT_COUNT
                BuildingType.POLICE_STATION -> Atlas.POLICE_STATION to Atlas.POLICE_STATION_COUNT
                BuildingType.FIRE_STATION -> Atlas.FIRE_STATION to Atlas.FIRE_STATION_COUNT
                BuildingType.PARK -> Atlas.PARK to Atlas.PARK_COUNT
            }
            first[t.ordinal] = f
            count[t.ordinal] = n
        }
    }

    /** The sprite for a building type's ordinal and its variant, within a look. */
    fun sprite(type: Int, variant: Int): Int = first[type] + (variant and 0xff) % count[type]
}
