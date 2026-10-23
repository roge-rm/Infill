package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReachedTest {
    private fun City.months(n: Int) = repeat(n) { repeat(31) { tick() } }

    private fun works(c: City, y0: Int, y1: Int) = c.allBuildings.filter { it.type.zone == Zone.INDUSTRIAL && it.y in y0..y1 && it.underway == 0 }

    @Test
    fun aFreshTownReadsFull() {
        val c = City(1900)
        c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 64, 60, 64, true)))
        c.apply(Action.PlaceZone(10, 65, 50, 67, Zone.INDUSTRIAL))
        for (b in c.allBuildings) assertEquals(100, c.reached(b))
    }

    @Test
    fun worksLinkedToHomesAreStaffedAndOnesApartAreNot() {
        val c = City(1900)
        val m = c.map
        c.apply(Action.BuildRoad(Action.roadPath(m, 0, 64, 100, 64, true)))
        c.apply(Action.PlaceZone(10, 60, 100, 63, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(10, 65, 60, 67, Zone.INDUSTRIAL))
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 105, 66))
        c.apply(Action.BuildPowerLine(Action.roadPath(m, 104, 66, 10, 59, false)))
        // Work on a street of its own, with no road to the homes.
        c.apply(Action.BuildRoad(Action.roadPath(m, 20, 20, 80, 20, true)))
        c.apply(Action.PlaceZone(20, 21, 80, 23, Zone.INDUSTRIAL))
        // Long enough for the works apart to be counted, before they come down.
        c.months(7)
        val linked = works(c, 60, 70)
        val apart = works(c, 18, 30)
        assertTrue(linked.isNotEmpty() && apart.isNotEmpty(), "${linked.size} linked, ${apart.size} apart")
        println("linked ${linked.map { c.reached(it) }}, apart ${apart.map { c.reached(it) }}")
        assertTrue(linked.any { c.reached(it) > 0 })
        for (b in linked) assertTrue(c.reached(b) in 0..100)
        for (b in apart) assertEquals(0, c.reached(b), "at ${b.x}, ${b.y}")
        for (b in apart) assertEquals(0, c.staffShareOf(b))
        // It saves and loads the same.
        val back = SaveGame.read(SaveGame.write(c))
        assertEquals(linked.sortedBy { it.id }.map { c.reached(it) }, works(back, 60, 70).sortedBy { it.id }.map { back.reached(it) })
        for (b in works(back, 18, 30)) assertEquals(0, back.reached(b))
        // And carries on the same through another month.
        c.months(1)
        back.months(1)
        assertEquals(c.allBuildings.sortedBy { it.id }.map { c.reached(it) }, back.allBuildings.sortedBy { it.id }.map { back.reached(it) })
        assertTrue(SaveGame.write(c).contentEquals(SaveGame.write(back)), "saves differ after a month")
    }

    @Test
    fun anOldSaveLoadsFull() {
        val c = SaveGame.read(javaClass.getResourceAsStream("/saves/v30.infill")!!.readBytes())
        for (b in c.allBuildings) assertEquals(100, c.reached(b), "${b.type}")
        c.months(2)
        for (b in c.allBuildings) assertTrue(c.reached(b) in 0..100, "${b.type} ${c.reached(b)}")
    }
}
