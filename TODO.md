# Plan: common mistakes and Punish mode

## 1. Common club mistakes (data first), in progress

Goal: replace hand-picked traps that club players rarely fall for with mistakes they actually make.

- [x] Show how often players rated 1400–2000 play each trap's mistake (Lichess explorer, `TrapStats`).
- [x] Show traps only in a line's Explain mode.
- [x] Explorer scan: for every position in your Scotch (White) and Pirc (Black) lines where the
      opponent is to move, fetch the moves club players play there. The explorer needs a login,
      so a temporary debug hook in the app does this (`ExplorerScan.kt`, started with
      `adb shell am force-stop app.chessnut.otb` then `adb shell am start -n app.chessnut.otb/.MainActivity --ez explorerScan true` (it only runs on a fresh start), which writes
      `files/explorer-scan.tsv`). Done 2026-10-11 (42 positions); the hook was removed. Recreate it for other openings.
- [x] On the Mac, score each popular move (≥3% of games, ≥30 games) with Stockfish at depth 20.
      Keep the moves that lose ≥150cp against the best move and have a clear refutation.
      Tool ready and dry-run checked: `MistakeMineTest` (run with `MISTAKE_IN=<pulled scan>
      MISTAKE_OUT=<out> STOCKFISH=/opt/homebrew/bin/stockfish ./gradlew :core:test --tests '*MistakeMineTest*' --rerun-tasks`).
- [x] Add them to `OpeningTraps` as "common mistakes" next to the traps (one note per move, a
      summary), and drop traps that are Rare and nearly never played (The b5 pawn, The trapped
      bishop, The loose e4 pawn, The c7 fork).
- [x] Wording: the button and banner say "Trap" for piece-winning tricks and "Common mistake"
      for smaller punishable errors (losing a pawn, a bad structure). Both show the
      Common/Occasional/Rare label.

## 2. Punish mode, done (2026-10-11)

Built as planned: a Punish button on the opening page, the game set just after the mistake,
the refutation or an engine-approved alternative (within 6% winning chance) accepted, wrong
moves turned back with the answer lit, a hint counts as help, spaced repetition per mistake
under "Punish: <opening>", eval hidden until answered, Explain it and Next mistake at the end.

Still to do:
- [ ] A "punished" count or medal on the opening's page.
- [ ] Tidy the branch note wrapping mid-move ("4." / "Nxd4").

## 3. Mistakes from the Lichess puzzle database, started (2026-10-11)

The puzzle database (database.lichess.org, CC0) has ~290k opening-phase puzzles, each tagged
with its opening. `PuzzleGamesTest` keeps those whose game followed one of your lines to within
6 moves of the mistake (games fetched in bulk from `/api/games/export/_ids`, no login needed).
For the Scotch and Pirc it found 19; 6 were added after a Stockfish check.

- [x] Run it for the other openings: 210 matches across 11 openings; 30 added after a Stockfish
      check, 2–3 per opening.
- [x] Loosen the match: only your own moves need to follow your lines (`OpeningTraps.fits`).
- [ ] Rerun the loosened match for the Scotch and Pirc, which only had the strict one.
- [x] Raised the cap to 6,000 puzzles per opening (31k candidates): 295 new matches; 9 added
      for the thin openings. Classic named traps: only the Siberian trap and the London's
      poisoned b2 pawn both fit your lines and lose with best play; the rest are sound.
- [ ] Your Ruy Lopez line `…b5 Bb3 d6 d4 Nxd4 Nxd4 exd4 Qxd4??` is the Noah's Ark trap
      (…c5 and …c4 trap the bishop): the book names that line after it, so the trainer
      drills White's losing move. Decide whether to drop or fix the line.

## Order of work

1. Extend the scan and mistakes to the other openings in the catalog.
