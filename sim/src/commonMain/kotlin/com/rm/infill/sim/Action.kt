package com.rm.infill.sim

/** Something the player does to the map. Every change goes through [City.plan] and [City.apply]. */
sealed interface Action {
    /**
     * A road of [type] along [tiles], given as map indices in order. One-way
     * roads run the way it was drawn. A two-wide road is drawn along its
     * right-hand carriageway, and stops at the first turn. With [pipes], a
     * water main, a sewer and a storm drain go under it too.
     */
    data class BuildRoad(val tiles: IntArray, val type: RoadType = RoadType.DIRT, val pipes: Boolean = false) : Action {
        override fun equals(other: Any?) = other is BuildRoad && type == other.type && pipes == other.pipes && tiles.contentEquals(other.tiles)
        override fun hashCode() = (tiles.contentHashCode() * 31 + type.hashCode()) * 2 + if (pipes) 1 else 0
    }

    /** Zones every tile from [x0], [y0] to [x1], [y1] as [zone]. */
    data class PlaceZone(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val zone: Byte, val density: Byte = Density.MEDIUM) : Action

    /** Clears roads, track, zones, power lines, buildings and trees from [x0], [y0] to [x1], [y1]. Pipes stay. */
    data class Bulldoze(val x0: Int, val y0: Int, val x1: Int, val y1: Int) : Action

    /** An embankment along [tiles], given as map indices in order, to keep a swollen river off the land behind it. */
    data class BuildBank(val tiles: IntArray) : Action {
        override fun equals(other: Any?) = other is BuildBank && tiles.contentEquals(other.tiles)
        override fun hashCode() = tiles.contentHashCode()
    }

    /** Tram track along the streets on [tiles], given as map indices in order. */
    data class BuildTram(val tiles: IntArray) : Action {
        override fun equals(other: Any?) = other is BuildTram && tiles.contentEquals(other.tiles)
        override fun hashCode() = tiles.contentHashCode()
    }

    /** Overhead wire for trolleybuses along the roads on [tiles], given as map indices in order. */
    data class BuildWire(val tiles: IntArray) : Action {
        override fun equals(other: Any?) = other is BuildWire && tiles.contentEquals(other.tiles)
        override fun hashCode() = tiles.contentHashCode()
    }

    /** Sets the crossings on [tiles] to [control] ([Junction]), or back to the town's choice with [Junction.AUTO]. */
    data class SetJunction(val tiles: IntArray, val control: Byte) : Action {
        override fun equals(other: Any?) = other is SetJunction && control == other.control && tiles.contentEquals(other.tiles)
        override fun hashCode() = tiles.contentHashCode() * 31 + control
    }

    /** A new line for trams or buses calling at [stops], map indices in order, run by [vehicles]. */
    data class AddLine(val tram: Boolean, val stops: IntArray, val vehicles: Int) : Action {
        override fun equals(other: Any?) = other is AddLine && tram == other.tram && vehicles == other.vehicles && stops.contentEquals(other.stops)
        override fun hashCode() = stops.contentHashCode() * 31 + vehicles
    }

    /** Line [id] run by [vehicles]; bought if more, the rest sold back if fewer. */
    data class SetVehicles(val id: Int, val vehicles: Int) : Action

    /** Line [id] taken off, its vehicles sold. */
    data class RemoveLine(val id: Int) : Action

    /** A lane kept for buses and trams along the roads on [tiles], map indices in order. */
    data class BuildLane(val tiles: IntArray) : Action {
        override fun equals(other: Any?) = other is BuildLane && tiles.contentEquals(other.tiles)
        override fun hashCode() = tiles.contentHashCode()
    }

    /** Paints the rectangle into district [id]: 0 takes it out of any, [NEW_DISTRICT] makes a new one. */
    data class PaintDistrict(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val id: Int) : Action

    /** District [id]'s policies set to [to], a copy with its changes. */
    data class SetDistrict(val id: Int, val to: District) : Action

    /** District [id] taken off the map. */
    data class RemoveDistrict(val id: Int) : Action

    /** Scrubbers fitted to the coal or oil station on [x], [y]. */
    data class FitScrubbers(val x: Int, val y: Int) : Action

    /** Street trees along the roads on [tiles], given as map indices in order. */
    data class PlantStreetTrees(val tiles: IntArray) : Action {
        override fun equals(other: Any?) = other is PlantStreetTrees && tiles.contentEquals(other.tiles)
        override fun hashCode() = tiles.contentHashCode()
    }

    /** Subway tunnel under [tiles], given as map indices in order. */
    data class BuildSubway(val tiles: IntArray) : Action {
        override fun equals(other: Any?) = other is BuildSubway && tiles.contentEquals(other.tiles)
        override fun hashCode() = tiles.contentHashCode()
    }

    /** A tram or bus stop ([Stop]) on the road at [x], [y]. */
    data class PlaceStop(val x: Int, val y: Int, val kind: Int) : Action

    /** Takes up the tram track, overhead wire, stops and subway tunnels from [x0], [y0] to [x1], [y1]. */
    data class RemoveTransit(val x0: Int, val y0: Int, val x1: Int, val y1: Int) : Action

    /**
     * Relays everything worn from [x0], [y0] to [x1], [y1]: roads, pipes,
     * track, tram track, wire and tunnels, a programme of works the crews go
     * through a few tiles a day.
     */
    data class RenewArea(val x0: Int, val y0: Int, val x1: Int, val y1: Int) : Action

    /** Takes up the water mains, sewers and storm drains from [x0], [y0] to [x1], [y1]. */
    data class RemovePipes(val x0: Int, val y0: Int, val x1: Int, val y1: Int) : Action

    /** A power line along [tiles], given as map indices in order. */
    /** A telephone trunk line, copper or fibre, on poles or in a duct underground. */
    data class BuildPhoneLine(val tiles: IntArray, val fibre: Boolean = false, val buried: Boolean = false) : Action {
        override fun equals(other: Any?) = other is BuildPhoneLine && fibre == other.fibre && buried == other.buried && tiles.contentEquals(other.tiles)
        override fun hashCode() = tiles.contentHashCode() * 4 + (if (fibre) 2 else 0) + (if (buried) 1 else 0)
    }

    /** Takes up the telephone lines in a rectangle. */
    data class RemovePhone(val x0: Int, val y0: Int, val x1: Int, val y1: Int) : Action

    data class BuildPowerLine(val tiles: IntArray, val high: Boolean = false, val buried: Boolean = false) : Action {
        override fun equals(other: Any?) = other is BuildPowerLine && high == other.high && buried == other.buried && tiles.contentEquals(other.tiles)
        override fun hashCode() = tiles.contentHashCode()
    }

    /** Railway track along [tiles], given as map indices in order. */
    data class BuildRail(val tiles: IntArray) : Action {
        override fun equals(other: Any?) = other is BuildRail && tiles.contentEquals(other.tiles)
        override fun hashCode() = tiles.contentHashCode()
    }

    /** A pipe of [kind] along [tiles], given as map indices in order. */
    data class BuildPipe(val tiles: IntArray, val kind: Pipe, val material: Material? = null) : Action {
        override fun equals(other: Any?) = other is BuildPipe && kind == other.kind && material == other.material && tiles.contentEquals(other.tiles)
        override fun hashCode() = tiles.contentHashCode() * 3 + kind.ordinal
    }

    /** A building the player places, like a power station, with its top left corner at [x], [y]. */
    data class PlaceBuilding(val type: BuildingType, val x: Int, val y: Int) : Action

    /** Parks on every free tile from [x0], [y0] to [x1], [y1]. Trees stay and become part of them. */
    data class PlaceParks(val x0: Int, val y0: Int, val x1: Int, val y1: Int) : Action

    companion object {
        /**
         * The tiles of a road dragged from one tile to another: along one axis and
         * then the other, like an L. [acrossFirst] goes east or west first.
         */
        fun roadPath(map: CityMap, x0: Int, y0: Int, x1: Int, y1: Int, acrossFirst: Boolean): IntArray {
            val out = ArrayList<Int>()
            var x = x0
            var y = y0
            out += map.index(x, y)
            fun stepX() { while (x != x1) { x += if (x1 > x) 1 else -1; out += map.index(x, y) } }
            fun stepY() { while (y != y1) { y += if (y1 > y) 1 else -1; out += map.index(x, y) } }
            if (acrossFirst) { stepX(); stepY() } else { stepY(); stepX() }
            return out.toIntArray()
        }
    }
}

/** The pipes under the map, and what each costs a tile. */
enum class Pipe(val price: Long) { WATER(15), SEWER(20), STORM(18) }

/** Why an action, an undo or a redo can't go ahead. */
/** The id a [Action.PaintDistrict] gives to make a new district. */
const val NEW_DISTRICT = -1

enum class Problem { NotEnoughMoney, NothingToDo, Blocked, TownBuiltThere, NeedsTrack, NeedsWater, NeedsTramTrack, NeedsTunnel, NoRoute }

/**
 * What an action would do: its [cost], the tiles it [changes] and the ones it
 * has to leave alone because they're [blocked]. [problem] is set if it can't go
 * ahead at all.
 */
class Plan(val cost: Long, val changes: IntArray, val blocked: IntArray, val problem: Problem?) {
    val ok get() = problem == null
}

/** What things cost, in the dollars of 1900. */
object Prices {
    /** A road over water costs this many times as much. */
    const val BRIDGE = 6
    const val CLEAR_TREES = 5L
    const val ZONE = 5L
    const val REMOVE_ROAD = 2L
    const val POWER_LINE = 5L
    const val REMOVE_LINE = 1L
    const val DEMOLISH = 15L
    const val COAL_PLANT = 3_000L
    const val POLICE_STATION = 1_500L
    const val FIRE_STATION = 1_800L
    const val PARK = 60L
    const val RAIL = 40L
    const val REMOVE_RAIL = 3L
    const val STATION = 1_200L
    const val FREIGHT_YARD = 2_500L
    const val REMOVE_PIPE = 2L
    const val BANK = 30L
    const val REMOVE_BANK = 5L
    const val PUMPING_STATION = 2_500L
    const val WELL_FIELD = 900L
    const val WATER_TOWER = 700L
    const val OUTFALL = 400L
    const val SEWAGE_WORKS = 3_000L
    const val HIGH_LINE = 20L

    /** Telephone lines a tile: copper and fibre, on poles and in a duct. Taking one up. */
    const val COPPER = 8L
    const val COPPER_DUCT = 40L
    const val FIBRE = 25L
    const val FIBRE_DUCT = 90L
    const val REMOVE_PHONE = 1L

    /** Power cable laid underground, and high-voltage cable, a tile. */
    const val CABLE = 30L
    const val HIGH_CABLE = 150L
    const val STREET_TREE = 15L
    const val OIL_PLANT = 6_000L
    const val GAS_PLANT = 15_000L
    const val HYDRO_PLANT = 10_000L
    const val NUCLEAR_PLANT = 80_000L
    const val SUBSTATION = 800L
    const val DUMP = 1_500L
    const val INCINERATOR = 5_000L
    const val RECYCLING = 4_000L
    const val TRAM_TRACK = 25L
    const val WIRE = 15L
    const val TUNNEL = 300L
    const val STOP = 50L
    const val REMOVE_TRANSIT = 5L
    const val TRAM_DEPOT = 2_000L
    const val BUS_GARAGE = 1_500L
    const val SUBWAY_STATION = 2_500L
    const val CLEAN_UP = 150L
    const val TREATMENT_PLANT = 9_000L
    const val STORM_POND = 600L
    const val STORM_OUTFALL = 300L
    const val SCHOOL = 1_600L
    const val HIGH_SCHOOL = 3_500L
    const val CLINIC = 700L
    const val HOSPITAL = 6_000L

    /** What it costs to put up a building the player places. */
    fun of(type: BuildingType): Long = when (type) {
        BuildingType.COAL_PLANT -> COAL_PLANT
        BuildingType.OIL_PLANT -> OIL_PLANT
        BuildingType.GAS_PLANT -> GAS_PLANT
        BuildingType.HYDRO_PLANT -> HYDRO_PLANT
        BuildingType.NUCLEAR_PLANT -> NUCLEAR_PLANT
        BuildingType.SUBSTATION -> SUBSTATION
        BuildingType.DUMP -> DUMP
        BuildingType.INCINERATOR -> INCINERATOR
        BuildingType.RECYCLING -> RECYCLING
        BuildingType.POLICE_STATION -> POLICE_STATION
        BuildingType.FIRE_STATION -> FIRE_STATION
        BuildingType.PARK -> PARK
        BuildingType.STATION, BuildingType.STATION_NS -> STATION
        BuildingType.FREIGHT_YARD, BuildingType.FREIGHT_YARD_NS -> FREIGHT_YARD
        BuildingType.PUMPING_STATION -> PUMPING_STATION
        BuildingType.WELL_FIELD -> WELL_FIELD
        BuildingType.WATER_TOWER -> WATER_TOWER
        BuildingType.OUTFALL -> OUTFALL
        BuildingType.SEWAGE_WORKS -> SEWAGE_WORKS
        BuildingType.TRAM_DEPOT -> TRAM_DEPOT
        BuildingType.BUS_GARAGE -> BUS_GARAGE
        BuildingType.SUBWAY_STATION -> SUBWAY_STATION
        BuildingType.TREATMENT_PLANT -> TREATMENT_PLANT
        BuildingType.STORM_POND -> STORM_POND
        BuildingType.STORM_OUTFALL -> STORM_OUTFALL
        BuildingType.SCHOOL -> SCHOOL
        BuildingType.HIGH_SCHOOL -> HIGH_SCHOOL
        BuildingType.CLINIC -> CLINIC
        BuildingType.HOSPITAL -> HOSPITAL
        BuildingType.VOLUNTEER_HALL -> 500L
        BuildingType.EXCHANGE -> 3_000L
        BuildingType.CELL_TOWER -> 4_000L
        BuildingType.POLICE_HQ -> 6_000L
        BuildingType.COURTHOUSE -> 5_000L
        BuildingType.JAIL -> 8_000L
        BuildingType.LADDER_COMPANY -> 2_500L
        BuildingType.AMBULANCE_STATION -> 2_000L
        BuildingType.NURSING_HOME -> 4_000L
        BuildingType.LIBRARY -> 1_500L
        BuildingType.COLLEGE -> 25_000L
        else -> 0L
    }
}
