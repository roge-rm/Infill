package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MixedUseTest {
    /** Blocks between streets with power, mains and sewers, in [year] and [era]. */
    private fun town(year: Int, era: Era, seed: Long = 43): City {
        val c = City(seed, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 50_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        City::class.java.getDeclaredField("era").apply { isAccessible = true }.set(c, era)
        val m = c.map
        for (y in 0 until 64) for (x in 60..63) m.terrain[m.index(x, y)] = Terrain.WATER
        fun road(x0: Int, y0: Int, x1: Int, y1: Int) = c.apply(Action.BuildRoad(Action.roadPath(m, x0, y0, x1, y1, y0 == y1), RoadType.STREET, pipes = true))
        for (y in listOf(10, 20, 30, 40, 50)) road(0, y, 56, y)
        for (x in listOf(10, 20, 30, 40, 50)) road(x, 10, x, 50)
        for (py in listOf(12, 22, 32, 42)) {
            c.apply(Action.PlaceBuilding(BuildingType.PUMPING_STATION, 58, py))
            c.apply(Action.BuildPipe(Action.roadPath(m, 57, py, 56, py, true), Pipe.WATER))
        }
        c.apply(Action.PlaceBuilding(BuildingType.OUTFALL, 59, 48))
        c.apply(Action.BuildPipe(Action.roadPath(m, 58, 48, 56, 48, true), Pipe.SEWER))
        c.everything = true
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 2, 55))
        c.apply(Action.BuildPowerLine(Action.roadPath(m, 5, 54, 5, 9, false)))
        c.apply(Action.BuildPowerLine(Action.roadPath(m, 5, 9, 55, 9, true)))
        for (x in listOf(15, 25, 35, 45)) c.apply(Action.BuildPowerLine(Action.roadPath(m, x, 9, x, 51, false)))
        c.everything = false
        return c
    }

    private val add = City::class.java.getDeclaredMethod("addBuilding", BuildingType::class.java, Int::class.java, Int::class.java, Int::class.java, Int::class.java)
        .apply { isAccessible = true }

    @Test
    fun notBeforeTheStreetcarAge() {
        val early = town(1905, Era.TOWNSHIP)
        assertTrue(early.plan(Action.PlaceZone(11, 11, 19, 19, Zone.MIXED, Density.MEDIUM)).blocked.isNotEmpty())
        val c = town(1915, Era.STREETCAR)
        assertTrue(c.plan(Action.PlaceZone(11, 11, 19, 19, Zone.MIXED, Density.MEDIUM)).ok)
        // Never rural.
        assertTrue(c.plan(Action.PlaceZone(11, 11, 19, 19, Zone.MIXED, Density.RURAL)).blocked.isNotEmpty())
    }

    @Test
    fun aMixedBuildingHasAHouseholdAndJobsAndIsCountedAsBoth() {
        val c = town(1950, Era.MOTOR)
        assertTrue(c.apply(Action.PlaceZone(11, 11, 19, 19, Zone.MIXED, Density.HIGH)).ok)
        c.tick()
        val before = c.stats.shopJobs
        val b = add.invoke(c, BuildingType.MIXED_BLOCK, 12, 11, 0, 0) as Building
        assertEquals(BuildingType.MIXED_BLOCK.capacity, b.people!!.size)
        val people = c.stats.population
        City::class.java.getDeclaredMethod("census").apply { isAccessible = true }.invoke(c)
        assertEquals(BuildingType.MIXED_BLOCK.jobs, c.stats.shopJobs - before)
        assertTrue(c.stats.population >= people)
        assertTrue(b in c.homes)
        // Clearing it costs the homes and the shops.
        assertTrue(c.worth(b) > 0)
    }

    @Test
    fun mixedLotsGrowWhenHomesAndShopsAreWanted() {
        val c = town(1925, Era.STREETCAR)
        c.apply(Action.PlaceZone(11, 11, 49, 19, Zone.RESIDENTIAL, Density.LOW))
        c.apply(Action.PlaceZone(11, 21, 29, 29, Zone.COMMERCIAL, Density.LOW))
        c.apply(Action.PlaceZone(31, 21, 49, 29, Zone.INDUSTRIAL, Density.MEDIUM))
        c.apply(Action.PlaceZone(11, 31, 49, 39, Zone.MIXED, Density.MEDIUM))
        repeat(36) { repeat(31) { c.tick() } }
        val m = c.map
        val all = (0 until m.size).mapNotNull { c.building(m.building[it]) }.distinctBy { it.id }
        val mixed = all.filter { it.type.zone == Zone.MIXED && it.underway == 0 }
        assertTrue(mixed.size >= 5, "${mixed.size} mixed: ${all.groupingBy { it.type }.eachCount()}")
        assertTrue(mixed.all { it.people != null })
        assertTrue(mixed.sumOf { it.people!!.size } > 0)
        // Homes and shops still grow on their own lots too.
        assertTrue(all.any { it.type.zone == Zone.RESIDENTIAL } && all.any { it.type.zone == Zone.COMMERCIAL })
    }

    @Test
    fun nearAStopFrom2000() {
        val c = town(2005, Era.INFILL)
        assertTrue(c.apply(Action.PlaceZone(11, 11, 19, 19, Zone.MIXED, Density.HIGH)).ok)
        val i = c.map.index(12, 11)
        val far = c.attraction(i, Zone.MIXED)
        c.everything = true
        assertTrue(c.apply(Action.PlaceStop(12, 10, Stop.BUS)).ok, "stop")
        assertTrue(c.attraction(i, Zone.MIXED) > far)
        assertFalse(c.attraction(i, Zone.MIXED) > maxOf(c.attraction(i, Zone.RESIDENTIAL), c.attraction(i, Zone.COMMERCIAL)) + Balance.MIXED_TRANSIT_APPEAL)
    }

    @Test
    fun savedAndOlderTownsLoad() {
        val c = town(1950, Era.MOTOR)
        assertTrue(c.apply(Action.PlaceZone(11, 11, 19, 19, Zone.MIXED, Density.HIGH)).ok)
        add.invoke(c, BuildingType.MAIN_STREET_FLATS, 12, 11, 0, 0)
        val back = SaveGame.read(SaveGame.write(c))
        assertEquals(Zone.MIXED, back.map.zone[back.map.index(13, 15)])
        assertEquals(BuildingType.MAIN_STREET_FLATS, back.buildingAt(12, 11)!!.type)
        assertTrue(back.buildingAt(12, 11)!!.people!!.size > 0)
        val old = SaveGame.read(javaClass.getResourceAsStream("/saves/v29.infill")!!.readBytes())
        assertEquals("Twenty-ninth", old.name)
        val people = old.stats.population
        repeat(70) { old.tick() }
        assertTrue(old.stats.population >= people / 2)
    }
}
