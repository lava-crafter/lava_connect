#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"
cd "$ROOT"
command -v cargo-ndk >/dev/null || { echo 'cargo-ndk is required (cargo install cargo-ndk)' >&2; exit 1; }
cargo ndk --platform 26 -t arm64-v8a -o "$ROOT/android/app/src/main/jniLibs" build --locked --release -p lava-connect-android-jni
cd "$ROOT/android"
./gradlew --no-daemon assembleDebug
