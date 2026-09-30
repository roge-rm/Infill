package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CityMapTest {
    @Test
    fun hashFollowsTheTerrain() {
        val a = CityMap(16, 16)
        val b = CityMap(16, 16)
        assertEquals(a.hash(), b.hash())
        b.terrain[b.index(3, 4)] = Terrain.WATER
        assertNotEquals(a.hash(), b.hash())
    }

    @Test
    fun hashFollowsTheShape() {
        assertNotEquals(CityMap(16, 8).hash(), CityMap(8, 16).hash())
    }
}
