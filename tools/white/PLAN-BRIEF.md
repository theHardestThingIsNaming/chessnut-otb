# Middlegame plans for White's repertoire against 1.e4 (the user drills these lines in a chess trainer app)

The lines: `/Users/ciaranwhyte/git/chessnutApp/core/src/main/resources/white-repertoire.tsv`, rows `chapter<TAB>w<TAB>SAN line`.
The user is White. After drilling a line, the app offers a PLAN: a model continuation (both sides) with a short note per move
and a summary of the idea. Write plans for your chapters (below). Work lean; do not edit any repo file except your output
file; never run Gradle or git.

## What to write
Cluster each chapter's lines by the point where Black's replies split, and write a handful of plans per chapter (3 to 6;
fewer if the lines agree for long). A plan covers every line that starts with its `before`. EVERY line of your chapters must
be covered by at least one plan. `before` = the cluster's shared opening moves (SAN, from move 1), trimmed to END ON A
WHITE MOVE (an odd number of plies). `moves` = the continuation, BLACK's move first, then alternating, 12 to 16 plies
(6 to 8 moves each), natural for the cluster; it ends with the position's idea clear. Pick the plan's moves from the
position at the end of the cluster's most played line (the first line in the chapter) where possible, then play on with
good moves.

Row format, TAB separated, one row per plan, in `/Users/ciaranwhyte/git/chessnutApp/data-cache/white/plans-<group>.tsv`:
`chapter<TAB>plan name<TAB>before<TAB>moves<TAB>summary<TAB>note 1 ¦ note 2 ¦ ... `
- chapter: exactly as in the lines file. Plan name: short, e.g. "Attack on the king's side".
- moves: SAN only, no move numbers, space separated. Notes: exactly one per move, joined by ` ¦ ` (space, U+00A6, space).
  Never put a TAB or the ¦ character inside a note.
- summary: 1 or 2 sentences, the idea in plain words (what White does, what Black's weaknesses are).
- Each note is one short sentence of INSIGHT, not description: the idea of the move, the threat, the plan, what it prevents.
  ("The bishop comes to b5 to pin the knight, so e5 can't be hit." beats "Bishop to b5.") Say what Black is aiming for too.
- Style: plain British English, no em-dashes, "king's side", "queen's side". Never "brilliant". Facts only about the
  position; no names or biographies.
- White's moves must be sound: check with `cd /Users/ciaranwhyte/git/chessnutApp/data-cache/white && python3 -I plancheck.py plans-<group>.tsv`
  (legality, coverage, and White's moves each losing at most 0.6 pawns against the engine's best). Fix every ERR and every
  UNCOVERED line, then run it again. Verify any concrete claim (a win of a pawn, a mate, a trap) with Stockfish before
  writing it; drop what you can't confirm.
Final report: 3 or 4 lines (plans per chapter, anything you couldn't cover).
