package com.rm.infill.map

import com.rm.infill.sim.BuildingType

/** Which atlas sprites draw each kind of building, and how many variants there are. */
internal object BuildingSprites {
    private val first = IntArray(BuildingType.entries.size)
    private val count = IntArray(BuildingType.entries.size)

    /** How many rows below a building's top row its sprite can reach up from: its height and how far it rises above it. */
    var rows = 0
        private set

    init {
        for (t in BuildingType.entries) {
            val (f, n) = when (t) {
                BuildingType.COTTAGE -> Atlas.COTTAGE to Atlas.COTTAGE_COUNT
                BuildingType.CABIN -> Atlas.CABIN to Atlas.CABIN_COUNT
                BuildingType.SMALLHOLDING_DEEP -> Atlas.SMALLHOLDING_DEEP to Atlas.SMALLHOLDING_DEEP_COUNT
                BuildingType.SMALLHOLDING_WIDE -> Atlas.SMALLHOLDING_WIDE to Atlas.SMALLHOLDING_WIDE_COUNT
                BuildingType.VILLA_DEEP -> Atlas.VILLA_DEEP to Atlas.VILLA_DEEP_COUNT
                BuildingType.VILLA_WIDE -> Atlas.VILLA_WIDE to Atlas.VILLA_WIDE_COUNT
                BuildingType.MANSION -> Atlas.MANSION to Atlas.MANSION_COUNT
                BuildingType.TERRACE_DEEP -> Atlas.TERRACE_DEEP to Atlas.TERRACE_DEEP_COUNT
                BuildingType.TERRACE_WIDE -> Atlas.TERRACE_WIDE to Atlas.TERRACE_WIDE_COUNT
                BuildingType.COURT_TENEMENTS -> Atlas.COURT_TENEMENTS to Atlas.COURT_TENEMENTS_COUNT
                BuildingType.SLAB_DEEP -> Atlas.SLAB_DEEP to Atlas.SLAB_DEEP_COUNT
                BuildingType.SLAB_WIDE -> Atlas.SLAB_WIDE to Atlas.SLAB_WIDE_COUNT
                BuildingType.STOREFRONTS_DEEP -> Atlas.STOREFRONTS_DEEP to Atlas.STOREFRONTS_DEEP_COUNT
                BuildingType.STOREFRONTS_WIDE -> Atlas.STOREFRONTS_WIDE to Atlas.STOREFRONTS_WIDE_COUNT
                BuildingType.COVERED_MARKET -> Atlas.COVERED_MARKET to Atlas.COVERED_MARKET_COUNT
                BuildingType.ARCADE_DEEP -> Atlas.ARCADE_DEEP to Atlas.ARCADE_DEEP_COUNT
                BuildingType.ARCADE_WIDE -> Atlas.ARCADE_WIDE to Atlas.ARCADE_WIDE_COUNT
                BuildingType.EMPORIUM -> Atlas.EMPORIUM to Atlas.EMPORIUM_COUNT
                BuildingType.STORE_BLOCK_DEEP -> Atlas.STORE_BLOCK_DEEP to Atlas.STORE_BLOCK_DEEP_COUNT
                BuildingType.STORE_BLOCK_WIDE -> Atlas.STORE_BLOCK_WIDE to Atlas.STORE_BLOCK_WIDE_COUNT
                BuildingType.ROADHOUSE_DEEP -> Atlas.ROADSIDE_DEEP to Atlas.ROADSIDE_DEEP_COUNT
                BuildingType.FEED_STORE -> Atlas.FEED_STORE to Atlas.FEED_STORE_COUNT
                BuildingType.LUMBER_YARD_DEEP -> Atlas.LUMBER_YARD_DEEP to Atlas.LUMBER_YARD_DEEP_COUNT
                BuildingType.LUMBER_YARD_WIDE -> Atlas.LUMBER_YARD_WIDE to Atlas.LUMBER_YARD_WIDE_COUNT
                BuildingType.BRICKWORKS -> Atlas.BRICKWORKS to Atlas.BRICKWORKS_COUNT
                BuildingType.SHEDS_DEEP -> Atlas.SHEDS_DEEP to Atlas.SHEDS_DEEP_COUNT
                BuildingType.SHEDS_WIDE -> Atlas.SHEDS_WIDE to Atlas.SHEDS_WIDE_COUNT
                BuildingType.STOREHOUSES -> Atlas.STOREHOUSES to Atlas.STOREHOUSES_COUNT
                BuildingType.FOUNDRY -> Atlas.FOUNDRY to Atlas.FOUNDRY_COUNT
                BuildingType.MACHINE_SHOP_DEEP -> Atlas.MACHINE_SHOP_DEEP to Atlas.MACHINE_SHOP_DEEP_COUNT
                BuildingType.MACHINE_SHOP_WIDE -> Atlas.MACHINE_SHOP_WIDE to Atlas.MACHINE_SHOP_WIDE_COUNT
                BuildingType.CHAMBERS_DEEP -> Atlas.CHAMBERS_DEEP to Atlas.CHAMBERS_DEEP_COUNT
                BuildingType.CHAMBERS_WIDE -> Atlas.CHAMBERS_WIDE to Atlas.CHAMBERS_WIDE_COUNT
                BuildingType.OFFICE_PARK -> Atlas.OFFICE_PARK to Atlas.OFFICE_PARK_COUNT
                BuildingType.OFFICE_ROW_DEEP -> Atlas.OFFICE_ROW_DEEP to Atlas.OFFICE_ROW_DEEP_COUNT
                BuildingType.OFFICE_ROW_WIDE -> Atlas.OFFICE_ROW_WIDE to Atlas.OFFICE_ROW_WIDE_COUNT
                BuildingType.OFFICE_COURT -> Atlas.OFFICE_COURT to Atlas.OFFICE_COURT_COUNT
                BuildingType.SLIM_OFFICES -> Atlas.SLIM_OFFICES to Atlas.SLIM_OFFICES_COUNT
                BuildingType.OFFICE_SLAB_DEEP -> Atlas.OFFICE_SLAB_DEEP to Atlas.OFFICE_SLAB_DEEP_COUNT
                BuildingType.OFFICE_SLAB_WIDE -> Atlas.OFFICE_SLAB_WIDE to Atlas.OFFICE_SLAB_WIDE_COUNT
                BuildingType.TWIN_SHOPHOUSES_DEEP -> Atlas.TWIN_SHOPHOUSES_DEEP to Atlas.TWIN_SHOPHOUSES_DEEP_COUNT
                BuildingType.TWIN_SHOPHOUSES_WIDE -> Atlas.TWIN_SHOPHOUSES_WIDE to Atlas.TWIN_SHOPHOUSES_WIDE_COUNT
                BuildingType.CORNER_PARADE -> Atlas.STREET_PARADE to Atlas.STREET_PARADE_COUNT
                BuildingType.SHOPS_AND_FLATS_DEEP -> Atlas.SHOPS_AND_FLATS_DEEP to Atlas.SHOPS_AND_FLATS_DEEP_COUNT
                BuildingType.SHOPS_AND_FLATS_WIDE -> Atlas.SHOPS_AND_FLATS_WIDE to Atlas.SHOPS_AND_FLATS_WIDE_COUNT
                BuildingType.PARADE_BLOCK -> Atlas.PARADE_BLOCK to Atlas.PARADE_BLOCK_COUNT
                BuildingType.MIXED_SLAB_DEEP -> Atlas.MIXED_SLAB_DEEP to Atlas.MIXED_SLAB_DEEP_COUNT
                BuildingType.MIXED_SLAB_WIDE -> Atlas.MIXED_SLAB_WIDE to Atlas.MIXED_SLAB_WIDE_COUNT
                BuildingType.MIXED_COURT -> Atlas.MIXED_COURT to Atlas.MIXED_COURT_COUNT
                BuildingType.MARKET_GARDEN -> Atlas.MARKET_GARDEN to Atlas.MARKET_GARDEN_COUNT
                BuildingType.ORCHARD_DEEP -> Atlas.ORCHARD_DEEP to Atlas.ORCHARD_DEEP_COUNT
                BuildingType.ORCHARD_WIDE -> Atlas.ORCHARD_WIDE to Atlas.ORCHARD_WIDE_COUNT
                BuildingType.SHOPHOUSE -> Atlas.SHOPHOUSE to Atlas.SHOPHOUSE_COUNT
                BuildingType.MAIN_STREET_FLATS -> Atlas.FLATS_OVER_SHOPS to Atlas.FLATS_OVER_SHOPS_COUNT
                BuildingType.MIXED_BLOCK -> Atlas.MIXED_BLOCK to Atlas.MIXED_BLOCK_COUNT
                BuildingType.PODIUM_TOWER -> Atlas.PODIUM_TOWER to Atlas.PODIUM_TOWER_COUNT
                BuildingType.TOWER_BLOCK -> Atlas.HIGHRISE to Atlas.HIGHRISE_COUNT
                BuildingType.SLENDER_TOWER -> Atlas.SLENDER_TOWER to Atlas.SLENDER_TOWER_COUNT
                BuildingType.FARMSTEAD -> Atlas.FARMSTEAD to Atlas.FARMSTEAD_COUNT
                BuildingType.COUNTRY_HOUSE -> Atlas.COUNTRY_HOUSE to Atlas.COUNTRY_HOUSE_COUNT
                BuildingType.ACREAGE_HOME -> Atlas.ACREAGE_HOME to Atlas.ACREAGE_HOME_COUNT
                BuildingType.HOTEL_TOWER -> Atlas.TALL_HOTEL to Atlas.TALL_HOTEL_COUNT
                BuildingType.CROSSROADS_STORE -> Atlas.CROSSROADS_STORE to Atlas.CROSSROADS_STORE_COUNT
                BuildingType.ROADHOUSE -> Atlas.ROADHOUSE to Atlas.ROADHOUSE_COUNT
                BuildingType.SKYSCRAPER -> Atlas.SKYSCRAPER to Atlas.SKYSCRAPER_COUNT
                BuildingType.SUPERTALL -> Atlas.SUPERTALL to Atlas.SUPERTALL_COUNT
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
                // Two looks for each side the track can be on.
                BuildingType.STATION -> Atlas.STATION_EW to Atlas.STATION_EW_COUNT
                BuildingType.STATION_NS -> Atlas.STATION_NS to Atlas.STATION_NS_COUNT
                BuildingType.FREIGHT_YARD -> Atlas.YARD_EW to Atlas.YARD_EW_COUNT
                BuildingType.FREIGHT_YARD_NS -> Atlas.YARD_NS to Atlas.YARD_NS_COUNT
                BuildingType.FREIGHT_TERMINAL -> Atlas.TERMINAL_EW to Atlas.TERMINAL_EW_COUNT
                BuildingType.FREIGHT_TERMINAL_NS -> Atlas.TERMINAL_NS to Atlas.TERMINAL_NS_COUNT
                BuildingType.AIRFIELD -> Atlas.AIRFIELD to Atlas.AIRFIELD_COUNT
                BuildingType.AIRPORT -> Atlas.AIRPORT_MID to Atlas.AIRPORT_MID_COUNT
                BuildingType.INTERNATIONAL_AIRPORT -> Atlas.AIRPORT_BIG to Atlas.AIRPORT_BIG_COUNT
                BuildingType.WHARF -> Atlas.WHARF_EW to Atlas.WHARF_EW_COUNT
                BuildingType.WHARF_NS -> Atlas.WHARF_NS to Atlas.WHARF_NS_COUNT
                BuildingType.DOCKS -> Atlas.DOCKS_EW to Atlas.DOCKS_EW_COUNT
                BuildingType.DOCKS_NS -> Atlas.DOCKS_NS to Atlas.DOCKS_NS_COUNT
                BuildingType.CONTAINER_PORT -> Atlas.BOXPORT_EW to Atlas.BOXPORT_EW_COUNT
                BuildingType.CONTAINER_PORT_NS -> Atlas.BOXPORT_NS to Atlas.BOXPORT_NS_COUNT
                BuildingType.PUMPING_STATION -> Atlas.PUMPING_STATION to Atlas.PUMPING_STATION_COUNT
                BuildingType.WELL_FIELD -> Atlas.WELL_FIELD to Atlas.WELL_FIELD_COUNT
                BuildingType.WATER_TOWER -> Atlas.TOWER to Atlas.TOWER_COUNT
                BuildingType.OUTFALL -> Atlas.SEWER_OUTFALL to Atlas.SEWER_OUTFALL_COUNT
                BuildingType.STORM_POND -> Atlas.STORM_POND to Atlas.STORM_POND_COUNT
                BuildingType.STORM_OUTFALL -> Atlas.STORM_OUTFALL to Atlas.STORM_OUTFALL_COUNT
                BuildingType.SCHOOL -> Atlas.SCHOOL to Atlas.SCHOOL_COUNT
                BuildingType.HIGH_SCHOOL -> Atlas.HIGH_SCHOOL to Atlas.HIGH_SCHOOL_COUNT
                BuildingType.CLINIC -> Atlas.CLINIC to Atlas.CLINIC_COUNT
                BuildingType.HOSPITAL -> Atlas.HOSPITAL to Atlas.HOSPITAL_COUNT
                BuildingType.ROW_HOUSES -> Atlas.ROW_HOUSES to Atlas.ROW_HOUSES_COUNT
                BuildingType.APARTMENTS -> Atlas.APARTMENTS to Atlas.APARTMENTS_COUNT
                BuildingType.APARTMENT_COURT -> Atlas.APARTMENT_COURT to Atlas.APARTMENT_COURT_COUNT
                BuildingType.MAIN_STREET -> Atlas.MAIN_STREET to Atlas.MAIN_STREET_COUNT
                BuildingType.OFFICE_BLOCK -> Atlas.OFFICE_BLOCK to Atlas.OFFICE_BLOCK_COUNT
                BuildingType.DEPARTMENT_STORE -> Atlas.DEPARTMENT_STORE to Atlas.DEPARTMENT_STORE_COUNT
                BuildingType.WORKS -> Atlas.WORKS to Atlas.WORKS_COUNT
                BuildingType.SEWAGE_WORKS -> Atlas.SEWAGE_WORKS to Atlas.SEWAGE_WORKS_COUNT
                BuildingType.TRAM_DEPOT -> Atlas.TRAM_DEPOT to Atlas.TRAM_DEPOT_COUNT
                BuildingType.BUS_GARAGE -> Atlas.BUS_GARAGE to Atlas.BUS_GARAGE_COUNT
                BuildingType.SUBWAY_STATION -> Atlas.SUBWAY_STATION to Atlas.SUBWAY_STATION_COUNT
                BuildingType.TREATMENT_PLANT -> Atlas.TREATMENT_PLANT to Atlas.TREATMENT_PLANT_COUNT
                BuildingType.OIL_PLANT -> Atlas.OIL_PLANT to Atlas.OIL_PLANT_COUNT
                BuildingType.GAS_PLANT -> Atlas.GAS_PLANT to Atlas.GAS_PLANT_COUNT
                BuildingType.HYDRO_PLANT -> Atlas.HYDRO_PLANT to Atlas.HYDRO_PLANT_COUNT
                BuildingType.NUCLEAR_PLANT -> Atlas.NUCLEAR_PLANT to Atlas.NUCLEAR_PLANT_COUNT
                BuildingType.SUBSTATION -> Atlas.SUBSTATION to Atlas.SUBSTATION_COUNT
                BuildingType.DUMP -> Atlas.DUMP to Atlas.DUMP_COUNT
                BuildingType.INCINERATOR -> Atlas.INCINERATOR to Atlas.INCINERATOR_COUNT
                BuildingType.RECYCLING -> Atlas.RECYCLING to Atlas.RECYCLING_COUNT
                BuildingType.FARM -> Atlas.FARM to Atlas.FARM_COUNT
                BuildingType.WOODLOT -> Atlas.WOODLOT to Atlas.WOODLOT_COUNT
                BuildingType.MINE -> Atlas.MINE to Atlas.MINE_COUNT
                BuildingType.COLLIERY -> Atlas.COLLIERY to Atlas.COLLIERY_COUNT
                BuildingType.OIL_WELL -> Atlas.OIL_WELL to Atlas.OIL_WELL_COUNT
                BuildingType.OFFICES -> Atlas.OFFICES to Atlas.OFFICES_COUNT
                BuildingType.OFFICE_BUILDING -> Atlas.OFFICE_BUILDING to Atlas.OFFICE_BUILDING_COUNT
                BuildingType.OFFICE_TOWER -> Atlas.OFFICE_TOWER to Atlas.OFFICE_TOWER_COUNT
                BuildingType.GLASS_TOWER -> Atlas.GLASS_TOWER to Atlas.GLASS_TOWER_COUNT
                BuildingType.VOLUNTEER_HALL -> Atlas.VOLUNTEER_HALL to Atlas.VOLUNTEER_HALL_COUNT
                BuildingType.LADDER_COMPANY -> Atlas.LADDER_COMPANY to Atlas.LADDER_COMPANY_COUNT
                BuildingType.AMBULANCE_STATION -> Atlas.AMBULANCE_STATION to Atlas.AMBULANCE_STATION_COUNT
                BuildingType.NURSING_HOME -> Atlas.NURSING_HOME to Atlas.NURSING_HOME_COUNT
                BuildingType.COOLING_CENTRE -> Atlas.COOLING_CENTRE to Atlas.COOLING_CENTRE_COUNT
                BuildingType.LIBRARY -> Atlas.LIBRARY to Atlas.LIBRARY_COUNT
                BuildingType.ELEMENTARY_SCHOOL -> Atlas.ELEMENTARY_SCHOOL to Atlas.ELEMENTARY_SCHOOL_COUNT
                BuildingType.COMMUNITY_SCHOOL -> Atlas.COMMUNITY_SCHOOL to Atlas.COMMUNITY_SCHOOL_COUNT
                BuildingType.COMPOSITE_HIGH -> Atlas.COMPOSITE_HIGH to Atlas.COMPOSITE_HIGH_COUNT
                BuildingType.BRANCH_LIBRARY -> Atlas.BRANCH_LIBRARY to Atlas.BRANCH_LIBRARY_COUNT
                BuildingType.MEDIA_LIBRARY -> Atlas.MEDIA_LIBRARY to Atlas.MEDIA_LIBRARY_COUNT
                BuildingType.HEALTH_CENTRE -> Atlas.HEALTH_CENTRE to Atlas.HEALTH_CENTRE_COUNT
                BuildingType.COMMUNITY_HEALTH -> Atlas.COMMUNITY_HEALTH to Atlas.COMMUNITY_HEALTH_COUNT
                BuildingType.GENERAL_HOSPITAL -> Atlas.GENERAL_HOSPITAL to Atlas.GENERAL_HOSPITAL_COUNT
                BuildingType.MEDICAL_CENTRE -> Atlas.MEDICAL_CENTRE to Atlas.MEDICAL_CENTRE_COUNT
                BuildingType.CARE_HOME -> Atlas.CARE_HOME to Atlas.CARE_HOME_COUNT
                BuildingType.MOTOR_FIRE_STATION -> Atlas.MOTOR_FIRE_STATION to Atlas.MOTOR_FIRE_STATION_COUNT
                BuildingType.FIRE_HALL -> Atlas.FIRE_HALL to Atlas.FIRE_HALL_COUNT
                BuildingType.PRECINCT -> Atlas.PRECINCT to Atlas.PRECINCT_COUNT
                BuildingType.COMMUNITY_POLICING -> Atlas.COMMUNITY_POLICING to Atlas.COMMUNITY_POLICING_COUNT
                BuildingType.PLAYGROUND -> Atlas.PLAYGROUND to Atlas.PLAYGROUND_COUNT
                BuildingType.TOWN_SQUARE -> Atlas.TOWN_SQUARE to Atlas.TOWN_SQUARE_COUNT
                BuildingType.PLAZA -> Atlas.PLAZA to Atlas.PLAZA_COUNT
                BuildingType.FORMAL_GARDEN -> Atlas.FORMAL_GARDEN to Atlas.FORMAL_GARDEN_COUNT
                BuildingType.CITY_PARK -> Atlas.CITY_PARK to Atlas.CITY_PARK_COUNT
                BuildingType.ALLOTMENTS -> Atlas.ALLOTMENTS to Atlas.ALLOTMENTS_COUNT
                BuildingType.COMMUNITY_GARDEN -> Atlas.COMMUNITY_GARDEN to Atlas.COMMUNITY_GARDEN_COUNT
                BuildingType.POCKET_PARK -> Atlas.POCKET_PARK to Atlas.POCKET_PARK_COUNT
                BuildingType.URBAN_WOODLAND -> Atlas.URBAN_WOODLAND to Atlas.URBAN_WOODLAND_COUNT
                BuildingType.BOTANICAL_GARDEN -> Atlas.BOTANICAL_GARDEN to Atlas.BOTANICAL_GARDEN_COUNT
                BuildingType.WETLAND_RESERVE -> Atlas.WETLAND_RESERVE to Atlas.WETLAND_RESERVE_COUNT
                BuildingType.GREENWAY -> Atlas.GREENWAY to Atlas.GREENWAY_COUNT
                BuildingType.DOG_PARK -> Atlas.DOG_PARK to Atlas.DOG_PARK_COUNT
                BuildingType.SPORTS_GROUND -> Atlas.SPORTS_GROUND to Atlas.SPORTS_GROUND_COUNT
                BuildingType.LIT_FIELDS -> Atlas.LIT_FIELDS to Atlas.LIT_FIELDS_COUNT
                BuildingType.PUBLIC_BATHS -> Atlas.PUBLIC_BATHS to Atlas.PUBLIC_BATHS_COUNT
                BuildingType.SWIMMING_POOL -> Atlas.SWIMMING_POOL to Atlas.SWIMMING_POOL_COUNT
                BuildingType.AQUATIC_CENTRE -> Atlas.AQUATIC_CENTRE to Atlas.AQUATIC_CENTRE_COUNT
                BuildingType.TENNIS_COURTS -> Atlas.TENNIS_COURTS to Atlas.TENNIS_COURTS_COUNT
                BuildingType.ICE_RINK -> Atlas.ICE_RINK to Atlas.ICE_RINK_COUNT
                BuildingType.BALLPARK -> Atlas.BALLPARK to Atlas.BALLPARK_COUNT
                BuildingType.ARENA -> Atlas.ARENA to Atlas.ARENA_COUNT
                BuildingType.STADIUM -> Atlas.STADIUM to Atlas.STADIUM_COUNT
                BuildingType.GOLF_COURSE -> Atlas.GOLF_COURSE to Atlas.GOLF_COURSE_COUNT
                BuildingType.SKATE_PARK -> Atlas.SKATE_PARK to Atlas.SKATE_PARK_COUNT
                BuildingType.REC_CENTRE -> Atlas.REC_CENTRE to Atlas.REC_CENTRE_COUNT
                BuildingType.BANDSTAND -> Atlas.BANDSTAND to Atlas.BANDSTAND_COUNT
                BuildingType.VARIETY_THEATRE -> Atlas.VARIETY_THEATRE to Atlas.VARIETY_THEATRE_COUNT
                BuildingType.PICTURE_PALACE -> Atlas.PICTURE_PALACE to Atlas.PICTURE_PALACE_COUNT
                BuildingType.MULTIPLEX -> Atlas.MULTIPLEX to Atlas.MULTIPLEX_COUNT
                BuildingType.OPERA_HOUSE -> Atlas.OPERA_HOUSE to Atlas.OPERA_HOUSE_COUNT
                BuildingType.MUSEUM -> Atlas.MUSEUM to Atlas.MUSEUM_COUNT
                BuildingType.ART_GALLERY -> Atlas.ART_GALLERY to Atlas.ART_GALLERY_COUNT
                BuildingType.CONCERT_HALL -> Atlas.CONCERT_HALL to Atlas.CONCERT_HALL_COUNT
                BuildingType.ZOO -> Atlas.ZOO to Atlas.ZOO_COUNT
                BuildingType.FAIRGROUND -> Atlas.FAIRGROUND to Atlas.FAIRGROUND_COUNT
                BuildingType.AMUSEMENT_PARK -> Atlas.AMUSEMENT_PARK to Atlas.AMUSEMENT_PARK_COUNT
                BuildingType.DRIVE_IN -> Atlas.DRIVE_IN to Atlas.DRIVE_IN_COUNT
                BuildingType.AQUARIUM -> Atlas.AQUARIUM to Atlas.AQUARIUM_COUNT
                BuildingType.CONVENTION_CENTRE -> Atlas.CONVENTION_CENTRE to Atlas.CONVENTION_CENTRE_COUNT
                BuildingType.TOWN_HALL -> Atlas.TOWN_HALL to Atlas.TOWN_HALL_COUNT
                BuildingType.CITY_HALL -> Atlas.CITY_HALL to Atlas.CITY_HALL_COUNT
                BuildingType.CIVIC_CENTRE -> Atlas.CIVIC_CENTRE to Atlas.CIVIC_CENTRE_COUNT
                BuildingType.POST_OFFICE -> Atlas.POST_OFFICE to Atlas.POST_OFFICE_COUNT
                BuildingType.SHELTER -> Atlas.SHELTER to Atlas.SHELTER_COUNT
                BuildingType.FOUNDERS_STATUE -> Atlas.FOUNDERS_STATUE to Atlas.FOUNDERS_STATUE_COUNT
                BuildingType.MAYORS_MANSION -> Atlas.MAYORS_MANSION to Atlas.MAYORS_MANSION_COUNT
                BuildingType.EXHIBITION_HALL -> Atlas.EXHIBITION_HALL to Atlas.EXHIBITION_HALL_COUNT
                BuildingType.OBSERVATION_TOWER -> Atlas.OBSERVATION_TOWER to Atlas.OBSERVATION_TOWER_COUNT
                BuildingType.CONSERVATORY -> Atlas.CONSERVATORY to Atlas.CONSERVATORY_COUNT
                BuildingType.TOWN_MUSEUM -> Atlas.TOWN_MUSEUM to Atlas.TOWN_MUSEUM_COUNT
                BuildingType.CEMETERY -> Atlas.CEMETERY to Atlas.CEMETERY_COUNT
                BuildingType.MEMORIAL_GARDEN -> Atlas.MEMORIAL_GARDEN to Atlas.MEMORIAL_GARDEN_COUNT
                BuildingType.FOUNTAIN -> Atlas.FOUNTAIN to Atlas.FOUNTAIN_COUNT
                BuildingType.CLOCK_TOWER -> Atlas.CLOCK_TOWER to Atlas.CLOCK_TOWER_COUNT
                BuildingType.WAR_MEMORIAL -> Atlas.WAR_MEMORIAL to Atlas.WAR_MEMORIAL_COUNT
                BuildingType.KINDERGARTEN -> Atlas.KINDERGARTEN to Atlas.KINDERGARTEN_COUNT
                BuildingType.JUNIOR_HIGH -> Atlas.JUNIOR_HIGH to Atlas.JUNIOR_HIGH_COUNT
                BuildingType.VOCATIONAL_SCHOOL -> Atlas.VOCATIONAL_SCHOOL to Atlas.VOCATIONAL_SCHOOL_COUNT
                BuildingType.CENTRAL_LIBRARY -> Atlas.CENTRAL_LIBRARY to Atlas.CENTRAL_LIBRARY_COUNT
                BuildingType.COMMUNITY_COLLEGE -> Atlas.COMMUNITY_COLLEGE to Atlas.COMMUNITY_COLLEGE_COUNT
                BuildingType.UNIVERSITY -> Atlas.UNIVERSITY to Atlas.UNIVERSITY_COUNT
                BuildingType.RESEARCH_CAMPUS -> Atlas.RESEARCH_CAMPUS to Atlas.RESEARCH_CAMPUS_COUNT
                BuildingType.SANATORIUM -> Atlas.SANATORIUM to Atlas.SANATORIUM_COUNT
                BuildingType.PUBLIC_HEALTH_OFFICE -> Atlas.PUBLIC_HEALTH_OFFICE to Atlas.PUBLIC_HEALTH_OFFICE_COUNT
                BuildingType.POLICE_BOX -> Atlas.POLICE_BOX to Atlas.POLICE_BOX_COUNT
                BuildingType.TRAFFIC_POLICE -> Atlas.TRAFFIC_POLICE to Atlas.TRAFFIC_POLICE_COUNT
                BuildingType.FIREBOAT_STATION -> Atlas.FIREBOAT_STATION to Atlas.FIREBOAT_STATION_COUNT
                BuildingType.PULVERIZED_COAL -> Atlas.PULVERIZED_COAL to Atlas.PULVERIZED_COAL_COUNT
                BuildingType.SUPERCRITICAL_COAL -> Atlas.SUPERCRITICAL_COAL to Atlas.SUPERCRITICAL_COAL_COUNT
                BuildingType.LARGE_OIL -> Atlas.LARGE_OIL to Atlas.LARGE_OIL_COUNT
                BuildingType.COMBINED_CYCLE -> Atlas.COMBINED_CYCLE to Atlas.COMBINED_CYCLE_COUNT
                BuildingType.HYDRO_STATION -> Atlas.HYDRO_STATION to Atlas.HYDRO_STATION_COUNT
                BuildingType.ADVANCED_REACTOR -> Atlas.ADVANCED_REACTOR to Atlas.ADVANCED_REACTOR_COUNT
                BuildingType.TALL_WIND -> Atlas.TALL_WIND to Atlas.TALL_WIND_COUNT
                BuildingType.BIFACIAL_SOLAR -> Atlas.BIFACIAL_SOLAR to Atlas.BIFACIAL_SOLAR_COUNT
                BuildingType.FLOATING_OFFSHORE -> Atlas.FLOATING_OFFSHORE to Atlas.FLOATING_OFFSHORE_COUNT
                BuildingType.TIDAL_ARRAY -> Atlas.TIDAL_ARRAY to Atlas.TIDAL_ARRAY_COUNT
                BuildingType.HYDRO_DAM -> Atlas.HYDRO_DAM to Atlas.HYDRO_DAM_COUNT
                BuildingType.PUMPED_STORAGE -> Atlas.PUMPED_STORAGE to Atlas.PUMPED_STORAGE_COUNT
                BuildingType.GEOTHERMAL -> Atlas.GEOTHERMAL to Atlas.GEOTHERMAL_COUNT
                BuildingType.SMALL_REACTOR -> Atlas.SMALL_REACTOR to Atlas.SMALL_REACTOR_COUNT
                BuildingType.LONG_STORAGE -> Atlas.LONG_STORAGE to Atlas.LONG_STORAGE_COUNT
                BuildingType.SANITARY_LANDFILL -> Atlas.SANITARY_LANDFILL to Atlas.SANITARY_LANDFILL_COUNT
                BuildingType.WASTE_TO_ENERGY -> Atlas.WASTE_TO_ENERGY to Atlas.WASTE_TO_ENERGY_COUNT
                BuildingType.MATERIALS_RECOVERY -> Atlas.MATERIALS_RECOVERY to Atlas.MATERIALS_RECOVERY_COUNT
                BuildingType.ADVANCED_SORTING -> Atlas.ADVANCED_SORTING to Atlas.ADVANCED_SORTING_COUNT
                BuildingType.TRANSFER_STATION -> Atlas.TRANSFER_STATION to Atlas.TRANSFER_STATION_COUNT
                BuildingType.COMPOST_YARD -> Atlas.COMPOST_YARD to Atlas.COMPOST_YARD_COUNT
                BuildingType.LANDFILL_GAS -> Atlas.LANDFILL_GAS to Atlas.LANDFILL_GAS_COUNT
                BuildingType.BIOGAS -> Atlas.BIOGAS to Atlas.BIOGAS_COUNT
                BuildingType.COLLEGE -> Atlas.COLLEGE to Atlas.COLLEGE_COUNT
                BuildingType.POLICE_HQ -> Atlas.POLICE_HQ to Atlas.POLICE_HQ_COUNT
                BuildingType.EXCHANGE -> Atlas.EXCHANGE to Atlas.EXCHANGE_COUNT
                BuildingType.WIND_FARM -> Atlas.WIND_FARM to Atlas.WIND_FARM_COUNT
                BuildingType.SOLAR_FARM -> Atlas.SOLAR_FARM to Atlas.SOLAR_FARM_COUNT
                BuildingType.BATTERY -> Atlas.BATTERY to Atlas.BATTERY_COUNT
                BuildingType.RIVER_TURBINE -> Atlas.RIVER_TURBINE to Atlas.RIVER_TURBINE_COUNT
                BuildingType.TIDAL_TURBINE -> Atlas.TIDAL_TURBINE to Atlas.TIDAL_TURBINE_COUNT
                BuildingType.OFFSHORE_WIND -> Atlas.OFFSHORE_WIND to Atlas.OFFSHORE_WIND_COUNT
                BuildingType.CELL_TOWER -> Atlas.CELL_TOWER to Atlas.CELL_TOWER_COUNT
                BuildingType.COURTHOUSE -> Atlas.COURTHOUSE to Atlas.COURTHOUSE_COUNT
                BuildingType.JAIL -> Atlas.JAIL to Atlas.JAIL_COUNT
            }
            first[t.ordinal] = f
            count[t.ordinal] = n
            for (v in 0 until n) {
                val rise = Atlas.rects[(f + v) * 5 + 4]
                rows = maxOf(rows, t.height - 1 + (rise + MapRenderer.TILE - 1) / MapRenderer.TILE)
            }
        }
    }

    /** How many ways a building type's ordinal can look. */
    fun variants(type: Int): Int = count[type]

    /** Whether any of a building type's sprites has a stack that smokes or steams. */
    fun smokes(type: Int): Boolean = (0 until count[type]).any { Atlas.plumeCount[first[type] + it] > 0 }

    /** The sprite for a building type's ordinal and one of its [variants], within a look. */
    fun sprite(type: Int, variant: Int): Int = first[type] + variant % count[type]
}
