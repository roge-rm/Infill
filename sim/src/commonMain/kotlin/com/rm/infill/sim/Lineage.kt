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
    class Kind(val type: BuildingType, val serves: Int = 100, val reach: Int = 100, val upkeep: Int = 100, val price: Long = 0)

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
    )

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
        val l = line[t.ordinal] ?: return 100
        var lost = 0
        for (k in l.subList(l.indexOfFirst { it.type == t } + 1, l.size)) {
            if (year < k.type.year) break
            lost += Balance.DATE_STEP * minOf(year - k.type.year, Balance.DATE_RAMP_YEARS) / Balance.DATE_RAMP_YEARS
        }
        return maxOf(Balance.DATE_FLOOR, 100 - lost)
    }
}
