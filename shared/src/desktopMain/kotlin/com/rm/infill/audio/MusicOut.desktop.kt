package com.rm.infill.audio

import com.rm.infill.res.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Vorbis music on a native output, with completion delivered on the UI thread. */
internal actual object MusicOut {
    private val available = runCatching { Synth.load() }.getOrDefault(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val watchers = arrayOfNulls<Job>(2)
    private val sounding = BooleanArray(2)
    private var paused = false

    actual suspend fun load(deck: Int, path: String): Boolean {
        if (!available) return false
        val bytes = runCatching { Res.readBytes(path) }.getOrNull() ?: return false
        stop(deck)
        return withContext(Dispatchers.IO) { nativeLoad(deck, bytes) }
    }
    actual fun start(deck: Int, volume: Float, onEnd: () -> Unit) {
        if (!available) return
        sounding[deck] = true
        nativeVolume(deck, volume)
        if (!paused) nativePlay(deck, true)
        watchers[deck]?.cancel()
        watchers[deck] = scope.launch {
            while (true) {
                delay(200)
                if (!paused && nativeEnded(deck)) {
                    sounding[deck] = false
                    onEnd()
                    break
                }
            }
        }
    }
    actual fun volume(deck: Int, volume: Float) { if (available) nativeVolume(deck, volume) }
    actual fun stop(deck: Int) {
        watchers[deck]?.cancel()
        watchers[deck] = null
        sounding[deck] = false
        if (available) nativeStop(deck)
    }
    actual fun pause(paused: Boolean) {
        this.paused = paused
        if (available) for (d in sounding.indices) if (sounding[d]) nativePlay(d, !paused)
    }
    private external fun nativeLoad(deck: Int, bytes: ByteArray): Boolean
    private external fun nativePlay(deck: Int, playing: Boolean)
    private external fun nativeVolume(deck: Int, volume: Float)
    private external fun nativeStop(deck: Int)
    private external fun nativeEnded(deck: Int): Boolean
}
