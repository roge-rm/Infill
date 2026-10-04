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

/**
 * The numbers for the buildings that are described by what they do rather
 * than by code of their own: their [price], [upkeep] a month (scaled by
 * the parks funding for green space), what they do as [green] space, the
 * [leisure] they give, the visitors they [draw], and whether they're
 * [painted] a tile at a time.
 */
class Spec(
    val price: Long, val upkeep: Double, val green: Green? = null, val leisure: Leisure? = null,
    val draw: Double = 0.0, val painted: Boolean = false,
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
    }

    /** [t]'s numbers, or null for a building whose numbers are its own code's. */
    fun of(t: BuildingType): Spec? = specs[t.ordinal]
}
