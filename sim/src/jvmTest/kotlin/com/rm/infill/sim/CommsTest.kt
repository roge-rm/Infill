package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommsTest {
    /** A town in [year] along a street at y 30, with power: homes north, offices and shops south. */
    private fun town(year: Int, exchange: Boolean = true, trunk: Boolean = true, fibre: Boolean = false, tower: Boolean = false): City {
        val c = City(9, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true; it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        val m = c.map
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 60, 30, true), RoadType.STREET, pipes = true)).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 27, 40, 29, Zone.RESIDENTIAL, Density.MEDIUM)).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 31, 22, 33, Zone.OFFICE, Density.MEDIUM)).ok)
        assertTrue(c.apply(Action.PlaceZone(24, 31, 40, 33, Zone.COMMERCIAL, Density.MEDIUM)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 50, 25)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 51, 26, 5, 26, true))).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 51, 27, 51, 34, true))).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 50, 34, 5, 34, true))).ok)
        if (exchange) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.EXCHANGE, 22, 24)).ok)
        // A trunk line from beside the exchange up to the north edge.
        if (trunk) assertTrue(c.apply(Action.BuildPhoneLine(Action.roadPath(m, 24, 24, 24, 0, false), fibre = fibre)).ok)
        if (tower) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.CELL_TOWER, 45, 28)).ok)
        return c
    }

    private fun City.months(n: Int) = repeat(n) { repeat(31) { tick() } }
    private fun City.service(x: Int, y: Int) = map.comms[map.index(x, y)].toInt()

    @Test
    fun anExchangeJoinedOutOfTownReachesTwiceAsFar() {
        val joined = town(1950)
        val alone = town(1950, trunk = false)
        joined.months(2)
        alone.months(2)
        // Four tiles off: both. Ten tiles off: only the one with a line out of town.
        assertEquals(Phone.SERVICE_PHONE, alone.service(22, 25))
        assertEquals(Phone.SERVICE_PHONE, joined.service(29, 20))
        assertEquals(Phone.SERVICE_NONE, alone.service(29, 20))
        fun covered(c: City) = c.map.comms.count { it >= Phone.SERVICE_PHONE }
        assertTrue(covered(joined) > covered(alone) * 3)
    }

    @Test
    fun aFullExchangeLeavesSomeWithoutAPhone() {
        val c = town(1920)
        c.months(36)
        val exchange = c.buildingAt(22, 24)!!
        assertEquals(Balance.EXCHANGE_LINES * c.staffed(BuildingType.EXCHANGE) / 100, exchange.room)
        assertTrue(exchange.served in 1..exchange.room)
    }

    @Test
    fun fibreOutOfTownBringsBroadband() {
        val copper = town(2000).also { it.months(6) }
        val fibre = town(2000, fibre = true).also { it.months(6) }
        assertEquals(0, copper.stats.withBroadband)
        assertTrue(fibre.stats.withBroadband > 0)
        assertTrue(fibre.service(23, 27) >= Phone.SERVICE_BROADBAND)
        // Fibre can't be laid before its time.
        val early = town(1980, trunk = false)
        early.everything = false
        assertFalse(early.plan(Action.BuildPhoneLine(Action.roadPath(early.map, 24, 24, 24, 0, false), fibre = true)).changes.isNotEmpty())
    }

    @Test
    fun aMastNeedsATrunkLine() {
        val c = town(1990, exchange = false, trunk = false, tower = true)
        c.months(2)
        assertEquals(Phone.SERVICE_NONE, c.service(40, 30))
        assertTrue(c.apply(Action.BuildPhoneLine(Action.roadPath(c.map, 46, 28, 46, 0, false))).ok)
        c.months(1)
        assertEquals(Phone.SERVICE_PHONE, c.service(40, 30))
    }

    @Test
    fun officesWantThePhone() {
        val with = town(1945).also { it.months(48) }
        val without = town(1945, exchange = false, trunk = false).also { it.months(48) }
        // Offices come in bigger and smaller buildings, so what's going up counts as well as what's open.
        fun offices(c: City) = c.stats.officeJobs + c.stats.officeJobsComing
        assertTrue(offices(with) >= offices(without), "with ${offices(with)}, without ${offices(without)}")
        assertTrue(with.stats.population + with.stats.jobs > without.stats.population + without.stats.jobs)
    }

    @Test
    fun broadbandKeepsSomeWorkersHome() {
        val c = town(2010, fibre = true)
        c.months(12)
        assertTrue(c.stats.workingFromHome > 0)
        val none = town(2010, exchange = false, trunk = false).also { it.months(12) }
        assertEquals(0, none.stats.workingFromHome)
    }

    @Test
    fun galesTakeLinesOnPolesButNotInDucts() {
        val c = town(1950, exchange = false, trunk = false)
        c.disasterLevel = 2
        val m = c.map
        assertTrue(c.apply(Action.BuildPhoneLine(Action.roadPath(m, 2, 10, 25, 10, true))).ok)
        assertTrue(c.apply(Action.BuildPhoneLine(Action.roadPath(m, 2, 15, 25, 15, true), buried = true)).ok)
        repeat(30) { c.gale() }
        assertTrue((2..25).any { m.out(m.index(it, 10), Broken.PHONE) })
        assertTrue((2..25).none { m.out(m.index(it, 15), Broken.PHONE) })
    }

    @Test
    fun linesUndoAndAreSaved() {
        val c = town(2000, fibre = true)
        val m = c.map
        assertTrue(c.apply(Action.BuildPhoneLine(Action.roadPath(m, 2, 10, 25, 10, true), buried = true)).ok)
        assertTrue(m.duct(m.index(5, 10)))
        c.undo()
        assertEquals(Phone.NONE, m.phone[m.index(5, 10)])
        c.redo()
        assertTrue(m.duct(m.index(5, 10)))
        c.months(2)
        val back = SaveGame.read(SaveGame.write(c))
        assertTrue(back.map.duct(back.map.index(5, 10)))
        assertTrue(back.map.comms.contentEquals(c.map.comms))
        assertEquals(c.stats.withPhone, back.stats.withPhone)
        // Taking them up.
        assertTrue(c.apply(Action.RemovePhone(2, 10, 25, 10)).ok)
        assertEquals(Phone.NONE, m.phone[m.index(5, 10)])
        // And a town from before the telephone carries on.
        val old = SaveGame.read(javaClass.getResourceAsStream("/saves/v20.infill")!!.readBytes())
        assertEquals("Twentieth", old.name)
        val people = old.stats.population
        repeat(70) { old.tick() }
        assertTrue(old.stats.population > people / 2)
    }

}
