// The phone's synth (app/src/main/cpp/synth) with a plain C face, built as WebAssembly for the
// page's AudioWorklet (synth-worklet.js). Everything runs on the worklet's one thread: it writes
// the page's messages in here and synth_render() mixes a block.
#include <emscripten/emscripten.h>

#include <memory>

#include "synth/synth.h"

namespace {

std::unique_ptr<infill::Synth> synth;

// Where the worklet writes its input, and where a block is mixed to.
int keys[infill::kMaxSceneEntries];
int recipes[infill::kMaxSceneEntries];
int flags[infill::kMaxSceneEntries];
float params[infill::kMaxSceneEntries * infill::kParams];
float gains[infill::bus::COUNT];
float out[2 * 1024];

}  // namespace

extern "C" {

EMSCRIPTEN_KEEPALIVE void synth_make(float sampleRate, int voiceBudget) {
    synth = std::make_unique<infill::Synth>(sampleRate, voiceBudget);
}

EMSCRIPTEN_KEEPALIVE int* synth_keys() { return keys; }
EMSCRIPTEN_KEEPALIVE int* synth_recipes() { return recipes; }
EMSCRIPTEN_KEEPALIVE int* synth_flags() { return flags; }
EMSCRIPTEN_KEEPALIVE float* synth_params() { return params; }
EMSCRIPTEN_KEEPALIVE float* synth_gains() { return gains; }
EMSCRIPTEN_KEEPALIVE float* synth_out() { return out; }
EMSCRIPTEN_KEEPALIVE int synth_max_scene() { return infill::kMaxSceneEntries; }
EMSCRIPTEN_KEEPALIVE int synth_param_count() { return infill::kParams; }
EMSCRIPTEN_KEEPALIVE int synth_bus_count() { return infill::bus::COUNT; }

/** The held sounds, [count] of them, from [keys], [recipes], [flags] and [params]. */
EMSCRIPTEN_KEEPALIVE void synth_scene(int count) {
    if (!synth) return;
    infill::Scene scene;
    scene.count = count < infill::kMaxSceneEntries ? count : infill::kMaxSceneEntries;
    for (int i = 0; i < scene.count; ++i) {
        scene.entries[i].key = keys[i];
        scene.entries[i].recipe = recipes[i];
        scene.entries[i].flags = flags[i];
        for (int j = 0; j < infill::kParams; ++j) scene.entries[i].p[j] = params[i * infill::kParams + j];
    }
    synth->publishScene(scene);
}

/** A one-shot, with its parameters in the first row of [params]. */
EMSCRIPTEN_KEEPALIVE void synth_event(int recipe, int eventFlags, int seed, float delay) {
    if (!synth) return;
    infill::Event event;
    event.recipe = recipe;
    event.flags = eventFlags;
    event.seed = static_cast<uint32_t>(seed);
    event.delay = delay;
    for (int j = 0; j < infill::kParams; ++j) event.p[j] = params[j];
    synth->pushEvent(event);
}

/** The buses' loudness, from [gains]. */
EMSCRIPTEN_KEEPALIVE void synth_bus_gains() {
    if (synth) synth->setBusGains(gains);
}

EMSCRIPTEN_KEEPALIVE void synth_room(float amount) {
    if (synth) synth->setRoom(amount);
}

EMSCRIPTEN_KEEPALIVE int synth_voices() {
    return synth ? synth->activeVoices() : 0;
}

/** [frames] stereo frames, interleaved, into [out]. */
EMSCRIPTEN_KEEPALIVE void synth_render(int frames) {
    if (!synth) return;
    if (frames > 1024) frames = 1024;
    synth->render(out, frames);
}

}  // extern "C"
