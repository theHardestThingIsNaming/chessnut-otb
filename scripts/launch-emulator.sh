#!/usr/bin/env bash
# Start the Mac BLE bridge (port 7877) and the arm64 Android emulator.
# Usage: ./scripts/launch-emulator.sh [avd-name]
set -euo pipefail

AVD="${1:-pixel_api36}"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
EMULATOR="$SDK/emulator/emulator"
ADB="$SDK/platform-tools/adb"
LOG="${CHESSNUT_EMULATOR_LOG:-$HOME/Library/Logs/chessnut-emulator.log}"

if [[ ! -x "$EMULATOR" || ! -x "$ADB" ]]; then
  echo "Android SDK tools not found under $SDK" >&2
  echo "Set ANDROID_SDK_ROOT if the SDK lives somewhere else." >&2
  exit 1
fi

ble_app() {
  local candidate
  for candidate in \
    "/Applications/BLEForEmulator.app" \
    "$HOME/Applications/BLEForEmulator.app"
  do
    if [[ -x "$candidate/Contents/MacOS/BLEForEmulator" ]]; then
      echo "$candidate"
      return 0
    fi
  done
  return 1
}

bridge_listening() {
  lsof -nP -iTCP:7877 -sTCP:LISTEN >/dev/null 2>&1
}

if bridge_listening; then
  echo "BLE bridge already listening on port 7877"
else
  app="$(ble_app)" || {
    echo "BLEForEmulator.app was not found in /Applications or ~/Applications." >&2
    exit 1
  }
  open "$app"
  ready=0
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    if bridge_listening; then
      ready=1
      break
    fi
    sleep 0.4
  done
  if [[ "$ready" -ne 1 ]]; then
    echo "Opened $app, but nothing is listening on port 7877 yet." >&2
    echo "Click BLE in the menu bar and allow Bluetooth if macOS asks." >&2
    exit 1
  fi
  echo "BLE bridge listening on port 7877 ($app)"
fi

if pgrep -f "qemu-system-.*-avd ${AVD}" >/dev/null 2>&1 \
  || pgrep -f "emulator -avd ${AVD}" >/dev/null 2>&1; then
  echo "Emulator $AVD is already running"
else
  mkdir -p "$(dirname "$LOG")"
  # auto picks a software renderer on this Mac and the window stays grey.
  nohup "$EMULATOR" -avd "$AVD" -gpu host -netdelay none -netspeed full >>"$LOG" 2>&1 &
  echo "Starting emulator $AVD (log: $LOG)"
fi

"$ADB" wait-for-device
booted=0
for _ in $(seq 1 90); do
  boot="$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  if [[ "$boot" == "1" ]]; then
    booted=1
    break
  fi
  sleep 2
done

if [[ "$booted" -ne 1 ]]; then
  echo "Emulator $AVD is up, but Android has not finished booting yet." >&2
  exit 1
fi

echo "Emulator $AVD is ready"
"$ADB" devices -l
