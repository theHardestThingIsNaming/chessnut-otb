# Club games

Android app for the Chessnut Air smart board. Play over the board with clocks, voice and board lights, train openings, play bots and Lichess opponents on the physical board, then review every game with Stockfish 18. Club games can go to your Lichess account automatically.

## Features

### Over the board
- **Live play** with countdown clocks (presets such as 5+5), draw and resign for each side, and checkmate ending the game. Games are saved when they end.
- **Read stored games** from the board's memory, with an optional "Read and clear board" after a confirmation.
- **Voice and sound**: moves are spoken, with beeps for check and illegal moves and a double beep for mate. Phone and board sounds can each be turned off.
- **Board lights**: moves to play are lit on the board, and the winner's half flashes at checkmate.
- **Board checks**: if the pieces don't match the game, the app names the wrong squares and lights them.
- A short settle delay, so sliding a piece across squares counts as one move. The board's battery level is shown, and the screen stays on during games.

### Bot
- Stockfish bots by Elo bracket, which don't always play the top line. Same clocks, draw and resign, and voice as over-the-board play. The bot's move is spoken, shown and lit for you to play on the board.
- A move history you can step back through during the game.

### Opening trainer
- Everyday openings, a gambits section and a meme openings section (Bongcloud, the Cow, Kilkenny Gambit, Jerome, Alien and more). Filters for gambits, White, Black, memes and unrated openings.
- **Sorted by win rate**: how often the opening's side wins on Lichess, using club-level rapid and classical games. Fetched with your Lichess login and cached for a month. Your best accuracy in each opening shows alongside.
- **Explain**: arrows for the top three book moves, with a key from main line to third. Out of book, the arrows switch to the engine's top three. With Explain off, moves are scored instead.
- **Ideas**: speaks the idea behind each move, from about 560 written notes on main lines, meme lines and the engine's strongest branches. Other moves get a rule-based description, which also flags sacrifices.
- **History**: speaks short stories about the opening as its lines are reached.
- Play either side's book move on the board to explore a branch. A spoken cue announces when you leave theory.

### Your repertoire
- **Import a Lichess study** by link. Each chapter becomes a line to drill, from the chapter's side, with variations as branches.
- **Spaced repetition**: the opponent steers into the most overdue line. A clean run pushes it back 1, 3, 7, 14 or 30 days; a miss brings it back tomorrow.

### Lichess online play
- Log in once with Lichess (OAuth, no password stored). Seek rapid or classical games of 10 minutes and longer, then play them on the board with clocks, voice and lights.
- Reconnects by itself if the connection drops or goes quiet, and finds a game whose start was missed. Ongoing games can be resumed from the lobby.
- Abort before both sides have moved, offer, accept or decline draws, and get low-time warnings. "Open on Lichess" after the game.

### Review
- Stockfish 18 analysis with accuracy for both sides (Lichess formulas), an eval bar, the top three lines, and live analysis of any side line you play.
- Move ratings from brilliant to blunder, a colour-coded move table, material balance, the opening name, and toggleable arrows for the best move, better moves and threats.
- **Learn from mistakes**: practise every position where a mistake or blunder was played. Find a better move on the phone, get hints, and see positions come back on a schedule.
- **Auto-upload**: club games from the board go to your Lichess account once saved, with an offline queue. Older games can be uploaded from the analysis menu. Turn it off in Settings.
- Paste or share a PGN into the app. Games can be sorted into folders.

The board protocol lives in `:core` (`app.chessnut.otb.protocol`) and is tested against a fake board. The command that deletes a stored game is only sent from "Read and clear board", after a confirmation and after each game is saved.

## Build

Needs JDK 21 and the Android SDK (platform 37). The first build downloads the official Stockfish Android binary (about 115 MB) and checks its SHA-256; it is packaged as `lib/arm64-v8a/libstockfish.so`, so only arm64 devices and emulators are supported.

```
./gradlew :core:test :app:assembleDebug
```

If Java isn't set up system-wide, point `JAVA_HOME` at a JDK 21 first, for example Homebrew's:

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

## Iterate on the Mac with the emulator

Start the BLE bridge and the `pixel_api36` emulator together:

```
./scripts/launch-emulator.sh
```

The bridge app is `BLEForEmulator` in Applications (the menu bar shows BLE). It listens on port 7877. Pass another AVD name if you are not using `pixel_api36`.

1. Create an arm64 emulator (for example `pixel_api36`, Google Play image) and start it.
2. Install and open [BLEForEmulator](https://github.com/engelon/BLEForEmulator) on the Mac and allow Bluetooth. It bridges the Mac's Bluetooth to the emulator on port 7877.
3. Install the debug APK: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. In the app, tap "Read from board", choose "Mac Bluetooth (BLEForEmulator)", then "Find board". "Simulated board" works with no hardware.

The Board screen shows every byte sent and received; "Share log" exports it. Logs are also kept in the app's `files/logs`.

## Install on the Pixel

With USB debugging on, build the debug APK and install it on the connected phone (the emulator is left alone):

```
./scripts/install-phone.sh
```

That debug build offers "Phone Bluetooth". Pass a device serial if more than one phone is plugged in. Release builds go through Google Play; see `PRIVACY.md` for the policy the listing links to.

Release builds need `otb.sourceUrl` and `otb.privacyUrl` set in `gradle.properties` (the GPL needs the source link in the app) and a `keystore.properties` file:

```
storeFile=/absolute/path/upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Then `./gradlew :app:bundleRelease` writes `app/build/outputs/bundle/release/app-release.aab`.

## Lichess

The app uses the Lichess API with the scopes `board:play` (online games) and `study:read` (private studies for the repertoire). The login token is encrypted on the phone and revoked when you log out. Logins made before `study:read` was added can still read public and unlisted studies; log out and back in to read private ones. The opening explorer needs a login, so win rates appear once you are logged in.

## Licence

GPL-3.0, because the app ships Stockfish. Piece images are the cburnett set from Lichess (GPL-2.0-or-later). The opening book is the Lichess chess-openings dataset (CC0). Chess rules come from chesslib (Apache-2.0). BLEForEmulator (MIT) is only in debug builds.
