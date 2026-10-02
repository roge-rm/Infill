package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalTest {
    /** Works off a road that doesn't reach the edge, beside a line that does, with a [type] on it. */
    private fun town(type: BuildingType, months: Int = 14): City {
        val c = City(29, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true; it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, 1970)
        val m = c.map
        assertTrue(c.apply(Action.BuildRail(Action.roadPath(m, 0, 30, 60, 30, true))).ok)
        assertTrue(c.apply(Action.PlaceBuilding(type, 20, 31)).ok, "$type")
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 5, 34, 58, 34, true), RoadType.STREET)).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 35, 40, 45, Zone.INDUSTRIAL)).ok)
        assertTrue(c.apply(Action.PlaceZone(42, 35, 58, 45, Zone.RESIDENTIAL)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 50, 48)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 49, 49, 5, 49, true))).ok)
        repeat(months) { repeat(31) { c.tick() } }
        return c
    }

    @Test
    fun aTerminalSendsFreightAndGrowsTheMarketMore() {
        val terminal = town(BuildingType.FREIGHT_TERMINAL)
        val yard = town(BuildingType.FREIGHT_YARD)
        val t = terminal.buildingAt(20, 31)!!
        assertTrue(terminal.railLinked(t))
        assertTrue(terminal.freightSent(t) > 0, "freight out by train")
        assertTrue(terminal.stats.goodsExported.sum() > 0)
        // A bigger market for the works, and works near it feel it.
        assertTrue(terminal.stats.industryDemand > yard.stats.industryDemand, "terminal ${terminal.stats.industryDemand}, yard ${yard.stats.industryDemand}")
        val near = City::class.java.getDeclaredField("nearTerminal").apply { isAccessible = true }.get(terminal) as BooleanArray
        assertTrue(near[terminal.map.index(20, 40)])
        assertTrue(!near[terminal.map.index(55, 60)])
    }

    @Test
    fun itsTrainsCarryContainers() {
        val c = town(BuildingType.FREIGHT_TERMINAL)
        val freight = c.trainRoutes.filter { !it.passengers }
        assertTrue(freight.isNotEmpty())
        assertTrue(freight.all { it.containers })
        // And after loading.
        val back = SaveGame.read(SaveGame.write(c))
        assertEquals(c.trainRoutes.count { it.containers }, back.trainRoutes.count { it.containers })
        val yard = town(BuildingType.FREIGHT_YARD, months = 3)
        assertTrue(yard.trainRoutes.none { it.containers })
    }

    @Test
    fun notBeforeItsTime() {
        val c = City(29, 64, 64, TerrainOptions(water = 0, trees = 0, river = false))
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, 1950)
        assertTrue(!c.allows(BuildingType.FREIGHT_TERMINAL))
    }
}
