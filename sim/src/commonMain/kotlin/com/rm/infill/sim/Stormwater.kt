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
        // Street trees soak up some of the rain off the road.
        val shaded = if (road >= 0 && map.streetTrees[i].toInt() != 0) road - Balance.STREET_TREE_SOAK else road
        return maxOf(building, shaded, track, if (building < 0 && road < 0 && track < 0) ground else -1)
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
        BuildingType.COAL_PLANT, BuildingType.OIL_PLANT, BuildingType.GAS_PLANT -> 85
        BuildingType.HYDRO_PLANT -> 70
        BuildingType.WIND_FARM -> 10
        BuildingType.SOLAR_FARM -> 40
        BuildingType.BATTERY -> 80
        BuildingType.RIVER_TURBINE, BuildingType.TIDAL_TURBINE, BuildingType.OFFSHORE_WIND -> 0
        BuildingType.FARM -> 10
        BuildingType.VOLUNTEER_HALL, BuildingType.LIBRARY, BuildingType.JAIL -> 70
        BuildingType.POLICE_HQ, BuildingType.EXCHANGE -> 85
        BuildingType.CELL_TOWER -> 20
        BuildingType.COURTHOUSE -> 75
        BuildingType.LADDER_COMPANY, BuildingType.AMBULANCE_STATION -> 85
        BuildingType.NURSING_HOME -> 75
        BuildingType.COLLEGE -> 65
        BuildingType.OFFICES -> 80
        BuildingType.OFFICE_BUILDING, BuildingType.OFFICE_TOWER, BuildingType.GLASS_TOWER -> 95
        BuildingType.TOWER_BLOCK, BuildingType.SLENDER_TOWER, BuildingType.HOTEL_TOWER, BuildingType.SKYSCRAPER, BuildingType.SUPERTALL -> 95
        // Big lots, mostly yard and field.
        BuildingType.FARMSTEAD, BuildingType.COUNTRY_HOUSE, BuildingType.ACREAGE_HOME -> 25
        BuildingType.CROSSROADS_STORE -> 45
        BuildingType.ROADHOUSE -> 70
        BuildingType.SHOPHOUSE, BuildingType.MAIN_STREET_FLATS, BuildingType.MIXED_BLOCK, BuildingType.PODIUM_TOWER -> 95
        BuildingType.WOODLOT -> 0
        BuildingType.MINE, BuildingType.COLLIERY -> 60
        BuildingType.OIL_WELL -> 30
        BuildingType.NUCLEAR_PLANT -> 80
        BuildingType.SUBSTATION -> 50
        BuildingType.DUMP -> 30
        BuildingType.INCINERATOR, BuildingType.RECYCLING -> 85
        BuildingType.POLICE_STATION, BuildingType.FIRE_STATION -> 80
        // Schools and hospitals have their yards and lawns.
        BuildingType.SCHOOL, BuildingType.HIGH_SCHOOL -> 65
        BuildingType.CLINIC, BuildingType.COOLING_CENTRE -> 75
        BuildingType.HOSPITAL -> 70
        BuildingType.PARK -> 5
        BuildingType.STATION, BuildingType.STATION_NS -> 75
        BuildingType.FREIGHT_YARD, BuildingType.FREIGHT_YARD_NS -> 70
        BuildingType.FREIGHT_TERMINAL, BuildingType.FREIGHT_TERMINAL_NS -> 90
        BuildingType.WHARF, BuildingType.WHARF_NS, BuildingType.DOCKS, BuildingType.DOCKS_NS -> 80
        BuildingType.AIRFIELD -> 20
        BuildingType.AIRPORT, BuildingType.INTERNATIONAL_AIRPORT -> 70
        BuildingType.CONTAINER_PORT, BuildingType.CONTAINER_PORT_NS -> 90
        BuildingType.PUMPING_STATION -> 60
        BuildingType.WELL_FIELD -> 10
        BuildingType.WATER_TOWER -> 40
        BuildingType.OUTFALL, BuildingType.STORM_OUTFALL -> 20
        BuildingType.SEWAGE_WORKS -> 55
        BuildingType.TRAM_DEPOT, BuildingType.BUS_GARAGE -> 90
        BuildingType.SUBWAY_STATION -> 60
        BuildingType.TREATMENT_PLANT -> 65
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
        RoadType.HIGHWAY -> 95
        RoadType.RAMP -> 90
    }

    private const val TRACK = 25
    private const val TREES = 5
    private const val GRASS = 10
    private const val BARE = 30
}
