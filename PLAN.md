# Club games: plan and leftovers

One list for the next session. The older "common mistakes and Punish" notes are folded in here.
What is already on the phone is under Done. What is worth doing next is under Next.

The app teaches an opening, then three things past move 15: middlegame plans, middlegame tactics,
and endgames. Built the same way for the main openings, including the Bishop's Opening.

Last shipped, and on the phone: the commit that adds the twelve scanned mistakes, on `main`.

## Done

- [x] Puzzle pipeline and puzzle trainer. A clean solve comes back once, after 3 days, then the puzzle is done. A miss comes back tomorrow. Opening lines still use 1, 3, 7, 14, 30 days.
- [x] Middlegame tactics on every main opening's page, including the Bishop's Opening. Lichess puzzles whose games started in your lines: Scotch 60, Italian 60, Nimzo-Indian 60, Pirc 58, King's Indian 57, Caro-Kann 57, Ruy Lopez 55, Slav 55, French 53, Vienna 52, Bishop's Opening 52, Sicilian 49, London 45, English 7.
- [x] Endgames on the home screen: 79 puzzles in six types, rating 1800 and up, long races left out. Pawn 11, rook 14, knight 15, bishop 14, queen 11, queen and rook 14.
- [x] Eight Essential endgames against the Lichess tablebase (the ladder, queen and rook mates, the square of the pawn, taking and holding the opposition, Lucena, Philidor). A move that lets the result slip is turned back. A mastered essential is hidden on the Endgames page until you tap "Show mastered".
- [x] A puzzle section with nothing left due (an endgame type, or an opening's middlegame tactics) is hidden the same way, with "Show solved". It comes back on its own when a puzzle is due again.
- [x] Middlegame plans for all 14 openings. Four each, except the English (3), the Vienna (2) and the London (2), which have fewer distinct structures in the lines you drill. The Bishop's Opening has four, one per line. Each plan starts from a line you drill, has a note per move, and no move of the planning side gives away more than about 60 centipawns at Stockfish depth 18.
- [x] A Punish card on each opening's page, in orange under the Survival card: how many of its common mistakes you have punished without help (for example 3/12).
- [x] Common-mistake scan of the other openings: 310 positions from the Lichess explorer (players rated 1400–2000), scored by Stockfish at depth 20. 67 moves lose 150cp or more; twelve of the most played are now Punish mistakes, each checked: the Fried Liver's ...Ke8, ...Qf6 and ...Kg8, ...Nxe4 against Ng5, the pinned c6 knight in the Ruy Lopez, ...Bxf2+ in the Bishop's Opening, three Vienna slips against f4, the English's ...Bc5, Nxf7 against the Caro-Kann, and Nxe5 into ...Qa5+ in the Sicilian. The full list is in `data-cache/scan/mistakes.tsv` for more.
- [x] Explain lights your move on the board: on your turn in Train, the green arrow's move (the line's, or the book's favourite past its end) is lit, so the board guides you as the screen does. Switching Explain off clears it.
- [x] Review of the day's work: every plan and trap rechecked with Stockfish (`PLAN_IN=all`, `TRAP_IN=all`). The Vienna's dark-squares plan now castles before Be3 (Be3 at once lost 65cp to ...Bg4). A missed puzzle no longer counts as solved.
- [x] The branch note in Train keeps each move together ("4. Nxd4" no longer splits across lines).
- [x] Loosened puzzle match rerun for the Scotch and the Pirc (187 matches, up from 19). Five new Punish mistakes, Stockfish-checked: the loose c5 bishop (...h6? Nxc6), the e-file pin (...Nxe5? and Re1), a smothered mate on d6, ...Qxf2# against an early dxc6, and the knight raid on e3. Candidates and games are in `data-cache/punish/`.
- [x] Common mistakes and Punish mode, traps for the main openings, and the book audit that keeps your own blunders out of the lines.
- [x] Bishop's Opening for White. The four replies players rated 1400–2000 actually play (Nf6, Nc6, Bc5, d6), and in each the white move that keeps the advantage. Punishes after 3.Nf3 against ...Bc5: ...Qg5 loses the queen, ...f5 loses e5 and opens the king, ...d5 loses a pawn. Four middlegame plans, one per line (knight to g5, knight to g3, bishop to b5 then d4, queen in the centre). 52 middlegame tactics. A written note on every move of the four lines.

## Next

- [ ] Optional. Two more Essential endgames if they earn a place: the Vancura (rook against a rook's pawn) and queen against a pawn. The eight already cover the usual club endings.
- [ ] Optional. A third Vienna plan and a third London plan, if a sound one turns up. The Vienna with ...g5 is already a tactic (the queen trade on f6), not a plan. Against the London's ...Qb6 the engine wants Na4, which is not written up yet.

### UX review, 2026-10-12

Walked through on the emulator: Home, Openings, an opening's page, Train, Punish, Mistakes, Endgames, a puzzle, plans. Not yet looked at: bot play, online, analysis, settings.

Fix first:
- [x] Back (the gesture and the ← arrow) from Train, Punish or Survival goes straight to Home, skipping the opening's page and the list. It should return to the opening's page you started from.
- [x] The Explain caption on the opening's page is out of date ("arrows show the book moves"). Say the green arrow is this line's move, and that the board lights it.
- [x] Endgames don't use their blue: the Home tile has the bot's pawn icon in green, and a puzzle's Hint button is green with a gold progress bar. The Home subtitle ("Puzzles from real endings, by type") leaves out the Essentials.
- [x] Punish's prompt card has a green border; Punish is orange.

Consistency:
- [ ] Hints differ: Punish lights the whole move at once, puzzles light the piece then the move. Pick one (the two steps).
- [ ] Puzzles don't say what the opponent just played; Punish and Mistakes do ("Your opponent played 7… Qxg2").
- [ ] In Train and Punish the opening's name is in the top bar and again as the green heading under it, and that heading is also where status text ("Hint lit") appears. Use the heading for status only.
- [ ] On the opening's page the plan count is gold and the tactics count green. Use one colour.
- [ ] The Essentials and puzzle sections on Endgames use different card styles. Essentials show no state (not tried, slipped, mastered).

Smaller:
- [ ] The Openings header reads "3 lines learned · In 5 openings": it counts openings started, which reads as if three lines were spread over five. Say "Started 5 openings" or count only openings with a learned line.
- [ ] An empty puzzle progress bar shows a stray dot at its right end (Material's stop indicator).
- [ ] Mistakes opens on a bare spinner for several seconds. Show what it's doing ("Loading 31 positions…").
- [ ] Punish, Mistakes and puzzles leave half the screen empty under the board. Room for the game's moves, or the idea and the eval after you answer.
- [ ] Home's Openings and Mistakes tiles change their subtitles once the counts load, which shifts the text. Reserve the line.
- [ ] An unanalysed game shows "Accuracy – · –". Say "Not analysed yet".
- [ ] "Lichess wins –" with no login gives no hint that logging in fills it.
- [ ] The plans list doesn't show which plans you've stepped through or tried against the bot.

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
