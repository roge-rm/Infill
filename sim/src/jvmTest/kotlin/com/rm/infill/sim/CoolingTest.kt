package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoolingTest {
    /** Dense blocks along streets in [year], with power, mains and sewers, a few months on. */
    private fun town(year: Int, seed: Long = 21): City {
        val c = City(seed, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.everything = true; it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 50_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        val m = c.map
        for (y in 0 until 64) for (x in 60..63) m.terrain[m.index(x, y)] = Terrain.WATER
        fun road(x0: Int, y0: Int, x1: Int, y1: Int) = c.apply(Action.BuildRoad(Action.roadPath(m, x0, y0, x1, y1, y0 == y1), RoadType.STREET, pipes = true))
        for (y in listOf(10, 20, 30, 40)) road(0, y, 56, y)
        for (x in listOf(10, 20, 30, 40, 50)) road(x, 10, x, 40)
        c.apply(Action.PlaceBuilding(BuildingType.PUMPING_STATION, 58, 14))
        c.apply(Action.BuildPipe(Action.roadPath(m, 57, 14, 50, 14, true), Pipe.WATER))
        c.apply(Action.PlaceBuilding(BuildingType.OUTFALL, 59, 36))
        c.apply(Action.BuildPipe(Action.roadPath(m, 58, 36, 50, 36, true), Pipe.SEWER))
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 2, 50))
        c.apply(Action.BuildPowerLine(Action.roadPath(m, 3, 49, 3, 40, false)))
        c.apply(Action.PlaceZone(11, 11, 49, 39, Zone.RESIDENTIAL, Density.HIGH))
        repeat(12) { repeat(31) { c.tick() } }
        return c
    }

    /** The whole built area made a district with [change] made to its policies. */
    private fun City.district(change: (District) -> Unit) {
        assertTrue(apply(Action.PaintDistrict(0, 0, 63, 63, NEW_DISTRICT)).ok)
        val d = districts.first().copy()
        change(d)
        assertTrue(apply(Action.SetDistrict(d.id, d)).ok)
        repeat(2) { repeat(31) { tick() } }
    }

    private fun City.heatAt(x: Int, y: Int) = map.heat[map.index(x, y)].toInt() and 0xff

    private fun City.averageHeat(): Int {
        var sum = 0
        var n = 0
        for (y in 11..39) for (x in 11..49) {
            sum += heatAt(x, y)
            n++
        }
        return sum / n
    }

    @Test
    fun coolRoofsAndGreenRoofsCoolTheirDistrict() {
        val plain = town(2010).also { it.district { } }
        val cool = town(2010).also { it.district { d -> d.coolRoofs = true } }
        val green = town(2010).also { it.district { d -> d.greenRoofs = true } }
        assertTrue(cool.averageHeat() < plain.averageHeat(), "cool ${cool.averageHeat()}, plain ${plain.averageHeat()}")
        assertTrue(green.averageHeat() < plain.averageHeat(), "green ${green.averageHeat()}, plain ${plain.averageHeat()}")
        // They cost something to keep up.
        assertTrue(cool.stats.environmentUpkeep > plain.stats.environmentUpkeep)
        assertTrue(green.stats.environmentUpkeep > cool.stats.environmentUpkeep)
    }

    @Test
    fun notBeforeTheirTime() {
        val c = town(1960)
        c.everything = false
        c.district { d -> d.coolRoofs = true }
        val withRoofs = c.averageHeat()
        c.district { d -> d.coolRoofs = false }
        assertEquals(withRoofs, c.averageHeat())
    }

    @Test
    fun aCoolingCentreSavesLivesInAHeatWave() {
        fun deaths(centre: Boolean): Int {
            val c = town(1980, seed = 23)
            if (centre) assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COOLING_CENTRE, 5, 21)).ok)
            repeat(2) { repeat(31) { c.tick() } }
            City::class.java.getDeclaredField("heatWaveDays").apply { isAccessible = true }.setInt(c, 10)
            var died = 0
            repeat(6) {
                City::class.java.getDeclaredField("heatWaveDays").apply { isAccessible = true }.setInt(c, 10)
                City::class.java.getDeclaredMethod("updatePeople").apply { isAccessible = true }.invoke(c)
                died += c.stats.deaths
            }
            return died
        }
        val without = deaths(false)
        val with = deaths(true)
        assertTrue(with < without, "with $with, without $without")
    }

    @Test
    fun theRoofsAreSaved() {
        val c = town(2010).also { it.district { d -> d.coolRoofs = true; d.greenRoofs = true } }
        val back = SaveGame.read(SaveGame.write(c))
        assertTrue(back.districts.first().coolRoofs && back.districts.first().greenRoofs)
    }
}
