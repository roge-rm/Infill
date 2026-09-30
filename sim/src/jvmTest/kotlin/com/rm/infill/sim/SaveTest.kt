package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaveTest {
    /** A town with a bit of everything: zones, power, services, parks, and a few years behind it. */
    private fun town(): City {
        val c = City(1900)
        val m = c.map
        c.name = "Ashford"
        c.apply(Action.BuildRoad(Action.roadPath(m, 0, 64, 120, 64, true)))
        c.apply(Action.PlaceZone(10, 60, 100, 63, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(10, 65, 50, 67, Zone.COMMERCIAL))
        c.apply(Action.PlaceZone(60, 65, 100, 67, Zone.INDUSTRIAL))
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 105, 66))
        c.apply(Action.BuildPowerLine(Action.roadPath(m, 104, 66, 10, 59, false)))
        c.apply(Action.PlaceBuilding(BuildingType.POLICE_STATION, 40, 57))
        c.apply(Action.PlaceBuilding(BuildingType.FIRE_STATION, 55, 69))
        c.apply(Action.PlaceParks(20, 56, 30, 57))
        c.residentialTax = 9
        c.fireFunding = 70
        repeat(3 * 365 + 40) { c.tick() }
        return c
    }

    private fun City.fingerprint() = listOf(map.hash(), funds, stats.population.toLong(), year.toLong(), month.toLong(), day.toLong(), rng.state)

    @Test
    fun aSavedCityLoadsTheSame() {
        val c = town()
        val loaded = SaveGame.read(SaveGame.write(c))
        assertEquals(c.fingerprint(), loaded.fingerprint())
        assertEquals("Ashford", loaded.name)
        assertEquals(9, loaded.residentialTax)
        assertEquals(70, loaded.fireFunding)
        assertEquals(c.history.values(Series.Population).toList(), loaded.history.values(Series.Population).toList())
        assertEquals(c.buildingCount, loaded.buildingCount)
        for (i in 0 until c.map.size) {
            assertEquals(c.map.buildingType[i], loaded.map.buildingType[i], "building type at $i")
            assertEquals(c.map.landValue[i], loaded.map.landValue[i], "land value at $i")
        }
    }

    @Test
    fun aLoadedCityCarriesOnTheSame() {
        val original = town()
        val loaded = SaveGame.read(SaveGame.write(original))
        repeat(3 * 365) {
            original.tick()
            loaded.tick()
        }
        assertEquals(original.fingerprint(), loaded.fingerprint())
        assertEquals(original.weather.temperature, loaded.weather.temperature)
        assertEquals(original.weather.snowCover, loaded.weather.snowCover)
    }

    @Test
    fun theSummaryReadsWithoutLoading() {
        val c = town()
        val s = assertNotNull(SaveGame.summary(SaveGame.write(c)))
        assertEquals("Ashford", s.name)
        assertEquals(c.year, s.year)
        assertEquals(c.stats.population, s.population)
        assertNull(SaveGame.summary(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun badFilesAreTurnedAway() {
        val bytes = SaveGame.write(town())
        assertFailsWith<SaveError> { SaveGame.read(bytes.copyOf(bytes.size / 2)) }
        assertFailsWith<SaveError> { SaveGame.read("hello".encodeToByteArray() + ByteArray(40)) }
        val newer = bytes.copyOf().also { it[7] = 99 }
        assertFailsWith<SaveError> { SaveGame.read(newer) }
    }

    @Test
    fun savesAreSmall() {
        val bytes = SaveGame.write(town())
        println("a 128 x 128 town saves in ${bytes.size} bytes")
        assertTrue(bytes.size < 200_000, "${bytes.size} bytes")
    }
}
