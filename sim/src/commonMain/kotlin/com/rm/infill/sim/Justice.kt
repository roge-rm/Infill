package com.rm.infill.sim

import kotlin.math.max
import kotlin.math.min

/** A month in the courts and the cells. */
object Justice {
    /**
     * What comes of [arrests] with the courts able to hear [canHear] cases and
     * [room] in the cells and jails, already holding [prisoners]: those heard,
     * those who'd be held, those held, and the percent of arrests that stuck.
     */
    class Month(val heard: Int, val wanting: Int, val prisoners: Int, val justice: Int)

    fun month(arrests: Int, canHear: Int, room: Int, prisoners: Int): Month {
        val heard = min(arrests, canHear)
        val convicted = heard * Balance.CONVICTED / 100
        // Those who've served their time go, the newly guilty come in, and any there's no room for are let go.
        val served = if (prisoners > 0) max(1, prisoners / Balance.SENTENCE) else 0
        val wanting = prisoners - served + convicted
        val held = min(wanting, room)
        val letGo = wanting - held
        val kept = if (convicted == 0) 100 else max(0, convicted - letGo) * 100 / convicted
        val justice = if (arrests == 0) 100 else heard * 100 / arrests * kept / 100
        return Month(heard, wanting, held, justice)
    }
}
