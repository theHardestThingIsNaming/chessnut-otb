# Content tools

Python scripts that built the app's content (opening lines, middlegame plans, master-game notes). They were
copied here from the git-ignored `data-cache/` so they are not lost. They still read and write `data-cache/`
(master game files, the Lichess puzzle dump, `pylib` with python-chess) and `/opt/homebrew/bin/stockfish`, so
they only run on the machine that has those. Paths are hard-coded to `/Users/ciaranwhyte/git/chessnutApp`.

## White repertoire against 1.e4 (`tools/white`)
- `check.py file.tsv`: legality, then Stockfish depth 14 over every White move of each line (`chapter<TAB>SAN moves`).
- `trim.py in.tsv out.tsv`: cuts each line before White's first move that loses 0.9 pawns or more (2.2 for gambit
  chapters), drops short or bad-ending lines and duplicates.
- `plancheck.py plans.tsv`: the same engine check over plan rows, and which lines a plan covers.
- `survey.py`, `scan.py`, `mkwork.py`: find master games that reach a line in the master files.
- `PLAN-BRIEF.md`: the brief given to the agents that wrote the plan notes.
- Output goes to `core/src/main/resources/white-repertoire.tsv` and `white-plans.tsv`; `WhiteRepertoireTest` checks them.

## Master games (`tools/masters`)
- `prep.py folder line gameId`: the fact sheet (engine eval, best moves) for writing a game's notes.
- `autonotes.py`, `enrich.py`: first-draft notes from the facts. `BRIEF*.md`: the briefs for the writing agents.
- `build.py folder notes prep`: turns a prep sheet and a notes file into `master-games.tsv` rows.
- `validate.py rows`: the checks `MasterGamesTest` makes, without Gradle.
- `merge.sh`, `merge-enrich.sh`, `pick.py`, `at.py`, `ui.sh`: merging, choosing games, engine lookups, phone helper.
