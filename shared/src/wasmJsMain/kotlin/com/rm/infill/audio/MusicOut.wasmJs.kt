@file:OptIn(ExperimentalWasmJsInterop::class)

package com.rm.infill.audio

import com.rm.infill.res.Res
import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

/**
 * The music in a browser: each piece fetched and decoded with Web Audio, and
 * played through a gain of its own. Like the synth, it starts sounding on
 * the first touch, click or key.
 */
internal actual object MusicOut {
    actual suspend fun load(deck: Int, path: String): Boolean =
        runCatching { loadPiece(deck, Res.getUri(path)).await<JsBoolean>().toBoolean() }.getOrDefault(false)

    actual fun start(deck: Int, volume: Float, onEnd: () -> Unit) = startPiece(deck, volume.toDouble(), onEnd)

    actual fun volume(deck: Int, volume: Float) = setVolume(deck, volume.toDouble())

    actual fun stop(deck: Int) = stopPiece(deck)

    actual fun pause(paused: Boolean) = pauseAll(paused)
}

private fun loadPiece(deck: Int, url: String): Promise<JsBoolean> = js("""{
    const m = globalThis.__infillMusic || (globalThis.__infillMusic = { ctx: null, decks: [{}, {}], paused: false });
    if (!m.ctx) {
        try { m.ctx = new AudioContext(); } catch (e) { return Promise.resolve(false); }
        const wake = () => { if (m.ctx.state === 'suspended' && !m.paused) m.ctx.resume().catch(() => {}); };
        for (const type of ['pointerdown', 'keydown', 'touchend']) window.addEventListener(type, wake, { capture: true });
    }
    return fetch(url)
        .then((r) => { if (!r.ok) throw new Error(r.status); return r.arrayBuffer(); })
        .then((data) => m.ctx.decodeAudioData(data))
        .then((buffer) => { m.decks[deck].buffer = buffer; return true; })
        .catch(() => false);
}""")

private fun startPiece(deck: Int, volume: Double, onEnd: () -> Unit): Unit = js("""{
    const m = globalThis.__infillMusic;
    if (!m || !m.ctx) return;
    const d = m.decks[deck];
    if (!d.buffer) return;
    const gain = m.ctx.createGain();
    gain.gain.value = volume;
    gain.connect(m.ctx.destination);
    const source = m.ctx.createBufferSource();
    source.buffer = d.buffer;
    source.connect(gain);
    source.onended = () => { if (d.source === source) { d.source = null; onEnd(); } };
    source.start();
    d.source = source;
    d.gain = gain;
}""")

private fun setVolume(deck: Int, volume: Double): Unit = js("""{
    const m = globalThis.__infillMusic;
    const d = m && m.decks[deck];
    if (d && d.gain) d.gain.gain.value = volume;
}""")

private fun stopPiece(deck: Int): Unit = js("""{
    const m = globalThis.__infillMusic;
    const d = m && m.decks[deck];
    if (!d) return;
    const s = d.source;
    d.source = null;
    if (s) { try { s.stop(); } catch (e) {} }
    if (d.gain) d.gain.disconnect();
    d.gain = null;
    d.buffer = null;
}""")

private fun pauseAll(paused: Boolean): Unit = js("""{
    const m = globalThis.__infillMusic;
    if (!m || !m.ctx) return;
    m.paused = paused;
    if (paused) m.ctx.suspend().catch(() => {}); else m.ctx.resume().catch(() => {});
}""")
