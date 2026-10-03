#include "synth.h"

#include <cstring>

namespace infill {

namespace {

/** Samples between working out a voice's filter coefficients again. */
constexpr int kControlInterval = 32;
// How loud the harbour's lapping is, set by ear against the engines.
constexpr float kPortWater = 0.75f;

bool continuous(int r) { return r <= recipe::LAST_CONTINUOUS; }

/** How long a one-shot is allowed to ring, in seconds, before it's cut. */
float lifetime(int r, const float* p) {
    switch (r) {
        case recipe::IMPACT: return (p[1] == material::METAL ? 2.0f : 0.9f) + p[2];
        case recipe::CRUNCH: return 1.1f;
        case recipe::EXPLOSION: return 2.5f + 4.0f * p[0];
        case recipe::SPLASH: return 1.6f;
        case recipe::THUNDER: return 9.0f;
        case recipe::CLICK: return 0.08f;
        case recipe::CAUTION: return 0.36f;
        case recipe::CLUNK: return 0.7f;
        case recipe::SLAP: return 0.6f;
        default: return 1.0f;
    }
}

/** A factor near 1, [amount] either way, from the voice's seed, so repeated hits vary. */
float vary(Rng& rng, float amount) { return 1.0f + amount * (2.0f * rng.uniform() - 1.0f); }

/**
 * The material's ringing modes, scaled for [size]. Given [rng], each is nudged in pitch and level.
 */
void setMaterial(Resonators& res, int m, float size, float sr, Rng* rng = nullptr) {
    float scale = 1.4f - 0.8f * clampf(size, 0.0f, 1.0f);
    float f[6], q[6], g[6];
    int n;
    switch (m) {
        case material::METAL: {
            const float F[] = {210, 530, 1240, 2650, 4100}, Q[] = {25, 30, 35, 40, 40}, G[] = {1, .8f, .6f, .4f, .25f};
            n = 5; std::memcpy(f, F, sizeof F); std::memcpy(q, Q, sizeof Q); std::memcpy(g, G, sizeof G); break;
        }
        case material::ROCK: {
            const float F[] = {95, 260, 700, 1600}, Q[] = {6, 8, 10, 12}, G[] = {1, .7f, .5f, .3f};
            n = 4; std::memcpy(f, F, sizeof F); std::memcpy(q, Q, sizeof Q); std::memcpy(g, G, sizeof G); break;
        }
        case material::WOOD: {
            const float F[] = {180, 420, 900}, Q[] = {10, 12, 14}, G[] = {1, .6f, .35f};
            n = 3; std::memcpy(f, F, sizeof F); std::memcpy(q, Q, sizeof Q); std::memcpy(g, G, sizeof G); break;
        }
        case material::SAND: case material::SNOW: {
            const float F[] = {60, 170}, Q[] = {1.3f, 1.6f}, G[] = {1, .5f};
            n = 2; std::memcpy(f, F, sizeof F); std::memcpy(q, Q, sizeof Q); std::memcpy(g, G, sizeof G); break;
        }
        default: {  // earth
            const float F[] = {60, 150, 380}, Q[] = {2, 3, 3}, G[] = {1, .6f, .3f};
            n = 3; std::memcpy(f, F, sizeof F); std::memcpy(q, Q, sizeof Q); std::memcpy(g, G, sizeof G); break;
        }
    }
    float overall = rng ? vary(*rng, 0.06f) : 1.0f;
    for (int i = 0; i < n; ++i) {
        f[i] *= scale * overall * (rng ? vary(*rng, 0.03f) : 1.0f);
        if (rng) g[i] *= vary(*rng, 0.3f);
    }
    res.set(n, f, q, g, sr);
}

void setMetal(Resonators& res, float scale, float sr, Rng* rng = nullptr) { setMaterial(res, material::METAL, 1.4f - scale, sr, rng); }

/**
 * Each recipe's level, measured in the gallery with the limiter off. A held sound at full strength
 * sits near -18 dBFS RMS and a one-shot peaks near -6, so a full scene mixes without the limiter
 * working. Engines sit lower, near -26, since they run for minutes. Wind and airflow sit about 12
 * dB under the rest.
 *
 * Listening fatigue is the rule: listenable over accurate. `./gradlew :app:soundGallery -Praw`
 * prints phone-speaker LUFS and energy per octave. Engines at full power sit at -29..-31 there,
 * with the rocket as reference. Long ambient sounds sit 4 dB or more under them, alerts at most
 * about 5 dB over. Held sounds keep their energy under about 1.5 kHz, with 4 kHz and up at least 10
 * dB under the loudest octave. Anything that repeats varies and never makes a beat.
 */
float trim(int r) {
    switch (r) {
        case recipe::JET: return 0.115f;   // just under a rocket, because flights are long
        case recipe::PROP: return 0.22f;
        case recipe::WIND: return 0.1f;
        case recipe::RAIN: return 0.24f;  // under the engines, because it lasts
        case recipe::FIRE: return 0.19f;
        case recipe::OUTBOARD: return 0.27f;
        case recipe::SURF: return 0.33f;  // ambient, so well under the engines
        case recipe::SEA: return 0.39f;  // long, so 6-7 dB under the engines when rough
        case recipe::COMPLEX: return 0.3f;  // background
        case recipe::PORT: return 0.3f;
        case recipe::IMPACT: return 0.2f;
        case recipe::CRUNCH: return 0.09f;
        case recipe::EXPLOSION: return 0.21f;
        case recipe::SPLASH: return 0.7f;
        case recipe::THUNDER: return 0.19f;
        case recipe::CLICK: return 0.15f;  // just a hint of a tick
        case recipe::CAUTION: return 0.8f;  // a clear step over the engines
        case recipe::CLUNK: return 0.25f;
        case recipe::SLAP: return 0.2f;  // a knock under the engines
        default: return 1.0f;
    }
}

/** Metal rings far harder than earth thuds, so each material has its own balance in an impact. */
float materialGain(int m) {
    switch (m) {
        case material::METAL: return 0.45f;
        case material::ROCK: return 1.0f;
        case material::WOOD: return 0.8f;
        case material::SAND: case material::SNOW: return 2.2f;
        default: return 1.8f;  // earth
    }
}

}  // namespace

Synth::Synth(float sampleRate, int voiceBudget)
    : sampleRate_(sampleRate), voiceBudget_(std::min(voiceBudget, kMaxVoices)) {
    tearDecay_ = std::pow(600.0f / 3200.0f, (1.0f / sampleRate) / 0.5f);
    for (int b = 0; b < bus::COUNT; ++b) {
        busTarget_[b].store(1.0f);
        bus_[b].setTime(0.05f, sampleRate);
        bus_[b].value = 1.0f;
    }
    room_.setTime(0.3f, sampleRate);
    room_.value = 0.15f;
    // Freeverb's tunings, scaled to this rate, with one side offset for width.
    const int combs[4] = {1116, 1188, 1277, 1356};
    const int allpasses[2] = {556, 441};
    float scale = sampleRate / 44100.0f;
    for (int c = 0; c < 2; ++c) {
        for (int i = 0; i < 4; ++i) comb_[c][i].assign(static_cast<size_t>((combs[i] + c * 23) * scale), 0.0f);
        for (int i = 0; i < 2; ++i) allpass_[c][i].assign(static_cast<size_t>((allpasses[i] + c * 23) * scale), 0.0f);
    }
}

void Synth::publishScene(const Scene& scene) {
    buffers_[writing_] = scene;
    writing_ = ready_.exchange(writing_ | 4) & 3;
}

bool Synth::pushEvent(const Event& event) {
    uint32_t head = eventHead_.load(std::memory_order_relaxed);
    uint32_t tail = eventTail_.load(std::memory_order_acquire);
    if (head - tail >= kEventSlots) return false;
    events_[head % kEventSlots] = event;
    eventHead_.store(head + 1, std::memory_order_release);
    return true;
}

void Synth::setBusGains(const float* gains) {
    for (int b = 0; b < bus::COUNT; ++b) busTarget_[b].store(clampf(gains[b], 0.0f, 2.0f));
}

int Synth::activeVoices() const {
    int n = 0;
    for (int i = 0; i < voiceBudget_; ++i) if (voices_[i].active) ++n;
    return n;
}

Voice* Synth::allocate() {
    Voice* quietest = nullptr;
    for (int i = 0; i < voiceBudget_; ++i) {
        Voice& v = voices_[i];
        if (!v.active) return &v;
        if (!quietest || v.loudness < quietest->loudness) quietest = &v;
    }
    // Full, so the quietest makes way.
    return quietest;
}

void Synth::startVoice(Voice& v, int r, int flags, const float* p, uint32_t seed, bool held) {
    float sr = sampleRate_;
    v = Voice();
    v.active = true;
    v.held = held;
    v.recipe = r;
    v.flags = flags;
    v.rng.seed(seed ? seed : nextSeed_++ * 2654435761u);
    for (int i = 0; i < kParams; ++i) {
        v.target[i] = p[i];
        v.param[i].setTime(0.04f, sr);
        v.param[i].value = p[i];
    }
    v.fade.setTime(continuous(r) ? 0.06f : 0.002f, sr);
    v.fade.value = continuous(r) ? 0.0f : 1.0f;
    v.distance.set(p[P_LOWPASS] > 0 ? p[P_LOWPASS] : 20000.0f, sr);

    switch (r) {
        case recipe::IMPACT: {
            float energy = p[0], size = p[2];
            int m = static_cast<int>(p[1] + 0.5f);
            setMaterial(v.res, m, size, sr, &v.rng);
            v.env[0].trigger(0.0005f, (0.004f + 0.02f * size) * vary(v.rng, 0.25f), sr);
            v.env[1].trigger(0.002f, (0.12f + 0.25f * size) * vary(v.rng, 0.2f), sr);
            v.state[0] = 150.0f * vary(v.rng, 0.12f);  // thump, sweeping down
            v.state[1] = std::min(1.5f, 0.25f + energy);
            v.f[0].set(m == material::ROCK ? 1600.0f : 900.0f, 0.7f, sr);
            break;
        }
        case recipe::CRUNCH:
            setMetal(v.res, 0.8f, sr, &v.rng);
            v.env[0].trigger(0.001f, 0.25f * vary(v.rng, 0.25f), sr);
            v.state[0] = vary(v.rng, 0.08f);
            v.state[1] = std::min(1.5f, 0.3f + p[0]);
            v.f[0].set(600.0f, 0.7f, sr);
            break;
        case recipe::EXPLOSION: {
            float size = clampf(p[0], 0.1f, 1.5f);
            v.env[0].trigger(0.0003f, 0.012f, sr);
            v.env[1].trigger(0.01f, (0.8f + 2.2f * size) * vary(v.rng, 0.2f), sr);
            v.env[2].trigger(0.2f, (2.5f + 2.0f * size) * vary(v.rng, 0.25f), sr);
            v.f[0].set(900.0f, 0.7f, sr);
            v.f[1].set(110.0f + 60.0f * (1.0f - size), 0.7f, sr);
            v.f[2].set(1500.0f, 1.2f, sr);
            v.f[3].set((420.0f - 120.0f * size) * vary(v.rng, 0.15f), 0.8f, sr);
            v.state[1] = 0.6f + 0.6f * size;
            break;
        }
        case recipe::SPLASH:
            v.env[0].trigger(0.002f, (0.08f + 0.2f * p[0]) * vary(v.rng, 0.25f), sr);
            v.f[0].set(1200.0f * vary(v.rng, 0.2f), 0.5f, sr);
            v.state[1] = std::min(1.2f, 0.3f + p[0]);
            break;
        case recipe::THUNDER: {
            float close = clampf(p[0], 0.0f, 1.0f);
            v.env[0].trigger(0.001f, 0.05f, sr, close * close);
            v.env[1].trigger(0.3f, (3.0f + 3.0f * (1.0f - close)) * vary(v.rng, 0.3f), sr);
            v.f[0].set(1200.0f, 0.7f, sr);
            v.f[1].set(90.0f + 60.0f * close, 0.7f, sr);
            v.f[2].set(260.0f + 200.0f * close, 0.8f, sr);
            v.state[0] = 0.7f;
            v.state[1] = 0.7f;
            break;
        }
        case recipe::CLICK:
            v.env[0].trigger(0.002f, 0.01f, sr);
            break;
        case recipe::CAUTION:
            break;
        case recipe::SLAP:
            // A hull meeting a wave: a short dull slap and the thump of the boat.
            v.env[0].trigger(0.001f, (0.04f + 0.08f * p[0]) * vary(v.rng, 0.25f), sr);
            v.env[1].trigger(0.002f, 0.12f * vary(v.rng, 0.2f), sr);
            v.f[0].set(480.0f * vary(v.rng, 0.2f), 0.6f, sr);
            v.state[0] = 80.0f * vary(v.rng, 0.15f);
            v.state[1] = std::min(1.0f, 0.3f + p[0]);
            break;
        case recipe::CLUNK: {
            float k = vary(v.rng, 0.08f);
            const float F[] = {300 * k, 800 * k * vary(v.rng, 0.03f), 1900 * k * vary(v.rng, 0.03f)}, Q[] = {15, 15, 18};
            const float G[] = {1, .6f * vary(v.rng, 0.3f), .3f * vary(v.rng, 0.3f)};
            v.res.set(3, F, Q, G, sr);
            v.env[0].trigger(0.0005f, 0.003f, sr);
            v.env[1].trigger(0.001f, 0.08f, sr);
            break;
        }
        default:
            break;
    }
}

void Synth::takeScene() {
    int ready = ready_.load(std::memory_order_acquire);
    if (!(ready & 4)) return;
    reading_ = ready_.exchange(reading_) & 3;
    const Scene& scene = buffers_[reading_];

    // Everything held is let go unless the scene still has it.
    for (int i = 0; i < voiceBudget_; ++i) if (voices_[i].active && continuous(voices_[i].recipe)) voices_[i].held = false;
    for (int e = 0; e < scene.count && e < kMaxSceneEntries; ++e) {
        const SceneEntry& entry = scene.entries[e];
        Voice* found = nullptr;
        for (int i = 0; i < voiceBudget_; ++i) {
            Voice& v = voices_[i];
            if (v.active && continuous(v.recipe) && v.key == entry.key && v.recipe == entry.recipe) { found = &v; break; }
        }
        if (!found) {
            found = allocate();
            startVoice(*found, entry.recipe, entry.flags, entry.p, static_cast<uint32_t>(entry.key) * 2654435761u, true);
            found->key = entry.key;
        }
        found->held = true;
        found->flags = entry.flags;
        std::memcpy(found->target, entry.p, sizeof entry.p);
    }
}

void Synth::takeEvents() {
    uint32_t tail = eventTail_.load(std::memory_order_relaxed);
    uint32_t head = eventHead_.load(std::memory_order_acquire);
    while (tail != head) {
        const Event& e = events_[tail % kEventSlots];
        Voice* v = allocate();
        startVoice(*v, e.recipe, e.flags, e.p, e.seed, false);
        v->delaySamples = static_cast<int>(e.delay * sampleRate_);
        ++tail;
    }
    eventTail_.store(tail, std::memory_order_release);
}

void Synth::renderVoice(Voice& v, float& left, float& right) {
    left = right = 0;
    if (v.delaySamples > 0) { --v.delaySamples; return; }
    const float sr = sampleRate_;
    const float dt = 1.0f / sr;
    float p[kParams];
    for (int i = 0; i < kParams; ++i) p[i] = v.param[i].next(v.target[i]);
    const bool control = (static_cast<int>(v.state[7]) % kControlInterval) == 0;
    v.state[7] += 1.0f;
    if (v.state[7] > 1e6f) v.state[7] = 0;
    Rng& rng = v.rng;
    float s = 0;
    // Doppler, for what moves. Engines and wheels shift every frequency they have.
    const float pf = p[P_PITCH] > 0 ? clampf(p[P_PITCH], 0.5f, 2.0f) : 1.0f;

    switch (v.recipe) {
        case recipe::JET: {
            float out = clampf(p[0], 0, 1), spool = clampf(p[1], 0, 1), air = clampf(p[2], 0, 1);
            // Heard from inside through the airframe: the turbine whine is muffled, well under the
            // roar, since a high whine tires you within a minute.
            float f = (260.0f + 1000.0f * spool) * pf;
            float tone = v.osc[0].sine(f, sr) * 0.12f + v.osc[1].sine(f * 2.01f, sr) * 0.03f + v.osc[2].sine(f * 0.5f, sr) * 0.1f;
            if (control) {
                v.f[0].set((300.0f + 700.0f * spool) * pf, 0.7f, sr);
                v.f[1].set((500.0f + 300.0f * air) * pf, 1.5f, sr);
                // The roar's body, low down where a phone speaker carries it.
                v.f[2].set((180.0f + 160.0f * spool) * pf, 0.8f, sr);
                // Everything through the hull, little above a kilohertz.
                v.f[3].set((900.0f + 500.0f * spool) * pf, 0.6f, sr);
            }
            float w = rng.white();
            v.f[0].process(v.pink.next(w));
            v.f[1].process(v.pink2.next(rng.white()));
            v.f[2].process(v.brown.next(w));
            float raw = tone * (0.3f + 0.7f * spool) + v.f[0].low * 1.2f + v.f[1].band * 0.5f * air + v.f[2].band * 1.5f;
            v.f[3].process(raw);
            s = v.f[3].low * out;
            break;
        }
        case recipe::PROP: {
            float out = clampf(p[0], 0, 1), rate = clampf(p[1], 5, 400) * pf, load = clampf(p[2], 0, 1);
            if (control) { v.f[0].set((900.0f + 900.0f * load) * pf, 0.8f, sr); v.f[1].set(300.0f * pf, 0.7f, sr); }
            v.f[0].process(v.osc[0].pulse(rate, 0.15f, sr));
            v.f[1].process(v.osc[1].saw(rate * 0.5f, sr) * (0.7f + 0.3f * rng.white()));
            s = (v.f[0].low * 0.5f + v.f[1].low * 0.5f + v.brown.next(rng.white()) * 0.15f) * out;
            break;
        }
        case recipe::WIND: {
            float loud = clampf(p[0], 0, 1.5f), gust = clampf(p[1], 0, 1), bright = clampf(p[2], 0, 1);
            // Gusts: a slow wander toward a new strength every second or so.
            v.state[2] -= dt;
            if (v.state[2] <= 0) { v.state[1] = rng.uniform(); v.state[2] = 0.4f + 1.2f * rng.uniform(); }
            v.state[0] += (v.state[1] - v.state[0]) * (dt / 0.6f);
            // Gusts swell and fall away, never a steady hiss.
            float swell = v.state[0] * v.state[0];
            float g = 0.15f + 0.85f * ((1.0f - gust) * 0.5f + gust * swell * 1.8f);
            if (control) { v.f[0].set(150.0f + 450.0f * bright + 350.0f * swell * gust, 1.2f, sr); v.f[1].set(110.0f, 0.8f, sr); }
            v.f[0].process(v.pink.next(rng.white()));
            v.f[1].process(v.brown.next(rng.white()));
            s = (v.f[0].band * 1.8f + v.f[1].low * 0.8f) * g * loud;
            break;
        }
        case recipe::RAIN: {
            float loud = clampf(p[0], 0, 1.5f), patter = clampf(p[1], 0, 1);
            // A soft wash with drops ticking in it, not a hiss, since it can last for minutes.
            if (control) { v.f[0].set(1600.0f, 0.6f, sr); v.f[1].set(2400.0f, 3.0f, sr); }
            v.f[0].process(v.pink.next(rng.white()));
            v.f[1].process(v.crackle.next(rng, 30.0f + 120.0f * patter, sr));
            s = (v.f[0].low * 0.8f + v.f[1].band * patter * 2.0f) * loud;
            break;
        }
        case recipe::FIRE: {
            float loud = clampf(p[0], 0, 1.5f);
            if (control) { v.f[0].set(2500.0f, 0.8f, sr); v.f[1].set(250.0f, 0.7f, sr); v.f[2].set(450.0f, 0.7f, sr); }
            v.f[0].process(v.crackle.next(rng, 60.0f, sr));
            v.f[1].process(v.brown.next(rng.white()));
            // The flames' own rush, flickering.
            v.state[0] += (rng.uniform() - v.state[0]) * (dt / 0.08f);
            v.f[2].process(v.pink.next(rng.white()));
            s = (v.f[0].band * 6.0f + v.f[1].low * 0.15f + v.f[2].band * 3.0f * (0.5f + v.state[0])) * loud;
            break;
        }
        case recipe::OUTBOARD: {
            // A low, uneven burble, never a buzz, with the propeller churning the water under it.
            // All kept well down and low pitched.
            float out = clampf(p[0], 0, 1), rev = clampf(p[1], 0, 1);
            float f = (16.0f + 38.0f * rev) * pf;
            if (control) {
                float corner = (320.0f + 220.0f * rev) * pf;
                v.f[0].set(corner, 0.7f, sr);
                v.f[3].set(corner, 0.7f, sr);
                v.f[2].set((350.0f + 250.0f * rev) * pf, 0.6f, sr);
                // The deepest thump taken off. It's felt more than heard and swamps good speakers.
                v.f[1].set(110.0f, 0.7f, sr);
            }
            float pulse = v.osc[0].pulse(f, 0.4f, sr);
            if (v.osc[0].phase < v.state[1]) v.state[0] = 0.55f + 0.45f * rng.uniform();
            v.state[1] = v.osc[0].phase;
            v.f[0].process(pulse * v.state[0]);
            v.f[3].process(v.f[0].low);
            v.f[2].process(v.brown.next(rng.white()));
            v.f[1].process(v.f[3].low * 0.9f + v.f[2].low * (0.3f + 0.5f * rev));
            s = v.f[1].high * out;
            break;
        }
        case recipe::SURF: {
            // Waves on the shore: a slow, low, soft swell and wash, since it lasts as long as you
            // stay. Each wave has its own length and strength, so there's no beat.
            float loud = clampf(p[0], 0, 1.5f);
            if (v.state[1] <= 0) { v.state[1] = 6.0f + 3.5f * rng.uniform(); v.state[2] = 0.6f + 0.4f * rng.uniform(); }
            v.state[0] += dt / v.state[1];
            if (v.state[0] >= 1.0f) {
                v.state[0] -= 1.0f;
                v.state[1] = 6.0f + 3.5f * rng.uniform();
                v.state[2] = 0.6f + 0.4f * rng.uniform();
            }
            float wave = std::pow(std::max(0.0f, std::sin(kTwoPi * v.state[0])), 3.0f) * v.state[2];
            if (control) { v.f[0].set(250.0f + 500.0f * wave, 0.6f, sr); v.f[1].set(700.0f + 400.0f * wave, 0.7f, sr); }
            v.f[0].process(v.brown.next(rng.white()) * 0.6f + v.pink.next(rng.white()) * 0.4f);
            v.f[1].process(v.pink2.next(rng.white()));
            s = (v.f[0].low * (0.35f + wave) + v.f[1].low * 0.25f * wave * wave) * loud * 1.5f;
            break;
        }
        case recipe::SEA: {
            // The open sea: each swell's wash rising and falling, each its own length and strength,
            // low and soft. Rough seas add a dark hiss of breaking crests, and a storm a deep roll
            // under it.
            float loud = clampf(p[0], 0, 1.5f), rough = clampf(p[1], 0, 1), storm = clampf(p[2], 0, 1);
            if (v.state[1] <= 0) { v.state[1] = 5.0f + 4.0f * rng.uniform(); v.state[2] = 0.5f + 0.5f * rng.uniform(); }
            v.state[0] += dt / v.state[1];
            if (v.state[0] >= 1.0f) {
                v.state[0] -= 1.0f;
                v.state[1] = (5.0f - 1.5f * storm) + 4.0f * rng.uniform();
                v.state[2] = 0.5f + 0.5f * rng.uniform();
            }
            float swell = 0.5f - 0.5f * std::cos(kTwoPi * v.state[0]);
            float wash = swell * swell * v.state[2];
            if (control) {
                v.f[0].set(160.0f + 360.0f * wash + 180.0f * rough, 0.6f, sr);
                v.f[1].set(450.0f + 450.0f * rough, 0.7f, sr);
                v.f[2].set(85.0f, 0.7f, sr);
            }
            v.f[0].process(v.brown.next(rng.white()) * 0.7f + v.pink.next(rng.white()) * 0.3f);
            v.f[1].process(v.pink2.next(rng.white()));
            v.f[2].process(v.f[0].low);
            s = (v.f[0].low * (0.35f + 0.8f * wash) + v.f[1].low * 0.3f * rough * wash + v.f[2].low * 1.4f * storm) * loud;
            break;
        }
        case recipe::COMPLEX: {
            // The launch complex standing by: a low electrical hum from the tower and lamps (more
            // at night) that wanders in strength. Now and then the propellant farm vents, a soft
            // breath that swells and dies over a few seconds, each one different, never on a beat.
            float loud = clampf(p[0], 0, 1.5f), lamps = clampf(p[1], 0, 1);
            v.state[1] += dt / 11.0f;
            v.state[1] -= std::floor(v.state[1]);
            float wander = 0.75f + 0.25f * std::sin(kTwoPi * v.state[1]) * std::sin(kTwoPi * v.state[1] * 2.3f + 1.1f);
            v.state[0] -= dt;
            if (v.state[0] <= 0) {
                v.state[0] = 9.0f + 16.0f * v.rng.uniform();
                v.state[2] = 0.75f + 0.5f * v.rng.uniform();
                v.env[0].trigger(0.6f + 0.9f * v.rng.uniform(), 1.2f + 1.8f * v.rng.uniform(), sr, 0.5f + 0.5f * v.rng.uniform());
            }
            if (control) {
                v.f[0].set(260.0f, 0.8f, sr);
                v.f[1].set(620.0f * v.state[2], 0.9f, sr);
                v.f[2].set(1100.0f, 0.6f, sr);
            }
            // Hum: a rounded buzz, with harmonics in the band a phone can play.
            v.f[0].process(v.osc[0].saw(100.0f, sr) * 0.6f + v.osc[1].sine(200.0f, sr) * 0.25f);
            float hum = (v.f[0].low + v.f[0].band * 0.5f) * (0.35f + 0.65f * lamps) * wander;
            // The vent: pink breath through a soft band, rounded off above.
            v.f[1].process(v.pink.next(rng.white()));
            v.f[2].process(v.f[1].band * v.env[0].next());
            s = (hum * 0.25f + v.f[2].low * 1.6f) * loud;
            break;
        }
        case recipe::PORT: {
            // A harbour at rest: soft overlapping laps at the piles, each its own length and
            // strength, nothing low enough to thud. Now and then a halyard knocks on a mast, a dull
            // clink, sometimes two. Nothing on a beat.
            float loud = clampf(p[0], 0, 1.5f);
            v.state[0] -= dt;
            if (v.state[0] <= 0) {
                v.state[0] = 0.3f + 1.1f * v.rng.uniform();
                v.state[3] = 0.8f + 0.5f * v.rng.uniform();
                // Two laps take turns, so one is still dying as the next comes in.
                v.state[4] = v.state[4] > 0.5f ? 0.0f : 1.0f;
                Decay& lap = v.env[v.state[4] > 0.5f ? 2 : 0];
                lap.trigger(0.12f + 0.2f * v.rng.uniform(), 0.25f + 0.35f * v.rng.uniform(), sr, 0.3f + 0.7f * v.rng.uniform());
                v.f[0].set(480.0f * v.state[3], 0.7f, sr);
            }
            v.state[1] -= dt;
            if (v.state[1] <= 0 || (v.state[2] > 0 && (v.state[2] -= dt) <= 0)) {
                bool second = v.state[1] > 0;
                if (!second) {
                    v.state[1] = 3.0f + 9.0f * v.rng.uniform();
                    // Sometimes it swings back and knocks again.
                    v.state[2] = v.rng.uniform() < 0.35f ? 0.14f + 0.2f * v.rng.uniform() : 0.0f;
                } else {
                    v.state[2] = 0.0f;
                }
                float f0 = (640.0f + 380.0f * v.rng.uniform());
                const float freqs[3] = {f0, f0 * 1.52f, f0 * 2.37f};
                const float qs[3] = {18.0f, 14.0f, 10.0f};
                const float gains[3] = {1.0f, 0.35f, 0.12f};
                v.res.set(3, freqs, qs, gains, sr);
                v.env[1].trigger(0.001f, 0.012f, sr, (second ? 0.45f : 0.8f) * (0.5f + 0.5f * v.rng.uniform()));
            }
            if (control) { v.f[1].set(200.0f, 0.6f, sr); v.f[3].set(1300.0f, 0.6f, sr); }
            // The water with the thud taken out below, always moving a little.
            v.f[1].process(v.pink.next(rng.white()));
            float lap = 0.12f + v.env[0].next() + v.env[2].next();
            v.f[0].process(v.f[1].high * lap);
            v.f[3].process(v.res.process(rng.white() * v.env[1].next()) * 0.6f);
            s = (v.f[0].low * kPortWater + v.f[3].low) * loud;
            break;
        }
        case recipe::IMPACT: {
            int m = static_cast<int>(p[1] + 0.5f);
            float ex = rng.white() * v.env[0].next();
            float ring = v.res.process(ex) * 3.0f;
            v.state[0] = 60.0f + (v.state[0] - 60.0f) * 0.9996f;
            float thump = v.osc[0].sine(v.state[0], sr) * v.env[1].next() * (m == material::METAL ? 0.5f : 1.0f);
            // The body of the blow: a short burst of the ground itself.
            v.f[0].process(rng.white() * v.env[1].value);
            float body = v.f[0].low * (m == material::METAL ? 0.3f : 5.0f);
            s = (ring + thump + body) * v.state[1] * materialGain(m);
            break;
        }
        case recipe::CRUNCH: {
            v.state[0] *= 0.99998f;  // the metal buckles, and its note sinks
            if (control) setMetal(v.res, 0.8f * v.state[0], sr);
            float ex = (rng.white() * 0.5f + v.crackle.next(rng, 300.0f, sr)) * v.env[0].next();
            v.f[0].process(ex);
            s = (v.res.process(ex) * 2.5f + v.f[0].low * 0.8f) * v.state[1];
            break;
        }
        case recipe::EXPLOSION: {
            float w = rng.white();
            v.f[0].process(w);
            float crack = v.f[0].high * v.env[0].next() * 1.5f;
            v.f[1].process(v.brown.next(rng.white()));
            float boom = v.f[1].low * v.env[1].next() * 2.5f;
            float sub = v.osc[0].sine(45.0f, sr) * v.env[1].value * 0.6f;
            // The blast itself, mid-range where it carries.
            v.f[3].process(v.pink.next(rng.white()));
            float blast = v.f[3].band * v.env[1].value * v.env[1].value * 14.0f;
            v.f[2].process(v.crackle.next(rng, 90.0f, sr));
            float tail = v.f[2].band * v.env[2].next() * 1.0f;
            s = (crack + boom + sub + blast + tail) * v.state[1];
            break;
        }
        case recipe::SLAP: {
            v.f[0].process(v.pink.next(rng.white()) * v.env[0].next());
            float slap = v.f[0].low * 2.0f;
            float thump = v.osc[0].sine(v.state[0], sr) * v.env[1].next() * 0.8f;
            s = (slap + thump) * v.state[1];
            break;
        }
        case recipe::SPLASH: {
            v.f[0].process(rng.white() * v.env[0].next());
            float burst = v.f[0].band * 1.5f;
            // Bubbles: short rising notes, thinning out.
            if (control) v.bubbling = std::exp(-v.age * 2.0f);
            if (rng.uniform() < 40.0f * p[0] * v.bubbling * dt) {
                v.state[0] = 400.0f + 1100.0f * rng.uniform();
                v.env[1].trigger(0.001f, 0.03f, sr);
            }
            v.state[0] *= 1.00005f;
            float bubble = v.osc[0].sine(v.state[0], sr) * v.env[1].next() * 0.5f;
            s = (burst + bubble) * v.state[1];
            break;
        }
        case recipe::THUNDER: {
            v.f[0].process(rng.white());
            float crack = v.f[0].high * v.env[0].next() * 2.0f;
            v.state[2] -= dt;
            if (v.state[2] <= 0) { v.state[1] = 0.4f + 0.6f * rng.uniform(); v.state[2] = 0.15f + 0.4f * rng.uniform(); }
            v.state[0] += (v.state[1] - v.state[0]) * (dt / 0.15f);
            v.f[1].process(v.brown.next(rng.white()));
            v.f[2].process(v.pink.next(rng.white()));
            float e = v.env[1].next() * v.state[0];
            s = crack + v.f[1].low * e * 2.0f + v.f[2].band * e * 7.0f;
            break;
        }
        case recipe::CLICK: {
            float f = p[0] > 0.5f ? 900.0f : 1300.0f;
            float e = v.env[0].next();
            s = (v.osc[0].sine(f, sr) * 0.35f + rng.white() * 0.05f) * e;
            break;
        }
        case recipe::CAUTION: {
            float a = v.age;
            float f = a < 0.14f ? 880.0f : 660.0f;
            float on = (a < 0.12f || (a > 0.16f && a < 0.28f)) ? 1.0f : 0.0f;
            v.state[0] += (on - v.state[0]) * (dt / 0.004f);
            s = v.osc[0].sine(f, sr) * v.state[0] * 0.25f;
            break;
        }
        case recipe::CLUNK: {
            float thud = v.osc[0].sine(120.0f, sr) * v.env[1].next();
            s = v.res.process(rng.white() * v.env[0].next()) * 2.5f + thud * 0.6f;
            s *= 0.5f + 0.5f * clampf(p[0], 0, 1);
            break;
        }
        default:
            break;
    }

    // Distance and air: whatever's far away loses its top.
    if (control) v.distance.set(p[P_LOWPASS] > 0 ? p[P_LOWPASS] : 20000.0f, sr);
    s = v.dc.next(v.distance.next(s));

    bool isContinuous = continuous(v.recipe);
    float fade = v.fade.next(!isContinuous || v.held ? 1.0f : 0.0f);
    s *= fade * trim(v.recipe) * (p[P_GAIN] > 0 ? p[P_GAIN] : 1.0f);
    // Pan at control rate, since per-sample trig was most of the synth's maths. The pan is
    // smoothed, so the steps can't be heard.
    if (control) panGains(p[P_PAN], v.panLeft, v.panRight);
    left = s * v.panLeft;
    right = s * v.panRight;
    v.loudness = v.loudness * 0.999f + std::fabs(s) * 0.001f;
    v.age += dt;

    if (isContinuous ? (!v.held && v.fade.value < 1e-4f) : v.age > lifetime(v.recipe, v.target)) v.active = false;
}

float Synth::reverb(float in, int c) {
    float out = 0;
    for (int i = 0; i < 4; ++i) {
        std::vector<float>& buf = comb_[c][i];
        int& idx = combIndex_[c][i];
        float y = buf[idx];
        combFilter_[c][i] = flush(y * 0.8f + combFilter_[c][i] * 0.2f);  // damping
        buf[idx] = flush(in + combFilter_[c][i] * 0.78f);                // room size
        idx = (idx + 1) % static_cast<int>(buf.size());
        out += y;
    }
    for (int i = 0; i < 2; ++i) {
        std::vector<float>& buf = allpass_[c][i];
        int& idx = allpassIndex_[c][i];
        float b = buf[idx];
        buf[idx] = flush(out + b * 0.5f);
        out = b - out;
        idx = (idx + 1) % static_cast<int>(buf.size());
    }
    return out * 0.25f;
}

void Synth::render(float* out, int frames) {
    takeScene();
    takeEvents();
    float busTargets[bus::COUNT];
    for (int b = 0; b < bus::COUNT; ++b) busTargets[b] = busTarget_[b].load(std::memory_order_relaxed);
    const float roomTarget = roomTarget_.load(std::memory_order_relaxed);

    for (int n = 0; n < frames; ++n) {
        float busL[bus::COUNT] = {}, busR[bus::COUNT] = {};
        for (int i = 0; i < voiceBudget_; ++i) {
            Voice& v = voices_[i];
            if (!v.active) continue;
            float l, r;
            renderVoice(v, l, r);
            int b = busOf(v.recipe);
            busL[b] += l;
            busR[b] += r;
        }
        float l = 0, r = 0;
        for (int b = 0; b < bus::COUNT; ++b) {
            float g = bus_[b].next(busTargets[b]);
            l += busL[b] * g;
            r += busR[b] * g;
        }
        float room = room_.next(roomTarget);
        float mono = (l + r) * 0.5f;
        l += reverb(mono, 0) * room;
        r += reverb(mono, 1) * room;

        // Limiter: instant down and slow back up, so a blast never clips.
        if (!limiting) { out[2 * n] = l; out[2 * n + 1] = r; continue; }
        float peak = std::max(std::fabs(l), std::fabs(r));
        if (peak * limiterGain_ > 0.9f) limiterGain_ = 0.9f / peak;
        else limiterGain_ += (1.0f - limiterGain_) * 0.0002f;
        out[2 * n] = softClip(l * limiterGain_);
        out[2 * n + 1] = softClip(r * limiterGain_);
    }
}

}  // namespace infill
