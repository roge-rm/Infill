#!/bin/bash
# Builds the synth as WebAssembly for the page: $1/synth.wasm, with synth-worklet.js beside it.
# It's one module with no threads and no JavaScript of its own, loaded by the worklet.
set -e
cd "$(dirname "$0")"
OUT="${1:-out}"
. "${EMSDK:-$HOME/.local/share/emsdk}/emsdk_env.sh" >/dev/null 2>&1
CPP=../../app/src/main/cpp
mkdir -p "$OUT"
em++ -O3 -std=c++17 -fno-exceptions -fno-rtti \
    -I"$CPP" synth_web.cpp "$CPP/synth/synth.cpp" \
    -o "$OUT/synth.wasm" \
    --no-entry -sSTANDALONE_WASM=1 -sINITIAL_MEMORY=4MB -sALLOW_MEMORY_GROWTH=0 \
    -sSTACK_SIZE=128KB -sFILESYSTEM=0 -sMALLOC=emmalloc
cp synth-worklet.js "$OUT/"
ls -la "$OUT"
