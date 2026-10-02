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

    /** The sprite for a building type's ordinal and one of its [variants], within a look. */
    fun sprite(type: Int, variant: Int): Int = first[type] + variant % count[type]
}
