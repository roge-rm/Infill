package com.rm.infill.sound

/**
 * The sounds the synth can make, by number. It's the same list as
 * `app/src/main/cpp/synth/recipes.h`, and a test holds the two to each other.
 */
object Recipes {
    // Held, for as long as the scene asks for them.
    const val JET = 2
    const val PROP = 3
    const val WIND = 6
    const val RAIN = 7
    const val FIRE = 10
    const val OUTBOARD = 12
    const val SURF = 13
    const val SEA = 15
    const val COMPLEX = 16
    const val PORT = 17
    const val TRAFFIC = 20
    const val TRAM = 21
    const val TRAIN = 22
    const val BIRDS = 23
    const val CRICKETS = 24
    const val CROWD = 25
    const val SIREN = 26

    // One-shots.
    const val IMPACT = 50
    const val CRUNCH = 51
    const val EXPLOSION = 53
    const val SPLASH = 54
    const val THUNDER = 56
    const val CLICK = 57
    const val CAUTION = 58
    const val CLUNK = 61
    const val SLAP = 62
    const val HORN = 63
    const val BELL = 64
    const val WHISTLE = 65
    const val CHIME = 66
}

/** What an impact is on, for how it rings. `material` in recipes.h. */
object Materials {
    const val METAL = 0
    const val ROCK = 1
    const val EARTH = 2
    const val SAND = 3
    const val SNOW = 4
    const val WOOD = 5
}

/** The mix's buses, each with its own volume. `bus` in recipes.h. */
object Buses {
    const val TOWN = 0
    const val EFFECTS = 1
    const val UI = 2
    const val MUSIC = 3
    const val COUNT = 4
}

/** Parameters every voice has after its recipe's own. `P_*` in synth.h. */
object SharedParams {
    const val COUNT = 9
    const val GAIN = 5
    const val PAN = 6
    const val LOWPASS = 7
    /** The frequency factor for something going by, 0 for none. */
    const val PITCH = 8
}
