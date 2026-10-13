# Club games

Android app for the Chessnut Air smart board. Play over the board with clocks, voice and board lights, train openings, play bots and Lichess opponents on the physical board, then review every game with Stockfish 18. Club games can go to your Lichess account automatically.

## Features

### Over the board
- **Live play** with countdown clocks (presets such as 5+5), draw and resign for each side, and checkmate ending the game. Games are saved when they end.
- **Import stored games** automatically: games played on the board while the app was away are copied in each time it connects, then cleared from the board.
- **Voice and sound**: moves are spoken, with beeps for check and illegal moves and a double beep for mate. Phone and board sounds can each be turned off.
- **Board lights**: moves to play are lit on the board, and the winner's half flashes at checkmate.
- **Board checks**: if the pieces don't match the game, the app names the wrong squares and lights them.
- **From here**: set up any position on the board, say whose move it is, then analyse it with Stockfish or play the bot from it. Also in the home menu, and "Play the bot from here" in any game review.
- A short settle delay (350 ms), so sliding a piece across squares counts as one move. The board's battery level is shown, and the screen stays on during games.

### Bot
- Stockfish bots by Elo bracket, which don't always play the top line. Same clocks, draw and resign, and voice as over-the-board play. The bot's move is spoken, shown and lit for you to play on the board.
- A move history you can step back through during the game.

### Opening trainer
- **Medals**: each opening earns bronze for a learned line, silver for half its lines and a Survival run of 2, gold for every line and a run of 5, per side. The list shows them with icons for lines learned, lines due, Survival best and Lichess win rate.
- **Grouped variations**: an opening's listed variations, such as the Scotch's Göring Gambit and Scotch Gambit, are part of its page, its lines and its Survival games instead of separate entries. Search still finds them on their own.
- **Opening pages**: tap any opening for its own page: lines learned, lines due, medal, Survival best and Lichess win rate, your chosen moves, every line with its review date and how often your colour wins from it on Lichess, the lines club players reach most listed first, and its history, with Train at the bottom.
- Everyday openings, a gambits section and a meme openings section (Bongcloud, the Cow, Kilkenny Gambit, Jerome, Alien and more). Filters for gambits, White's, Black's, memes and openings not yet played. The chosen opening shows its start position, moves and progress.
- **Learn every line**: each opening is split into lines the way a coach writes a repertoire: one move for your side, the opponent's most common replies. A game follows one line. With Explain off it is graded from memory with spaced repetition, and the list shows how many lines you know. Reviews that come due appear at the top. See all lines by variation name, and tap one to drill it.
- **Survival**: from an opening's page, play one of your learned lines picked at random against a varied opponent, then keep going with book moves or the engine's top two for as long as you can. Reviews aren't affected. Each line keeps its own best run, shown on the opening page. When a run ends, it says where your move ranked among the engine's top five and how many pawns it gave away (or that it missed or allowed a mate), then the engine's top two.
- **Punish mode**: a Punish button on an opening's page sets up one of its traps or common mistakes just after the opponent has made it, and you find the refutation. Your move must be the refutation's, or one the engine rates nearly as good; a wrong move is turned back with the answer lit. Mistakes you haven't punished come first, most common first, then reviews on the same 1, 3, 7, 14, 30 day schedule. The eval bar stays hidden until you answer, and Explain it plays the trap out with notes. It's played on the screen even with the board connected, since it starts mid-game. The opening's page has a Punish card, in orange: how many of its common mistakes you've punished without help, such as 3/12.
- **Branch points**: in Train and Survival, where your lines split on your move, a gold arrow shows the move of the line you're on and a note names the other lines' moves, so you never have to guess which line you're in.
- **Ratings online**: in a Lichess game both players' ratings show beside their names ("?" when provisional), and a rated game shows what's at stake for you, such as "Rated rapid · Win +6 · Draw −1 · Loss −8", worked out with the Glicko formula from both ratings and their deviations.
- **Low time**: a clock turns red in the last fifteenth of the game (the last 20 seconds of a 5 minute game, 40 of a 10 minute one), in bot, online and over-the-board games. Over the board can also be played with No clock.
- **Online extras**: the opening being played shows above the board, and after a game Rematch challenges the same opponent with colours swapped (it needs the challenge permission, so log out of Lichess and back in once).
- **Mistakes with a follow-up**: practising a mistake takes two moves. Find a better move, the opponent answers with the engine's best, then find the follow-up: any of the engine's top three that comes within 5% winning chances of its best, so where only one move is good, only that counts. Both without help and the position is done for good; a hint or a wrong try brings it back tomorrow.
- **Chess.com games by opening**: on an opening's page, Import my Chess.com games brings in your games of the last 30 days in that opening, from its side (your Scotch games as White, your Pirc games as Black). They go to a Chess.com folder, are analysed one at a time, and their mistakes (yours only) join your practice. The folder keeps the last 30 days; older games are let go, keeping their mistakes and opening score, so it doesn't grow without end. Chess.com's public data needs no login; live play stays on Lichess, since Chess.com offers no API for it.
- **Beyond the opening**: each opening's page has Middlegame plans (twelve for the Scotch, two of them built from master games; ten for the Nimzo-Indian, thirteen for the Slav, fourteen for the Pirc, sixteen for the Italian, eighteen for the French, twenty for the King's Indian, twenty-two for the Sicilian and twenty-eight each for the Ruy Lopez and the English, one after each of their lines; three each for the Vienna and the London, one after each of theirs; four for the Bishop's Opening and the Caro-Kann: a model continuation from the end of one of your lines, a note per move, every move of yours Stockfish-checked, stepped through on the board and then tried against the bot) and Middlegame tactics, real puzzles from Lichess games that began in your lines (7 for the English, 52 for the Bishop's Opening, 45 to 60 for the others; the Scotch's are rated 1500 and up, each a chain of at least three of your moves ending in mate or a clear advantage). Home has Endgames: eight Essentials (the ladder, queen and rook mates, the square of the pawn, taking and holding the opposition, Lucena, Philidor) played against perfect defence from the Lichess tablebase, where a move that lets the result slip is turned back with the move that kept it; and 79 puzzles from real endings in six types, rating 1800 and up, with the long races left out (11 pawn, 14 rook, 15 knight, 14 bishop, 11 queen, 14 queen and rook). Both use one puzzle trainer: your opponent's move plays out, you find each answer, a hint lights the piece then the move, and a clean solve comes back once, after 3 days, then it's done. A miss comes back tomorrow. Opening lines still use the 1, 3, 7, 14, 30 day steps. A mastered essential, and a puzzle section with nothing left due, stays hidden until you ask to see it.
- **Master games**: each Scotch line has a classical master game that reached its end, such as Carlsen–Aronian 2011 or Staunton–Popert 1840. Guess the master's next six moves: the opponent's moves play out with a note on why, and a red arrow when they threaten something. A move about as good as the master's counts too. Then play the game out to its end. Plans show how often masters played each of their moves. Games come from the Lichess masters database and PGN Mentor.
- **No blunders taught**: every move of yours in the main openings was checked with Stockfish. Book lines named after traps your side falls into (Noah's Ark in the Ruy Lopez) and moves far below the engine's best are left out of your lines, and the engine's move takes their place.
- **Common traps**: in Train's Explain mode, when your line reaches a position where the opponent's natural-looking move loses, a Show common trap button plays it out on the board with notes, then Back to the line returns you. Traps and common mistakes cover the main openings, including the Bishop's Opening (the queen on g5, the early ...f5, and ...d5) and classic named traps such as the Siberian trap and the London's poisoned b2 pawn, picked from moves Lichess players rated 1400–2000 actually play and from popular Lichess puzzles (CC0) whose games followed your lines, all checked with Stockfish. Each shows how often Lichess players rated 1400–2000 actually fall for it (Common, Occasional or Rare).
- **Your own moves**: play a different book move than the line's on your turn, and the trainer offers to make it your move. The opening's lines are rebuilt around your choices.
- **Sorted by win rate**: how often the opening's side wins on Lichess, using club-level rapid and classical games. Fetched with your Lichess login and cached for a month. Your best accuracy in each opening shows alongside.
- **Explain**: arrows for the top three book moves, with a key from main line to third. In Train the green arrow is always the move of the line you're on, for either side, so following it on the board (where you play both sides) keeps you in the line. With the board connected, your move is lit on the board too, so you can follow the line without looking at the phone. Out of book, the arrows switch to the engine's top three. With Explain off, moves are scored instead.
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
- Move ratings from brilliant to blunder, a move table that colours only the turning points (brilliant moves, mistakes and blunders) and marks inaccuracies with ?!, material balance, the opening name, and toggleable arrows for the best move, better moves and threats.
- **Learn from mistakes**: practise every position where a mistake or blunder was played. Find a better move on the phone, get hints, and see positions come back on a schedule.
- **Auto-upload**: club games from the board go to your Lichess account once saved, with an offline queue. Older games can be uploaded from the analysis menu. Turn it off in Settings.
- **Share** any game as a Lichess link from the game list or the analysis screen. It is uploaded first if needed, without needing a login; the PGN is shared instead when Lichess can't be reached.
- **A short list**: only the newest five games outside folders are kept. Move a game into a folder to keep it. Before an older game is deleted, its mistakes to practise and its opening score are saved.
- Paste or share a PGN into the app. Games can be sorted into folders.

Where an opening's book line stops on the opponent's move, the trainer has no answer for you, so `openings-custom.tsv` carries engine answers for those spots. To refresh them with a local Stockfish: `BOOK_FILL_OUT=/tmp/fill.tsv STOCKFISH=$(which stockfish) ./gradlew :core:test --tests '*BookFillTest*' --rerun-tasks`, review the output, and append it.

The board protocol lives in `:core` (`app.chessnut.otb.protocol`) and is tested against a fake board. The command that deletes a stored game is only sent during the import on connect, and only after that game is saved on the phone.

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
