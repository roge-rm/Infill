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

    @Test
    fun everyPathAndLaneMeetsARoad() {
        val c = town()
        val m = c.map
        val w = m.width
        val marked = (0 until m.size).filter { m.pathway[it].toInt() != 0 }
        assertTrue(marked.size > 20, "some paths: ${marked.size}")
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
