package com.rm.infill.sim

/** How much of each tile is hard surface that sheds rain rather than soaking it up. */
object Stormwater {
    /** The share of tile [i] that's hard surface, 0 to 100: its building, road or track, or else its ground. */
    fun hardness(map: CityMap, i: Int): Int {
        val t = map.buildingType[i].toInt()
        val building = if (t == 0) -1 else building(BuildingType.entries[t - 1])
        val road = RoadType.of(map.road[i])?.let { road(it) } ?: -1
        val track = if (map.rail[i] != Rail.NONE) TRACK else -1
        val ground = when (map.terrain[i]) {
            Terrain.TREES -> TREES
            Terrain.DIRT -> BARE
            else -> GRASS
        }
        return maxOf(building, road, track, if (building < 0 && road < 0 && track < 0) ground else -1)
    }

    private fun building(t: BuildingType): Int = when (t) {
        BuildingType.COTTAGE -> 40
        BuildingType.HOUSE -> 50
        BuildingType.LARGE_HOUSE -> 60
        BuildingType.ROW_HOUSES -> 70
        BuildingType.TENEMENT -> 85
        BuildingType.APARTMENTS -> 90
        BuildingType.APARTMENT_COURT -> 85
        BuildingType.GENERAL_STORE -> 60
        BuildingType.SHOP -> 80
        BuildingType.MAIN_STREET -> 90
        BuildingType.OFFICE_BLOCK, BuildingType.DEPARTMENT_STORE -> 95
        BuildingType.BANK, BuildingType.HOTEL -> 90
        BuildingType.WORKSHOP -> 70
        BuildingType.MILL -> 80
        BuildingType.WAREHOUSE -> 90
        BuildingType.FACTORY, BuildingType.WORKS -> 95
        BuildingType.COAL_PLANT -> 85
        BuildingType.POLICE_STATION, BuildingType.FIRE_STATION -> 80
        // Schools and hospitals have their yards and lawns.
        BuildingType.SCHOOL, BuildingType.HIGH_SCHOOL -> 65
        BuildingType.CLINIC -> 75
        BuildingType.HOSPITAL -> 70
        BuildingType.PARK -> 5
        BuildingType.STATION, BuildingType.STATION_NS -> 75
        BuildingType.FREIGHT_YARD, BuildingType.FREIGHT_YARD_NS -> 70
        BuildingType.PUMPING_STATION -> 60
        BuildingType.WELL_FIELD -> 10
        BuildingType.WATER_TOWER -> 40
        BuildingType.OUTFALL, BuildingType.STORM_OUTFALL -> 20
        BuildingType.STORM_POND -> 0
    }

    private fun road(t: RoadType): Int = when (t) {
        RoadType.DIRT -> 45
        RoadType.GRAVEL -> 35
        RoadType.LANE -> 30
        RoadType.STREET, RoadType.ONE_WAY_STREET -> 90
        RoadType.AVENUE, RoadType.ONE_WAY_AVENUE -> 95
        // The median soaks some up.
        RoadType.BOULEVARD -> 80
    }

    private const val TRACK = 25
    private const val TREES = 5
    private const val GRASS = 10
    private const val BARE = 30
}
