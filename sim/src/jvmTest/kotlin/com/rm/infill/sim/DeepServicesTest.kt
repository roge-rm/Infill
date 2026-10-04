package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeepServicesTest {
    /** An empty map in [year] with everything allowed and money to spend, and a street across at y 12. */
    private fun street(year: Int): City {
        val c = City(1, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true }
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 12, 63, 12, true), RoadType.STREET)).ok)
        return c
    }

    private fun City.cover(layer: ByteArray, x: Int, y: Int = 11) = layer[map.index(x, y)].toInt() and 0xff

    /** The services test's town: homes along a main street, shops and industry, power, and a school and high school. */
    private fun town(college: Boolean = false, library: Boolean = false, ambulance: Boolean = false, nursing: Boolean = false, highSchool: Boolean = true): City {
        val c = City(7, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, 1920)
        val m = c.map
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 60, 30, true))).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 28, 40, 29, Zone.RESIDENTIAL)).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 31, 18, 32, Zone.COMMERCIAL)).ok)
        assertTrue(c.apply(Action.PlaceZone(24, 31, 40, 32, Zone.INDUSTRIAL)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 50, 25)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 51, 27, 5, 27, true))).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 51, 27, 51, 33, true))).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 50, 33, 5, 33, true))).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.SCHOOL, 10, 35)).ok)
        if (highSchool) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.HIGH_SCHOOL, 20, 35)).ok)
        if (college) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COLLEGE, 30, 35)).ok)
        if (library) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.LIBRARY, 25, 25)).ok)
        if (ambulance) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.AMBULANCE_STATION, 22, 31)).ok)
        if (nursing) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.NURSING_HOME, 34, 25)).ok)
        return c
    }

    private fun City.run(years: Int) = repeat(years * 365) { tick() }

    @Test
    fun aVolunteerHallCoversLessThanAFireHall() {
        fun reach(type: BuildingType): Int {
            val c = street(1925)
            assertTrue(c.apply(Action.PlaceBuilding(type, 10, 10)).ok)
            repeat(32) { c.tick() }
            return (0 until 64).count { c.cover(c.map.fireCover, it) > 0 }
        }
        val hall = reach(BuildingType.FIRE_STATION)
        val volunteers = reach(BuildingType.VOLUNTEER_HALL)
        assertTrue(volunteers in 1 until hall, "volunteers $volunteers, hall $hall")
    }

    @Test
    fun laddersAndAmbulancesDriveLikeFireEngines() {
        val c = street(1925)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.LADDER_COMPANY, 10, 10)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.AMBULANCE_STATION, 40, 10)).ok)
        repeat(32) { c.tick() }
        assertTrue(c.cover(c.map.ladderCover, 20) > 0)
        assertTrue(c.cover(c.map.ambulanceCover, 50) > 0)
        // A ladder company is not a fire hall.
        assertEquals(0, c.cover(c.map.fireCover, 20))
    }

    @Test
    fun aWornServiceWorksLessUntilRenovated() {
        val c = street(1990)
        // Age alone; what it needs is tested apart.
        c.needsApply = false
        // The kind of its year, so it's worn and not dated.
        val kind = c.newest(BuildingType.FIRE_STATION)
        assertTrue(c.apply(Action.PlaceBuilding(kind, 10, 10)).ok)
        c.tick()
        val b = c.buildingAt(10, 10)!!
        assertEquals(100, c.condition(b))
        // Eighty years old, on a life of fifty.
        b.built = c.monthNow - 80 * 12
        val worn = c.condition(b)
        assertTrue(worn in Balance.WORN_SERVICE until 100, "condition $worn")
        assertTrue(c.renovatable(b))

        val funds = c.funds
        val plan = c.apply(Action.RenewArea(b.x, b.y, b.x, b.y))
        assertTrue(plan.ok)
        assertTrue(plan.cost >= Prices.of(kind) * Balance.RENOVATE_SHARE / 100)
        assertEquals(funds - plan.cost, c.funds)
        assertEquals(100, c.condition(b))
        assertEquals(Balance.RENOVATE_DAYS, b.outage)
        // Shut while the work's done.
        assertTrue(!c.renovatable(b))

        // Undo puts back its age and opens it; redo renovates it again.
        c.undo()
        assertEquals(worn, c.condition(b))
        assertEquals(0, b.outage)
        c.redo()
        assertEquals(Balance.RENOVATE_DAYS, b.outage)
        repeat(Balance.RENOVATE_DAYS + 1) { c.tick() }
        assertEquals(0, b.outage)
        assertEquals(100, c.condition(b))
    }

    @Test
    fun aNewServiceIsNotRenovated() {
        val c = street(1990)
        assertTrue(c.apply(Action.PlaceBuilding(c.newest(BuildingType.POLICE_STATION), 10, 10)).ok)
        c.tick()
        assertTrue(!c.apply(Action.RenewArea(10, 10, 11, 10)).ok)
    }

    /** The homes' average high schooling. */
    private fun City.highSchooling(): Int {
        val homes = (0 until map.size).mapNotNull { building(map.building[it]) }.distinct().mapNotNull { it.people }.filter { it.size > 0 }
        return homes.sumOf { it.highSchooling } / maxOf(1, homes.size)
    }

    @Test
    fun aCollegeTakesStudentsAndALibraryTeaches() {
        // With no high school, a college is where teenagers go on.
        val college = town(college = true, highSchool = false).also { it.run(1) }
        assertTrue((college.buildingAt(30, 35)!!.served) > 0)
        // And a library teaches some of them anyway.
        val plain = town(highSchool = false).also { it.run(3) }
        val library = town(library = true, highSchool = false).also { it.run(3) }
        assertTrue(library.highSchooling() > plain.highSchooling(), "library ${library.highSchooling()}, none ${plain.highSchooling()}")
    }

    @Test
    fun ambulancesAndNursingHomesKeepPeopleAlive() {
        // Health goes up and down a few points year to year, so it's taken on average over ten years.
        fun health(c: City): Int {
            c.run(5)
            var sum = 0
            repeat(120) {
                repeat(30) { c.tick() }
                sum += c.stats.health
            }
            return sum / 120
        }
        val plain = health(town())
        val cared = health(town(ambulance = true, nursing = true))
        assertTrue(cared > plain, "cared $cared, none $plain")
    }

    @Test
    fun theCoverIsSavedWithTheTown() {
        val c = street(1925)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.FIRE_STATION, 10, 10)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.AMBULANCE_STATION, 40, 10)).ok)
        repeat(40) { c.tick() }
        val back = SaveGame.read(SaveGame.write(c))
        assertContentEquals(c.map.fireCover, back.map.fireCover)
        assertContentEquals(c.map.ambulanceCover, back.map.ambulanceCover)
        assertTrue(back.map.fireCover.any { it.toInt() != 0 })
    }

    @Test
    fun aTownFromBeforeTheCoverWasSavedHasItOnLoading() {
        val c = SaveGame.read(javaClass.getResourceAsStream("/saves/v17.infill")!!.readBytes())
        assertEquals("Seventeenth", c.name)
        assertTrue(c.map.fireCover.any { it.toInt() != 0 })
        assertTrue(c.map.policeCover.any { it.toInt() != 0 })
        val people = c.stats.population
        assertTrue(people > 0)
        repeat(70) { c.tick() }
        assertTrue(c.stats.population > people / 2)
    }


}
