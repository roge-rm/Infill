#!/bin/bash
# Builds every file of an Infill release from the committed tree, into ~/Projects/releases/infill-VERSION.
# Usage: tools/build-release.sh 0.15.0
set -euo pipefail
V=${1:?usage: tools/build-release.sh VERSION}
REPO=$(cd "$(dirname "$0")/.." && pwd)
OUT=$HOME/Projects/releases/infill-$V
cd "$REPO"
[ -z "$(git status --porcelain)" ] || { echo "tree not clean"; exit 1; }
grep -q "versionName = \"$V\"" app/build.gradle.kts || { echo "version is not $V"; exit 1; }
[ -f "$REPO/../Keys/infill-keystore.properties" ] || { echo "no release key settings in ../Keys"; exit 1; }

# The music is not in git. The APK, the browser version and the F-Droid publisher all need it.
MUSIC=shared/src/commonMain/composeResources/files/music
ls "$MUSIC"/* >/dev/null 2>&1 || { echo "no music files in $MUSIC"; exit 1; }

# Where Gradle puts each module's build folder, as in tools/serve_web.sh.
BUILD_ROOT=$(sed -n 's/^infill\.buildRoot=//p' "$HOME/.gradle/gradle.properties" 2>/dev/null || true)
if [ -n "$BUILD_ROOT" ]; then
    BASE=$BUILD_ROOT/$(basename "$REPO")
    APP=$BASE/app
    DESKTOP=$BASE/desktop
else
    APP=$REPO/app/build
    DESKTOP=$REPO/desktop/build
fi

mkdir -p "$OUT"

# The APK (arm64, armeabi-v7a and x86_64), from clean, signed with the release key.
heavy build ./gradlew clean :app:assembleRelease
# The APK's version code and key, against what the release should be.
TOOLS=$(ls -d "$HOME"/Android/Sdk/build-tools/*/ | sort -V | tail -1)
CERT=94a967c788b7f85c658af1bb2bfc087bb169ac020598396a271fd595397ac1e5
CODE=$(sed -n 's/^ *versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts)
f=$APP/outputs/apk/release/app-release.apk
"${TOOLS}aapt2" dump badging "$f" | grep -E "^package|native-code"
"${TOOLS}aapt2" dump badging "$f" | grep -q "versionCode='$CODE' versionName='$V'" || { echo "$f: not version $V ($CODE)"; exit 1; }
"${TOOLS}apksigner" verify --print-certs "$f" | grep -q "SHA-256 digest: $CERT" || { echo "$f: wrong key"; exit 1; }
cp "$f" "$OUT/infill-$V.apk"

# The browser version. The F-Droid publisher builds its own from the tag; this is to see it builds.
heavy build ./gradlew :shared:wasmJsBrowserDistribution

# The desktop builds: Debian packages and AppImages for x86-64 and arm64, and Windows' installer
# and portable zip. Docker does the cross builds.
heavy build ./gradlew :desktop:debAmd64 :desktop:debArm64 :desktop:appImageAmd64 :desktop:appImageArm64 :desktop:windowsX64
cp "$DESKTOP/deb/infill_${V}_amd64.deb" "$DESKTOP/deb/infill_${V}_arm64.deb" "$OUT/"
cp "$DESKTOP/appimage/infill-$V-x86_64.AppImage" "$DESKTOP/appimage/infill-$V-aarch64.AppImage" "$OUT/"
cp "$DESKTOP/windows/infill-$V-setup.exe" "$DESKTOP/windows/infill-$V-windows-x64.zip" "$OUT/"

cd "$OUT"
sha256sum "infill-$V.apk" "infill_${V}_amd64.deb" "infill_${V}_arm64.deb" \
    "infill-$V-x86_64.AppImage" "infill-$V-aarch64.AppImage" "infill-$V-setup.exe" "infill-$V-windows-x64.zip" > SHA256SUMS
cat SHA256SUMS

# Only the newest release folder is kept.
for d in "$HOME"/Projects/releases/infill-*; do
    v=${d##*-}
    [ "$v" != "$V" ] && [ "$(printf '%s\n%s\n' "$v" "$V" | sort -V | head -1)" = "$v" ] && rm -rf "${d:?}"
done
echo "Update ~/.claude/resources.md: the release copy is now infill-$V."
echo "After the GitHub release: copy the two .deb files into ~/Projects/fdroid/inbox/ for the apt repo, and the APK for F-Droid."
echo DONE
