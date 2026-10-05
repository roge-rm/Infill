package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChallengeTest {
    private fun town(year: Int): City {
        val c = City(7, 64, 64, TerrainOptions(water = 0, trees = 0, river = false))
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        return c
    }

    private fun City.runMonths(months: Int) = repeat(months * 31) { tick() }

    @Test
    fun aChallengeIsWonWhenEveryGoalIsMet() {
        val c = town(2005)
        // Nobody's in debt with $20,000, and approval starts at 60.
        c.challenge = Challenge.INTO_THE_RED
        c.takeEvents { }
        c.runMonths(1)
        val told = ArrayList<EventKind>()
        c.takeEvents { told += it.kind }
        assertEquals(ChallengeResult.WON, c.challengeResult)
        assertTrue(EventKind.ChallengeWon in told)
    }

    @Test
    fun aChallengeIsLostOnceItsYearIsOut() {
        val c = town(Challenge.STREETCAR_SUBURB.until)
        c.challenge = Challenge.STREETCAR_SUBURB
        while (c.year <= Challenge.STREETCAR_SUBURB.until) c.tick()
        c.runMonths(1)
        assertEquals(ChallengeResult.LOST, c.challengeResult)
        // And it stays lost: the town plays on.
        c.runMonths(1)
        assertEquals(ChallengeResult.LOST, c.challengeResult)
    }

    @Test
    fun aYearWithoutFloodsCountsOnlyAfterAWholeYear() {
        val c = town(1925)
        c.challenge = Challenge.THE_RIVER_RISES
        c.challengeStart = c.monthNow
        val goal = Challenge.THE_RIVER_RISES.goals.first { it.measure == ChallengeMeasure.NO_FLOODS }
        assertEquals(false, c.challengeMet(goal))
        c.runMonths(13)
        assertEquals(true, c.challengeMet(goal))
    }

    @Test
    fun theChallengeIsSaved() {
        val c = town(1970)
        c.challenge = Challenge.RENEWAL
        c.challengeStart = 123
        val back = SaveGame.read(SaveGame.write(c))
        assertEquals(Challenge.RENEWAL, back.challenge)
        assertEquals(123, back.challengeStart)
        assertEquals(ChallengeResult.GOING, back.challengeResult)
    }
}
