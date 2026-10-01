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
    fun landValue(map: CityMap, buildingTypes: (Int) -> BuildingType?, nearRoad: BooleanArray, out: ByteArray) {
        val w = map.width
        val h = map.height
        val water = SummedArea(w, h) { if (map.terrain[it] == Terrain.WATER) 1 else 0 }
        val trees = SummedArea(w, h) { if (map.terrain[it] == Terrain.TREES) 1 else 0 }
        val foul = if (map.foul.any { it.toInt() != 0 }) SummedArea(w, h) { map.foul[it].toInt() and 0xff } else null
        val parks = SummedArea(w, h) { if (buildingTypes(it) == BuildingType.PARK) 1 else 0 }
        val shops = SummedArea(w, h) { buildingTypes(it)?.let { t -> if (t.zone == Zone.COMMERCIAL) t.capacity else 0 } ?: 0 }
        val industry = SummedArea(w, h) { if (buildingTypes(it)?.zone == Zone.INDUSTRIAL) 1 else 0 }
        val traffic = SummedArea(w, h) { map.congestion[it].toInt() and 0xff }
        // The railway, if there is one, read straight off the map.
        val railway = map.rail.any { it != Rail.NONE }
        fun typeOn(i: Int) = map.buildingType[i].toInt() - 1
        val stations = if (railway) SummedArea(w, h) { val t = typeOn(it); if (t == STATION || t == STATION_NS) 1 else 0 } else null
        val yards = if (railway) SummedArea(w, h) { val t = typeOn(it); if (t == YARD || t == YARD_NS) 1 else 0 } else null
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
            if (nearRoad[i]) v += 10
            v += (map.policeCover[i].toInt() and 0xff) / 10 + (map.fireCover[i].toInt() and 0xff) / 12
            v -= (map.pollution[i].toInt() and 0xff) / 2
            v -= (map.crime[i].toInt() and 0xff) / 3
            if (industry.around(x, y, 2) > 0 && buildingTypes(i)?.zone != Zone.INDUSTRIAL) v -= 15
            // Buyers remember floods.
            v -= (map.floodMemory[i].toInt() and 0xff) / Balance.STIGMA_VALUE
            // The noise of busy roads, trains and yards, and a station within a walk.
            v -= min(20, traffic.around(x, y, 1) / 48)
            if (stations != null && track != null && yards != null) {
                if (stations.around(x, y, 5) > 0) v += 12
                if (track.around(x, y, 1) > 0) v -= 6
                if (yards.around(x, y, 2) > 0 && buildingTypes(i)?.zone != Zone.INDUSTRIAL) v -= 10
            }
            out[i] = v.coerceIn(0, 255).toByte()
        }
    }

    /**
     * Crime, 0 to 255, where people are: crowding, cheap land and joblessness
     * raise it, police lower it. Empty land has none.
     */
    fun crime(map: CityMap, residents: (Int) -> Int, occupied: (Int) -> Boolean, unemployment: Int, out: ByteArray) {
        val w = map.width
        val h = map.height
        val people = SummedArea(w, h, residents)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (!occupied(i)) {
                out[i] = 0
                continue
            }
            var v = people.around(x, y, 3) / 6
            v += max(0, 80 - (map.landValue[i].toInt() and 0xff)) / 3
            v += unemployment
            v -= (map.policeCover[i].toInt() and 0xff) * 7 / 10
            out[i] = v.coerceIn(0, 255).toByte()
        }
    }
}
