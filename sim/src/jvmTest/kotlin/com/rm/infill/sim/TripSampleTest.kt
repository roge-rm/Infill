package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TripSampleTest {
    @Test
    fun aMonthsTripsGiveRoutesThatStartAndEndWhereTheTripsDo() {
        val c = City(1, 48, 48, TerrainOptions(water = 0, trees = 0, river = false))
        c.apply(Action.BuildRoad(Action.roadPath(c.map, 2, 20, 45, 20, true), RoadType.STREET))
        val size = c.map.size
        val from = c.map.index(2, 20)
        val to = c.map.index(45, 20)
        val t = Traffic(c.map)
        val w = IntArray(size).also { it[from] = 30 }
        val j = IntArray(size).also { it[to] = 30 }
        t.newMonth(w, IntArray(size), IntArray(size), j, IntArray(size), 0, carWorkersAt = w)
        t.sendDay(1, 1)
        t.newMonth(IntArray(size), IntArray(size), IntArray(size), IntArray(size), IntArray(size), 0)
        val s = t.tripSample
        assertTrue(s.size > 0)
        var cars = 0
        for (i in 0 until s.size) {
            val last = s.length(i) - 1
            if (s.kind(i) == TripSample.CAR) {
                cars++
                assertEquals(from, s.tile(i, 0))
                assertEquals(to, s.tile(i, last))
                assertEquals(30, s.weight(i))
            }
            for (k in 0 until last) {
                val a = s.tile(i, k)
                val b = s.tile(i, k + 1)
                val d = kotlin.math.abs(a % c.map.width - b % c.map.width) + kotlin.math.abs(a / c.map.width - b / c.map.width)
                assertEquals(1, d, "tiles ${a} and ${b} join")
            }
        }
        assertEquals(1, cars)
        assertEquals(0, s.pick(0))
    }

    @Test
    fun aLargeSampleIsDrawnInProportionToTrips() {
        val legs = IntList()
        // One busy route with as many trips as 4000 quiet ones put together.
        for (r in 0..4000) {
            legs.add(TripSample.CAR); legs.add(r); legs.add(r + 1)
            legs.add(if (r == 0) 4000 else 1)
            legs.add(2); legs.add(r); legs.add(r + 1)
        }
        val s = TripSample.build(legs, 7)
        assertTrue(s.size <= TripSample.MAX)
        assertEquals(8000L, s.trips)
        assertEquals(TripSample.MAX.toLong(), s.totalWeight)
        assertEquals(0, s.tile(0, 0))
        val busy = s.weight(0)
        assertTrue(busy in 1300..1700, "busy route drawn $busy times of ${TripSample.MAX}")
        for (i in 1 until s.size) assertTrue(s.weight(i) in 1..5)
    }

    @Test
    fun pastTheCapEveryTripIsCountedAndTheWholeMonthIsSampled() {
        fun month(cap: Int?): TripSample {
            val c = City(1, 48, 48, TerrainOptions(water = 0, trees = 0, river = false))
            c.apply(Action.BuildRoad(Action.roadPath(c.map, 2, 20, 45, 20, true), RoadType.STREET))
            val size = c.map.size
            val t = Traffic(c.map)
            if (cap != null) t.legCap = cap
            val w = IntArray(size)
            val j = IntArray(size)
            for (x in 2..44) w[c.map.index(x, 20)] = 10 + x
            for (x in 5..45 step 8) j[c.map.index(x, 20)] = 200
            t.newMonth(w, IntArray(size), IntArray(size), j, IntArray(size), 3, carWorkersAt = w)
            for (d in 1..30) t.sendDay(d, 30)
            t.newMonth(IntArray(size), IntArray(size), IntArray(size), IntArray(size), IntArray(size), 3)
            return t.tripSample
        }
        val full = month(null)
        val capped = month(400)
        assertTrue(full.size > 30, "${full.size} routes")
        assertTrue(capped.size < full.size)
        assertEquals(full.trips, capped.trips)
        // The kept legs stand for all of them, give or take.
        assertTrue(capped.totalWeight in full.totalWeight / 2..full.totalWeight * 2, "${capped.totalWeight} of ${full.totalWeight}")
        // Legs from all over the month, not just the first days': trips from most of the starts are in.
        val starts = (0 until capped.size).map { capped.tile(it, 0) }.toSet()
        val all = (0 until full.size).map { full.tile(it, 0) }.toSet()
        assertTrue(starts.size * 3 > all.size, "${starts.size} of ${all.size} starts")
    }
}
