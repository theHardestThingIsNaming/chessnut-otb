---
name: explain-flagged-moves
description: Pull the moves the user flagged on the phone for a closer look, explain each one in depth, and ship the explanations in the app. Use when the user plugs in the phone and asks to explain, review or answer their flagged moves, or says "flagged moves".
---

The app has a hollow bookmark on master games, Train and the analysis screen. Tapping it flags the move just played,
with an optional question, into `files/flags.tsv` on the phone. This skill turns those into explanations the app shows
behind a small "i" on the same position and move, wherever it turns up.

1. **Pull**: `scripts/flags/pull.sh` (phone connected; set `SERIAL` if it is not 5B100DLCH001K6). It prints the file.
2. **Facts**: `python3 -I scripts/flags/facts.py <that file>` prints, for every flag not yet answered, the game so far,
   the note that was showing, the user's question, the board, and the engine's best four moves with scores plus how the
   played move compares. (Needs python-chess in `data-cache/pylib` and Stockfish.)
3. **Write** one explanation per flag, then append `key<TAB>text` rows to `core/src/main/resources/move-insights.tsv`
   (keep the key exactly as printed; `\n` for a new line). Write for a club player, plain British English, no em-dashes.
   Say, as they apply:
   - what the move does: the square, the piece, the idea, the threat or the plan it prepares;
   - why this move rather than the alternatives, naming the engine's other choices and what separates them
     (use the scores: "about equal", "a tenth of a pawn better", "this one drops material because ...");
   - for a quiet move (a waiting move, a small improvement, a regrouping, a prophylactic move): what it improves or
     takes away, and what would go wrong without it; say honestly when the choice is mostly a matter of taste;
   - what to look for next time in a similar position.
   Answer the user's question first when they asked one. Verify any concrete claim (a threat, a trap, "wins a pawn")
   with the engine before writing it; drop what you cannot confirm. No biographies or unverified facts.
4. **Test and ship**: `./gradlew :core:test`, build, push to main, and install on the phone (the usual routine). The
   flag then shows an "i" with the explanation. Flags with an explanation drop off the pending list by themselves.

Keep it lean: only flagged moves are analysed, a few hundred words each at most.
