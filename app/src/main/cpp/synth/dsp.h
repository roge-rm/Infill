// The building blocks of every sound: noise, filters, oscillators, envelopes and resonators.
// Header-only, no allocation, no platform, so the audio callback and the sound gallery share it.
#pragma once

#include <cmath>
#include <cstdint>
#include <algorithm>

namespace infill {

constexpr float kPi = 3.14159265358979f;
constexpr float kTwoPi = 6.28318530717959f;

inline float clampf(float x, float lo, float hi) { return std::min(hi, std::max(lo, x)); }
inline float lerpf(float a, float b, float t) { return a + (b - a) * t; }
inline float dbToGain(float db) { return std::pow(10.0f, db / 20.0f); }

/** Deterministic noise, so a voice seeded the same sounds the same on every render. */
struct Rng {
    uint32_t state = 0x9E3779B9u;
    void seed(uint32_t s) { state = s ? s : 0x9E3779B9u; }
    uint32_t next() {
        uint32_t x = state;
        x ^= x << 13; x ^= x >> 17; x ^= x << 5;
        return state = x;
    }
    /** -1..1 */
    float white() { return (next() >> 8) * (2.0f / 16777216.0f) - 1.0f; }
    /** 0..1 */
    float uniform() { return (next() >> 8) * (1.0f / 16777216.0f); }
};

/** Pink noise, Paul Kellet's economy filter: equal energy per octave, for rumble and rush. */
struct Pink {
    float b0 = 0, b1 = 0, b2 = 0;
    float next(float white) {
        b0 = 0.99765f * b0 + white * 0.0990460f;
        b1 = 0.96300f * b1 + white * 0.2965164f;
        b2 = 0.57000f * b2 + white * 1.0526913f;
        return (b0 + b1 + b2 + white * 0.1848f) * 0.2f;
    }
};

/**
 * Brown noise: leaky integrated white, the roll under an engine. The leak puts its corner near
 * 75 Hz, since lower is lost on phone speakers and drifts toward DC.
 */
struct Brown {
    float y = 0;
    float next(float white) {
        y = (y + 0.05f * white) * 0.99f;
        return y * 3.0f;
    }
};

/**
 * Zero for a value too small to matter. Decaying feedback ends in denormals, which are very slow on
 * some CPUs, and WebAssembly can't switch them off.
 */
inline float flush(float x) { return (x > -1e-15f && x < 1e-15f) ? 0.0f : x; }

/** Takes out DC and anything below hearing. */
struct DcBlock {
    float x1 = 0, y1 = 0;
    float next(float x) {
        float y = flush(x - x1 + 0.997f * y1);
        x1 = x; y1 = y;
        return y;
    }
};

/** A value eased toward where it's told to be, so nothing clicks. */
struct Smooth {
    float value = 0;
    float coeff = 0.001f;
    void setTime(float seconds, float sampleRate) {
        coeff = 1.0f - std::exp(-1.0f / std::max(1.0f, seconds * sampleRate));
    }
    float next(float target) { value += (target - value) * coeff; return value; }
};

/** Simper's state-variable filter: low, band and high pass at once, stable when swept. */
struct Svf {
    float ic1 = 0, ic2 = 0;
    float g = 0, k = 1, a1 = 0, a2 = 0, a3 = 0;
    float low = 0, band = 0, high = 0;
    void set(float freq, float q, float sampleRate) {
        freq = clampf(freq, 10.0f, sampleRate * 0.45f);
        g = std::tan(kPi * freq / sampleRate);
        k = 1.0f / std::max(0.05f, q);
        a1 = 1.0f / (1.0f + g * (g + k));
        a2 = g * a1;
        a3 = g * a2;
    }
    void process(float x) {
        float v3 = x - ic2;
        float v1 = a1 * ic1 + a2 * v3;
        float v2 = ic2 + a2 * ic1 + a3 * v3;
        ic1 = 2 * v1 - ic1;
        ic2 = 2 * v2 - ic2;
        low = v2; band = v1; high = x - k * v1 - v2;
    }
};

/** A one-pole low pass, for cheap distance and air absorption. */
struct OnePole {
    float y = 0, a = 1;
    void set(float freq, float sampleRate) { a = 1.0f - std::exp(-kTwoPi * clampf(freq, 5.0f, sampleRate * 0.49f) / sampleRate); }
    float next(float x) { y += (x - y) * a; return y; }
};

/** PolyBLEP: rounds off a saw's or square's step so it doesn't alias into a whistle. */
inline float polyBlep(float t, float dt) {
    if (t < dt) { t /= dt; return t + t - t * t - 1.0f; }
    if (t > 1.0f - dt) { t = (t - 1.0f) / dt; return t * t + t + t + 1.0f; }
    return 0.0f;
}

struct Osc {
    float phase = 0;
    float sine(float freq, float sampleRate) {
        phase += freq / sampleRate; phase -= std::floor(phase);
        return std::sin(kTwoPi * phase);
    }
    float saw(float freq, float sampleRate) {
        float dt = freq / sampleRate;
        phase += dt; phase -= std::floor(phase);
        return 2.0f * phase - 1.0f - polyBlep(phase, dt);
    }
    float pulse(float freq, float width, float sampleRate) {
        float dt = freq / sampleRate;
        phase += dt; phase -= std::floor(phase);
        float v = phase < width ? 1.0f : -1.0f;
        v += polyBlep(phase, dt);
        float t2 = phase - width; t2 -= std::floor(t2);
        v -= polyBlep(t2, dt);
        return v;
    }
};

/** Attack, then an exponential fall: the shape of every knock, crack and thud. */
struct Decay {
    float value = 0, attack = 0, release = 0, level = 0;
    bool rising = false;
    void trigger(float attackSeconds, float releaseSeconds, float sampleRate, float peak = 1.0f) {
        attack = 1.0f / std::max(1.0f, attackSeconds * sampleRate);
        release = std::exp(-1.0f / std::max(1.0f, releaseSeconds * sampleRate));
        level = peak;
        rising = true;
    }
    float next() {
        if (rising) {
            value += attack * level;
            if (value >= level) { value = level; rising = false; }
        } else {
            value *= release;
        }
        return value;
    }
    bool done() const { return !rising && value < 1e-4f; }
};

/** A few tuned ringing band passes that give a hit its material: tank, rock, earth. */
struct Resonators {
    static constexpr int kMax = 6;
    Svf band[kMax];
    float gain[kMax] = {};
    int count = 0;
    void set(int n, const float* freqs, const float* qs, const float* gains, float sampleRate) {
        count = std::min(n, kMax);
        for (int i = 0; i < count; ++i) { band[i].set(freqs[i], qs[i], sampleRate); gain[i] = gains[i]; }
    }
    float process(float x) {
        float y = 0;
        for (int i = 0; i < count; ++i) { band[i].process(x); y += band[i].band * gain[i]; }
        return y;
    }
};

/** Sparse random clicks for fire, plasma and solid motors. [rate] is clicks per second. */
struct Crackle {
    float env = 0;
    float sign = 1;
    float next(Rng& rng, float rate, float sampleRate) {
        if (rng.uniform() < rate / sampleRate) { env = 0.4f + 0.6f * rng.uniform(); sign = rng.white() > 0 ? 1.0f : -1.0f; }
        env *= 0.93f;
        return env * sign * (0.5f + 0.5f * rng.white());
    }
};

/** Equal-power panning, -1 left to 1 right. */
inline void panGains(float pan, float& left, float& right) {
    float a = (clampf(pan, -1.0f, 1.0f) + 1.0f) * 0.25f * kPi;
    left = std::cos(a);
    right = std::sin(a);
}

/** A soft clip that keeps a hot mix from breaking up harshly. */
inline float softClip(float x) {
    if (x > 1.5f) return 1.0f;
    if (x < -1.5f) return -1.0f;
    return x - (4.0f / 27.0f) * x * x * x;
}

}  // namespace infill
