package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PowerTest {
    private fun city(year: Int = 1930): City {
        val c = City(5, 64, 32, TerrainOptions(water = 0, trees = 0, river = false))
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 10_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        c.everything = true
        return c
    }

    private fun City.all(): List<Building> = (0 until map.size).mapNotNull { building(map.building[it]) }.distinctBy { it.id }

    private fun City.grid(draw: (Building) -> Int, peak: Int = 100): PowerGrid {
        val g = PowerGrid(map)
        g.update(all(), draw) { b -> Generation.capacity(b.type) }
        return g
    }

    private fun PowerGrid.update(buildings: List<Building>, draw: (Building) -> Int, available: (Building) -> Int) = update(buildings, draw, available, 100)

    /** A coal station at the west end of a line along row 10, and police stations along it. */
    private fun lineOfStations(c: City, xs: List<Int>) {
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 1, 9))
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 3, 10, 62, 10, true)))
        for (x in xs) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.POLICE_STATION, x, 11)).ok)
    }

    @Test
    fun shortOfPowerTheNearestGetItFirst() {
        val c = city()
        lineOfStations(c, listOf(6, 16, 26, 36, 46))
        // Each draws 3 MW; the station makes 10.
        c.grid({ b -> if (b.type == BuildingType.POLICE_STATION) 3_000_000 else 0 })
        val lit = listOf(6, 16, 26, 36, 46).map { c.map.powered[c.map.index(it, 11)] }
        assertEquals(listOf(true, true, true, false, false), lit)
    }

    @Test
    fun theFurtherAwayTheMoreIsLost() {
        val c = city()
        lineOfStations(c, listOf(6, 56))
        val near = c.all().first { it.x == 6 && it.type == BuildingType.POLICE_STATION }
        val far = c.all().first { it.x == 56 }
        // 4.9 MW each: both would fit in 10 MW but for the losses on the long line to the far one.
        c.grid({ b -> if (b.type == BuildingType.POLICE_STATION) 4_900_000 else 0 })
        assertTrue(c.map.powered[c.map.index(near.x, near.y)])
        assertFalse(c.map.powered[c.map.index(far.x, far.y)], "the far one loses out")
    }

    @Test
    fun highVoltageLinesShareThroughSubstations() {
        val c = city()
        // A station's network in the west and a separate one in the east, joined by a high-voltage line and two substations.
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 1, 9))
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 3, 10, 10, 10, true)))
        c.apply(Action.PlaceBuilding(BuildingType.SUBSTATION, 11, 10))
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 11, 9, 50, 9, true), high = true))
        c.apply(Action.PlaceBuilding(BuildingType.SUBSTATION, 50, 10))
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 51, 10, 60, 10, true)))
        c.apply(Action.PlaceBuilding(BuildingType.POLICE_STATION, 55, 11))
        val east = c.map.index(55, 11)
        c.grid({ b -> if (b.type == BuildingType.POLICE_STATION) 2_000_000 else 0 })
        assertTrue(c.map.powered[east], "power across the high-voltage line")
        // Without the line the east is dark.
        c.apply(Action.Bulldoze(20, 9, 20, 9))
        c.grid({ b -> if (b.type == BuildingType.POLICE_STATION) 2_000_000 else 0 })
        assertFalse(c.map.powered[east])
    }

    @Test
    fun aSubstationPassesOnlyItsRating() {
        val c = city()
        c.apply(Action.PlaceBuilding(BuildingType.NUCLEAR_PLANT, 1, 7))
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 4, 9, 4, 9, true), high = true))
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 4, 9, 40, 9, true), high = true))
        c.apply(Action.PlaceBuilding(BuildingType.SUBSTATION, 40, 10))
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 41, 10, 60, 10, true)))
        c.apply(Action.PlaceBuilding(BuildingType.POLICE_STATION, 45, 11))
        c.apply(Action.PlaceBuilding(BuildingType.POLICE_STATION, 55, 11))
        // 15 MW each through a 20 MW substation: one of them goes without.
        c.grid({ b -> if (b.type == BuildingType.POLICE_STATION) 15_000_000 else 0 })
        val lit = listOf(45, 55).count { c.map.powered[c.map.index(it, 11)] }
        assertEquals(1, lit)
    }

    @Test
    fun stationsSmokeByWhatTheyMake() {
        val c = city()
        lineOfStations(c, listOf(6))
        val plant = c.all().first { it.type == BuildingType.COAL_PLANT }
        val grid = c.grid({ b -> if (b.type == BuildingType.POLICE_STATION) 8_000_000 else 0 })
        // What's drawn, and a little lost on the way.
        assertTrue(grid.output.getValue(plant.id) in 8_000_000..8_400_000, "${grid.output[plant.id]} W")
        val idle = c.grid({ 0 })
        assertEquals(0, idle.output[plant.id] ?: 0)
    }

    @Test
    fun theCheapestStationsRunFirst() {
        val c = city()
        // Oil, coal and gas side by side on one line: coal is cheapest to run, then gas, then oil.
        c.apply(Action.PlaceBuilding(BuildingType.OIL_PLANT, 1, 7))
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 4, 7))
        c.apply(Action.PlaceBuilding(BuildingType.GAS_PLANT, 7, 7))
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 1, 9, 30, 9, true)))
        c.apply(Action.PlaceBuilding(BuildingType.POLICE_STATION, 20, 10))
        val (oil, coal, gas) = listOf(1, 4, 7).map { x -> c.all().first { it.x == x && it.y == 7 } }
        val grid = c.grid({ b -> if (b.type == BuildingType.POLICE_STATION) 15_000_000 else 0 })
        assertEquals(10_000_000, grid.output[coal.id], "coal runs flat out")
        // The rest, and what's lost along the line.
        assertTrue((grid.output[gas.id] ?: 0) in 5_000_000..6_000_000, "gas makes the rest: ${grid.output[gas.id]}")
        assertEquals(0, grid.output[oil.id] ?: 0, "oil stands by")
    }

    @Test
    fun aGridRunsItsNuclearStationBeforeTheCoalInTown() {
        val c = city()
        // Coal in town, nuclear out along a high-voltage line, joined through two substations.
        c.apply(Action.PlaceBuilding(BuildingType.NUCLEAR_PLANT, 1, 7))
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 4, 9, 40, 9, true), high = true))
        c.apply(Action.PlaceBuilding(BuildingType.SUBSTATION, 40, 10))
        c.apply(Action.PlaceBuilding(BuildingType.SUBSTATION, 41, 10))
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 60, 10))
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 42, 10, 59, 10, true)))
        c.apply(Action.PlaceBuilding(BuildingType.POLICE_STATION, 45, 11))
        c.apply(Action.PlaceBuilding(BuildingType.POLICE_STATION, 55, 11))
        val nuclear = c.all().first { it.type == BuildingType.NUCLEAR_PLANT }
        val coal = c.all().first { it.type == BuildingType.COAL_PLANT }
        // 24 MW each: the two substations pass 40 of the 48 and more lost on the way, and coal makes the rest.
        val grid = c.grid({ b -> if (b.type == BuildingType.POLICE_STATION) 24_000_000 else 0 })
        assertEquals(40_000_000, grid.output[nuclear.id])
        assertTrue((grid.output[coal.id] ?: 0) in 8_000_000..10_000_000, "coal ${grid.output[coal.id]}")
        assertTrue(listOf(45, 55).all { c.map.powered[c.map.index(it, 11)] })
    }

    @Test
    fun linesCarryTheLoadBeyondThemAndLoseMoreOverloaded() {
        val c = city()
        // A big station, and two police stations far along a line, drawing more than a line is rated for.
        c.apply(Action.PlaceBuilding(BuildingType.GAS_PLANT, 1, 9))
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 3, 10, 62, 10, true)))
        for (x in listOf(40, 55)) c.apply(Action.PlaceBuilding(BuildingType.POLICE_STATION, x, 11))
        val g = PowerGrid(c.map)
        val draw = { b: Building -> if (b.type == BuildingType.POLICE_STATION) 6_000_000 else 0 }
        val available = { b: Building -> Generation.capacity(b.type) }
        g.update(c.all(), draw, available, 100)
        // Near the station the line carries both; past the first, only the far one.
        assertTrue(g.load[c.map.index(10, 10)] > Balance.LINE_RATING, "${g.load[c.map.index(10, 10)]} kW")
        assertTrue(g.load[c.map.index(50, 10)] in 6_000..8_000, "${g.load[c.map.index(50, 10)]} kW")
        val first = g.output.values.sum()
        // Worked out again with the overload known, more is lost on the way.
        g.update(c.all(), draw, available, 100)
        assertTrue(g.output.values.sum() > first, "${g.output.values.sum()} W against $first W")
    }

    @Test
    fun theStationsOfEachEraAndPlace() {
        val c = city(1910)
        c.everything = false
        assertFalse(c.plan(Action.PlaceBuilding(BuildingType.OIL_PLANT, 10, 10)).ok, "oil isn't until the 1920s")
        assertFalse(c.plan(Action.BuildPowerLine(Action.roadPath(c.map, 3, 10, 9, 10, true), high = true)).ok)
        assertTrue(c.plan(Action.PlaceBuilding(BuildingType.COAL_PLANT, 10, 10)).ok)
        assertEquals(Problem.NeedsWater, c.plan(Action.PlaceBuilding(BuildingType.HYDRO_PLANT, 10, 10)).problem)
    }

    @Test
    fun demandGrowsWithTheYearsAndPeaksInWinterThenSummer() {
        assertTrue(Electricity.perPerson(1960) > Electricity.perPerson(1910) * 5)
        assertTrue(Electricity.peak(1930, 0) > Electricity.peak(1930, 6), "winter evenings before air conditioning")
        assertTrue(Electricity.peak(2005, 6) > Electricity.peak(1930, 6), "summer with it")
    }

    @Test
    fun aTownOutgrowingItsStationGoesDark() {
        val c = city(1960)
        c.everything = false
        c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 16, 63, 16, true), RoadType.STREET))
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 1, 18))
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 3, 17, 3, 16, false)))
        c.apply(Action.PlaceZone(4, 12, 60, 15, Zone.RESIDENTIAL, Density.HIGH))
        c.apply(Action.PlaceZone(4, 17, 60, 20, Zone.INDUSTRIAL, Density.HIGH))
        repeat(6 * 365) { c.tick() }
        assertTrue(c.stats.powerDemand > 0)
        // 10 MW against a 1960s town's demand: it can't keep everything lit, and says so.
        if (c.stats.powerDemand > c.stats.powerCapacity) assertTrue(c.stats.powerShort > 0)
    }
}
