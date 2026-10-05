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
    fun aSandboxCostsNothingAndHasEverything() {
        val c = town(1900)
        c.sandbox = true
        val funds = c.funds
        // A nuclear station in 1900, with money the town hasn't got.
        val plan = c.apply(Action.PlaceBuilding(BuildingType.NUCLEAR_PLANT, 10, 10))
        assertTrue(plan.ok, "${plan.problem}")
        assertTrue(plan.cost > funds)
        assertEquals(funds, c.funds)
        c.undo()
        assertEquals(funds, c.funds)
        // A month's upkeep leaves the money where it was, and there's no debt to fall into.
        c.apply(Action.PlaceBuilding(BuildingType.NUCLEAR_PLANT, 10, 10))
        c.runMonths(2)
        assertEquals(funds, c.funds)
        assertTrue(!c.canSellBond(1))
        val back = SaveGame.read(SaveGame.write(c))
        assertTrue(back.sandbox && back.everything)
    }

    @Test
    fun landmarksAreEarnedOnceAndBuiltOnce() {
        val c = town(1975)
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        // Too small a town for a statue: it can't go down.
        assertEquals(Problem.Blocked, c.plan(Action.PlaceBuilding(BuildingType.FOUNDERS_STATUE, 10, 10)).problem)
        // At 1,000 people it's earned, with the news; and the museum too, the town being 75.
        c.stats.population = 1_000
        c.takeEvents { }
        City::class.java.getDeclaredMethod("landmarkMonth").apply { isAccessible = true }.invoke(c)
        val told = ArrayList<CityEvent>()
        c.takeEvents { told += it }
        assertTrue(c.earned(BuildingType.FOUNDERS_STATUE))
        assertTrue(c.earned(BuildingType.TOWN_MUSEUM))
        assertTrue(told.any { it.kind == EventKind.LandmarkEarned && it.type == BuildingType.FOUNDERS_STATUE })
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.FOUNDERS_STATUE, 10, 10)).ok)
        // Only one.
        assertEquals(Problem.OnlyOne, c.plan(Action.PlaceBuilding(BuildingType.FOUNDERS_STATUE, 20, 20)).problem)
        // Earned for good, whatever happens to the town after.
        c.stats.population = 0
        c.runMonths(1)
        assertTrue(c.earned(BuildingType.FOUNDERS_STATUE))
        assertTrue(SaveGame.read(SaveGame.write(c)).earned(BuildingType.FOUNDERS_STATUE))
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
