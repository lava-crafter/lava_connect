#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
cargo build --locked -p lava-connect-android-jni
base="${TMPDIR:-/tmp}"
if [[ -d /tmp/opencode && -w /tmp/opencode ]]; then base="${TMPDIR:-/tmp/opencode}"; fi
temporary="$(mktemp -d "$base/lava-jni.XXXXXX")"
trap 'rm -rf "$temporary"' EXIT
javac -d "$temporary/classes" bindings/android-jni/tests/java/NativeCore.java
java -cp "$temporary/classes" com.lavacrafter.connect.nativecore.NativeCore \
    "$(pwd)/target/debug/liblava_connect.so" "$temporary/identity"
