package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoneyTest {
    private fun City.setFunds(v: Long) = City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(this, v)

    /** A small town at work, in [year]. */
    private fun town(year: Int = 1950): City {
        val c = City(5, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.disasterLevel = 0 }
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        City::class.java.getDeclaredField("era").apply { isAccessible = true }.set(c, Era.of(year))
        c.setFunds(1_000_000L)
        val m = c.map
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 63, 30, true), RoadType.STREET, pipes = true)).ok)
        assertTrue(c.apply(Action.PlaceZone(4, 27, 60, 29, Zone.RESIDENTIAL, Density.MEDIUM)).ok)
        assertTrue(c.apply(Action.PlaceZone(4, 31, 30, 33, Zone.COMMERCIAL, Density.MEDIUM)).ok)
        assertTrue(c.apply(Action.PlaceZone(32, 31, 60, 33, Zone.INDUSTRIAL, Density.MEDIUM)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 2, 10)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 3, 12, 3, 30, false))).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 3, 30, 63, 30, true))).ok)
        return c
    }

    private fun City.months(n: Int) = repeat(n) { repeat(31) { tick() } }

    @Test
    fun aBondRaisesMoneyAndIsPaidBackWithInterest() {
        val c = town()
        c.months(12)
        assertTrue(c.stats.income > 0)
        val before = c.funds
        val size = c.bondSize(1)
        assertTrue(c.sellBond(1))
        assertEquals(before + size, c.funds)
        val b = c.bonds.single()
        // Paid back over the term, more than was raised.
        assertTrue(b.payment * Bonds.TERM_YEARS * 12 > b.raised)
        c.months(1)
        assertEquals(b.payment, c.stats.bondCost)
        assertEquals(Bonds.TERM_YEARS * 12 - 1, b.monthsLeft)
        // It's saved.
        val back = SaveGame.read(SaveGame.write(c))
        assertEquals(b.payment, back.bonds.single().payment)
        assertEquals(c.rating, back.rating)
    }

    @Test
    fun ratesFollowTheEraAndTheRating() {
        assertTrue(Bonds.rate(1981, 0) > Bonds.rate(1950, 0))
        assertTrue(Bonds.rate(1950, 3) > Bonds.rate(1950, 0))
        // Twenty years of a thousand at 5%: about $6.60 a month.
        assertEquals(7, Bonds.payment(1000, 500, 240))
    }

    @Test
    fun debtLowersTheRatingAndBringsTheOverseer() {
        val c = town()
        c.months(6)
        val rating = c.rating
        // Kept in debt each month, whatever the month brings in.
        repeat(4) {
            c.setFunds(-1000)
            c.months(1)
        }
        assertTrue(c.rating > rating, "rating $rating to ${c.rating}")
        assertFalse(c.overseen)
        // Past the limit, the overseer has the books: nothing new is built and funding's capped.
        c.setFunds(-c.debtLimit() - 100_000)
        c.months(1)
        assertTrue(c.overseen)
        assertEquals(Problem.Overseen, c.plan(Action.PlaceBuilding(BuildingType.SCHOOL, 40, 10)).problem)
        assertTrue(c.policeFunding <= Balance.OVERSEER_FUNDING)
        assertFalse(c.canSellBond(1))
        assertTrue(c.advice.any { it.kind == AdviceKind.OVERSEEN })
        // Out of debt, it leaves.
        c.setFunds(10_000)
        c.months(1)
        assertFalse(c.overseen)
    }

    @Test
    fun exportsPayBusinessRates() {
        val c = town()
        c.months(18)
        if (c.stats.goodsExported.sum() > 0) assertTrue(c.stats.tradeIncome > 0)
        assertEquals((c.stats.goodsExported.sum() * Balance.TRADE_RATE).toLong(), c.stats.tradeIncome)
    }
}
