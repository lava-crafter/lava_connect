#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$SDK/ndk/30.0.16248370}"
cd "$ROOT"
command -v cargo >/dev/null || { echo 'cargo is required; install Rust and source "$HOME/.cargo/env"' >&2; exit 1; }
command -v cargo-ndk >/dev/null || { echo 'cargo-ndk is required (cargo install cargo-ndk)' >&2; exit 1; }
command -v jar >/dev/null || { echo 'JDK 17 is required (jar not found)' >&2; exit 1; }
cargo ndk --platform 26 -t arm64-v8a -o "$ROOT/android/app/src/main/jniLibs" build --locked --release -p lava-connect-android-jni
test -s "$ROOT/android/app/src/main/jniLibs/arm64-v8a/liblava_connect.so" || { echo 'Rust native library was not generated' >&2; exit 1; }
cd "$ROOT/android"
./gradlew --no-daemon assembleDebug
APK="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
CONTENTS="$(jar tf "$APK")"
grep -Fxq 'lib/arm64-v8a/liblava_connect.so' <<< "$CONTENTS" || { echo 'APK is missing liblava_connect.so' >&2; exit 1; }
printf '\nAPK (ARM64, includes Rust core): %s\n' "$APK"
