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
    fun theLotBecomesAGarage() {
        assertEquals(BuildingType.PARK_AND_RIDE, BuildingType.PARKING_GARAGE.root)
        assertTrue(BuildingType.PARKING_GARAGE.parkRide)
        assertEquals(BuildingType.PARK_AND_RIDE.width, BuildingType.PARKING_GARAGE.width)
    }
}
