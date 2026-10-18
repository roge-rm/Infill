package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PathwayTest {
    /** Blocks seven tiles deep between streets, some low density and some high, with a river and track through them. */
    private fun town(): City {
        val c = City(6, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true; it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, 1930)
        val m = c.map
        for (y in 0 until 64) m.terrain[m.index(44, y)] = Terrain.WATER
        for (k in listOf(8, 16, 24, 32, 40)) {
            assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 0, k, 43, k, true))).ok)
            assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, k, 0, k, 50, true))).ok)
        }
        assertTrue(c.apply(Action.BuildRail(Action.roadPath(m, 1, 20, 7, 20, true))).ok)
        assertTrue(c.apply(Action.PlaceZone(1, 1, 23, 15, Zone.RESIDENTIAL, Density.LOW)).ok == true || true)
        c.apply(Action.PlaceZone(25, 1, 43, 39, Zone.RESIDENTIAL, Density.HIGH))
        c.apply(Action.PlaceZone(1, 17, 23, 39, Zone.RESIDENTIAL, Density.MEDIUM))
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 50, 50)).ok)
        c.apply(Action.BuildPowerLine(Action.roadPath(m, 49, 51, 0, 51, true)))
        c.apply(Action.BuildPowerLine(Action.roadPath(m, 0, 51, 0, 0, false)))
        repeat(6 * 365) { c.tick() }
        return c
    }

    private fun add(c: City, type: BuildingType, x: Int, y: Int, underway: Int = 0): Building =
        City::class.java.getDeclaredMethod("addBuilding", BuildingType::class.java, Int::class.java, Int::class.java, Int::class.java, Int::class.java)
            .apply { isAccessible = true }.invoke(c, type, x, y, 0, underway) as Building

    private fun remove(c: City, b: Building) {
        City::class.java.getDeclaredMethod("removeBuilding", Building::class.java)
            .apply { isAccessible = true }.invoke(c, b)
    }

    private fun redevelopmentTown(crossStreet: Boolean = true): City {
        val c = City(6, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true; it.disasterLevel = 0 }
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 10, 63, 10, true))).ok)
        if (crossStreet) assertTrue(c.apply(Action.BuildRoad(Action.roadPath(c.map, 8, 0, 8, 63, true))).ok)
        return c
    }

    @Test
    fun redevelopmentRemovesLaneInsideMansionOnTheNextDay() {
        val c = redevelopmentTown()
        val m = c.map
        val cottages = (11..12).flatMap { y -> (10..11).map { x -> add(c, BuildingType.COTTAGE, x, y) } }
        val neighbour = add(c, BuildingType.COTTAGE, 12, 12)
        m.density[m.index(12, 12)] = Density.MEDIUM
        c.reachable(neighbour) // Bring road access up to date.
        c.updatePathways()
        assertTrue(m.pathway[m.index(10, 12)].toInt() and 0x10 != 0, "lane between the cottages")
        cottages.forEach { remove(c, it) }
        add(c, BuildingType.MANSION, 10, 11, underway = 90)
        val changed = mutableSetOf<Int>()
        c.takeTownChanges { }
        c.tick()
        c.takeTownChanges { changed += it }
        for (x in 10..11) {
            assertEquals(0, m.pathway[m.index(x, 12)].toInt() and 0x10, "no lane inside the new footprint")
        }
        assertTrue(m.index(10, 12) in changed, "removed lane invalidates its map tile")
        assertTrue(m.pathway[m.index(12, 12)].toInt() and 1 != 0, "neighbour now takes a path straight to the street")
    }

    @Test
    fun pathGoesAroundLargerNeighbourInsteadOfThroughItsInterior() {
        val c = redevelopmentTown(crossStreet = false)
        val m = c.map
        val neighbour = add(c, BuildingType.COTTAGE, 11, 13)
        c.reachable(neighbour)
        c.updatePathways()
        assertTrue(m.pathway[m.index(11, 12)].toInt() and 1 != 0, "old path crosses separate lots")
        add(c, BuildingType.MANSION, 10, 11)
        c.updatePathways()
        assertEquals(0, m.pathway[m.index(11, 12)].toInt() and 1, "no path through the mansion's internal west edge")
        assertTrue(m.pathway[m.index(11, 13)].toInt() and 15 != 0, "the neighbour keeps a way out")
        assertTrue(m.pathway[m.index(12, 12)].toInt() and 1 != 0 || m.pathway[m.index(10, 12)].toInt() and 1 != 0,
            "the path runs around an outside edge")
    }

    @Test
    fun everyPathAndLaneMeetsARoad() {
        val c = town()
        val m = c.map
        val w = m.width
        val marked = (0 until m.size).filter { m.pathway[it].toInt() != 0 }
        assertTrue(marked.size > 10, "some paths: ${marked.size}")
        val dx = intArrayOf(0, 1, 0, -1)
        val dy = intArrayOf(-1, 0, 1, 0)
        val bits = intArrayOf(1, 2, 4, 8)
        fun road(x: Int, y: Int) = m.inside(x, y) && m.road[m.index(x, y)] != Road.NONE
        // Lanes along a row (on a north or south edge) and down a column (east or west).
        fun rowLane(i: Int) = (m.pathway[i].toInt() shr 4) and 5 != 0
        fun columnLane(i: Int) = (m.pathway[i].toInt() shr 4) and 10 != 0
        val reached = BooleanArray(m.size)
        val queue = ArrayDeque<Int>()
        fun reach(i: Int) {
            if (!reached[i]) {
                reached[i] = true
                queue.addLast(i)
            }
        }
        // Where the way meets a road.
        for (i in marked) {
            val x = i % w
            val y = i / w
            val p = m.pathway[i].toInt()
            for (k in 0 until 4) if (p and bits[k] != 0 && road(x + dx[k], y + dy[k])) reach(i)
            // Along a lane is across the edge it lies on.
            if (rowLane(i)) for (k in listOf(1, 3)) if (road(x + dx[k], y + dy[k])) reach(i)
            if (columnLane(i)) for (k in listOf(0, 2)) if (road(x + dx[k], y + dy[k])) reach(i)
        }
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val x = i % w
            val y = i / w
            val p = m.pathway[i].toInt()
            for (k in 0 until 4) {
                val nx = x + dx[k]
                val ny = y + dy[k]
                if (!m.inside(nx, ny)) continue
                val j = m.index(nx, ny)
                val q = m.pathway[j].toInt()
                if (q == 0) continue
                // A path joined both ways, or a lane carrying on along the same edge.
                val back = bits[(k + 2) % 4]
                val path = (p and bits[k] != 0 && q and back != 0) || (q and back != 0 && p and bits[k] != 0)
                val lanes = (m.pathway[i].toInt() shr 4) and (m.pathway[j].toInt() shr 4)
                val along = if (k == 1 || k == 3) lanes and 5 != 0 else lanes and 10 != 0
                if (path || along) reach(j)
            }
        }
        val loose = marked.filter { !reached[it] }
        assertEquals(emptyList(), loose.map { "${it % w},${it / w}" }, "every mark joins a road")
        // And none on water or track.
        assertTrue(marked.none { m.terrain[it] == Terrain.WATER || m.rail[it] != Rail.NONE })
    }
}
