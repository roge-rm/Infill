package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransitTest {
    /** A long street with a power station at one end, and nothing else yet. */
    private fun city(year: Int = 1905): City {
        val c = City(6, 64, 32, TerrainOptions(water = 0, trees = 0, river = false))
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, year)
        c.everything = true
        c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 16, 63, 16, true), RoadType.STREET))
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 30, 19))
        // Power across the street, to the lots north of it.
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 31, 18, 31, 16, false)))
        return c
    }

    private fun City.i(x: Int, y: Int) = map.index(x, y)

    /** A line along the street calling at the stops at [xs], in order. */
    private fun City.line(tram: Boolean, vararg xs: Int, vehicles: Int = 3) =
        apply(Action.AddLine(tram, IntArray(xs.size) { i(xs[it], 16) }, vehicles))

    /** Trips from the west end of the street to the east, everyone on foot or by what runs. */
    private fun City.trips(workers: Int, cars: Int = 0): Traffic {
        val t = City::class.java.getDeclaredField("traffic").apply { isAccessible = true }.get(this) as Traffic
        val n = map.size
        // The networks as they are now.
        City::class.java.getDeclaredMethod("updateNetworks").apply { isAccessible = true }.invoke(this)
        val w = IntArray(n).also { it[i(2, 16)] = workers }
        val c = IntArray(n).also { it[i(2, 16)] = cars }
        val j = IntArray(n).also { it[i(60, 16)] = workers }
        t.newMonth(w, IntArray(n), IntArray(n), j, IntArray(n), 0, carWorkersAt = c)
        t.sendDay(1, 1)
        t.newMonth(IntArray(n), IntArray(n), IntArray(n), IntArray(n), IntArray(n), 0)
        return t
    }

    @Test
    fun withoutCarsPeopleWalkAndDontFillTheRoads() {
        val c = city()
        val t = c.trips(50)
        assertEquals(50, t.workersPlaced)
        assertEquals(50, t.lastModes[Mode.WALK.ordinal])
        assertEquals(0, t.lastVolume[c.i(30, 16)], "walkers aren't traffic")
        assertTrue(t.lastFootfall[c.i(30, 16)] > 0, "but they go by the shops")
        assertEquals(58 * Balance.WALK_TIME, t.commute[c.i(2, 16)])
    }

    @Test
    fun thoseWithCarsDriveAndFillTheRoads() {
        val c = city()
        val t = c.trips(50, cars = 30)
        assertEquals(30, t.lastModes[Mode.CAR.ordinal])
        assertEquals(20, t.lastModes[Mode.WALK.ordinal])
        assertTrue(t.lastVolume[c.i(30, 16)] > 0)
    }

    @Test
    fun aTramIsQuickerThanWalking() {
        val c = city()
        assertTrue(c.apply(Action.BuildTram(Action.roadPath(c.map, 2, 16, 60, 16, true))).ok)
        assertTrue(c.apply(Action.PlaceStop(2, 16, Stop.TRAM)).ok)
        assertTrue(c.apply(Action.PlaceStop(60, 16, Stop.TRAM)).ok)
        assertTrue(c.line(true, 2, 60).ok)
        // No depot, no trams.
        assertEquals(50, c.trips(50).lastModes[Mode.WALK.ordinal])
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.TRAM_DEPOT, 30, 14)).ok, "a depot beside the track")
        val t = c.trips(50)
        assertEquals(50, t.lastModes[Mode.TRAM.ordinal])
        assertTrue(t.commute[c.i(2, 16)] < 58 * Balance.WALK_TIME, "${t.commute[c.i(2, 16)]} s by tram")
        assertTrue(t.lastTramVolume[c.i(30, 16)] > 0)
        assertTrue(t.lastStopRiders[c.i(2, 16)] > 0)
    }

    @Test
    fun aDepotWithoutPowerRunsNoTrams() {
        val c = city()
        c.apply(Action.BuildTram(Action.roadPath(c.map, 2, 16, 60, 16, true)))
        c.apply(Action.PlaceStop(2, 16, Stop.TRAM))
        c.apply(Action.PlaceStop(60, 16, Stop.TRAM))
        c.line(true, 2, 60)
        c.apply(Action.PlaceBuilding(BuildingType.TRAM_DEPOT, 50, 14))
        // Far from the power station, and nothing to carry the power there.
        assertFalse(c.map.powered[c.i(50, 14)])
        assertEquals(0, c.trips(50).lastModes[Mode.TRAM.ordinal])
    }

    @Test
    fun brokenTramTrackStopsTheTramsUntilItsMended() {
        val c = city()
        c.apply(Action.BuildTram(Action.roadPath(c.map, 2, 16, 60, 16, true)))
        c.apply(Action.PlaceStop(2, 16, Stop.TRAM))
        c.apply(Action.PlaceStop(60, 16, Stop.TRAM))
        c.line(true, 2, 60)
        c.apply(Action.PlaceBuilding(BuildingType.TRAM_DEPOT, 30, 14))
        assertEquals(50, c.trips(50).lastModes[Mode.TRAM.ordinal])
        val fail = City::class.java.declaredMethods.first { it.name == "fail" }.apply { isAccessible = true }
        fail.invoke(c, c.i(45, 16), Broken.TRAM, Balance.MEND_TRACK, Balance.REPAIR_TRACK)
        assertTrue(c.map.closed(c.i(45, 16)), "the street's dug up to mend it")
        assertEquals(0, c.trips(50).lastModes[Mode.TRAM.ordinal], "no trams past the break")
        repeat(Balance.MEND_TRACK + 1) { c.tick() }
        assertEquals(50, c.trips(50).lastModes[Mode.TRAM.ordinal], "running again")
        assertTrue(c.map.tramLaid[c.i(45, 16)] >= 0)
    }

    @Test
    fun tramStopsGoOnTrackAndDepotsBesideIt() {
        val c = city()
        assertFalse(c.plan(Action.PlaceStop(10, 16, Stop.TRAM)).ok, "no track there")
        assertEquals(Problem.NeedsTramTrack, c.plan(Action.PlaceBuilding(BuildingType.TRAM_DEPOT, 30, 13)).problem)
        assertFalse(c.plan(Action.BuildTram(Action.roadPath(c.map, 10, 10, 20, 10, true))).ok, "tram track needs a street")
    }

    @Test
    fun busesRunOnTheRoadsFromAGarage() {
        val c = city(1930)
        c.apply(Action.PlaceStop(2, 16, Stop.BUS))
        c.apply(Action.PlaceStop(60, 16, Stop.BUS))
        c.line(false, 2, 60)
        c.apply(Action.PlaceBuilding(BuildingType.BUS_GARAGE, 40, 17))
        val t = c.trips(50)
        assertEquals(50, t.lastModes[Mode.BUS.ordinal])
        assertTrue(t.lastBusVolume[c.i(30, 16)] > 0)
    }

    @Test
    fun trolleybusesRunOnWireFromAPoweredGarage() {
        val c = city(1930)
        c.apply(Action.PlaceStop(2, 16, Stop.BUS))
        c.apply(Action.PlaceStop(60, 16, Stop.BUS))
        c.line(false, 2, 60)
        // Beside the power line, so it has power.
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.BUS_GARAGE, 32, 17)).ok)
        assertEquals(50, c.trips(50).lastModes[Mode.BUS.ordinal], "diesel buses without wire")
        // Wire that doesn't reach the garage runs nothing; the diesel buses carry on.
        c.apply(Action.BuildWire(Action.roadPath(c.map, 2, 16, 20, 16, true)))
        assertEquals(50, c.trips(50).lastModes[Mode.BUS.ordinal], "wire short of the garage")
        assertEquals(-1, c.trolleyNetwork(c.i(10, 16)))
        // Wire all along, past the garage: the street's buses are trolleybuses now.
        c.apply(Action.BuildWire(Action.roadPath(c.map, 2, 16, 60, 16, true)))
        val t = c.trips(50)
        assertTrue(c.trolleyNetwork(c.i(10, 16)) >= 0)
        assertEquals(50, t.lastModes[Mode.TROLLEY.ordinal], "modes ${t.lastModes.toList()}")
        // And no fumes from them.
        repeat(2) { c.trips(2_000) }
        City::class.java.getDeclaredMethod("updatePollution").apply { isAccessible = true }.invoke(c)
        assertEquals(0, c.map.pollution[c.i(10, 14)].toInt() and 0xff)
    }

    @Test
    fun electricRidersMakeThePowerStationWorkHarder() {
        fun plantOutput(riders: Int): Int {
            val c = city()
            c.apply(Action.BuildTram(Action.roadPath(c.map, 2, 16, 60, 16, true)))
            c.apply(Action.PlaceStop(2, 16, Stop.TRAM))
            c.apply(Action.PlaceStop(60, 16, Stop.TRAM))
            c.line(true, 2, 60)
            c.apply(Action.PlaceBuilding(BuildingType.TRAM_DEPOT, 30, 14))
            if (riders > 0) c.trips(riders)
            City::class.java.getDeclaredMethod("updateNetworks").apply { isAccessible = true }.invoke(c)
            return c.stationOutput(c.buildingAt(30, 19)!!)
        }
        assertTrue(plantOutput(4_000) > plantOutput(0), "${plantOutput(4_000)} W with riders, ${plantOutput(0)} W without")
    }

    @Test
    fun theSubwayIsQuickest() {
        val c = city(1920)
        c.apply(Action.BuildTram(Action.roadPath(c.map, 2, 16, 60, 16, true)))
        c.apply(Action.PlaceStop(2, 16, Stop.TRAM))
        c.apply(Action.PlaceStop(60, 16, Stop.TRAM))
        c.apply(Action.PlaceBuilding(BuildingType.TRAM_DEPOT, 30, 14))
        assertTrue(c.apply(Action.BuildSubway(Action.roadPath(c.map, 2, 17, 60, 17, true))).ok)
        assertEquals(Problem.NeedsTunnel, c.plan(Action.PlaceBuilding(BuildingType.SUBWAY_STATION, 2, 18)).problem)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.SUBWAY_STATION, 2, 17)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.SUBWAY_STATION, 60, 17)).ok)
        // Power along the line of stations.
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 2, 18, 60, 18, true)))
        val t = c.trips(50)
        assertEquals(50, t.lastModes[Mode.SUBWAY.ordinal], "modes ${t.lastModes.toList()}")
        assertTrue(t.lastSubwayVolume[c.i(30, 17)] > 0)
    }

    @Test
    fun ridersPayFaresAndTheUpkeepIsPaid() {
        val c = city()
        c.apply(Action.BuildTram(Action.roadPath(c.map, 2, 16, 60, 16, true)))
        c.apply(Action.PlaceStop(2, 16, Stop.TRAM))
        c.apply(Action.PlaceStop(60, 16, Stop.TRAM))
        c.apply(Action.PlaceBuilding(BuildingType.TRAM_DEPOT, 30, 14))
        // A stop by the depot and the power station, where the first jobs are.
        c.apply(Action.PlaceStop(31, 16, Stop.TRAM))
        c.line(true, 2, 31, 60)
        c.apply(Action.PlaceZone(0, 15, 6, 15, Zone.RESIDENTIAL))
        c.apply(Action.PlaceZone(56, 17, 63, 18, Zone.INDUSTRIAL))
        repeat(3 * 365) { c.tick() }
        assertTrue(c.stats.byMode[Mode.TRAM.ordinal] > 0, "modes ${c.stats.byMode.toList()}")
        assertTrue(c.stats.fareIncome > 0)
        assertTrue(c.stats.transitUpkeep > 0)
    }

    /** The pollution beside the street after [workers] commute along it, [cars] of them driving, in [year]. */
    private fun fumes(year: Int, workers: Int, cars: Int): Int {
        val c = city(year)
        // No power station in the way of the reading.
        c.apply(Action.Bulldoze(30, 19, 31, 20))
        repeat(3) { c.trips(workers, cars) }
        City::class.java.getDeclaredMethod("updatePollution").apply { isAccessible = true }.invoke(c)
        return c.map.pollution[c.i(20, 15)].toInt() and 0xff
    }

    @Test
    fun trafficFoulsTheAirAndWalkersDont() {
        assertEquals(0, fumes(1960, 2_000, 0), "walkers")
        val driving = fumes(1960, 2_000, 2_000)
        assertTrue(driving > 0, "drivers")
        assertTrue(fumes(1995, 2_000, 2_000) < driving, "cleaner engines by the 1990s")
        assertTrue(Fumes.level(1905) < Fumes.level(1960))
        assertTrue(Fumes.level(2040) < Fumes.level(1980))
    }

    @Test
    fun carsComeWithTheYearsAndTheWellOffFirst() {
        assertEquals(0, Cars.share(1903, Wealth.MIDDLE))
        assertTrue(Cars.share(1925, Wealth.MIDDLE) in 15..25)
        assertTrue(Cars.share(1965, Wealth.MIDDLE) > 60)
        assertTrue(Cars.share(1930, Wealth.WELL_OFF) > Cars.share(1930, Wealth.POOR))
    }

    @Test
    fun transitSurvivesASaveAndUndo() {
        val c = city()
        val path = Action.roadPath(c.map, 2, 16, 60, 16, true)
        c.apply(Action.BuildTram(path))
        c.apply(Action.PlaceStop(2, 16, Stop.TRAM))
        val loaded = SaveGame.read(SaveGame.write(c))
        assertTrue(c.map.tram.contentEquals(loaded.map.tram))
        assertEquals(Stop.TRAM, loaded.map.stop[c.i(2, 16)].toInt())
        c.undo()
        assertEquals(0, c.map.stop[c.i(2, 16)].toInt())
        c.undo()
        assertEquals(0, c.map.tram[c.i(30, 16)].toInt())
        // Bulldozing the street takes its track with it.
        loaded.apply(Action.Bulldoze(30, 16, 30, 16))
        assertEquals(0, loaded.map.tram[c.i(30, 16)].toInt())
    }

    @Test
    fun stopsNeedALineToBeServed() {
        val c = city(1930)
        c.apply(Action.PlaceStop(2, 16, Stop.BUS))
        c.apply(Action.PlaceStop(60, 16, Stop.BUS))
        c.apply(Action.PlaceBuilding(BuildingType.BUS_GARAGE, 40, 17))
        // A garage and stops, but no line: everyone walks.
        assertEquals(50, c.trips(50).lastModes[Mode.WALK.ordinal])
        assertTrue(c.line(false, 2, 60).ok)
        assertEquals(50, c.trips(50).lastModes[Mode.BUS.ordinal])
        // A line needs stops of its kind, and a way between them.
        assertFalse(c.plan(Action.AddLine(true, intArrayOf(c.i(2, 16), c.i(60, 16)), 2)).ok, "no tram stops")
        // A road of its own, with no way onto the street.
        c.apply(Action.BuildRoad(Action.roadPath(c.map, 8, 10, 12, 10, true), RoadType.STREET))
        c.apply(Action.PlaceStop(10, 10, Stop.BUS))
        assertEquals(Problem.NoRoute, c.plan(Action.AddLine(false, intArrayOf(c.i(2, 16), c.i(10, 10)), 2)).problem)
    }

    @Test
    fun moreVehiclesMeanShorterWaits() {
        fun wait(vehicles: Int): Int {
            val c = city(1930)
            c.apply(Action.PlaceStop(2, 16, Stop.BUS))
            c.apply(Action.PlaceStop(60, 16, Stop.BUS))
            c.apply(Action.PlaceBuilding(BuildingType.BUS_GARAGE, 40, 17))
            c.line(false, 2, 60, vehicles = vehicles)
            City::class.java.getDeclaredMethod("updateNetworks").apply { isAccessible = true }.invoke(c)
            return c.lineState(c.lines[0].id)!!.wait
        }
        assertTrue(wait(6) < wait(2), "6 buses ${wait(6)} s, 2 buses ${wait(2)} s")
    }

    @Test
    fun aGarageKeepsOnlySoManyBuses() {
        val c = city(1930)
        c.apply(Action.PlaceStop(2, 16, Stop.BUS))
        c.apply(Action.PlaceStop(60, 16, Stop.BUS))
        c.apply(Action.PlaceBuilding(BuildingType.BUS_GARAGE, 40, 17))
        c.line(false, 2, 60, vehicles = Balance.GARAGE_HOLDS * 2)
        City::class.java.getDeclaredMethod("updateNetworks").apply { isAccessible = true }.invoke(c)
        assertEquals(Balance.GARAGE_HOLDS, c.lineState(c.lines[0].id)!!.vehicles)
    }

    @Test
    fun aBusLaneTakesTheBusesPastTheTraffic() {
        fun busTrip(lane: Boolean): Int {
            val c = city(1960)
            c.apply(Action.PlaceStop(2, 16, Stop.BUS))
            c.apply(Action.PlaceStop(60, 16, Stop.BUS))
            c.apply(Action.PlaceBuilding(BuildingType.BUS_GARAGE, 40, 17))
            c.line(false, 2, 60, vehicles = 10)
            if (lane) assertTrue(c.apply(Action.BuildLane(Action.roadPath(c.map, 0, 16, 63, 16, true))).ok)
            // A jam of cars along the street, then the bus riders.
            repeat(2) { c.trips(3_000, 3_000) }
            val t = c.trips(20)
            return t.commute[c.i(2, 16)]
        }
        assertTrue(busTrip(lane = true) < busTrip(lane = false), "with a lane ${busTrip(true)} s, without ${busTrip(false)} s")
    }

    @Test
    fun linesUndoAndSurviveASave() {
        val c = city(1930)
        c.apply(Action.PlaceStop(2, 16, Stop.BUS))
        c.apply(Action.PlaceStop(60, 16, Stop.BUS))
        c.apply(Action.PlaceBuilding(BuildingType.BUS_GARAGE, 40, 17))
        val funds = c.funds
        c.line(false, 2, 60, vehicles = 4)
        assertEquals(funds - 4 * Balance.BUS_PRICE, c.funds)
        val id = c.lines[0].id
        assertTrue(c.apply(Action.SetVehicles(id, 6)).ok)
        assertEquals(6, c.lines[0].vehicles)
        c.undo()
        assertEquals(4, c.lines[0].vehicles)
        c.apply(Action.BuildLane(Action.roadPath(c.map, 10, 16, 20, 16, true)))
        val loaded = SaveGame.read(SaveGame.write(c))
        assertEquals(1, loaded.lines.size)
        assertEquals(4, loaded.lines[0].vehicles)
        assertTrue(loaded.lines[0].stops.contentEquals(c.lines[0].stops))
        assertEquals(1, loaded.map.lane[c.i(15, 16)].toInt())
        assertTrue(c.apply(Action.RemoveLine(id)).ok)
        assertTrue(c.lines.isEmpty())
        c.undo()
        assertEquals(1, c.lines.size)
    }

    @Test
    fun anOldTownsStopsBecomeLines() {
        val c = city(1930)
        for (x in listOf(2, 20, 40, 60)) c.apply(Action.PlaceStop(x, 16, Stop.BUS))
        c.apply(Action.PlaceBuilding(BuildingType.BUS_GARAGE, 40, 17))
        City::class.java.getDeclaredMethod("autoLines").apply { isAccessible = true }.invoke(c)
        assertEquals(1, c.lines.size)
        assertEquals(4, c.lines[0].stops.size)
        assertEquals(50, c.trips(50).lastModes[Mode.BUS.ordinal])
    }
}
