package com.rm.infill.sim

/** The numbers the simulation is tuned with, in one place. Money is in the dollars of 1900. */
object Balance {
    /** Share of residents who work. */
    const val LABOUR_SHARE = 0.45

    /** Residents per shop job the town wants. */
    const val RESIDENTS_PER_SHOP_JOB = 7.0

    /** Settlers who'd come anyway, before there are jobs to draw them, and more as the town gets known. */
    const val SETTLERS = 30
    const val SETTLERS_PER_RESIDENT = 0.02

    /** The outside market for the town's goods, in jobs: a base, a share of the town's size, and growth a year. */
    const val EXPORT_BASE = 40.0
    const val EXPORT_PER_RESIDENT = 0.32
    const val EXPORT_GROWTH = 0.02

    /** How much of that market a town with no road to the edge of the map can reach. */
    const val UNCONNECTED_EXPORTS = 0.4

    /** Tax rates start here, in percent, and demand is neutral at them. */
    const val DEFAULT_TAX = 7

    /** How much each point of tax above or below the default moves demand. */
    const val TAX_DEMAND = 0.08

    /** Monthly tax, per resident or job, at 1% tax. */
    const val RESIDENT_TAX = 0.06
    const val JOB_TAX = 0.08

    /** Monthly upkeep. */
    const val LINE_UPKEEP = 0.1
    const val HIGH_LINE_UPKEEP = 0.3

    // The telephone. An exchange's lines, by hand and once automatic, and from 1970; the tiles it reaches,
    // half that with no trunk line out of town, and the tiles broadband reaches from an exchange with fibre
    // out, and fast service from a fibre line; the years they come. A mast's reach. A business takes a line
    // for this many jobs. Lines' lives, mending, upkeep. What a phone and broadband are worth to homes and
    // businesses, faded in and then expected; the share of educated workers working from home on broadband
    // and fast service; how much quicker a fire's reported and a crime, in a town with phones.
    const val EXCHANGE_LINES = 1_000
    const val AUTOMATIC_LINES = 3_000
    const val DIGITAL_LINES = 10_000
    const val AUTOMATIC_YEAR = 1930
    const val DIGITAL_YEAR = 1970
    const val PHONE_REACH = 10
    const val DSL_REACH = 6
    const val FAST_REACH = 2
    const val FIBRE_YEAR = 1995
    const val BROADBAND_YEAR = 1995
    const val FAST_YEAR = 2005
    const val TOWER_REACH = 12
    const val JOBS_PER_LINE = 5
    const val COPPER_LIFE = 40
    const val FIBRE_LIFE = 30
    const val MEND_PHONE = 4
    const val MEND_DUCT = 8
    const val REPAIR_PHONE = 30L
    const val COPPER_UPKEEP = 0.1
    const val FIBRE_UPKEEP = 0.15
    const val EXCHANGE_UPKEEP = 50.0
    const val MAST_UPKEEP = 40.0
    const val PHONE_APPEAL = 6
    const val PHONE_NEEDED = 8
    const val BROADBAND_APPEAL = 6
    const val BROADBAND_NEEDED = 10
    const val WFH_YEAR = 2000
    const val WFH_BROADBAND = 15
    const val WFH_FAST = 30
    const val CALL_COVER = 30
    const val CALL_ARRESTS = 20

    // Power cable underground: upkeep a tile, the year high-voltage cable can be laid, the years it lasts,
    // and the days and money to find and mend a fault. Poles and pylons overhead take value off the land
    // next to them, within a tile and two.
    const val CABLE_UPKEEP = 0.2
    const val HIGH_CABLE_UPKEEP = 0.6
    const val HIGH_CABLE_YEAR = 1950
    const val CABLE_LIFE = 40
    const val HIGH_CABLE_LIFE = 50
    const val MEND_CABLE = 10
    const val REPAIR_CABLE = 120L
    const val POLE_VALUE = 4
    const val PYLON_VALUE = 10
    // Power stations' upkeep a month, standing idle; fuel is on top, by what they make.
    const val PLANT_UPKEEP = 25.0
    const val OIL_PLANT_UPKEEP = 30.0
    const val GAS_PLANT_UPKEEP = 40.0
    const val HYDRO_PLANT_UPKEEP = 60.0
    const val NUCLEAR_PLANT_UPKEEP = 500.0

    // Wind, solar and batteries: upkeep a month; the wind speeds a wind farm starts making power at and
    // makes its most at; how much of the sun a fully overcast sky keeps off; how much a wind farm's
    // noise takes off the land within a couple of tiles.
    const val WIND_UPKEEP = 40.0
    const val SOLAR_UPKEEP = 30.0
    const val BATTERY_UPKEEP = 50.0
    const val WIND_START = 15
    const val WIND_FULL = 60
    const val CLOUD_SHADE = 75
    const val WIND_VALUE = 6

    /** What a highway's noise takes off the land within two tiles of it. */
    const val HIGHWAY_VALUE = 14

    // On the water: upkeep a month; how much more wind there is offshore, in wind speed; how many days the
    // tide takes to come round to the same hour, the least it's running at, and how far from the map's edge
    // tidal water reaches up an estuary.
    const val RIVER_TURBINE_UPKEEP = 8.0
    const val TIDAL_UPKEEP = 60.0
    const val OFFSHORE_UPKEEP = 90.0
    const val OFFSHORE_WIND_GAIN = 15
    const val TIDE_DAYS = 15
    const val TIDE_LEAST = 15
    const val TIDE_REACH = 10
    // Fuel a month for each megawatt made, which sets the order the grid runs them in.
    const val COAL_FUEL = 3.5
    const val GAS_FUEL = 4.0
    const val OIL_FUEL = 5.5
    const val NUCLEAR_FUEL = 0.5
    const val SUBSTATION_UPKEEP = 10.0

    // Power: the evening peak above the day's average, in percent; what's lost per thousand for each tile
    // along ordinary lines; what a substation passes, in watts; the year high-voltage lines come in;
    // watts a month for each rider on electric transit; the least a station smokes, in percent of full.
    const val EVENING_PEAK = 15
    const val LINE_LOSS = 4
    /** What an ordinary line carries before it's overloaded, in kilowatts, and how much more it loses a step past that, per thousand. */
    const val LINE_RATING = 8_000
    const val OVERLOAD_LOSS = 15
    const val SUBSTATION_RATING = 20_000_000
    const val HIGH_LINE_YEAR = 1920
    const val TRACTION_W = 40
    const val IDLE_FUMES = 25

    // Rivers: how far sewage spreads upstream against the flow, and how much further downstream; works' waste, per job, by water.
    const val UPSTREAM = 1
    const val DOWNSTREAM_REACH = 2
    const val WORKS_FOUL = 2

    // Smog: how much of the town's pollution hangs in still air, more in the cold and fog; what it costs health and appeal.
    const val SMOG_COLD = 150
    const val SMOG_FOG = 130
    const val SMOG_HEALTH = 6
    const val SMOG_APPEAL = 8
    const val SMOG_WARNING = 60
    /** The fewest built tiles smog is spread over. */
    const val SMOG_TOWN = 300

    // Heat: green or water within reach of a tile cools it this much each; what a hot summer home loses in appeal.
    const val HEAT_REACH = 2
    const val GREEN_COOLS = 8
    const val HEAT_APPEAL = 16
    const val STREET_TREE_UPKEEP = 0.1
    const val STREET_TREE_VALUE = 4
    const val STREET_TREE_SOAK = 15

    // Contaminated land: what living by brownfield or a dump costs health, and a dump's land value.
    const val CONTAMINATED_HEALTH = 10
    const val DUMP_HEALTH = 5
    const val DUMP_VALUE = 30

    // Garbage: a dump's room in kilograms, what an incinerator and a recycling centre take a month, how far they reach,
    // recycling's share of what it's given, upkeep, and what going uncollected costs appeal, health and the street.
    const val DUMP_ROOM = 30_000_000
    const val INCINERATOR_TAKES = 600_000
    const val RECYCLING_TAKES = 300_000
    const val GARBAGE_REACH = 60
    /** Below this many people, each home burns or buries its own garbage. */
    const val GARBAGE_TOWN = 1500
    /** A dump this close to full, in kilograms, counts as full. */
    const val DUMP_FULL = 200_000
    const val RECYCLED = 30
    const val DUMP_UPKEEP = 20.0
    const val INCINERATOR_UPKEEP = 80.0
    const val RECYCLING_UPKEEP = 60.0
    const val INCINERATOR_FUMES = 25
    const val UNCOLLECTED_APPEAL = 6
    const val UNCOLLECTED_HEALTH = 6
    const val UNCOLLECTED_GRIME = 12

    // Disasters. Each chance is for normal; "fewer" halves it.
    // Gales: the chance in a hundred each power line, wire, tree and street tree comes down; days and cost to mend a line.
    const val GALE_DOWN = 3
    const val MEND_LINE = 3
    const val REPAIR_LINE = 20L
    // Blizzards: snow this heavy in this much wind, and the days the roads are snowed in, a day less for each bus garage.
    const val BLIZZARD = 85
    const val BLIZZARD_DAYS = 4
    const val BLIZZARD_WIND = 65
    // Heat waves (as hot as the climate's [Climate.heatWave]): what a hot home loses in health; elderly deaths a month
    // per thousand for each 10 of heat.
    const val HEAT_HEALTH = 10
    const val HEAT_DEATHS = 2
    const val HEAT_WAVE_PEAK = 15
    const val HOT_HOME = 100
    // Industrial accidents: the chance in a million each month for heavy works, and a nuclear station's.
    const val ACCIDENT_PPM = 200
    const val NUCLEAR_PPM = 5
    const val NUCLEAR_WEAR_PPM = 200
    const val NUCLEAR_REACH = 6
    const val NUCLEAR_BILL = 500_000L
    const val SPILL_FOUL = 200
    // Earthquakes: the chance in a million each month, how far one reaches, and what it costs a building it damages.
    const val QUAKE_PPM = 2_083
    const val QUAKE_REACH = 14
    const val QUAKE_BILL = 200L
    const val MEND_QUAKE = 20
    const val MEND_EXPLOSION = 45
    const val BUILDING_CODES = 1935
    // Epidemics: the chance in a million each month of one starting, deaths a month per thousand among children and the
    // elderly in a stricken home, and what it costs its health.
    const val EPIDEMIC_PPM = 1_500
    const val EPIDEMIC_HEALTH = 25
    const val EPIDEMIC_CHILD_DEATHS = 15
    const val EPIDEMIC_ELDERLY_DEATHS = 60

    /** How far land round a hydro station goes under its reservoir. */
    const val RESERVOIR_REACH = 2
    const val POLICE_UPKEEP = 40.0
    const val FIRE_UPKEEP = 45.0
    const val PARK_UPKEEP = 0.5

    /** How far a fully funded station reaches, in tiles. Less money, less reach, down to 40% of it. */
    const val POLICE_REACH = 14
    const val FIRE_REACH = 12

    // Once they have motors, fire engines and police cars drive from their stations: full cover within the
    // first of these many seconds by road, fading to none at the second. Police cars from this year.
    const val FIRE_RESPONSE_FULL = 360
    const val FIRE_RESPONSE_MOST = 600
    const val POLICE_RESPONSE_FULL = 360
    const val POLICE_RESPONSE_MOST = 600
    const val PATROL_CAR_YEAR = 1920

    // Crowding: how far past its places a school or doctor takes people in, in percent; each then gets
    // the share its places make of those it takes.
    const val OVERFILL = 140

    // The new services. A volunteer hall's strength against a fire station's, in percent. Ladders from
    // 1905, without which a tall building's fire can't be fought. Ambulances: health they add at full cover,
    // and the share of adult and elderly deaths they save. A nursing home's places and the share of its
    // residents' deaths it saves. A library's reach and the schooling it adds. A college's places and reach,
    // and the appeal it lends offices nearby.
    const val VOLUNTEER_STRENGTH = 50
    const val LADDER_YEAR = 1905
    const val AMBULANCE_HEALTH = 8
    const val AMBULANCE_SAVES = 25
    const val NURSING_PLACES = 150
    const val NURSING_REACH = 14
    const val NURSING_SAVES = 30
    const val LIBRARY_REACH = 10
    const val LIBRARY_SCHOOLING = 15
    const val COLLEGE_PLACES = 600
    const val COLLEGE_REACH = 30
    const val COLLEGE_OFFICES = 8

    // Upkeep a month.
    const val VOLUNTEER_UPKEEP = 8.0
    const val LADDER_UPKEEP = 40.0
    const val AMBULANCE_UPKEEP = 35.0
    const val NURSING_UPKEEP = 40.0
    const val LIBRARY_UPKEEP = 12.0
    const val COLLEGE_UPKEEP = 150.0

    // Crime by kind. The share of theft and vice, in percent, full police cover puts off. Rackets from 1920: how much
    // of the theft and vice where justice fails feeds them a month (one part in this), how much they fade a
    // month anyway and with a fully staffed police headquarters, and how much they put businesses off.
    const val THEFT_POLICE = 60
    const val VICE_POLICE = 40
    const val SHOP_THEFT = 12
    const val NIGHTLIFE = 12
    const val RACKETS_YEAR = 1920
    const val RACKETS_GROW = 10
    const val RACKETS_FADE = 2
    const val DETECTIVES = 14
    const val RACKETS_APPEAL = 6

    // Justice. People times crime over 255, over this, are the offences a month; this share of those in
    // police cover end in an arrest. A police station hears this many cases a month and holds this many,
    // a courthouse hears this many and a jail holds this many. This share of the cases heard end in a
    // sentence, of this many months on average. Crime is up to this much more, in percent, where no
    // arrest sticks. A jail takes this much off land value within this many tiles.
    const val OFFENCE_SHARE = 10
    const val ARREST_SHARE = 60
    const val LOCKUP_CASES = 15
    const val CELLS = 10
    const val COURT_CASES = 150
    const val JAIL_PLACES = 400
    const val CONVICTED = 70
    const val SENTENCE = 6
    const val JUSTICE_SLACK = 30
    const val JAIL_VALUE = 15
    const val JAIL_REACH = 3

    // Upkeep a month.
    const val HQ_UPKEEP = 120.0
    const val COURT_UPKEEP = 80.0
    const val JAIL_UPKEEP = 150.0

    // A service past its expected life works at less, by half a point a point of wear past it, down to this;
    // renovating one costs this share of its price and shuts it for this many days.
    const val WORN_SERVICE = 60
    const val RENOVATE_SHARE = 30
    const val RENOVATE_DAYS = 30
    /** A service runs at no less than this share of its strength, however short of staff the town is. */
    const val LEAST_STAFF = 30

    /** Motor fire engines, from this year, reach further. */
    const val MOTOR_FIRE_YEAR = 1915
    const val MOTOR_FIRE_REACH = 18

    /** Chance in ten thousand each month that a building catches fire with no fire station near, in 1900. */
    const val FIRE_CHANCE = 10
    const val FIRE_CHANCE_INDUSTRY = 25

    /** How much more readily a wooden first-rung building catches, and how much less a solid one, in percent. */
    const val WOODEN_FIRE = 140
    const val SOLID_FIRE = 60

    /** Chance in a hundred each day that a fire spreads to a building next to it. */
    const val FIRE_SPREAD = 4

    /** How many days a fire burns, and up to how many more: a minute or two at normal speed. */
    const val FIRE_DAYS = 3
    const val FIRE_DAYS_MORE = 4

    /** The weather changes every this many days: a few spells in each month's day and night. */
    const val WEATHER_DAYS = 5

    /** Fire cover at which a burning building is saved, damaged, rather than lost. */
    const val FIRE_SAVED = 80

    /** Growth a month can't go past, as a share of what the zone has, with a floor for small towns. */
    const val GROWTH_SHARE = 0.2
    const val GROWTH_FLOOR = 60

    /** Road upkeep over water is this many times as much. */
    const val BRIDGE_UPKEEP = 3.0

    /** Shopping trips a month: one for this many residents, and how many a shop job serves. */
    const val RESIDENTS_PER_SHOPPER = 4
    const val SHOPPERS_PER_SHOP_JOB = 8

    /** Loads of freight a month for every ten jobs at a works. */
    const val FREIGHT_PER_TEN_JOBS = 3

    /** Longest trip anyone makes to work or the shops, and for freight, in seconds. */
    const val LONGEST_TRIP = 90 * 60
    const val LONGEST_FREIGHT = 3 * 3_600

    /**
     * What a commute does to a home's appeal: nothing up to [FINE_COMMUTE]
     * minutes, then a point for every two minutes more, up to [LONG_COMMUTE].
     * No job in reach at all costs [NO_COMMUTE].
     */
    const val FINE_COMMUTE = 15
    const val LONG_COMMUTE = 15
    const val NO_COMMUTE = 15

    /** A point of appeal to a shop for this many trips a month past its door, up to [PASSING_TRADE]. */
    const val TRIPS_PER_PASSING_POINT = 40
    const val PASSING_TRADE = 10

    /** What it costs a works' appeal when its freight can't get out. */
    const val FREIGHT_STUCK = 15

    /** Monthly upkeep of a tile of track, a station and a freight yard. */
    const val RAIL_UPKEEP = 0.6
    const val STATION_UPKEEP = 25.0
    const val YARD_UPKEEP = 40.0

    /** Seconds for a train to cross a tile, and the wait for one at a station. */
    const val RAIL_TIME = 6
    const val RAIL_WAIT = 300

    /** Seconds a road trip loses at a level crossing. */
    const val CROSSING_DELAY = 20

    /** And at one across a highway, where fast traffic has to come to a stop for the train. */
    const val HIGHWAY_CROSSING_DELAY = 90

    /** Passengers or loads a day that make a train's worth, for how busy a line looks. */
    const val TRAIN_LOAD = 8

    /** The outside market with a freight yard on a line to the edge, and settlers with a station on one. */
    const val RAIL_EXPORTS = 1.3
    const val RAIL_SETTLERS = 1.5

    // A freight terminal on a line to the edge: the outside market, more than a yard's; how far works feel the
    // pull of one and how much; a little more again with a port it's near, so containers go ship to train; and
    // its monthly upkeep.
    const val TERMINAL_EXPORTS = 1.6
    const val TERMINAL_REACH = 10
    const val TERMINAL_APPEAL = 8
    const val PORT_RAIL_REACH = 8
    const val PORT_RAIL_EXPORTS = 1.1
    const val TERMINAL_UPKEEP = 150.0

    // Ports: the outside market with a port ships reach, by how big a port it is (none, a wharf, docks, a container
    // port); coal and fuel oil brought in by ship, against the usual markup; what the town charges a load through
    // a port and a visitor off a ship; and each kind of port's monthly upkeep.
    val PORT_EXPORTS = doubleArrayOf(1.0, 1.15, 1.3, 1.6)
    const val PORT_IMPORT_MARKUP = 1.2
    const val PORT_DUE = 0.4
    const val SEA_VISITOR_DUE = 0.5
    val PORT_UPKEEP = doubleArrayOf(0.0, 30.0, 80.0, 250.0)

    // Airports, by size (none, an airfield, an airport, a big one): visitors a day they can bring, office jobs they
    // draw, settlers they bring as a share more, how far their noise carries and what it takes off the land and off
    // homes' appeal at its worst, upkeep a month, and loads of air freight a month they can take. What the town
    // takes in landing fees for each visitor and each load.
    val AIR_VISITORS = intArrayOf(0, 20, 150, 600)
    val AIR_OFFICES = intArrayOf(0, 10, 80, 300)
    val AIR_SETTLERS = doubleArrayOf(1.0, 1.05, 1.15, 1.3)
    val AIR_NOISE_REACH = intArrayOf(0, 2, 5, 8)
    val AIR_NOISE = intArrayOf(0, 6, 20, 35)
    val AIR_UPKEEP = doubleArrayOf(0.0, 40.0, 300.0, 1200.0)
    val AIR_CARGO = intArrayOf(0, 0, 40, 200)
    const val LANDING_FEE = 1.0
    const val AIR_CARGO_FEE = 2.0

    // Ships: what a step costs a ship, and more for each bit of shore beside it, so they keep to the middle; loads
    // that make a ship's worth, for how many come.
    const val SHIP_STEP = 10
    const val SHIP_SHORE = 4
    const val SHIP_LOAD = 40
    /** What waiting for a bridge to open costs a ship's way, against a step of open water. */
    const val SHIP_WAIT = 30
    // Bridges: a worn one is posted against trucks at this much wear, and shut as unsafe at this much; how a shut
    // one's marked; days a long high one stays shut after a gale; loads of trucks in a month that wear a bridge a
    // month more. A lifting bridge holds the traffic this many seconds on average for each ship a month, and at
    // most this long. Tolls: the cents to cross to start with and at most, and the seconds each cent puts a driver off.
    const val POSTED_WEAR = 70
    const val UNSAFE_WEAR = 100
    const val SHUT_UNSAFE = 255
    const val GALE_SHUT_DAYS = 2
    const val TRUCK_WEAR_LOADS = 300
    const val LIFT_DELAY = 4
    const val LIFT_MAX = 60
    const val TOLL_CENTS = 25
    const val TOLL_MOST = 200
    const val TOLL_SECONDS_PER_CENT = 4

    // Tunnels: road tunnels from this year; their upkeep a tile, more for the fans and pumps of a road one; and how
    // far from a tunnel power has to be to keep its pumps going.
    const val ROAD_TUNNEL_YEAR = 1920
    const val ROAD_TUNNEL_UPKEEP = 2.0
    const val RAIL_TUNNEL_UPKEEP = 1.0

    /** Ships burn coal and smoke until this year, and trains run on steam until this one. */
    const val STEAM_UNTIL = 1950
    const val STEAM_TRAINS_UNTIL = 1955

    // Warming: the world's from this year, in tenths of a degree a decade; the town's own share at most, in tenths,
    // and its carbon, in tonnes, for each tenth.
    const val WARMING_FROM = 1980
    /** The Future era asks for carbon a person a month no more than this, in kilograms. */
    const val FUTURE_CARBON = 500L
    const val WARMING_PER_DECADE = 3
    const val TOWN_WARMING_MOST = 3
    const val CARBON_PER_TENTH = 2_000_000L

    // Carbon a month: hours in a month; tonnes for each point of a works' pollution; for each hundred thousand
    // vehicle-tiles at a hundred percent of the year's fumes; and for heating, in hundredths of a tonne a person for
    // each degree below [HEATING_BELOW]. On the map, the square root of the tonnes times this, and how much more a
    // road's counts, since it's spread along the road.
    const val HOURS_A_MONTH = 720L
    const val WORKS_CARBON = 2L
    const val TRAFFIC_CARBON = 12L
    const val HEATING_BELOW = 12
    const val HEATING_CARBON = 3L
    const val CARBON_MAP_SCALE = 12.0
    const val ROAD_CARBON_SHOW = 20L

    // Cooling the town: cool roofs and paving from this year cut what roofs and paving give off to this share; green
    // roofs from this year count as this share of green, and hold this share of the rain off a roof; what each costs
    // a building a month; what green roofs add to homes' appeal; and how much more a park cools from this year (splash
    // pads and shade).
    const val COOL_ROOF_YEAR = 1990
    const val COOL_ROOF_HEAT = 60
    const val GREEN_ROOF_YEAR = 2000
    const val GREEN_ROOF_GREEN = 50
    const val GREEN_ROOF_RAIN = 30
    const val COOL_ROOF_UPKEEP = 0.1
    const val GREEN_ROOF_UPKEEP = 0.3
    const val GREEN_ROOF_APPEAL = 3
    const val PARK_COOLS_YEAR = 1970
    const val PARK_COOLS_MORE = 50
    // Cooling centres: people they take in a heat wave, how far, and the share of heat deaths they save; their upkeep.
    const val COOLING_PLACES = 2_500
    const val COOLING_REACH = 10
    const val COOLING_SAVES = 75
    const val COOLING_UPKEEP = 10.0
    /** In a district with cool or green roofs, homes need this much less air conditioning. */
    const val COOL_ROOF_POWER = 15

    /** Towers need a ladder company this close, in the ladder cover's terms. */
    const val TOWER_LADDER = 60

    /**
     * A building the zoning no longer allows stays until it's this many years
     * old, then comes down at one chance in [NONCONFORMING_ODDS] a month.
     */
    const val NONCONFORMING_YEARS = 30
    const val NONCONFORMING_ODDS = 120

    /** What clearing a building pays its owners for each place in it, at middling land value: homes, shops and offices, works and farms. */
    const val WORTH_HOME = 60
    const val WORTH_SHOP = 80
    const val WORTH_WORKS = 50

    /** An empty home is worth this share, in percent; a heritage building twice as much. */
    const val WORTH_EMPTY = 25
    const val HERITAGE_WORTH = 2

    /**
     * Neighbours upset by a clearing: as far as [UPSET_REACH], or
     * [HERITAGE_UPSET_REACH] for heritage outside a district. [UPSET] at once,
     * [UPSET_MORE] for each clearing after, fading [UPSET_FADE] a month; a
     * home loses a point of appeal for each [UPSET_APPEAL].
     */
    const val UPSET_REACH = 4
    const val HERITAGE_UPSET_REACH = 12
    const val UPSET = 100
    const val UPSET_MORE = 20
    const val UPSET_FADE = 5
    const val UPSET_APPEAL = 8

    /**
     * The status line: a shortage of power or water this many percent of
     * what's wanted, a zone this wanted, a sample of this many of its lots,
     * and garbage collected under this percent.
     */
    const val ADVICE_SHORT = 5
    const val ADVICE_DEMAND = 20
    const val ADVICE_SAMPLE = 60
    const val ADVICE_GARBAGE = 80

    /**
     * Commuters a month a link over the border carries: for a road, this
     * percent of its capacity, and for a track, so many by train.
     */
    const val COMMUTERS_PER_CAPACITY = 50
    const val RAIL_LINK_COMMUTERS = 1500

    /** Loads of goods a month a link carries: for a road, this percent of its capacity, and for a track, so many. */
    const val LOADS_PER_CAPACITY = 10
    const val RAIL_LINK_LOADS = 600

    /** Water meeting the map's edge along this many tiles is open sea, which rivers run down to. */
    const val SEA_EDGE = 24

    /** How far in from the edge water has to reach to count as the sea there. */
    const val SEA_DEPTH = 5

    /** Homes for each shop job in a mixed building, roughly, for sharing demand between zones. */
    const val MIXED_PEOPLE_PER_JOB = 3

    /** From 2000, homes over shops near a tram or bus stop are wanted more: transit-oriented. */
    const val MIXED_TRANSIT_FROM = 2000
    const val MIXED_TRANSIT_APPEAL = 10

    /** People forced out of town put off settlers, by up to this share in percent, fading a twelfth a month. */
    const val DISPLACED_MOST = 50

    /** Jets from this year at the big airports. */
    const val JET_YEAR = 1960

    // Visitors, on an average day: some come to any town, more to a bigger one, and more for its parks and heritage.
    // Most can come by road once there are cars; trains, ships and planes bring more. They stay in hotels if
    // there's room, a share of them, and spend more than people who live here; day trippers spend less.
    const val VISITORS_BASE = 20.0
    const val VISITORS_PER_RESIDENT = 0.02
    const val PARK_DRAW = 1.5
    const val HERITAGE_DRAW = 4.0
    const val ROAD_VISITORS = 30
    const val ROAD_VISITORS_BY_CAR = 150
    const val RAIL_VISITORS = 60
    val SEA_VISITORS = intArrayOf(0, 20, 150, 0)
    const val STAY_SHARE = 50
    const val ROOMS_PER_JOB = 3
    const val GUEST_SPEND = 150
    const val TRIPPER_SPEND = 80
    /** How far a hotel looks for parks to please its guests, in tiles. */
    const val HOTEL_PARKS = 4

    /** Monthly upkeep of a tile of pipe, and of each part of the waterworks. */
    const val PIPE_UPKEEP = 0.15
    const val PUMP_UPKEEP = 30.0
    const val WELL_UPKEEP = 10.0
    const val TOWER_UPKEEP = 8.0
    const val OUTFALL_UPKEEP = 5.0
    const val SEWAGE_WORKS_UPKEEP = 40.0
    const val TREATMENT_UPKEEP = 120.0

    /** People a pumping station and a well field supply, residents and workers alike. */
    const val PUMP_SUPPLY = 3_000
    const val WELL_SUPPLY = 800

    /** How far water goes along the mains from a source or a tower before the pressure's too low, in tiles. */
    const val PRESSURE_REACH = 30

    /** How near a building has to be to a main or a sewer to be on it, in tiles: as far as a road reaches. */
    const val PIPE_REACH = 3

    /** How far sewage spreads through the water from an outfall, in tiles, and how much there is for every hundred people. */
    const val FOUL_REACH = 20
    const val FOUL_PER_HUNDRED = 6

    /** Fire cover mains water adds, for the hydrants. */
    const val HYDRANT_COVER = 90

    /** Monthly upkeep of a storm pond and a storm outfall. */
    const val POND_UPKEEP = 4.0
    const val STORM_OUTFALL_UPKEEP = 3.0

    /**
     * Stormwater. Rain runs off each tile by its share of hard surface, and
     * snowmelt this many times over for each point of snow cover lost. The soft
     * ground around soaks up [ABSORB] percent of the rain that falls on it; what
     * neither soaks up nor drains away within [FLOOD_AREA] tiles stands as a
     * flood, [FLOOD_SCALE] levels for each unit left on a tile.
     */
    const val MELT_RUNOFF = 1

    /** Rain or melt less than this soaks in everywhere. */
    const val DOWNPOUR = 25
    const val ABSORB = 120
    const val FLOOD_AREA = 2
    const val FLOOD_SCALE = 3

    /**
     * The ground: each wet spell soaks it by this share of the rain and melt, in
     * percent, and a dry spell dries it this much, more in warm weather. Soaked
     * ground takes in only [SOAKED_SOAK] percent of what dry ground would, and
     * frozen ground [FROZEN_SOAK].
     */
    const val GROUND_WETS = 30
    const val GROUND_DRIES = 25
    const val GROUND_DRIES_WARM = 40
    const val SOAKED_SOAK = 15
    const val FROZEN_SOAK = 20

    /**
     * What runs off over the ground to somewhere lower, even from paving, in a
     * downpour: this much a tile when the land round about is dry, down to
     * [RUNS_AWAY_SOAKED] when it's soaked.
     */
    const val RUNS_AWAY_DRY = 45
    const val RUNS_AWAY_SOAKED = 15

    /**
     * The rivers: they rise by this share of the rain and melt, in percent, from
     * dry ground and from soaked, fall this much a day, and past [BANKFULL]
     * spill onto the land beside them, a tile deeper for every [SPILL_STEP] over.
     */
    const val RIVER_RISE_DRY = 15
    const val RIVER_RISE_SOAKED = 55
    const val RIVER_FALL = 3
    const val BANKFULL = 70
    const val SPILL_STEP = 15
    const val SPILL_MOST = 3

    /** What a storm pond holds in one downpour, and how far round it it catches the rain without drains. */
    const val POND_HOLDS = 4_000
    const val POND_REACH = 3

    /** The share of runoff the sewers take where there are no storm drains, in percent. It all goes out at the outfalls. */
    const val SEWER_TAKES = 50

    /**
     * After a flood: the clean-up bill for each flooded building (times its
     * stage), road tile and track tile; mud left as grime, this share of the
     * flood level in percent; and the memory of it, fading this much a month,
     * which costs land value (a point for every [STIGMA_VALUE]) and a home's
     * appeal (one for every [STIGMA_APPEAL]).
     */
    const val CLEANUP_BUILDING = 10L
    const val CLEANUP_ROAD = 3L
    const val CLEANUP_TRACK = 4L
    const val MUD = 40
    const val STIGMA_FADE = 3
    const val STIGMA_VALUE = 6
    const val STIGMA_APPEAL = 12

    /**
     * Sickness: a flooded home on a well or a septic tank loses people one time
     * in [SICK_CHANCE]; one on the sewer when the sewers overflowed, one in
     * [SICK_CHANCE_SEWER].
     */
    const val SICK_CHANCE = 4
    const val SICK_CHANCE_SEWER = 10

    /**
     * Flood levels: from [FLOODED] a place is flooded, shops and works shut, and
     * power and pumping stations stop; from [FLOOD_DAMAGE] the water's deep,
     * roads and track are closed and a building can be damaged, one time in
     * [FLOOD_DAMAGE_CHANCE].
     */
    const val FLOODED = 64
    const val FLOOD_DAMAGE = 128
    const val FLOOD_DAMAGE_CHANCE = 5

    /** How much a flood goes down each day, what it costs a place's appeal, and how much slower it makes a road. */
    const val FLOOD_DRAIN = 20
    const val FLOOD_APPEAL = 15
    const val FLOOD_SLOW = 3

    /** Appeal of mains water and the sewer to a home, and what a well in grime costs it. */
    const val MAINS_APPEAL = 5
    const val SEWER_APPEAL = 3
    const val BAD_WELL = 10

    /**
     * Schools: pupils a school and a high school take, fully funded, and how far
     * children come to them. Children of high school age are a third of them.
     */
    const val SCHOOL_PLACES = 240
    const val HIGH_SCHOOL_PLACES = 400
    const val SCHOOL_REACH = 12
    const val HIGH_SCHOOL_REACH = 18
    const val TEENS = 3

    /** Months for children's schooling to catch up with how many of them have a place: about three years. */
    const val SCHOOLING_PACE = 36

    /** Health care: people a clinic and a hospital look after, fully funded, and how far they come. */
    const val CLINIC_CARES = 1_500
    const val HOSPITAL_CARES = 12_000
    const val CLINIC_REACH = 8
    const val HOSPITAL_REACH = 22

    /** Monthly upkeep of a school, high school, clinic and hospital, fully funded. */
    const val SCHOOL_UPKEEP = 30.0
    const val HIGH_SCHOOL_UPKEEP = 60.0
    const val CLINIC_UPKEEP = 15.0
    const val HOSPITAL_UPKEEP = 90.0

    /**
     * Health: where it settles, from a starting [HEALTH_BASE], and how fast it
     * gets there, a [HEALTH_PACE]th of the gap a month. Care adds up to
     * [CARE_HEALTH], mains water and the sewer [MAINS_HEALTH] each, wealth
     * [WEALTH_HEALTH] a class, a park nearby [PARK_HEALTH]; pollution takes a
     * point for every [POLLUTION_HEALTH], grime [GRIME_HEALTH] a step, a
     * tenement's crowding [CROWDING_HEALTH], and a sickness [SICK_HEALTH] at once.
     */
    const val HEALTH_BASE = 50
    const val HEALTH_PACE = 6
    const val CARE_HEALTH = 25
    const val MAINS_HEALTH = 6
    const val WEALTH_HEALTH = 5
    const val PARK_HEALTH = 4
    const val POLLUTION_HEALTH = 5
    const val GRIME_HEALTH = 4
    const val CROWDING_HEALTH = 8
    const val SICK_HEALTH = 20

    /** Health below which people start to move away. */
    const val UNHEALTHY = 40

    /** Land value up to which homes are built poor, and from which they're built well off. */
    const val POOR_BELOW = 70
    const val WELL_OFF_FROM = 120

    /** Share of a skill's jobs going unfilled from which businesses wanting it can't grow, in percent. */
    const val SKILL_SHORT = 25

    // Wear: the chance in millionths a month that something fails at its expected life, and at most.
    const val FAIL_AT_LIFE = 400
    const val FAIL_MOST = 20_000

    /** How long track lasts, in years; tram track, trolleybus wire and subway tunnel too. */
    const val TRACK_LIFE = 35
    const val TRAM_TRACK_LIFE = 30
    const val WIRE_LIFE = 25
    const val TUNNEL_LIFE = 80

    // Days to mend a failure, and what it costs.
    const val MEND_MAIN = 4
    const val MEND_SEWER = 7
    const val MEND_DRAIN = 5
    const val MEND_ROAD = 3
    const val MEND_TRACK = 4
    const val MEND_PLANT = 21
    const val REPAIR_MAIN = 120L
    const val REPAIR_SEWER = 200L
    const val REPAIR_DRAIN = 120L
    const val REPAIR_ROAD = 30L
    const val REPAIR_TRACK = 90L
    const val MEND_WIRE = 2
    const val MEND_TUNNEL = 10
    const val REPAIR_WIRE = 40L
    const val REPAIR_TUNNEL = 600L

    /** A patch puts back a tenth of a thing's life; it doesn't make it new. */
    const val PATCH_SHARE = 10

    /** How deep a burst main floods the street, and how much grime a broken sewer leaves round it. */
    const val BURST_FLOOD = 200
    const val SEWER_GRIME = 60

    /** How much a broken road surface slows the traffic over it. */
    const val POTHOLE_SLOW = 3

    /** Days the crews spend relaying a tile, and how many tiles of a programme they start a day. */
    const val WORKS_DAYS = 6
    const val WORKS_PER_DAY = 3

    /** Relaying a worn road costs this much of a new one, in percent, and the pipes under it this much of theirs. */
    const val RENEW_ROAD = 60
    const val PIPES_WITH_ROAD = 50

    /** Worn this far through its life, something can be relaid. */
    const val RENEWABLE_WEAR = 40

    // From HERITAGE_FROM, solid old buildings put up before HERITAGE_BEFORE are sought after instead of shabby.
    const val HERITAGE_FROM = 1970
    const val HERITAGE_BEFORE = 1930
    const val HERITAGE_APPEAL = 10

    /** From this year, works that close leave brownfield, which costs this a tile to clean and takes this off land value round it. */
    const val BROWNFIELD_FROM = 1960
    const val BROWNFIELD_VALUE = 25

    /** Homes past this many years lose appeal, up to WORN_APPEAL at twice that. */
    const val WORN_YEARS = 40
    const val WORN_APPEAL = 10

    // Getting about: seconds to cross a tile on foot, by tram and by subway, and what a bus stopping adds a tile.
    const val WALK_TIME = 60
    const val TRAM_TIME = 20
    const val SUBWAY_TIME = 8
    const val BUS_STOPPING = 6

    // Waits to board, in seconds, on a well served network, and how much one depot, garage or station serves:
    // tiles of track, road or tunnel, and riders a month.
    const val TRAM_WAIT = 240
    const val BUS_WAIT = 300
    const val SUBWAY_WAIT = 180
    const val TRACK_PER_DEPOT = 120
    const val ROAD_PER_GARAGE = 300
    const val TUNNEL_PER_STATION = 15
    const val TRAM_CAPACITY = 3_000
    // Lines: how many trams a depot keeps and buses a garage; riders a vehicle carries a month before it's crowded;
    // the shortest wait at a stop; what a vehicle costs to buy and keep a month.
    const val DEPOT_HOLDS = 12
    /** Stops a line made for a town from before lines takes at most. */
    const val AUTO_LINE_STOPS = 8
    const val GARAGE_HOLDS = 20
    const val TRAM_VEHICLE_RIDERS = 1_200
    const val BUS_VEHICLE_RIDERS = 700
    const val SHORTEST_WAIT = 30
    /** The wait a new line is given vehicles for, in seconds. */
    const val AIMED_WAIT = 300
    const val TRAM_PRICE = 4_000L
    const val BUS_PRICE = 1_500L
    const val TRAM_VEHICLE_UPKEEP = 30.0
    const val BUS_VEHICLE_UPKEEP = 20.0
    // Bus and tram lanes: what they cost a tile, the share of the road left for cars, and the wait at a crossing for what uses them.
    const val LANE_PRICE = 60L
    const val LANE_UPKEEP = 0.2
    const val LANE_CAR_SHARE = 60
    const val LANE_JUNCTION = 3
    const val BUS_CAPACITY = 2_500
    const val SUBWAY_CAPACITY = 5_000

    /** Land value for a tram or bus stop within STOP_REACH tiles, and for a subway station within SUBWAY_REACH. */
    const val STOP_REACH = 3
    const val STOP_VALUE = 6
    const val SUBWAY_REACH = 5
    const val SUBWAY_VALUE = 12

    /** Pollution on a road for every hundred vehicles over it a month, as dirty as a 1920s car; how far it spreads; what a bus counts for in cars. */
    const val FUMES_PER_HUNDRED = 8
    const val FUMES_REACH = 2
    const val BUS_FUMES = 3

    /** What a rider pays to board a tram, bus or subway train. */
    const val FARE = 0.30

    /** Trolleybuses' first year. */
    const val TROLLEYBUS_YEAR = 1925

    /** Riders a month on trams, trolleybuses and the subway for each point of pollution they add at each power station. */
    const val RIDERS_PER_PLANT_POINT = 400

    // Upkeep a month: a tile of tram track, of overhead wire, of subway tunnel, and a stop; depots, garages and stations.
    const val TRAM_TRACK_UPKEEP = 0.3
    const val WIRE_UPKEEP = 0.2
    const val TUNNEL_UPKEEP = 1.5
    const val STOP_UPKEEP = 1.0
    const val DEPOT_UPKEEP = 40.0
    const val GARAGE_UPKEEP = 30.0
    const val SUBWAY_STATION_UPKEEP = 25.0

    // Eras' milestones.
    const val STREETCAR_PEOPLE = 1_500
    const val MOTOR_PEOPLE = 8_000
    const val MOTOR_SERVED = 60
    const val MOTOR_POWERED = 75
    const val RENEWAL_PEOPLE = 25_000
    const val INFILL_LAND = 75
    const val FUTURE_KEPT_UP = 90
    const val FUTURE_GREEN_TRIPS = 33

    // What homes expect as the years go by: mains water, the sewer and power go
    // from a draw to expected, and going without counts against a home.
    const val MAINS_FADES = 1915
    const val MAINS_EXPECTED_BY = 1945
    const val MAINS_EXPECTED = 14
    const val SEWER_FADES = 1920
    const val SEWER_EXPECTED_BY = 1955
    const val SEWER_EXPECTED = 12
    const val POWER_FADES = 1920
    const val POWER_EXPECTED_BY = 1950
    const val POWER_EXPECTED = 10

    // Land value for people and jobs within ACTIVITY_REACH tiles: a point for every ACTIVITY_PER_VALUE of them, up to ACTIVITY_VALUE.
    const val ACTIVITY_REACH = 6
    const val ACTIVITY_PER_VALUE = 25
    const val ACTIVITY_VALUE = 45

    // An empty home's chance of selling each month, in percent: SALE_BASE with
    // no one looking, SALE_PER_DEMAND more for every percent of the town that's
    // looking for a home, between SALE_LEAST and SALE_MOST.
    const val SALE_BASE = 12
    const val SALE_PER_DEMAND = 6
    const val SALE_LEAST = 3
    const val SALE_MOST = 85

    /** How much less an empty home holds on when the town has too many homes. */
    const val EMPTY_SHRINK = 30

    // Goods. What a load brought in costs against what it fetches; coal a coal station burns a month for
    // each megawatt it makes; how much further than the edge freight will go to a buyer in town, in seconds.
    const val IMPORT_MARKUP = 1.5
    /** Dollars a load is worth at a price of 1, for the trade figures. */
    const val LOAD_VALUE = 10.0
    const val COAL_PER_MW = 2
    /** Fuel oil an oil station burns a month for each megawatt, and refineries from this year. */
    const val FUEL_PER_MW = 2
    const val REFINERY_YEAR = 1905
    /** What shops need to sell, in hundredths of a load a month for each job: food, goods, and fuel for the town's cars. */
    const val SHOP_FOOD = 20
    const val SHOP_GOODS = 20
    const val SHOP_FUEL = 30
    /** How much less a shop earns that has to bring in all it sells, in percent, and one that can't get stock at all. */
    const val IMPORT_DRAG = 25
    const val NO_STOCK = 50
    const val EXPORT_DETOUR = 1_800

    // Farmland answers a share of the outside market, as industry does, and what the town brings in from
    // the land; works answer the market and what the town brings in of what they make. Jobs a load stands for.
    const val FARM_MARKET = 0.3
    const val LOADS_PER_FARM_JOB = 1.0
    const val LOADS_PER_WORKS_JOB = 0.35

    // How much a works likes getting what it needs in town, and a farm or mine selling there, at most.
    const val LOCAL_APPEAL = 10
    const val FARMLAND_APPEAL = 45
    const val POOR_SOIL = 50
    /** Office jobs the town wants for each hundred people, in 1900 and by 2000, and a few to start. */
    const val OFFICES_1900 = 2.0
    const val OFFICES_2000 = 16.0
    const val OFFICE_BASE = 10.0
    /** Office work pays more tax a job than a shop's, for the same land. */
    const val OFFICE_TAX = 1.5
    /** At most how much a farmland lot appeals more for making what the town's short of. */
    const val SHORT_APPEAL = 25

    // Junctions: how much longer a car waits at a full crossing, and at most; how busy, against the road's
    // capacity, a crossing gets stop signs and then lights on its own, in percent.
    const val JUNCTION_SLOPE = 40
    const val JUNCTION_MOST = 240
    const val AUTO_STOP = 30
    const val AUTO_LIGHTS = 70
    // Traffic flow: the flow at which shops and offices are neither helped nor hurt, and at most how much;
    // fumes from crawling traffic, in percent more at most, and more for each second spent waiting at a crossing.
    const val FLOW_PAR = 85
    // Green against pollution: what each tile of park or woods within reach takes off, a street tree half that,
    // in percent, and at most; how much of what crosses a belt of park or woods gets through; scrubbers on a
    // coal or oil station, from when, what they cost and keep, and the share of smoke they let out.
    const val GREEN_SINK = 4
    const val GREEN_SINK_MOST = 50
    const val GREEN_SINK_REACH = 2
    const val BELT_PASSES = 40
    const val SCRUBBER_YEAR = 1970
    const val SCRUBBER_PRICE = 5_000L
    const val SCRUBBER_UPKEEP = 25.0
    const val SCRUBBED_SHARE = 30
    // Districts: how much a point of tax off puts on a lot's appeal, how far a district's tax may move from the
    // town's, and how much limited parking cuts driving, in percent.
    const val DISTRICT_TAX_APPEAL = 2
    const val DISTRICT_TAX_RANGE = 5
    const val PARKING_CUT = 35
    // Free fares: how many seconds shorter a free ride feels to someone choosing how to go. No heavy trucks:
    // how many times slower a truck finds a street it's kept off. Pollution limit: the share works give off,
    // and the appeal it costs them. Rent control: the share of rent, and so tax, homes bring in.
    const val FREE_FARE_PULL = 240
    const val TRUCK_BAN_SLOW = 6
    const val CLEAN_WORKS_SHARE = 40
    const val CLEAN_WORKS_APPEAL = 8
    const val RENT_CONTROL_TAX = 75
    const val MAX_DISTRICTS = 40
    /** The highest rung of industry a district with no heavy industry lets in. */
    const val LIGHT_INDUSTRY = 2
    /** The flow the Renewal era asks for. */
    const val RENEWAL_FLOW = 75
    const val FLOW_APPEAL = 8
    const val IDLE_MOST = 60
    const val IDLE_PER_WAIT = 2

    /** How far a lot can be from a road and still grow, in tiles. */
    const val ROAD_REACH = 3

    /** How many lots are looked at for each thing that grows, picking the best. */
    const val CANDIDATES = 8


    /** Days a building waits after growing before it grows again. */
    const val SETTLE_DAYS = 20

    /** How long a building stands before it's pulled down for something bigger. */
    const val REBUILD_DAYS = 180
}
