package com.rm.infill.audio

import com.rm.infill.sim.Action
import com.rm.infill.sim.City
import com.rm.infill.sim.EventKind
import com.rm.infill.sim.Plan
import com.rm.infill.sim.Problem
import com.rm.infill.sound.Materials
import com.rm.infill.sound.Recipes
import com.rm.infill.sound.SharedParams
import kotlin.time.TimeSource

/**
 * The game's own sounds, over the town's: the tools as they build and
 * clear, and the news. Short and soft, on the effects and interface
 * volumes. Buttons make none.
 */
object Sounds {
    private var seed = 0
    private var lastChime: TimeSource.Monotonic.ValueTimeMark? = null

    private fun play(recipe: Int, vararg p: Float, gain: Float = 1f, pan: Float = 0f, delay: Float = 0f) {
        val params = FloatArray(SharedParams.COUNT)
        for ((j, v) in p.withIndex()) params[j] = v
        params[SharedParams.GAIN] = gain
        params[SharedParams.PAN] = pan
        AudioEngine.event(recipe, params, delay, ++seed)
    }

    /** Something that couldn't be done. */
    fun caution() = play(Recipes.CAUTION, 0f)

    /** The news: good, bad, or a new era. Not more than one at a time, however much happens at once. */
    private fun chime(kind: Float) {
        val now = TimeSource.Monotonic.markNow()
        lastChime?.let { if (it.elapsedNow().inWholeMilliseconds < CHIME_GAP_MS && kind != ERA) return }
        lastChime = now
        play(Recipes.CHIME, kind)
    }

    /**
     * What [action] sounds like once [plan] says how it went: laying a road,
     * track or pipe, painting zones, putting up a building, clearing. [pan]
     * leans it towards where it is on screen.
     */
    fun action(action: Action, plan: Plan, city: City, pan: Float = 0f) {
        if (!plan.ok) {
            if (plan.problem != Problem.NothingToDo) caution()
            return
        }
        val size = plan.changes.size
        val p = pan.coerceIn(-0.6f, 0.6f)
        when (action) {
            is Action.BuildRoad -> {
                play(Recipes.IMPACT, 0.5f, Materials.EARTH.toFloat(), 0.4f, gain = 0.7f, pan = p)
                if (size > 4) play(Recipes.IMPACT, 0.4f, Materials.EARTH.toFloat(), 0.3f, gain = 0.6f, pan = p, delay = 0.12f)
            }
            is Action.BuildRail, is Action.BuildTram, is Action.BuildSubway -> {
                play(Recipes.IMPACT, 0.4f, Materials.METAL.toFloat(), 0.2f, gain = 0.5f, pan = p)
                play(Recipes.IMPACT, 0.3f, Materials.METAL.toFloat(), 0.2f, gain = 0.4f, pan = p, delay = 0.18f)
            }
            is Action.BuildPipe, is Action.BuildPowerLine, is Action.BuildPhoneLine, is Action.BuildWire, is Action.BuildBank,
            is Action.BuildLane, is Action.PlaceStop -> play(Recipes.CLUNK, 0.6f, gain = 0.8f, pan = p)
            is Action.PlaceZone, is Action.PaintDistrict -> chimeTick(p)
            is Action.PlaceParks, is Action.PlantStreetTrees, is Action.PlantTrees -> play(Recipes.IMPACT, 0.25f, Materials.EARTH.toFloat(), 0.2f, gain = 0.5f, pan = p)
            is Action.PlaceBuilding -> {
                val big = (action.type.width * action.type.height / 16f).coerceIn(0.2f, 1f)
                play(Recipes.IMPACT, 0.4f + 0.4f * big, (if (city.year < BRICK_UNTIL) Materials.WOOD else Materials.ROCK).toFloat(), big, gain = 0.8f, pan = p)
                play(Recipes.CLUNK, 0.5f, gain = 0.5f, pan = p, delay = 0.15f)
            }
            is Action.Bulldoze, is Action.RemoveTunnel, is Action.RemoveTransit, is Action.RemovePipes, is Action.RemovePhone, is Action.RenewArea -> {
                play(Recipes.CRUNCH, (0.4f + size / 40f).coerceAtMost(1.5f), gain = 0.8f, pan = p)
                play(Recipes.IMPACT, 0.4f, Materials.EARTH.toFloat(), 0.5f, gain = 0.5f, pan = p, delay = 0.08f)
            }
            else -> {}
        }
    }

    private fun chimeTick(pan: Float) = play(Recipes.CHIME, TICK, gain = 0.8f, pan = pan)

    /** The news, as the status line tells it, and the odd crash or rumble with it. */
    fun event(kind: EventKind) {
        when (kind) {
            EventKind.EraArrived -> chime(ERA)
            EventKind.FireSaved, EventKind.EpidemicOver -> chime(GOOD)
            EventKind.Earthquake -> {
                play(Recipes.EXPLOSION, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 250f, gain = 0.9f)
                chime(BAD)
            }
            EventKind.IndustrialAccident, EventKind.NuclearAccident -> {
                play(Recipes.EXPLOSION, 0.8f, 0f, 0f, 0f, 0f, 0f, 0f, 900f, gain = 0.7f)
                chime(BAD)
            }
            EventKind.Flooding, EventKind.RiverFlood, EventKind.TunnelFlooded -> {
                play(Recipes.SPLASH, 1.2f, gain = 0.6f)
                chime(BAD)
            }
            else -> chime(BAD)
        }
    }

    private const val TICK = 0f
    private const val GOOD = 1f
    private const val BAD = 2f
    private const val ERA = 3f
    private const val CHIME_GAP_MS = 3000L

    /** Buildings put up before this sound like wood going up; after, like brick and concrete. */
    private const val BRICK_UNTIL = 1920
}
