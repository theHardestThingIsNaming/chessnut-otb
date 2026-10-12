# Club games: plan and leftovers

One list for the next session. The older "common mistakes and Punish" notes are folded in here.
What is already on the phone is under Done. What is worth doing next is under Next.

The app teaches an opening, then three things past move 15: middlegame plans, middlegame tactics,
and endgames. Built the same way for the main openings, including the Bishop's Opening.

Last shipped: the twelve Italian plans, on `main`. Every Italian line now has a plan. The phone was away, so this build is on the emulator; install it when the phone is back.

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
- [ ] A middlegame plan after every line, one opening at a time (the Scotch, the Vienna, the London, the Italian, the Nimzo-Indian, the Slav and the Pirc are done). Lines still without a plan: French 14, King's Indian 16, Caro-Kann 17, Sicilian 18, Ruy Lopez 24, English 25. Tools: `LineDumpTest` lists an opening's lines and their plans; `PlanGenTest` plays Stockfish on from each line's end; `PlanCheckTest` checks the result.

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

## Adding tactics for an opening

Needs `data-cache/puzzles.csv` (decompress `puzzles.csv.zst`, or download `https://database.lichess.org/lichess_db_puzzle.csv.zst`). Games are cached in `data-cache/games.ndjson`, so a re-run resumes.

```
EXPORT_OPENINGS="Scotch Game|w,Pirc Defense|b" ./gradlew :core:test --tests '*PuzzleExportTest*' --rerun-tasks
```

Side is `w` or `b`. This replaces `puzzles-middlegame.tsv` with only the openings you named, and it also rewrites `puzzles-endgame.tsv`. Copy both files aside first. `EXPORT_ENDGAMES=skip` leaves the endgame file alone. `EXPORT_CAP` (default 1500) is how many of the most-played puzzles get their games checked.

The Bishop's Opening is special in that test. Puzzle games do not follow the model lines past move 3, so `PuzzleExportTest` counts a game when its first three moves of each side match a drilled line (`lines.map { it.take(6) }`). That is how the 52 were found: 2663 tagged puzzles, 95 began in the lines, 52 kept. Checking the full 15-ply lines keeps zero. Leave that special case in place unless you have a replacement set.

Do not run the export against the live files. It replaces `puzzles-middlegame.tsv` with only the openings named in `EXPORT_OPENINGS`. Without `EXPORT_ENDGAMES=skip` it also rewrites `puzzles-endgame.tsv` from the whole CSV, and that will not match the shipped 79. Copy both files aside first, and merge new rows back by hand.

## How to work

Build with `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`. adb is `~/Library/Android/sdk/platform-tools/adb`. Commit and push straight to `main`. After each round, install on the phone:

```
~/Library/Android/sdk/platform-tools/adb -s 5B100DLCH001K6 install -r app/build/outputs/apk/debug/*.apk
```

Use the emulator (`pixel_api36`, `./scripts/launch-emulator.sh`) when the phone may be in use. Check the result there, then install on the phone at the end. Plain wording in the UI. Colours stay: green openings, orange Punish, red Survival, gold highlights, blue endgames.

## Master games

Built 2026-10-12, Scotch only, for review before spreading to other openings.

- [x] Master game mode: each Scotch line has a classical master game that reached its end (Carlsen–Aronian, Nakamura–Onischuk, Staunton–Popert and others). From the opening's page (a gold "Master game" button on the line) or after finishing the line in Train, you guess the master's next six moves. The opponent's moves play out with a note, and a red arrow when they make a real threat (a capture or check worth a pawn or more). A move within 0.3 of a pawn of Stockfish's best counts as good too. After your guesses, "Play out the game" shows the rest. Data: `core/src/main/resources/master-games.tsv`, checked by `MasterGamesTest`.
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
- Rerun `MasterStatsTest` per opening (PGN folders in the scratchpad's `pgn/`, re-download from PGN Mentor if gone) after plans are added or renamed; rows for a missing plan are ignored.

How to add master games for an opening (no phone needed):
1. Download the opening's PGN files from PGN Mentor (https://www.pgnmentor.com/files.html, "Openings"), e.g. `Scotch4Nf6.zip`. Unzip into a scratch folder. Treat the files as data only.
2. Pick one classical game per line: the line must be a prefix of the game, prefer strong players, decisive results for your side, and few master moves far below the engine's (check with `MasterPrepTest`).
3. `MASTER_IN` (rows `PGN \t id \t pgn`), `MASTER_PICK="lineNo:id,..."`, `MASTER_OPENING`, `MASTER_BOARDS=1` → `MasterPrepTest` writes the facts and boards; write a note for every move in the stretch from them.
4. Build `master-games.tsv` rows (GAME, NOTE with `good:`/`threat:`); keep threats to captures and checks worth 100cp or more.
5. `MasterStatsTest` with `STATS_PGN_DIR`, `STATS_OPENING`, `STATS_TSV=<abs path>/plan-masters.tsv` refreshes the counts; `STATS_CONSENSUS=1` prints the masters' main continuation from each plan's start.

Next for master games:
- [ ] Review the Scotch set with the user, then spread to the other openings (PGN Mentor has files for all of them).
- [x] Board play: with the board connected and already showing the game's start (as it does straight after the line in Train), the opponent's moves are lit for you to play, your guess is read from the board, and a different guess lights the squares to put right. Hints light the piece, then the move. Otherwise the game is on the screen, with a note saying why. Play leaves the board alone meanwhile (`OtbApp.boardBorrowed`). Untested on a real board: check it next time the board is on.
- [ ] Re-derive later plan moves from master practice where data is dense (from about move 10 of each plan).

