package com.rm.infill.sim

import com.rm.infill.sim.BuildingType.ALLOTMENTS
import com.rm.infill.sim.BuildingType.BOTANICAL_GARDEN
import com.rm.infill.sim.BuildingType.CITY_PARK
import com.rm.infill.sim.BuildingType.COMMUNITY_GARDEN
import com.rm.infill.sim.BuildingType.DOG_PARK
import com.rm.infill.sim.BuildingType.FORMAL_GARDEN
import com.rm.infill.sim.BuildingType.GREENWAY
import com.rm.infill.sim.BuildingType.PARK
import com.rm.infill.sim.BuildingType.PLAYGROUND
import com.rm.infill.sim.BuildingType.PLAZA
import com.rm.infill.sim.BuildingType.POCKET_PARK
import com.rm.infill.sim.BuildingType.TOWN_SQUARE
import com.rm.infill.sim.BuildingType.URBAN_WOODLAND
import com.rm.infill.sim.BuildingType.WETLAND_RESERVE

/**
 * What a green space does for the land around it. Each tile of it: [soak]s
 * up pollution (2 is as much as a park or woods, and 2 or more stops it
 * passing through), [cool]s in summer in hundredths of a tree, sheds
 * rain by its [hard]ness (0 to 100), and adds [value] to the land within
 * four tiles, up to [valueCap] from all of them. Planted, it comes into
 * its own over [matures] years.
 */
class Green(val soak: Int, val cool: Int, val hard: Int, val value: Int, val valueCap: Int = Balance.PARK_VALUE_CAP, val matures: Int = 0)

/**
 * The leisure a building gives the homes around it, by kind: [green] (parks
 * and gardens), [sport] and [culture], each fading to nothing at [reach]
 * tiles. People come to want it from [risesFrom], most at [peak], and go
 * off it from [fadesFrom], to [fadesTo] percent by [fadedBy]: the bandstand
 * goes out of fashion, the cinema comes and goes.
 */
class Leisure(
    val green: Int = 0, val sport: Int = 0, val culture: Int = 0, val reach: Int,
    val risesFrom: Int = 0, val peak: Int = 0, val fadesFrom: Int = 9999, val fadedBy: Int = 9999, val fadesTo: Int = 100,
) {
    /** How much it's wanted in [year], in percent. */
    fun fashion(year: Int): Int = when {
        year < risesFrom -> 0
        year < peak -> 100 * (year - risesFrom) / maxOf(1, peak - risesFrom)
        year < fadesFrom -> 100
        year < fadedBy -> 100 - (100 - fadesTo) * (year - fadesFrom) / maxOf(1, fadedBy - fadesFrom)
        else -> fadesTo
    }
}

/** Which of the town's budgets keeps a building: parks and leisure, schools, health, police, fire, or the town hall's own. */
enum class Fund { PARKS, SCHOOLS, HEALTH, POLICE, FIRE, CIVIC }

/**
 * The numbers for the buildings that are described by what they do rather
 * than by code of their own: their [price], [upkeep] a month (scaled by
 * the parks funding for green space), what they do as [green] space, the
 * [leisure] they give, the visitors they [draw], and whether they're
 * [painted] a tile at a time, and the budget it comes out of, its [fund].
 */
class Spec(
    val price: Long, val upkeep: Double, val green: Green? = null, val leisure: Leisure? = null,
    val draw: Double = 0.0, val painted: Boolean = false, val fund: Fund = Fund.PARKS,
)

object Specs {
    private val specs = arrayOfNulls<Spec>(BuildingType.entries.size)

    private fun put(t: BuildingType, spec: Spec) {
        specs[t.ordinal] = spec
    }

    init {
        put(PARK, Spec(Prices.PARK, Balance.PARK_UPKEEP, Green(2, 100, 5, 8), Leisure(green = 30, reach = 4), painted = true))
        put(PLAYGROUND, Spec(150, 2.0, Green(1, 60, 30, 6), Leisure(green = 20, sport = 20, reach = 4)))
        put(TOWN_SQUARE, Spec(800, 6.0, Green(0, 20, 80, 3), Leisure(green = 10, culture = 30, reach = 6)))
        put(PLAZA, Spec(1_200, 8.0, Green(0, 40, 85, 3), Leisure(green = 10, culture = 40, reach = 6)))
        put(FORMAL_GARDEN, Spec(1_000, 8.0, Green(2, 110, 10, 4), Leisure(green = 50, reach = 6), draw = 6.0))
        put(CITY_PARK, Spec(4_000, 25.0, Green(3, 130, 5, 3, valueCap = Balance.BIG_PARK_VALUE_CAP), Leisure(green = 90, sport = 15, reach = 10), draw = 15.0))
        put(ALLOTMENTS, Spec(400, 2.0, Green(2, 90, 5, 3), Leisure(green = 30, reach = 5)))
        put(COMMUNITY_GARDEN, Spec(600, 3.0, Green(2, 100, 5, 3), Leisure(green = 40, culture = 10, reach = 5)))
        put(POCKET_PARK, Spec(300, 3.0, Green(1, 100, 20, 8), Leisure(green = 30, reach = 3)))
        put(URBAN_WOODLAND, Spec(60, 0.3, Green(4, 150, 0, 6, matures = Balance.WOODLAND_MATURES), Leisure(green = 20, reach = 4), painted = true))
        put(BOTANICAL_GARDEN, Spec(6_000, 40.0, Green(3, 130, 5, 4, valueCap = Balance.BIG_PARK_VALUE_CAP), Leisure(green = 70, culture = 30, reach = 10), draw = 25.0))
        put(WETLAND_RESERVE, Spec(3_000, 10.0, Green(3, 140, 0, 3), Leisure(green = 50, reach = 10), draw = 10.0))
        put(GREENWAY, Spec(80, 0.4, Green(1, 80, 10, 5), Leisure(green = 25, sport = 15, reach = 3), painted = true))
        put(DOG_PARK, Spec(300, 3.0, Green(1, 80, 10, 5), Leisure(green = 20, reach = 4)))

        // Sport.
        put(BuildingType.SPORTS_GROUND, Spec(700, 5.0, leisure = Leisure(green = 15, sport = 50, reach = 8)))
        put(BuildingType.LIT_FIELDS, Spec(1_200, 9.0, leisure = Leisure(green = 10, sport = 70, reach = 8)))
        put(BuildingType.PUBLIC_BATHS, Spec(1_500, 12.0, leisure = Leisure(sport = 40, reach = 8)))
        put(BuildingType.SWIMMING_POOL, Spec(2_000, 15.0, leisure = Leisure(sport = 60, reach = 10)))
        put(BuildingType.AQUATIC_CENTRE, Spec(4_000, 25.0, leisure = Leisure(sport = 80, reach = 12), draw = 5.0))
        put(BuildingType.TENNIS_COURTS, Spec(500, 3.0, leisure = Leisure(sport = 30, reach = 5)))
        put(BuildingType.ICE_RINK, Spec(1_800, 12.0, leisure = Leisure(sport = 45, culture = 10, reach = 10)))
        put(BuildingType.BALLPARK, Spec(6_000, 30.0, leisure = Leisure(sport = 60, reach = 14), draw = 15.0))
        put(BuildingType.ARENA, Spec(15_000, 60.0, leisure = Leisure(sport = 50, culture = 40, reach = 16), draw = 25.0))
        put(BuildingType.STADIUM, Spec(30_000, 120.0, leisure = Leisure(sport = 80, reach = 20), draw = 50.0))
        put(BuildingType.GOLF_COURSE, Spec(5_000, 25.0, Green(2, 120, 5, 3), Leisure(green = 30, sport = 40, reach = 12)))
        put(BuildingType.SKATE_PARK, Spec(300, 2.0, leisure = Leisure(sport = 25, reach = 5)))
        put(BuildingType.REC_CENTRE, Spec(2_500, 18.0, leisure = Leisure(sport = 50, culture = 15, reach = 10)))

        // Culture: some come into fashion and go out again.
        put(BuildingType.BANDSTAND, Spec(200, 1.0, leisure = Leisure(green = 10, culture = 35, reach = 5, fadesFrom = 1930, fadedBy = 1965, fadesTo = 40)))
        put(BuildingType.VARIETY_THEATRE, Spec(2_000, 10.0, leisure = Leisure(culture = 50, reach = 10, fadesFrom = 1925, fadedBy = 1950, fadesTo = 50)))
        put(BuildingType.PICTURE_PALACE, Spec(2_500, 12.0, leisure = Leisure(culture = 60, reach = 12, risesFrom = 1915, peak = 1935, fadesFrom = 1955, fadedBy = 1985, fadesTo = 60)))
        put(BuildingType.MULTIPLEX, Spec(3_000, 14.0, leisure = Leisure(culture = 55, reach = 14)))
        put(BuildingType.OPERA_HOUSE, Spec(10_000, 50.0, leisure = Leisure(culture = 70, reach = 16), draw = 20.0))
        put(BuildingType.MUSEUM, Spec(7_000, 35.0, leisure = Leisure(culture = 50, reach = 16), draw = 25.0))
        put(BuildingType.ART_GALLERY, Spec(4_000, 20.0, leisure = Leisure(culture = 45, reach = 12), draw = 12.0))
        put(BuildingType.CONCERT_HALL, Spec(12_000, 55.0, leisure = Leisure(culture = 70, reach = 18), draw = 15.0))
        put(BuildingType.ZOO, Spec(12_000, 60.0, Green(2, 110, 20, 2), Leisure(green = 40, culture = 40, reach = 16), draw = 40.0))
        put(BuildingType.FAIRGROUND, Spec(2_500, 15.0, leisure = Leisure(culture = 40, reach = 12, fadesFrom = 1950, fadedBy = 1970, fadesTo = 50), draw = 15.0))
        put(BuildingType.AMUSEMENT_PARK, Spec(15_000, 70.0, leisure = Leisure(culture = 55, reach = 14), draw = 45.0))
        put(BuildingType.DRIVE_IN, Spec(1_500, 6.0, leisure = Leisure(culture = 40, reach = 14, fadesFrom = 1975, fadedBy = 1990, fadesTo = 20)))
        put(BuildingType.AQUARIUM, Spec(14_000, 60.0, leisure = Leisure(culture = 45, reach = 16), draw = 35.0))
        put(BuildingType.CONVENTION_CENTRE, Spec(25_000, 90.0, leisure = Leisure(culture = 20, reach = 10), draw = 40.0))

        // Civic: the hall and the post office out of the town hall's budget, the rest kept with the parks.
        put(BuildingType.TOWN_HALL, Spec(3_000, 30.0, fund = Fund.CIVIC))
        put(BuildingType.CITY_HALL, Spec(5_000, 45.0, draw = 4.0, fund = Fund.CIVIC))
        put(BuildingType.CIVIC_CENTRE, Spec(7_000, 60.0, draw = 4.0, fund = Fund.CIVIC))
        put(BuildingType.POST_OFFICE, Spec(900, 12.0, fund = Fund.CIVIC))
        put(BuildingType.SHELTER, Spec(500, 10.0, fund = Fund.CIVIC))
        // Landmarks draw visitors and give their neighbours something to be proud of.
        put(BuildingType.FOUNDERS_STATUE, Spec(500, 2.0, leisure = Leisure(culture = 30, reach = 8), draw = 4.0))
        put(BuildingType.MAYORS_MANSION, Spec(4_000, 15.0, leisure = Leisure(green = 30, culture = 30, reach = 10), draw = 8.0))
        put(BuildingType.EXHIBITION_HALL, Spec(15_000, 60.0, leisure = Leisure(culture = 80, reach = 20), draw = 50.0))
        put(BuildingType.OBSERVATION_TOWER, Spec(20_000, 40.0, leisure = Leisure(culture = 60, reach = 24), draw = 60.0))
        put(BuildingType.CONSERVATORY, Spec(10_000, 40.0, leisure = Leisure(green = 80, culture = 40, reach = 16), draw = 30.0))
        put(BuildingType.TOWN_MUSEUM, Spec(6_000, 25.0, leisure = Leisure(culture = 60, reach = 16), draw = 20.0))
        put(BuildingType.CEMETERY, Spec(1_500, 4.0, Green(2, 110, 10, 0), Leisure(green = 10, reach = 4)))
        put(BuildingType.MEMORIAL_GARDEN, Spec(2_000, 6.0, Green(2, 120, 5, 3), Leisure(green = 30, culture = 10, reach = 6)))
        put(BuildingType.FOUNTAIN, Spec(400, 2.0, Green(0, 120, 60, 4), Leisure(green = 10, culture = 20, reach = 4)))
        put(BuildingType.CLOCK_TOWER, Spec(1_200, 2.0, leisure = Leisure(culture = 25, reach = 6), draw = 3.0))
        put(BuildingType.WAR_MEMORIAL, Spec(600, 1.0, leisure = Leisure(green = 5, culture = 20, reach = 5)))

        // Schooling.
        put(BuildingType.KINDERGARTEN, Spec(500, 8.0, fund = Fund.SCHOOLS))
        put(BuildingType.JUNIOR_HIGH, Spec(2_500, 40.0, fund = Fund.SCHOOLS))
        put(BuildingType.VOCATIONAL_SCHOOL, Spec(3_000, 50.0, fund = Fund.SCHOOLS))
        put(BuildingType.CENTRAL_LIBRARY, Spec(5_000, 35.0, draw = 6.0, fund = Fund.SCHOOLS))
        put(BuildingType.COMMUNITY_COLLEGE, Spec(9_000, 70.0, fund = Fund.SCHOOLS))
        put(BuildingType.UNIVERSITY, Spec(45_000, 300.0, draw = 15.0, fund = Fund.SCHOOLS))
        put(BuildingType.RESEARCH_CAMPUS, Spec(30_000, 120.0, fund = Fund.SCHOOLS))

        // Health, police and fire.
        put(BuildingType.SANATORIUM, Spec(4_000, 45.0, fund = Fund.HEALTH))
        put(BuildingType.PUBLIC_HEALTH_OFFICE, Spec(2_000, 30.0, fund = Fund.HEALTH))
        put(BuildingType.POLICE_BOX, Spec(150, 4.0, fund = Fund.POLICE))
        put(BuildingType.TRAFFIC_POLICE, Spec(1_800, 35.0, fund = Fund.POLICE))
        put(BuildingType.FIREBOAT_STATION, Spec(3_500, 50.0, fund = Fund.FIRE))
    }

    /** [t]'s numbers, or null for a building whose numbers are its own code's. */
    fun of(t: BuildingType): Spec? = specs[t.ordinal]
}
