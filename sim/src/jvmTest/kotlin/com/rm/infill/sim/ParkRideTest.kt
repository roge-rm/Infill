package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParkRideTest {
    /** A long street with homes at the west end and work at the east, and a subway from x 40 to the east end under it. */
    private fun city(): City {
        val c = City(6, 128, 32, TerrainOptions(water = 0, trees = 0, river = false))
        City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, 1_000_000L)
        City::class.java.getDeclaredField("year").apply { isAccessible = true }.setInt(c, 1970)
        c.everything = true
        c.apply(Action.BuildRoad(Action.roadPath(c.map, 0, 16, 127, 16, true), RoadType.STREET))
        c.apply(Action.PlaceBuilding(BuildingType.COAL_PLANT, 30, 19))
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 31, 18, 31, 16, false)))
        assertTrue(c.apply(Action.BuildSubway(Action.roadPath(c.map, 40, 17, 120, 17, true))).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.SUBWAY_STATION, 40, 17)).ok)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.SUBWAY_STATION, 120, 17)).ok)
        c.apply(Action.BuildPowerLine(Action.roadPath(c.map, 31, 18, 120, 18, true)))
        return c
    }

    private fun City.i(x: Int, y: Int) = map.index(x, y)

    private val City.traffic get() = City::class.java.getDeclaredField("traffic").apply { isAccessible = true }.get(this) as Traffic

    /** A month of [cars] drivers going from the west end of the street to work at the east end. */
    private fun City.trips(cars: Int): Traffic {
        val t = traffic
        val n = map.size
        City::class.java.getDeclaredMethod("updateNetworks").apply { isAccessible = true }.invoke(this)
        City::class.java.getDeclaredMethod("updateParkRides").apply { isAccessible = true }.invoke(this)
        t.cycling = 0
        val w = IntArray(n).also { it[i(2, 16)] = cars }
        val j = IntArray(n).also { it[i(120, 16)] = cars }
        t.newMonth(w, IntArray(n), IntArray(n), j, IntArray(n), 0, carWorkersAt = w.copyOf())
        t.sendDay(1, 1)
        t.newMonth(IntArray(n), IntArray(n), IntArray(n), IntArray(n), IntArray(n), 0)
        return t
    }

    @Test
    fun driversParkAndGoOnBySubway() {
        assertEquals(50, city().trips(50).lastModes[Mode.CAR.ordinal], "no park and ride, so they drive")
        val c = city()
        assertEquals(Problem.NeedsStation, c.plan(Action.PlaceBuilding(BuildingType.PARK_AND_RIDE, 30, 13)).problem)
        assertTrue(c.apply(Action.PlaceBuilding(BuildingType.PARK_AND_RIDE, 40, 14)).ok)
        val t = c.trips(50)
        assertEquals(50, t.lastModes[Mode.SUBWAY.ordinal], "modes ${t.lastModes.toList()}")
        val lot = c.allBuildings.first { it.type == BuildingType.PARK_AND_RIDE }
        assertEquals(50, c.parkedAt(lot))
        assertTrue(t.lastVolume[c.i(30, 16)] > 0, "they drive to the lot")
        assertEquals(0, t.lastVolume[c.i(60, 16)], "and not past it")
    }

    @Test
    fun aFullLotSendsTheRestOnByCar() {
        val c = city()
        c.apply(Action.PlaceBuilding(BuildingType.PARK_AND_RIDE, 40, 14))
        val spaces = c.spacesAt(c.allBuildings.first { it.type == BuildingType.PARK_AND_RIDE })
        assertEquals(Balance.PARK_AND_RIDE_SPACES, spaces)
        val t = c.trips(spaces + 300)
        assertEquals(spaces, t.lastModes[Mode.SUBWAY.ordinal], "modes ${t.lastModes.toList()}")
        assertEquals(300, t.lastModes[Mode.CAR.ordinal])
        assertEquals(spaces + 300, t.workersPlaced, "everyone gets to work")
    }

    @Test
    fun parkedDriversDontWalkToWorkFromTheLot() {
        val c = city()
        c.apply(Action.PlaceBuilding(BuildingType.PARK_AND_RIDE, 40, 14))
        // Work right by the lot: they drive there, and never park to walk the last bit.
        val t = c.traffic
        val n = c.map.size
        City::class.java.getDeclaredMethod("updateNetworks").apply { isAccessible = true }.invoke(c)
        City::class.java.getDeclaredMethod("updateParkRides").apply { isAccessible = true }.invoke(c)
        t.cycling = 0
        val w = IntArray(n).also { it[c.i(2, 16)] = 20 }
        val j = IntArray(n).also { it[c.i(42, 16)] = 20 }
        t.newMonth(w, IntArray(n), IntArray(n), j, IntArray(n), 0, carWorkersAt = w.copyOf())
        t.sendDay(1, 1)
        t.newMonth(IntArray(n), IntArray(n), IntArray(n), IntArray(n), IntArray(n), 0)
        assertEquals(20, t.lastModes[Mode.CAR.ordinal])
        assertEquals(0, t.lastParked.sum())
    }

    @Test
    fun payingToParkPutsSomeOffAndBringsInMoney() {
        val c = city()
        c.apply(Action.PlaceBuilding(BuildingType.PARK_AND_RIDE, 40, 14))
        val lot = c.allBuildings.first { it.type == BuildingType.PARK_AND_RIDE }
        assertFalse(lot.paidParking)
        assertTrue(c.apply(Action.SetParkingFee(40, 14, true)).ok)
        assertTrue(lot.paidParking)
        assertEquals(Problem.NothingToDo, c.plan(Action.SetParkingFee(40, 14, true)).problem)
        val t = c.trips(50)
        assertEquals(50, t.lastPaidParked, "still quicker than driving, fee and all")
        c.undo()
        assertFalse(lot.paidParking)
        c.redo()
        assertTrue(lot.paidParking)
    }

    @Test
    fun aPaidLotSurvivesASave() {
        val c = city()
        c.apply(Action.PlaceBuilding(BuildingType.PARK_AND_RIDE, 40, 14))
        c.apply(Action.SetParkingFee(40, 14, true))
        val back = SaveGame.read(SaveGame.write(c))
        assertTrue(back.allBuildings.first { it.type == BuildingType.PARK_AND_RIDE }.paidParking)
    }

    @Test
    fun aBusyStreetTakesLongerToParkOn() {
        val quiet = city().let { it.trips(50).commute[it.i(2, 16)] }
        // The same drive, to a street with a thousand jobs on it.
        val c = city()
        val tr = c.traffic
        val n = c.map.size
        City::class.java.getDeclaredMethod("updateNetworks").apply { isAccessible = true }.invoke(c)
        tr.cycling = 0
        val w = IntArray(n).also { it[c.i(2, 16)] = 50 }
        val j = IntArray(n).also { it[c.i(120, 16)] = 1_000 }
        tr.newMonth(w, IntArray(n), IntArray(n), j, IntArray(n), 0, carWorkersAt = w.copyOf())
        tr.sendDay(1, 1)
        tr.newMonth(IntArray(n), IntArray(n), IntArray(n), IntArray(n), IntArray(n), 0)
        assertEquals(50, tr.lastModes[Mode.CAR.ordinal], "modes ${tr.lastModes.toList()}")
        // The quiet street has its 50 jobs to look among, the busy one a thousand.
        assertEquals(quiet - 50 / Balance.JOBS_A_SECOND_OF_SEARCH + 1_000 / Balance.JOBS_A_SECOND_OF_SEARCH, tr.commute[c.i(2, 16)])
    }

    @Test
    fun someWithCarsCycleWhereThereAreLanes() {
        val c = city()
        val t = c.traffic
        val n = c.map.size
        City::class.java.getDeclaredMethod("updateNetworks").apply { isAccessible = true }.invoke(c)
        t.cycling = 0
        t.carCycling = IntArray(n) { 20 }
        val w = IntArray(n).also { it[c.i(2, 16)] = 100 }
        val j = IntArray(n).also { it[c.i(30, 16)] = 100 }
        t.newMonth(w, IntArray(n), IntArray(n), j, IntArray(n), 0, carWorkersAt = w.copyOf())
        t.sendDay(1, 1)
        t.newMonth(IntArray(n), IntArray(n), IntArray(n), IntArray(n), IntArray(n), 0)
        assertEquals(20, t.lastModes[Mode.BIKE.ordinal], "modes ${t.lastModes.toList()}")
        assertEquals(80, t.lastModes[Mode.CAR.ordinal])
        // None before 1990, and only as far as the roads near home have lanes.
        val year = City::class.java.getDeclaredField("year").apply { isAccessible = true }
        year.setInt(c, 2000)
        assertEquals(null, c.carCycling())
        assertTrue(c.apply(Action.BuildCycleLane(Action.roadPath(c.map, 0, 16, 40, 16, true))).ok)
        val pull = c.carCycling()!!
        assertEquals(Balance.CAR_CYCLE_PULL, pull[c.i(2, 16)], "lanes on every road near the west end")
        assertEquals(0, pull[c.i(100, 16)], "none near the east end")
        assertTrue(pull[c.i(44, 16)] in 1 until Balance.CAR_CYCLE_PULL, "some, just past the end of the lanes")
        year.setInt(c, 1985)
        assertEquals(null, c.carCycling())
    }

    @Test
    fun fewerKeepACarNearAStationFrom1990() {
        val c = city()
        val fewer = City::class.java.getDeclaredMethod("fewerCars", Building::class.java, BooleanArray::class.java).apply { isAccessible = true }
        val near = City::class.java.getDeclaredMethod("nearStations").apply { isAccessible = true }
        val year = City::class.java.getDeclaredField("year").apply { isAccessible = true }
        val home = Building(1, BuildingType.HOUSE, 41, 14, 0)
        val far = Building(2, BuildingType.HOUSE, 2, 14, 0)
        year.setInt(c, 1980)
        assertEquals(null, near.invoke(c))
        year.setInt(c, 2020)
        val n = near.invoke(c) as BooleanArray
        assertEquals(Balance.STATION_CAR_CUT, fewer.invoke(c, home, n))
        assertEquals(0, fewer.invoke(c, far, n))
        assertEquals(Balance.TOWER_CAR_CUT, fewer.invoke(c, Building(3, BuildingType.SKYSCRAPER, 2, 10, 0), n), "a tower anywhere")
        year.setInt(c, 2005)
        assertEquals(Balance.STATION_CAR_CUT / 2, fewer.invoke(c, home, near.invoke(c) as BooleanArray), "half way in")
    }

    @Test
    fun theLotBecomesAGarage() {
        assertEquals(BuildingType.PARK_AND_RIDE, BuildingType.PARKING_GARAGE.root)
        assertTrue(BuildingType.PARKING_GARAGE.parkRide)
        assertEquals(BuildingType.PARK_AND_RIDE.width, BuildingType.PARKING_GARAGE.width)
    }
}
