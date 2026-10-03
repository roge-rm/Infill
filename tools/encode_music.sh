#!/bin/sh
# Makes the game's music from the masters in music/: each FLAC, named by era
# and number (township-1.flac), becomes Ogg Vorbis in the shared resources,
# all brought to the same loudness, a little under the town's sound.
#   tools/encode_music.sh
set -e
root="$(cd "$(dirname "$0")/.." && pwd)"
src="$root/music"
out="$root/shared/src/commonMain/composeResources/files/music"
mkdir -p "$out"
for f in "$src"/*.flac; do
    [ -e "$f" ] || { echo "No FLAC files in $src" >&2; exit 1; }
    name="$(basename "$f" .flac)"
    ffmpeg -loglevel error -y -i "$f" -af "loudnorm=I=-20:TP=-1.5:LRA=11" -ar 44100 -ac 2 -c:a libvorbis -q:a 3 "$out/$name.ogg"
    echo "$name.ogg $(du -k "$out/$name.ogg" | cut -f1) KB"
done
