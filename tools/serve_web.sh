#!/bin/sh
# Serves the browser build on this machine's network, for testing before it's
# published. Build it first with ./gradlew :shared:wasmJsBrowserDistribution
#   tools/serve_web.sh [port]
# Nothing is cached, so a reload always gets the latest build.
set -e
port="${1:-8790}"
root="$(cd "$(dirname "$0")/.." && pwd)"
build_root="$(sed -n 's/^infill\.buildRoot=//p' "$HOME/.gradle/gradle.properties" 2>/dev/null)"
if [ -n "$build_root" ]; then
    dist="$build_root/$(basename "$root")/shared/dist/wasmJs/productionExecutable"
else
    dist="$root/shared/build/dist/wasmJs/productionExecutable"
fi
[ -f "$dist/index.html" ] || { echo "No build in $dist" >&2; exit 1; }
cd "$dist"
exec python3 - "$port" <<'PY'
import sys, http.server
class Handler(http.server.SimpleHTTPRequestHandler):
    extensions_map = {**http.server.SimpleHTTPRequestHandler.extensions_map, ".wasm": "application/wasm"}
    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()
port = int(sys.argv[1])
print(f"Serving on port {port}", flush=True)
http.server.ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
PY
