package com.rm.infill.sim

import com.rm.infill.sound.Recipes
import com.rm.infill.sound.SharedParams
import com.rm.infill.sound.TownSound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TownSoundTest {
    private fun town(year: Int): City {
        val c = City(71, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.disasterLevel = 0 }
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        c.everything = true
        val m = c.map
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 63, 30, true), RoadType.STREET)).ok)
        assertTrue(c.apply(Action.PlaceZone(2, 31, 60, 34, Zone.RESIDENTIAL, Density.LOW)).ok)
        assertTrue(c.apply(Action.PlaceZone(2, 26, 30, 29, Zone.COMMERCIAL, Density.LOW)).ok)
        assertTrue(c.apply(Action.PlaceZone(32, 26, 60, 29, Zone.INDUSTRIAL, Density.LOW)).ok)
        repeat(18) { repeat(31) { c.tick() } }
        assertTrue(c.stats.population > 0)
        return c
    }

    private fun frame(c: City, x: Float = 32f, y: Float = 30f, half: Float = 10f, hour: Float = 12f): TownSound.Frame {
        val t = TownSound(c)
        t.survey()
        val out = TownSound.Frame()
        t.frame(TownSound.Listener(x, y, half, half * 2, 0.6f), hour, 0.1f, emptyList(), out)
        return out
    }

    private fun TownSound.Frame.param(key: Int, p: Int): Float {
        for (k in 0 until count) if (keys[k] == key) return params[k * SharedParams.COUNT + p]
        error("no sound $key")
    }

    @Test
    fun aBusyStreetInViewIsHeardAndFadesFarOff() {
        val c = town(1950)
        val here = frame(c).loudness(TownSound.KEY_TRAFFIC)
        assertTrue(here > 0.02f, "traffic $here")
        // Off in an empty corner, far less of it.
        val corner = frame(c, x = 4f, y = 4f, half = 4f).loudness(TownSound.KEY_TRAFFIC)
        assertTrue(corner < here / 2, "corner $corner, street $here")
    }

    @Test
    fun hoovesIn1905AndEnginesIn1950() {
        val old = frame(town(1905))
        val later = frame(town(1950))
        assertTrue(old.param(TownSound.KEY_TRAFFIC, 1) < 0.1f)
        assertEquals(1f, later.param(TownSound.KEY_TRAFFIC, 1))
        assertEquals(Recipes.TRAFFIC, later.recipes[later.keys.indexOf(TownSound.KEY_TRAFFIC)])
    }

    @Test
    fun quieterAtNightAndNoBirds() {
        val c = town(1950)
        val m = c.map
        for (y in 40..60) for (x in 10..50) m.terrain[m.index(x, y)] = Terrain.TREES
        val noon = frame(c, y = 40f, half = 20f)
        val night = frame(c, y = 40f, half = 20f, hour = 2f)
        assertTrue(noon.loudness(TownSound.KEY_BIRDS) > 0f)
        assertEquals(0f, night.loudness(TownSound.KEY_BIRDS))
        assertTrue(night.loudness(TownSound.KEY_TRAFFIC) < noon.loudness(TownSound.KEY_TRAFFIC))
    }

    @Test
    fun soundsArePannedToWhereTheyAre() {
        val c = town(1950)
        // The street's to the south of a view centred north of it, and to the east of one centred off the west edge.
        val west = frame(c, x = -10f, y = 30f, half = 20f)
        assertTrue(west.param(TownSound.KEY_TRAFFIC, SharedParams.PAN) > 0.2f)
    }

    @Test
    fun aNearFireIsLouderThanAFarOne() {
        val c = town(1950)
        val b = c.allBuildings.first { it.type.zone == Zone.RESIDENTIAL && it.underway == 0 }
        b.burning = 10
        val near = frame(c, x = b.x.toFloat(), y = b.y.toFloat(), half = 8f).loudness(TownSound.KEY_FIRE)
        val far = frame(c, x = (b.x + 40f) % 64f, y = 5f, half = 8f).loudness(TownSound.KEY_FIRE)
        assertTrue(near > far * 2, "near $near, far $far")
        // And the fire engines are coming, with a siren for the year.
        assertEquals(1f, frame(c, x = b.x.toFloat(), y = b.y.toFloat()).param(TownSound.KEY_SIREN, 1))
    }

    @Test
    fun windAndRainAllRound() {
        val c = town(1950)
        Weather::class.java.getDeclaredField("precipitation").apply { isAccessible = true }.set(c.weather, Precipitation.Rain)
        Weather::class.java.getDeclaredField("intensity").apply { isAccessible = true }.setInt(c.weather, 60)
        val f = frame(c)
        assertTrue(f.loudness(TownSound.KEY_RAIN) > 0f)
        assertEquals(0f, f.param(TownSound.KEY_RAIN, SharedParams.PAN))
        assertTrue(f.loudness(TownSound.KEY_WIND) > 0f)
    }
}
