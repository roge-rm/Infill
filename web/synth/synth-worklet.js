// The synth's audio thread on a web page. The page sends the synth's WebAssembly over first, then
// the game's calls as messages (the scene each frame, one-shots, the buses, the room), and this
// mixes a block whenever the browser asks. It's the same synth a phone runs, one thread here.

const WASI = { fd_write: () => 0, fd_close: () => 0, fd_seek: () => 0 };

class InfillSynth extends AudioWorkletProcessor {
  constructor(options) {
    super();
    this.synth = null;
    this.waiting = [];
    this.blocks = 0;
    const { wasm, voiceBudget } = options.processorOptions;
    WebAssembly.instantiate(wasm, { wasi_snapshot_preview1: WASI }).then(({ instance }) => {
      const e = instance.exports;
      e._initialize();
      e.synth_make(sampleRate, voiceBudget);
      this.synth = e;
      this.params = e.synth_param_count();
      this.maxScene = e.synth_max_scene();
      for (const m of this.waiting) this.take(m);
      this.waiting = null;
      this.port.postMessage({ ready: true });
    }, (error) => this.port.postMessage({ failed: String(error) }));
    this.port.onmessage = (event) => {
      if (this.synth) this.take(event.data);
      else if (event.data.type !== 'scene') this.waiting.push(event.data);
    };
  }

  // The memory can't grow, so these views stay good once made, but they're cheap to make anyway.
  f32(at, length) { return new Float32Array(this.synth.memory.buffer, at, length); }
  i32(at, length) { return new Int32Array(this.synth.memory.buffer, at, length); }

  take(m) {
    const e = this.synth;
    switch (m.type) {
      case 'scene': {
        const count = Math.min(m.count, this.maxScene);
        this.i32(e.synth_keys(), count).set(m.keys.subarray(0, count));
        this.i32(e.synth_recipes(), count).set(m.recipes.subarray(0, count));
        this.i32(e.synth_flags(), count).set(m.flags.subarray(0, count));
        this.f32(e.synth_params(), count * this.params).set(m.params.subarray(0, count * this.params));
        e.synth_scene(count);
        break;
      }
      case 'event':
        this.f32(e.synth_params(), this.params).set(m.params.subarray(0, this.params));
        e.synth_event(m.recipe, m.flags, m.seed, m.delay);
        break;
      case 'gains':
        this.f32(e.synth_gains(), m.gains.length).set(m.gains);
        e.synth_bus_gains();
        break;
      case 'room':
        e.synth_room(m.amount);
        break;
    }
  }

  process(inputs, outputs) {
    const out = outputs[0];
    if (!this.synth || out.length < 2) return true;
    const frames = out[0].length;
    this.synth.synth_render(frames);
    const mixed = this.f32(this.synth.synth_out(), frames * 2);
    const left = out[0], right = out[1];
    for (let n = 0; n < frames; n++) {
      left[n] = mixed[2 * n];
      right[n] = mixed[2 * n + 1];
    }
    // How many voices are sounding, now and then, for the debug overlay.
    if (++this.blocks % 64 === 0) this.port.postMessage({ voices: this.synth.synth_voices() });
    return true;
  }
}

registerProcessor('infill-synth', InfillSynth);
