// Renders every sound to WAV off the device, through the game's synth, for tuning.
//
//     ./gradlew :app:soundGallery    ->   app/build/sound-gallery/*.wav
//
// Each file is a held sound swept through its range, or a one-shot at a few strengths. A line per
// file gives peak and RMS.
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <functional>
#include <string>
#include <vector>

#include "../synth/synth.h"

using namespace infill;

namespace {

constexpr float kRate = 48000.0f;
constexpr int kBlock = 480;  // 10 ms between scene updates, like a frame

void writeWav(const std::string& path, const std::vector<float>& stereo) {
    FILE* f = std::fopen(path.c_str(), "wb");
    if (!f) { std::perror(path.c_str()); return; }
    auto u32 = [&](uint32_t v) { std::fwrite(&v, 4, 1, f); };
    auto u16 = [&](uint16_t v) { std::fwrite(&v, 2, 1, f); };
    uint32_t dataBytes = static_cast<uint32_t>(stereo.size() * 2);
    std::fwrite("RIFF", 1, 4, f); u32(36 + dataBytes);
    std::fwrite("WAVEfmt ", 1, 8, f); u32(16); u16(1); u16(2); u32(48000); u32(48000 * 4); u16(4); u16(16);
    std::fwrite("data", 1, 4, f); u32(dataBytes);
    for (float s : stereo) {
        int v = static_cast<int>(std::lround(clampf(s, -1.0f, 1.0f) * 32767.0f));
        u16(static_cast<uint16_t>(static_cast<int16_t>(v)));
    }
    std::fclose(f);
}

/** A held sound over [seconds], with its parameters set each block by [shape](t 0..1, params). */
struct Held {
    int recipe;
    int flags = 0;
    std::function<void(float, float*)> shape;
};

/** A one-shot at [time] seconds. */
struct Shot {
    float time;
    int recipe;
    std::vector<float> p;
    int flags = 0;
};

bool raw = false;

/** A direct-form biquad in double, for measuring. */
struct Biquad {
    double b0, b1, b2, a1, a2, z1 = 0, z2 = 0;
    double run(double x) {
        double y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }
};

/**
 * How loud it sounds rather than its power: ITU-R BS.1770 K-weighting (a lift above about 1.5 kHz,
 * a cut below about 60 Hz) at 48 kHz, in LUFS.
 */
double loudness(const std::vector<float>& stereo, bool phone = false) {
    // A phone speaker gives little below about 350 Hz, so a fourth-order high pass there too.
    auto highPass = [](double freq) {
        double w = 2.0 * M_PI * freq / 48000.0, alpha = std::sin(w) / (2.0 * 0.7071), c = std::cos(w), a0 = 1 + alpha;
        return Biquad{(1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0};
    };
    double sum = 0;
    for (int ch = 0; ch < 2; ++ch) {
        Biquad shelf{1.53512485958697, -2.69169618940638, 1.19839281085285, -1.69065929318241, 0.73248077421585};
        Biquad high{1.0, -2.0, 1.0, -1.99004745483398, 0.99007225036621};
        Biquad speaker1 = highPass(350), speaker2 = highPass(350);
        for (size_t i = ch; i < stereo.size(); i += 2) {
            double x = stereo[i];
            if (phone) x = speaker2.run(speaker1.run(x));
            double y = high.run(shelf.run(x));
            sum += y * y;
        }
    }
    return -0.691 + 10.0 * std::log10(std::max(1e-12, sum / std::max<size_t>(1, stereo.size() / 2)));
}

/** Where the energy sits: dB in each octave from 63 Hz to 8 kHz, relative to the loudest. */
std::string octaves(const std::vector<float>& stereo) {
    const float centres[] = {63, 125, 250, 500, 1000, 2000, 4000, 8000};
    double energy[8] = {};
    for (int b = 0; b < 8; ++b) {
        Svf f;
        f.set(centres[b], 1.41f, kRate);
        for (size_t i = 0; i < stereo.size(); i += 2) { f.process(stereo[i]); energy[b] += f.band * f.band; }
    }
    double most = *std::max_element(energy, energy + 8);
    std::string line;
    char cell[16];
    for (double e : energy) {
        std::snprintf(cell, sizeof cell, "%5.0f", 10.0 * std::log10(std::max(1e-12, e / most)));
        line += cell;
    }
    return line;
}

void render(const std::string& dir, const std::string& name, float seconds, std::vector<Held> held,
            std::vector<Shot> shots = {}, float room = 0.15f) {
    Synth synth(kRate, 48);
    synth.setRoom(room);
    synth.limiting = !raw;
    std::vector<float> out(static_cast<size_t>(seconds * kRate) * 2, 0.0f);
    int frames = static_cast<int>(seconds * kRate);
    size_t nextShot = 0;
    for (int at = 0; at < frames; at += kBlock) {
        float t = static_cast<float>(at) / frames;
        Scene scene;
        scene.count = static_cast<int>(held.size());
        for (size_t i = 0; i < held.size(); ++i) {
            SceneEntry& e = scene.entries[i];
            e.key = static_cast<int>(i + 1);
            e.recipe = held[i].recipe;
            e.flags = held[i].flags;
            std::memset(e.p, 0, sizeof e.p);
            held[i].shape(t, e.p);
        }
        synth.publishScene(scene);
        while (nextShot < shots.size() && shots[nextShot].time * kRate <= at) {
            Event e;
            e.recipe = shots[nextShot].recipe;
            e.flags = shots[nextShot].flags;
            e.seed = static_cast<uint32_t>(nextShot * 7919 + 17);
            for (size_t j = 0; j < shots[nextShot].p.size() && j < kParams; ++j) e.p[j] = shots[nextShot].p[j];
            synth.pushEvent(e);
            ++nextShot;
        }
        int n = std::min(kBlock, frames - at);
        synth.render(out.data() + static_cast<size_t>(at) * 2, n);
    }
    double sum = 0; float peak = 0;
    for (float s : out) { sum += s * s; peak = std::max(peak, std::fabs(s)); }
    double rms = std::sqrt(sum / std::max<size_t>(1, out.size()));
    std::printf("%-22s peak %6.1f  rms %6.1f dBFS  loud %6.1f  phone %6.1f LUFS  octaves 63..8k:%s\n", name.c_str(),
                20.0 * std::log10(std::max(1e-9f, peak)), 20.0 * std::log10(std::max(1e-12, rms)),
                loudness(out), loudness(out, true), octaves(out).c_str());
    writeWav(dir + "/" + name + ".wav", out);
}

float ramp(float t) { return t < 0.5f ? t * 2 : (1 - t) * 2; }

}  // namespace

int main(int argc, char** argv) {
    std::string dir = argc > 1 ? argv[1] : ".";
    raw = argc > 2 && std::string(argv[2]) == "raw";

    // Vehicles.
    render(dir, "jet-spool", 8, {{recipe::JET, 0, [](float t, float* p) { p[0] = 0.4f + 0.6f * ramp(t); p[1] = ramp(t); p[2] = t; }}});
    render(dir, "prop", 6, {{recipe::PROP, 0, [](float t, float* p) { p[0] = 0.8f; p[1] = 40 + 60 * ramp(t); p[2] = ramp(t); }}});
    render(dir, "outboard", 6, {{recipe::OUTBOARD, 0, [](float t, float* p) { p[0] = 0.8f; p[1] = ramp(t); }}});

    // Each vehicle held steady as the game drives it (SoundScene's numbers), to compare by ear.
    render(dir, "level-surf", 16, {{recipe::SURF, 0, [](float, float* p) { p[0] = 1.0f; }}});
    render(dir, "level-sea-calm", 20, {{recipe::SEA, 0, [](float, float* p) { p[0] = 0.4f; p[1] = 0.1f; }}});
    render(dir, "level-sea-rough", 20, {{recipe::SEA, 0, [](float, float* p) { p[0] = 1.0f; p[1] = 0.8f; }}});
    render(dir, "level-sea-storm", 20, {{recipe::SEA, 0, [](float, float* p) { p[0] = 1.0f; p[1] = 1.0f; p[2] = 1.0f; }}});
    render(dir, "level-complex-day", 30, {{recipe::COMPLEX, 0, [](float, float* p) { p[0] = 1.0f; }}});
    render(dir, "level-complex-night", 30, {{recipe::COMPLEX, 0, [](float, float* p) { p[0] = 1.0f; p[1] = 1.0f; }}});
    render(dir, "level-port", 30, {{recipe::PORT, 0, [](float, float* p) { p[0] = 1.0f; }}});
    render(dir, "level-slap", 1, {{recipe::SLAP, 0, [](float, float* p) { p[0] = 1.0f; }}});
    // A plane going by at 150 m/s, with Doppler from high to low as it passes.
    render(dir, "doppler-flyby", 8, {{recipe::JET, 0, [](float t, float* p) {
        float x = (t - 0.5f) * 8.0f * 150.0f, d = std::sqrt(x * x + 60.0f * 60.0f);
        p[0] = 1; p[1] = 0.8f; p[2] = 0.5f;
        p[8] = 343.0f / (343.0f + 150.0f * x / d);
        p[5] = 60.0f / (60.0f + d * 0.5f) * 3.0f;
    }}});
    render(dir, "level-jet-full", 5, {{recipe::JET, 0, [](float, float* p) { p[0] = 1; p[1] = 1; p[2] = 0.5f; }}});
    render(dir, "level-jet-cruise", 5, {{recipe::JET, 0, [](float, float* p) { p[0] = 0.35f + 0.65f * 0.6f; p[1] = 0.6f; p[2] = 0.5f; }}});
    render(dir, "level-jet-idle", 5, {{recipe::JET, 0, [](float, float* p) { p[0] = 0.35f; p[1] = 0; p[2] = 0.5f; }}});
    render(dir, "level-prop-full", 5, {{recipe::PROP, 0, [](float, float* p) { p[0] = 1; p[1] = 100; p[2] = 1; }}});
    render(dir, "level-prop-cruise", 5, {{recipe::PROP, 0, [](float, float* p) { p[0] = 0.3f + 0.7f * 0.6f; p[1] = 30 + 70 * 0.6f; p[2] = 0.6f; }}});
    render(dir, "level-outboard-full", 5, {{recipe::OUTBOARD, 0, [](float, float* p) { p[0] = 1; p[1] = 1; }}});
    render(dir, "level-outboard-cruise", 5, {{recipe::OUTBOARD, 0, [](float, float* p) { p[0] = 0.4f + 0.6f * 0.6f; p[1] = 0.6f; }}});

    // The town.
    render(dir, "traffic-horses", 10, {{recipe::TRAFFIC, 0, [](float, float* p) { p[0] = 0.8f; p[1] = 0; }}});
    render(dir, "traffic-1930", 10, {{recipe::TRAFFIC, 0, [](float, float* p) { p[0] = 0.8f; p[1] = 1; }}});
    render(dir, "traffic-highway", 10, {{recipe::TRAFFIC, 0, [](float, float* p) { p[0] = 1; p[1] = 1; p[3] = 1; }}});
    render(dir, "traffic-electric", 10, {{recipe::TRAFFIC, 0, [](float, float* p) { p[0] = 0.8f; p[1] = 1; p[2] = 1; }}});
    render(dir, "traffic-years", 20, {{recipe::TRAFFIC, 0, [](float t, float* p) { p[0] = 0.8f; p[1] = std::min(1.0f, t * 2); p[2] = std::max(0.0f, t * 2 - 1); }}});
    render(dir, "tram", 20, {{recipe::TRAM, 0, [](float, float* p) { p[0] = 1; }}});
    render(dir, "train-steam", 12, {{recipe::TRAIN, 0, [](float t, float* p) { p[0] = 1; p[1] = 0; p[2] = ramp(t); }}});
    render(dir, "train-diesel", 12, {{recipe::TRAIN, 0, [](float t, float* p) { p[0] = 1; p[1] = 1; p[2] = ramp(t); }}});
    render(dir, "train-electric", 12, {{recipe::TRAIN, 0, [](float t, float* p) { p[0] = 1; p[1] = 2; p[2] = ramp(t); }}});
    render(dir, "birds", 15, {{recipe::BIRDS, 0, [](float, float* p) { p[0] = 1; p[1] = 0.2f; }}});
    render(dir, "birds-dawn", 15, {{recipe::BIRDS, 0, [](float, float* p) { p[0] = 1; p[1] = 1; }}});
    render(dir, "crickets", 10, {{recipe::CRICKETS, 0, [](float, float* p) { p[0] = 1; }}});
    render(dir, "crowd", 10, {{recipe::CROWD, 0, [](float, float* p) { p[0] = 1; }}});
    render(dir, "siren-bell", 8, {{recipe::SIREN, 0, [](float, float* p) { p[0] = 1; p[1] = 0; }}});
    render(dir, "siren-wail", 12, {{recipe::SIREN, 0, [](float, float* p) { p[0] = 1; p[1] = 1; }}});
    render(dir, "siren-electronic", 12, {{recipe::SIREN, 0, [](float, float* p) { p[0] = 1; p[1] = 2; }}});
    render(dir, "horns", 4, {}, {{0.1f, recipe::HORN, {0, 0.3f}}, {1.3f, recipe::HORN, {1, 0.2f}}, {1.8f, recipe::HORN, {1, 0.1f}}, {2.6f, recipe::HORN, {2, 0.6f}}});
    render(dir, "bell", 2, {}, {{0.1f, recipe::BELL, {1.0f}}});
    render(dir, "whistles", 12, {}, {{0.1f, recipe::WHISTLE, {0, 0.5f}}, {3.5f, recipe::WHISTLE, {1, 0.5f}}, {7.0f, recipe::WHISTLE, {2, 0.6f}}});
    render(dir, "hammering", 4, {}, {{0.1f, recipe::IMPACT, {0.5f, material::WOOD, 0.2f}}, {0.5f, recipe::IMPACT, {0.5f, material::WOOD, 0.2f}}, {0.9f, recipe::IMPACT, {0.6f, material::WOOD, 0.2f}}, {2.0f, recipe::IMPACT, {0.4f, material::METAL, 0.1f}}, {2.35f, recipe::IMPACT, {0.4f, material::METAL, 0.1f}}});

    // Places and weather.
    render(dir, "wind", 12, {{recipe::WIND, 0, [](float t, float* p) { p[0] = 0.2f + 0.8f * ramp(t); p[1] = 0.8f; p[2] = ramp(t); }}});
    render(dir, "rain", 6, {{recipe::RAIN, 0, [](float t, float* p) { p[0] = 0.7f; p[1] = t; }}});
    render(dir, "surf", 16, {{recipe::SURF, 0, [](float, float* p) { p[0] = 0.8f; }}});
    render(dir, "fire", 6, {{recipe::FIRE, 0, [](float, float* p) { p[0] = 0.8f; }}});
    render(dir, "thunder", 10, {}, {{0.2f, recipe::THUNDER, {1.0f}}, {4.5f, recipe::THUNDER, {0.2f}}});

    // Crashes.
    render(dir, "impact-metal", 6, {}, {{0.1f, recipe::IMPACT, {0.2f, material::METAL, 0.3f}}, {2.0f, recipe::IMPACT, {1.0f, material::METAL, 0.6f}}, {4.0f, recipe::IMPACT, {1.8f, material::METAL, 0.9f}}});
    render(dir, "impact-rock", 4, {}, {{0.1f, recipe::IMPACT, {0.3f, material::ROCK, 0.5f}}, {1.8f, recipe::IMPACT, {1.5f, material::ROCK, 0.8f}}});
    render(dir, "impact-earth", 4, {}, {{0.1f, recipe::IMPACT, {0.3f, material::EARTH, 0.5f}}, {1.8f, recipe::IMPACT, {1.5f, material::EARTH, 0.8f}}});
    render(dir, "impact-sand-snow", 4, {}, {{0.1f, recipe::IMPACT, {1.0f, material::SAND, 0.6f}}, {1.8f, recipe::IMPACT, {1.0f, material::SNOW, 0.6f}}});
    render(dir, "crunch", 3, {}, {{0.1f, recipe::CRUNCH, {0.5f}}, {1.5f, recipe::CRUNCH, {1.5f}}});
    render(dir, "explosion-near", 8, {}, {{0.1f, recipe::EXPLOSION, {1.0f}}});
    render(dir, "explosion-far", 8, {}, {{0.1f, recipe::EXPLOSION, {0.8f, 0, 0, 0, 0, 0, 0, 400.0f}}});
    render(dir, "splash", 4, {}, {{0.1f, recipe::SPLASH, {0.4f}}, {1.8f, recipe::SPLASH, {1.5f}}});

    // Parts and interface.
    render(dir, "clunk", 2, {}, {{0.1f, recipe::CLUNK, {1.0f}}, {0.9f, recipe::CLUNK, {0.4f}}});
    render(dir, "click", 1, {}, {{0.1f, recipe::CLICK, {0.0f}}, {0.5f, recipe::CLICK, {1.0f}}});
    render(dir, "caution", 1, {}, {{0.1f, recipe::CAUTION, {0.0f}}});
    return 0;
}
