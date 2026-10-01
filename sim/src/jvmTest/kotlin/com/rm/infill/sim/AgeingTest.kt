package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgeingTest {
    /** A town by a river on the east, with a pumping station and a street with its pipes, and homes along it. */
    private fun city(): City {
        val c = City(8, 48, 48, TerrainOptions(water = 0, trees = 0, river = false))
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        // Late enough that things can have been laid decades back.
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, 1950)
        val m = c.map
        for (y in 0 until 48) for (x in 44..47) m.terrain[m.index(x, y)] = Terrain.WATER
        c.apply(Action.BuildRoad(Action.roadPath(m, 2, 20, 41, 20, true), RoadType.STREET, pipes = true))
        c.apply(Action.PlaceBuilding(BuildingType.PUMPING_STATION, 42, 18))
        c.apply(Action.BuildPipe(Action.roadPath(m, 42, 20, 41, 20, true), Pipe.WATER))
        c.apply(Action.PlaceZone(2, 21, 40, 22, Zone.RESIDENTIAL, Density.LOW))
        return c
    }

    private fun City.call(name: String, vararg args: Any?) =
        City::class.java.declaredMethods.first { it.name == name && it.parameterCount == args.size }.apply { isAccessible = true }.invoke(this, *args)

    private fun City.days(n: Int) = repeat(n) { tick() }

    @Test
    fun theChanceOfFailingGrowsWithAge() {
        assertEquals(0, Ageing.failChance(10 * 12, 50), "young things don't fail")
        assertEquals(Balance.FAIL_AT_LIFE, Ageing.failChance(50 * 12, 50))
        assertEquals(Balance.FAIL_AT_LIFE * 4, Ageing.failChance(100 * 12, 50))
        assertEquals(Balance.FAIL_MOST, Ageing.failChance(1000 * 12, 50))
    }

    @Test
    fun mostGoesInTheEndButSomeLastsLongPastItsLife() {
        val rng = Rng(3)
        val life = 50
        val n = 10_000
        fun survivingAt(years: Int): Int {
            var alive = 0
            repeat(n) {
                var ok = true
                for (month in 1..years * 12) {
                    if (rng.nextInt(1_000_000) < Ageing.failChance(month, life)) { ok = false; break }
                }
                if (ok) alive++
            }
            return alive * 100 / n
        }
        val atLife = survivingAt(life)
        val atTwice = survivingAt(life * 2)
        val atThrice = survivingAt(life * 3)
        assertTrue(atLife >= 80, "$atLife% last their expected life")
        assertTrue(atTwice in 20..70, "$atTwice% last twice it")
        assertTrue(atThrice in 1..20, "$atThrice% last three times it")
    }

    @Test
    fun aBurstMainCutsTheWaterAndShutsTheRoadUntilItsMended() {
        val c = city()
        c.days(40)
        val m = c.map
        val home = (2..40).map { m.index(it, 21) }.first { m.building[it] != 0 && m.watered[it] }
        val hx = home % m.width
        // Burst the main all along the stretch the home could draw from.
        val r = Balance.PIPE_REACH
        for (x in hx - r..hx + r) c.call("fail", m.index(x, 20), Broken.WATER, Balance.MEND_MAIN, Balance.REPAIR_MAIN)
        val at = m.index(hx, 20)
        m.waterLaid[at] = 0
        assertTrue(m.closed(at))
        c.days(1)
        assertFalse(m.watered[home], "water beyond the burst")
        c.days(Balance.MEND_MAIN + 1)
        assertFalse(m.closed(at))
        assertTrue(m.watered[home], "water back once it's mended")
        assertTrue(m.waterLaid[at] > 0, "the patch puts back a little life")
        assertTrue(m.waterLaid[at] < c.monthNow, "but doesn't make it new")
    }

    @Test
    fun potholesSlowTheTrafficWithoutClosingTheRoad() {
        val c = city()
        val at = c.map.index(10, 20)
        c.call("fail", at, Broken.ROAD, Balance.MEND_ROAD, Balance.REPAIR_ROAD)
        assertTrue(c.map.potholed(at))
        assertFalse(c.map.closed(at))
    }

    @Test
    fun drawingOverAWornMainRelaysIt() {
        val c = city()
        val m = c.map
        val path = Action.roadPath(m, 5, 20, 15, 20, true)
        assertFalse(c.plan(Action.BuildPipe(path, Pipe.WATER)).ok, "a new main stays")
        for (i in path) m.waterLaid[i] = (c.monthNow - 40 * 12).toShort()
        val plan = c.apply(Action.BuildPipe(path, Pipe.WATER))
        assertTrue(plan.ok)
        assertEquals(path.size * Material.CAST_IRON.cost, plan.cost)
        for (i in path) assertEquals(c.monthNow.toShort(), m.waterLaid[i])
        // A programme: the first tiles dug up now, the rest waiting their turn.
        assertTrue(m.closed(path[0]))
        assertFalse(m.closed(path.last()))
        c.days(path.size / Balance.WORKS_PER_DAY + Balance.WORKS_DAYS + 2)
        for (i in path) assertEquals(0, m.broken[i].toInt(), "works done at $i")
    }

    @Test
    fun aWornRoadIsRelaidWithItsPipesForLess() {
        val c = city()
        val m = c.map
        val path = Action.roadPath(m, 5, 20, 9, 20, true)
        for (i in path) {
            m.roadLaid[i] = (c.monthNow - 20 * 12).toShort()
            m.waterLaid[i] = (c.monthNow - 40 * 12).toShort()
        }
        val plan = c.plan(Action.BuildRoad(path, RoadType.STREET, pipes = true))
        val each = RoadType.STREET.price * Balance.RENEW_ROAD / 100 + Material.CAST_IRON.cost * Balance.PIPES_WITH_ROAD / 100
        assertEquals(path.size * each, plan.cost)
        c.apply(Action.BuildRoad(path, RoadType.STREET, pipes = true))
        assertEquals(c.monthNow.toShort(), m.roadLaid[path[0]])
        // Undone, everything's as it was, ages and all.
        c.undo()
        assertEquals((c.monthNow - 20 * 12).toShort(), m.roadLaid[path[0]])
        assertEquals((c.monthNow - 40 * 12).toShort(), m.waterLaid[path[0]])
        assertEquals(0, m.broken[path[0]].toInt())
    }

    @Test
    fun renewingAnAreaRelaysWhatsWornAndLeavesTheRest() {
        val c = city()
        val m = c.map
        // The west half of the street worn, the east half new.
        for (x in 2..20) {
            val i = m.index(x, 20)
            m.roadLaid[i] = (c.monthNow - 20 * 12).toShort()
            m.waterLaid[i] = (c.monthNow - 40 * 12).toShort()
        }
        val plan = c.plan(Action.RenewArea(0, 18, 47, 22))
        assertTrue(plan.ok)
        assertEquals(19, plan.changes.size, "the worn tiles only")
        c.apply(Action.RenewArea(0, 18, 47, 22))
        assertEquals(c.monthNow.toShort(), m.roadLaid[m.index(10, 20)])
        assertEquals(c.monthNow.toShort(), m.waterLaid[m.index(10, 20)])
        assertTrue(m.closed(m.index(2, 20)), "the first street dug up")
        assertFalse(m.closed(m.index(20, 20)), "the last waiting its turn")
        assertEquals(0, m.broken[m.index(30, 20)].toInt(), "new road left alone")
        c.days(19 / Balance.WORKS_PER_DAY + Balance.WORKS_DAYS + 2)
        assertEquals(0, (2..20).count { m.broken[m.index(it, 20)].toInt() != 0 })
    }

    @Test
    fun aWornOutPowerStationBreaksDown() {
        val c = city()
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 10, 30))
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 12, 30, 12, 23, false)))
        c.days(40)
        val plant = c.buildingAt(10, 30)!!
        assertTrue(c.map.powered[c.map.index(12, 25)])
        plant.built = c.monthNow - 200 * 12
        var months = 0
        while (plant.outage == 0 && months < 2000) {
            c.call("wearOut")
            months++
        }
        assertTrue(plant.outage > 0, "it never broke down")
        c.days(1)
        assertFalse(c.map.powered[c.map.index(12, 25)], "no power while it's down")
        c.days(Balance.MEND_PLANT + 1)
        assertTrue(c.map.powered[c.map.index(12, 25)], "power back once it's mended")
    }

    @Test
    fun sewageWorksFoulTheRiverLess() {
        for (type in listOf(BuildingType.OUTFALL, BuildingType.SEWAGE_WORKS, BuildingType.TREATMENT_PLANT)) {
            val c = city()
            c.everything = true
            val m = c.map
            c.apply(Action.BuildPipe(Action.roadPath(m, 41, 20, 41, 23, false), Pipe.SEWER))
            assertTrue(c.apply(Action.PlaceBuilding(type, 44 - type.width, 22)).ok, "$type")
            repeat(3) { val mo = c.month; while (c.month == mo) c.tick() }
            // The sewage reaching it: every building on the sewer.
            val flow = (0 until m.size).mapNotNull { c.building(m.building[it]) }.distinctBy { it.id }
                .filter { m.sewered[m.index(it.x, it.y)] }.sumOf { it.type.capacity }
            @Suppress("UNCHECKED_CAST")
            val sewage = City::class.java.getDeclaredField("sewage").apply { isAccessible = true }.get(c) as Map<Int, Int>
            assertTrue(flow > 0)
            assertEquals(flow * type.fouls / 100, sewage.values.single(), "$type")
        }
        assertEquals(50, BuildingType.SEWAGE_WORKS.fouls)
        assertEquals(10, BuildingType.TREATMENT_PLANT.fouls)
    }

    private fun City.setYear(y: Int) = City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(this, y)

    @Test
    fun oldBrickBecomesHeritage() {
        val c = city()
        c.everything = true
        c.apply(Action.PlaceZone(10, 30, 10, 30, Zone.RESIDENTIAL))
        c.days(60)
        val b = c.buildingAt(10, 30) ?: return
        b.underway = 0
        b.type = BuildingType.LARGE_HOUSE
        b.built = Ageing.monthOf(1910, 0)
        c.setYear(1960)
        assertFalse(c.isHeritage(b), "not before 1970")
        c.setYear(1975)
        assertTrue(c.isHeritage(b))
        b.type = BuildingType.COTTAGE
        assertFalse(c.isHeritage(b), "a cottage isn't built to last")
        b.type = BuildingType.LARGE_HOUSE
        b.built = Ageing.monthOf(1950, 0)
        assertFalse(c.isHeritage(b), "too new")
    }

    @Test
    fun closedWorksLeaveBrownfieldToCleanUp() {
        fun closeWorks(year: Int): City {
            val c = city()
            c.apply(Action.PlaceZone(10, 21, 12, 22, Zone.INDUSTRIAL))
            var guard = 0
            while (c.buildingAt(10, 21)?.let { it.underway == 0 } != true && guard++ < 400) c.tick()
            c.setYear(year)
            c.call("shrink", c.buildingAt(10, 21)!!)
            return c
        }
        val early = closeWorks(1920)
        assertEquals(0, early.map.brownfield[early.map.index(10, 21)].toInt(), "not before 1960")
        val c = closeWorks(1965)
        val i = c.map.index(10, 21)
        assertEquals(1, c.map.brownfield[i].toInt())
        @Suppress("UNCHECKED_CAST")
        val options = c.call("choices", null as Building?, i, Zone.INDUSTRIAL, 100) as List<BuildingType>
        assertTrue(options.isEmpty(), "nothing goes up on it")
        val plan = c.apply(Action.Bulldoze(10, 21, 10, 21))
        assertTrue(plan.cost >= Prices.CLEAN_UP)
        assertEquals(0, c.map.brownfield[i].toInt())
    }

    @Test
    fun agesAndRepairsSurviveASave() {
        val c = city()
        c.days(40)
        val at = c.map.index(10, 20)
        c.call("fail", at, Broken.SEWER, Balance.MEND_SEWER, Balance.REPAIR_SEWER)
        val loaded = SaveGame.read(SaveGame.write(c))
        assertTrue(c.map.waterLaid.contentEquals(loaded.map.waterLaid))
        assertTrue(c.map.roadLaid.contentEquals(loaded.map.roadLaid))
        assertEquals(c.map.tileFix(at), loaded.map.tileFix(at))
        c.days(30)
        loaded.days(30)
        assertEquals(c.map.hash(), loaded.map.hash())
        assertEquals(0, loaded.map.broken[at].toInt())
    }

    @Test
    fun olderSavesGuessTheirAges() {
        val c = SaveGame.read(javaClass.getResourceAsStream("/saves/v6.infill")!!.readBytes())
        val m = c.map
        val road = (0 until m.size).first { m.road[it] != Road.NONE }
        assertEquals((c.monthNow / 2).toShort(), m.roadLaid[road])
        c.days(60)
        assertTrue(c.stats.population > 0)
    }
}
