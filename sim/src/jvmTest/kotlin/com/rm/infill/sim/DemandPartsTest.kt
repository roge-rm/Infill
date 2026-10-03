package com.rm.infill.sim

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class DemandPartsTest {
    @Test
    fun thePartsAddUpToTheDemand() {
        val c = City(5, 64, 64, TerrainOptions(water = 0, trees = 0, river = false)).also { it.disasterLevel = 0 }
        val m = c.map
        c.apply(Action.BuildRoad(Action.roadPath(m, 0, 30, 60, 30, true)))
        c.apply(Action.PlaceZone(2, 27, 58, 29, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(2, 31, 30, 33, Zone.COMMERCIAL))
        c.apply(Action.PlaceZone(32, 31, 58, 33, Zone.INDUSTRIAL))
        repeat(24) { repeat(31) { c.tick() } }
        val s = c.stats
        val parts = c.demandParts().associateBy { it.zone }
        val wanted = mapOf(
            Zone.RESIDENTIAL to s.residentialDemand, Zone.COMMERCIAL to s.commercialDemand, Zone.INDUSTRIAL to s.industryDemand,
            Zone.OFFICE to s.officeDemand, Zone.FARMLAND to s.farmDemand,
        )
        for ((zone, demand) in wanted) {
            val z = parts.getValue(zone)
            assertTrue(abs(z.total - demand) <= 1, "zone $zone: parts say ${z.total}, the sim $demand")
            // What's shown adds up to the total, the tax's share and all.
            assertTrue(abs(z.parts.sumOf { it.amount } - z.total) <= 1, "zone $zone")
        }
        // Homes are wanted for the workers the jobs need, less the people already here.
        val homes = parts.getValue(Zone.RESIDENTIAL).parts.map { it.source }
        assertTrue(DemandSource.WORKERS_NEEDED in homes && DemandSource.LIVING_HERE in homes)
    }
}
