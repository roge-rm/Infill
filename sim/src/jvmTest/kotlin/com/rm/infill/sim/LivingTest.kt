package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LivingTest {
    /** Homes north of a main street, shops and works south, a power station wired in, grown for [years] from [year]. */
    private fun town(years: Int = 2, year: Int = 1900, seed: Long = 7, terrain: TerrainOptions = TerrainOptions(water = 0, trees = 0, river = false)): City {
        val c = City(seed, 64, 64, terrain)
        c.everything = true
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 5_000_000L)
        val m = c.map
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 60, 30, true))).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 28, 40, 29, Zone.RESIDENTIAL)).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 31, 18, 32, Zone.COMMERCIAL)).ok)
        assertTrue(c.apply(Action.PlaceZone(24, 31, 40, 32, Zone.INDUSTRIAL)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 50, 25)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 51, 27, 5, 27, true))).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 51, 27, 51, 33, true))).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 50, 33, 5, 33, true))).ok)
        repeat(years * 365) { c.tick() }
        return c
    }

    private fun City.runMonths(months: Int) = repeat(months * 31) { tick() }

    private fun City.told(): List<EventKind> = ArrayList<EventKind>().also { out -> takeEvents { out += it.kind } }

    @Test
    fun theBabyBoomAndTheLongFall() {
        assertTrue(Demography.births(1955, Wealth.MIDDLE) > Demography.births(1935, Wealth.MIDDLE))
        assertTrue(Demography.births(1985, Wealth.MIDDLE) < Demography.births(1955, Wealth.MIDDLE))
        assertTrue(Demography.elderlyDeaths(2020) < Demography.elderlyDeaths(1960))
        assertEquals(10, Demography.household(10, 1950))
        assertTrue(Demography.household(10, 2010) < 10)
        assertEquals(1, Demography.household(1, 2030))
    }

    @Test
    fun householdsShrinkFromTheSixties() {
        val c = town(years = 3, year = 1990)
        val homes = c.allBuildings.filter { it.people != null && !it.people!!.empty && it.underway == 0 }
        assertTrue(homes.isNotEmpty())
        for (b in homes) assertEquals(Demography.household(b.type.capacity, c.year), b.people!!.size, "${b.type}")
    }

    @Test
    fun thePoorArePricedOffDearLandUnlessHoused() {
        fun pricedOut(social: Boolean): Int {
            val c = town()
            if (social) {
                City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, 1950)
                c.setOrdinance(Ordinance.SOCIAL_HOUSING, true)
            }
            for (b in c.allBuildings) b.people?.let { if (!it.empty) it.wealth = Wealth.POOR }
            var out = 0
            repeat(Balance.PRICED_OUT_ODDS + 2) {
                // The land's dear when the month turns.
                val start = c.month
                while (c.month == start) {
                    for (i in 0 until c.map.size) c.map.landValue[i] = 250.toByte()
                    c.tick()
                }
                out += c.stats.pricedOut
            }
            return out
        }
        assertTrue(pricedOut(social = false) > 0)
        assertEquals(0, pricedOut(social = true))
    }

    @Test
    fun aShelterTakesInRoughSleepers() {
        val c = town()
        City::class.java.getDeclaredField("homeless").apply { isAccessible = true }.setInt(c, 200)
        c.runMonths(1)
        assertTrue(c.stats.roughSleepers > 0)
        assertEquals(0, c.stats.sheltered)
        City::class.java.getDeclaredField("homeless").apply { isAccessible = true }.setInt(c, 200)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.SHELTER, 20, 25)).ok)
        c.runMonths(2)
        assertTrue(c.stats.sheltered > 0, "sheltered ${c.stats.sheltered} of ${c.homeless}")
    }

    @Test
    fun theFluOf1918AndMedicineInTheNews() {
        val c = town(years = 0, year = 1918)
        c.disasterLevel = 2
        // A town of more than 300 for the flu to take hold.
        repeat(400) { c.tick() }
        val kinds = ArrayList<CityEvent>()
        c.takeEvents { kinds += it }
        val flu = kinds.firstOrNull { it.kind == EventKind.Epidemic }
        if (flu != null) assertEquals(Disease.INFLUENZA.ordinal + 1, flu.count)
        val later = town(years = 0, year = 1944)
        while (later.year < 1956) later.tick()
        val told = ArrayList<CityEvent>()
        later.takeEvents { told += it }
        assertEquals(listOf(0, 1), told.filter { it.kind == EventKind.MedicalAdvance }.map { it.count })
    }

    @Test
    fun aMineWorksItsSeamOut() {
        val c = town()
        val m = c.map
        for (y in 40..43) for (x in 10..13) m.resource[m.index(x, y)] = Resource.ORE
        val add = City::class.java.declaredMethods.first { it.name == "addBuilding" && it.parameterCount == 5 }.apply { isAccessible = true }
        val mine = add.invoke(c, BuildingType.MINE, 11, 41, 0, 0) as Building
        mine.underway = 0
        mine.fill = 16 * Balance.SEAM_MONTHS - 1
        c.takeEvents { }
        c.runMonths(1)
        assertTrue(EventKind.WorkedOut in c.told())
        assertEquals(null, c.buildingAt(11, 41))
        assertTrue(m.brownfield[m.index(11, 41)].toInt() != 0)
        assertEquals(Resource.NONE, m.resource[m.index(10, 40)])
    }

    @Test
    fun aDroughtCutsTheWater() {
        val c = town()
        val dry = City::class.java.declaredMethods.first { it.name == "dryOut" }.apply { isAccessible = true }
        c.ground = 0
        c.takeEvents { }
        repeat(40) { dry.invoke(c, 0, 25) }
        assertTrue(c.drought >= Balance.DROUGHT_AT)
        assertTrue(EventKind.Drought in c.told())
        dry.invoke(c, 40, 20)
        assertTrue(c.drought < 100)
    }

    @Test
    fun aStormSurgeFloodsTheShoreUnlessBanked() {
        val coast = TerrainOptions(water = 0, trees = 0, river = false, sea = Sea.ONE_SIDE)
        val c = town(years = 0, terrain = coast)
        val m = c.map
        c.stormSurge(force = true)
        val flooded = (0 until m.size).count { m.terrain[it] != Terrain.WATER && (m.flood[it].toInt() and 0xff) >= Balance.FLOODED }
        assertTrue(flooded > 0)
        // Banks along every shore keep it out.
        val d = town(years = 0, terrain = coast)
        val n = d.map
        for (i in 0 until n.size) {
            if (n.terrain[i] == Terrain.WATER) continue
            val x = i % n.width
            val y = i / n.width
            val shore = listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1).any { (a, b) -> n.inside(a, b) && n.terrain[n.index(a, b)] == Terrain.WATER }
            if (shore) n.bank[i] = 1
        }
        d.stormSurge(force = true)
        assertEquals(0, (0 until n.size).count { n.terrain[it] != Terrain.WATER && n.bank[it].toInt() == 0 && (n.flood[it].toInt() and 0xff) >= Balance.FLOODED })
    }

    @Test
    fun treesArePlantedAndSpread() {
        val c = town(years = 0)
        val m = c.map
        val funds = c.funds
        val plan = c.apply(Action.PlantTrees(10, 45, 14, 47))
        assertTrue(plan.ok)
        assertEquals(15, plan.changes.size)
        assertEquals(funds - 15 * Prices.PLANT_TREES, c.funds)
        assertEquals(Terrain.TREES, m.terrain[m.index(12, 46)])
        c.undo()
        assertEquals(Terrain.GRASS, m.terrain[m.index(12, 46)])
        c.apply(Action.PlantTrees(10, 45, 30, 55))
        val before = (0 until m.size).count { m.terrain[it] == Terrain.TREES }
        repeat(20 * 365) { c.tick() }
        assertTrue((0 until m.size).count { m.terrain[it] == Terrain.TREES } > before)
    }

    @Test
    fun theHomelessAndTheDroughtAreSaved() {
        val c = town()
        City::class.java.getDeclaredField("homeless").apply { isAccessible = true }.setInt(c, 77)
        City::class.java.getDeclaredField("drought").apply { isAccessible = true }.setInt(c, 42)
        c.startEpidemic(2, 10, Disease.CHOLERA)
        val back = SaveGame.read(SaveGame.write(c))
        assertEquals(77, back.homeless)
        assertEquals(42, back.drought)
        assertEquals(Disease.CHOLERA, back.epidemicKind)
    }
}
