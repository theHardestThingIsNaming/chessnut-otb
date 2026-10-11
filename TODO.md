# Plan: common mistakes and Punish mode

## 1. Common club mistakes (data first), in progress

Goal: replace hand-picked traps that club players rarely fall for with mistakes they actually make.

- [x] Show how often players rated 1400–2000 play each trap's mistake (Lichess explorer, `TrapStats`).
- [x] Show traps only in a line's Explain mode.
- [ ] Explorer scan: for every position in your Scotch (White) and Pirc (Black) lines where the
      opponent is to move, fetch the moves club players play there. The explorer needs a login,
      so a temporary debug hook in the app does this (`ExplorerScan.kt`, started with
      `adb shell am force-stop app.chessnut.otb` then `adb shell am start -n app.chessnut.otb/.MainActivity --ez explorerScan true` (it only runs on a fresh start), which writes
      `files/explorer-scan.tsv`). Remove the hook once done; don't commit it.
- [ ] On the Mac, score each popular move (≥3% of games, ≥30 games) with Stockfish at depth 20.
      Keep the moves that lose ≥150cp against the best move and have a clear refutation.
- [ ] Add them to `OpeningTraps` as "common mistakes" next to the traps (one note per move, a
      summary), and drop traps that are Rare and nearly never played (The b5 pawn, The trapped
      bishop, The loose e4 pawn, The c7 fork).
- [ ] Wording: the button and banner say "Trap" for piece-winning tricks and "Common mistake"
      for smaller punishable errors (losing a pawn, a bad structure). Both show the
      Common/Occasional/Rare label.

## 2. Punish mode (new mode)

Idea: play a normal line; at some point the computer plays a common mistake instead of the
book move, and you have to find the best way to punish it.

- Source: the traps and common mistakes from part 1. Each one starts from a position in your
  lines (`Trap.before`) and has the mistake (`moves[0]`) and the refutation (`moves[1..]`).
- Flow:
  1. Choose an opening and Punish (a third button beside Train and Survival, with its own
     colour, e.g. Mistake orange).
  2. The app plays the line up to the trap position. You play your book moves as usual.
  3. The opponent plays the mistake. A prompt says "Your opponent erred. Punish it!".
     No arrows and no notes.
  4. You play your move. It's correct if it's the refutation's first move, or if Stockfish
     scores it within ~50cp of the best move (several moves can punish).
  5. For longer refutations, keep going: the opponent plays the engine's best defence and
     you find each follow-up, up to the end of the refutation.
  6. Then reveal: the notes from the trap, the eval swing, and Common/Occasional/Rare.
- Scoring: spaced repetition per mistake, like lines (`LineProgress`, keyed by trap name).
  Missed ones come back tomorrow. A "punished" count on the opening's page.
- Order: start with mistakes you haven't punished, then due reviews; weight by how often
  club players make them (Common first).
- UI: reuse `PlayKind.Trainer` with a `punish` flag, like Survival's flag. The TrapBanner shows
  only after the answer. A hint button reveals the piece to move.
- Tests: core test that every mistake's refutation is legal and that the first refutation
  move is Stockfish's best (TrapCheckTest already checks this).

## Order of work

1. Finish part 1: the scan, scoring, new mistakes, and pruning rare traps.
2. Build Punish mode on the Scotch and Pirc.
3. Extend the scan and mistakes to the other openings in the catalog.
