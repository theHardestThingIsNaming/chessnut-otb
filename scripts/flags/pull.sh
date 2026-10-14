#!/bin/bash
# Pulls the flagged moves off the phone (the app's files/flags.tsv) into data-cache/flags/<timestamp>.tsv and prints its path.
# Needs the phone connected with adb; the debug build allows run-as.
set -e
ADB=${ADB:-~/Library/Android/sdk/platform-tools/adb}
SERIAL=${SERIAL:-5B100DLCH001K6}
OUT="$(cd "$(dirname "$0")/../.." && pwd)/data-cache/flags"
mkdir -p "$OUT"
F="$OUT/$(date +%Y%m%d-%H%M%S).tsv"
$ADB -s "$SERIAL" shell run-as app.chessnut.otb cat files/flags.tsv > "$F" 2>/dev/null || true
echo "$F ($(grep -c . "$F") flagged)"
