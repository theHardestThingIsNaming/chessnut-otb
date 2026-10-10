#!/usr/bin/env bash
# Build the debug APK and install it on a connected phone.
# Usage: ./scripts/install-phone.sh [device-serial]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
ADB="$SDK/platform-tools/adb"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"

if [[ ! -x "$ADB" ]]; then
  echo "adb not found under $SDK" >&2
  echo "Set ANDROID_SDK_ROOT if the SDK lives somewhere else." >&2
  exit 1
fi

if [[ -z "${JAVA_HOME:-}" && -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi

cd "$ROOT"
./gradlew :app:assembleDebug --offline

serial="${1:-}"
if [[ -z "$serial" ]]; then
  serial="$("$ADB" devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ { print $1; exit }')"
fi
if [[ -z "$serial" ]]; then
  echo "No phone is connected. Plug it in and allow USB debugging." >&2
  "$ADB" devices -l >&2
  exit 1
fi

"$ADB" -s "$serial" install -r "$APK"
"$ADB" -s "$serial" shell am start -n app.chessnut.otb/.MainActivity
echo "Installed on $serial"
