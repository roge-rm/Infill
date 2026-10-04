package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LineageTest {
    private fun town(year: Int, era: Era): City {
        val c = City(3, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.disasterLevel = 0 }
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        City::class.java.getDeclaredField("era").apply { isAccessible = true }.set(c, era)
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 12, 63, 12, true), RoadType.STREET)).ok)
        return c
    }

    private val roots = listOf(
        BuildingType.SCHOOL, BuildingType.HIGH_SCHOOL, BuildingType.LIBRARY, BuildingType.CLINIC,
        BuildingType.HOSPITAL, BuildingType.NURSING_HOME, BuildingType.FIRE_STATION, BuildingType.POLICE_STATION,
    )

    @Test
    fun eachLineKeepsItsFootprintAndComesInOrder() {
        for (root in roots) {
            val line = Lineage.lineOf(root)
            assertTrue(line.size >= 2, "$root has a newer kind")
            for (t in line) {
                assertEquals(root, t.root, "$t's root")
                assertEquals(root.width to root.height, t.width to t.height, "$t keeps $root's footprint")
                assertTrue(Prices.of(t) > 0, "$t has a price")
                assertEquals(root.service, t.service, "$t is a service as $root is")
            }
            for ((a, b) in line.zipWithNext()) {
                assertTrue(a.year < b.year, "$a before $b")
                assertEquals(b, Lineage.successor(a))
            }
            assertNull(Lineage.successor(line.last()))
        }
        // Anything else is a line of its own.
        assertEquals(BuildingType.COLLEGE, BuildingType.COLLEGE.root)
        assertEquals(listOf(BuildingType.COLLEGE), Lineage.lineOf(BuildingType.COLLEGE))
    }

    @Test
    fun anOlderKindDatesAsNewerOnesCome() {
        // The elementary school came in 1930, the community school in 2005.
        assertEquals(100, Lineage.dated(BuildingType.SCHOOL, 1929))
        assertEquals(100, Lineage.dated(BuildingType.SCHOOL, 1930))
        assertEquals(90, Lineage.dated(BuildingType.SCHOOL, 1940))
        assertEquals(80, Lineage.dated(BuildingType.SCHOOL, 1950))
        assertEquals(70, Lineage.dated(BuildingType.SCHOOL, 2015))
        assertEquals(60, Lineage.dated(BuildingType.SCHOOL, 2040))
        assertEquals(90, Lineage.dated(BuildingType.ELEMENTARY_SCHOOL, 2015))
        assertEquals(100, Lineage.dated(BuildingType.COMMUNITY_SCHOOL, 2040))
        assertEquals(100, Lineage.dated(BuildingType.COLLEGE, 2040))
    }

    @Test
    fun theTrayHasTheNewestKindTheEraAllows() {
        // The year has come, but the town is still a township: it builds the schoolhouse.
        assertEquals(BuildingType.SCHOOL, town(1935, Era.TOWNSHIP).newest(BuildingType.SCHOOL))
        assertEquals(BuildingType.ELEMENTARY_SCHOOL, town(1935, Era.STREETCAR).newest(BuildingType.SCHOOL))
        assertEquals(BuildingType.FIRE_HALL, town(1980, Era.RENEWAL).newest(BuildingType.FIRE_STATION))
        assertEquals(BuildingType.FIRE_HALL, town(1980, Era.RENEWAL).newest(BuildingType.MOTOR_FIRE_STATION))
    }

    @Test
    fun renovatingBringsAnOlderKindUpToDate() {
        val c = town(1935, Era.STREETCAR)
        c.needsApply = false
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.SCHOOL, 10, 10)).ok)
        repeat(40) { c.tick() }
        val b = c.buildingAt(10, 10)!!
        assertTrue(c.outdated(b))
        assertTrue(c.renovatable(b))
        val id = b.id
        val funds = c.funds
        val plan = c.apply(Action.RenewArea(b.x, b.y, b.x, b.y))
        assertTrue(plan.ok)
        assertEquals(Prices.of(BuildingType.ELEMENTARY_SCHOOL) * Balance.UPGRADE_SHARE / 100, plan.cost)
        assertEquals(funds - plan.cost, c.funds)
        assertEquals(BuildingType.ELEMENTARY_SCHOOL, b.type)
        assertEquals(id, c.buildingAt(10, 10)!!.id)
        assertEquals(BuildingType.ELEMENTARY_SCHOOL.ordinal + 1, c.map.buildingType[c.map.index(11, 11)].toInt())
        assertTrue(!c.outdated(b))

        c.undo()
        assertEquals(BuildingType.SCHOOL, b.type)
        assertEquals(BuildingType.SCHOOL.ordinal + 1, c.map.buildingType[c.map.index(11, 11)].toInt())
        assertEquals(0, b.outage)
        c.redo()
        assertEquals(BuildingType.ELEMENTARY_SCHOOL, b.type)
        assertEquals(Balance.RENOVATE_DAYS, b.outage)
    }

    @Test
    fun aNewerKindTakesInMore() {
        fun room(kind: BuildingType): Int {
            val c = town(1935, Era.STREETCAR)
            c.needsApply = false
            assertTrue(c.apply(Action.PlaceBuilding(kind, 10, 8)).ok)
            c.apply(Action.PlaceZone(0, 13, 63, 20, Zone.RESIDENTIAL, Density.MEDIUM))
            repeat(70) { c.tick() }
            return c.buildingAt(10, 8)!!.room
        }
        val old = room(BuildingType.SCHOOL)
        val new = room(BuildingType.ELEMENTARY_SCHOOL)
        assertTrue(old > 0 && new > old, "schoolhouse $old, elementary $new")
    }

    @Test
    fun theMapHoldsEveryKind() {
        val c = town(2010, Era.INFILL)
        c.everything = true
        val last = BuildingType.entries.last { it.zone == Zone.NONE && it.width == 2 && it.height == 1 }
        assertTrue(last.ordinal > 127, "a kind past what a byte holds: $last")
        assertTrue(c.apply(Action.PlaceBuilding(last, 20, 10)).ok)
        assertEquals(last.ordinal + 1, c.map.buildingType[c.map.index(20, 10)].toInt())
        assertNotEquals(0, c.map.building[c.map.index(21, 10)])
    }
}
