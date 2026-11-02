# Master games for the White repertoire against 1.e4 (extra notes for BRIEF.md)

Read `/Users/ciaranwhyte/git/chessnutApp/data-cache/masters/BRIEF.md` first and follow it exactly: it is the lean master-game
workflow (prep.py, autonotes.py, edit the TODO notes, build.py, validate.py).
Differences here:
- Folders are `wr4`, `wr5` ... (one per repertoire chapter; the worklist is `M/<folder>-work.tsv`; `M/white-chapters.tsv` names
  each folder's chapter). The user plays WHITE in every one of them. Do only the folders and lines you were given.
- The lines are long (20 to 26 plies), so each game reaches its line deep in the opening and the guessing stretch starts
  late. Teach the plan from the line's end: what each side is aiming for.
- Opening names in the rows come out as `Repertoire: <chapter>`: that is correct, leave it.
- If a game turns out poor (White's win comes only from a blunder with nothing to learn, or fewer than about 8 plies
  remain after the line), skip it and say why in your report.
