package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LeisureTest {
    private fun town(year: Int, era: Era = Era.of(year)): City {
        val c = City(4, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.disasterLevel = 0 }
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        City::class.java.getDeclaredField("era").apply { isAccessible = true }.set(c, era)
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 30, 63, 30, true), RoadType.STREET)).ok)
        return c
    }

    private fun City.months(n: Int) = repeat(n) { repeat(31) { tick() } }

    @Test
    fun aParkGivesLeisureNearItAndLessFurtherOff() {
        val c = town(1950)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.CITY_PARK, 20, 20)).ok)
        c.months(1)
        val near = c.leisureAt(c.map.index(22, 25))
        val further = c.leisureAt(c.map.index(29, 25))
        val far = c.leisureAt(c.map.index(50, 25))
        assertTrue(near > further && further > far, "near $near, further $further, far $far")
        assertEquals(0, far)
    }

    @Test
    fun whatPeopleWantComesAndGoes() {
        val bandstand = Leisure(culture = 30, reach = 5, fadesFrom = 1930, fadedBy = 1965, fadesTo = 40)
        assertEquals(100, bandstand.fashion(1910))
        assertEquals(40, bandstand.fashion(1970))
        assertTrue(bandstand.fashion(1950) in 41..99)
        val cinema = Leisure(culture = 50, reach = 10, risesFrom = 1915, peak = 1940, fadesFrom = 1960, fadedBy = 1985, fadesTo = 50)
        assertEquals(0, cinema.fashion(1910))
        assertEquals(100, cinema.fashion(1950))
        assertEquals(50, cinema.fashion(1990))
        // What people expect rises with the century.
        assertTrue(town(1900).leisureExpected() < town(1950).leisureExpected())
        assertTrue(town(1950).leisureExpected() < town(2020).leisureExpected())
    }

    @Test
    fun leisureDrawsHomesMoreSoOnDenseLand() {
        fun appeal(park: Boolean, density: Byte): Int {
            val c = town(1990)
            assertTrue(c.apply(Action.PlaceZone(10, 31, 30, 33, Zone.RESIDENTIAL, density)).ok)
            if (park) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.CITY_PARK, 18, 34)).ok)
            c.months(1)
            return c.attraction(c.map.index(20, 32), Zone.RESIDENTIAL)
        }
        val low = appeal(true, Density.LOW) - appeal(false, Density.LOW)
        val high = appeal(true, Density.HIGH) - appeal(false, Density.HIGH)
        assertTrue(low > 0, "a park draws: $low")
        assertTrue(high > low, "more so on dense land: low $low, high $high")
    }

    @Test
    fun plantedWoodlandComesIntoItsOwn() {
        val young = town(1950)
        assertTrue(young.apply(Action.PlaceParks(10, 10, 14, 14, BuildingType.URBAN_WOODLAND)).ok)
        young.months(1)
        val first = young.map.leisureGreen[young.map.index(12, 12)].toInt() and 0xff
        young.months(12 * Balance.WOODLAND_MATURES)
        val grown = young.map.leisureGreen[young.map.index(12, 12)].toInt() and 0xff
        assertTrue(first in 1 until grown, "young $first, grown $grown")
    }

    @Test
    fun aWetlandHoldsTheRain() {
        val c = town(1990)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.WETLAND_RESERVE, 10, 10)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.PLAZA, 20, 10)).ok)
        assertTrue(c.apply(Action.PlaceParks(30, 10, 30, 10)).ok)
        val wetland = Stormwater.hardness(c.map, c.map.index(11, 11))
        val park = Stormwater.hardness(c.map, c.map.index(30, 10))
        val plaza = Stormwater.hardness(c.map, c.map.index(21, 11))
        assertTrue(wetland < park && park < plaza, "wetland $wetland, park $park, plaza $plaza")
    }

    @Test
    fun aGreenwayWaitsForItsYear() {
        val early = town(1950)
        assertTrue(!early.apply(Action.PlaceParks(10, 10, 14, 10, BuildingType.GREENWAY)).ok)
        val later = town(1990)
        assertTrue(later.apply(Action.PlaceParks(10, 10, 14, 10, BuildingType.GREENWAY)).ok)
        assertEquals(BuildingType.GREENWAY, later.buildingAt(12, 10)!!.type)
        // Only what's laid a tile at a time can be.
        assertTrue(!later.apply(Action.PlaceParks(10, 12, 14, 12, BuildingType.CITY_PARK)).ok)
    }

    @Test
    fun theTownSquareBecomesAPlaza() {
        assertEquals(BuildingType.TOWN_SQUARE, town(1950).newest(BuildingType.TOWN_SQUARE))
        assertEquals(BuildingType.PLAZA, town(1970).newest(BuildingType.TOWN_SQUARE))
        assertEquals(BuildingType.COMMUNITY_GARDEN, town(1980).newest(BuildingType.ALLOTMENTS))
    }

    @Test
    fun sportCutsTheft() {
        // The same streets worked out twice, once with plenty of sport about.
        val m = CityMap(16, 16)
        fun theft(sport: Int): Int {
            m.leisureSport.fill(sport.toByte())
            Effects.crime(m, { 20 }, { 10 }, { true }, unemployment = 10, justice = 100)
            return m.theft[m.index(8, 8)].toInt() and 0xff
        }
        val without = theft(0)
        val with = theft(100)
        assertTrue(without > 0, "some theft to cut: $without")
        assertEquals(without * (100 - Balance.LEISURE_THEFT) / 100, with, "with $with, without $without")
    }

    @Test
    fun bigGardensDrawVisitors() {
        fun visitors(garden: Boolean): Int {
            val c = town(1950)
            assertTrue(c.apply(Action.PlaceZone(10, 31, 40, 33, Zone.RESIDENTIAL, Density.MEDIUM)).ok)
            if (garden) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.BOTANICAL_GARDEN, 20, 20)).ok)
            c.months(6)
            return c.stats.visitors
        }
        assertTrue(visitors(true) > visitors(false))
    }

    @Test
    fun leisureIsSavedAndWorkedOutAgainOnLoading() {
        val c = town(1960)
        assertTrue(c.apply(Action.PlaceZone(10, 31, 40, 33, Zone.RESIDENTIAL, Density.MEDIUM)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.CITY_PARK, 22, 34)).ok)
        c.months(12)
        assertTrue(c.stats.leisure > 0)
        val loaded = SaveGame.read(SaveGame.write(c))
        assertEquals(c.stats.leisure, loaded.stats.leisure)
        val i = c.map.index(24, 36)
        assertEquals(c.map.leisureGreen[i], loaded.map.leisureGreen[i])
    }
}
