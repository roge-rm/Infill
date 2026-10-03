package com.rm.infill.audio

import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer
import com.rm.infill.res.Res

/** The music through MediaPlayer, each piece read from the resources into memory. */
internal actual object MusicOut {
    private val players = arrayOfNulls<MediaPlayer>(2)
    private val sounding = BooleanArray(2)
    private var paused = false

    actual suspend fun load(deck: Int, path: String): Boolean {
        val bytes = runCatching { Res.readBytes(path) }.getOrNull() ?: return false
        stop(deck)
        val p = MediaPlayer()
        return runCatching {
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            p.setDataSource(Bytes(bytes))
            p.prepare()
            players[deck] = p
            true
        }.getOrElse {
            p.release()
            false
        }
    }

    actual fun start(deck: Int, volume: Float, onEnd: () -> Unit) {
        val p = players[deck] ?: return
        p.setVolume(volume, volume)
        p.setOnCompletionListener {
            sounding[deck] = false
            onEnd()
        }
        sounding[deck] = true
        if (!paused) p.start()
    }

    actual fun volume(deck: Int, volume: Float) {
        players[deck]?.setVolume(volume, volume)
    }

    actual fun stop(deck: Int) {
        sounding[deck] = false
        players[deck]?.let {
            runCatching { it.stop() }
            it.release()
        }
        players[deck] = null
    }

    actual fun pause(paused: Boolean) {
        this.paused = paused
        for (d in players.indices) if (sounding[d]) players[d]?.let { runCatching { if (paused) it.pause() else it.start() } }
    }

    /** A piece held in memory, for MediaPlayer to read. */
    private class Bytes(private val data: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= data.size) return -1
            val n = minOf(size.toLong(), data.size - position).toInt()
            System.arraycopy(data, position.toInt(), buffer, offset, n)
            return n
        }

        override fun getSize(): Long = data.size.toLong()

        override fun close() {}
    }
}
