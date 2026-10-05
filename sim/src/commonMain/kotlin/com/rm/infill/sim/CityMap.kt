package com.rm.infill.sim

/**
 * The map as a set of layers, one array per layer, indexed by
 * `y * width + x`. Every layer the simulation adds lives here as another
 * array so the whole map can be saved and hashed in one pass.
 */
class CityMap(val width: Int, val height: Int) {
    val size = width * height

    val terrain = ByteArray(size)
    val road = ByteArray(size)

    /** Which way traffic runs on a one-way road tile ([Heading]), 0 on a road both ways. */
    val roadHeading = ByteArray(size)
    val zone = ByteArray(size)

    /** How dense each zoned tile may build ([Density]), 0 where it isn't zoned. */
    val density = ByteArray(size)
    val power = ByteArray(size)

    /** Railway track ([Rail]). A tile with road and track is a level crossing. */
    val rail = ByteArray(size)

    /** Tram track along a street, 1 where there's some, and subway tunnel under anything, 1 where there's one. */
    val tram = ByteArray(size)
    val subway = ByteArray(size)

    /** Street trees along a road, 1 where they've been planted. */
    val streetTrees = ByteArray(size)

    /** How hot each tile runs in summer, 0 to 255: paving and roofs warm it, green and water cool it. Worked out each month. */
    val heat = ByteArray(size)

    /** Overhead wire for trolleybuses along a road, 1 where there's some. */
    val wire = ByteArray(size)

    /** Tram and bus stops on a road tile ([Stop]). */
    val stop = ByteArray(size)

    /** Water mains, sewers and storm drains under each tile, laid by hand: 1 where there's one. */
    val waterPipe = ByteArray(size)
    val sewerPipe = ByteArray(size)
    val stormPipe = ByteArray(size)

    /**
     * When each tile's road, water main, sewer, storm drain and track were laid,
     * in months from January 1900 ([Ageing.monthOf]).
     */
    val roadLaid = ShortArray(size)
    val waterLaid = ShortArray(size)
    val sewerLaid = ShortArray(size)
    val stormLaid = ShortArray(size)
    val railLaid = ShortArray(size)

    /** When each tile's tram track, trolleybus wire and subway tunnel went in, the same way. */
    val tramLaid = ShortArray(size)
    val wireLaid = ShortArray(size)
    val subwayLaid = ShortArray(size)

    /** Land left fouled where works closed down, 1 where it is: nothing's built on it until it's cleaned up. */
    val brownfield = ByteArray(size)

    /** Land filled in from the water, the years it has left to green over; 0 once it has, or for land that always was. */
    val fresh = ByteArray(size)

    /** At each crossing, the control the player chose ([Junction], [Junction.AUTO] for the town's), and the one it has now. */
    val junction = ByteArray(size)
    val control = ByteArray(size)

    /** The district each tile's in, 0 for none. */
    val district = ByteArray(size)

    /** 1 where a lane of the road is kept for buses, trolleybuses and trams. */
    val lane = ByteArray(size)

    /** What's in the ground ([Resource]), laid down with the land. */
    val resource = ByteArray(size)

    /** What's broken on each tile ([Broken]), and the days left until it's mended or the works there are done. */
    val broken = ShortArray(size)
    val mending = ByteArray(size)

    fun mendingDays(i: Int): Int = mending[i].toInt() and 0xff

    /** Whether the crews are at it on tile [i]: mending a failure, or at works that have reached it rather than waiting their turn. */
    fun underRepair(i: Int): Boolean = (broken[i].toInt() and Broken.WORKS) == 0 || mendingDays(i) <= Balance.WORKS_DAYS

    /** Whether tile [i]'s pipe or track marked [bit] is out of use. */
    fun out(i: Int, bit: Int): Boolean = (broken[i].toInt() and bit) != 0 && underRepair(i)

    /** For each bridge tile and each approach to one: its kind and what's set for it ([Bridge]). */
    val bridge = ByteArray(size)

    /** Days a bridge tile stays shut by the weather, or [Balance.SHUT_UNSAFE] while it's too worn to cross. */
    val bridgeShut = ByteArray(size)

    /** 1 on a road at the edge of the map that the player has kept in town: it doesn't lead out. */
    val unlinked = ByteArray(size)

    /** Whether tile [i] is on the edge of the map. */
    fun atEdge(i: Int): Boolean {
        val x = i % width
        val y = i / width
        return x == 0 || y == 0 || x == width - 1 || y == height - 1
    }

    /** Whether the road on tile [i] leads out of town: at the edge, and not kept in. */
    fun leadsOut(i: Int): Boolean = road[i] != Road.NONE && atEdge(i) && unlinked[i].toInt() == 0

    /** The kind of bridge on tile [i], if a road or track crosses water there: null for a plain one. */
    fun bridgeKind(i: Int): BridgeKind? = BridgeKind.of(bridge[i].toInt() and Bridge.KIND)

    /** Where the town's carbon comes from, 0 to 255, worked out each month. */
    val carbon = ByteArray(size)

    /** How upset the neighbours are by homes and businesses cleared nearby, 0 to 250, fading month by month. */
    val upset = ByteArray(size)

    /** The planes' noise around each airport, 0 to 255, worked out each month. */
    val noise = ByteArray(size)

    /** For land leading up to a high bridge: [Bridge.ACROSS] if it runs east to west, 1 north to south, else 0. Worked out when the bridges change. */
    val approach = ByteArray(size)

    /**
     * A second level under the ground: the road ([RoadType] id) or track in a
     * tunnel under each tile, the way a one-way tunnel runs, and when it went
     * in. At each end a portal, where it comes up: the [Heading] it opens
     * toward, on a tile with nothing else on the surface. Under water it's a
     * tunnel under the river, under a road or track an underpass, and under
     * anything else cut and cover.
     */
    val lowRoad = ByteArray(size)
    val lowHeading = ByteArray(size)
    val lowRail = ByteArray(size)
    val portal = ByteArray(size)
    val lowLaid = ShortArray(size)

    /** Whether a road or track runs in a tunnel under tile [i]. ([lowHeading] is a [Heading] one way, or [Tunnel.NORTH_SOUTH] or [Tunnel.EAST_WEST] both ways.) */
    fun tunnelled(i: Int): Boolean = lowRoad[i].toInt() != 0 || lowRail[i].toInt() != 0

    /** Whether the tunnel under tile [i] is shut: flooded, or being dug out again. */
    fun tunnelShut(i: Int): Boolean = out(i, Broken.LOW)

    /** Whether a road or track bridges the water on tile [i]. */
    fun bridged(i: Int): Boolean = terrain[i] == Terrain.WATER && (road[i] != Road.NONE || rail[i] != Rail.NONE)

    /** Whether the bridge on tile [i] is shut: by the town, by the weather or as unsafe. */
    fun bridgeClosed(i: Int): Boolean = (bridge[i].toInt() and Bridge.SHUT) != 0 || bridgeShut[i].toInt() != 0

    /** Whether tile [i] is the middle of a swing bridge, the pier it turns on, which ships go round. */
    fun pivot(i: Int): Boolean {
        if (bridgeKind(i) != BridgeKind.SWING || !bridged(i)) return false
        val ew = (bridge[i].toInt() and Bridge.ACROSS) != 0
        val dx = if (ew) 1 else 0
        val dy = if (ew) 0 else 1
        val x = i % width
        val y = i / width
        fun on(k: Int) = inside(x + dx * k, y + dy * k) && bridged(index(x + dx * k, y + dy * k))
        var before = 0
        while (on(-(before + 1))) before++
        var after = 0
        while (on(after + 1)) after++
        return before == (before + after + 1) / 2
    }

    /** How much room the bridge on tile [i] leaves ships: plain ones are low. */
    fun clearance(i: Int): Int = bridgeKind(i)?.clearance ?: Bridge.LOW

    /** Whether the road on tile [i] is shut: dug up for a pipe, or being relaid. */
    fun closed(i: Int): Boolean {
        if (bridgeClosed(i) && terrain[i] == Terrain.WATER) return true
        val b = broken[i].toInt()
        if (b == 0 || !underRepair(i)) return false
        // A cable fault is dug up too.
        return (b and Broken.DUG) != 0 || ((b and Broken.WORKS) != 0 && (b and Broken.ROAD) != 0) ||
            ((b and Broken.POWER) != 0 && cable(i)) || ((b and Broken.PHONE) != 0 && duct(i))
    }

    /** Whether the road on tile [i] has broken up, which slows everything on it. */
    fun potholed(i: Int): Boolean = (broken[i].toInt() and (Broken.ROAD or Broken.WORKS)) == Broken.ROAD

    /** When everything on tile [i] was laid, packed into one number for undo, twelve bits each. */
    fun tileLaid(i: Int): Long {
        var v = 0L
        for (a in arrayOf(roadLaid, waterLaid, sewerLaid, stormLaid, railLaid)) v = (v shl 12) or (a[i].toLong() and 0xfff)
        return v
    }

    fun setTileLaid(i: Int, packed: Long) {
        var v = packed
        for (a in arrayOf(railLaid, stormLaid, sewerLaid, waterLaid, roadLaid)) {
            a[i] = (v and 0xfff).toShort()
            v = v shr 12
        }
    }

    /** When the transit on tile [i] went in, and its crossing's control, packed for undo like [tileLaid]. */
    fun tileTransitLaid(i: Int): Long =
        ((powerLaid[i].toLong() and 0xfff) shl 50) or ((buried[i].toLong() and 0x3) shl 48) or
            ((district[i].toLong() and 0xff) shl 40) or ((lane[i].toLong() and 0x1) shl 39) or ((junction[i].toLong() and 0x7) shl 36) or
            ((tramLaid[i].toLong() and 0xfff) shl 24) or ((wireLaid[i].toLong() and 0xfff) shl 12) or (subwayLaid[i].toLong() and 0xfff)

    fun setTileTransitLaid(i: Int, v: Long) {
        junction[i] = ((v shr 36) and 0x7).toByte()
        lane[i] = ((v shr 39) and 0x1).toByte()
        district[i] = ((v shr 40) and 0xff).toByte()
        buried[i] = ((v shr 48) and 0x3).toByte()
        powerLaid[i] = ((v shr 50) and 0xfff).toShort()
        tramLaid[i] = ((v shr 24) and 0xfff).toShort()
        wireLaid[i] = ((v shr 12) and 0xfff).toShort()
        subwayLaid[i] = (v and 0xfff).toShort()
    }

    /**
     * The way from a lot off the road to the road, for the map to draw: a path
     * leaving the tile north 1, east 2, south 4 or west 8; back lanes along
     * its edges, the same four shifted up 4, since a tile where a lane along a
     * row meets one down a column has two; and what it's made of (bits 8 and
     * 9: dirt, gravel or paved). Bit 10: the way's as wide as a lane, for the
     * deliveries to shops and works. Worked out each month.
     */
    val pathway = ShortArray(size)

    /** Telephone trunk lines: [Phone.COPPER] or [Phone.FIBRE], and when each went up. */
    val phone = ByteArray(size)
    val phoneLaid = ShortArray(size)

    /** The telephone service on each tile, worked out each month: none, a phone, broadband, fast. */
    val comms = ByteArray(size)

    /** Whether the phone line on tile [i] runs in a duct underground. */
    fun duct(i: Int): Boolean = phone[i].toInt() != 0 && (buried[i].toInt() and BURIED_PHONE) != 0

    /** The phone line on a tile, and when it went up, packed for undo. */
    fun tileUtil(i: Int): Long = ((unlinked[i].toLong() and 0x1) shl 46) or (((density[i].toLong() shr 2) and 0x1) shl 45) or ((lowLaid[i].toLong() and 0xfff) shl 33) or ((portal[i].toLong() and 0x7) shl 30) or
        ((lowRail[i].toLong() and 0x1) shl 29) or ((lowHeading[i].toLong() and 0x7) shl 26) or ((lowRoad[i].toLong() and 0xf) shl 22) or
        ((bridge[i].toLong() and 0xff) shl 14) or ((phone[i].toLong() and 0x3) shl 12) or (phoneLaid[i].toLong() and 0xfff)

    fun setTileUtil(i: Int, v: Long) {
        phone[i] = ((v shr 12) and 0x3).toByte()
        phoneLaid[i] = (v and 0xfff).toShort()
        bridge[i] = ((v shr 14) and 0xff).toByte()
        lowRoad[i] = ((v shr 22) and 0xf).toByte()
        lowHeading[i] = ((v shr 26) and 0x7).toByte()
        lowRail[i] = ((v shr 29) and 0x1).toByte()
        portal[i] = ((v shr 30) and 0x7).toByte()
        lowLaid[i] = ((v shr 33) and 0xfff).toShort()
        // Density's third bit, for tower and rural, which [tileState] has no room for.
        density[i] = ((density[i].toInt() and 0x3) or (((v shr 45) and 0x1).toInt() shl 2)).toByte()
        unlinked[i] = ((v shr 46) and 0x1).toByte()
    }

    /** Which lines on a tile run underground: [BURIED_POWER], [BURIED_PHONE]. */
    val buried = ByteArray(size)

    /** When the power line on a tile was put up or laid, in months from 1900. */
    val powerLaid = ShortArray(size)

    /** Whether the power line on tile [i] is underground cable. */
    fun cable(i: Int): Boolean = power[i] != Power.NONE && (buried[i].toInt() and BURIED_POWER) != 0

    /** What's broken on tile [i] and the days to mend it, packed for undo. */
    fun tileFix(i: Int): Int = (broken[i].toInt() and 0xffff) or (mendingDays(i) shl 16)

    fun setTileFix(i: Int, v: Int) {
        broken[i] = (v and 0xffff).toShort()
        mending[i] = (v shr 16).toByte()
    }

    /** Embankments along the water, 1 where there's one. A swollen river can't get past them. */
    val bank = ByteArray(size)

    /** How badly each tile has flooded, remembered for years and fading month by month: buyers are wary of it. */
    val floodMemory = ByteArray(size)

    /** Floodwater standing on each tile after rain, 0 to 255, draining away over days. */
    val flood = ByteArray(size)

    /** The id of the building on each tile, 0 for none. */
    val building = IntArray(size)

    /**
     * The building's type (its ordinal plus one, 0 for none) and variant on each
     * tile, kept in step with the city's buildings. The map is drawn from these,
     * on another thread, so it never has to look at the buildings themselves.
     */
    val buildingType = ShortArray(size)
    val buildingVariant = ByteArray(size)

    /** Homes standing empty for sale. Follows from the buildings. */
    val forSale = BooleanArray(size)

    /** A building going up: 0 when it stands, 1 while the ground's dug, 2 once the frame's up. Follows from the buildings. */
    val site = ByteArray(size)

    /** Pollution, 0 to 255, worked out each month. Not saved; it follows from the buildings. */
    val pollution = ByteArray(size)

    /**
     * Soot and dirt left by pollution, 0 to 255. It follows the pollution slowly,
     * up over months and down over years, and it's what shows on the map.
     */
    val grime = ByteArray(size)

    /** How grimy a tile looks, 0 to 3. The map changes only when this does. */
    fun grimeLevel(i: Int): Int = (grime[i].toInt() and 0xff) / 64

    /** What land is worth, 0 to 255, worked out each month. */
    val landValue = ByteArray(size)

    /** Crime, 0 to 255, worked out each month: in all, and theft, vice and rackets apart. */
    val crime = ByteArray(size)
    val theft = ByteArray(size)
    val vice = ByteArray(size)
    val rackets = ByteArray(size)

    /** How well police and fire stations reach each tile, 0 to 255. */
    val policeCover = ByteArray(size)
    val fireCover = ByteArray(size)

    /**
     * The leisure within reach of each tile, 0 to 255, by kind: parks and
     * gardens, sport, and culture (see [Leisure]). Worked out each month and
     * on loading, so never saved.
     */
    val leisureGreen = ByteArray(size)
    val leisureSport = ByteArray(size)
    val leisureCulture = ByteArray(size)

    /** Cover by ladder companies, which a tall building's fire needs, and by ambulances. */
    val ladderCover = ByteArray(size)
    val ambulanceCover = ByteArray(size)

    /** Days left burning on each tile of a building on fire, 0 for none. The map draws the flames from it. */
    val fire = ByteArray(size)

    /** How full each road tile was last month: 128 is at capacity, 255 twice it or more. */
    val congestion = ByteArray(size)

    /**
     * How long the commute is from each home, 1 to 254 in steps of half a
     * minute, 255 if no job could be reached, 0 if there's no home here.
     */
    val commute = ByteArray(size)

    /** How busy each track tile was last month with trains' passengers and freight: 128 is a train's worth a day, 255 two. */
    val railBusy = ByteArray(size)

    /** Sewage in the water, 0 to 255, from the outfalls. It builds up and clears slowly, like grime. */
    val foul = ByteArray(size)

    /** How foul water looks, 0 to 3. The map changes only when this does. */
    fun foulLevel(i: Int): Int = (foul[i].toInt() and 0xff) / 64

    /** Whether each tile has mains water and is on the sewer, worked out whenever the pipes or buildings change. */
    val watered = BooleanArray(size)
    val sewered = BooleanArray(size)

    /** Whether each tile has power, worked out whenever power lines or buildings change. */
    val powered = BooleanArray(size)

    fun index(x: Int, y: Int) = y * width + x

    fun inside(x: Int, y: Int) = x in 0 until width && y in 0 until height

    fun terrainAt(x: Int, y: Int): Byte = terrain[index(x, y)]
    fun roadAt(x: Int, y: Int): Byte = road[index(x, y)]
    fun zoneAt(x: Int, y: Int): Byte = zone[index(x, y)]
    fun powerAt(x: Int, y: Int): Byte = power[index(x, y)]
    fun buildingAt(x: Int, y: Int): Int = building[index(x, y)]

    /**
     * Everything on a tile that the player or the town can change, packed into
     * one number for undo: terrain, road, zone, power, track, the road's
     * heading, the pipes and the zone's density in the low half; the building, tram track, subway and stops in the high half.
     */
    fun tileState(i: Int): Long =
        (terrain[i].toLong() and 0x0f) or ((road[i].toLong() and 0x0f) shl 4) or ((zone[i].toLong() and 0x07) shl 8) or
            ((power[i].toLong() and 0x03) shl 11) or ((rail[i].toLong() and 0x03) shl 13) or
            ((roadHeading[i].toLong() and 0x0f) shl 15) or ((waterPipe[i].toLong() and 0x07) shl 19) or
            ((sewerPipe[i].toLong() and 0x03) shl 22) or ((stormPipe[i].toLong() and 0x03) shl 24) or
            ((bank[i].toLong() and 0x01) shl 26) or ((density[i].toLong() and 0x03) shl 27) or
            ((brownfield[i].toLong() and 0x01) shl 29) or ((wire[i].toLong() and 0x01) shl 30) or ((streetTrees[i].toLong() and 0x01) shl 31) or ((building[i].toLong() and 0x0fffffff) shl 32) or
            ((tram[i].toLong() and 0x01) shl 60) or ((subway[i].toLong() and 0x01) shl 61) or ((stop[i].toLong() and 0x03) shl 62)

    fun setTileState(i: Int, state: Long) {
        terrain[i] = (state and 0x0f).toByte()
        road[i] = ((state shr 4) and 0x0f).toByte()
        zone[i] = ((state shr 8) and 0x07).toByte()
        power[i] = ((state shr 11) and 0x03).toByte()
        rail[i] = ((state shr 13) and 0x03).toByte()
        roadHeading[i] = ((state shr 15) and 0x0f).toByte()
        waterPipe[i] = ((state shr 19) and 0x07).toByte()
        sewerPipe[i] = ((state shr 22) and 0x03).toByte()
        stormPipe[i] = ((state shr 24) and 0x03).toByte()
        bank[i] = ((state shr 26) and 0x01).toByte()
        density[i] = ((state shr 27) and 0x03).toByte()
        brownfield[i] = ((state shr 29) and 0x01).toByte()
        wire[i] = ((state shr 30) and 0x01).toByte()
        streetTrees[i] = ((state shr 31) and 0x01).toByte()
        building[i] = ((state ushr 32) and 0x0fffffff).toInt()
        tram[i] = ((state ushr 60) and 0x01).toByte()
        subway[i] = ((state ushr 61) and 0x01).toByte()
        stop[i] = ((state ushr 62) and 0x03).toByte()
    }

    /** FNV-1a over every layer. Two maps with the same hash are the same map. */
    fun hash(): Long {
        var h = FNV_OFFSET
        h = mix(h, width.toLong())
        h = mix(h, height.toLong())
        for (layer in arrayOf(terrain, road, roadHeading, zone, density, power, rail, tram, wire, subway, stop, streetTrees, waterPipe, sewerPipe, stormPipe, bank, grime, fire, junction, control, lane, district, bridge, lowRoad, lowHeading, lowRail, portal)) for (b in layer) h = mix(h, b.toLong())
        for (b in building) h = mix(mix(h, b.toLong()), (b ushr 8).toLong())
        return h
    }

    private fun mix(h: Long, v: Long): Long = (h xor (v and 0xff)) * FNV_PRIME

    private companion object {
        const val FNV_OFFSET = -0x340d631b7bdddcdbL
        const val FNV_PRIME = 0x100000001b3L
    }
}

/** In [CityMap.buried]: the power line runs underground. */
const val BURIED_POWER = 1

/** In [CityMap.buried]: the phone line runs in a duct. */
const val BURIED_PHONE = 2

/** What's on [CityMap.phone]. */
object Phone {
    const val NONE: Byte = 0
    const val COPPER: Byte = 1
    const val FIBRE: Byte = 2

    /** What [CityMap.comms] says. */
    const val SERVICE_NONE = 0
    const val SERVICE_PHONE = 1
    const val SERVICE_BROADBAND = 2
    const val SERVICE_FAST = 3
}

/** The way a tunnel runs, in [CityMap.lowHeading]: one way along a [Heading], or both ways along one of these. */
object Tunnel {
    const val NORTH_SOUTH = 5
    const val EAST_WEST = 6

    /** The [CityMap.lowHeading] for a tunnel drawn [run]: its [heading] if one-way, else the way it runs. */
    fun heading(run: Int, heading: Int): Int = when {
        heading != 0 -> heading
        run == Heading.EAST.toInt() || run == Heading.WEST.toInt() -> EAST_WEST
        else -> NORTH_SOUTH
    }

    /** Whether a tunnel running [low] lets traffic go [h]. */
    fun goes(low: Int, h: Int): Boolean {
        val ew = h == Heading.EAST.toInt() || h == Heading.WEST.toInt()
        return when (low) {
            NORTH_SOUTH -> !ew
            EAST_WEST -> ew
            else -> low == h
        }
    }
}
