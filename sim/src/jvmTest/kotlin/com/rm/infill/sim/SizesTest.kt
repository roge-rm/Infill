package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SizesTest {
    private fun City.all(): List<Building> = (0 until map.size).mapNotNull { building(map.building[it]) }.distinctBy { it.id }

    private fun town(): City {
        val c = City(5, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 30, 63, 30, true)))
        return c
    }

    @Test
    fun aRuralStripOneLotDeepGrowsSmallHomes() {
        val c = town()
        // One row of rural lots along the road: no room for a farmstead's 2 by 2.
        assertTrue(c.apply(Action.PlaceZone(2, 29, 60, 29, Zone.RESIDENTIAL, Density.RURAL)).ok)
        repeat(24) { repeat(31) { c.tick() } }
        val homes = c.all().filter { it.type.zone == Zone.RESIDENTIAL }
        assertTrue(homes.isNotEmpty(), "nothing grew on the strip")
        assertTrue(homes.all { it.type.height == 1 && it.type.density == Density.RURAL }, homes.map { it.type }.toString())
    }

    @Test
    fun farmlandGrowsFarmsOfEverySize() {
        val c = town()
        // Farmland needs nothing to grow, and has all three sizes from the start: a market garden, an orchard and a farm.
        assertTrue(c.apply(Action.PlaceZone(2, 24, 60, 29, Zone.RESIDENTIAL, Density.LOW)).ok)
        assertTrue(c.apply(Action.PlaceZone(2, 31, 60, 36, Zone.FARMLAND)).ok)
        repeat(6 * 12) { repeat(31) { c.tick() } }
        val sizes = c.all().filter { it.type.zone == Zone.FARMLAND }.map { it.type.width * it.type.height }.toSet()
        assertTrue(1 in sizes && 2 in sizes && 4 in sizes, "sizes grown: $sizes")
    }

    @Test
    fun aSmallScaleBuildingOnlyTakesInLesserNeighbours() {
        val c = town()
        assertTrue(c.apply(Action.PlaceZone(10, 28, 11, 29, Zone.RESIDENTIAL, Density.LOW)).ok)
        val add = City::class.java.getDeclaredMethod("addBuilding", BuildingType::class.java, Int::class.java, Int::class.java, Int::class.java, Int::class.java)
            .apply { isAccessible = true }
        val at = City::class.java.getDeclaredMethod("assemblyAt", BuildingType::class.java, Int::class.java, Building::class.java).apply { isAccessible = true }
        // A cottage on one lot of the four: a mansion, two rungs up, can take it in.
        add.invoke(c, BuildingType.COTTAGE, 10, 28, 0, 0)
        assertTrue((at.invoke(c, BuildingType.MANSION, c.map.index(11, 29), null) as Int) >= 0)
        // A large house as good as a mansion: it stays, and the mansion waits.
        add.invoke(c, BuildingType.LARGE_HOUSE, 11, 28, 0, 0)
        assertEquals(-1, at.invoke(c, BuildingType.MANSION, c.map.index(11, 29), null))
    }
}
