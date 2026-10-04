package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChronicleTest {
    private fun town(): City {
        val c = City(9, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        val m = c.map
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 63, 30, true), RoadType.STREET)).ok)
        assertTrue(c.apply(Action.PlaceZone(4, 27, 60, 29, Zone.RESIDENTIAL)).ok)
        assertTrue(c.apply(Action.PlaceZone(4, 31, 30, 33, Zone.COMMERCIAL)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 2, 10)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 3, 12, 3, 30, false))).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 3, 30, 63, 30, true))).ok)
        return c
    }

    @Test
    fun theFirstOfAKindIsNewsOnce() {
        val c = town()
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.SCHOOL, 10, 35)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.SCHOOL, 20, 35)).ok)
        val schools = c.chronicle.count { it.event.kind == EventKind.FirstBuilt && it.event.type == BuildingType.SCHOOL }
        assertEquals(1, schools)
        assertTrue(c.chronicle.any { it.event.kind == EventKind.FirstBuilt && it.event.type == BuildingType.COAL_PLANT })
        // Taking the news doesn't take it out of the chronicle.
        c.takeEvents { }
        assertEquals(1, c.chronicle.count { it.event.kind == EventKind.FirstBuilt && it.event.type == BuildingType.SCHOOL })
    }

    @Test
    fun itsStoryAndItsYearsAreSaved() {
        val c = town()
        repeat(3 * 365) { c.tick() }
        assertTrue(c.history.yearsKept >= 2)
        val back = SaveGame.read(SaveGame.write(c))
        assertEquals(c.chronicle.size, back.chronicle.size)
        assertEquals(c.chronicle.last().event.kind, back.chronicle.last().event.kind)
        assertEquals(c.history.yearsKept, back.history.yearsKept)
        assertTrue(c.history.yearValues(Series.Population).contentEquals(back.history.yearValues(Series.Population)))
        // Building on what's loaded, a kind it had already isn't news again.
        assertTrue(back.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 40, 10)).ok)
        assertEquals(1, back.chronicle.count { it.event.kind == EventKind.FirstBuilt && it.event.type == BuildingType.COAL_PLANT })
    }

    @Test
    fun aSnapshotShowsTheTown() {
        val c = town()
        val tiles = Snapshot.of(c.map, c.allBuildings.associateBy { it.id })
        assertEquals(Snapshot.ROAD, tiles[c.map.index(10, 30)])
        assertEquals(Snapshot.CIVIC, tiles[c.map.index(2, 10)])
    }
}
