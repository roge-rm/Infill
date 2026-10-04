package com.rm.infill.sim

/** What a city-run building comes to need as the years go by: power, mains water and the sewer, the phone, broadband. */
enum class Need(val cost: Int) {
    POWER(40), WATER(20), PHONE(20), BROADBAND(15);
}

/**
 * The needs of each kind of city-run building, and the year each one comes
 * in. A building has a need met when the service reaches it and it's been
 * fitted for it: built or renovated since the need came in. Each one unmet
 * takes [Need.cost] percent off how well it works, down to [LEAST].
 */
object Needs {
    const val LEAST = 30

    /** A station, yard, port or airport does its work at all from this well, in percent. */
    const val WORKING = 50

    private fun of(vararg pairs: Pair<Need, Int>) = pairs.toList()

    private val SCHOOL = of(Need.POWER to 1920, Need.WATER to 1930, Need.PHONE to 1940, Need.BROADBAND to 2000)
    private val HEALTH = of(Need.POWER to 1910, Need.WATER to 1910, Need.PHONE to 1920, Need.BROADBAND to 2000)
    private val POLICE = of(Need.PHONE to 1910, Need.POWER to 1920, Need.BROADBAND to 2000)
    private val JAIL = of(Need.POWER to 1920, Need.WATER to 1920, Need.PHONE to 1930, Need.BROADBAND to 2005)
    private val FIRE = of(Need.PHONE to 1910, Need.WATER to 1920, Need.POWER to 1930, Need.BROADBAND to 2005)
    private val STATION = of(Need.PHONE to 1920, Need.POWER to 1930, Need.BROADBAND to 2005)
    private val YARD = of(Need.PHONE to 1930, Need.POWER to 1940)
    private val DEPOT = of(Need.POWER to 1900, Need.PHONE to 1930)
    private val TERMINAL = of(Need.POWER to 1965, Need.PHONE to 1965, Need.BROADBAND to 2000)
    private val WHARF = of(Need.PHONE to 1940, Need.POWER to 1950)
    private val DOCKS = of(Need.POWER to 1920, Need.PHONE to 1930)
    private val CONTAINER = of(Need.POWER to 1966, Need.WATER to 1966, Need.PHONE to 1966, Need.BROADBAND to 2000)
    private val AIRFIELD = of(Need.PHONE to 1940)
    private val CIVIC = of(Need.PHONE to 1910, Need.POWER to 1920, Need.BROADBAND to 2000)
    private val AIRPORT = of(Need.POWER to 1950, Need.WATER to 1950, Need.PHONE to 1950, Need.BROADBAND to 2000)

    /** What [type] needs, with the year each comes in; nothing for what grows on zoned land, or for the utilities themselves. */
    fun of(type: BuildingType): List<Pair<Need, Int>> = when {
        type.school || type.root == BuildingType.LIBRARY || type == BuildingType.CENTRAL_LIBRARY || type == BuildingType.RESEARCH_CAMPUS -> SCHOOL
        type.health -> HEALTH
        type == BuildingType.JAIL -> JAIL
        type.justice -> POLICE
        type.fire -> FIRE
        type.civic -> CIVIC
        type.station -> STATION
        type.terminal -> TERMINAL
        type.yard -> YARD
        type == BuildingType.TRAM_DEPOT || type == BuildingType.BUS_GARAGE || type == BuildingType.SUBWAY_STATION -> DEPOT
        type == BuildingType.WHARF || type == BuildingType.WHARF_NS -> WHARF
        type == BuildingType.DOCKS || type == BuildingType.DOCKS_NS -> DOCKS
        type.portTier == 3 -> CONTAINER
        type == BuildingType.AIRFIELD -> AIRFIELD
        type.airport -> AIRPORT
        else -> emptyList()
    }
}
