package com.rm.infill.sim

/**
 * A named part of the town, painted onto the map, with its own policies: its
 * taxes set above or below the town's, how high it may build, whether its
 * old buildings are kept, whether parking is limited, and whether heavy
 * industry is let in.
 */
class District(val id: Int, var name: String) {
    /** Points above or below the town's tax, for homes, shops and offices, and works. */
    val tax = IntArray(3)

    /** The highest density let in ([Density]), or [Density.NONE] for no limit. */
    var height: Byte = Density.NONE

    /** Heritage buildings here are kept, never pulled down to build bigger. */
    var heritage = false

    /** Parking's limited: fewer people here drive. */
    var parking = false

    /** No heavy industry: works here stay small. */
    var lightIndustry = false

    /** Free fares on the buses, trolleybuses and trams boarded here. */
    var freeFares = false

    /** No heavy trucks: freight keeps off these streets unless it has business here. */
    var noTrucks = false

    /** A pollution limit: works here give off less, and find it dearer to set up. */
    var cleanWorks = false

    /** Rent control: homes here stay within reach of poorer households. */
    var rentControl = false

    /** Cool roofs and paving (from 1990): pale, so roofs and streets here give off less heat. */
    var coolRoofs = false

    /** Green roofs (from 2000): planted, so roofs here cool like greenery and hold some of the rain. */
    var greenRoofs = false

    fun copy() = District(id, name).also { d ->
        tax.copyInto(d.tax)
        d.height = height
        d.heritage = heritage
        d.parking = parking
        d.lightIndustry = lightIndustry
        d.freeFares = freeFares
        d.noTrucks = noTrucks
        d.cleanWorks = cleanWorks
        d.rentControl = rentControl
        d.coolRoofs = coolRoofs
        d.greenRoofs = greenRoofs
    }

    internal fun writeTo(w: SaveWriter) {
        w.int(id); w.string(name)
        for (v in tax) w.int(v)
        w.int(height.toInt()); w.bool(heritage); w.bool(parking); w.bool(lightIndustry)
        // Since version 16.
        w.bool(freeFares); w.bool(noTrucks); w.bool(cleanWorks); w.bool(rentControl)
        // Since version 28.
        w.bool(coolRoofs); w.bool(greenRoofs)
    }

    companion object {
        internal fun readFrom(r: SaveReader, version: Int): District {
            val d = District(r.int(), r.string())
            for (k in d.tax.indices) d.tax[k] = r.int()
            d.height = r.int().toByte(); d.heritage = r.bool(); d.parking = r.bool(); d.lightIndustry = r.bool()
            if (version >= 16) {
                d.freeFares = r.bool(); d.noTrucks = r.bool(); d.cleanWorks = r.bool(); d.rentControl = r.bool()
            }
            if (version >= 28) {
                d.coolRoofs = r.bool(); d.greenRoofs = r.bool()
            }
            return d
        }

        /** Which of a district's taxes a zone pays. */
        fun taxIndex(zone: Byte): Int = when (zone) {
            Zone.RESIDENTIAL, Zone.MIXED -> 0
            Zone.COMMERCIAL, Zone.OFFICE -> 1
            else -> 2
        }
    }
}

/** A district's figures as last counted. */
class DistrictFigures(val people: Int, val jobs: Int, val landValue: Int, val crime: Int, val pollution: Int, val tiles: Int)
