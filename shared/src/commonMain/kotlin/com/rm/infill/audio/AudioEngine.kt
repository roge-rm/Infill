package com.rm.infill.audio

import kotlin.concurrent.Volatile

/**
 * The sound engine: the synth brought over from Apogee, on its own audio
 * thread, driven from here. The game never waits on it: the scene is handed
 * over a few times a second, one-shots are queued, and with no synth the game
 * plays silent. Reached through [Synth]: JNI on Android, WebAssembly in a
 * browser.
 */
object AudioEngine {
    private val available: Boolean = runCatching { Synth.load() }.getOrDefault(false)

    @Volatile
    var running = false
        private set

    /** Opens the output and starts the synth with room for [voiceBudget] voices at once. */
    fun start(voiceBudget: Int) {
        if (!available || running) return
        running = runCatching { Synth.nativeStart(voiceBudget) }.getOrDefault(false)
        lastGains = null
    }

    fun stop() {
        if (!available || !running) return
        Synth.nativeStop()
        running = false
    }

    /** Put away, the output stops; back again, it picks up where it was. */
    fun pause(paused: Boolean) {
        if (running) Synth.nativePause(paused)
    }

    /**
     * The held sounds now: [count] of them, each with a key that stays the
     * same while the sound lasts, its recipe, flags, and
     * [com.rm.infill.sound.SharedParams.COUNT] parameters each in [params].
     */
    fun scene(count: Int, keys: IntArray, recipes: IntArray, flags: IntArray, params: FloatArray) {
        if (running) Synth.nativeScene(count, keys, recipes, flags, params)
    }

    /** A one-shot, [delay] seconds from now. */
    fun event(recipe: Int, params: FloatArray, delay: Float = 0f, seed: Int = 0) {
        if (running) Synth.nativeEvent(recipe, 0, seed, delay, params)
    }

    /** How loud each of the [com.rm.infill.sound.Buses] is, 0 to 1, sent only when it changes. */
    fun busGains(gains: FloatArray) {
        if (!running || lastGains?.contentEquals(gains) == true) return
        lastGains = gains.copyOf()
        Synth.nativeBusGains(gains)
    }

    private var lastGains: FloatArray? = null

    /** How much the town echoes: more in a built-up town than out in the fields. */
    fun room(amount: Float) {
        if (running && amount != lastRoom) {
            lastRoom = amount
            Synth.nativeRoom(amount)
        }
    }

    private var lastRoom = Float.NaN

    /** Voices sounding now. */
    val activeVoices: Int get() = if (running) Synth.nativeActiveVoices() else 0
}

/** The synth itself, the same C++ everywhere. [load] says whether it's there to use. */
internal expect object Synth {
    fun load(): Boolean
    fun nativeActiveVoices(): Int
    fun nativeStart(voiceBudget: Int): Boolean
    fun nativeStop()
    fun nativePause(paused: Boolean)
    fun nativeScene(count: Int, keys: IntArray, recipes: IntArray, flags: IntArray, params: FloatArray)
    fun nativeEvent(recipe: Int, flags: Int, seed: Int, delay: Float, params: FloatArray)
    fun nativeBusGains(gains: FloatArray)
    fun nativeRoom(amount: Float)
}
