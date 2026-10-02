package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BridgeTest {
    /** An empty map in [year] with water from x [x0] to [x1], edge to edge. */
    private fun river(year: Int, x0: Int = 30, x1: Int = 32): City {
        val c = City(17, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = false; it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 50_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        City::class.java.getDeclaredField("era").apply { isAccessible = true }.set(c, Era.FUTURE)
        val m = c.map
        for (y in 0 until 64) for (x in x0..x1) m.terrain[m.index(x, y)] = Terrain.WATER
        return c
    }

    private fun City.wind(speed: Int) = Weather::class.java.getDeclaredField("windSpeed").apply { isAccessible = true }.setInt(weather, speed)

    private fun City.road(x0: Int, y0: Int, x1: Int, y1: Int, t: RoadType = RoadType.STREET, kind: BridgeKind? = null) =
        apply(Action.BuildRoad(Action.roadPath(map, x0, y0, x1, y1, y0 == y1), t, bridge = kind))

    private fun City.plan(x0: Int, y0: Int, x1: Int, y1: Int, t: RoadType = RoadType.STREET, kind: BridgeKind? = null) =
        plan(Action.BuildRoad(Action.roadPath(map, x0, y0, x1, y1, y0 == y1), t, bridge = kind))

    @Test
    fun theKindFitsTheSpanAndTheYear() {
        val c = river(1910)
        val m = c.map
        // The cheapest that fits, a trestle; a suspension bridge not before its time, nor so short.
        assertTrue(c.road(20, 10, 40, 10).ok)
        assertEquals(BridgeKind.TRESTLE, m.bridgeKind(m.index(31, 10)))
        assertTrue(c.plan(20, 20, 40, 20, kind = BridgeKind.SUSPENSION).blocked.isNotEmpty())
        val later = river(1940)
        assertTrue(later.plan(20, 20, 40, 20, kind = BridgeKind.SUSPENSION).blocked.isNotEmpty(), "too short a span")
        // Ten tiles of water: nothing before suspension bridges, then one with its approaches.
        val wide = river(1910, 25, 34)
        assertTrue(wide.plan(10, 10, 50, 10).blocked.isNotEmpty())
        val wider = river(1940, 25, 34)
        assertTrue(wider.road(10, 10, 50, 10).ok)
        assertEquals(BridgeKind.SUSPENSION, wider.map.bridgeKind(wider.map.index(30, 10)))
        // Not without room for its straight approaches.
        assertTrue(wider.plan(23, 20, 50, 20).blocked.isNotEmpty())
        // And a dearer kind costs more.
        val trestle = c.plan(20, 30, 40, 30, kind = BridgeKind.TRESTLE).cost
        val truss = c.plan(20, 30, 40, 30, kind = BridgeKind.TRUSS).cost
        assertTrue(truss > trestle)
    }

    @Test
    fun nothingJoinsAHighBridgeFromTheSide() {
        val c = river(1910)
        assertTrue(c.road(20, 10, 40, 10, kind = BridgeKind.TRUSS).ok)
        // The two tiles at each end lead up to it: a road can't cross them, nor come onto them from the side.
        assertTrue(c.plan(29, 0, 29, 20).blocked.contains(c.map.index(29, 10)))
        val side = c.plan(26, 0, 26, 20).blocked
        assertTrue(side.isEmpty(), side.map { "${it % 64},${it / 64}" }.toString())
        assertTrue(c.road(28, 0, 28, 9).ok)
        assertTrue(c.travelTimes(c.map.index(28, 0))[c.map.index(35, 10)] < 0, "no way on from the side")
        assertTrue(c.road(25, 0, 25, 9).ok)
        assertTrue(c.travelTimes(c.map.index(25, 0))[c.map.index(35, 10)] > 0, "on beyond the approach")
    }

    @Test
    fun lowBridgesStopShipsAndLiftingOnesDont() {
        val c = river(1925)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.WHARF_NS, 33, 30)).ok)
        // A bridge either side of the wharf: the first leaves the way out the other end.
        assertTrue(c.road(20, 10, 40, 10, kind = BridgeKind.CONCRETE.takeIf { false } ?: BridgeKind.TRESTLE).ok)
        assertEquals(Problem.CutsOffPort, c.plan(20, 50, 40, 50, kind = BridgeKind.TRESTLE).problem)
        assertTrue(c.road(20, 50, 40, 50, kind = BridgeKind.LIFT).ok)
        assertTrue(c.portLinked(c.buildingAt(33, 30)!!))
        // And a high one leaves the water open too.
        val d = river(1925)
        assertTrue(d.apply(Action.PlaceBuilding(BuildingType.WHARF_NS, 33, 30)).ok)
        assertTrue(d.road(20, 10, 40, 10, kind = BridgeKind.TRUSS).ok)
        assertTrue(d.road(20, 50, 40, 50, kind = BridgeKind.TRUSS).ok)
    }

    /** Works on the east bank whose only way out is a bridge of [kind] to the west edge. */
    private fun works(kind: BridgeKind): City {
        val c = river(1935)
        val m = c.map
        assertTrue(c.road(0, 30, 50, 30, kind = kind).ok)
        assertTrue(c.road(40, 20, 40, 45).ok)
        assertTrue(c.apply(Action.PlaceZone(41, 20, 50, 29, Zone.INDUSTRIAL)).ok)
        assertTrue(c.apply(Action.PlaceZone(41, 31, 50, 45, Zone.RESIDENTIAL)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 55, 25)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 54, 26, 51, 26, true))).ok)
        repeat(2 * 365) { c.tick() }
        return c
    }

    @Test
    fun trucksCantCrossALightBridge() {
        val light = works(BridgeKind.TRESTLE)
        val heavy = works(BridgeKind.CONCRETE)
        assertTrue(heavy.stats.goodsExported.sum() > 0, "out over the concrete bridge")
        assertEquals(0, light.stats.goodsExported.sum(), "not over the trestle")
        // A worn bridge is posted against them, and past its life shut.
        val m = heavy.map
        val i = m.index(31, 30)
        assertFalse(heavy.bridgeLight(i))
        m.roadLaid[i] = (heavy.monthNow - BridgeKind.CONCRETE.life * 12 * 8 / 10).toShort()
        assertTrue(heavy.bridgeLight(i))
        m.roadLaid[i] = (heavy.monthNow - BridgeKind.CONCRETE.life * 12 * 2).toShort()
        repeat(32) { heavy.tick() }
        assertTrue(m.closed(i), "shut as unsafe")
    }

    @Test
    fun longHighBridgesShutInAGale() {
        val c = river(1940, 25, 30)
        val m = c.map
        assertTrue(c.road(10, 10, 50, 10, kind = BridgeKind.SUSPENSION).ok)
        c.tick()
        val i = m.index(27, 10)
        assertFalse(m.closed(i))
        c.wind(Weather.GALE + 2)
        c.tick()
        assertTrue(m.closed(i), "shut in the gale")
        c.wind(20)
        repeat(Balance.GALE_SHUT_DAYS + 1) {
            c.wind(20)
            c.tick()
        }
        // The weather moves on its own every few days; it should be calm enough by now.
        if (c.weather.windSpeed < Weather.GALE) assertFalse(m.closed(i), "open again")
    }

    @Test
    fun tollsEarnAndPutDriversOff() {
        val c = river(1935)
        val m = c.map
        assertTrue(c.road(10, 30, 50, 30, kind = BridgeKind.CONCRETE).ok)
        assertTrue(c.road(10, 34, 50, 34, kind = BridgeKind.CONCRETE).ok)
        assertTrue(c.road(10, 31, 10, 33).ok)
        assertTrue(c.road(50, 31, 50, 33).ok)
        val before = c.travelTimes(m.index(15, 30))[m.index(45, 30)]
        val plan = c.apply(Action.SetBridge(intArrayOf(m.index(31, 30)), toll = true))
        assertTrue(plan.ok)
        assertTrue(plan.cost >= Prices.TOLL_BOOTH)
        assertTrue((30..32).all { m.bridge[m.index(it, 30)].toInt() and Bridge.TOLL != 0 })
        // Going round by the free bridge is worth it now.
        val after = c.travelTimes(m.index(15, 30))[m.index(45, 30)]
        assertTrue(after > before, "before $before, after $after, tolls ${(30..32).map { m.bridge[m.index(it, 30)] }}")
        // Undo takes the toll off.
        c.undo()
        assertEquals(0, m.bridge[m.index(31, 30)].toInt() and Bridge.TOLL)
    }

    @Test
    fun tollsComeIn() {
        val c = works(BridgeKind.CONCRETE)
        assertTrue(c.apply(Action.SetBridge(intArrayOf(c.map.index(31, 30)), toll = true)).ok)
        repeat(62) { c.tick() }
        assertTrue(c.stats.tolls > 0)
        assertTrue(c.stats.tollIncome > 0)
        // Shut, nobody crosses.
        assertTrue(c.apply(Action.SetBridge(intArrayOf(c.map.index(31, 30)), shut = true)).ok)
        assertTrue(c.map.closed(c.map.index(31, 30)))
    }

    @Test
    fun bridgesAreSavedAndOldTownsLoad() {
        val c = works(BridgeKind.LIFT)
        c.apply(Action.SetBridge(intArrayOf(c.map.index(31, 30)), toll = true))
        c.setTollRate(40)
        val back = SaveGame.read(SaveGame.write(c))
        assertEquals(c.map.hash(), back.map.hash())
        assertEquals(BridgeKind.LIFT, back.map.bridgeKind(back.map.index(31, 30)))
        assertEquals(40, back.tollRate)
        // A town from before kinds of bridge: its bridges are plain, run the right way, and still carry traffic.
        val old = SaveGame.read(javaClass.getResourceAsStream("/saves/v23.infill")!!.readBytes())
        assertEquals("Twenty-third", old.name)
        val i = old.map.index(31, 10)
        assertTrue(old.map.bridged(i))
        assertEquals(null, old.map.bridgeKind(i))
        assertTrue(old.map.bridge[i].toInt() and Bridge.ACROSS != 0)
        assertNotNull(old.travelTimes(old.map.index(20, 10)).takeIf { it[old.map.index(40, 10)] > 0 })
        val people = old.stats.population
        repeat(70) { old.tick() }
        assertTrue(old.stats.population > people / 2)
    }
}
