package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PowerEraTest {
    private fun City.setYear(year: Int) = City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(this, year)

    /** An empty map in [year] with everything allowed and money to spend, and a street across at y 12. */
    private fun street(year: Int, size: Int = 64): City {
        val c = City(1, size, size, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true }
        c.setYear(year)
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 12, size - 1, 12, true), RoadType.STREET)).ok)
        return c
    }

    /** Homes, shops and works along a street at y 30, with power. */
    private fun town(size: Int = 64): City {
        val c = City(8, size, size, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true; it.disasterLevel = 0 }
        c.setYear(1960)
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        val m = c.map
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 63, 30, true), RoadType.STREET, pipes = true)).ok)
        assertTrue(c.apply(Action.PlaceZone(4, 27, 60, 29, Zone.RESIDENTIAL, Density.MEDIUM)).ok)
        assertTrue(c.apply(Action.PlaceZone(4, 31, 30, 33, Zone.COMMERCIAL, Density.MEDIUM)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 2, 10)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 3, 12, 3, 30, false))).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 3, 30, 63, 30, true))).ok)
        c.garbageTown = 0
        return c
    }

    private fun City.months(n: Int) = repeat(n) { repeat(31) { tick() } }

    @Test
    fun aNewerStationMakesMoreForLessFuelAndSmoke() {
        val old = BuildingType.COAL_PLANT
        val new = BuildingType.SUPERCRITICAL_COAL
        assertEquals(Generation.capacity(old) * 4, Generation.capacity(new))
        assertTrue(Generation.fuel(new) < Generation.fuel(old))
        assertTrue(Generation.fumes(new) < Generation.fumes(old))
        assertTrue(Generation.carbon(new) < Generation.carbon(old))
        // The lines keep their footprints.
        for (t in BuildingType.entries) {
            val root = t.root
            assertEquals(root.width to root.height, t.width to t.height, "$t")
        }
    }

    @Test
    fun anOutdatedStationMakesLessUntilBroughtUpToDate() {
        val c = street(1925)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 10, 10)).ok)
        val b = c.buildingAt(10, 10)!!
        val fresh = c.stationAvailable(b)
        assertEquals(Generation.capacity(BuildingType.COAL_PLANT), fresh)
        c.setYear(1995)
        val dated = c.stationAvailable(b)
        assertTrue(dated < fresh, "dated $dated, fresh $fresh")
        assertTrue(c.outdated(b))
        assertTrue(c.apply(Action.RenewArea(10, 10, 10, 10)).ok)
        assertEquals(BuildingType.SUPERCRITICAL_COAL, b.type)
        b.outage = 0
        assertEquals(Generation.capacity(BuildingType.SUPERCRITICAL_COAL), c.stationAvailable(b))
    }

    @Test
    fun landfillGasNeedsAFillingDump() {
        val c = street(1990)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.SANITARY_LANDFILL, 10, 13)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.LANDFILL_GAS, 14, 14)).ok)
        val gas = c.buildingAt(14, 14)!!
        assertEquals(0, c.stationAvailable(gas))
        val fill = c.buildingAt(10, 13)!!
        // A landfill holds half as much again as a dump.
        assertEquals(Balance.DUMP_ROOM * 3 / 2, c.dumpRoom(fill))
        fill.fill = c.dumpRoom(fill) / 2
        assertEquals(Generation.capacity(BuildingType.LANDFILL_GAS), c.stationAvailable(gas))
    }

    @Test
    fun compostTakesSomeAndATransferStationReachesFarDumps() {
        // The dump's too far off for the trucks, until there's a transfer station.
        fun collected(transfer: Boolean): Int {
            val c = town(128)
            assertTrue(c.apply(Action.PlaceBuilding(BuildingType.DUMP, 120, 120)).ok)
            if (transfer) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.TRANSFER_STATION, 30, 25)).ok, "transfer")
            c.months(8)
            return c.stats.wasteCollected
        }
        val without = collected(false)
        val with = collected(true)
        assertTrue(with > without, "collected with a transfer station $with, without $without")

        val c = town()
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.DUMP, 40, 10)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COMPOST_YARD, 30, 34)).ok, "compost")
        c.months(8)
        assertTrue(c.buildingAt(30, 34)!!.served > 0, "composted ${c.buildingAt(30, 34)!!.served}, ${c.stats.population} people, ${c.stats.waste} t")
    }

    @Test
    fun wasteToEnergyMakesPowerFromWhatItBurns() {
        val c = town()
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.WASTE_TO_ENERGY, 40, 34)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 41, 31, 41, 34, false))).ok)
        c.months(8)
        val b = c.buildingAt(40, 34)!!
        val could = c.stationAvailable(b)
        assertTrue(could in 1 until Generation.capacity(BuildingType.WASTE_TO_ENERGY) + 1, "could make $could, ${c.stats.population} people, ${c.stats.waste} t, ${c.stats.wasteCollected}% collected, outage ${b.outage}")
    }

    @Test
    fun theNewKindsSave() {
        val c = street(2040)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.SMALL_REACTOR, 10, 13)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.GEOTHERMAL, 20, 13)).ok)
        c.months(1)
        val loaded = SaveGame.read(SaveGame.write(c))
        assertEquals(BuildingType.SMALL_REACTOR, loaded.buildingAt(10, 13)!!.type)
        assertEquals(c.stats.powerCapacity, loaded.stats.powerCapacity)
    }
}
