package com.rm.infill.sim

import com.rm.infill.sim.BuildingType.CARE_HOME
import com.rm.infill.sim.BuildingType.CLINIC
import com.rm.infill.sim.BuildingType.COMMUNITY_HEALTH
import com.rm.infill.sim.BuildingType.COMMUNITY_POLICING
import com.rm.infill.sim.BuildingType.COMMUNITY_SCHOOL
import com.rm.infill.sim.BuildingType.COMPOSITE_HIGH
import com.rm.infill.sim.BuildingType.ELEMENTARY_SCHOOL
import com.rm.infill.sim.BuildingType.FIRE_HALL
import com.rm.infill.sim.BuildingType.FIRE_STATION
import com.rm.infill.sim.BuildingType.GENERAL_HOSPITAL
import com.rm.infill.sim.BuildingType.HEALTH_CENTRE
import com.rm.infill.sim.BuildingType.HIGH_SCHOOL
import com.rm.infill.sim.BuildingType.HOSPITAL
import com.rm.infill.sim.BuildingType.LIBRARY
import com.rm.infill.sim.BuildingType.BRANCH_LIBRARY
import com.rm.infill.sim.BuildingType.MEDIA_LIBRARY
import com.rm.infill.sim.BuildingType.MEDICAL_CENTRE
import com.rm.infill.sim.BuildingType.MOTOR_FIRE_STATION
import com.rm.infill.sim.BuildingType.NURSING_HOME
import com.rm.infill.sim.BuildingType.POLICE_STATION
import com.rm.infill.sim.BuildingType.PRECINCT
import com.rm.infill.sim.BuildingType.SCHOOL

/**
 * Kinds of building that follow one another as the years go by: the
 * schoolhouse, then the elementary school, then the community school. Each
 * newer kind keeps the footprint of the one before it and does the same job
 * better, takes its place in the trays once the town can build it, and
 * leaves the older ones dated: they keep working, a little less well for
 * each newer kind as the years since it came go by, until they're renovated
 * into the newest.
 */
object Lineage {
    /**
     * One kind in a line, against the first: how much it [serves] (places,
     * patients, pupils), how far it [reaches], and its [upkeep], in percent;
     * and its [price] (0 for the first, whose price is its own).
     */
    class Kind(
        val type: BuildingType, val serves: Int = 100, val reach: Int = 100, val upkeep: Int = 100, val price: Long = 0,
        /** For a power station, against the first: fuel for each megawatt, smoke and carbon, in percent. */
        val fuel: Int = 100, val fumes: Int = 100, val carbon: Int = 100,
    )

    private val lines: List<List<Kind>> = listOf(
        listOf(Kind(SCHOOL), Kind(ELEMENTARY_SCHOOL, 133, 117, 127, 2_200), Kind(COMMUNITY_SCHOOL, 142, 117, 150, 3_000)),
        listOf(Kind(HIGH_SCHOOL), Kind(COMPOSITE_HIGH, 125, 111, 125, 5_000)),
        listOf(Kind(LIBRARY), Kind(BRANCH_LIBRARY, 120, 110, 117, 1_800), Kind(MEDIA_LIBRARY, 133, 120, 133, 2_200)),
        listOf(Kind(CLINIC), Kind(HEALTH_CENTRE, 140, 100, 133, 1_000), Kind(COMMUNITY_HEALTH, 160, 110, 160, 1_400)),
        listOf(Kind(HOSPITAL), Kind(GENERAL_HOSPITAL, 130, 118, 122, 8_000), Kind(MEDICAL_CENTRE, 160, 127, 155, 12_000)),
        listOf(Kind(NURSING_HOME), Kind(CARE_HOME, 140, 100, 120, 5_000)),
        listOf(Kind(FIRE_STATION), Kind(MOTOR_FIRE_STATION, 100, 130, 111, 3_000), Kind(FIRE_HALL, 100, 140, 122, 4_000)),
        listOf(Kind(POLICE_STATION), Kind(PRECINCT, 100, 125, 112, 3_000), Kind(COMMUNITY_POLICING, 100, 135, 120, 3_500)),
        listOf(Kind(BuildingType.TOWN_SQUARE), Kind(BuildingType.PLAZA)),
        listOf(Kind(BuildingType.ALLOTMENTS), Kind(BuildingType.COMMUNITY_GARDEN)),
        listOf(Kind(BuildingType.SPORTS_GROUND), Kind(BuildingType.LIT_FIELDS)),
        listOf(Kind(BuildingType.PUBLIC_BATHS), Kind(BuildingType.SWIMMING_POOL), Kind(BuildingType.AQUATIC_CENTRE)),
        listOf(Kind(BuildingType.VARIETY_THEATRE), Kind(BuildingType.PICTURE_PALACE), Kind(BuildingType.MULTIPLEX)),
        listOf(Kind(BuildingType.FAIRGROUND), Kind(BuildingType.AMUSEMENT_PARK)),
        listOf(Kind(BuildingType.TOWN_HALL), Kind(BuildingType.CITY_HALL), Kind(BuildingType.CIVIC_CENTRE)),
        listOf(Kind(BuildingType.CEMETERY), Kind(BuildingType.MEMORIAL_GARDEN)),
        // A police box is a beat's worth of a station: a constable and a telephone.
        listOf(Kind(BuildingType.POLICE_BOX, serves = 30, reach = 45)),
        // Power stations: [Kind.serves] is what each makes against the first.
        listOf(
            Kind(BuildingType.COAL_PLANT),
            Kind(BuildingType.PULVERIZED_COAL, serves = 200, upkeep = 150, price = 6_000, fuel = 85, fumes = 80, carbon = 90),
            Kind(BuildingType.SUPERCRITICAL_COAL, serves = 400, upkeep = 250, price = 14_000, fuel = 70, fumes = 55, carbon = 80),
        ),
        listOf(Kind(BuildingType.OIL_PLANT), Kind(BuildingType.LARGE_OIL, serves = 200, upkeep = 160, price = 12_000, fuel = 85, fumes = 80, carbon = 95)),
        listOf(Kind(BuildingType.GAS_PLANT), Kind(BuildingType.COMBINED_CYCLE, serves = 160, upkeep = 130, price = 25_000, fuel = 65, fumes = 60, carbon = 70)),
        listOf(Kind(BuildingType.HYDRO_PLANT), Kind(BuildingType.HYDRO_STATION, serves = 160, upkeep = 120, price = 15_000)),
        listOf(Kind(BuildingType.NUCLEAR_PLANT), Kind(BuildingType.ADVANCED_REACTOR, serves = 130, upkeep = 110, price = 110_000, fuel = 85)),
        listOf(Kind(BuildingType.WIND_FARM), Kind(BuildingType.TALL_WIND, serves = 220, upkeep = 140, price = 14_000)),
        listOf(Kind(BuildingType.SOLAR_FARM), Kind(BuildingType.BIFACIAL_SOLAR, serves = 150, upkeep = 110, price = 12_000)),
        listOf(Kind(BuildingType.OFFSHORE_WIND), Kind(BuildingType.FLOATING_OFFSHORE, serves = 150, upkeep = 120, price = 30_000)),
        listOf(Kind(BuildingType.TIDAL_TURBINE), Kind(BuildingType.TIDAL_ARRAY, serves = 200, upkeep = 130, price = 20_000)),
        listOf(Kind(BuildingType.HYDRO_DAM, price = 40_000)),
        listOf(Kind(BuildingType.PUMPED_STORAGE, price = 30_000)),
        listOf(Kind(BuildingType.GEOTHERMAL, price = 25_000)),
        listOf(Kind(BuildingType.SMALL_REACTOR, price = 60_000)),
        listOf(Kind(BuildingType.LONG_STORAGE, price = 20_000)),
        // Garbage: [Kind.serves] is what each holds or takes against the first.
        listOf(Kind(BuildingType.DUMP), Kind(BuildingType.SANITARY_LANDFILL, serves = 150, upkeep = 150, price = 3_000)),
        listOf(Kind(BuildingType.INCINERATOR), Kind(BuildingType.WASTE_TO_ENERGY, serves = 150, upkeep = 130, price = 12_000, fumes = 50)),
        listOf(
            Kind(BuildingType.RECYCLING),
            Kind(BuildingType.MATERIALS_RECOVERY, serves = 150, upkeep = 120, price = 6_000),
            Kind(BuildingType.ADVANCED_SORTING, serves = 200, upkeep = 140, price = 9_000),
        ),
        listOf(Kind(BuildingType.TRANSFER_STATION, price = 2_500)),
        listOf(Kind(BuildingType.COMPOST_YARD, price = 1_200)),
        listOf(Kind(BuildingType.LANDFILL_GAS, price = 2_000)),
        listOf(Kind(BuildingType.BIOGAS, price = 6_000)),
    )

    /** The year a kind with nothing after it went out all the same: the sanatorium with antibiotics, the police box with the patrol car's radio. */
    private val retired = mapOf(BuildingType.SANATORIUM to 1955, BuildingType.POLICE_BOX to 1970)

    /** Whether [t] has gone out by [year], so it's no longer built. */
    fun retired(t: BuildingType, year: Int): Boolean = retired[t]?.let { year >= it } == true

    private val kinds = arrayOfNulls<Kind>(BuildingType.entries.size)
    private val line = arrayOfNulls<List<Kind>>(BuildingType.entries.size)

    init {
        for (l in lines) for (k in l) {
            kinds[k.type.ordinal] = k
            line[k.type.ordinal] = l
        }
    }

    /** The first kind of [t]'s line, or [t] if it's in none. */
    fun rootOf(t: BuildingType): BuildingType = line[t.ordinal]?.first()?.type ?: t

    /** [t]'s place in its line, for its numbers: a plain kind for a building in none. */
    fun kindOf(t: BuildingType): Kind = kinds[t.ordinal] ?: plain(t)

    private val plainKinds = HashMap<BuildingType, Kind>()

    private fun plain(t: BuildingType) = plainKinds.getOrPut(t) { Kind(t) }

    /** All the kinds in [t]'s line, oldest first: just [t] if it's in none. */
    fun lineOf(t: BuildingType): List<BuildingType> = line[t.ordinal]?.map { it.type } ?: listOf(t)

    /** The kind that came after [t], or null if it's the newest. */
    fun successor(t: BuildingType): BuildingType? {
        val l = line[t.ordinal] ?: return null
        return l.getOrNull(l.indexOfFirst { it.type == t } + 1)?.type
    }

    /**
     * How up to date [t] is in [year], in percent: [Balance.DATE_STEP] off for
     * each newer kind, coming in over [Balance.DATE_RAMP_YEARS] from the year
     * it came, down to [Balance.DATE_FLOOR]. The world doesn't wait: it's the
     * calendar that dates a building, whatever the town's era.
     */
    fun dated(t: BuildingType, year: Int): Int {
        // Gone out with nothing after it: dated as if by one newer kind.
        retired[t]?.let { if (year >= it) return maxOf(Balance.DATE_FLOOR, 100 - Balance.DATE_STEP * minOf(year - it, Balance.DATE_RAMP_YEARS) / Balance.DATE_RAMP_YEARS) }
        val l = line[t.ordinal] ?: return 100
        var lost = 0
        for (k in l.subList(l.indexOfFirst { it.type == t } + 1, l.size)) {
            if (year < k.type.year) break
            lost += Balance.DATE_STEP * minOf(year - k.type.year, Balance.DATE_RAMP_YEARS) / Balance.DATE_RAMP_YEARS
        }
        return maxOf(Balance.DATE_FLOOR, 100 - lost)
    }
}
