// The game's sounds by number. audio/Recipes.kt has the same list and a test checks they agree.
// Brought over from Apogee, without the rockets.
#pragma once

namespace infill {

namespace recipe {
// Continuous: held for as long as the scene asks for them.
constexpr int JET = 2;         // p0 output, p1 spool, p2 airspeed
constexpr int PROP = 3;        // p0 output, p1 blade rate Hz, p2 load
constexpr int WIND = 6;        // p0 loudness, p1 gustiness, p2 brightness
constexpr int RAIN = 7;        // p0 loudness, p1 patter on the hull
constexpr int FIRE = 10;       // p0 loudness
constexpr int OUTBOARD = 12;   // p0 output, p1 revs
constexpr int SURF = 13;       // p0 loudness
constexpr int SEA = 15;        // p0 loudness, p1 roughness, p2 storm
constexpr int COMPLEX = 16;    // p0 loudness, p1 lamps lit (0..1)
constexpr int PORT = 17;       // p0 loudness
// The town's own.
constexpr int TRAFFIC = 20;    // p0 loudness, p1 motors (0 horses and carts .. 1 engines), p2 electric, p3 speed (0 street .. 1 highway)
constexpr int TRAM = 21;       // p0 loudness
constexpr int TRAIN = 22;      // p0 loudness, p1 kind (0 steam, 1 diesel, 2 electric), p2 speed (0..1)
constexpr int BIRDS = 23;      // p0 loudness, p1 chorus (0 a few .. 1 the dawn chorus)
constexpr int CRICKETS = 24;   // p0 loudness
constexpr int CROWD = 25;      // p0 loudness
constexpr int SIREN = 26;      // p0 loudness, p1 kind (0 bell, 1 wailing, 2 electronic)
constexpr int LAST_CONTINUOUS = 49;

// One-shots: fired once, then they ring out.
constexpr int IMPACT = 50;     // p0 energy, p1 material, p2 size
constexpr int CRUNCH = 51;     // p0 energy
constexpr int EXPLOSION = 53;  // p0 size
constexpr int SPLASH = 54;     // p0 energy
constexpr int THUNDER = 56;    // p0 closeness
constexpr int CLICK = 57;      // p0 kind
constexpr int CAUTION = 58;    // p0 kind
constexpr int CLUNK = 61;      // p0 size
constexpr int SLAP = 62;       // p0 energy
constexpr int HORN = 63;       // p0 kind (0 bulb, 1 car, 2 truck), p1 length (0..1)
constexpr int BELL = 64;       // p0 how hard
constexpr int WHISTLE = 65;    // p0 kind (0 steam whistle, 1 diesel horn, 2 ship's horn), p1 length (0..1)
constexpr int CHIME = 66;      // p0 kind (0 a tick, 1 good news, 2 bad news, 3 a new era)
}  // namespace recipe

/** Surfaces an impact can be on, for its character. */
namespace material {
constexpr int METAL = 0;
constexpr int ROCK = 1;
constexpr int EARTH = 2;
constexpr int SAND = 3;
constexpr int SNOW = 4;
constexpr int WOOD = 5;
}  // namespace material

/** Which mix bus a recipe plays through: each has its own volume in Settings. */
namespace bus {
constexpr int TOWN = 0;
constexpr int EFFECTS = 1;
constexpr int UI = 2;
constexpr int MUSIC = 3;
constexpr int COUNT = 4;
}  // namespace bus

inline int busOf(int r) {
    switch (r) {
        case recipe::IMPACT: case recipe::CRUNCH: case recipe::EXPLOSION: case recipe::SPLASH: case recipe::SLAP: case recipe::CLUNK:
            return bus::EFFECTS;
        case recipe::CLICK: case recipe::CAUTION: case recipe::CHIME:
            return bus::UI;
        default:
            return bus::TOWN;
    }
}

}  // namespace infill
