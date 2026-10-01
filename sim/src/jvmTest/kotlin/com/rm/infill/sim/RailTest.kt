package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RailTest {
    private fun city(size: Int = 32) = City(1, size, size, TerrainOptions(water = 0, trees = 0, river = false))

    private fun City.rail(x0: Int, y0: Int, x1: Int, y1: Int, acrossFirst: Boolean = true): Plan =
        apply(Action.BuildRail(Action.roadPath(map, x0, y0, x1, y1, acrossFirst)))

    private fun City.road(x0: Int, y0: Int, x1: Int, y1: Int, type: RoadType = RoadType.STREET, acrossFirst: Boolean = true): Plan =
        apply(Action.BuildRoad(Action.roadPath(map, x0, y0, x1, y1, acrossFirst), type))

    private fun City.i(x: Int, y: Int) = map.index(x, y)

    @Test
    fun trackCostsAndBridgesStraight() {
        val c = city()
        for (y in 0 until 32) c.map.terrain[c.i(16, y)] = Terrain.WATER
        val plan = c.rail(10, 5, 20, 5)
        assertTrue(plan.ok)
        assertEquals(10 * Prices.RAIL + Prices.RAIL * Prices.BRIDGE, plan.cost)
        assertEquals(Rail.TRACK, c.map.rail[c.i(16, 5)])
        // No turning on a bridge.
        val corner = c.plan(Action.BuildRail(Action.roadPath(c.map, 12, 10, 16, 14, true)))
        assertEquals(listOf(c.i(16, 10)), corner.blocked.toList())
    }

    @Test
    fun trackCrossesRoadsOnlyStraightAcross() {
        val c = city()
        c.road(4, 10, 24, 10)
        // Straight across: a level crossing.
        assertTrue(c.rail(12, 4, 12, 16, acrossFirst = false).ok)
        assertEquals(Rail.TRACK, c.map.rail[c.i(12, 10)])
        assertEquals(RoadType.STREET.id, c.map.road[c.i(12, 10)])
        // Along the road: blocked.
        val along = c.plan(Action.BuildRail(Action.roadPath(c.map, 14, 10, 18, 10, true)))
        assertEquals(5, along.blocked.size)
        // A road drawn along the track can't share it either.
        val roadAlong = c.plan(Action.BuildRoad(Action.roadPath(c.map, 12, 12, 12, 14, false), RoadType.DIRT))
        assertEquals(3, roadAlong.blocked.size)
        // But a new road straight across it can.
        assertTrue(c.road(8, 14, 16, 14).ok)
        assertEquals(Rail.TRACK, c.map.rail[c.i(12, 14)])
    }

    @Test
    fun bulldozeAndUndoTakeTrack() {
        val c = city()
        c.rail(4, 5, 13, 5)
        c.apply(Action.Bulldoze(6, 5, 7, 5))
        assertEquals(Rail.NONE, c.map.rail[c.i(6, 5)])
        c.undo()
        assertEquals(Rail.TRACK, c.map.rail[c.i(6, 5)])
    }

    @Test
    fun zonesAndBuildingsKeepOffTheTrack() {
        val c = city()
        c.rail(4, 5, 13, 5)
        val zone = c.plan(Action.PlaceZone(4, 4, 13, 6, Zone.RESIDENTIAL))
        assertEquals(10, zone.blocked.size)
        assertEquals(Problem.Blocked, c.plan(Action.PlaceBuilding(BuildingType.POLICE_STATION, 6, 5)).problem)
    }

    @Test
    fun stationsGoBesideTheTrack() {
        val c = city()
        c.rail(4, 10, 20, 10)
        assertEquals(Problem.NeedsTrack, c.plan(Action.PlaceBuilding(BuildingType.STATION, 6, 14)).problem)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.STATION, 6, 11)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.FREIGHT_YARD, 12, 8)).ok)
        c.rail(24, 4, 24, 20, acrossFirst = false)
        // Lying the wrong way for the track.
        assertEquals(Problem.NeedsTrack, c.plan(Action.PlaceBuilding(BuildingType.STATION, 25, 5)).problem)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.STATION_NS, 25, 5)).ok)
        assertEquals(Problem.NeedsTrack, c.plan(Action.PlaceBuilding(BuildingType.STATION_NS, 27, 12)).problem)
    }

    /**
     * Homes at one end of a long road and jobs at the other, with or without a
     * railway between stations at each end.
     */
    private fun commuterTown(train: Boolean): City {
        val c = City(3, 96, 32, TerrainOptions(water = 0, trees = 0, river = false))
        c.road(0, 16, 95, 16, RoadType.DIRT)
        c.apply(Action.PlaceZone(2, 17, 12, 18, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(82, 17, 93, 18, Zone.INDUSTRIAL))
        if (train) {
            c.rail(0, 13, 95, 13)
            c.apply(Action.PlaceBuilding(BuildingType.STATION, 6, 14))
            c.apply(Action.PlaceBuilding(BuildingType.STATION, 86, 14))
        }
        repeat(2 * 365) { c.tick() }
        return c
    }

    @Test
    fun trainsShortenLongCommutes() {
        val road = commuterTown(train = false)
        val rail = commuterTown(train = true)
        assertTrue(rail.stats.commute < road.stats.commute, "by train ${rail.stats.commute} min, by road ${road.stats.commute} min")
        val station = rail.buildingAt(6, 14)!!
        assertTrue(rail.riders(station) > 0, "no one took the train")
        assertTrue(rail.railLinked(station))
        assertTrue(rail.trainRoutes.any { it.passengers && it.load > 0 })
        assertTrue(rail.map.railBusy[rail.i(50, 13)] > 0)
    }

    @Test
    fun aFreightYardShipsAndGrowsTheMarket() {
        fun town(yard: Boolean): City {
            val c = City(4, 96, 32, TerrainOptions(water = 0, trees = 0, river = false))
            c.road(0, 16, 60, 16, RoadType.DIRT)
            c.apply(Action.PlaceZone(40, 17, 58, 18, Zone.INDUSTRIAL))
            c.apply(Action.PlaceZone(4, 14, 30, 15, Zone.RESIDENTIAL))
            if (yard) {
                c.rail(40, 20, 95, 20)
                c.apply(Action.PlaceBuilding(BuildingType.FREIGHT_YARD, 44, 21))
                c.road(45, 17, 45, 20, RoadType.DIRT, acrossFirst = false)
            }
            repeat(3 * 365) { c.tick() }
            return c
        }
        val without = town(yard = false)
        val with = town(yard = true)
        val yard = with.buildingAt(44, 21)!!
        assertTrue(with.freightSent(yard) > 0, "nothing went by train")
        // The market, whether or not there are the hands to work it yet.
        fun City.market() = stats.industryJobs + stats.industryDemand
        assertTrue(with.market() > without.market(), "${with.market()} vs ${without.market()}")
    }

    @Test
    fun aRailTownSavesAndCarriesOn() {
        val original = commuterTown(train = true)
        repeat(12) { original.tick() }
        val loaded = SaveGame.read(SaveGame.write(original))
        assertTrue(original.map.rail.contentEquals(loaded.map.rail))
        repeat(400) {
            original.tick()
            loaded.tick()
        }
        assertEquals(original.map.hash(), loaded.map.hash())
        assertEquals(original.funds, loaded.funds)
        assertTrue(original.map.railBusy.contentEquals(loaded.map.railBusy))
        assertEquals(original.trainRoutes.size, loaded.trainRoutes.size)
    }

    @Test
    fun aVersionTwoSaveStillLoads() {
        val c = SaveGame.read(javaClass.getResourceAsStream("/saves/v2.infill")!!.readBytes())
        assertEquals("Twoford", c.name)
        assertEquals(150, c.stats.population)
        assertEquals(Heading.SOUTH, c.map.roadHeading[c.map.index(20, 30)])
        assertFalse(c.map.rail.any { it != Rail.NONE })
        repeat(70) { c.tick() }
        assertTrue(c.stats.population > 0)
    }
}
