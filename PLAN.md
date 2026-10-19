# Club games: plan and leftovers

One list for the next session. The older "common mistakes and Punish" notes are folded in here.
What is already on the phone is under Done. What is worth doing next is under Next.

The app teaches an opening, then three things past move 15: middlegame plans, middlegame tactics,
and endgames. Built the same way for the main openings, including the Bishop's Opening.

Last shipped: the sixteen King's Indian plans, on `main`, and installed on the phone. Every King's Indian line now has a plan. The emulator was left alone this round; another agent was using it.

## Done

- [x] Puzzle pipeline and puzzle trainer. A clean solve comes back once, after 3 days, then the puzzle is done. A miss comes back tomorrow. Opening lines still use 1, 3, 7, 14, 30 days.
- [x] Middlegame tactics on every main opening's page, including the Bishop's Opening. Lichess puzzles whose games started in your lines: Scotch 60, Italian 60, Nimzo-Indian 60, Pirc 58, King's Indian 57, Caro-Kann 57, Ruy Lopez 55, Slav 55, French 53, Vienna 52, Bishop's Opening 52, Sicilian 49, London 45, English 7.
- [x] Endgames on the home screen: 79 puzzles in six types, rating 1800 and up, long races left out. Pawn 11, rook 14, knight 15, bishop 14, queen 11, queen and rook 14.
- [x] Eight Essential endgames against the Lichess tablebase (the ladder, queen and rook mates, the square of the pawn, taking and holding the opposition, Lucena, Philidor). A move that lets the result slip is turned back. A mastered essential is hidden on the Endgames page until you tap "Show mastered".
- [x] A puzzle section with nothing left due (an endgame type, or an opening's middlegame tactics) is hidden the same way, with "Show solved". It comes back on its own when a puzzle is due again.
- [x] Middlegame plans for all 14 openings. Four each, except the English (3), the Vienna (2) and the London (2), which have fewer distinct structures in the lines you drill. The Bishop's Opening has four, one per line. Each plan starts from a line you drill, has a note per move, and no move of the planning side gives away more than about 60 centipawns at Stockfish depth 18.
- [x] A Punish card on each opening's page, in orange under the Survival card: how many of its common mistakes you have punished without help (for example 3/12).
- [x] An opening's lines are ordered by how many club games (Lichess, rapid and classical, 1600–2200) reach them, most first, from the explorer counts already cached; the order is fixed when the lines are built, so it never jumps while you look. Without a Lichess login, or before the counts arrive, the book order stays.
- [x] Less repeated text across the app: Middlegame tactics is one line on the opening's page; lines show Survival as the red bolt, "Plan" on their button, and "Due now"; "as White" and "· N due today" (beside Review N) are gone; the Openings legend is an ⓘ with icons and medals explained; puzzles drop the move number and endgame type the prompt and title already give; essentials and the Endgames blurbs say the tablebase once; the Lichess login names Lichess once.
- [x] The "Middlegame plans" row is gone from Beyond the opening: every plan starts from one of your lines, so its line's button is the way in. Beyond the opening now holds the tactics.
- [x] Harder Scotch tactics: rated 1500 to 2300, at least three moves of yours (EXPORT_MIN_RATING=1500, EXPORT_MIN_PLIES=6), each ending in mate (8), a crushing (22) or a clear advantage (30). The other openings still have the 900–2300 sets; rerun them the same way if these suit.
- [x] Every Scotch line now leads into a middlegame plan: six new ones (pressure on c7 in the Millennium, winning the piece back in the Sarratt, the b-file in the Cochrane, the e5–f4 pawn pair in the Scotch Gambit, the pawn on g7 in the Göring, broken pawns against ...d5), played on by Stockfish and checked (worst move of yours 24cp). A test keeps every Scotch line covered. The other openings could get the same.
- [x] Every Vienna line now leads into a middlegame plan: one new one, the fork on c7 after the queen trade. You gave a knight for a pawn; trade queens, step the bishop back to e2, and Nb5. When Black's knight checks on f2 and the bishop takes the rook, Nxc7+ forks the king and the other rook. Played on by Stockfish and checked (worst move of yours 7cp). The game stays level. The same test now covers the Vienna with the Scotch.
- [x] Every London line now leads into a middlegame plan: one new one, the e4 break against ...Qb6. The queen leaves b2, White takes on c5, plays h3 and Bd3, castles, and breaks with e4. Both bishops end in the centre. Played on by Stockfish and checked (worst move of yours 8cp). White keeps a small edge. The covered-openings test now includes the London.
- [x] Every Nimzo-Indian line now leads into a middlegame plan: six new ones (the knight to e4, the doubled c-pawns, the bishop on a6, queens off on d1, the queen taking the rook on a1, the open f-file). Played on by Stockfish and checked (worst move of yours 13cp). Where White keeps the bishop pair, or a small edge, or the game stays level after the queen grabs a rook, the summary says so. The covered-openings test now includes the Nimzo-Indian.
- [x] Every Slav line now leads into a middlegame plan: nine new ones (the extra pawn on c4, the pawn on b3, the king on d2, queens off on b3, the knight on b4, the pawn on c2, doubled b-pawns, the half-open c-file, meeting the e4 break). Played on by Stockfish and checked (worst move of yours 12cp). The pawn on b3 keeps about half a pawn; the pawn on c2 is two extra pawns and still about level, because the knight attacks the pawn on c2 and the king has not castled. The covered-openings test now includes the Slav.
- [x] Every Pirc line now leads into a middlegame plan: ten new ones (the doubled h-pawns, the bishop on a6, queens off on d1, the knight on f5, the queen on b6, blocking with ...h5, the pawn on g6, the pin on f3, the rook on the e-file, a pawn to the good). Played on by Stockfish and checked (worst move of yours 18cp). After ...Nxe4 you keep about three quarters of a pawn; the pin on f3 and the rook on the e-file stay level; elsewhere White keeps a small edge or about half a pawn. The covered-openings test now includes the Pirc.
- [x] Every Italian line now leads into a middlegame plan: twelve new ones (the bishop takes on d3, the bishop on f8, the queen on c4, the king stays in the centre, the pawn on f6, pawns on d4 e4 and f4, both rooks go, the queen on d4, the king on e6, check on b5, the king on e7, the open king). Played on by Stockfish and checked (worst move of yours 43cp). Where White is a bishop down and still winning, or the king stays in the centre and White is already winning, or a rook for a knight comes back to level, the summary says so. The covered-openings test now includes the Italian.
- [x] Every French line now leads into a middlegame plan: fourteen new ones (queens off on h8, the knight on a5, the knight on e4, the f5 break, queens off on d2, doubled e-pawns, knights off on d4, the bishop on b7, the queen takes on a2, the bishop on f7, the f6 break, the open centre, the bishop takes on b4, the open e-file). Four follow the masters, where the engine's first try was a move they rarely play: the knight on a5 (Nbc6 in 1,685 of 1,994 games), knights off on d4 (...a6 in 2,358 of 3,516), the bishop on b7 (...Bb4+ in 127 of 286), and the bishop takes on b4 (Nc6 in 333 of 660). The older "queen on a5" now breaks with ...f6 (130 of 242 games); its old ...Nf5 was played in none of them. Played on by Stockfish and checked (worst move of yours 58cp). The bishop on f7 is a loss, about a pawn; the queen that takes on a2 and the open e-file leave White a small edge; the knight on e4 keeps a small edge for Black. The bishop on b7 leaves an isolated pawn on d4 and the game level. The bishop takes on b4 leaves you a pawn up, with White's centre still a small edge. The covered-openings test now includes the French.
- [x] Every King's Indian line now leads into a middlegame plan: sixteen new ones (the knight on c5, the pawn on g4, the queen on h4, the knight on c4, the rook on the e-file, the knight on e5, the knight on f5, queens off on d2, the pawn on a4, the queen on f6, the pawn on e4, the knight on e4, the pawn on b4, the pin on f3, the half-open b-file, the knight on d4). Three follow the masters: the knight on c4 (Nxg3 in 98 of 120 games), the rook on the e-file (Re8 in 93 of 290) and the half-open b-file (Bd7 in 179 of 180). Played on by Stockfish and checked (worst move of yours 44cp). The knight on c4 leaves White about three quarters of a pawn after the queen comes off; the pawn on a4 leaves White about a pawn; the queen on h4 leaves you a pawn up with the game level; the pin on f3 and the knight on f5 keep a small edge for Black. Left as is: "Expand with ...b5" plays ...Nbd7 before ...a6, and masters play ...a6 first (106 of 189), then the same knight move. The covered-openings test now includes the King's Indian.
- [x] Slimmer opening page: the stats are one row of icons (learned, due, medal, Lichess wins, Survival best, punished) with an ⓘ legend; each line has a Middlegame plan button; Explain, Ideas and History are left to the Train screen.
- [x] Plans are linked to their lines: each line on the opening's page names the plan that starts where it ends (tap to open it), and finishing that line in Train offers "Continue with the plan". Back from a plan opened this way returns to where you were.
- [x] The opening's page has a clearer order: one stats card (lines learned, due, medal, Lichess wins; then Survival best, last run and punished), Beyond the opening, then the lines, with the Chess.com import at the end. Train's options (Explain, Ideas, History) are pinned just above the Train, Punish and Survival buttons.
- [x] A variation's page (the Vienna's Mieses, say) has its own lines, so it showed no plans, tactics or Punish. It now says they are on the main opening's page, with a link there.
- [x] The analysis move table colours only brilliant moves, mistakes and blunders; good and excellent moves are plain, and inaccuracies keep just their ?!.
- [x] Common-mistake scan of the other openings: 310 positions from the Lichess explorer (players rated 1400–2000), scored by Stockfish at depth 20. 67 moves lose 150cp or more; twelve of the most played are now Punish mistakes, each checked: the Fried Liver's ...Ke8, ...Qf6 and ...Kg8, ...Nxe4 against Ng5, the pinned c6 knight in the Ruy Lopez, ...Bxf2+ in the Bishop's Opening, three Vienna slips against f4, the English's ...Bc5, Nxf7 against the Caro-Kann, and Nxe5 into ...Qa5+ in the Sicilian. The full list is in `data-cache/scan/mistakes.tsv` for more.
- [x] Explain lights your move on the board: on your turn in Train, the green arrow's move (the line's, or the book's favourite past its end) is lit, so the board guides you as the screen does. Switching Explain off clears it.
- [x] Review of the day's work: every plan and trap rechecked with Stockfish (`PLAN_IN=all`, `TRAP_IN=all`). The Vienna's dark-squares plan now castles before Be3 (Be3 at once lost 65cp to ...Bg4). A missed puzzle no longer counts as solved.
- [x] The branch note in Train keeps each move together ("4. Nxd4" no longer splits across lines).
- [x] Loosened puzzle match rerun for the Scotch and the Pirc (187 matches, up from 19). Five new Punish mistakes, Stockfish-checked: the loose c5 bishop (...h6? Nxc6), the e-file pin (...Nxe5? and Re1), a smothered mate on d6, ...Qxf2# against an early dxc6, and the knight raid on e3. Candidates and games are in `data-cache/punish/`.
- [x] Common mistakes and Punish mode, traps for the main openings, and the book audit that keeps your own blunders out of the lines.
- [x] Bishop's Opening for White. The four replies players rated 1400–2000 actually play (Nf6, Nc6, Bc5, d6), and in each the white move that keeps the advantage. Punishes after 3.Nf3 against ...Bc5: ...Qg5 loses the queen, ...f5 loses e5 and opens the king, ...d5 loses a pawn. Four middlegame plans, one per line (knight to g5, knight to g3, bishop to b5 then d4, queen in the centre). 52 middlegame tactics. A written note on every move of the four lines.

## Next

- [ ] Optional. Two more Essential endgames if they earn a place: the Vancura (rook against a rook's pawn) and queen against a pawn. The eight already cover the usual club endings.
- [x] Middlegame tactics for every line, not just the opening (2026-10-13). Up to five per line, matched by position, so a club game that reaches your line by another move order counts; every tactic already shipped kept, with your progress. 1,444 in all (Ruy Lopez 145, King's Indian 138, Caro-Kann 134, Sicilian 134, French 128, English 111 up from 7, Pirc 110, Italian 100, Nimzo-Indian 98, Slav 98, Scotch 87, Bishop's Opening 57, Vienna 56, London 48). 189 of 211 lines have tactics; the 22 without are ones club games almost never reach. Each tactic carries its line, and a session starts with the most played line's, in the order the opening's lines are listed, easiest first within each.
- [x] A middlegame plan after every line of every opening (2026-10-13 finished the Sicilian 22, Ruy Lopez 28, English 28 and Caro-Kann 21). Where master games are dense the plans follow them (opponent replies the most common master move, your moves where most masters agree); elsewhere Stockfish at depth 20, every move of yours within 60 centipawns. `MiddlegamePlansTest.everyLineOfEveryOpeningLeadsIntoAPlan` keeps it so. The Caro-Kann has no master counts (no PGN Mentor file). Tools: `LineDumpTest` lists lines and their plans; `PlanGenTest` plays Stockfish on; `MasterStatsTest.consensus` with `STATS_LINES_IN` shows master practice from any line; `PlanCheckTest` checks the result.

### UX review, 2026-10-12

Walked through on the emulator: Home, Openings, an opening's page, Train, Punish, Mistakes, Endgames, a puzzle, plans. Not yet looked at: bot play, online, analysis, settings.

Fix first:
- [x] Back (the gesture and the ← arrow) from Train, Punish or Survival goes straight to Home, skipping the opening's page and the list. It should return to the opening's page you started from.
- [x] The Explain caption on the opening's page is out of date ("arrows show the book moves"). Say the green arrow is this line's move, and that the board lights it.
- [x] Endgames don't use their blue: the Home tile has the bot's pawn icon in green, and a puzzle's Hint button is green with a gold progress bar. The Home subtitle ("Puzzles from real endings, by type") leaves out the Essentials.
- [x] Punish's prompt card has a green border; Punish is orange.

Consistency:
- [x] Hints differ: Punish lights the whole move at once, puzzles light the piece then the move. Pick one (the two steps).
- [x] Puzzles don't say what the opponent just played; Punish and Mistakes do ("Your opponent played 7… Qxg2").
- [x] In Train and Punish the opening's name is in the top bar and again as the green heading under it, and that heading is also where status text ("Hint lit") appears. Use the heading for status only.
- [x] On the opening's page the plan count is gold and the tactics count green. Use one colour.
- [x] The Essentials and puzzle sections on Endgames use different card styles. Essentials show no state (not tried, slipped, mastered).

Smaller:
- [x] The Openings header reads "3 lines learned · In 5 openings": it counts openings started, which reads as if three lines were spread over five. Say "Started 5 openings" or count only openings with a learned line.
- [x] An empty puzzle progress bar shows a stray dot at its right end (Material's stop indicator).
- [x] Mistakes opens on a bare spinner for several seconds. Show what it's doing ("Loading 31 positions…").
- [x] Punish, Mistakes and puzzles leave half the screen empty under the board. Room for the game's moves, or the idea and the eval after you answer. Left as it is: a mistake keeps only its position (its game may be gone from the list), so there are no moves to show for every one.
- [x] Home's Openings and Mistakes tiles change their subtitles once the counts load, which shifts the text. Reserve the line. Checked: the line is one line high either way, so the text changes in place and nothing moves.
- [x] An unanalysed game shows "Accuracy – · –". Say "Not analysed yet".
- [x] "Lichess wins –" with no login gives no hint that logging in fills it.
- [x] The plans list doesn't show which plans you've stepped through or tried against the bot.

## Left as it is

Bringing a solved puzzle or essential back early because a new mistake in a game "should have been" caught by it. Matching the exact position is possible and almost never happens: a club game does not reach the Lichess puzzle you solved. Matching the idea (a fork, a pin, the Lucena shape) would put the wrong puzzles back into To do. The day steps above are the schedule.

## Checking a plan

Plans live in `MiddlegamePlans.kt`. `before` is the end of a line from `OpeningTree.lines` (it must be a prefix of one). `moves` is both sides, starting with the opponent. One note per move.

Write candidates as `label|before|moves` and run:

```
PLAN_IN=/tmp/plan-in.txt PLAN_OUT=/tmp/plan-out.txt STOCKFISH=/opt/homebrew/bin/stockfish \
  ./gradlew :core:test --tests '*PlanCheckTest*' --rerun-tasks
```

`PLAN_IN=all` checks every shipped plan; `TRAP_IN=all` does the same for every trap with `TrapCheckTest`, adding what the punishing move gives back. The number after each move is the centipawns it gives away. Keep the planning side's under about 60. The first move of `moves` is the opponent's; then they alternate. `ILLEGAL` means the move does not parse. Gradle caches tests, so a changed input needs `--rerun-tasks`.

`MiddlegamePlansTest` checks every plan is legal and starts from a drilled line.

After writing an opening's plans, refresh their master counts (classical master games from PGN Mentor, in `data-cache/pgnmentor/<opening>/`, git-ignored; folders: scotch, italian, ruy, vienna, bishop, english, london, french, pirc, slav, nimzo, kid, sicilian; there's none for the Caro-Kann):

```
STATS_PGN_DIR=$PWD/data-cache/pgnmentor/italian STATS_OPENING="Italian Game|w" STATS_OUT=/tmp/stats.txt \
  STATS_TSV=/tmp/italian.tsv ./gradlew :core:test --tests '*MasterStatsTest.stats*' --rerun-tasks
```

Then replace that opening's rows in `core/src/main/resources/plan-masters.tsv` with the new file's. `/tmp/stats.txt` shows each plan move as `move(played/reached)`: where many master games reach a position (say 100+) and few play your side's move (under a third), masters prefer something else. `STATS_CONSENSUS=1 STATS_PLAN="<name>"` with `MasterStatsTest.consensus` prints what they play instead; following it, Stockfish-checked, made the better plans so far.

## Adding tactics for an opening

Needs `data-cache/puzzles.csv` (decompress `puzzles.csv.zst`, or download `https://database.lichess.org/lichess_db_puzzle.csv.zst`). Games are cached in `data-cache/games.ndjson`, so a re-run resumes.

```
EXPORT_OPENINGS="Scotch Game|w,Pirc Defense|b" ./gradlew :core:test --tests '*PuzzleExportTest*' --rerun-tasks
```

To top up lines instead (what the shipped set now uses), add `EXPORT_PER_LINE=5`: it keeps every shipped tactic, adds new ones to lines with fewer than five, writes each tactic's line in a ninth column, and leaves other openings alone. The shipped run used `EXPORT_CAP=20000 EXPORT_MIN_PLAYS=50 EXPORT_ENDGAMES=skip`, the Scotch with `EXPORT_MIN_RATING=1500 EXPORT_MIN_PLIES=6`, the Bishop's Opening with `EXPORT_LINE_SLACK=10` (its lines run deep). Twelve openings at once took 50 minutes, mostly fetching games.

Side is `w` or `b`. Without `EXPORT_PER_LINE` the export replaces `puzzles-middlegame.tsv` with only the openings you named, and it also rewrites `puzzles-endgame.tsv`. Copy both files aside first. `EXPORT_ENDGAMES=skip` leaves the endgame file alone. `EXPORT_CAP` (default 1500) is how many of the most-played puzzles get their games checked.

The Bishop's Opening is special in that test. Puzzle games do not follow the model lines past move 3, so `PuzzleExportTest` counts a game when its first three moves of each side match a drilled line (`lines.map { it.take(6) }`). That is how the 52 were found: 2663 tagged puzzles, 95 began in the lines, 52 kept. Checking the full 15-ply lines keeps zero. Leave that special case in place unless you have a replacement set.

Do not run the export against the live files. It replaces `puzzles-middlegame.tsv` with only the openings named in `EXPORT_OPENINGS`. Without `EXPORT_ENDGAMES=skip` it also rewrites `puzzles-endgame.tsv` from the whole CSV, and that will not match the shipped 79. Copy both files aside first, and merge new rows back by hand.

## How to work

Build with `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`. adb is `~/Library/Android/sdk/platform-tools/adb`. Commit and push straight to `main`. After each round, install on the phone:

```
~/Library/Android/sdk/platform-tools/adb -s 5B100DLCH001K6 install -r app/build/outputs/apk/debug/*.apk
```

Use the emulator (`pixel_api36`, `./scripts/launch-emulator.sh`) when the phone may be in use. Check the result there, then install on the phone at the end. Plain wording in the UI. Colours stay: green openings, orange Punish, red Survival, gold highlights, blue endgames.

## The physical board

Every screen that plays on the board reads it the same way (`BoardRead`, core, tested in `BoardReadTest`): a settled reading is the same position, a legal move, a piece in the hand (pieces lifted, nothing new put down: a move under way, never a mistake), or unexpected (castling half done, a piece on the wrong square). An unexpected board must stay that way for two seconds (`BoardRead.MISMATCH_MS`) before its squares are named and lit, at most eight (`MAX_LIT`). Lights go through `BoardLights`: the newest request wins and a stalled radio can't hold things up. Used by Train/Survival/Punish (`PlayViewModel`), online play (`OnlineViewModel`) and master games. The over-the-board recorder (`LiveGameTracker`) already had the same piece-in-the-hand rule and keeps its own lights. Its illegal-board beep now waits the same two seconds (`BoardRead.MISMATCH_MS`), cancelled by the next reading: a capture where the taken piece is nudged aside as the capturing piece lands (13:15, 2026-10-13, Qxd4 with the knight pushed to d5) beeped as illegal for the 0.7 s it lasted. Train now also names a mismatched board, as online play did; it used to stay silent.

Online play checks the board when a game starts or resumes, and names a wrong board while an opponent's move is lit (comparing against the position before or after that move, whichever is closer). Before, a setup mistake (a rated game, 2026-10-13: Black's king and queen swapped) left the lit move pending forever with no message, and your own move was then set aside. Train, Survival and Punish already wait for a correct setup before the first move.

## Move marks

Fixed 2026-10-13, from a game where 17...gxf5 (dropping the h5 knight) was marked brilliant and the rest of the table was grey:
- Inaccuracy / mistake / blunder now use Lichess's steps, 5 / 10 / 15 points of win percentage (Lichess's 0.1 / 0.2 / 0.3 are in winning chances, -1 to 1). The app had used 10 / 20 / 30, twice as forgiving, so real blunders showed as "?!". Train keeps the forgiving steps for passing a move (`AccuracyCalculator.TRAINING`). The mistakes deck takes mistakes and blunders from games, so it now finds more.
- Brilliant needs a sacrifice that is the engine's move or within 2 of it, leaving the mover at 30% or better, from a position not already above 90%. Win chances flatten out in lost positions, so the old "drop under 10" let a piece thrown away there count.
- The move table colours the engine's top moves green as well as brilliant moves, mistakes and blunders.

An illegal move on the board sounds at once (phone and board beeper, per the sound settings) in online play and Train/Survival/Punish: `BoardRead.illegalMove`, which leaves out halves of legal moves (castling king first, en passant, a pawn on the last rank before promotion).

## Mistakes practice

Capped 2026-10-13, when 73 were due (partly from the stricter Lichess steps): `MistakeQueue` picks at most 15 at a time. Mistakes already under way come first, on their review dates; new ones fill the rest, blunders first, then by how much the move gave away, then newest game, at most 3 new ones per game. Mistakes made from an already lost position (under 15% to win) are left out. The rest wait their turn; "Practise all" still has everything, and a game opened on its own shows all its mistakes. Home's count uses the same rule.

## Speed

The openings list took 10–15 seconds after a cold start on the phone (2026-10-13). Fixed: the opening book is indexed (replies by position, lines by name) and read as written instead of replayed (`OpeningBookTest` checks every line against the replay); lines are ranked by popularity with each line replayed once, not once per comparison; each game's accuracies are kept in `accuracy.tsv` until its analysis changes (`AccuracyCache`); a refresh that a newer one replaced stops early. Now about 5 seconds from a cold start of the debug build, starting at launch. A release build would be faster still (no debug JIT), but it's signed differently, so switching means reinstalling.

## Play the line for me

- [x] Train has a "Play line" chip beside Explain, Ideas and History whenever the opening's line you are drilling can continue from the board (phone play, not Survival or Punish). It plays the rest of the line, both sides, one move at a time. Ideas on, with sound: each move is said and its note spoken, and the next move waits for the voice. Ideas on, no sound: the note stays up for a time proportional to its length. Ideas off: half a second between moves. The chip becomes "Stop". Not graded: the line isn't marked as learned. All openings' lines use it. Not yet checked on a device (the emulator would not run the app on 2026-10-15).

## Flagged moves

- [x] A quiet hollow bookmark on the master game screen, in Train (beside the status line) and on the analysis screen, next to the navigation keys. Tap to flag the move just played, with an optional question; filled means flagged, tap again to edit the question or remove it. Flags are kept in `files/flags.tsv` on the phone (`MoveFlag`, `FlagStore`, core); the key is the position before the move plus the move (`FlagKey`), so a move reached by another order is the same flag.
- [x] Answering them: plug the phone in and use the `explain-flagged-moves` skill. `scripts/flags/pull.sh` pulls the file, `scripts/flags/facts.py` prints the engine facts for each pending flag, and the explanations are appended to `core/src/main/resources/move-insights.tsv` as `key<TAB>text`. A move with an explanation shows an "i" beside the bookmark on any screen where that position and move come up.
- [ ] Not yet flaggable: plan stepper, puzzles, Punish and Survival, online games.

## Master games

Built 2026-10-12, Scotch only, for review before spreading to other openings.

- [x] Master game mode: each Scotch line has a classical master game that reached its end (Carlsen–Aronian, Nakamura–Onischuk, Staunton–Popert and others). From the opening's page (a gold "Master game" button on the line) or after finishing the line in Train, you guess the master's next six moves. The opponent's moves play out with a note, and a red arrow when they make a real threat (a capture or check worth a pawn or more). A move within 0.3 of a pawn of Stockfish's best counts as good too. After your guesses, "Play out the game" shows the rest. Data: `core/src/main/resources/master-games.tsv`, checked by `MasterGamesTest`.
- [x] After a master game, Analyse saves it to your games (a "Master games" folder, so it isn't trimmed with the recent five) and opens it in the analysis screen; Back returns to the master game. It adds nothing to your mistakes.
- [x] Plans show how often masters played each move ("Masters: 4,994 of 6,274 games"), so book moves stand apart from the engine's ideas. Data: `plan-masters.tsv`, from `MasterStatsTest`.
- [x] Two Scotch plans built from master practice: "f4 and Ba3 against ...g6" (Mieses) and "Castle long against ...d6" (3...Nxd4). White scores 64–78% in master games there; the old plans followed Stockfish, which masters mostly don't play at those points.

Findings, master games as a source for middlegame plans:
- Where master data is dense (thousands of games: Mieses, Scotch Gambit with ...Nf6, the Sarratt), masters confirm the Stockfish plans move for move until about move 10, then often choose differently. Those later moves are better taken from master practice.
- Where it's thin (under ~30 games: lines 1, 2, 3, 6, 8, 9), the engine plans stay; there's no human consensus to follow.
- 3...Nxd4 (line 10): masters mostly play 5...d6, not the plan's 5...Qf6. The new "Castle long" plan covers the common reply.

Master counts now cover every opening's plans (PGN Mentor classical games: 17k Vienna to 666k Sicilian; none for the Caro-Kann, PGN Mentor has no file for it). Of 82 plans:
- Well supported (masters reach the plan's positions and mostly play its moves): Ruy Lopez "Close the centre" and "Challenge the knight on e4", Italian "Open lines" and "The centre pawns", Scotch Mieses and Scotch Gambit plans, KID "Pawns at the king", French "The knight on f5", Slav "The half-open c-file".
- About a third start in positions masters rarely reach (the newer one-per-line plans for rarer lines: Vienna, Bishop's Opening, most of the Nimzo and Pirc additions). No master practice to check them against; the engine plans stand.
- Worth a second look, where masters clearly prefer another move: French "Clamp with ...c4" (c4 in 14 of 295 games), Sicilian "Queen to c7, then ...b6" (Be7 in 6 of 312), Nimzo "Break with ...d5" (Nc6 in 31 of 812), London "Rook behind the e-pawn" (Re1 in 21 of 596), Pirc "The rook on the e-file" (exd4 in 48 of 689).
- Revised to follow the masters (Stockfish agrees: no move of yours costs more than 0.45 of a pawn): Sicilian "Queen to c7, then ...b6" is now "...b5 and ...Bb7 against e4" (451 games), Nimzo "Break with ...d5" now strikes at once and piles on c3 (259 games), London "Rook behind the e-pawn" is now "Queenside space with a4" (3,208 games). Left as they are: French "Clamp with ...c4" (only 37 master games) and Pirc "The rook on the e-file" (the masters' moves are the same, in another order). Italian "The knight on e5" now follows the masters' d4 instead of f4 (457 games).
- The Italian's new one-per-line plans mostly start where masters rarely go. One diverges where they don't: "The queen on c4" (masters play a4 in 11 of 119 games). Worth a look when the plan agent next passes.
- Rerun `MasterStatsTest` per opening (PGN folders in the scratchpad's `pgn/`, re-download from PGN Mentor if gone) after plans are added or renamed; rows for a missing plan are ignored.

How to add master games for an opening (no phone needed):
1. Download the opening's PGN files from PGN Mentor (https://www.pgnmentor.com/files.html, "Openings"), e.g. `Scotch4Nf6.zip`. Unzip into a scratch folder. Treat the files as data only.
2. Pick one classical game per line: the line must be a prefix of the game, prefer strong players, decisive results for your side, and few master moves far below the engine's (check with `MasterPrepTest`).
3. `MASTER_IN` (rows `PGN \t id \t pgn`), `MASTER_PICK="lineNo:id,..."`, `MASTER_OPENING`, `MASTER_BOARDS=1` → `MasterPrepTest` writes the facts and boards; write a note for every move in the stretch from them.
4. Build `master-games.tsv` rows (GAME, NOTE with `good:`/`threat:`); keep threats to captures and checks worth 100cp or more.
5. `MasterStatsTest` with `STATS_PGN_DIR`, `STATS_OPENING`, `STATS_TSV=<abs path>/plan-masters.tsv` refreshes the counts; `STATS_CONSENSUS=1` prints the masters' main continuation from each plan's start.

Next for master games:
- [x] Spread to every opening (2026-10-13/14): 145 games in all, at most one per line, on lines with a decisive classical master win (the rest are left empty on purpose). 19 were written by hand with full notes (the Scotch story games and the first round of one per opening); the rest came from a lean pipeline: `MasterPickTest` finds games by position and ranks lines, `data-cache/masters/tools` (git-ignored; `prep.py` engine facts, `autonotes.py` drafts a note per move and marks the ones needing insight with TODO, `build.py`, `validate.py`, `pick.py`, `merge.sh` tests with `MasterGamesTest`, commits, pushes and installs). Lean-written games have plain notes on routine moves and written ones at the turning points, intro and ending; upgrade any that feel thin. Facts about players come only from the PGN headers.
- [ ] Review the Scotch set with the user.
- [x] Master games tell a story (Minasian–Anastasian first, as the model): the scene at the time (who the players were, what was at stake), a green arrow for your line's moves, a note on every move to the end of the game, key moments where the game pauses with arrows and a Continue (REVIEW rows), and a closing note on how it ended and what became of the players (OUTRO row). The guessing runs to the decisive move (14.f4) rather than a fixed six moves. `MasterEvalTest` walks a game with Stockfish to find its turning points. The other nine Scotch games still have the plain version; give them the same treatment after review.
- [x] Master games start from move 1: you play your line first (exact moves, with the opening notes), then guess the master's moves once the screen says the line has ended. Your line's moves aren't scored or marked (the arrow shows them); only the master's moves are. A spoken "Your move." prompts each guess, and the line's end is announced. The players' names and ratings show above and below the board (none for the 1840 game).
- [x] Board play: with the board connected, set up the starting position (squares still wrong are lit, up to 8), then the opponent's moves are lit for you to play, your moves are read from the pieces, and a different move lights the squares to put right. Hints light the piece, then the move. Play leaves the board alone meanwhile (`OtbApp.boardBorrowed`). Check it with the real board.
- [ ] Re-derive later plan moves from master practice where data is dense (from about move 10 of each plan).



## White repertoire against 1.e4 (2026-10-19)

- [x] 21 chapters, 178 lines, bundled in `core/src/main/resources/white-repertoire.tsv` (chapter, w, line; most played first) and loaded by `RepertoireStore` as built-in chapters named "Repertoire: <chapter>" (Caro-Kann Advance/Tal, Short, Fantasy, Panov; French Advance, Tarrasch, Exchange, Horwitz; Sicilian Alapin, Grand Prix, Closed, Smith-Morra, Bb5; Scandinavian x2, Pirc Austrian, Modern, Alekhine x2, Philidor, Nimzowitsch/Owen). Lines were Stockfish-checked and trimmed (`data-cache/white/check.py`, `trim.py`; White moves losing 0.9 or more cut, the Tal and Smith-Morra chapters looser).
- [x] 130 plans in `white-plans.tsv` (chapter, name, before, moves, summary, notes joined by " ¦ "), each from the lines' shared moves; `WhiteRepertoireTest` checks every line is covered. Only the moves were engine-checked (`plancheck.py`); the note wording was not.
- [x] 18 master games (the French, Sicilian, Pirc and Philidor chapters, plus one Caro-Kann Panov) added to `master-games.tsv`, found by position in the PGN Mentor files (`data-cache/white/masters/scan.py`, `mkwork.py`). No master data exists for the Caro-Kann, Scandinavian, Alekhine, Modern or Nimzowitsch lines, so those have none.
- [ ] Not done: tactics for these lines; an engine pass over the master-game and plan note wording; the newer Alekhine/Philidor/Nimzowitsch lines the agent rewrote after the build (`data-cache/white/other2.tsv`, reviewed 14:56 or later).

- Caro-Kann Advance (Short) and Fantasy extended with 6 more lines and plans (Black replies ...Qb6, ...h5, ...Nd7 to 4.Nf3; Fantasy ...e6 with ...dxe4, and ...Nf6). Still thin: French Horwitz, Nimzowitsch/Owen.
