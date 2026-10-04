package com.rm.infill

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.rm.infill.platform.SimLock
import com.rm.infill.platform.simDispatcher
import com.rm.infill.sim.Action
import com.rm.infill.sim.City
import com.rm.infill.sim.CityEvent
import com.rm.infill.sim.Plan
import com.rm.infill.sim.SaveGame
import kotlinx.coroutines.withContext

/**
 * The city as the UI sees it. The town's days are worked out on their own
 * thread ([advance]), so the screen never waits on the turn of the month;
 * changes from the screen go through [apply] and the like, which hold the
 * town's [lock] while they work, and move [revision] on so anything showing
 * the city is drawn again. The tiles changed are kept for the map to rebake.
 */
@Stable
class GameState(val city: City) {
    /** Goes up with every change to the city. Read it to be redrawn when the city changes. */
    var revision by mutableIntStateOf(0)
        private set

    /** Held by whatever changes the town or reads it whole. See [SimLock]. */
    val lock = SimLock()

    private val changed = ArrayList<Int>()

    /** What the days raised, waiting for the screen to take it. */
    private val events = ArrayList<CityEvent>()

    init {
        // The month's long: between its steps the town lets go, so an action from the screen needn't wait it out.
        city.between = {
            lock.unlock()
            lock.lock()
        }
        city.refreshViews()
    }

    /** Runs [block] holding the town's lock, waiting for it if the sim has it. */
    inline fun <T> locked(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    /** Runs [block] if the town's free now, and gives null if the sim has it. */
    inline fun <T> tryLocked(block: () -> T): T? {
        if (!lock.tryLock()) return null
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    fun apply(action: Action): Plan {
        val plan = locked {
            city.apply(action).also { plan ->
                if (plan.ok) {
                    // Tiles it changed beyond its own, such as the paths to what it cleared.
                    city.takeTownChanges { changed += it }
                    for (i in plan.changes) changed += i
                    city.refreshViews()
                }
            }
        }
        revision++
        return plan
    }

    /** What [action] would do, for the preview while dragging. */
    fun plan(action: Action): Plan = locked { city.plan(action) }

    val canUndo get() = revision.let { city.canUndo }
    val canRedo get() = revision.let { city.canRedo }

    fun undo(): Plan? = changing { city.undo() }

    fun redo(): Plan? = changing { city.redo() }

    private fun changing(step: () -> Plan?): Plan? {
        val plan = locked {
            step()?.also { plan ->
                if (plan.ok) for (i in plan.changes) changed += i
                city.refreshViews()
            }
        }
        revision++
        return plan
    }

    /** Events an action raised, such as people forced out by a clearing, told at once rather than with the next day's. */
    fun takeEvents(onEvent: (CityEvent) -> Unit) {
        val got = tryLocked {
            city.takeEvents { events += it }
            events.toList().also { events.clear() }
        } ?: return
        got.forEach(onEvent)
    }

    /**
     * Moves the town on [days] days on the sim's own thread, then lets the
     * screen know once, back on its thread. Events raised go to [onEvent].
     * Says whether a new month came in.
     */
    suspend fun advance(days: Int, onEvent: (CityEvent) -> Unit = {}): Boolean {
        if (days <= 0) return false
        val newMonth = withContext(simDispatcher) {
            locked {
                val before = city.month
                repeat(days) { city.tick() }
                city.takeTownChanges { changed += it }
                city.takeEvents { events += it }
                city.month != before
            }
        }
        revision++
        takeEvents(onEvent)
        return newMonth
    }

    /** The town written out for saving, waiting for the sim to finish the day it's on. */
    fun saveBytes(): ByteArray = locked { SaveGame.write(city) }

    /** The same, worked out on the sim's thread so the screen carries on meanwhile. */
    suspend fun saveBytesAway(): ByteArray = withContext(simDispatcher) { saveBytes() }

    /** Changes that aren't actions: taxes, funding and the toll. */
    private fun setting(change: () -> Unit) {
        locked(change)
        revision++
    }

    fun setTollRate(cents: Int) = setting { city.setTollRate(cents) }

    /** Passes or repeals a town-wide law. */
    fun setOrdinance(o: com.rm.infill.sim.Ordinance, on: Boolean) = setting { city.setOrdinance(o, on) }

    /** Whether the town holds elections. */
    fun setElections(on: Boolean) = setting { city.elections = on }

    /** Sells a bond of [years] of income, if the town can. */
    fun sellBond(years: Int) = setting { city.sellBond(years) }

    /** Tax rates in percent; any left out stay as they are. */
    fun setTaxes(r: Int = city.residentialTax, c: Int = city.commercialTax, i: Int = city.industrialTax) = setting {
        city.residentialTax = r
        city.commercialTax = c
        city.industrialTax = i
    }

    /** Service funding in percent; any left out stay as they are. */
    fun setFunding(
        police: Int = city.policeFunding, fire: Int = city.fireFunding, parks: Int = city.parkFunding,
        schools: Int = city.schoolFunding, health: Int = city.healthFunding, relief: Int = city.reliefFunding,
    ) = setting {
        city.reliefFunding = relief
        // No more than the overseer allows, if it's in.
        val cap = city.fundingCap()
        city.policeFunding = police.coerceAtMost(cap)
        city.fireFunding = fire.coerceAtMost(cap)
        city.parkFunding = parks.coerceAtMost(cap)
        city.schoolFunding = schools.coerceAtMost(cap)
        city.healthFunding = health.coerceAtMost(cap)
    }

    /** Hands over the tiles changed since the last call, as map indices; none this frame if the sim has the town. */
    fun takeChanges(each: (Int) -> Unit) {
        val got = tryLocked { changed.toIntArray().also { changed.clear() } } ?: return
        for (i in got) each(i)
    }
}
