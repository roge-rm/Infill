@file:OptIn(ExperimentalWasmJsInterop::class)

package com.rm.infill.audio

import kotlin.js.ExperimentalWasmJsInterop

/**
 * The synth in a browser: the phone's synth built as WebAssembly (web/synth) in an AudioWorklet,
 * each call sent over as a message. Silent if there's no synth.wasm or no Web Audio.
 */
internal actual object Synth {
    private var voices = 0

    actual fun load(): Boolean = hasWebAudio()

    actual fun nativeActiveVoices(): Int = voices

    actual fun nativeStart(voiceBudget: Int): Boolean {
        start(voiceBudget) { voices = it }
        return true
    }

    actual fun nativeStop() = stop()

    actual fun nativePause(paused: Boolean) = pause(paused)

    actual fun nativeScene(count: Int, keys: IntArray, recipes: IntArray, flags: IntArray, params: FloatArray) {
        val n = minOf(count, keys.size, recipes.size, flags.size)
        val jsKeys = ints(n)
        val jsRecipes = ints(n)
        val jsFlags = ints(n)
        for (i in 0 until n) {
            setInt(jsKeys, i, keys[i])
            setInt(jsRecipes, i, recipes[i])
            setInt(jsFlags, i, flags[i])
        }
        val p = minOf(params.size, n * PARAMS)
        val jsParams = floats(p)
        for (i in 0 until p) setFloat(jsParams, i, params[i])
        sendScene(n, jsKeys, jsRecipes, jsFlags, jsParams)
    }

    actual fun nativeEvent(recipe: Int, flags: Int, seed: Int, delay: Float, params: FloatArray) {
        val jsParams = floats(params.size)
        for (i in params.indices) setFloat(jsParams, i, params[i])
        sendEvent(recipe, flags, seed, delay.toDouble(), jsParams)
    }

    actual fun nativeBusGains(gains: FloatArray) {
        val jsGains = floats(gains.size)
        for (i in gains.indices) setFloat(jsGains, i, gains[i])
        sendGains(jsGains)
    }

    actual fun nativeRoom(amount: Float) = sendRoom(amount.toDouble())

    /** The synth's parameters per sound (kParams in synth.h). */
    private const val PARAMS = 9
}

private fun hasWebAudio(): Boolean = js("typeof AudioWorkletNode !== 'undefined'")

private fun ints(size: Int): JsAny = js("new Int32Array(size)")

private fun floats(size: Int): JsAny = js("new Float32Array(size)")

private fun setInt(array: JsAny, index: Int, value: Int): Unit = js("array[index] = value")

private fun setFloat(array: JsAny, index: Int, value: Float): Unit = js("array[index] = value")

/**
 * Makes the context and worklet, queueing calls until it's ready (except scenes, which come every
 * frame). Browsers won't play until the page is touched, so the first touch, click or key starts it.
 */
private fun start(voiceBudget: Int, onVoices: (Int) -> Unit): Unit = js("""{
    const s = globalThis.__infillSynth || (globalThis.__infillSynth = { queue: [], node: null, ctx: null });
    if (s.ctx) return;
    s.send = (message, transfer) => {
        if (s.node) s.node.port.postMessage(message, transfer || []);
        else if (message.type !== 'scene') s.queue.push(message);
    };
    try {
        s.ctx = new AudioContext({ latencyHint: 'interactive' });
    } catch (e) {
        console.warn('InfillAudio: no audio context', e);
        return;
    }
    const wake = () => { if (s.ctx && s.ctx.state === 'suspended' && !s.paused) s.ctx.resume().catch(() => {}); };
    for (const type of ['pointerdown', 'keydown', 'touchend']) window.addEventListener(type, wake, { capture: true });
    const ctx = s.ctx;
    Promise.all([
        fetch('synth.wasm').then((r) => { if (!r.ok) throw new Error('synth.wasm ' + r.status); return r.arrayBuffer(); }),
        ctx.audioWorklet.addModule('synth-worklet.js'),
    ]).then(([wasm]) => {
        if (s.ctx !== ctx) return;
        const node = new AudioWorkletNode(ctx, 'infill-synth', {
            numberOfInputs: 0,
            numberOfOutputs: 1,
            outputChannelCount: [2],
            processorOptions: { wasm, voiceBudget },
        });
        node.port.onmessage = (event) => {
            if (event.data.voices !== undefined) onVoices(event.data.voices);
            else if (event.data.failed) console.warn('InfillAudio: synth failed', event.data.failed);
            else if (event.data.ready) console.info('InfillAudio: synth running at ' + ctx.sampleRate + ' Hz');
        };
        node.connect(ctx.destination);
        s.node = node;
        for (const m of s.queue) node.port.postMessage(m);
        s.queue = [];
        wake();
    }).catch((e) => console.warn('InfillAudio: silent', e));
}""")

private fun stop(): Unit = js("""{
    const s = globalThis.__infillSynth;
    if (!s || !s.ctx) return;
    s.ctx.close().catch(() => {});
    s.ctx = null;
    s.node = null;
    s.queue = [];
}""")

private fun pause(paused: Boolean): Unit = js("""{
    const s = globalThis.__infillSynth;
    if (!s || !s.ctx) return;
    s.paused = paused;
    if (paused) s.ctx.suspend().catch(() => {}); else s.ctx.resume().catch(() => {});
}""")

private fun sendScene(count: Int, keys: JsAny, recipes: JsAny, flags: JsAny, params: JsAny): Unit = js("""{
    const s = globalThis.__infillSynth;
    if (s && s.send) s.send({ type: 'scene', count, keys, recipes, flags, params }, [keys.buffer, recipes.buffer, flags.buffer, params.buffer]);
}""")

private fun sendEvent(recipe: Int, flags: Int, seed: Int, delay: Double, params: JsAny): Unit = js("""{
    const s = globalThis.__infillSynth;
    if (s && s.send) s.send({ type: 'event', recipe, flags, seed, delay, params });
}""")

private fun sendGains(gains: JsAny): Unit = js("""{
    const s = globalThis.__infillSynth;
    if (s && s.send) s.send({ type: 'gains', gains });
}""")

private fun sendRoom(amount: Double): Unit = js("""{
    const s = globalThis.__infillSynth;
    if (s && s.send) s.send({ type: 'room', amount });
}""")
