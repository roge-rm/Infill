package com.rm.infill.audio

import com.rm.infill.sim.Era
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * The music: the era's pieces one after another, with a quiet spell between
 * them, and a fade to the new era's when one arrives. The pieces are
 * files/music/<era>-<n>.ogg, numbered from 1; an era with none is quiet.
 */
class Music {
    private var deck = 0
    private var playing: Era? = null
    private var ended = false
    private var level = 0f
    private val next = HashMap<Era, Int>()
    private val none = HashSet<Era>()

    /**
     * Runs for as long as the game does: [era] is the town's era, or null when
     * there's no town to play for, and [volume] how loud the music is, 0 to 1.
     */
    suspend fun run(era: () -> Era?, volume: () -> Float) {
        var quiet = FIRST_WAIT
        while (true) {
            delay(STEP_MS)
            val want = era()
            val loud = volume()
            val current = playing
            if (current != null && (want != current || ended)) {
                // The era's changed or the piece is over: fade it out, then a quiet spell.
                if (!ended) fade(level, 0f, FADE_OUT_MS, loud)
                MusicOut.stop(deck)
                playing = null
                quiet = if (ended && want == current) GAP_LEAST + Random.nextFloat() * GAP_MORE else ERA_WAIT
                continue
            }
            if (current != null) {
                MusicOut.volume(deck, level * loud)
                continue
            }
            if (want == null || want in none) continue
            quiet -= STEP_MS / 1000f
            if (quiet > 0f) continue
            if (!start(want)) {
                quiet = GAP_LEAST
                continue
            }
            fade(0f, 1f, FADE_IN_MS, loud)
        }
    }

    /** Starts the era's next piece, from the first again after the last. */
    private suspend fun start(era: Era): Boolean {
        val name = era.name.lowercase()
        var n = next[era] ?: 1
        deck = 1 - deck
        if (!MusicOut.load(deck, "files/music/$name-$n.ogg")) {
            if (n == 1 || !MusicOut.load(deck, "files/music/$name-1.ogg")) {
                none += era
                return false
            }
            n = 1
        }
        next[era] = n + 1
        ended = false
        playing = era
        level = 0f
        MusicOut.start(deck, 0f) { ended = true }
        return true
    }

    private suspend fun fade(from: Float, to: Float, ms: Long, loud: Float) {
        val steps = (ms / FADE_STEP_MS).toInt().coerceAtLeast(1)
        for (k in 1..steps) {
            level = from + (to - from) * k / steps
            MusicOut.volume(deck, level * loud)
            delay(FADE_STEP_MS)
        }
    }

    /** Put away: the music stops where it is, and goes on from there when it's back. */
    fun pause(paused: Boolean) = MusicOut.pause(paused)

    private companion object {
        const val STEP_MS = 200L
        const val FADE_STEP_MS = 50L
        const val FADE_IN_MS = 3000L
        const val FADE_OUT_MS = 4000L
        /** Seconds before the first piece, between the end of one and the next (at least, and up to this much more), and after a new era. */
        const val FIRST_WAIT = 4f
        const val GAP_LEAST = 40f
        const val GAP_MORE = 80f
        const val ERA_WAIT = 2f
    }
}

/** Plays the music's files, two decks so one can fade out as the next starts. */
internal expect object MusicOut {
    /** Gets the piece at [path] in the resources ready on [deck]; false if there's no such piece. */
    suspend fun load(deck: Int, path: String): Boolean

    /** Starts what's loaded on [deck] at [volume]; [onEnd] when it's played through. */
    fun start(deck: Int, volume: Float, onEnd: () -> Unit)

    fun volume(deck: Int, volume: Float)

    fun stop(deck: Int)

    fun pause(paused: Boolean)
}
