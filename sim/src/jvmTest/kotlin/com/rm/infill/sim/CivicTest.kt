package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CivicTest {
    /** An empty map in [year] with everything allowed and money to spend, and a street across at y 12. */
    private fun street(year: Int): City {
        val c = City(1, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true }
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 12, 63, 12, true), RoadType.STREET)).ok)
        return c
    }

    /** Homes along a main street, shops and industry, power, and a school unless not. */
    private fun town(year: Int = 1920, school: Boolean = true, highSchool: Boolean = true): City {
        val c = City(7, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        val m = c.map
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 60, 30, true))).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 28, 40, 29, Zone.RESIDENTIAL)).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 31, 18, 32, Zone.COMMERCIAL)).ok)
        assertTrue(c.apply(Action.PlaceZone(24, 31, 40, 32, Zone.INDUSTRIAL)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 50, 25)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 51, 27, 5, 27, true))).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 51, 27, 51, 33, true))).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 50, 33, 5, 33, true))).ok)
        if (school) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.SCHOOL, 10, 35)).ok)
        if (highSchool) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.HIGH_SCHOOL, 20, 35)).ok)
        return c
    }

    private fun City.months(n: Int) = repeat(n) { repeat(31) { tick() } }

    private fun City.homesNow() = (0 until map.size).mapNotNull { building(map.building[it]) }.distinct().mapNotNull { it.people }.filter { it.size > 0 }

    @Test
    fun theTownHallSavesOnTheLawsAndThereIsOnlyOne() {
        val c = town(1950)
        c.needsApply = false
        c.months(12)
        c.setOrdinance(Ordinance.PUBLIC_HEALTH_ACT, true)
        c.setOrdinance(Ordinance.BUILDING_CODE, true)
        val full = c.ordinanceCost()
        assertTrue(full > 0)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.CITY_HALL, 44, 35)).ok)
        c.months(4)
        assertEquals(Balance.HALL_SAVES[1], c.hallSaves())
        assertTrue(c.ordinanceCost() < full, "with a hall ${c.ordinanceCost()}, without $full")
        // A second hall can't go up, of any kind.
        assertEquals(Problem.OnlyOne, c.plan(Action.PlaceBuilding(BuildingType.TOWN_HALL, 44, 40)).problem)
        // It's paid for out of its own budget, and that's saved.
        assertTrue(c.stats.civicUpkeep > 0)
        assertEquals(c.stats.civicUpkeep, SaveGame.read(SaveGame.write(c)).stats.civicUpkeep)
    }

    @Test
    fun aJuniorHighTakesTheYoungerTeens() {
        fun highSchool(junior: Boolean): Int {
            val c = town(1935)
            if (junior) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.JUNIOR_HIGH, 30, 35)).ok)
            c.months(14)
            if (junior) assertTrue(c.buildingAt(30, 35)!!.served > 0, "the junior high took ${c.buildingAt(30, 35)!!.served}")
            return c.buildingAt(20, 35)!!.served
        }
        val with = highSchool(true)
        val without = highSchool(false)
        assertTrue(with < without, "high school with a junior high $with, without $without")
    }

    @Test
    fun aKindergartenGivesTheLittleOnesAStart() {
        fun schooling(kindergarten: Boolean): Int {
            val c = town(1925)
            if (kindergarten) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.KINDERGARTEN, 16, 35)).ok)
            c.months(30)
            val homes = c.homesNow()
            return homes.sumOf { it.schooling } / maxOf(1, homes.size)
        }
        val with = schooling(true)
        val without = schooling(false)
        assertTrue(with > without, "with a kindergarten $with, without $without")
    }

    @Test
    fun aVocationalSchoolGivesThoseWithoutSchoolATrade() {
        fun schooled(trades: Boolean): Int {
            val c = town(1925, school = false, highSchool = false)
            if (trades) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.VOCATIONAL_SCHOOL, 20, 35)).ok)
            c.months(72)
            return c.homesNow().sumOf { it.schooled[Education.SCHOOLED] }
        }
        val with = schooled(true)
        val without = schooled(false)
        assertTrue(with > without, "schooled with a vocational school $with, without $without")
    }

    @Test
    fun theSanatoriumAndThePoliceBoxGoOut() {
        val c = street(1950)
        c.everything = false
        City::class.java.getDeclaredField("era").apply { isAccessible = true }.set(c, Era.RENEWAL)
        assertTrue(c.allows(BuildingType.SANATORIUM))
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, 1960)
        assertFalse(c.allows(BuildingType.SANATORIUM))
        assertTrue(c.allows(BuildingType.POLICE_BOX))
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, 1975)
        assertFalse(c.allows(BuildingType.POLICE_BOX))
        // Those still standing work on, dated.
        assertEquals(100, Lineage.dated(BuildingType.SANATORIUM, 1954))
        assertEquals(Balance.DATE_FLOOR.coerceAtLeast(100 - Balance.DATE_STEP), Lineage.dated(BuildingType.SANATORIUM, 1990))
    }

    @Test
    fun aPublicHealthOfficeHoldsAnEpidemicBack() {
        fun died(office: Boolean): Int {
            val c = town(1925)
            c.disasterLevel = 0
            if (office) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.PUBLIC_HEALTH_OFFICE, 44, 35)).ok)
            c.months(24)
            c.startEpidemic(4, 90)
            var died = 0
            repeat(4) {
                c.months(1)
                died += c.stats.deaths
            }
            return died
        }
        val with = died(true)
        val without = died(false)
        assertTrue(with < without, "died with a health office $with, without $without")
    }

    @Test
    fun fireboatsCoverTheShore() {
        val c = street(1920)
        c.needsApply = false
        for (x in 0 until 64) for (y in 20..22) c.map.terrain[c.map.index(x, y)] = Terrain.WATER
        val inland = c.plan(Action.PlaceBuilding(BuildingType.FIREBOAT_STATION, 10, 4)).problem
        assertEquals(Problem.NeedsWater, inland)
        val shore = c.plan(Action.PlaceBuilding(BuildingType.FIREBOAT_STATION, 10, 18)).problem
        assertEquals(null, shore)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.FIREBOAT_STATION, 10, 18)).ok)
        c.months(1)
        val north = c.map.fireCover[c.map.index(18, 18)].toInt() and 0xff
        val south = c.map.fireCover[c.map.index(18, 24)].toInt() and 0xff
        assertTrue(north > 0 && south > 0, "north $north, south $south")
        // Inland, it's no help.
        assertEquals(0, c.map.fireCover[c.map.index(12, 8)].toInt() and 0xff)
    }

    @Test
    fun trafficPoliceDirectTheCrossings() {
        val c = street(1935)
        c.months(1)
        assertFalse(c.pointDuty)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.TRAFFIC_POLICE, 10, 10)).ok)
        c.months(1)
        assertTrue(c.pointDuty)
    }

    @Test
    fun aUniversityAResearchCampusAndAPostOfficeDrawBusiness() {
        val c = street(2005)
        c.needsApply = false
        assertTrue(c.apply(Action.PlaceZone(30, 13, 33, 14, Zone.OFFICE)).ok)
        assertTrue(c.apply(Action.PlaceZone(36, 13, 38, 14, Zone.COMMERCIAL)).ok)
        c.months(1)
        val office = c.attraction(c.map.index(31, 13), Zone.OFFICE)
        val shop = c.attraction(c.map.index(37, 13), Zone.COMMERCIAL)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.UNIVERSITY, 20, 4)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.RESEARCH_CAMPUS, 40, 4)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.POST_OFFICE, 34, 10)).ok)
        c.months(1)
        assertTrue(c.attraction(c.map.index(31, 13), Zone.OFFICE) >= office + Balance.COLLEGE_OFFICES + Balance.CAMPUS_OFFICES)
        assertTrue(c.attraction(c.map.index(37, 13), Zone.COMMERCIAL) > shop)
    }
}
