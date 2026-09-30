# Infill

Infill is a city builder for Android 8.1 and up, and it also runs in a web browser.

You start with a small town in 1900 and a few simple tools, and as the years go by the city and the systems that run it get deeper. New technology arrives in eras, and parts of your city get redone as it does: wells give way to water mains, streetcars to cars and back to transit, low rise blocks to something denser.

It's very early. Right now there's a generated map with rivers, lakes and woods that you can move around, through the seasons and the time of day, and not much else.

## Building

You need the Android SDK.

```sh
./gradlew :app:assembleDebug                   # debug APK
./gradlew :sim:jvmTest                         # the simulation's tests
./gradlew :shared:wasmJsBrowserDistribution    # the browser version
tools/serve_web.sh                             # serve it on port 8790
uv run --with pillow tools/gen_tiles.py        # draw the tiles again
```

| | |
|---|---|
| Minimum Android | 8.1 (API 27) |
| Built against | API 37 |
| UI | Kotlin, Compose Multiplatform |

## Licence

Infill is free software under the GNU General Public License, version 3 or later. See [LICENSE](LICENSE).

Copyright © 2026 Dan Hunke.
