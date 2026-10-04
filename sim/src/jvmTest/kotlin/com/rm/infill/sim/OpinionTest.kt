package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpinionTest {
    /** Homes north of a main street, shops and works south, a power station wired in, grown for [years]. */
    private fun town(years: Int = 2, year: Int = 1900): City {
        val c = City(7, 64, 64, TerrainOptions(water = 0, trees = 0, river = false))
        c.everything = true
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        val m = c.map
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 60, 30, true))).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 28, 40, 29, Zone.RESIDENTIAL)).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 31, 18, 32, Zone.COMMERCIAL)).ok)
        assertTrue(c.apply(Action.PlaceZone(24, 31, 40, 32, Zone.INDUSTRIAL)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 50, 25)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 51, 27, 5, 27, true))).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 51, 27, 51, 33, true))).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 50, 33, 5, 33, true))).ok)
        c.run(years)
        return c
    }

    private fun City.run(years: Int) = repeat(years * 365) { tick() }
    private fun City.runMonths(months: Int) = repeat(months * 31) { tick() }

    private fun homeTile(c: City): Int = (0 until c.map.size).first { c.moodAt(it) >= 0 }

    @Test
    fun eachEraWeighsItsConcernsToAHundred() {
        for (era in Era.entries) assertEquals(100, Opinion.weights(era).sum(), "$era")
    }

    @Test
    fun highTaxesWearApprovalDownSlowly() {
        val low = town()
        val high = town()
        high.residentialTax = 20; high.commercialTax = 20; high.industrialTax = 20
        val start = high.approval
        high.runMonths(1)
        // It moves a step, not all the way.
        assertTrue(high.approval in start - 15 until start, "${high.approval} from $start")
        high.runMonths(11)
        low.runMonths(12)
        assertTrue(high.approval + 8 < low.approval, "${high.approval} at 20% against ${low.approval} at 7%")
        assertEquals(Concern.TAXES, Opinion.worst(high.era, high.concerns))
    }

    @Test
    fun anAnsweredPetitionPleasesAndALapsedOneDoesnt() {
        val c = town()
        val i = homeTile(c)
        val x = i % c.map.width
        val y = i / c.map.width
        c.petitions.clear()
        c.petitions += Petition(Want.POLICE, x, y, c.monthNow + 12)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.POLICE_STATION, x, y - 4)).ok)
        c.takeEvents { }
        val before = c.approval
        c.runMonths(2)
        val told = ArrayList<EventKind>()
        c.takeEvents { told += it.kind }
        assertTrue(EventKind.PetitionMet in told)
        assertTrue(c.petitions.none { it.x == x && it.y == y })
        assertTrue(c.approval >= before, "${c.approval} from $before")

        c.petitions.clear()
        c.petitions += Petition(Want.SEWER, x, y, c.monthNow)
        c.runMonths(1)
        told.clear()
        c.takeEvents { told += it.kind }
        assertTrue(EventKind.PetitionLapsed in told)
    }

    @Test
    fun aGrantIsPaidWhenItsGoalIsMet() {
        val c = town()
        val field = City::class.java.getDeclaredField("grant").apply { isAccessible = true }
        field.set(c, Grant(GrantKind.HIGHWAYS, 12_345L, c.grantFigure(GrantKind.HIGHWAYS) + 5, c.monthNow + 36))
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 10, 20, 10, true), RoadType.HIGHWAY)).ok)
        c.takeEvents { }
        c.runMonths(1)
        val told = ArrayList<EventKind>()
        c.takeEvents { told += it.kind }
        assertTrue(EventKind.GrantPaid in told)
        assertNull(c.grant)
    }

    @Test
    fun clearingHomesBringsProtestsFromRenewalOn() {
        val c = town(year = 1965)
        City::class.java.getDeclaredField("era").apply { isAccessible = true }.set(c, Era.RENEWAL)
        val homes = (0 until c.map.size).filter { c.moodAt(it) >= 0 && it / c.map.width == 29 }
        assertTrue(homes.size >= Balance.PROTEST_HOMES)
        val x0 = homes.first() % c.map.width
        val x1 = homes.last() % c.map.width
        // Thought poorly of, the protesters stop it.
        c.approval = Balance.PROTEST_BELOW - 5
        assertEquals(Problem.Protest, c.plan(Action.Bulldoze(x0, 29, x1, 29)).problem)
        // Well enough thought of, it goes ahead, but it costs.
        c.approval = 70
        c.takeEvents { }
        assertTrue(c.apply(Action.Bulldoze(x0, 29, x1, 29)).ok)
        assertEquals(70 - Balance.PROTEST_COST, c.approval)
        val told = ArrayList<EventKind>()
        c.takeEvents { told += it.kind }
        assertTrue(EventKind.Protest in told)
    }

    @Test
    fun aLostElectionCapsTaxesForATerm() {
        val c = town(years = 1, year = 1903)
        c.elections = true
        c.residentialTax = 15
        while (c.month != City.ELECTION_MONTH - 1) c.tick()
        // Thought poorly of on the eve of the vote.
        c.approval = 10
        c.runMonths(1)
        assertTrue(c.taxCapUntil > c.monthNow)
        assertEquals(Balance.CAPPED_TAX, c.maxTax())
        assertFalse(c.residentialTax > Balance.CAPPED_TAX)
    }

    @Test
    fun opinionIsSaved() {
        val c = town()
        c.elections = true
        c.petitions += Petition(Want.PARK, 10, 28, c.monthNow + 5)
        val field = City::class.java.getDeclaredField("grant").apply { isAccessible = true }
        field.set(c, Grant(GrantKind.SEWERS, 9_000L, 80, c.monthNow + 20))
        val back = SaveGame.read(SaveGame.write(c))
        assertEquals(c.approval, back.approval)
        assertEquals(c.concerns.toList(), back.concerns.toList())
        assertTrue(back.elections)
        assertEquals(Want.PARK, back.petitions.last().want)
        val g = assertNotNull(back.grant)
        assertEquals(GrantKind.SEWERS, g.kind)
        assertEquals(9_000L, g.amount)
    }
}
