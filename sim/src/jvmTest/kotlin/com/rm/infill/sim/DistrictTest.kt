package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DistrictTest {
    private fun city(year: Int = 1930): City {
        val c = City(4, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.disasterLevel = 0; it.everything = true }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        return c
    }

    private fun City.i(x: Int, y: Int) = map.index(x, y)

    private fun City.all(): List<Building> = (0 until map.size).mapNotNull { building(map.building[it]) }.distinctBy { it.id }

    /** Two matching halves of a town either side of x 32, with jobs between. */
    private fun town(c: City, density: Byte = Density.HIGH) {
        val m = c.map
        for (k in 10..50 step 6) {
            c.apply(Action.BuildRoad(Action.roadPath(m, 0, k, 63, k, true), RoadType.STREET, pipes = true))
            c.apply(Action.BuildRoad(Action.roadPath(m, k, 0, k, 63, true), RoadType.STREET, pipes = true))
        }
        // Water from a lake in the corner.
        for (y in 0..6) for (x in 56..63) m.terrain[m.index(x, y)] = Terrain.WATER
        c.apply(Action.PlaceBuilding(BuildingType.PUMPING_STATION, 54, 2))
        c.apply(Action.BuildPipe(Action.roadPath(m, 54, 4, 50, 10, false), Pipe.WATER))
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 1, 52))
        for (k in 10..50 step 6) c.apply(Action.BuildPowerLine(Action.roadPath(m, 2, k + 1, 62, k + 1, true)))
        c.apply(Action.BuildPowerLine(Action.roadPath(m, 2, 51, 2, 11, false)))
        c.apply(Action.PlaceZone(11, 11, 51, 33, Zone.RESIDENTIAL, density))
        c.apply(Action.PlaceZone(11, 35, 51, 39, Zone.COMMERCIAL, density))
        c.apply(Action.PlaceZone(11, 41, 51, 49, Zone.INDUSTRIAL, density))
    }

    private fun City.months(n: Int) = repeat(n) { repeat(31) { tick() } }

    @Test
    fun paintingMakesANamedDistrictAndUndoTakesItBack() {
        val c = city()
        assertTrue(c.apply(Action.PaintDistrict(10, 10, 20, 20, NEW_DISTRICT)).ok)
        assertEquals(1, c.districts.size)
        val d = c.districts[0]
        assertTrue(d.name.isNotBlank())
        assertEquals(d, c.districtAt(c.i(15, 15)))
        assertNull(c.districtAt(c.i(30, 30)))
        // Painting more into it, then out again.
        c.apply(Action.PaintDistrict(21, 10, 25, 20, d.id))
        assertEquals(d, c.districtAt(c.i(23, 15)))
        c.apply(Action.PaintDistrict(10, 10, 25, 20, 0))
        assertTrue(c.districts.isEmpty(), "painted out of every tile, it's gone")
        c.undo()
        assertEquals(1, c.districts.size)
        assertEquals(d.id, c.districtAt(c.i(15, 15))?.id)
        c.undo()
        c.undo()
        assertTrue(c.districts.isEmpty())
        assertNull(c.districtAt(c.i(15, 15)))
    }

    @Test
    fun aHeightLimitKeepsADistrictLow() {
        val c = city()
        town(c)
        c.apply(Action.PaintDistrict(0, 0, 31, 63, NEW_DISTRICT))
        val d = c.districts[0].copy().also { it.height = Density.LOW }
        assertTrue(c.apply(Action.SetDistrict(d.id, d)).ok)
        c.months(72)
        val west = c.all().filter { it.x < 31 && it.type.zone != Zone.NONE }
        val east = c.all().filter { it.x > 33 && it.type.zone != Zone.NONE }
        assertTrue(west.all { it.type.density <= Density.LOW }, "west: ${west.map { it.type }.toSet()}")
        assertTrue(east.any { it.type.density > Density.LOW }, "east: ${east.map { it.type }.toSet()}")
    }

    @Test
    fun noHeavyIndustryKeepsWorksSmall() {
        val c = city()
        town(c)
        c.apply(Action.PaintDistrict(0, 0, 31, 63, NEW_DISTRICT))
        val d = c.districts[0].copy().also { it.lightIndustry = true }
        c.apply(Action.SetDistrict(d.id, d))
        c.months(72)
        val west = c.all().filter { it.x < 31 && it.type.zone == Zone.INDUSTRIAL }
        assertTrue(west.all { it.type.stage <= Balance.LIGHT_INDUSTRY }, "${west.map { it.type }.toSet()}")
    }

    @Test
    fun lowerTaxesDrawAndPayLess() {
        val c = city()
        town(c)
        c.apply(Action.PaintDistrict(0, 0, 31, 63, NEW_DISTRICT))
        val cut = c.districts[0].copy().also { it.tax.fill(-Balance.DISTRICT_TAX_RANGE) }
        c.apply(Action.SetDistrict(cut.id, cut))
        val west = c.i(20, 20)
        val east = c.i(44, 20)
        assertTrue(c.attraction(west, Zone.RESIDENTIAL) > c.attraction(east, Zone.RESIDENTIAL) - 4)
        c.months(24)
        val before = c.stats.residentialIncome
        // The same district taxed hard brings in more from its homes.
        val raise = c.districts[0].copy().also { it.tax.fill(Balance.DISTRICT_TAX_RANGE) }
        c.apply(Action.SetDistrict(raise.id, raise))
        c.months(1)
        assertTrue(c.stats.residentialIncome > before, "${c.stats.residentialIncome} against $before")
    }

    @Test
    fun limitedParkingMeansFewerDrive() {
        fun driving(parking: Boolean): Int {
            val c = city(1960)
            town(c, Density.MEDIUM)
            c.apply(Action.PaintDistrict(0, 0, 63, 63, NEW_DISTRICT))
            val d = c.districts[0].copy().also { it.parking = parking }
            c.apply(Action.SetDistrict(d.id, d))
            c.months(36)
            val modes = c.stats.byMode
            return modes[Mode.CAR.ordinal] * 100 / maxOf(1, modes.sum())
        }
        assertTrue(driving(true) < driving(false), "${driving(true)}% with limited parking, ${driving(false)}% without")
    }

    @Test
    fun heritageIsKept() {
        val c = city(1960)
        town(c)
        c.apply(Action.PaintDistrict(0, 0, 63, 63, NEW_DISTRICT))
        val d = c.districts[0].copy().also { it.heritage = true }
        c.apply(Action.SetDistrict(d.id, d))
        // Old brick homes, built long enough ago to be heritage.
        c.months(12)
        val old = c.all().filter { it.type.heritage && it.underway == 0 }
        for (b in old) b.built = 0
        val ids = old.filter { c.isHeritage(it) }.map { it.id }.toSet()
        c.months(36)
        val stillThere = c.all().filter { it.id in ids }
        assertEquals(ids.size, stillThere.size, "heritage pulled down")
        assertTrue(stillThere.all { c.isHeritage(it) })
    }

    @Test
    fun districtsHaveFiguresAndSurviveASave() {
        val c = city()
        town(c)
        c.apply(Action.PaintDistrict(0, 0, 31, 63, NEW_DISTRICT))
        val d = c.districts[0].copy().also { it.tax[0] = -2; it.parking = true; it.height = Density.MEDIUM }
        c.apply(Action.SetDistrict(d.id, d))
        c.months(24)
        val f = c.districtFigures(d.id)
        assertTrue(f.people > 0 && f.tiles > 0)
        val loaded = SaveGame.read(SaveGame.write(c)).also { it.disasterLevel = 0 }
        assertEquals(1, loaded.districts.size)
        val e = loaded.districts[0]
        assertEquals(d.name, e.name)
        assertEquals(-2, e.tax[0])
        assertTrue(e.parking)
        assertEquals(Density.MEDIUM, e.height)
        assertEquals(e.id, loaded.districtAt(c.i(10, 10))?.id)
        assertFalse(loaded.districtAt(c.i(40, 10)) != null)
        repeat(100) { c.tick(); loaded.tick() }
        assertEquals(c.map.hash(), loaded.map.hash())
    }

    @Test
    fun freeFaresDrawRidersAndBringInNothing() {
        fun run(free: Boolean): City {
            val c = city(1930)
            town(c, Density.MEDIUM)
            val m = c.map
            for (y in listOf(12, 24, 36, 48)) c.apply(Action.PlaceStop(28, y, Stop.BUS))
            c.apply(Action.PlaceBuilding(BuildingType.BUS_GARAGE, 29, 6))
            c.apply(Action.BuildRoad(Action.roadPath(m, 28, 6, 28, 10, false), RoadType.STREET))
            c.apply(Action.AddLine(false, listOf(12, 24, 36, 48).map { m.index(28, it) }.toIntArray(), 6))
            c.apply(Action.PaintDistrict(0, 0, 63, 63, NEW_DISTRICT))
            val d = c.districts[0].copy().also { it.freeFares = free }
            c.apply(Action.SetDistrict(d.id, d))
            c.months(36)
            return c
        }
        val paying = run(false)
        val free = run(true)
        assertTrue(free.stats.byMode[Mode.BUS.ordinal] >= paying.stats.byMode[Mode.BUS.ordinal], "${free.stats.byMode[Mode.BUS.ordinal]} free, ${paying.stats.byMode[Mode.BUS.ordinal]} paying")
        assertTrue(paying.stats.fareIncome > 0)
        assertEquals(0, free.stats.fareIncome)
    }

    @Test
    fun trucksKeepOffStreetsTheyreBannedFrom() {
        val c = city(1950)
        val m = c.map
        // Two ways from the works to the west edge: straight through the middle, or round.
        c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 55, 30, true), RoadType.STREET))
        c.apply(Action.BuildRoad(Action.roadPath(m, 50, 30, 50, 50, true), RoadType.STREET))
        c.apply(Action.BuildRoad(Action.roadPath(m, 0, 50, 50, 50, true), RoadType.STREET))
        val n = m.size
        fun freight(): Traffic {
            val t = City::class.java.getDeclaredField("traffic").apply { isAccessible = true }.get(c) as Traffic
            val f = IntArray(n).also { it[m.index(55, 30)] = 200 }
            t.newMonth(IntArray(n), IntArray(n), f, IntArray(n), IntArray(n), 0)
            t.sendDay(1, 1)
            t.newMonth(IntArray(n), IntArray(n), IntArray(n), IntArray(n), IntArray(n), 0)
            return t
        }
        val before = freight().lastVolume[m.index(25, 30)]
        assertTrue(before > 0, "through the middle")
        c.apply(Action.PaintDistrict(10, 25, 45, 35, NEW_DISTRICT))
        val d = c.districts[0].copy().also { it.noTrucks = true }
        c.apply(Action.SetDistrict(d.id, d))
        // The running average fades as the trucks stay away.
        repeat(4) { freight() }
        val t = freight()
        assertTrue(t.lastVolume[m.index(25, 30)] < before / 10, "kept out: ${t.lastVolume[m.index(25, 30)]} against $before")
        assertTrue(t.lastVolume[m.index(25, 50)] > 0, "round instead")
    }

    @Test
    fun aPollutionLimitCleansTheAir() {
        fun air(limit: Boolean): Int {
            val c = city()
            town(c)
            c.apply(Action.PaintDistrict(0, 0, 63, 63, NEW_DISTRICT))
            val d = c.districts[0].copy().also { it.cleanWorks = limit }
            c.apply(Action.SetDistrict(d.id, d))
            c.months(36)
            return (0 until c.map.size).sumOf { c.map.pollution[it].toInt() and 0xff }
        }
        assertTrue(air(true) < air(false), "${air(true)} with the limit, ${air(false)} without")
    }

    @Test
    fun rentControlKeepsTheWellOffOut() {
        val c = city()
        town(c)
        c.apply(Action.PaintDistrict(0, 0, 63, 63, NEW_DISTRICT))
        val d = c.districts[0].copy().also { it.rentControl = true }
        c.apply(Action.SetDistrict(d.id, d))
        c.months(36)
        assertTrue(c.homes.isNotEmpty())
        assertTrue(c.homes.none { it.people!!.wealth == Wealth.WELL_OFF }, "${c.homes.groupingBy { it.people!!.wealth }.eachCount()}")
        val loaded = SaveGame.read(SaveGame.write(c))
        assertTrue(loaded.districts[0].rentControl)
    }

    @Test
    fun districtsWaitForTheStreetcarAge() {
        val c = City(5, 64, 64, TerrainOptions(water = 0, trees = 0, river = false))
        assertFalse(c.allowsDistricts())
        assertFalse(c.apply(Action.PaintDistrict(10, 10, 20, 20, NEW_DISTRICT)).ok)
        City::class.java.getDeclaredField("era").apply { isAccessible = true }.set(c, Era.STREETCAR)
        assertTrue(c.apply(Action.PaintDistrict(10, 10, 20, 20, NEW_DISTRICT)).ok)
    }
}
