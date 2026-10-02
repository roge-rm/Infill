package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RezoningTest {
    /** A street at y 20 with mains and power in 1950, and nothing grown. */
    private fun town(): City {
        val c = City(41, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, 1950)
        City::class.java.getDeclaredField("era").apply { isAccessible = true }.set(c, Era.MOTOR)
        val m = c.map
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 0, 20, 60, 20, true), RoadType.STREET)).ok)
        return c
    }

    private val add = City::class.java.getDeclaredMethod("addBuilding", BuildingType::class.java, Int::class.java, Int::class.java, Int::class.java, Int::class.java)
        .apply { isAccessible = true }

    private fun City.put(t: BuildingType, x: Int, y: Int): Building = add.invoke(this, t, x, y, 0, 0) as Building

    private fun City.call(name: String) = City::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(this)

    private fun City.empty(b: Building) {
        b.people = Household(0, 0, 0, b.people!!.wealth)
    }

    @Test
    fun homesRezonedForShopsStayTillTheyWearOut() {
        val c = town()
        assertTrue(c.apply(Action.PlaceZone(10, 21, 20, 23, Zone.RESIDENTIAL, Density.LOW)).ok)
        val young = c.put(BuildingType.HOUSE, 10, 21)
        val old = c.put(BuildingType.HOUSE, 12, 21)
        val emptied = c.put(BuildingType.HOUSE, 14, 21)
        old.built = c.monthNow - Balance.NONCONFORMING_YEARS * 12 - 1
        // Rezoning over them is allowed now; it used to need bulldozing first.
        assertTrue(c.apply(Action.PlaceZone(10, 21, 20, 23, Zone.COMMERCIAL, Density.LOW)).ok)
        assertFalse(c.conforms(young))
        c.empty(emptied)
        repeat(Balance.NONCONFORMING_ODDS * 4) { c.call("replaceNonconforming") }
        assertEquals(young, c.buildingAt(10, 21), "a young home stays")
        assertNull(c.buildingAt(12, 21), "an old one comes down in time")
        assertNull(c.buildingAt(14, 21), "an empty one comes down rather than sell")
        // Never over what the town runs.
        c.everything = true
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.SCHOOL, 30, 21)).ok)
        assertTrue(c.plan(Action.PlaceZone(30, 21, 31, 22, Zone.COMMERCIAL, Density.LOW)).blocked.isNotEmpty())
    }

    @Test
    fun aDownzonedBuildingStaysButGrowsNoFurther() {
        val c = town()
        assertTrue(c.apply(Action.PlaceZone(10, 21, 20, 23, Zone.RESIDENTIAL, Density.HIGH)).ok)
        val flats = c.put(BuildingType.TENEMENT, 10, 21)
        assertTrue(c.conforms(flats))
        assertTrue(c.apply(Action.PlaceZone(10, 21, 20, 23, Zone.RESIDENTIAL, Density.LOW)).ok)
        assertFalse(c.conforms(flats))
        repeat(200) { c.call("replaceNonconforming") }
        assertEquals(flats, c.buildingAt(10, 21), "it stays till it's old")
    }

    @Test
    fun aFarmsteadOnLandZonedLowBecomesAHouseOnOneLot() {
        val c = town()
        // Before mains and power were expected, so a cottage is wanted here.
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, 1905)
        assertTrue(c.apply(Action.PlaceZone(10, 21, 20, 23, Zone.RESIDENTIAL, Density.RURAL)).ok)
        val farm = c.put(BuildingType.FARMSTEAD, 10, 21)
        assertTrue(c.apply(Action.PlaceZone(10, 21, 20, 23, Zone.RESIDENTIAL, Density.LOW)).ok)
        val choices = City::class.java.getDeclaredMethod("choices", Building::class.java, Int::class.java, Byte::class.java, Int::class.java).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val next = choices.invoke(c, farm, c.map.index(10, 21), Zone.RESIDENTIAL, 100) as List<BuildingType>
        assertEquals(listOf(BuildingType.COTTAGE), next)
        // Growing it: the farmstead's four lots are freed, one gets the cottage.
        repeat(31) { c.tick() }
        farm.age = 10_000
        repeat(400) { if (c.buildingAt(10, 21)?.type == BuildingType.FARMSTEAD) City::class.java.getDeclaredMethod("growOnce", Byte::class.java).apply { isAccessible = true }.invoke(c, Zone.RESIDENTIAL) }
        val m = c.map
        assertTrue((0 until m.size).none { c.building(m.building[it])?.type == BuildingType.FARMSTEAD }, "the farmstead gave way")
        assertTrue((0 until m.size).any { c.building(m.building[it])?.type == BuildingType.COTTAGE })
        // No lot is left pointing at a building that isn't there or doesn't cover it.
        for (y in 21..22) for (x in 10..11) {
            val b = c.building(m.building[m.index(x, y)]) ?: continue
            assertTrue(x in b.x until b.x + b.type.width && y in b.y until b.y + b.type.height, "lot $x,$y")
        }
    }

    @Test
    fun clearingHomesPaysTheOwnersAndForcesPeopleOut() {
        val c = town()
        assertTrue(c.apply(Action.PlaceZone(10, 21, 30, 23, Zone.RESIDENTIAL, Density.LOW)).ok)
        val home = c.put(BuildingType.HOUSE, 10, 21)
        val spare = c.put(BuildingType.LARGE_HOUSE, 20, 21).also { c.empty(it) }
        val people = home.people!!.size
        assertTrue(people > 0)
        val plan = c.plan(Action.Bulldoze(10, 21, 10, 21))
        assertEquals(Prices.DEMOLISH + c.worth(home), plan.cost)
        assertTrue(c.worth(home) > c.worth(spare), "an empty home is worth less")
        val pullBefore = c.attraction(c.map.index(12, 21), Zone.RESIDENTIAL)
        assertTrue(c.apply(Action.Bulldoze(10, 21, 10, 21)).ok)
        // They moved into the empty house down the street, so nobody left town.
        assertFalse(spare.people!!.empty)
        assertEquals(0, c.displaced)
        assertTrue(c.attraction(c.map.index(12, 21), Zone.RESIDENTIAL) < pullBefore, "the neighbours are upset")
        // Undo brings the house back, empty: its people have gone.
        val funds = c.funds
        c.undo()
        assertEquals(funds + plan.cost, c.funds)
        assertTrue(c.buildingAt(10, 21)!!.people!!.empty)
    }

    @Test
    fun withNowhereToGoTheyLeaveTownAndPutSettlersOff() {
        val c = town()
        assertTrue(c.apply(Action.PlaceZone(10, 21, 30, 23, Zone.RESIDENTIAL, Density.LOW)).ok)
        c.put(BuildingType.HOUSE, 10, 21)
        c.put(BuildingType.HOUSE, 11, 21)
        val shop = c.put(BuildingType.GENERAL_STORE, 14, 21)
        val people = c.homes.sumOf { it.people!!.size }
        assertTrue(c.apply(Action.Bulldoze(10, 21, 11, 21)).ok)
        assertEquals(people, c.displaced)
        val cut = City::class.java.getDeclaredMethod("displacedCut").apply { isAccessible = true }.invoke(c) as Int
        assertTrue(cut > 0)
        // It fades.
        repeat(24) { c.call("fadeUpset") }
        assertTrue(c.displaced < people / 4)
        assertEquals(0, c.map.upset[c.map.index(12, 21)].toInt() and 0xff)
        // A shop's jobs go with it.
        c.map.zone[c.map.index(14, 21)] = Zone.COMMERCIAL
        assertTrue(c.apply(Action.Bulldoze(14, 21, 14, 21)).ok)
        assertNull(c.buildingAt(shop.x, shop.y))
    }

    @Test
    fun theUpsetAndTheForcedOutAreSaved() {
        val c = town()
        assertTrue(c.apply(Action.PlaceZone(10, 21, 30, 23, Zone.RESIDENTIAL, Density.LOW)).ok)
        c.put(BuildingType.HOUSE, 10, 21)
        assertTrue(c.apply(Action.Bulldoze(10, 21, 10, 21)).ok)
        val back = SaveGame.read(SaveGame.write(c))
        assertEquals(c.displaced, back.displaced)
        assertEquals(c.map.upset[c.map.index(11, 21)], back.map.upset[back.map.index(11, 21)])
        assertTrue((back.map.upset[back.map.index(11, 21)].toInt() and 0xff) > 0)
    }
}
