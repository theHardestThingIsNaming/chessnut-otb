# Club games: plan and leftovers

One list for the next session. The older "common mistakes and Punish" notes are folded in here.
What is already on the phone is under Done. What is worth doing next is under Next.

The app teaches an opening, then three things past move 15: middlegame plans, middlegame tactics,
and endgames. Built the same way for the main openings, including the Bishop's Opening.

Last shipped, and on the phone: `e13c2cf` on `main`. Working tree was clean at that commit.

## Done

- [x] Puzzle pipeline and puzzle trainer. A clean solve comes back once, after 3 days, then the puzzle is done. A miss comes back tomorrow. Opening lines still use 1, 3, 7, 14, 30 days.
- [x] Middlegame tactics on every main opening's page, including the Bishop's Opening. Lichess puzzles whose games started in your lines: Scotch 60, Italian 60, Nimzo-Indian 60, Pirc 58, King's Indian 57, Caro-Kann 57, Ruy Lopez 55, Slav 55, French 53, Vienna 52, Bishop's Opening 52, Sicilian 49, London 45, English 7.
- [x] Endgames on the home screen: 79 puzzles in six types, rating 1800 and up, long races left out. Pawn 11, rook 14, knight 15, bishop 14, queen 11, queen and rook 14.
- [x] Eight Essential endgames against the Lichess tablebase (the ladder, queen and rook mates, the square of the pawn, taking and holding the opposition, Lucena, Philidor). A move that lets the result slip is turned back. A mastered essential is hidden on the Endgames page until you tap "Show mastered".
- [x] A puzzle section with nothing left due (an endgame type, or an opening's middlegame tactics) is hidden the same way, with "Show solved". It comes back on its own when a puzzle is due again.
- [x] Middlegame plans for all 14 openings. Four each, except the English (3), the Vienna (2) and the London (2), which have fewer distinct structures in the lines you drill. The Bishop's Opening has four, one per line. Each plan starts from a line you drill, has a note per move, and no move of the planning side gives away more than about 60 centipawns at Stockfish depth 18.
- [x] Common mistakes and Punish mode, traps for the main openings, and the book audit that keeps your own blunders out of the lines.
- [x] Bishop's Opening for White. The four replies players rated 1400–2000 actually play (Nf6, Nc6, Bc5, d6), and in each the white move that keeps the advantage. Punishes after 3.Nf3 against ...Bc5: ...Qg5 loses the queen, ...f5 loses e5 and opens the king, ...d5 loses a pawn. Four middlegame plans, one per line (knight to g5, knight to g3, bishop to b5 then d4, queen in the centre). 52 middlegame tactics. A written note on every move of the four lines.

## Next

- [ ] A "punished" count or medal on the opening's page, next to the lines and Survival figures.
- [ ] The branch note in Train wraps in the middle of a move ("4." on one line, "Nxd4" on the next). Keep the move together.
- [ ] Rerun the loosened puzzle match for the Scotch and the Pirc only. The other openings already had it (`OpeningTraps.fits`: only your own moves need to follow the lines). Those two still have the older, stricter set.
- [ ] Common-mistake scan for the openings other than the Scotch and the Pirc. The explorer hook was removed after the Scotch/Pirc run; recreate it, scan, then `MistakeMineTest` at depth 20 and keep the moves that lose 150cp or more.
- [ ] Optional. Two more Essential endgames if they earn a place: the Vancura (rook against a rook's pawn) and queen against a pawn. The eight already cover the usual club endings.
- [ ] Optional. A third Vienna plan and a third London plan, if a sound one turns up. The Vienna with ...g5 is already a tactic (the queen trade on f6), not a plan. Against the London's ...Qb6 the engine wants Na4, which is not written up yet.

## Left as it is

Bringing a solved puzzle or essential back early because a new mistake in a game "should have been" caught by it. Matching the exact position is possible and almost never happens: a club game does not reach the Lichess puzzle you solved. Matching the idea (a fork, a pin, the Lucena shape) would put the wrong puzzles back into To do. The day steps above are the schedule.

## Checking a plan

Plans live in `MiddlegamePlans.kt`. `before` is the end of a line from `OpeningTree.lines` (it must be a prefix of one). `moves` is both sides, starting with the opponent. One note per move.

Write candidates as `label|before|moves` and run:

```
PLAN_IN=/tmp/plan-in.txt PLAN_OUT=/tmp/plan-out.txt STOCKFISH=/opt/homebrew/bin/stockfish \
  ./gradlew :core:test --tests '*PlanCheckTest*' --rerun-tasks
```

The number after each move is the centipawns it gives away. Keep the planning side's under about 60. The first move of `moves` is the opponent's; then they alternate. `ILLEGAL` means the move does not parse. Gradle caches tests, so a changed input needs `--rerun-tasks`.

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
