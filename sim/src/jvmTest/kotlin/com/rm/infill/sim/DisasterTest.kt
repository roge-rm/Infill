package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertTrue

class DisasterTest {
    @Test
    fun eachDisasterCanBeStartedOnPurpose() {
        val c = City(53, 96, 96, TerrainOptions(water = 10, trees = 30, river = false, sea = Sea.ONE_SIDE))
        val p = Player(c, false) {}
        p.start()
        while (c.year < 1922) {
            repeat(Balance.DEMAND_DAYS) { c.tick() }
            p.week()
            c.takeEvents { }
        }
        // Whatever the setting.
        c.disasterLevel = 0
        val news = mapOf(
            DisasterKind.FIRE to EventKind.FireStarted, DisasterKind.GALE to EventKind.Gale, DisasterKind.STORM_SURGE to EventKind.StormSurge,
            DisasterKind.EARTHQUAKE to EventKind.Earthquake, DisasterKind.EPIDEMIC to EventKind.Epidemic, DisasterKind.ACCIDENT to EventKind.IndustrialAccident,
        )
        // The surge first, before a flood leaves the shore under water already.
        for (kind in listOf(DisasterKind.STORM_SURGE) + DisasterKind.entries.filter { it != DisasterKind.STORM_SURGE }) {
            assertTrue(c.canStart(kind), "$kind can't start")
            c.takeEvents { }
            assertTrue(c.startDisaster(kind), "$kind didn't start")
            val told = ArrayList<EventKind>()
            c.takeEvents { told += it.kind }
            news[kind]?.let { assertTrue(it in told, "$kind: $told") }
        }
        // No sea, no surge.
        assertTrue(!City(5, 32, 32, TerrainOptions(water = 0, trees = 0, river = false)).canStart(DisasterKind.STORM_SURGE))
    }
}
