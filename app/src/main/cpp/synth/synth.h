// The sound engine short of the device: voices, mixer and room.
//
// Two threads, no locks. Once a frame the game thread publishes the scene (every held sound and its
// parameters) through a triple buffer and pushes one-shots into a single-producer ring. The audio
// thread takes the newest scene, eases voices toward it, starts one-shots and mixes. Nothing on the
// audio thread allocates.
#pragma once

#include <atomic>
#include <cstdint>
#include <vector>

#include "dsp.h"
#include "recipes.h"

namespace infill {

constexpr int kParams = 9;
constexpr int kMaxVoices = 64;
constexpr int kMaxSceneEntries = 64;
constexpr int kEventSlots = 128;

/**
 * Shared parameters after the recipe's own: loudness (0 means 1), pan, distance filter cutoff
 * (0 for none) and Doppler pitch.
 */
constexpr int P_GAIN = 5;
constexpr int P_PAN = 6;
constexpr int P_LOWPASS = 7;
/** Doppler frequency factor (0 means 1). Engines and wheels only. */
constexpr int P_PITCH = 8;

/** One held sound, as the game describes it. */
struct SceneEntry {
    int32_t key = 0;
    int32_t recipe = 0;
    int32_t flags = 0;
    float p[kParams] = {};
};

struct Scene {
    int count = 0;
    SceneEntry entries[kMaxSceneEntries];
};

/** A one-shot, as the game describes it, [delay] seconds before it starts. */
struct Event {
    int32_t recipe = 0;
    int32_t flags = 0;
    uint32_t seed = 0;
    float delay = 0;
    float p[kParams] = {};
};

/** One voice: whatever DSP any recipe needs, so the pool is one fixed size. */
struct Voice {
    bool active = false;
    bool held = false;       // continuous, and still in the scene
    int32_t key = 0;
    int32_t recipe = 0;
    int32_t flags = 0;
    float target[kParams] = {};
    Smooth param[kParams];
    Smooth fade;             // in when started, out when dropped
    int delaySamples = 0;
    float age = 0;           // seconds since it started sounding
    float loudness = 0;      // recent level, for choosing which one to steal

    Rng rng;
    Pink pink, pink2;
    Brown brown, brown2;
    Svf f[5];
    Osc osc[3];
    Decay env[3];
    Resonators res;
    Crackle crackle;
    OnePole distance;
    DcBlock dc;
    float state[8] = {};     // recipe scratch: timers, glides, sweeps
    // Worked out at control rate: pan, a rocket's shaped output, a splash's bubbling.
    float panLeft = 0.7071f, panRight = 0.7071f;
    float shaped = 0;
    float bubbling = 0;
};

class Synth {
public:
    explicit Synth(float sampleRate, int voiceBudget = kMaxVoices);

    /** Game thread: the held sounds this frame. */
    void publishScene(const Scene& scene);

    /** Game thread: a one-shot. False if the queue is full (it gets dropped). */
    bool pushEvent(const Event& event);

    /** Game thread: loudness per bus, 0..1 each. */
    void setBusGains(const float* gains);

    /** Game thread: how much reverb, more inside a hull. */
    void setRoom(float amount) { roomTarget_.store(amount); }

    /** Audio thread: [frames] stereo frames, interleaved, into [out]. */
    void render(float* out, int frames);

    float sampleRate() const { return sampleRate_; }

    /** Only off to measure recipes' raw levels in the gallery. */
    bool limiting = true;
    int activeVoices() const;

private:
    void takeScene();
    void takeEvents();
    Voice* allocate();
    void startVoice(Voice& v, int recipe, int flags, const float* p, uint32_t seed, bool held);
    void renderVoice(Voice& v, float& left, float& right);
    float reverb(float in, int channel);

    float sampleRate_;

    float tearDecay_ = 1.0f;  // a tear's sweep down, per sample, worked out once
    int voiceBudget_;
    Voice voices_[kMaxVoices];

    // Triple buffer. The writer swaps its filled buffer into [ready_] with the fresh bit (4) set;
    // the reader, seeing the bit, swaps its old one in. Index and bit share one atomic, so neither
    // side sees a half-made swap.
    Scene buffers_[3];
    int writing_ = 0;
    int reading_ = 1;
    std::atomic<int> ready_{2};

    Event events_[kEventSlots];
    std::atomic<uint32_t> eventHead_{0};
    std::atomic<uint32_t> eventTail_{0};

    std::atomic<float> busTarget_[bus::COUNT];
    Smooth bus_[bus::COUNT];
    std::atomic<float> roomTarget_{0.15f};
    Smooth room_;

    // A small room: four combs and two all-passes a side.
    std::vector<float> comb_[2][4];
    int combIndex_[2][4] = {};
    float combFilter_[2][4] = {};
    std::vector<float> allpass_[2][2];
    int allpassIndex_[2][2] = {};

    float limiterGain_ = 1.0f;
    uint32_t nextSeed_ = 1;
};

}  // namespace infill
