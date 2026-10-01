package com.rm.infill

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.rm.infill.sim.Action
import com.rm.infill.sim.City
import com.rm.infill.sim.CityEvent
import com.rm.infill.sim.Plan

/**
 * The city as the UI sees it. Changes go through [apply], which moves
 * [revision] on so anything showing the city is drawn again, and keeps a list
 * of changed tiles for the map to rebake.
 */
@Stable
class GameState(val city: City) {
    /** Goes up with every change to the city. Read it to be redrawn when the city changes. */
    var revision by mutableIntStateOf(0)
        private set

    private val changed = ArrayList<Int>()

    fun apply(action: Action): Plan {
        val plan = city.apply(action)
        if (plan.ok) changedBy(plan)
        return plan
    }

    val canUndo get() = revision.let { city.canUndo }
    val canRedo get() = revision.let { city.canRedo }

    fun undo(): Plan? = city.undo()?.also { if (it.ok) changedBy(it) else revision++ }

    fun redo(): Plan? = city.redo()?.also { if (it.ok) changedBy(it) else revision++ }

    private fun changedBy(plan: Plan) {
        for (i in plan.changes) changed += i
        revision++
    }

    /** Moves the town on [days] days, then lets the screen know once. Events it raises go to [onEvent]. */
    fun tick(days: Int, onEvent: (CityEvent) -> Unit = {}) {
        if (days <= 0) return
        repeat(days) { city.tick() }
        city.takeTownChanges { changed += it }
        city.takeEvents(onEvent)
        revision++
    }

    /** Tax rates in percent; any left out stay as they are. */
    fun setTaxes(r: Int = city.residentialTax, c: Int = city.commercialTax, i: Int = city.industrialTax) {
        city.residentialTax = r
        city.commercialTax = c
        city.industrialTax = i
        revision++
    }

    /** Service funding in percent; any left out stay as they are. */
    fun setFunding(
        police: Int = city.policeFunding, fire: Int = city.fireFunding, parks: Int = city.parkFunding,
        schools: Int = city.schoolFunding, health: Int = city.healthFunding,
    ) {
        city.policeFunding = police
        city.fireFunding = fire
        city.parkFunding = parks
        city.schoolFunding = schools
        city.healthFunding = health
        revision++
    }

    /** Hands over the tiles changed since the last call, as map indices. */
    fun takeChanges(each: (Int) -> Unit) {
        for (i in changed) each(i)
        changed.clear()
    }
}
