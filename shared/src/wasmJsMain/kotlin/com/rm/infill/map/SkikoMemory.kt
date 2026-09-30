@file:JsModule("./skiko.mjs")

package com.rm.infill.map

/** Skia's WebAssembly module, once it has loaded. Its `_` holds the module's exports, memory included. */
internal external val loadedWasm: JsAny
