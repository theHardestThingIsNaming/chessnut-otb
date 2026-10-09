# Club games

Android app that reads the games stored on a Chessnut Air over Bluetooth and reviews them on the phone with Stockfish 18: accuracy for both sides, an eval bar, the engine's top three lines, and live analysis of any side line you play on the board. A PGN can also be pasted or shared into the app.

The board protocol lives in `:core` (`app.chessnut.otb.protocol`) and is tested against a fake board. The command that deletes a stored game is only sent from "Read and clear board", after a confirmation and after each game is saved.

## Build

Needs JDK 21 and the Android SDK (platform 37). The first build downloads the official Stockfish Android binary (about 115 MB) and checks its SHA-256; it is packaged as `lib/arm64-v8a/libstockfish.so`, so only arm64 devices and emulators are supported.

```
./gradlew :core:test :app:assembleDebug
```

## Iterate on the Mac with the emulator

1. Create an arm64 emulator (for example `pixel_api36`, Google Play image) and start it.
2. Install and open [BLEForEmulator](https://github.com/engelon/BLEForEmulator) on the Mac and allow Bluetooth. It bridges the Mac's Bluetooth to the emulator on port 7877.
3. Install the debug APK: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. In the app, tap "Read from board", choose "Mac Bluetooth (BLEForEmulator)", then "Find board". "Simulated board" works with no hardware.

The Board screen shows every byte sent and received; "Share log" exports it. Logs are also kept in the app's `files/logs`.

## Install on the Pixel

With USB debugging on, `adb install -r app/build/outputs/apk/debug/app-debug.apk` installs the debug build, which offers "Phone Bluetooth". Release builds go through Google Play; see `PRIVACY.md` for the policy the listing links to.

Release builds need `otb.sourceUrl` and `otb.privacyUrl` set in `gradle.properties` (the GPL needs the source link in the app) and a `keystore.properties` file:

```
storeFile=/absolute/path/upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Then `./gradlew :app:bundleRelease` writes `app/build/outputs/bundle/release/app-release.aab`.

## Licence

GPL-3.0, because the app ships Stockfish. Piece images are the cburnett set from Lichess (GPL-2.0-or-later). Chess rules come from chesslib (Apache-2.0). BLEForEmulator (MIT) is only in debug builds.
