# Master games for the chess trainer app (token-lean workflow)

The user drills opening lines on a real board. For a line, the app replays a master game that reached its end: the user
plays their line, then guesses the master's moves, with a note on every move, pauses at key moments, an intro and an
outro. The user wants rich content: insights about the move help a lot. Quota is tight, so work lean.

Work in `M = /Users/ciaranwhyte/git/chessnutApp/data-cache/masters`. Tools in `M/tools`, run from M as
`python3 -I tools/<tool>.py`. Never edit anything else in the repo, never run Gradle, never commit. Your output is one
rows file per game in `M/out/`.

## Worklist
`M/<folder>-work.tsv`: `rank, line, game id, players, ratings, year, event, plies`. Skip DONE and SKIP rows, and any line
with `out/<folder>-<line>.rows` or `out/merged/<folder>-<line>.rows`. Do games in rank order.

## Per game (4 commands, then edit one file)

1. `python3 -I tools/prep.py <folder> <line> <game id>`        engine facts, about 15 s, no boards (small output)
2. `python3 -I tools/autonotes.py <folder> <line> <folder>-<line>-prep.txt`
   Writes `M/<folder>-<line>.notes`: a factual draft note for every ply. Plies that need insight start with `TODO:`.
3. Open the notes file (one read). Rewrite only:
   - every `TODO:` note (turning points, captures with check, the first guess, the last moves) into a note that says
     the idea: the threat, the plan, the weakness, why it works, or why it is a mistake and what was better;
   - the intro, the outro, and 3 or 4 `review` lines (one per turning point; see format);
   - the `end` line (just after the decisive moment; 10 to 25 of your moves to guess);
   - any other note where you know the idea at once and can say it in a sentence (no engine call needed).
   Leave the rest of the drafted notes alone: they are accurate.
   Use `python3 -I tools/at.py <folder>-<line>-prep.txt <ply> [extra SAN] [--multi 2] [--depth 14]` to check a claim
   you're unsure of (a mate, a won piece, a trap, "X was better"): only for claims you will actually write.
4. `python3 -I tools/build.py <folder> <folder>-<line>.notes <folder>-<line>-prep.txt > out/<folder>-<line>.rows`
   `python3 -I tools/validate.py out/<folder>-<line>.rows`   Fix what it reports. A failing rows file: delete it.

Edit the notes file with small replacements of single lines, not by rewriting the whole file.

## Format reminders (the draft already has the right lines)
```
end <ply>                  guess runs from the line's end up to this ply
white <name> / black <name> / event <event, place>      optional overrides ("Bobby Fischer"; "K. Guseinov" if only an initial)
intro <text>               end with "Play your line, then find <surname>'s moves."
note <ply> <text>          every ply from the line's end to the game's last move
review <ply>|<title>|<text>|<arrows>      game pauses after the move at <ply>; arrows uci:C, C = G idea, B support, R danger
outro <text>
```
Arrows must start on a square with a piece (before or after the move at that ply), e.g. `f4f5:G,d5f6:B`.
Ply p: White's move number p/2+1 when even, Black's when odd. The user is White in english, ruy, italian, scotch,
london, vienna, bishop; Black in sicilian, french, kid, pirc, slav, nimzo.

## Style
- Plain, short, British English, no em-dashes. "King's side", "queen's side". Never "brilliant"; "!" sparingly.
- Teach: a plan, a pattern, a reason. "The bishop comes to b7, aiming at your king's side" beats "develops the bishop".
- Say honestly when the master (or the user's side) errs, and what the engine preferred.
- **Facts about people and events only from the PGN header** (names, ratings, event, year, result) and what is plainly
  true of the position. No web searches, no biographies, no tournament winners, no ages or titles. When unsure, leave
  it out. A plain intro and outro are fine; a wrong fact is not.
- Reviews often end with a question ("Can you find it?") when they come just before the master's key move.

## Finish
Report in a few lines: games written (line, players, one-line lesson) and any skipped with the reason. Keep the report short.
