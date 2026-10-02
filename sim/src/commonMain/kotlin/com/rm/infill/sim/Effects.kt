package com.rm.infill.sim

import kotlin.math.max
import kotlin.math.min

/**
 * Sums over squares of a map layer in constant time: build it once from a
 * value per tile, then ask for the total within [r] tiles of any tile.
 */
internal class SummedArea(private val width: Int, private val height: Int, value: (Int) -> Int) {
    private val table = LongArray((width + 1) * (height + 1))

    init {
        for (y in 0 until height) {
            var row = 0L
            for (x in 0 until width) {
                row += value(y * width + x)
                table[(y + 1) * (width + 1) + x + 1] = table[y * (width + 1) + x + 1] + row
            }
        }
    }

    /** The total over the square within [r] tiles of [x], [y], clipped to the map. */
    fun around(x: Int, y: Int, r: Int): Int {
        val x0 = max(0, x - r)
        val y0 = max(0, y - r)
        val x1 = min(width, x + r + 1)
        val y1 = min(height, y + r + 1)
        val w = width + 1
        return (table[y1 * w + x1] - table[y0 * w + x1] - table[y1 * w + x0] + table[y0 * w + x0]).toInt()
    }
}

/**
 * The monthly effects that follow from what's on the map: how far the police
 * and fire stations reach, what land is worth, and where crime is.
 */
internal object Effects {
    private val STATION = BuildingType.STATION.ordinal
    private val STATION_NS = BuildingType.STATION_NS.ordinal
    private val YARD = BuildingType.FREIGHT_YARD.ordinal
    private val YARD_NS = BuildingType.FREIGHT_YARD_NS.ordinal
    private val TERMINAL = BuildingType.FREIGHT_TERMINAL.ordinal
    private val TERMINAL_NS = BuildingType.FREIGHT_TERMINAL_NS.ordinal

    /** Cover from each station, strongest at it and fading to nothing at its reach. */
    fun cover(map: CityMap, stations: List<Building>, reach: Int, out: ByteArray) {
        out.fill(0)
        for (b in stations) {
            val cx = b.x + b.type.width / 2
            val cy = b.y + b.type.height / 2
            for (dy in -reach..reach) for (dx in -reach..reach) {
                val x = cx + dx
                val y = cy + dy
                if (!map.inside(x, y)) continue
                val d2 = dx * dx + dy * dy
                if (d2 > reach * reach) continue
                // Full cover for most of the way, falling off over the last third.
                val d = kotlin.math.sqrt(d2.toDouble())
                val v = if (d < reach * 2 / 3.0) 255 else (255 * (reach - d) / (reach / 3.0)).toInt()
                val i = map.index(x, y)
                if (v > (out[i].toInt() and 0xff)) out[i] = v.coerceIn(0, 255).toByte()
            }
        }
    }

    /**
     * Land value, 0 to 255: clean water, trees and parks nearby, being near the shops
     * and on a road, a station nearby, and police and fire cover raise it;
     * pollution, crime, industry next door, busy roads, track and freight
     * yards lower it.
     */
    fun landValue(map: CityMap, buildingTypes: (Int) -> BuildingType?, nearRoad: BooleanArray, out: ByteArray, activity: (Int) -> Int = { 0 }) {
        val w = map.width
        val h = map.height
        val water = SummedArea(w, h) { if (map.terrain[it] == Terrain.WATER) 1 else 0 }
        val trees = SummedArea(w, h) { if (map.terrain[it] == Terrain.TREES) 1 else 0 }
        val foul = if (map.foul.any { it.toInt() != 0 }) SummedArea(w, h) { map.foul[it].toInt() and 0xff } else null
        val parks = SummedArea(w, h) { if (buildingTypes(it) == BuildingType.PARK) 1 else 0 }
        val shops = SummedArea(w, h) { buildingTypes(it)?.let { t -> if (t.zone == Zone.COMMERCIAL || t.zone == Zone.OFFICE) t.capacity else t.jobs } ?: 0 }
        val industry = SummedArea(w, h) { if (buildingTypes(it)?.zone == Zone.INDUSTRIAL) 1 else 0 }
        val traffic = SummedArea(w, h) { map.congestion[it].toInt() and 0xff }
        val busy = SummedArea(w, h, activity)
        val avenues = if (map.streetTrees.any { it.toInt() != 0 }) SummedArea(w, h) { map.streetTrees[it].toInt() } else null
        val dumps = SummedArea(w, h) { if (buildingTypes(it) == BuildingType.DUMP) 1 else 0 }
        val jails = SummedArea(w, h) { if (buildingTypes(it) == BuildingType.JAIL) 1 else 0 }
        val highways = if (map.road.any { RoadType.of(it)?.limited == true }) SummedArea(w, h) { if (RoadType.of(map.road[it])?.limited == true) 1 else 0 } else null
        val turbines = SummedArea(w, h) { if (buildingTypes(it) == BuildingType.WIND_FARM) 1 else 0 }
        val overhead = map.power.any { it != Power.NONE }
        val poles = if (overhead) SummedArea(w, h) { if (map.power[it] == Power.LINE && !map.cable(it)) 1 else 0 } else null
        val pylons = if (overhead) SummedArea(w, h) { if (map.power[it] == Power.HIGH && !map.cable(it)) 1 else 0 } else null
        val stops = if (map.stop.any { it.toInt() != 0 }) SummedArea(w, h) { if (map.stop[it].toInt() != 0) 1 else 0 } else null
        val subway = SummedArea(w, h) { if (buildingTypes(it) == BuildingType.SUBWAY_STATION) 1 else 0 }
        val fouled = if (map.brownfield.any { it.toInt() != 0 }) SummedArea(w, h) { map.brownfield[it].toInt() } else null
        // The railway, if there is one, read straight off the map.
        val railway = map.rail.any { it != Rail.NONE }
        fun typeOn(i: Int) = map.buildingType[i].toInt() - 1
        val stations = if (railway) SummedArea(w, h) { val t = typeOn(it); if (t == STATION || t == STATION_NS) 1 else 0 } else null
        val yards = if (railway) SummedArea(w, h) { val t = typeOn(it); if (t == YARD || t == YARD_NS || t == TERMINAL || t == TERMINAL_NS) 1 else 0 } else null
        val track = if (railway) SummedArea(w, h) { if (map.rail[it] != Rail.NONE) 1 else 0 } else null
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (map.terrain[i] == Terrain.WATER) {
                out[i] = 0
                continue
            }
            var v = 60
            if (water.around(x, y, 3) > 0) v += 25
            // Unless the water's foul.
            if (foul != null) v -= min(30, foul.around(x, y, 3) / 60)
            v += min(trees.around(x, y, 2) * 3, 18)
            v += min(parks.around(x, y, 4) * 8, 32)
            v += min(shops.around(x, y, 8) / 3, 40)
            // Where much goes on, the land's wanted: a busy centre grows dear.
            v += min(busy.around(x, y, Balance.ACTIVITY_REACH) / Balance.ACTIVITY_PER_VALUE, Balance.ACTIVITY_VALUE)
            if (nearRoad[i]) v += 10
            v += (map.policeCover[i].toInt() and 0xff) / 10 + (map.fireCover[i].toInt() and 0xff) / 12
            v -= (map.pollution[i].toInt() and 0xff) / 2
            v -= (map.crime[i].toInt() and 0xff) / 3
            if (industry.around(x, y, 2) > 0 && buildingTypes(i)?.zone != Zone.INDUSTRIAL) v -= 15
            // A street with trees, and none of a dump's smell.
            if (avenues != null && avenues.around(x, y, 1) > 0) v += Balance.STREET_TREE_VALUE
            if (dumps.around(x, y, 4) > 0 && buildingTypes(i) != BuildingType.DUMP) v -= Balance.DUMP_VALUE
            // Poles and wires next door, and pylons a little further.
            if (poles != null && poles.around(x, y, 1) > 0) v -= Balance.POLE_VALUE
            if (pylons != null && pylons.around(x, y, 2) > 0) v -= Balance.PYLON_VALUE
            // The roar of a highway.
            if (highways != null && map.road[i] == Road.NONE && highways.around(x, y, 2) > 0) v -= Balance.HIGHWAY_VALUE
            // The hum of turbines.
            if (turbines.around(x, y, 2) > 0 && buildingTypes(i) != BuildingType.WIND_FARM) v -= Balance.WIND_VALUE
            // Nor next to a jail.
            if (jails.around(x, y, Balance.JAIL_REACH) > 0 && buildingTypes(i) != BuildingType.JAIL) v -= Balance.JAIL_VALUE
            // A tram or bus stop round the corner, and a subway station a walk away.
            if (stops != null && stops.around(x, y, Balance.STOP_REACH) > 0) v += Balance.STOP_VALUE
            if (subway.around(x, y, Balance.SUBWAY_REACH) > 0) v += Balance.SUBWAY_VALUE
            // Nobody wants to live by a fouled works site.
            if (fouled != null && fouled.around(x, y, 2) > 0) v -= Balance.BROWNFIELD_VALUE
            // Buyers remember floods.
            v -= (map.floodMemory[i].toInt() and 0xff) / Balance.STIGMA_VALUE
            // The noise of busy roads, trains and yards, and a station within a walk.
            v -= min(20, traffic.around(x, y, 1) / 48)
            if (stations != null && track != null && yards != null) {
                if (stations.around(x, y, 5) > 0) v += 12
                if (track.around(x, y, 1) > 0) v -= 6
                if (yards.around(x, y, 2) > 0 && buildingTypes(i)?.zone != Zone.INDUSTRIAL) v -= 10
            }
            // Under the planes.
            if (buildingTypes(i)?.zone != Zone.INDUSTRIAL) v -= map.noise[i].toInt() and 0xff
            out[i] = v.coerceIn(0, 255).toByte()
        }
    }

    /**
     * Crime, 0 to 255, where people are, by kind. Theft follows cheap land,
     * joblessness and shops to steal from; vice follows crowding and busy
     * shopping streets. Police on patrol put off a share of both, theft the
     * more, and catch some of the rest. All of it is the two
     * with half the rackets already there, and more where [justice], the
     * percent of arrests that stick, is low. Empty land has none.
     */
    fun crime(map: CityMap, residents: (Int) -> Int, shops: (Int) -> Int, occupied: (Int) -> Boolean, unemployment: Int, justice: Int) {
        val w = map.width
        val h = map.height
        val people = SummedArea(w, h, residents)
        val trade = SummedArea(w, h, shops)
        val slack = 100 + Balance.JUSTICE_SLACK * (100 - justice.coerceIn(0, 100)) / 100
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (!occupied(i)) {
                map.crime[i] = 0
                map.theft[i] = 0
                map.vice[i] = 0
                continue
            }
            val police = map.policeCover[i].toInt() and 0xff
            val nearShops = trade.around(x, y, 3)
            var theft = max(0, 80 - (map.landValue[i].toInt() and 0xff)) / 3 + unemployment
            theft += min(Balance.SHOP_THEFT, nearShops / 20)
            theft = theft * (255 * 100 - police * Balance.THEFT_POLICE) / (255 * 100)
            var vice = people.around(x, y, 3) / 6 + min(Balance.NIGHTLIFE, nearShops / 30)
            vice = vice * (255 * 100 - police * Balance.VICE_POLICE) / (255 * 100)
            theft = theft.coerceIn(0, 255) * slack / 100
            vice = vice.coerceIn(0, 255) * slack / 100
            map.theft[i] = theft.coerceIn(0, 255).toByte()
            map.vice[i] = vice.coerceIn(0, 255).toByte()
            map.crime[i] = (theft + vice + (map.rackets[i].toInt() and 0xff) / 2).coerceIn(0, 255).toByte()
        }
    }

    /**
     * A month of rackets, from 1920: they feed on the theft and vice where
     * justice fails and the police are thin, spread a little to the
     * neighbours, and fade, the faster with [detectives] on them.
     */
    fun rackets(map: CityMap, justice: Int, detectives: Int) {
        val w = map.width
        val h = map.height
        val unpunished = 150 - justice.coerceIn(0, 100)
        val grown = IntArray(map.size)
        for (i in 0 until map.size) {
            val feed = ((map.theft[i].toInt() and 0xff) + (map.vice[i].toInt() and 0xff)) * unpunished / 100 / Balance.RACKETS_GROW
            val police = (map.policeCover[i].toInt() and 0xff) / 20
            grown[i] = ((map.rackets[i].toInt() and 0xff) + feed - police - Balance.RACKETS_FADE - detectives).coerceIn(0, 255)
        }
        // A racket reaches the next street: each tile takes a quarter of the way to its strongest neighbour.
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            var most = grown[i]
            if (x > 0) most = max(most, grown[i - 1])
            if (x < w - 1) most = max(most, grown[i + 1])
            if (y > 0) most = max(most, grown[i - w])
            if (y < h - 1) most = max(most, grown[i + w])
            map.rackets[i] = (grown[i] + (most - grown[i]) / 4).coerceIn(0, 255).toByte()
        }
    }
}
