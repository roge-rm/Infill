package com.rm.infill.sim

/** How dense a zone may build: each zoned tile has one, and it caps how far up the ladder its buildings go. */
object Density {
    const val NONE: Byte = 0
    const val LOW: Byte = 1
    const val MEDIUM: Byte = 2
    const val HIGH: Byte = 3
}

/**
 * Every kind of building. Zoned ones climb a ladder of [stage]s, one ladder a
 * zone running up through the densities, a few rungs with a choice of two.
 * [capacity] is residents for homes and jobs for everything else. A rung
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
) {
    COTTAGE(Zone.RESIDENTIAL, 1, 5, density = Density.LOW, buildDays = 20),
    HOUSE(Zone.RESIDENTIAL, 2, 9, density = Density.LOW, needs = 1, appeal = 55, buildDays = 30),
    LARGE_HOUSE(Zone.RESIDENTIAL, 3, 14, density = Density.LOW, needs = 2, appeal = 63, buildDays = 45),
    ROW_HOUSES(Zone.RESIDENTIAL, 4, 22, density = Density.MEDIUM, needs = 2, appeal = 66, value = 70, buildDays = 60),
    TENEMENT(Zone.RESIDENTIAL, 5, 32, density = Density.MEDIUM, needs = 3, appeal = 70, value = 75, buildDays = 75),
    APARTMENTS(Zone.RESIDENTIAL, 6, 60, density = Density.HIGH, needs = 3, year = 1905, appeal = 72, value = 100, buildDays = 120),
    APARTMENT_COURT(Zone.RESIDENTIAL, 7, 260, width = 2, height = 2, density = Density.HIGH, needs = 3, year = 1915, appeal = 74, value = 120, buildDays = 200),

    GENERAL_STORE(Zone.COMMERCIAL, 1, 3, density = Density.LOW, buildDays = 20),
    SHOP(Zone.COMMERCIAL, 2, 6, density = Density.LOW, needs = 1, appeal = 50, buildDays = 30),
    MAIN_STREET(Zone.COMMERCIAL, 3, 12, density = Density.MEDIUM, needs = 2, appeal = 60, value = 70, buildDays = 60),
    BANK(Zone.COMMERCIAL, 3, 10, density = Density.MEDIUM, needs = 2, appeal = 60, value = 95, buildDays = 70),
    HOTEL(Zone.COMMERCIAL, 4, 16, density = Density.MEDIUM, needs = 3, appeal = 68, value = 80, buildDays = 90),
    OFFICE_BLOCK(Zone.COMMERCIAL, 5, 50, density = Density.HIGH, needs = 3, year = 1910, appeal = 70, value = 110, buildDays = 150),
    DEPARTMENT_STORE(Zone.COMMERCIAL, 6, 160, width = 2, height = 2, density = Density.HIGH, needs = 3, year = 1910, appeal = 72, value = 120, buildDays = 220),

    WORKSHOP(Zone.INDUSTRIAL, 1, 6, pollution = 4, density = Density.LOW, buildDays = 20),
    MILL(Zone.INDUSTRIAL, 2, 12, pollution = 10, density = Density.LOW, needs = 1, appeal = 55, buildDays = 40),
    WAREHOUSE(Zone.INDUSTRIAL, 3, 16, pollution = 6, density = Density.MEDIUM, needs = 2, appeal = 58, buildDays = 50),
    FACTORY(Zone.INDUSTRIAL, 4, 30, pollution = 18, density = Density.MEDIUM, needs = 3, appeal = 62, buildDays = 90),
    WORKS(Zone.INDUSTRIAL, 5, 140, width = 2, height = 2, pollution = 40, density = Density.HIGH, needs = 3, appeal = 64, buildDays = 200),

    /** Offices: rooms over a shop, an office building, a tower from the 1920s, and a glass one from the 1960s. */
    OFFICES(Zone.OFFICE, 1, 10, density = Density.LOW, needs = 1, appeal = 52, value = 55, buildDays = 30),
    OFFICE_BUILDING(Zone.OFFICE, 2, 30, density = Density.MEDIUM, needs = 2, year = 1905, appeal = 62, value = 80, buildDays = 80),
    OFFICE_TOWER(Zone.OFFICE, 3, 200, width = 2, height = 2, density = Density.HIGH, needs = 3, year = 1920, appeal = 70, value = 115, buildDays = 240),
    GLASS_TOWER(Zone.OFFICE, 4, 480, width = 2, height = 2, density = Density.HIGH, needs = 3, year = 1960, appeal = 74, value = 135, buildDays = 300),

    /** On farmland, by what's under the lot: a mine on ore, a colliery on coal, a well on oil, a woodlot in the woods, otherwise a farm. */
    MINE(Zone.FARMLAND, 1, 20, width = 2, height = 2, pollution = 8, density = Density.LOW, buildDays = 90),
    COLLIERY(Zone.FARMLAND, 1, 20, width = 2, height = 2, pollution = 12, density = Density.LOW, buildDays = 90),
    WOODLOT(Zone.FARMLAND, 1, 3, density = Density.LOW, buildDays = 15),
    OIL_WELL(Zone.FARMLAND, 1, 4, pollution = 10, density = Density.LOW, buildDays = 40),
    FARM(Zone.FARMLAND, 1, 4, width = 2, height = 2, density = Density.LOW, buildDays = 30),

    /** Power stations: their smoke follows their output, see [Generation]. Hydro goes beside a river. */
    COAL_PLANT(Zone.NONE, 0, 8, width = 2, height = 2, life = 35),
    OIL_PLANT(Zone.NONE, 0, 10, width = 2, height = 2, year = 1920, life = 35),
    GAS_PLANT(Zone.NONE, 0, 8, width = 2, height = 2, year = 1960, life = 40),
    HYDRO_PLANT(Zone.NONE, 0, 6, width = 2, height = 2, life = 70),
    NUCLEAR_PLANT(Zone.NONE, 0, 40, width = 3, height = 3, year = 1970, life = 50),

    /** Where a high-voltage line steps down to the streets' lines. */
    SUBSTATION(Zone.NONE, 0, 0, year = 1920, life = 50),

    /** Garbage: a dump that fills, an incinerator that burns it (from 1930), recycling that takes some (from 1975). */
    DUMP(Zone.NONE, 0, 6, width = 3, height = 3),
    INCINERATOR(Zone.NONE, 0, 12, width = 2, height = 2, year = 1930, life = 40),
    RECYCLING(Zone.NONE, 0, 15, width = 2, height = 2, year = 1975),

    POLICE_STATION(Zone.NONE, 0, 10, width = 2, height = 1),
    FIRE_STATION(Zone.NONE, 0, 12, width = 2, height = 2),
    PARK(Zone.NONE, 0, 0),

    /** Stations and freight yards, placed beside the track, lying east to west or north to south. */
    STATION(Zone.NONE, 0, 6, width = 3, height = 1),
    STATION_NS(Zone.NONE, 0, 6, width = 1, height = 3),
    FREIGHT_YARD(Zone.NONE, 0, 20, width = 3, height = 2, pollution = 6),
    FREIGHT_YARD_NS(Zone.NONE, 0, 20, width = 2, height = 3, pollution = 6),

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
    SCHOOL(Zone.NONE, 0, 8, width = 2, height = 2),
    HIGH_SCHOOL(Zone.NONE, 0, 16, width = 3, height = 2, year = 1910),
    CLINIC(Zone.NONE, 0, 4),
    HOSPITAL(Zone.NONE, 0, 60, width = 3, height = 3),
    ;

    /** A building the city runs rather than one that grows on zoned land. */
    val service get() = this == POLICE_STATION || this == FIRE_STATION || this == PARK || school || health

    /** Teaches children. */
    val school get() = this == SCHOOL || this == HIGH_SCHOOL

    /** Looks after people's health. */
    val health get() = this == CLINIC || this == HOSPITAL

    /** Passengers board here. */
    val station get() = this == STATION || this == STATION_NS

    /** Freight goes by train from here. */
    val yard get() = this == FREIGHT_YARD || this == FREIGHT_YARD_NS

    /** Has to go beside the track. */
    val railway get() = station || yard

    val needsPower get() = needs >= 1
    val needsWater get() = needs >= 2
    val needsSewer get() = needs >= 3

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
    val previous: BuildingType? get() = rung(zone, stage - 1).firstOrNull()

    /** Takes more than one lot. */
    val large get() = width > 1 || height > 1

    /** Built of brick or stone to last, so that once it's old enough it's valued as heritage. */
    val heritage get() = this == LARGE_HOUSE || this == ROW_HOUSES || this == TENEMENT || this == APARTMENTS || this == APARTMENT_COURT ||
        this == MAIN_STREET || this == BANK || this == HOTEL || this == OFFICE_BLOCK || this == DEPARTMENT_STORE ||
        this == OFFICE_BUILDING || this == OFFICE_TOWER

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
