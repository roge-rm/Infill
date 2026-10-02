package com.rm.infill.sim

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
 * The rates people live by, which change with the era: how many children are
 * born, how long people live, what schooling newcomers bring and how their
 * households are made up. Rates are per thousand people a month.
 */
object Demography {
    /** The share of adults who work, in percent. */
    const val WORKING = 70

    /** Children grow up in about 16 years, and adults grow old in about 45. */
    const val GROWING_UP = 5
    const val GROWING_OLD = 2

    /** A value that goes from [at1900] to [at1960] in a straight line, and stays there after. */
    private fun era(year: Int, at1900: Int, at1960: Int): Int {
        val t = (year - 1900).coerceIn(0, 60)
        return at1900 + (at1960 - at1900) * t / 60
    }

    /** Births a month for every thousand adults: many in 1900, fewer later, fewer again the better off. */
    fun births(year: Int, wealth: Int): Int {
        val base = era(year, 5, 3)
        return when (wealth) {
            Wealth.POOR -> base * 12 / 10
            Wealth.WELL_OFF -> base * 8 / 10
            else -> base
        }
    }

    /**
     * Deaths a month for every thousand children, adults and elderly, at a home
     * of middling health. Poorer health raises them, up to two and a half
     * times; better lowers them, to half.
     */
    fun childDeaths(year: Int): Int = era(year, 2, 0)
    fun adultDeaths(year: Int): Int = 1
    fun elderlyDeaths(year: Int): Int = era(year, 9, 5)

    /** How health scales deaths, in percent: 100 at health 60. */
    fun healthFactor(health: Int): Int = (100 + (60 - health) * 2).coerceIn(50, 250)

    /** Newcomers' schooling, in percent unschooled, schooled and educated, better each decade. */
    fun newcomerSchooling(year: Int): IntArray {
        val educated = era(year, 5, 30)
        val unschooled = era(year, 55, 10)
        return intArrayOf(unschooled, 100 - unschooled - educated, educated)
    }

    /** A newcomer household's make-up, in percent children, adults and elderly. */
    fun newcomerAges(year: Int): IntArray {
        val children = era(year, 40, 28)
        val elderly = era(year, 8, 14)
        return intArrayOf(children, 100 - children - elderly, elderly)
    }

    /**
     * The schooling each kind of job wants, in percent unschooled, schooled and
     * educated: a workshop takes anyone, a bank wants educated clerks.
     */
    fun jobSkills(type: BuildingType): IntArray = when (type) {
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
        else -> intArrayOf(40, 60, 0)
    }

    /** What a worker earns at each level of schooling, against an unschooled worker's 10. */
    val WAGES = intArrayOf(10, 16, 25)

    /** How much each class of home pays in tax and spends in the shops, in percent of a middling one. */
    val TAX_BY_WEALTH = intArrayOf(85, 110, 160)
    val SPENDING_BY_WEALTH = intArrayOf(85, 105, 140)
}
