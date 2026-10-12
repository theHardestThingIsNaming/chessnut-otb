# Plan: beyond the opening (middlegame plans, middlegame tactics, endgames)

The app teaches openings well: Train, Punish, Survival, traps, mistakes. This plan carries
a player past move 15 with three things, built the same way for every opening, starting with
the Scotch (White) and the Pirc (Black).

## What the player gets

1. **Middlegame plans** for each opening: the 3–5 typical plans that follow from its lines
   (the Scotch's queenside pawn majority, the Pirc's ...c5/...e5 breaks and so on). Each is a
   short animated walk-through on the board, with notes and arrows, like the trap viewer.
   Ends with "Try it": play the position on against Stockfish.
2. **Middlegame tactics** for each opening: real puzzles from games that started with your
   lines, so the tactics are the ones *your* structures produce. Solved on the board, one
   move at a time, with spaced repetition.
3. **Endgames**, a new section on the home screen:
   - **Endgame puzzles** by type (pawn, rook, minor piece, queen), easiest first.
   - **Essential endgames**: Lucena, Philidor, king and pawn opposition, the square rule and
     more, played against perfect defence, with every move checked by the tablebase. A move
     that throws away the win (or the draw) is caught at once, with the right one shown.

## Open resources (all free to use)

| Source | Licence | Used for |
|---|---|---|
| Lichess puzzle database (database.lichess.org) | CC0 | Middlegame tactics (themes `middlegame` + opening tags), endgame puzzles (`pawnEndgame`, `rookEndgame`, `bishopEndgame`, `knightEndgame`, `queenEndgame`, `queenRookEndgame`) |
| Lichess game export (`/api/games/export/_ids`) | Lichess ToS, public games | The moves before each puzzle, to check it came from your lines |
| Lichess tablebase API (tablebase.lichess.ovh) | Free, no login | Perfect play and move-by-move checking in essential endgames (7 pieces or fewer) |
| Stockfish (bundled) | GPL | Checking every plan's moves; "Try it" opponent; offline fallback |
| Plans' wording | Written here | Facts about structures are free; no text copied from books or sites |

## How it fits the app (UX)

- **The opening page grows a "Beyond the opening" card** under the Train / Punish / Survival
  row: *Middlegame plans (4)* and *Middlegame tactics (40)*, with progress like the lines'.
  No new screens to learn: plans use the trap viewer, tactics use the Punish-style board flow
  (prompt, ✓/✗ mark, hint, explain, next).
- **Home gets an "Endgames" tile** next to Openings and Mistakes, with a due count.
- **One puzzle trainer** serves middlegame tactics and endgame puzzles: same board, marks,
  hints, spaced repetition (1, 3, 7, 14, 30 days) and "Next".
- **Colour language stays**: orange for punishing, red for Survival, gold for highlights, and
  a new calm blue for endgames.

## Scalable pipeline (any opening, one command)

Tools in `core/src/test` (gated, like the existing ones), with downloads cached in
`data-cache/` (git-ignored):

1. `PuzzleExportTest`: from the puzzle CSV, picks per opening (a) middlegame puzzles from
   the opening's tag, solved by the opening's side, popularity ≥ 80; (b) endgame puzzles by
   type, spread over ratings 800–2200. Fetches their games and keeps middlegame puzzles whose
   game came from your lines (`OpeningTraps.fits`, with a longer reach).
2. Writes `core/src/main/resources/puzzles-middlegame.tsv` and `puzzles-endgame.tsv`, small
   enough to ship (a few hundred rows each).
3. Plans live in `MiddlegamePlans.kt`, one entry per plan (name, opening, side, the line
   it starts from, model moves, a note per move); a test checks every plan is legal, starts
   from your lines and that Stockfish doesn't refute its moves.

Adding an opening = run the export with its name, write its plans, run the tests.

## Phases

- [x] 1. Pipeline: export middlegame tactics (Scotch, Pirc) and endgame puzzles to resources.
- [x] 2. Puzzle trainer: one screen for both, with marks, hints, spaced repetition.
- [x] 3. Opening page: "Beyond the opening" card with Middlegame tactics.
- [x] 4. Home: Endgames tile, endgame puzzles by type.
- [x] 5. Middlegame plans for the Scotch and the Pirc, in the trap viewer, engine-checked.
- [ ] 6. Essential endgames against the tablebase.
- [ ] 7. Extend tactics and plans to the other main openings.

Each phase: tests, emulator check, commit, install.
