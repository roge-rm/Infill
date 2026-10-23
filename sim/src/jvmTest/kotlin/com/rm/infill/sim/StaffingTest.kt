package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StaffingTest {
    private fun City.months(n: Int) = repeat(n) { repeat(31) { tick() } }

    private fun works(c: City, y0: Int, y1: Int) = c.allBuildings.filter { it.type.zone == Zone.INDUSTRIAL && it.y in y0..y1 && it.underway == 0 }

    /** Homes and works on a main street, and more works on a street of their own with no road to the homes. */
    private fun apartTown(): City {
        val c = City(1900)
        val m = c.map
        c.apply(Action.BuildRoad(Action.roadPath(m, 0, 64, 100, 64, true)))
        c.apply(Action.PlaceZone(10, 60, 100, 63, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(10, 65, 60, 67, Zone.INDUSTRIAL))
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 105, 66))
        c.apply(Action.BuildPowerLine(Action.roadPath(m, 104, 66, 10, 59, false)))
        c.apply(Action.BuildRoad(Action.roadPath(m, 20, 20, 80, 20, true)))
        c.apply(Action.PlaceZone(20, 21, 80, 23, Zone.INDUSTRIAL))
        return c
    }

    @Test
    fun unreachedWorksMakeNothingAndComeDown() {
        val c = apartTown()
        // Till some of the works apart have been short long enough.
        var months = 0
        while (months < 24 && works(c, 18, 30).none { it.understaffed >= Balance.STAFF_MONTHS }) {
            c.months(1)
            months++
        }
        val linked = works(c, 60, 70)
        val apart = works(c, 18, 30)
        assertTrue(linked.isNotEmpty() && apart.isNotEmpty(), "${linked.size} linked, ${apart.size} apart")
        // Works its workers reach make their goods; ones they can't make nothing.
        assertTrue(linked.any { (c.output(it)?.second ?: 0) > 0 })
        for (b in apart) assertEquals(0, c.output(b)?.second ?: 0, "at ${b.x}, ${b.y}")
        // Under staffed long enough, they stop growing and come down.
        val long = apart.filter { it.understaffed >= Balance.STAFF_MONTHS }
        assertTrue(long.isNotEmpty(), "counts ${apart.map { it.understaffed }}")
        // An advice line says why.
        c.updateAdvice()
        assertTrue(c.advice.any { it.kind == AdviceKind.UNREACHED }, "${c.advice.map { it.kind }}")
        val before = long.associate { it.id to it.type.capacity }
        c.months(12)
        val after = before.keys.map { id -> c.allBuildings.firstOrNull { it.id == id }?.type?.capacity ?: 0 }
        assertTrue(before.values.zip(after).all { (b, a) -> a <= b }, "none grew: $before to $after")
        assertTrue(before.values.sum() > after.sum(), "some came down: $before to $after")
        // Works with their workers aren't counted as short.
        for (b in works(c, 60, 70)) if (c.staffShareOf(b) >= Balance.STAFF_LOW) assertEquals(0, b.understaffed)
        assertTrue(works(c, 18, 30).all { c.staffShort(it) == null || c.staffShort(it) == StaffShort.UNREACHED })
    }

    /** Works going up on the street with no road to the homes over [days] days: ones first seen being built. */
    private fun builds(c: City, days: Int, seen: HashSet<Int>): Int {
        var n = 0
        repeat(days) {
            c.tick()
            for (b in c.allBuildings) if (b.type.zone == Zone.INDUSTRIAL && b.y in 18..30 && seen.add(b.id) && b.underway > 0) n++
        }
        return n
    }

    @Test
    fun unreachedWorksDontKeepRebuildingUntilARoadLinksThem() {
        val c = apartTown()
        val seen = HashSet<Int>()
        val first = builds(c, 6 * 31, seen)
        assertTrue(first > 0)
        // Once measured out of reach, nothing more goes up there.
        val later = builds(c, 3 * 365, seen)
        println("apart: $first built the first six months, $later in the next three years")
        assertTrue(later <= 2, "$later built in three years")
        val m = c.map
        assertEquals(AdviceKind.UNREACHED, c.whyNotAt(m.index(40, 21)))
        // A road to the homes lets it grow again.
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 20, 20, 5, 20, true))).ok)
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 5, 20, 5, 64, true))).ok)
        val linked = builds(c, 31, seen)
        assertTrue(c.whyNotAt(m.index(40, 21)) != AdviceKind.UNREACHED)
        val more = linked + builds(c, 3 * 31, seen)
        println("apart: $linked built the month after linking, $more in four months")
        assertTrue(more > 0, "nothing built in four months once linked")
    }

    @Test
    fun farWorkInATownShortOfWorkersIsShortOfWorkersNotCutOff() {
        val c = City(1900)
        val m = c.map
        // A few homes at one end of a long street, and far more work along it, the furthest an hour's walk away.
        c.apply(Action.BuildRoad(Action.roadPath(m, 0, 64, 110, 64, true)))
        c.apply(Action.PlaceZone(10, 60, 30, 63, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(31, 65, 70, 67, Zone.INDUSTRIAL))
        c.apply(Action.PlaceZone(31, 60, 70, 63, Zone.COMMERCIAL))
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 105, 66))
        c.apply(Action.BuildPowerLine(Action.roadPath(m, 104, 66, 10, 59, false)))
        c.months(24)
        val work = c.allBuildings.filter { (it.type.zone == Zone.INDUSTRIAL || it.type.zone == Zone.COMMERCIAL) && it.underway == 0 }
        val short = work.filter { c.staffShareOf(it) < Balance.STAFF_WARN }
        println("short of workers: ${work.size} work buildings, ${short.size} short, staffed ${work.sortedBy { it.x }.map { "${it.x}:${c.staffShareOf(it)}" }}")
        assertTrue(short.isNotEmpty(), "none short")
        for (b in short) assertTrue(c.staffShort(b) != StaffShort.UNREACHED, "at ${b.x}, ${b.y}")
        for (x in 31..70) assertTrue(!c.unreachedRoad(m.index(x, 64)), "road at $x")
        for (x in 31..70) assertTrue(c.whyNotAt(m.index(x, 66)) != AdviceKind.UNREACHED, "lot at $x")
    }

    /** Homes on the west bank, works on an island to the east, a bridge between. */
    private fun islandTown(): City {
        val c = City(17, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.disasterLevel = 0 }
        val m = c.map
        for (y in 0 until 64) for (x in 30..32) m.terrain[m.index(x, y)] = Terrain.WATER
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 55, 30, true))).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 26, 28, 29, Zone.RESIDENTIAL)).ok)
        assertTrue(c.apply(Action.PlaceZone(35, 31, 55, 33, Zone.INDUSTRIAL)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 5, 33)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 7, 32, 7, 25, false))).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 50, 36)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 50, 35, 50, 34, false))).ok)
        return c
    }

    @Test
    fun anIslandBehindAShutBridgeIsCutOffTillItOpens() {
        val c = islandTown()
        val m = c.map
        c.months(18)
        val island = m.index(45, 30)
        assertTrue(!c.unreachedRoad(island))
        assertTrue(c.allBuildings.any { it.type.zone == Zone.INDUSTRIAL }, "no works")
        // Shut, nobody can get there.
        val bridge = intArrayOf(m.index(30, 30), m.index(31, 30), m.index(32, 30))
        assertTrue(c.apply(Action.SetBridge(bridge, shut = true)).ok)
        c.months(2)
        assertTrue(c.unreachedRoad(island))
        val works = c.allBuildings.filter { it.type.zone == Zone.INDUSTRIAL && it.underway == 0 }
        for (b in works) assertEquals(StaffShort.UNREACHED, c.staffShort(b), "at ${b.x}, ${b.y}")
        // Open again, it's reached within a month or two.
        assertTrue(c.apply(Action.SetBridge(bridge, shut = false)).ok)
        c.months(2)
        assertTrue(!c.unreachedRoad(island))
        for (x in 35..55) assertTrue(c.whyNotAt(m.index(x, 31)) != AdviceKind.UNREACHED, "lot at $x")
    }

    @Test
    fun theMonthsShortOfStaffKeepThroughASave() {
        val c = apartTown()
        c.months(7)
        val counts = c.allBuildings.filter { it.understaffed > 0 }.associate { it.id to it.understaffed }
        assertTrue(counts.isNotEmpty())
        val back = SaveGame.read(SaveGame.write(c))
        assertEquals(counts, back.allBuildings.filter { it.understaffed > 0 }.associate { it.id to it.understaffed })
        c.months(1)
        back.months(1)
        assertTrue(SaveGame.write(c).contentEquals(SaveGame.write(back)), "saves differ after a month")
    }

    @Test
    fun workThatCameDownShortOfStaffComesBackWhenStaffed() {
        val c = islandTown()
        val m = c.map
        c.months(36)
        val grown = c.allBuildings.filter { it.type.zone == Zone.INDUSTRIAL && it.underway == 0 && it.type.previous != null }
        assertTrue(grown.isNotEmpty(), "no works past the first rung")
        // Shut the bridge till some come down a rung for want of staff.
        val bridge = intArrayOf(m.index(30, 30), m.index(31, 30), m.index(32, 30))
        assertTrue(c.apply(Action.SetBridge(bridge, shut = true)).ok)
        var months = 0
        while (months < 24 && c.allBuildings.none { it.shrunkShort }) {
            c.months(1)
            months++
        }
        assertTrue(c.allBuildings.any { it.shrunkShort }, "none came down in $months months")
        // Still short, it's still held back, and it says why.
        c.months(2)
        val ids = c.allBuildings.filter { it.shrunkShort }.map { it.id }.toSet()
        assertTrue(ids.isNotEmpty())
        for (b in c.allBuildings.filter { it.id in ids }) assertTrue(c.whyNotAt(m.index(b.x, b.y)) != null, "at ${b.x}, ${b.y}")
        // The flag keeps through a save.
        val back = SaveGame.read(SaveGame.write(c))
        assertEquals(ids, back.allBuildings.filter { it.shrunkShort }.map { it.id }.toSet())
        // Open again and staffed for a month, it may grow.
        assertTrue(c.apply(Action.SetBridge(bridge, shut = false)).ok)
        c.months(3)
        val staffed = c.allBuildings.filter { it.id in ids && c.staffShareOf(it) >= Balance.STAFF_LOW }
        assertTrue(staffed.isNotEmpty())
        for (b in staffed) assertTrue(!b.shrunkShort, "at ${b.x}, ${b.y}")
    }

    @Test
    fun workThatCameDownShortOfStaffDoesntGrowWhileShort() {
        val c = City(1900)
        val m = c.map
        // A few homes at one end of a long street and more work along it than they can staff.
        c.apply(Action.BuildRoad(Action.roadPath(m, 0, 64, 110, 64, true)))
        c.apply(Action.PlaceZone(10, 60, 30, 63, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(31, 65, 70, 67, Zone.INDUSTRIAL))
        c.apply(Action.PlaceZone(31, 60, 70, 63, Zone.COMMERCIAL))
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 105, 66))
        c.apply(Action.BuildPowerLine(Action.roadPath(m, 104, 66, 10, 59, false)))
        c.months(24)
        // As if each had just come down a rung short of staff.
        val work = c.allBuildings.filter { paysByStaffType(it.type) && it.underway == 0 }
        for (b in work) { b.shrunkShort = true; b.understaffed = 0 }
        val sizes = work.associate { it.id to it.type }
        var grewShort = 0
        repeat(3) {
            c.months(1)
            for (b in c.allBuildings) if (b.id in sizes && b.type != sizes[b.id] && b.shrunkShort) grewShort++
        }
        val short = c.allBuildings.filter { it.id in sizes && c.staffShareOf(it) < Balance.STAFF_LOW }
        val staffed = c.allBuildings.filter { it.id in sizes && c.staffShareOf(it) >= Balance.STAFF_LOW }
        println("held back: ${short.size} short, ${staffed.size} staffed, $grewShort grew while short")
        assertTrue(short.isNotEmpty() && staffed.isNotEmpty())
        assertEquals(0, grewShort)
        // Short, it keeps the mark before its months are up, and says why it can't grow.
        for (b in short) {
            assertTrue(b.shrunkShort && b.understaffed < Balance.STAFF_MONTHS, "at ${b.x}, ${b.y}")
            assertEquals(AdviceKind.FEW_WORKERS, c.whyNotAt(m.index(b.x, b.y)), "at ${b.x}, ${b.y}")
        }
        for (b in staffed) assertTrue(!b.shrunkShort, "at ${b.x}, ${b.y}")
    }

    private fun paysByStaffType(t: BuildingType) = t.zone == Zone.INDUSTRIAL || t.zone == Zone.COMMERCIAL

    @Test
    fun anOldSaveStartsWithNoMonthsShort() {
        val c = SaveGame.read(javaClass.getResourceAsStream("/saves/v30.infill")!!.readBytes())
        for (b in c.allBuildings) assertEquals(0, b.understaffed)
        // Nothing reads short in its first month back.
        c.months(1)
        for (b in c.allBuildings) assertTrue(b.understaffed <= 1)
    }

    @Test
    fun aServiceShortOfStaffIsWeakerButNotBelowTheFloor() {
        val c = City(1, 64, 64, TerrainOptions(water = 0, trees = 0, river = false))
        val m = c.map
        c.everything = true
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 60, 30, true))).ok)
        assertTrue(c.apply(Action.PlaceZone(5, 28, 40, 29, Zone.RESIDENTIAL)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 50, 25)).ok)
        assertTrue(c.apply(Action.BuildPowerLine(Action.roadPath(m, 51, 27, 5, 27, true))).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.SCHOOL, 20, 31)).ok)
        // A second school on a road of its own that nobody can reach.
        assertTrue(c.apply(Action.BuildRoad(Action.roadPath(m, 5, 10, 40, 10, true))).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.SCHOOL, 20, 11)).ok)
        c.months(14)
        val near = c.buildingAt(20, 31)!!
        val far = c.buildingAt(20, 11)!!
        assertEquals(0, c.reached(far))
        assertEquals(Balance.LEAST_STAFF, c.strength(far, 100))
        assertTrue(c.strength(near, 100) > c.strength(far, 100), "${c.strength(near, 100)} near")
        // Short of teachers, it's weaker, down to the floor and no further.
        val shortage = City::class.java.getDeclaredField("skillShortage").apply { isAccessible = true }.get(c) as IntArray
        val full = c.strength(near, 100)
        shortage[Education.EDUCATED] = 80
        assertTrue(c.strength(near, 100) < full)
        shortage.fill(100)
        assertEquals(Balance.LEAST_STAFF, c.strength(near, 100))
        // Funding still counts at the floor.
        assertEquals((40 + 60 * 50 / 100) * Balance.LEAST_STAFF / 100, c.strength(near, 50))
    }
}
