package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalloutTest {
    @Test
    fun servicesSendVehiclesAlongTheRoads() {
        val c = City(41, 128, 128, TerrainOptions(water = 10, trees = 30, river = false))
        val p = Player(c, false) {}
        p.start()
        while (c.year < 1935) {
            repeat(Balance.DEMAND_DAYS) { c.tick() }
            p.week()
            c.takeEvents { }
        }
        val seen = HashMap<CalloutKind, Int>()
        repeat(60) {
            c.tick()
            for (k in c.callouts) seen[k.kind] = (seen[k.kind] ?: 0) + 1
        }
        println("callouts over 60 days: $seen")
        assertTrue((seen[CalloutKind.GARBAGE] ?: 0) > 0)
        assertTrue((seen[CalloutKind.POLICE] ?: 0) > 0)
        for (k in c.callouts) {
            val r = k.route
            // Road all the way, a tile at a time.
            for (j in r.indices) assertTrue(c.map.road[r[j]] != Road.NONE)
            for (j in 1 until r.size) {
                val d = kotlin.math.abs(r[j] % 128 - r[j - 1] % 128) + kotlin.math.abs(r[j] / 128 - r[j - 1] / 128)
                assertEquals(1, d)
            }
            for (s in k.stops) assertTrue(s in 0 until r.size - 1)
        }
        // A fire: an engine's sent, and comes back.
        val home = (0 until c.map.size).asSequence().mapNotNull { c.building(c.map.building[it]) }
            .first { it.people != null && it.burning == 0 && c.map.fireCover[c.map.index(it.x, it.y)].toInt() and 0xff > 200 }
        City::class.java.getDeclaredMethod("ignite", Building::class.java).apply { isAccessible = true }.invoke(c, home)
        c.tick()
        val engine = c.callouts.single { it.kind == CalloutKind.FIRE && it.at == c.map.index(home.x, home.y) }
        assertTrue(engine.urgent)
        assertEquals(1, engine.stops.size)
        assertEquals(engine.route.first(), engine.route.last())
    }
}
