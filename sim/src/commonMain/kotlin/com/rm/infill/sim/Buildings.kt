package com.rm.infill.sim

/**
 * How dense a zone may build: each zoned tile has one, and it caps how far up
 * the ladder its buildings go. Rural lots are big and grow only rural
 * buildings; towers go above high. Compare them by [rank], since they're
 * numbered in the order they came in.
 */
object Density {
    const val NONE: Byte = 0
    const val LOW: Byte = 1
    const val MEDIUM: Byte = 2
    const val HIGH: Byte = 3
    const val TOWER: Byte = 4
    const val RURAL: Byte = 5

    /** Where [d] stands, least dense first: rural 0 to tower 4, and nothing below all of them. */
    fun rank(d: Byte): Int = when (d) {
        RURAL -> 0
        LOW -> 1
        MEDIUM -> 2
        HIGH -> 3
        TOWER -> 4
        else -> -1
    }

    /** Whether zone [zone] can be zoned at [density]: rural for homes and shops, towers for all but works and farms. */
    fun fits(zone: Byte, density: Byte): Boolean = when (density) {
        RURAL -> zone == Zone.RESIDENTIAL || zone == Zone.COMMERCIAL
        TOWER -> zone == Zone.RESIDENTIAL || zone == Zone.COMMERCIAL || zone == Zone.OFFICE || zone == Zone.MIXED
        else -> true
    }
}

/**
 * Every kind of building. Zoned ones climb a ladder of [stage]s, one ladder a
 * zone running up through the densities, a few rungs with a choice of two.
 * [capacity] is residents for homes and jobs for everything else; a mixed
 * building has [capacity] residents over [jobs] shop jobs. A rung
 * needs the lot zoned for its [density], the utilities in [needs] (1 power, 2
 * mains water as well, 3 the sewer as well), its [year], the [appeal] and the
 * land [value] to pay for it, and takes [buildDays] to put up. Most stand on
 * one lot; the biggest take four.
 */
enum class BuildingType(
    val zone: Byte,
    val stage: Int,
    val capacity: Int,
    val width: Int = 1,
    val height: Int = 1,
    /** Pollution it gives off, spread over the tiles around it. */
    val pollution: Int = 0,
    val density: Byte = Density.NONE,
    val needs: Int = 0,
    val year: Int = 1900,
    val appeal: Int = 0,
    val value: Int = 0,
    val buildDays: Int = 0,
    /** For the works the city runs: how many years it's expected to go before it starts breaking down. */
    val life: Int = 0,
    /** Shop jobs under a mixed building's homes. */
    val jobs: Int = 0,
) {
    COTTAGE(Zone.RESIDENTIAL, 1, 5, density = Density.LOW, buildDays = 20),
    HOUSE(Zone.RESIDENTIAL, 2, 9, density = Density.LOW, needs = 1, appeal = 55, buildDays = 30),
    LARGE_HOUSE(Zone.RESIDENTIAL, 3, 14, density = Density.LOW, needs = 2, appeal = 63, buildDays = 45),
    ROW_HOUSES(Zone.RESIDENTIAL, 4, 22, density = Density.MEDIUM, needs = 2, appeal = 66, value = 70, buildDays = 60),
    TENEMENT(Zone.RESIDENTIAL, 5, 32, density = Density.MEDIUM, needs = 3, appeal = 70, value = 75, buildDays = 75),
    APARTMENTS(Zone.RESIDENTIAL, 6, 60, density = Density.HIGH, needs = 3, year = 1905, appeal = 72, value = 100, buildDays = 120),
    APARTMENT_COURT(Zone.RESIDENTIAL, 7, 260, width = 2, height = 2, density = Density.HIGH, needs = 3, year = 1915, appeal = 74, value = 120, buildDays = 200),
    /** Towers: a block of flats from the 1930s, then a slender glass one from 2000. */
    TOWER_BLOCK(Zone.RESIDENTIAL, 7, 600, width = 2, height = 2, density = Density.TOWER, needs = 3, year = 1930, appeal = 76, value = 150, buildDays = 300),
    SLENDER_TOWER(Zone.RESIDENTIAL, 8, 900, width = 2, height = 2, density = Density.TOWER, needs = 3, year = 2000, appeal = 80, value = 170, buildDays = 360),

    /** Homes over shops: a shop with rooms above, flats over a row of shops, a block over a parade, a tower on a podium of shops. */
    SHOPHOUSE(Zone.MIXED, 1, 4, density = Density.LOW, year = 1910, value = 40, buildDays = 40, jobs = 2),
    MAIN_STREET_FLATS(Zone.MIXED, 2, 14, density = Density.MEDIUM, needs = 2, year = 1910, appeal = 54, value = 70, buildDays = 80, jobs = 5),
    MIXED_BLOCK(Zone.MIXED, 3, 50, density = Density.HIGH, needs = 3, year = 1950, appeal = 64, value = 100, buildDays = 160, jobs = 14),
    PODIUM_TOWER(Zone.MIXED, 4, 400, width = 2, height = 2, density = Density.TOWER, needs = 3, year = 2000, appeal = 78, value = 150, buildDays = 320, jobs = 80),
    /** Homes over shops in other sizes: twin shophouses and a corner parade, shops and flats and a parade block, a slab and a court. */
    TWIN_SHOPHOUSES_DEEP(Zone.MIXED, 1, 8, height = 2, density = Density.LOW, year = 1910, value = 40, buildDays = 60, jobs = 4),
    TWIN_SHOPHOUSES_WIDE(Zone.MIXED, 1, 8, width = 2, density = Density.LOW, year = 1910, value = 40, buildDays = 60, jobs = 4),
    CORNER_PARADE(Zone.MIXED, 1, 16, width = 2, height = 2, density = Density.LOW, year = 1910, value = 45, buildDays = 90, jobs = 8),
    SHOPS_AND_FLATS_DEEP(Zone.MIXED, 2, 28, height = 2, density = Density.MEDIUM, needs = 2, year = 1910, appeal = 54, value = 70, buildDays = 120, jobs = 10),
    SHOPS_AND_FLATS_WIDE(Zone.MIXED, 2, 28, width = 2, density = Density.MEDIUM, needs = 2, year = 1910, appeal = 54, value = 70, buildDays = 120, jobs = 10),
    PARADE_BLOCK(Zone.MIXED, 2, 56, width = 2, height = 2, density = Density.MEDIUM, needs = 2, year = 1910, appeal = 56, value = 72, buildDays = 160, jobs = 20),
    MIXED_SLAB_DEEP(Zone.MIXED, 3, 100, height = 2, density = Density.HIGH, needs = 3, year = 1950, appeal = 64, value = 100, buildDays = 220, jobs = 28),
    MIXED_SLAB_WIDE(Zone.MIXED, 3, 100, width = 2, density = Density.HIGH, needs = 3, year = 1950, appeal = 64, value = 100, buildDays = 220, jobs = 28),
    MIXED_COURT(Zone.MIXED, 3, 200, width = 2, height = 2, density = Density.HIGH, needs = 3, year = 1950, appeal = 66, value = 105, buildDays = 280, jobs = 56),
    /**
     * In other sizes, so a lot of any shape has something to grow: a 1 by 2 runs
     * back from the road (DEEP) or along it (WIDE), the same building turned.
     */
    VILLA_DEEP(Zone.RESIDENTIAL, 3, 26, height = 2, density = Density.LOW, needs = 2, appeal = 63, buildDays = 60),
    VILLA_WIDE(Zone.RESIDENTIAL, 3, 26, width = 2, density = Density.LOW, needs = 2, appeal = 63, buildDays = 60),
    MANSION(Zone.RESIDENTIAL, 3, 40, width = 2, height = 2, density = Density.LOW, needs = 2, appeal = 68, value = 90, buildDays = 90),
    TERRACE_DEEP(Zone.RESIDENTIAL, 4, 44, height = 2, density = Density.MEDIUM, needs = 2, appeal = 66, value = 70, buildDays = 90),
    TERRACE_WIDE(Zone.RESIDENTIAL, 4, 44, width = 2, density = Density.MEDIUM, needs = 2, appeal = 66, value = 70, buildDays = 90),
    COURT_TENEMENTS(Zone.RESIDENTIAL, 5, 120, width = 2, height = 2, density = Density.MEDIUM, needs = 3, appeal = 70, value = 75, buildDays = 140),
    SLAB_DEEP(Zone.RESIDENTIAL, 6, 120, height = 2, density = Density.HIGH, needs = 3, year = 1905, appeal = 72, value = 100, buildDays = 180),
    SLAB_WIDE(Zone.RESIDENTIAL, 6, 120, width = 2, density = Density.HIGH, needs = 3, year = 1905, appeal = 72, value = 100, buildDays = 180),

    /** Rural: a farmstead, a country house from 1920, and a house on acreage from 1950, each on a big lot. */
    FARMSTEAD(Zone.RESIDENTIAL, 1, 5, width = 2, height = 2, density = Density.RURAL, buildDays = 40),
    COUNTRY_HOUSE(Zone.RESIDENTIAL, 2, 8, width = 2, height = 2, density = Density.RURAL, needs = 1, year = 1920, appeal = 45, buildDays = 60),
    ACREAGE_HOME(Zone.RESIDENTIAL, 3, 10, width = 2, height = 2, density = Density.RURAL, needs = 1, year = 1950, appeal = 50, buildDays = 70),
    /** And on the smaller rural lots, a cabin and a smallholding. */
    CABIN(Zone.RESIDENTIAL, 1, 3, density = Density.RURAL, buildDays = 20),
    SMALLHOLDING_DEEP(Zone.RESIDENTIAL, 1, 4, height = 2, density = Density.RURAL, buildDays = 30),
    SMALLHOLDING_WIDE(Zone.RESIDENTIAL, 1, 4, width = 2, density = Density.RURAL, buildDays = 30),

    GENERAL_STORE(Zone.COMMERCIAL, 1, 3, density = Density.LOW, buildDays = 20),
    SHOP(Zone.COMMERCIAL, 2, 6, density = Density.LOW, needs = 1, appeal = 50, buildDays = 30),
    MAIN_STREET(Zone.COMMERCIAL, 3, 12, density = Density.MEDIUM, needs = 2, appeal = 60, value = 70, buildDays = 60),
    BANK(Zone.COMMERCIAL, 3, 10, density = Density.MEDIUM, needs = 2, appeal = 60, value = 95, buildDays = 70),
    HOTEL(Zone.COMMERCIAL, 4, 16, density = Density.MEDIUM, needs = 3, appeal = 68, value = 80, buildDays = 90),
    OFFICE_BLOCK(Zone.COMMERCIAL, 5, 50, density = Density.HIGH, needs = 3, year = 1910, appeal = 70, value = 110, buildDays = 150),
    DEPARTMENT_STORE(Zone.COMMERCIAL, 6, 160, width = 2, height = 2, density = Density.HIGH, needs = 3, year = 1910, appeal = 72, value = 120, buildDays = 220),
    HOTEL_TOWER(Zone.COMMERCIAL, 6, 300, width = 2, height = 2, density = Density.TOWER, needs = 3, year = 1960, appeal = 76, value = 150, buildDays = 300),
    /** Shops in other sizes: storefronts and a covered market, an arcade and an emporium, and a block of stores. */
    STOREFRONTS_DEEP(Zone.COMMERCIAL, 2, 12, height = 2, density = Density.LOW, needs = 1, appeal = 50, buildDays = 45),
    STOREFRONTS_WIDE(Zone.COMMERCIAL, 2, 12, width = 2, density = Density.LOW, needs = 1, appeal = 50, buildDays = 45),
    COVERED_MARKET(Zone.COMMERCIAL, 2, 24, width = 2, height = 2, density = Density.LOW, needs = 1, appeal = 52, buildDays = 60),
    ARCADE_DEEP(Zone.COMMERCIAL, 3, 24, height = 2, density = Density.MEDIUM, needs = 2, appeal = 60, value = 70, buildDays = 90),
    ARCADE_WIDE(Zone.COMMERCIAL, 3, 24, width = 2, density = Density.MEDIUM, needs = 2, appeal = 60, value = 70, buildDays = 90),
    EMPORIUM(Zone.COMMERCIAL, 3, 48, width = 2, height = 2, density = Density.MEDIUM, needs = 2, appeal = 62, value = 75, buildDays = 120),
    STORE_BLOCK_DEEP(Zone.COMMERCIAL, 5, 100, height = 2, density = Density.HIGH, needs = 3, year = 1910, appeal = 70, value = 110, buildDays = 180),
    STORE_BLOCK_WIDE(Zone.COMMERCIAL, 5, 100, width = 2, density = Density.HIGH, needs = 3, year = 1910, appeal = 70, value = 110, buildDays = 180),
    /** Rural shops: a crossroads store, and a roadhouse from 1930. */
    CROSSROADS_STORE(Zone.COMMERCIAL, 1, 4, density = Density.RURAL, buildDays = 20),
    ROADHOUSE(Zone.COMMERCIAL, 2, 10, width = 2, height = 1, density = Density.RURAL, needs = 1, year = 1930, appeal = 40, buildDays = 40),
    ROADHOUSE_DEEP(Zone.COMMERCIAL, 2, 10, height = 2, density = Density.RURAL, needs = 1, year = 1930, appeal = 40, buildDays = 40),
    FEED_STORE(Zone.COMMERCIAL, 1, 8, width = 2, height = 2, density = Density.RURAL, buildDays = 40),

    WORKSHOP(Zone.INDUSTRIAL, 1, 6, pollution = 4, density = Density.LOW, buildDays = 20),
    MILL(Zone.INDUSTRIAL, 2, 12, pollution = 10, density = Density.LOW, needs = 1, appeal = 55, buildDays = 40),
    WAREHOUSE(Zone.INDUSTRIAL, 3, 16, pollution = 6, density = Density.MEDIUM, needs = 2, appeal = 58, buildDays = 50),
    FACTORY(Zone.INDUSTRIAL, 4, 30, pollution = 18, density = Density.MEDIUM, needs = 3, appeal = 62, buildDays = 90),
    WORKS(Zone.INDUSTRIAL, 5, 140, width = 2, height = 2, pollution = 40, density = Density.HIGH, needs = 3, appeal = 64, buildDays = 200),
    /** Industry in other sizes: a lumber yard and a brickworks, goods sheds and storehouses, a foundry and a machine shop. */
    LUMBER_YARD_DEEP(Zone.INDUSTRIAL, 1, 12, height = 2, pollution = 6, density = Density.LOW, buildDays = 30),
    LUMBER_YARD_WIDE(Zone.INDUSTRIAL, 1, 12, width = 2, pollution = 6, density = Density.LOW, buildDays = 30),
    BRICKWORKS(Zone.INDUSTRIAL, 2, 48, width = 2, height = 2, pollution = 20, density = Density.LOW, needs = 1, appeal = 55, buildDays = 70),
    SHEDS_DEEP(Zone.INDUSTRIAL, 3, 32, height = 2, pollution = 10, density = Density.MEDIUM, needs = 2, appeal = 58, buildDays = 70),
    SHEDS_WIDE(Zone.INDUSTRIAL, 3, 32, width = 2, pollution = 10, density = Density.MEDIUM, needs = 2, appeal = 58, buildDays = 70),
    STOREHOUSES(Zone.INDUSTRIAL, 3, 64, width = 2, height = 2, pollution = 12, density = Density.MEDIUM, needs = 2, appeal = 58, buildDays = 100),
    FOUNDRY(Zone.INDUSTRIAL, 5, 35, pollution = 30, density = Density.HIGH, needs = 3, appeal = 64, buildDays = 100),
    MACHINE_SHOP_DEEP(Zone.INDUSTRIAL, 5, 70, height = 2, pollution = 30, density = Density.HIGH, needs = 3, appeal = 64, buildDays = 150),
    MACHINE_SHOP_WIDE(Zone.INDUSTRIAL, 5, 70, width = 2, pollution = 30, density = Density.HIGH, needs = 3, appeal = 64, buildDays = 150),

    /** Offices: rooms over a shop, an office building, a tower from the 1920s, and a glass one from the 1960s. */
    OFFICES(Zone.OFFICE, 1, 10, density = Density.LOW, needs = 1, appeal = 52, value = 55, buildDays = 30),
    OFFICE_BUILDING(Zone.OFFICE, 2, 30, density = Density.MEDIUM, needs = 2, year = 1905, appeal = 62, value = 80, buildDays = 80),
    OFFICE_TOWER(Zone.OFFICE, 3, 200, width = 2, height = 2, density = Density.HIGH, needs = 3, year = 1920, appeal = 70, value = 115, buildDays = 240),
    GLASS_TOWER(Zone.OFFICE, 4, 480, width = 2, height = 2, density = Density.HIGH, needs = 3, year = 1960, appeal = 74, value = 135, buildDays = 300),
    /** Above high: a skyscraper with setbacks from the 1930s, and a supertall from 2000. */
    SKYSCRAPER(Zone.OFFICE, 4, 800, width = 2, height = 2, density = Density.TOWER, needs = 3, year = 1930, appeal = 76, value = 150, buildDays = 360),
    SUPERTALL(Zone.OFFICE, 5, 1400, width = 3, height = 3, density = Density.TOWER, needs = 3, year = 2000, appeal = 80, value = 180, buildDays = 480),
    /** Offices in other sizes: chambers and an office park, an office row and court, a narrow block and a slab. */
    CHAMBERS_DEEP(Zone.OFFICE, 1, 20, height = 2, density = Density.LOW, needs = 1, appeal = 52, value = 55, buildDays = 45),
    CHAMBERS_WIDE(Zone.OFFICE, 1, 20, width = 2, density = Density.LOW, needs = 1, appeal = 52, value = 55, buildDays = 45),
    OFFICE_PARK(Zone.OFFICE, 1, 40, width = 2, height = 2, density = Density.LOW, needs = 1, year = 1955, appeal = 55, value = 60, buildDays = 90),
    OFFICE_ROW_DEEP(Zone.OFFICE, 2, 60, height = 2, density = Density.MEDIUM, needs = 2, year = 1905, appeal = 62, value = 80, buildDays = 120),
    OFFICE_ROW_WIDE(Zone.OFFICE, 2, 60, width = 2, density = Density.MEDIUM, needs = 2, year = 1905, appeal = 62, value = 80, buildDays = 120),
    OFFICE_COURT(Zone.OFFICE, 2, 120, width = 2, height = 2, density = Density.MEDIUM, needs = 2, year = 1905, appeal = 64, value = 85, buildDays = 160),
    SLIM_OFFICES(Zone.OFFICE, 3, 60, density = Density.HIGH, needs = 3, year = 1920, appeal = 70, value = 115, buildDays = 150),
    OFFICE_SLAB_DEEP(Zone.OFFICE, 3, 100, height = 2, density = Density.HIGH, needs = 3, year = 1920, appeal = 70, value = 115, buildDays = 200),
    OFFICE_SLAB_WIDE(Zone.OFFICE, 3, 100, width = 2, density = Density.HIGH, needs = 3, year = 1920, appeal = 70, value = 115, buildDays = 200),

    /** On farmland, by what's under the lot: a mine on ore, a colliery on coal, a well on oil, a woodlot in the woods, otherwise a farm. */
    MINE(Zone.FARMLAND, 1, 20, width = 2, height = 2, pollution = 8, density = Density.LOW, buildDays = 90),
    COLLIERY(Zone.FARMLAND, 1, 20, width = 2, height = 2, pollution = 12, density = Density.LOW, buildDays = 90),
    WOODLOT(Zone.FARMLAND, 1, 3, density = Density.LOW, buildDays = 15),
    OIL_WELL(Zone.FARMLAND, 1, 4, pollution = 10, density = Density.LOW, buildDays = 40),
    FARM(Zone.FARMLAND, 1, 4, width = 2, height = 2, density = Density.LOW, buildDays = 30),
    /** Smaller farms for smaller lots: a market garden, and an orchard. */
    MARKET_GARDEN(Zone.FARMLAND, 1, 2, density = Density.LOW, buildDays = 15),
    ORCHARD_DEEP(Zone.FARMLAND, 1, 3, height = 2, density = Density.LOW, buildDays = 25),
    ORCHARD_WIDE(Zone.FARMLAND, 1, 3, width = 2, density = Density.LOW, buildDays = 25),

    /** Power stations: their smoke follows their output, see [Generation]. Hydro goes beside a river. */
    COAL_PLANT(Zone.NONE, 0, 8, width = 2, height = 2, life = 35),
    OIL_PLANT(Zone.NONE, 0, 10, width = 2, height = 2, year = 1920, life = 35),
    GAS_PLANT(Zone.NONE, 0, 8, width = 2, height = 2, year = 1960, life = 40),
    HYDRO_PLANT(Zone.NONE, 0, 6, width = 2, height = 2, life = 70),
    NUCLEAR_PLANT(Zone.NONE, 0, 40, width = 3, height = 3, year = 1970, life = 50),

    /** Power from the weather: wind turbines, solar panels, and batteries to keep it for the evening. */
    WIND_FARM(Zone.NONE, 0, 4, width = 2, height = 2, year = 2000, life = 25),
    SOLAR_FARM(Zone.NONE, 0, 3, width = 3, height = 3, year = 2005, life = 25),
    BATTERY(Zone.NONE, 0, 2, width = 2, height = 1, year = 2030, life = 15),

    /** Out on the water: a turbine in a river's current, turbines in the tide, and wind farms offshore. */
    RIVER_TURBINE(Zone.NONE, 0, 1, year = 1985, life = 30),
    TIDAL_TURBINE(Zone.NONE, 0, 3, width = 2, height = 1, year = 2010, life = 25),
    OFFSHORE_WIND(Zone.NONE, 0, 6, width = 2, height = 2, year = 2010, life = 25),

    /** Where a high-voltage line steps down to the streets' lines. */
    SUBSTATION(Zone.NONE, 0, 0, year = 1920, life = 50),

    /** Garbage: a dump that fills, an incinerator that burns it (from 1930), recycling that takes some (from 1975). */
    DUMP(Zone.NONE, 0, 6, width = 3, height = 3),
    INCINERATOR(Zone.NONE, 0, 12, width = 2, height = 2, year = 1930, life = 40),
    RECYCLING(Zone.NONE, 0, 15, width = 2, height = 2, year = 1975),

    POLICE_STATION(Zone.NONE, 0, 10, width = 2, height = 1, life = 50),

    /** The telephone: an exchange, and a mast for mobile phones (from 1985). */
    EXCHANGE(Zone.NONE, 0, 20, width = 2, height = 1, life = 50),
    CELL_TOWER(Zone.NONE, 0, 2, year = 1985, life = 30),

    /** Justice: a police headquarters with its detectives (from 1920), a courthouse and a jail. */
    POLICE_HQ(Zone.NONE, 0, 60, width = 3, height = 2, year = 1920, life = 60),
    COURTHOUSE(Zone.NONE, 0, 30, width = 2, height = 2, life = 80),
    JAIL(Zone.NONE, 0, 40, width = 3, height = 3, life = 60),
    FIRE_STATION(Zone.NONE, 0, 12, width = 2, height = 2, life = 50),

    /** Fire: a volunteer hall, cheap and half as strong; a ladder company (from 1905), which tall buildings need. */
    VOLUNTEER_HALL(Zone.NONE, 0, 2, life = 40),
    LADDER_COMPANY(Zone.NONE, 0, 14, width = 2, height = 2, year = 1905, life = 50),

    /** Health: an ambulance station (from 1910), and a nursing home for the elderly. */
    AMBULANCE_STATION(Zone.NONE, 0, 10, width = 2, height = 1, year = 1910, life = 40),
    NURSING_HOME(Zone.NONE, 0, 20, width = 2, height = 2, life = 50),

    /** A cooling centre (from 1960): somewhere cool for the old and frail to go in a heat wave. */
    COOLING_CENTRE(Zone.NONE, 0, 6, year = 1960, life = 40),

    /** Learning: a library, and a college. */
    LIBRARY(Zone.NONE, 0, 4, life = 60),
    COLLEGE(Zone.NONE, 0, 50, width = 3, height = 3, life = 70),
    PARK(Zone.NONE, 0, 0),

    /** Stations and freight yards, placed beside the track, lying east to west or north to south. */
    STATION(Zone.NONE, 0, 6, width = 3, height = 1),
    STATION_NS(Zone.NONE, 0, 6, width = 1, height = 3),
    FREIGHT_YARD(Zone.NONE, 0, 20, width = 3, height = 2, pollution = 6),
    FREIGHT_YARD_NS(Zone.NONE, 0, 20, width = 2, height = 3, pollution = 6),

    /** A freight terminal for containers (from 1965), much bigger than a yard: cranes over its sidings, and stacks. */
    FREIGHT_TERMINAL(Zone.NONE, 0, 60, width = 5, height = 2, pollution = 8, year = 1965, life = 50),
    FREIGHT_TERMINAL_NS(Zone.NONE, 0, 60, width = 2, height = 5, pollution = 8, year = 1965, life = 50),

    /**
     * Ports, beside water ships can reach from the edge of the map, lying east to west or north to south:
     * a wharf, docks with a passenger berth (from 1920), and a container port (from 1966).
     */
    WHARF(Zone.NONE, 0, 30, width = 3, height = 2, pollution = 4, life = 50),
    WHARF_NS(Zone.NONE, 0, 30, width = 2, height = 3, pollution = 4, life = 50),
    DOCKS(Zone.NONE, 0, 80, width = 4, height = 3, pollution = 6, year = 1920, life = 60),
    DOCKS_NS(Zone.NONE, 0, 80, width = 3, height = 4, pollution = 6, year = 1920, life = 60),
    CONTAINER_PORT(Zone.NONE, 0, 60, width = 6, height = 3, pollution = 8, year = 1966, life = 50),
    CONTAINER_PORT_NS(Zone.NONE, 0, 60, width = 3, height = 6, pollution = 8, year = 1966, life = 50),

    /** Airports, east to west along the runway: a grass airfield (from 1920), an airport (1950) and a big one with jets (1975). */
    AIRFIELD(Zone.NONE, 0, 15, width = 4, height = 3, year = 1920, life = 40),
    AIRPORT(Zone.NONE, 0, 120, width = 6, height = 4, pollution = 6, year = 1950, life = 50),
    INTERNATIONAL_AIRPORT(Zone.NONE, 0, 400, width = 8, height = 4, pollution = 10, year = 1975, life = 60),

    /** Water: a pumping station beside a river or lake, a well field anywhere, a tower, and an outfall for the sewers. */
    PUMPING_STATION(Zone.NONE, 0, 6, width = 2, height = 2, life = 40),
    WELL_FIELD(Zone.NONE, 0, 2, width = 2, height = 2, life = 25),
    WATER_TOWER(Zone.NONE, 0, 0, life = 50),
    OUTFALL(Zone.NONE, 0, 0),

    /** Sewage works on the water: settling tanks from the Streetcar city, full treatment from Renewal. They take the place of an outfall. */
    SEWAGE_WORKS(Zone.NONE, 0, 8, width = 2, height = 2, year = 1910, life = 50),
    TREATMENT_PLANT(Zone.NONE, 0, 20, width = 3, height = 2, year = 1970, life = 50),

    /** Stormwater: a pond that holds it, and an outfall for the storm drains. */
    STORM_POND(Zone.NONE, 0, 0, width = 2, height = 2),
    STORM_OUTFALL(Zone.NONE, 0, 0),

    /** Transit: a tram depot beside its track, a bus garage (from 1920) and a subway station over its tunnel (from 1910). */
    TRAM_DEPOT(Zone.NONE, 0, 20, width = 2, height = 2, life = 50),
    BUS_GARAGE(Zone.NONE, 0, 25, width = 2, height = 2, year = 1920, life = 40),
    SUBWAY_STATION(Zone.NONE, 0, 4, year = 1910),

    /** Schooling and health: a school, a high school (from 1910), a doctor's clinic and a hospital. */
    SCHOOL(Zone.NONE, 0, 8, width = 2, height = 2, life = 50),
    HIGH_SCHOOL(Zone.NONE, 0, 16, width = 3, height = 2, year = 1910, life = 60),
    CLINIC(Zone.NONE, 0, 4, life = 40),
    HOSPITAL(Zone.NONE, 0, 60, width = 3, height = 3, life = 50),
    ;

    /** A building the city runs rather than one that grows on zoned land. */
    val service get() = this == PARK || justice || fire || school || health || this == LIBRARY

    /** Keeps the peace: police, courts and jails. */
    val justice get() = this == POLICE_STATION || this == POLICE_HQ || this == COURTHOUSE || this == JAIL

    /** Has police on patrol from it. */
    val patrols get() = this == POLICE_STATION || this == POLICE_HQ

    /** Fights fires. */
    val fire get() = this == FIRE_STATION || this == VOLUNTEER_HALL || this == LADDER_COMPANY

    /** Teaches. */
    val school get() = this == SCHOOL || this == HIGH_SCHOOL || this == COLLEGE

    /** Looks after people's health. */
    val health get() = this == CLINIC || this == HOSPITAL || this == NURSING_HOME || this == AMBULANCE_STATION || this == COOLING_CENTRE

    /** Passengers board here. */
    val station get() = this == STATION || this == STATION_NS

    /** Freight goes by train from here. */
    val yard get() = this == FREIGHT_YARD || this == FREIGHT_YARD_NS || terminal

    /** A freight terminal, for containers. */
    val terminal get() = this == FREIGHT_TERMINAL || this == FREIGHT_TERMINAL_NS

    /** Has to go beside the track. */
    val railway get() = station || yard

    /** Planes land here. */
    val airport get() = airTier > 0

    /** How big an airport it is: 1 an airfield, 2 an airport, 3 a big one, 0 for anything else. */
    val airTier: Int get() = when (this) {
        AIRFIELD -> 1
        AIRPORT -> 2
        INTERNATIONAL_AIRPORT -> 3
        else -> 0
    }

    /** Ships load and unload here. */
    val port get() = portTier > 0

    /** The era it first goes up in: its year's, or later where its zone or density waits for an era of its own. */
    val era: Era get() = maxOf(
        Era.of(year),
        if (density == Density.TOWER) Era.MOTOR else Era.TOWNSHIP,
        if (zone == Zone.MIXED || zone == Zone.OFFICE) Era.STREETCAR else Era.TOWNSHIP,
    )

    /**
     * The building this one is a bigger or smaller kind of, for what its work
     * needs, how its ground sheds rain and the like; itself for the rest.
     */
    val like: BuildingType get() = when (this) {
        VILLA_DEEP, VILLA_WIDE, MANSION -> LARGE_HOUSE
        TERRACE_DEEP, TERRACE_WIDE -> ROW_HOUSES
        COURT_TENEMENTS -> TENEMENT
        SLAB_DEEP, SLAB_WIDE -> APARTMENTS
        CABIN, SMALLHOLDING_DEEP, SMALLHOLDING_WIDE -> FARMSTEAD
        STOREFRONTS_DEEP, STOREFRONTS_WIDE, COVERED_MARKET -> SHOP
        ARCADE_DEEP, ARCADE_WIDE, EMPORIUM -> MAIN_STREET
        STORE_BLOCK_DEEP, STORE_BLOCK_WIDE -> DEPARTMENT_STORE
        ROADHOUSE_DEEP -> ROADHOUSE
        FEED_STORE -> CROSSROADS_STORE
        LUMBER_YARD_DEEP, LUMBER_YARD_WIDE -> WORKSHOP
        BRICKWORKS -> MILL
        SHEDS_DEEP, SHEDS_WIDE, STOREHOUSES -> WAREHOUSE
        FOUNDRY, MACHINE_SHOP_DEEP, MACHINE_SHOP_WIDE -> FACTORY
        CHAMBERS_DEEP, CHAMBERS_WIDE, OFFICE_PARK -> OFFICES
        OFFICE_ROW_DEEP, OFFICE_ROW_WIDE, OFFICE_COURT -> OFFICE_BUILDING
        SLIM_OFFICES, OFFICE_SLAB_DEEP, OFFICE_SLAB_WIDE -> OFFICE_TOWER
        TWIN_SHOPHOUSES_DEEP, TWIN_SHOPHOUSES_WIDE, CORNER_PARADE -> SHOPHOUSE
        SHOPS_AND_FLATS_DEEP, SHOPS_AND_FLATS_WIDE, PARADE_BLOCK -> MAIN_STREET_FLATS
        MIXED_SLAB_DEEP, MIXED_SLAB_WIDE, MIXED_COURT -> MIXED_BLOCK
        MARKET_GARDEN, ORCHARD_DEEP, ORCHARD_WIDE -> FARM
        else -> this
    }

    /** Grows food on the land: a farm, a market garden or an orchard. */
    val isFarm get() = this == FARM || this == MARKET_GARDEN || this == ORCHARD_DEEP || this == ORCHARD_WIDE

    /** Takes guests: the hotel and the hotel tower. */
    val hotel get() = this == HOTEL || this == HOTEL_TOWER

    /** How big a port it is: 1 a wharf, 2 docks, 3 a container port, 0 for anything else. */
    val portTier: Int get() = when (this) {
        WHARF, WHARF_NS -> 1
        DOCKS, DOCKS_NS -> 2
        CONTAINER_PORT, CONTAINER_PORT_NS -> 3
        else -> 0
    }

    val needsPower get() = needs >= 1
    val needsWater get() = needs >= 2
    val needsSewer get() = needs >= 3

    /** Stands out in the water, every tile of it. */
    val inWater get() = this == RIVER_TURBINE || this == TIDAL_TURBINE || this == OFFSHORE_WIND

    /** Has to be beside water. */
    val onWater get() = this == PUMPING_STATION || outfall || this == STORM_OUTFALL || this == HYDRO_PLANT

    /** Where the sewers come out. */
    val outfall get() = this == OUTFALL || this == SEWAGE_WORKS || this == TREATMENT_PLANT

    /** How much of the sewage that comes out here fouls the water, in percent. */
    val fouls: Int get() = when (this) {
        SEWAGE_WORKS -> 50
        TREATMENT_PLANT -> 10
        else -> 100
    }

    /** Where mains water comes from. */
    val waterSource get() = this == PUMPING_STATION || this == WELL_FIELD

    /** The rung up in the same zone, the choices there if it has more than one, empty at the top. */
    val next: List<BuildingType> get() = rung(zone, stage + 1)

    /** The rung below, the first choice there, or null if this is the first. */
    val previous: BuildingType? get() = rung(zone, stage - 1).let { r -> r.firstOrNull { (it.density == Density.RURAL) == (density == Density.RURAL) } ?: r.firstOrNull() }

    /** Takes more than one lot. */
    val large get() = width > 1 || height > 1

    /** Built of brick or stone to last, so that once it's old enough it's valued as heritage. */
    val heritage get() = this == LARGE_HOUSE || this == ROW_HOUSES || this == TENEMENT || this == APARTMENTS || this == APARTMENT_COURT ||
        this == MAIN_STREET || this == BANK || this == HOTEL || this == OFFICE_BLOCK || this == DEPARTMENT_STORE ||
        this == OFFICE_BUILDING || this == OFFICE_TOWER || this == MAIN_STREET_FLATS

    /** Office work, wherever it stands: the office zone's, and office blocks and banks among the shops. */
    val office get() = zone == Zone.OFFICE || this == OFFICE_BLOCK || this == BANK

    companion object {
        private val rungs = HashMap<Int, List<BuildingType>>()

        /** The kinds of building on rung [stage] of [zone]'s ladder. */
        fun rung(zone: Byte, stage: Int): List<BuildingType> =
            if (zone == Zone.NONE) emptyList()
            else rungs.getOrPut(zone * 100 + stage) { entries.filter { it.zone == zone && it.stage == stage } }

        fun firstFor(zone: Byte): BuildingType = rung(zone, 1).first()
    }
}

/** One building on the map. [x], [y] is its top left tile. [variant] picks how it looks. */
class Building(val id: Int, var type: BuildingType, val x: Int, val y: Int, val variant: Int) {
    /** The people living here, for a home. */
    var people: Household? = null

    /** Days since it was built or last grew. */
    var age = 0

    /** Days left of a fire, 0 when it isn't burning. */
    var burning = 0

    /** Days this month a shop or works has been shut by floodwater. */
    var closedDays = 0

    /** Days left before it's built, 0 once it's standing. Nobody lives or works in it until then. */
    var underway = 0

    /** When it was finished, in months from January 1900 ([Ageing.monthOf]). */
    var built = 0

    /** Days left of a breakdown, for the works the city runs: it does nothing until it's mended. */
    var outage = 0

    /** For a dump, what's in it, in kilograms. */
    var fill = 0

    /** Last month nobody took its garbage away. */
    var uncollected = false

    /** For a school or doctor, last month: those it took in, and the room it had for them. */
    var served = 0
    var room = 0

    /** For a coal or oil station, scrubbers fitted to clean its smoke. */
    var scrubbed = false

    /** For a works on industrial land, what it makes: a [WorksKind]'s ordinal, or -1. */
    var kind = -1

    /**
     * Last month, in percent: for a works, how much of what it needed came
     * from the town rather than from outside; for a farm, woodlot or mine,
     * how much of what it made was taken in town; for a coal station, its coal.
     */
    var local = 0

    val worksKind: WorksKind? get() = if (kind >= 0) WorksKind.entries[kind] else null
}
