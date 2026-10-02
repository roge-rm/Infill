package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZoneTierTest {
    /** Blocks between streets with power, mains and sewers, a ladder company, in [year] and [era]. */
    private fun town(year: Int, era: Era = Era.FUTURE, seed: Long = 31): City {
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
        c.apply(Action.PlaceBuilding(BuildingType.NUCLEAR_PLANT, 2, 55))
        c.apply(Action.BuildPowerLine(Action.roadPath(m, 5, 54, 5, 10, false)))
        c.apply(Action.PlaceBuilding(BuildingType.LADDER_COMPANY, 31, 31))
        c.everything = false
        return c
    }

    private fun City.all(): List<Building> = (0 until map.size).mapNotNull { building(map.building[it]) }.distinctBy { it.id }

    @Test
    fun ruralLotsGrowRuralBuildingsAndNothingElseDoes() {
        val c = town(1955)
        assertTrue(c.apply(Action.PlaceZone(11, 11, 19, 49, Zone.RESIDENTIAL, Density.RURAL)).ok)
        assertTrue(c.apply(Action.PlaceZone(21, 11, 29, 49, Zone.RESIDENTIAL, Density.LOW)).ok)
        assertTrue(c.apply(Action.PlaceZone(41, 11, 49, 19, Zone.COMMERCIAL, Density.RURAL)).ok)
        repeat(36) { repeat(31) { c.tick() } }
        val homes = c.all().filter { it.type.zone == Zone.RESIDENTIAL }
        val rural = homes.filter { it.x in 11..19 }
        val low = homes.filter { it.x in 21..29 }
        assertTrue(rural.isNotEmpty() && low.isNotEmpty(), "rural ${rural.size}, low ${low.size}")
        assertTrue(rural.all { it.type.density == Density.RURAL }, rural.map { it.type }.toString())
        assertTrue(low.none { it.type.density == Density.RURAL })
        // Fewer people a tile on the big lots.
        val ruralPeople = rural.sumOf { it.people?.size ?: 0 }
        val lowPeople = low.sumOf { it.people?.size ?: 0 }
        assertTrue(ruralPeople < lowPeople, "rural $ruralPeople, low $lowPeople")
        // Rural's only for homes and shops.
        assertTrue(c.plan(Action.PlaceZone(31, 41, 39, 49, Zone.INDUSTRIAL, Density.RURAL)).blocked.isNotEmpty())
    }

    @Test
    fun towersWaitForTheMotorAge() {
        val early = town(1935, Era.STREETCAR)
        assertTrue(early.plan(Action.PlaceZone(31, 21, 39, 29, Zone.OFFICE, Density.TOWER)).blocked.isNotEmpty(), "not before the motor age")
        assertTrue(early.plan(Action.PlaceZone(31, 21, 39, 29, Zone.INDUSTRIAL, Density.TOWER)).blocked.isNotEmpty())
        val c = town(1950, Era.MOTOR)
        assertTrue(c.plan(Action.PlaceZone(31, 21, 39, 29, Zone.OFFICE, Density.TOWER)).ok)
    }

    @Test
    fun aBigBuildingCanGiveWayToAnotherOnItsLot() {
        // Before this, a two by two could never give way to another: an office tower stayed one for good.
        val c = town(1970, Era.RENEWAL)
        val m = c.map
        assertTrue(c.apply(Action.PlaceZone(31, 21, 39, 29, Zone.OFFICE, Density.TOWER)).ok)
        val add = City::class.java.getDeclaredMethod("addBuilding", BuildingType::class.java, Int::class.java, Int::class.java, Int::class.java, Int::class.java)
        add.isAccessible = true
        val tower = add.invoke(c, BuildingType.OFFICE_TOWER, 33, 23, 0, 0) as Building
        val asm = City::class.java.getDeclaredMethod("assemblyAt", BuildingType::class.java, Int::class.java, Building::class.java).apply { isAccessible = true }
        val i = m.index(33, 23)
        assertTrue(asm.invoke(c, BuildingType.GLASS_TOWER, i, tower) as Int >= 0)
        assertTrue(asm.invoke(c, BuildingType.SKYSCRAPER, i, tower) as Int >= 0)
        assertTrue(asm.invoke(c, BuildingType.SUPERTALL, i, tower) as Int >= 0)
        // Something else can't build over it.
        assertTrue((asm.invoke(c, BuildingType.GLASS_TOWER, i, null) as Int) < 0)
        // A tower can't go up on a lot zoned only for high.
        assertTrue(c.apply(Action.PlaceZone(41, 21, 49, 29, Zone.OFFICE, Density.HIGH)).ok)
        assertTrue((asm.invoke(c, BuildingType.SKYSCRAPER, m.index(44, 24), null) as Int) < 0)
        assertTrue(asm.invoke(c, BuildingType.GLASS_TOWER, m.index(44, 24), null) as Int >= 0)
    }

    @Test
    fun aHeightLimitOfHighKeepsTowersOut() {
        val c = town(1970, Era.RENEWAL)
        assertTrue(c.apply(Action.PlaceZone(31, 21, 39, 29, Zone.OFFICE, Density.TOWER)).ok)
        assertTrue(c.apply(Action.PaintDistrict(30, 20, 40, 30, NEW_DISTRICT)).ok)
        val d = c.districts.first().copy().also { it.height = Density.HIGH }
        assertTrue(c.apply(Action.SetDistrict(d.id, d)).ok)
        val heightAt = City::class.java.getDeclaredMethod("heightAt", Int::class.java).apply { isAccessible = true }
        assertEquals(Density.HIGH, heightAt.invoke(c, c.map.index(33, 23)))
    }

    @Test
    fun theNewDensitiesUndoAndSave() {
        val c = town(1970, Era.RENEWAL)
        val m = c.map
        assertTrue(c.apply(Action.PlaceZone(11, 11, 19, 19, Zone.RESIDENTIAL, Density.RURAL)).ok)
        assertTrue(c.apply(Action.PlaceZone(31, 21, 39, 29, Zone.RESIDENTIAL, Density.TOWER)).ok)
        assertEquals(Density.TOWER, m.density[m.index(33, 23)])
        c.undo()
        assertEquals(Density.NONE, m.density[m.index(33, 23)])
        c.redo()
        assertEquals(Density.TOWER, m.density[m.index(33, 23)])
        assertEquals(Density.RURAL, m.density[m.index(15, 15)])
        val back = SaveGame.read(SaveGame.write(c))
        assertEquals(Density.RURAL, back.map.density[back.map.index(15, 15)])
        assertEquals(Density.TOWER, back.map.density[back.map.index(33, 23)])
        // An older town still loads and grows.
        val old = SaveGame.read(javaClass.getResourceAsStream("/saves/v28.infill")!!.readBytes())
        assertEquals("Twenty-eighth", old.name)
        val people = old.stats.population
        repeat(70) { old.tick() }
        assertTrue(old.stats.population > people / 2)
        assertFalse(old.map.density.any { it == Density.RURAL || it == Density.TOWER })
    }
}
