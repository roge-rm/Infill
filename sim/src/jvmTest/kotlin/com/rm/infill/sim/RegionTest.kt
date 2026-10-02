package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegionTest {
    private val region get() = Region("Lakelands", 77, TerrainOptions(water = 40, trees = 40, river = true))

    @Test
    fun theLandCarriesOnAcrossTheBorders() {
        val r = region
        val west = r.found(3, "Westford", "lakelands")
        val east = r.found(4, "Eastford", "lakelands")
        val side = r.side
        // The column just inside each side of their shared edge comes from neighbouring columns of the one land.
        val all = r.whole()
        for (y in 0 until side) {
            assertEquals(all.terrain[all.index(side - 1, side + y)], west.map.terrain[west.map.index(side - 1, y)])
            assertEquals(all.terrain[all.index(side, side + y)], east.map.terrain[east.map.index(0, y)])
        }
        // And it's not all the same: there's water somewhere and land somewhere.
        assertTrue(all.terrain.any { it == Terrain.WATER } && all.terrain.any { it != Terrain.WATER })
        // The same seed makes the same land.
        assertContentEquals(all.terrain, region.whole().terrain)
    }

    @Test
    fun neighboursSeeEachOthersBorders() {
        val r = region
        val west = r.found(3, "Westford", "lakelands")
        west.everything = true
        // A street out to the east edge on row 40, on dry land.
        val m = west.map
        val y = (10 until r.side - 10).first { yy -> (90 until r.side).all { m.terrain[m.index(it, yy)] != Terrain.WATER } }
        assertTrue(west.apply(Action.BuildRoad(Action.roadPath(m, 90, y, r.side - 1, y, true), RoadType.STREET)).ok)
        r.record(west, "lakelands-3")
        val east = r.found(4, "Eastford", "lakelands")
        val borders = r.bordersOf(east)
        val fromWest = assertNotNull(borders[Border.WEST])
        assertTrue(fromWest.road[y] != Road.NONE)
        assertEquals(Road.NONE, fromWest.road[y + 3])
        // Nothing to the north of the top row, nor yet to the east.
        assertNull(borders[Border.NORTH])
        assertNull(borders[Border.EAST])
    }

    @Test
    fun theRegionAndItsTownsAreSaved() {
        val r = region
        val town = r.found(4, "Middleton", "lakelands")
        repeat(40) { town.tick() }
        r.record(town, "lakelands-4")
        val back = Region.read(r.write())
        assertEquals("Lakelands", back.name)
        assertEquals(77, back.seed)
        val t = assertNotNull(back.towns[4])
        assertEquals("Middleton", t.name)
        assertEquals("lakelands-4", t.file)
        assertContentEquals(r.towns[4]!!.borders[Border.EAST].terrain, t.borders[Border.EAST].terrain)
        assertContentEquals(r.towns[4]!!.picture, t.picture)
        assertTrue(Region.isRegion(r.write()))
        // The town keeps where it belongs.
        val city = SaveGame.read(SaveGame.write(town))
        assertEquals("lakelands", city.region)
        assertEquals(4, city.square)
        // A town on its own belongs nowhere.
        val alone = SaveGame.read(SaveGame.write(City(5, 64, 64)))
        assertNull(alone.region)
        assertEquals(-1, alone.square)
    }

    @Test
    fun aRegionCanBeAnyOfItsGridsAndSizes() {
        for (grid in Region.GRIDS) for (side in listOf(64, 96)) {
            val r = Region("Small", 9, TerrainOptions(), grid, side)
            val last = grid * grid - 1
            val town = r.found(last, "Corner", "small")
            assertEquals(side, town.map.width)
            assertEquals(side * grid, r.whole().width)
            // The far corner's land is the bottom right of the region's.
            val all = r.whole()
            assertEquals(all.terrain[all.index(side * grid - 1, side * grid - 1)], town.map.terrain[town.map.index(side - 1, side - 1)])
            assertEquals(-1, r.neighbour(last, Border.EAST))
            assertEquals(-1, r.neighbour(last, Border.SOUTH))
            assertEquals(last - 1, r.neighbour(last, Border.WEST))
            r.record(town, "small-$last")
            val back = Region.read(r.write())
            assertEquals(grid, back.size)
            assertEquals(side, back.side)
            assertEquals("Corner", back.towns[last]!!.name)
        }
        // The biggest a town can be.
        val big = Region("Big", 4, TerrainOptions(), 2, 256)
        assertEquals(256, big.found(0, "Wide", "big").map.width)
    }

    @Test
    fun anOlderTownLoads() {
        val old = SaveGame.read(javaClass.getResourceAsStream("/saves/v30.infill")!!.readBytes())
        assertEquals("Thirtieth", old.name)
        assertNull(old.region)
        val people = old.stats.population
        assertTrue(people > 0)
        repeat(70) { old.tick() }
        assertTrue(old.stats.population > people / 2)
    }
}
