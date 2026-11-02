# Upgrading generic master-game notes (token-lean)

Some master-game notes in the chess trainer app are plain drafts such as "White's bishop goes to c2." Your job is to
replace them with a sentence or two of insight: what the move does and why. The user said: "insights about the move
help a lot", including quiet moves (a waiting move, a small improvement, a regrouping): say what it improves or takes
away. Work in `M = /Users/ciaranwhyte/git/chessnutApp/data-cache/masters`, run tools from M as `python3 -I tools/...`.
Never edit anything outside `M/enrich/`. No Gradle, no commits.

## Per game (your list is given in the task)
1. `python3 -I tools/enrich.py list <game id>` prints one line per generic note: ply | move (who) | what it says now |
   FEN before the move | engine's top three moves there with scores (for the side to move) | after the move: eval
   (White's view) and the engine's line. About 20 s.
2. Write `M/enrich/<game id>.new`: one line per ply you improve, `ply<TAB>new text`. Do as many as you can with real
   content; skip a ply only when you honestly have nothing to add beyond the draft (the file may omit it).
3. `python3 -I tools/enrich.py check <game id> M/enrich/<game id>.new` and fix what it reports.
   For a concrete claim you are unsure of (a threat, a trap, "wins a pawn", what a piece now attacks), check it:
   `python3 -I tools/at.py <game id> <ply> [extra SAN moves] [--multi 2] [--depth 14]` prints the board and engine lines
   after the game's first <ply> moves (so ply p shows the position before move p is played; use p+1 for after).

## What a good note says
- The idea of the move in terms of the position: the square or file it takes, the piece it hits or defends, the plan
  it prepares ("Rd1 lines up against the d6 pawn before the knight is chased away"), or the weakness it creates.
- If the engine prefers another move by more than about 0.3 of a pawn, say so plainly, with the move.
- If the move is a small improvement or a waiting move, say that and what it improves.
- Both sides' moves: for the opponent's move, say what it threatens or what it gives up.
- One or two short sentences. Plain British English, no em-dashes. "King's side", "queen's side". "You" is the user's
  side as stated on the header line of the facts. Do not call moves "brilliant".
- Facts only from the position and the engine lines given. No biographies, no unverified claims.

## Finish
A two-line report: games done, notes written.
