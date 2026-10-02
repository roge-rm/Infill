package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CableTest {
    /** An empty map in [year] with money, a river down x 30 to 31, and track down x 40. */
    private fun city(year: Int = 1960): City {
        val c = City(3, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true; it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        val m = c.map
        for (y in 0 until 64) for (x in 30..31) m.terrain[m.index(x, y)] = Terrain.WATER
        assertTrue(c.apply(Action.BuildRail(Action.roadPath(m, 40, 0, 40, 63, false))).ok)
        return c
    }

    private fun City.across(y: Int, x0: Int, x1: Int, high: Boolean = false, buried: Boolean = false) =
        apply(Action.BuildPowerLine(Action.roadPath(map, x0, y, x1, y, true), high, buried))

    private fun City.call(name: String, vararg args: Any?) =
        City::class.java.declaredMethods.first { it.name == name && it.parameterCount == args.size }.apply { isAccessible = true }.invoke(this, *args)

    @Test
    fun cableGoesUnderRiversWhereLinesCant() {
        val c = city()
        // Poles can't stand in the river, though the wires span the track.
        val poles = c.plan(Action.BuildPowerLine(Action.roadPath(c.map, 25, 10, 45, 10, true)))
        assertTrue(c.map.index(30, 10) in poles.blocked.toList() && c.map.index(40, 10) !in poles.blocked.toList())
        val funds = c.funds
        val plan = c.across(10, 25, 45, buried = true)
        assertTrue(plan.ok)
        // Twenty one tiles, two of them under water at bridge price.
        assertEquals(19 * Prices.CABLE + 2 * Prices.CABLE * Prices.BRIDGE, plan.cost)
        assertEquals(funds - plan.cost, c.funds)
        assertTrue(c.map.cable(c.map.index(30, 10)))
        assertTrue(c.map.cable(c.map.index(40, 10)))
    }

    @Test
    fun highVoltageCableWaitsForItsYear() {
        val early = city(1930).also { it.everything = false }
        assertFalse(early.across(10, 2, 8, high = true, buried = true).ok)
        assertTrue(early.across(10, 2, 8, buried = true).ok)
        val late = city(1960)
        assertTrue(late.across(10, 2, 8, high = true, buried = true).ok)
    }

    @Test
    fun galesBringDownLinesButNotCable() {
        val c = city()
        c.disasterLevel = 2
        assertTrue(c.across(10, 2, 25).ok)
        assertTrue(c.across(20, 2, 25, buried = true).ok)
        repeat(30) { c.gale() }
        val m = c.map
        assertTrue((2..25).any { m.out(m.index(it, 10), Broken.POWER) }, "some line came down")
        assertTrue((2..25).none { m.out(m.index(it, 20), Broken.POWER) }, "no cable did")
    }

    @Test
    fun oldCableFailsAndTakesLongerToMendThanALine() {
        val c = city()
        assertTrue(c.across(20, 2, 25, buried = true).ok)
        val m = c.map
        // Laid sixty years back.
        for (x in 2..25) m.powerLaid[m.index(x, 20)] = (c.monthNow - 60 * 12).toShort()
        var failed = -1
        for (month in 0 until 60) {
            c.call("wearOut")
            failed = (2..25).map { m.index(it, 20) }.firstOrNull { m.out(it, Broken.POWER) } ?: -1
            if (failed >= 0) break
        }
        assertTrue(failed >= 0, "worn cable gave way")
        assertTrue(m.mendingDays(failed) > Balance.MEND_LINE)
        // And renewing relays it new.
        for (x in 2..25) m.broken[m.index(x, 20)] = 0
        val renew = c.apply(Action.RenewArea(2, 20, 25, 20))
        assertTrue(renew.ok)
        assertEquals(c.monthNow, m.powerLaid[m.index(10, 20)].toInt())
    }

    @Test
    fun polesAndPylonsTakeValueOffTheLandAndCableDoesnt() {
        fun valueBy(high: Boolean, buried: Boolean): Int {
            val c = city()
            assertTrue(c.across(10, 2, 25, high, buried).ok)
            c.call("updateEnvironment")
            c.tick()
            repeat(40) { c.tick() }
            return c.map.landValue[c.map.index(12, 11)].toInt() and 0xff
        }
        val cable = valueBy(high = false, buried = true)
        assertTrue(valueBy(high = false, buried = false) < cable)
        assertTrue(valueBy(high = true, buried = false) < valueBy(high = false, buried = false))
    }

    @Test
    fun cableUnderAStreetDigsItUp() {
        val c = city()
        val m = c.map
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 2, 10, 25, 10, true), RoadType.STREET)).ok)
        assertTrue(c.across(10, 2, 25, buried = true).ok)
        assertTrue((m.broken[m.index(5, 10)].toInt() and Broken.WORKS) != 0)
    }

    @Test
    fun undoTakesCableBackToALine() {
        val c = city()
        val m = c.map
        assertTrue(c.across(10, 2, 25).ok)
        assertTrue(c.across(10, 2, 25, buried = true).ok)
        assertTrue(m.cable(m.index(5, 10)))
        c.undo()
        assertFalse(m.cable(m.index(5, 10)))
        assertEquals(Power.LINE, m.power[m.index(5, 10)])
        c.redo()
        assertTrue(m.cable(m.index(5, 10)))
        // And bulldozing clears it all.
        assertTrue(c.apply(Action.Bulldoze(2, 10, 25, 10)).ok)
        assertEquals(0, m.buried[m.index(5, 10)].toInt())
    }

    @Test
    fun cableIsSavedAndOldSavesGetAges() {
        val c = city()
        assertTrue(c.across(10, 25, 45, buried = true).ok)
        repeat(5) { c.tick() }
        val back = SaveGame.read(SaveGame.write(c))
        assertTrue(back.map.cable(back.map.index(30, 10)))
        assertEquals(c.map.powerLaid[c.map.index(30, 10)], back.map.powerLaid[back.map.index(30, 10)])
        // A town from before cable: its lines get an age, and it carries on.
        val old = SaveGame.read(javaClass.getResourceAsStream("/saves/v19.infill")!!.readBytes())
        assertEquals("Nineteenth", old.name)
        val i = (0 until old.map.size).first { old.map.power[it] != Power.NONE }
        assertTrue(old.map.powerLaid[i] > 0)
        val people = old.stats.population
        repeat(70) { old.tick() }
        assertTrue(old.stats.population > people / 2)
    }

    @Test
    fun linesOnPolesSpanTrackStraightAcross() {
        val c = city()
        val m = c.map
        // Track runs north to south down x 40: a line east to west crosses it, one along it doesn't.
        assertTrue(c.across(10, 35, 39).ok)
        assertTrue(c.across(10, 41, 45).ok)
        val over = c.plan(Action.BuildPowerLine(Action.roadPath(m, 38, 10, 42, 10, true)))
        assertTrue(m.index(40, 10) in over.changes.toList(), "the line spans the track")
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 38, 10, 42, 10, true))).ok)
        assertEquals(Power.LINE, m.power[m.index(40, 10)])
        val along = c.plan(Action.BuildPowerLine(Action.roadPath(m, 40, 20, 40, 25, false)))
        assertTrue(along.changes.none { it % m.width == 40 }, "not along the track")
        // A phone line too.
        assertTrue(m.index(40, 12) in c.plan(Action.BuildPhoneLine(Action.roadPath(m, 38, 12, 42, 12, true))).changes.toList())
        // And new track can go under a line straight across, but not turn under it.
        assertTrue(c.across(30, 10, 20).ok)
        assertTrue(m.index(15, 30) in c.plan(Action.BuildRail(Action.roadPath(m, 15, 25, 15, 35, false))).changes.toList())
        assertTrue(m.index(15, 30) !in c.plan(Action.BuildRail(Action.roadPath(m, 15, 25, 18, 30, false))).changes.toList())
    }
}
