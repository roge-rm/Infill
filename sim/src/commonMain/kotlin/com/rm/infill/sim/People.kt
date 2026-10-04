package com.rm.infill.sim

/**
 * What an epidemic is: cholera and typhoid from foul water, consumption
 * from crowding, or influenza, worse where the air is bad.
 */
enum class Disease { CHOLERA, CONSUMPTION, INFLUENZA }

/** How much schooling an adult has: little or none, a school's, or high school and more. */
object Education {
    const val UNSCHOOLED = 0
    const val SCHOOLED = 1
    const val EDUCATED = 2
    const val LEVELS = 3
}

/** How well off a home is. */
object Wealth {
    const val POOR = 0
    const val MIDDLE = 1
    const val WELL_OFF = 2
    const val LEVELS = 3
}

/**
 * The people in one home: children, adults and the elderly, always adding up
 * to as many as the home holds. Adults by how much schooling they had; the
 * children's schooling so far, at school and at high school; the home's
 * health; and how well off it is.
 */
class Household(var children: Int, var adults: Int, var elderly: Int, var wealth: Int) {
    /** Adults at each level of [Education], adding up to [adults]. */
    val schooled = IntArray(Education.LEVELS)

    /** How far the children here have got at school and at high school, 0 to 100. */
    var schooling = 0
    var highSchooling = 0

    /** 0 to 100. */
    var health = 60

    /** Months the home has stood empty and for sale since the last of its people died, 0 while it's lived in. */
    var forSale = 0

    val size get() = children + adults + elderly

    val empty get() = size == 0

    /** Adults of working age who look for work. */
    fun workers(): Int = workersAt(0) + workersAt(1) + workersAt(2)

    /** Workers at each level of education, in proportion to the adults. */
    fun workersAt(level: Int): Int = (schooled[level] * Demography.WORKING + 50) / 100

    internal fun writeTo(w: SaveWriter) {
        w.int(children); w.int(adults); w.int(elderly); w.int(wealth)
        for (v in schooled) w.int(v)
        w.int(schooling); w.int(highSchooling); w.int(health); w.int(forSale)
    }

    companion object {
        internal fun readFrom(r: SaveReader): Household {
            val h = Household(r.int(), r.int(), r.int(), r.int())
            for (k in h.schooled.indices) h.schooled[k] = r.int()
            h.schooling = r.int(); h.highSchooling = r.int(); h.health = r.int(); h.forSale = r.int()
            return h
        }
    }
}

/**
 * The rates people live by, which change through the century: how many
 * children are born, how long people live, what schooling newcomers bring,
 * and how their households are made up and how big they are. Births and
 * deaths are per ten thousand people a month.
 */
object Demography {
    /** The share of adults who work, in percent. */
    const val WORKING = 70

    /** Children grow up in about 16 years, and adults grow old in about 45. */
    const val GROWING_UP = 5
    const val GROWING_OLD = 2

    /** A value by [year] along a table of [years] and [values], straight between them and level past either end. */
    private fun by(year: Int, years: IntArray, values: IntArray): Int {
        if (year <= years.first()) return values.first()
        for (k in 1 until years.size) if (year <= years[k]) return values[k - 1] + (values[k] - values[k - 1]) * (year - years[k - 1]) / (years[k] - years[k - 1])
        return values.last()
    }

    /** Many children in 1900, fewer in the depression and the war, the baby boom after it, then a long fall. */
    private val BIRTH_YEARS = intArrayOf(1900, 1915, 1932, 1941, 1947, 1957, 1966, 1976, 1990, 2010, 2040)
    private val BIRTHS = intArrayOf(50, 45, 33, 32, 50, 55, 40, 27, 26, 22, 20)

    /** Fewer children die as water, then antibiotics and vaccines, come in. */
    private val CHILD_YEARS = intArrayOf(1900, 1920, 1945, 1960, 1990)
    private val CHILD_DEATHS = intArrayOf(20, 12, 5, 3, 1)

    /** And the old live longer, the most in the 1950s and again after 1970 with heart medicine. */
    private val ELDER_YEARS = intArrayOf(1900, 1930, 1960, 1990, 2020, 2050)
    private val ELDER_DEATHS = intArrayOf(90, 80, 55, 42, 32, 28)

    private val ADULT_YEARS = intArrayOf(1900, 1945, 1990)
    private val ADULT_DEATHS = intArrayOf(10, 8, 6)

    /** Births a month for every ten thousand adults, fewer the better off. */
    fun births(year: Int, wealth: Int): Int {
        val base = by(year, BIRTH_YEARS, BIRTHS)
        return when (wealth) {
            Wealth.POOR -> base * 12 / 10
            Wealth.WELL_OFF -> base * 8 / 10
            else -> base
        }
    }

    /**
     * Deaths a month for every ten thousand children, adults and elderly, at a
     * home of middling health. Poorer health raises them, up to two and a half
     * times; better lowers them, to half.
     */
    fun childDeaths(year: Int): Int = by(year, CHILD_YEARS, CHILD_DEATHS)
    fun adultDeaths(year: Int): Int = by(year, ADULT_YEARS, ADULT_DEATHS)
    fun elderlyDeaths(year: Int): Int = by(year, ELDER_YEARS, ELDER_DEATHS)

    /** How full a household keeps its home, in percent: full until the 1950s, then smaller households as families shrink. */
    private val SIZE_YEARS = intArrayOf(1950, 1970, 1990, 2010, 2030)
    private val SIZES = intArrayOf(100, 95, 90, 87, 85)

    fun occupancy(year: Int): Int = by(year, SIZE_YEARS, SIZES)

    /** How many live in a home of [capacity] in [year]: at least one. */
    fun household(capacity: Int, year: Int): Int = maxOf(1, capacity * occupancy(year) / 100)

    /** How health scales deaths, in percent: 100 at health 60. */
    fun healthFactor(health: Int): Int = (100 + (60 - health) * 2).coerceIn(50, 250)

    private val SCHOOL_YEARS = intArrayOf(1900, 1960, 1990, 2020)
    private val NEW_EDUCATED = intArrayOf(5, 30, 45, 55)
    private val NEW_UNSCHOOLED = intArrayOf(55, 10, 5, 3)

    /** Newcomers' schooling, in percent unschooled, schooled and educated, better each decade. */
    fun newcomerSchooling(year: Int): IntArray {
        val educated = by(year, SCHOOL_YEARS, NEW_EDUCATED)
        val unschooled = by(year, SCHOOL_YEARS, NEW_UNSCHOOLED)
        return intArrayOf(unschooled, 100 - unschooled - educated, educated)
    }

    /** A newcomer household's make-up, in percent children, adults and elderly: young families in the boom, older ones since. */
    private val AGE_YEARS = intArrayOf(1900, 1945, 1958, 1975, 1995, 2030)
    private val NEW_CHILDREN = intArrayOf(40, 28, 34, 26, 21, 18)
    private val NEW_ELDERLY = intArrayOf(8, 12, 11, 15, 18, 22)

    fun newcomerAges(year: Int): IntArray {
        val children = by(year, AGE_YEARS, NEW_CHILDREN)
        val elderly = by(year, AGE_YEARS, NEW_ELDERLY)
        return intArrayOf(children, 100 - children - elderly, elderly)
    }

    /**
     * The schooling each kind of job wants, in percent unschooled, schooled and
     * educated: a workshop takes anyone, a bank wants educated clerks.
     */
    fun jobSkills(type: BuildingType): IntArray = when (type.like) {
        BuildingType.GENERAL_STORE -> intArrayOf(70, 30, 0)
        BuildingType.SHOP -> intArrayOf(40, 60, 0)
        BuildingType.BANK -> intArrayOf(0, 55, 45)
        BuildingType.MAIN_STREET -> intArrayOf(40, 55, 5)
        BuildingType.OFFICE_BLOCK -> intArrayOf(5, 55, 40)
        BuildingType.DEPARTMENT_STORE -> intArrayOf(35, 60, 5)
        BuildingType.WORKS -> intArrayOf(60, 32, 8)
        BuildingType.HOTEL -> intArrayOf(40, 50, 10)
        BuildingType.WORKSHOP -> intArrayOf(90, 10, 0)
        BuildingType.OFFICES -> intArrayOf(10, 65, 25)
        BuildingType.OFFICE_BUILDING -> intArrayOf(5, 60, 35)
        BuildingType.OFFICE_TOWER -> intArrayOf(5, 50, 45)
        BuildingType.GLASS_TOWER -> intArrayOf(0, 40, 60)
        BuildingType.SKYSCRAPER -> intArrayOf(0, 45, 55)
        BuildingType.SUPERTALL -> intArrayOf(0, 35, 65)
        BuildingType.HOTEL_TOWER -> intArrayOf(35, 50, 15)
        BuildingType.CROSSROADS_STORE -> intArrayOf(75, 25, 0)
        BuildingType.SHOPHOUSE -> intArrayOf(60, 40, 0)
        BuildingType.MAIN_STREET_FLATS -> intArrayOf(40, 60, 0)
        BuildingType.MIXED_BLOCK -> intArrayOf(35, 60, 5)
        BuildingType.PODIUM_TOWER -> intArrayOf(25, 60, 15)
        BuildingType.ROADHOUSE -> intArrayOf(70, 30, 0)
        BuildingType.FARM, BuildingType.WOODLOT -> intArrayOf(95, 5, 0)
        BuildingType.MINE, BuildingType.COLLIERY, BuildingType.OIL_WELL -> intArrayOf(85, 13, 2)
        BuildingType.MILL -> intArrayOf(80, 20, 0)
        BuildingType.WAREHOUSE -> intArrayOf(85, 15, 0)
        BuildingType.FACTORY -> intArrayOf(60, 33, 7)
        BuildingType.COAL_PLANT -> intArrayOf(60, 35, 5)
        BuildingType.WIND_FARM, BuildingType.SOLAR_FARM, BuildingType.BATTERY,
        BuildingType.RIVER_TURBINE, BuildingType.TIDAL_TURBINE, BuildingType.OFFSHORE_WIND -> intArrayOf(0, 60, 40)
        BuildingType.POLICE_STATION -> intArrayOf(20, 80, 0)
        BuildingType.FIRE_STATION -> intArrayOf(30, 70, 0)
        BuildingType.SCHOOL -> intArrayOf(0, 25, 75)
        BuildingType.HIGH_SCHOOL -> intArrayOf(0, 15, 85)
        BuildingType.CLINIC -> intArrayOf(0, 40, 60)
        BuildingType.HOSPITAL -> intArrayOf(10, 40, 50)
        BuildingType.VOLUNTEER_HALL -> intArrayOf(60, 40, 0)
        BuildingType.EXCHANGE -> intArrayOf(20, 70, 10)
        BuildingType.CELL_TOWER -> intArrayOf(0, 50, 50)
        BuildingType.POLICE_HQ -> intArrayOf(10, 60, 30)
        BuildingType.COURTHOUSE -> intArrayOf(10, 30, 60)
        BuildingType.JAIL -> intArrayOf(40, 50, 10)
        BuildingType.LADDER_COMPANY -> intArrayOf(30, 70, 0)
        BuildingType.AMBULANCE_STATION -> intArrayOf(10, 70, 20)
        BuildingType.NURSING_HOME -> intArrayOf(40, 50, 10)
        BuildingType.LIBRARY -> intArrayOf(0, 40, 60)
        BuildingType.COLLEGE -> intArrayOf(10, 30, 60)
        BuildingType.MUSEUM, BuildingType.OPERA_HOUSE, BuildingType.ART_GALLERY, BuildingType.CONCERT_HALL -> intArrayOf(0, 40, 60)
        BuildingType.TOWN_HALL -> intArrayOf(10, 50, 40)
        BuildingType.POST_OFFICE -> intArrayOf(30, 70, 0)
        BuildingType.KINDERGARTEN, BuildingType.JUNIOR_HIGH -> intArrayOf(0, 25, 75)
        BuildingType.VOCATIONAL_SCHOOL -> intArrayOf(10, 60, 30)
        BuildingType.CENTRAL_LIBRARY -> intArrayOf(0, 40, 60)
        BuildingType.COMMUNITY_COLLEGE -> intArrayOf(10, 35, 55)
        BuildingType.UNIVERSITY -> intArrayOf(5, 25, 70)
        BuildingType.RESEARCH_CAMPUS -> intArrayOf(0, 20, 80)
        BuildingType.SANATORIUM -> intArrayOf(20, 50, 30)
        BuildingType.PUBLIC_HEALTH_OFFICE -> intArrayOf(0, 40, 60)
        BuildingType.POLICE_BOX, BuildingType.TRAFFIC_POLICE -> intArrayOf(20, 80, 0)
        BuildingType.FIREBOAT_STATION -> intArrayOf(30, 70, 0)
        else -> intArrayOf(40, 60, 0)
    }

    /** What a worker earns at each level of schooling, against an unschooled worker's 10. */
    val WAGES = intArrayOf(10, 16, 25)

    /** How much each class of home pays in tax and spends in the shops, in percent of a middling one. */
    val TAX_BY_WEALTH = intArrayOf(85, 110, 160)
    val SPENDING_BY_WEALTH = intArrayOf(85, 105, 140)
}
