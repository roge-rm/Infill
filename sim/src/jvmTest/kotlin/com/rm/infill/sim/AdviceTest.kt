package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdviceTest {
    private fun town(year: Int = 1930): City {
        val c = City(61, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        return c
    }

    private fun City.months(n: Int) = repeat(n) { repeat(31) { tick() } }

    private fun City.kinds() = advice.map { it.kind to it.zone }

    @Test
    fun homesWantedAndNowhereToBuildThem() {
        val c = town()
        c.months(2)
        assertTrue(c.stats.residentialDemand >= Balance.ADVICE_DEMAND, "${c.stats.residentialDemand}")
        assertTrue((AdviceKind.ZONE_MORE to Zone.RESIDENTIAL) in c.kinds(), "${c.kinds()}")
    }

    @Test
    fun zonedLandOffTheRoadIsToldOf() {
        val c = town()
        assertTrue(c.apply(Action.PlaceZone(10, 10, 20, 20, Zone.RESIDENTIAL, Density.LOW)).ok)
        c.months(2)
        val a = c.advice.first { it.zone == Zone.RESIDENTIAL }
        assertEquals(AdviceKind.NO_ROAD, a.kind)
        assertTrue(a.x in 10..20 && a.y in 10..20)
    }

    @Test
    fun homesThatCantGrowForWantOfPower() {
        // By 1950 a cottage is no longer enough and the house above it needs power.
        val c = town(1950)
        val m = c.map
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 0, 20, 60, 20, true), RoadType.STREET)).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 21, 55, 21, Zone.RESIDENTIAL, Density.LOW)).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 17, 55, 19, Zone.INDUSTRIAL, Density.LOW)).ok)
        c.months(24)
        val homes = c.kinds().filter { it.second == Zone.RESIDENTIAL }.map { it.first }
        assertEquals(listOf(AdviceKind.NO_POWER), homes, "${c.kinds()} demand ${c.stats.residentialDemand}")
    }

    @Test
    fun inDebt() {
        val c = town()
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, -100L)
        c.updateAdvice()
        assertEquals(AdviceKind.DEBT, c.advice.first().kind)
    }

    @Test
    fun workedOutAgainOnLoading() {
        val c = town()
        c.months(2)
        val back = SaveGame.read(SaveGame.write(c))
        assertEquals(c.kinds(), back.kinds())
    }
}
