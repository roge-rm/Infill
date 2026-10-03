package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServicesTest {
    /** The growth test's town: homes north of a main street, shops and industry south, a power station wired in. */
    private fun town(seed: Long = 7, police: Boolean = false, fire: Boolean = false, parks: Boolean = false): City {
        val c = City(seed, 64, 64, TerrainOptions(water = 0, trees = 0, river = false))
        val m = c.map
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 60, 30, true))).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 28, 40, 29, Zone.RESIDENTIAL)).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 31, 18, 32, Zone.COMMERCIAL)).ok)
        assertTrue(c.apply(Action.PlaceZone(24, 31, 40, 32, Zone.INDUSTRIAL)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 50, 25)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 51, 27, 5, 27, true))).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 51, 27, 51, 33, true))).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 50, 33, 5, 33, true))).ok)
        if (police) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.POLICE_STATION, 20, 25)).ok)
        if (fire) {
            assertTrue(c.apply(Action.PlaceBuilding(BuildingType.FIRE_STATION, 10, 35)).ok)
            assertTrue(c.apply(Action.PlaceBuilding(BuildingType.FIRE_STATION, 30, 35)).ok)
        }
        if (parks) assertTrue(c.apply(Action.PlaceParks(10, 24, 30, 25)).ok)
        return c
    }

    private fun City.run(years: Int) = repeat(years * 365) { tick() }

    @Test
    fun lotsGrowThreeTilesFromARoad() {
        val c = City(7, 64, 64, TerrainOptions(water = 0, trees = 0, river = false))
        val m = c.map
        c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 60, 30, true)))
        // Homes four rows deep north of the road, and work south of it.
        c.apply(Action.PlaceZone(5, 26, 40, 29, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(5, 31, 18, 32, Zone.COMMERCIAL))
        c.apply(Action.PlaceZone(24, 31, 40, 32, Zone.INDUSTRIAL))
        c.run(3)
        fun builtOn(y: Int) = (5..40).count { m.building[m.index(it, y)] != 0 }
        assertTrue(builtOn(27) > 0, "the third row grows")
        assertEquals(0, builtOn(26), "the fourth doesn't")
    }

    @Test
    fun motorFireEnginesDriveToFires() {
        fun coverAt(year: Int, x: Int, avenue: Boolean = false): Int {
            val c = City(1, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true }
            City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
            c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 12, 63, 12, true), if (avenue) RoadType.AVENUE else RoadType.STREET))
            c.apply(Action.PlaceBuilding(BuildingType.FIRE_STATION, 10, 10))
            repeat(32) { c.tick() }
            return c.map.fireCover[c.map.index(x, 11)].toInt() and 0xff
        }
        // A horse-drawn engine reaches round its hall; nothing 16 tiles off.
        assertEquals(0, coverAt(1905, 27))
        // A motor engine drives along the street and gets there.
        assertTrue(coverAt(1925, 27) > 0)
        // A quicker road reaches further.
        assertTrue(coverAt(1925, 34, avenue = true) > coverAt(1925, 34))
        // And off any road it can't reach at all.
        val c = City(1, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true }
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, 1925)
        c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 12, 63, 12, true), RoadType.STREET))
        c.apply(Action.PlaceBuilding(BuildingType.FIRE_STATION, 10, 10))
        repeat(32) { c.tick() }
        assertEquals(0, c.map.fireCover[c.map.index(12, 30)].toInt() and 0xff)
    }

    @Test
    fun aCrowdedSchoolTeachesLessAndShortStaffWeakensIt() {
        val c = town()
        c.everything = true
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, 1930)
        c.apply(Action.PlaceBuilding(BuildingType.SCHOOL, 20, 24))
        c.run(3)
        val school = c.buildingAt(20, 24)!!
        assertTrue(school.room > 0)
        // Short of educated people to teach, it's weaker.
        val shortage = City::class.java.getDeclaredField("skillShortage").apply { isAccessible = true }.get(c) as IntArray
        val full = c.strength(BuildingType.SCHOOL, 100)
        shortage[Education.EDUCATED] = 80
        assertTrue(c.strength(BuildingType.SCHOOL, 100) < full)
        assertTrue(c.staffed(BuildingType.SCHOOL) >= Balance.LEAST_STAFF)
    }

    @Test
    fun policeLowerCrime() {
        val without = town().also { it.run(6) }
        val with = town(police = true).also { it.run(6) }
        assertTrue(with.stats.crime < without.stats.crime, "crime ${with.stats.crime} with police, ${without.stats.crime} without")
    }

    @Test
    fun parksRaiseLandValue() {
        val without = town().also { it.run(3) }
        val with = town(parks = true).also { it.run(3) }
        val i = with.map.index(20, 27)
        assertTrue((with.map.landValue[i].toInt() and 0xff) > (without.map.landValue[i].toInt() and 0xff))
    }

    @Test
    fun waterIsWorthLivingBy() {
        val c = City(1, 32, 32, TerrainOptions(water = 0, trees = 0, river = false))
        for (y in 0 until 32) c.map.terrain[c.map.index(5, y)] = Terrain.WATER
        repeat(32) { c.tick() }
        val near = c.map.landValue[c.map.index(7, 10)].toInt() and 0xff
        val far = c.map.landValue[c.map.index(25, 10)].toInt() and 0xff
        assertTrue(near > far, "$near by the water, $far away from it")
    }

    @Test
    fun fireStationsSaveBuildings() {
        var lostWithout = 0
        var lostWith = 0
        var fires = 0
        for (seed in 1L..6L) {
            val a = town(seed).also { it.run(10) }
            a.takeEvents { if (it.kind == EventKind.BuildingLost) lostWithout++; if (it.kind == EventKind.FireStarted) fires++ }
            val b = town(seed, fire = true).also { it.run(10) }
            b.takeEvents { if (it.kind == EventKind.BuildingLost) lostWith++ }
        }
        assertTrue(fires > 10, "only $fires fires in 60 years of towns")
        assertTrue(lostWith < lostWithout, "$lostWith lost with fire stations, $lostWithout without")
    }

    @Test
    fun fundingSetsReach() {
        val c = town(police = true)
        repeat(40) { c.tick() }
        val far = c.map.index(21 + 12, 25)
        assertTrue((c.map.policeCover[far].toInt() and 0xff) > 0)
        c.policeFunding = 20
        repeat(31) { c.tick() }
        assertEquals(0, c.map.policeCover[far].toInt() and 0xff)
    }

    @Test
    fun theBooksAddUp() {
        val c = town(police = true, fire = true, parks = true).also { it.run(4) }
        val s = c.stats
        assertEquals(s.residentialIncome + s.commercialIncome + s.industrialIncome, s.income)
        assertEquals(s.roadUpkeep + s.powerUpkeep + s.policeUpkeep + s.fireUpkeep + s.parkUpkeep, s.upkeep)
        assertTrue(s.policeUpkeep > 0 && s.fireUpkeep > 0 && s.parkUpkeep > 0)
        val before = s.residentialIncome
        c.residentialTax = 14
        repeat(31) { c.tick() }
        assertTrue(c.stats.residentialIncome > before)
    }

    @Test
    fun historyKeepsEachMonth() {
        val c = town().also { it.run(2) }
        assertEquals(24, c.history.count)
        val pop = c.history.values(Series.Population)
        assertEquals(c.stats.population.toLong(), pop.last())
        assertEquals(1902 to 0, c.history.dateOf(23))
    }

    @Test
    fun parksCanBeUndone() {
        val c = town()
        val hash = c.map.hash()
        val funds = c.funds
        assertTrue(c.apply(Action.PlaceParks(10, 20, 14, 22)).ok)
        c.undo()
        assertEquals(hash, c.map.hash())
        assertEquals(funds, c.funds)
    }

    @Test
    fun parksLetGoAddLessToTheLandAroundThem() {
        fun value(funding: Int): Int {
            val c = City(31, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true; it.disasterLevel = 0 }
            City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
            assertTrue(c.apply(Action.PlaceParks(30, 30, 33, 33)).ok)
            c.parkFunding = funding
            repeat(32) { c.tick() }
            return c.map.landValue[c.map.index(35, 35)].toInt() and 0xff
        }
        assertTrue(value(100) > value(0), "${value(100)} against ${value(0)}")
    }
}
