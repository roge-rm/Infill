package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PeopleTest {
    private fun city(seed: Long = 5): City {
        val c = City(seed, 64, 64, TerrainOptions(water = 0, trees = 0, river = false))
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        return c
    }

    /** Homes along a street with shops and works across it, and room in the middle of the homes for a school or a doctor. */
    private fun town(seed: Long = 5, extras: City.() -> Unit = {}): City {
        val c = city(seed)
        val m = c.map
        c.apply(Action.BuildRoad(Action.roadPath(m, 20, 30, 63, 30, true), RoadType.STREET))
        c.apply(Action.BuildRoad(Action.roadPath(m, 26, 10, 26, 50, false), RoadType.STREET))
        c.extras()
        c.apply(Action.PlaceZone(28, 22, 32, 29, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(35, 22, 40, 29, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(27, 10, 40, 21, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(21, 31, 25, 50, Zone.COMMERCIAL))
        c.apply(Action.PlaceZone(27, 31, 40, 50, Zone.INDUSTRIAL))
        return c
    }

    private fun City.months(n: Int, each: () -> Unit = {}) {
        repeat(n) {
            val m = month
            while (month == m) tick()
            each()
        }
    }

    /** The share of adults with some schooling in the homes south of y 22, round the school's lot. */
    private fun City.schooledShare(): Int {
        val near = homes.filter { it.y >= 22 }
        val adults = near.sumOf { it.people!!.adults }
        val schooled = near.sumOf { it.people!!.schooled[Education.SCHOOLED] + it.people!!.schooled[Education.EDUCATED] }
        return if (adults == 0) 0 else schooled * 100 / adults
    }

    @Test
    fun everyoneIsCountedOnce() {
        val c = town()
        c.months(60)
        val s = c.stats
        assertTrue(s.population > 100, "only ${s.population} people")
        assertEquals(c.homes.sumOf { it.people!!.size }, s.population)
        assertEquals(s.population, s.children + s.adults + s.elderly)
        assertEquals(s.population, s.byWealth.sum())
        for (b in c.homes) {
            val h = b.people!!
            assertEquals(h.adults, h.schooled.sum(), "schooling for ${b.type} at ${b.x}, ${b.y}")
            assertTrue(h.size <= b.type.capacity)
            assertTrue(h.children >= 0 && h.adults >= 0 && h.elderly >= 0)
        }
    }

    @Test
    fun peopleAreBornAndDie() {
        val c = town()
        var births = 0
        var deaths = 0
        c.months(120) { births += c.stats.births; deaths += c.stats.deaths }
        assertTrue(births > 0 && deaths > 0, "$births born, $deaths died")
        assertTrue(births > deaths, "a young town grows: $births born, $deaths died")
    }

    @Test
    fun aSchoolSchoolsTheChildren() {
        val without = town()
        val with = town { apply(Action.PlaceBuilding(BuildingType.SCHOOL, 33, 28)) }
        without.months(240)
        with.months(240)
        assertTrue(with.stats.pupils > 0)
        assertTrue(with.schooledShare() >= without.schooledShare() + 8, "${with.schooledShare()}% schooled with a school, ${without.schooledShare()}% without")
    }

    @Test
    fun aClinicMakesPeopleHealthier() {
        val without = town()
        val with = town { apply(Action.PlaceBuilding(BuildingType.CLINIC, 33, 29)) }
        without.months(36)
        with.months(36)
        assertTrue(with.stats.cared > 0)
        assertTrue(with.stats.health > without.stats.health + 5, "health ${with.stats.health} with a clinic, ${without.stats.health} without")
    }

    @Test
    fun businessesWaitForTheSkillsTheyNeed() {
        val c = town()
        c.months(24)
        val short = City::class.java.getDeclaredMethod("skillsShort", BuildingType::class.java).apply { isAccessible = true }
        val shortage = City::class.java.getDeclaredField("skillShortage").apply { isAccessible = true }.get(c) as IntArray
        shortage.fill(0)
        assertEquals(false, short.invoke(c, BuildingType.BANK))
        shortage[Education.EDUCATED] = 60
        assertEquals(true, short.invoke(c, BuildingType.BANK), "a bank needs educated clerks")
        assertEquals(false, short.invoke(c, BuildingType.WORKSHOP), "a workshop doesn't")
        shortage[Education.EDUCATED] = 0
        shortage[Education.UNSCHOOLED] = 60
        assertEquals(true, short.invoke(c, BuildingType.WORKSHOP), "but it needs hands")
    }

    /** The town's homes all empty, as if the last of each had died. */
    private fun City.emptyAll() {
        for (b in homes) {
            val h = b.people!!
            h.children = 0; h.adults = 0; h.elderly = 0
            h.schooled.fill(0)
        }
    }

    /** The share of empty homes sold in a month with [looking] in a hundred people looking for a home. */
    private fun soldInAMonth(looking: Int): Int {
        val c = town()
        c.months(36)
        c.emptyAll()
        val all = c.homes.size
        c.stats.homeSeekers = c.stats.population.coerceAtLeast(100) * looking / 100
        c.sell(c.homes)
        return c.homes.count { !it.people!!.empty } * 100 / all
    }

    @Test
    fun emptyHomesSellQuicklyWhenWantedAndSlowlyWhenNot() {
        val wanted = soldInAMonth(15)
        val quiet = soldInAMonth(0)
        val glut = soldInAMonth(-10)
        assertTrue(wanted > 60, "$wanted% sold in a month when many are looking")
        assertTrue(quiet in 5..25, "$quiet% sold when few are")
        assertTrue(glut < 8, "$glut% sold with too many homes")
    }

    @Test
    fun whenTheLastGrownUpDiesTheHomeEmpties() {
        val c = town()
        c.months(36)
        val b = c.homes.first { it.people!!.children > 0 }
        val h = b.people!!
        h.adults = 0; h.elderly = 0
        h.schooled.fill(0)
        // Slow sales, so it's still for sale after a month.
        c.months(1)
        assertTrue(h.empty, "the children went to family")
        assertTrue(c.stats.emptied >= 1)
        assertTrue(c.stats.emptyHomes >= 1)
        var months = 0
        while (h.empty && months < 240) {
            c.months(1)
            months++
        }
        assertTrue(!h.empty, "it sold in the end")
        assertEquals(b.type.capacity, h.size, "to a household that fills it")
    }

    @Test
    fun peopleCarryOnAfterASave() {
        val c = town { apply(Action.PlaceBuilding(BuildingType.SCHOOL, 33, 28)) }
        c.months(30)
        c.emptyAll()
        val loaded = SaveGame.read(SaveGame.write(c))
        for (b in c.homes) {
            val h = b.people!!
            val l = loaded.building(b.id)!!.people!!
            assertEquals(listOf(h.children, h.adults, h.elderly, h.wealth, h.schooling, h.health, h.forSale), listOf(l.children, l.adults, l.elderly, l.wealth, l.schooling, l.health, l.forSale))
        }
        c.months(24)
        loaded.months(24)
        assertEquals(c.stats.population, loaded.stats.population)
        assertEquals(c.stats.peopleNumbers().toList(), loaded.stats.peopleNumbers().toList())
    }

    @Test
    fun aVersionFourSaveFindsItsPeople() {
        val c = SaveGame.read(javaClass.getResourceAsStream("/saves/v4.infill")!!.readBytes())
        assertEquals(270, c.stats.population)
        assertEquals(c.stats.population, c.homes.sumOf { it.people!!.size })
        assertTrue(c.stats.workers > 0)
        c.months(3)
        assertTrue(c.stats.population > 0)
    }
}
