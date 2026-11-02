#!/bin/bash
# Merges finished games from data-cache/masters/out into master-games.tsv: each validated, then MasterGamesTest.
# Commits, pushes, and installs on the phone if it's connected. A merged rows file moves to out/merged/.
R=/Users/ciaranwhyte/git/chessnutApp; M=$R/data-cache/masters; T=$R/core/src/main/resources/master-games.tsv
mkdir -p $M/out/merged
added=()
for f in $M/out/*.rows; do
  [ -e "$f" ] || continue
  if python3 -I $M/tools/validate.py "$f" > /dev/null 2> $M/merge-err.txt; then
    cat "$f" >> $T; added+=("$(basename "$f" .rows)")
  else
    echo "rejected $(basename "$f"): $(tail -1 $M/merge-err.txt)"
  fi
done
[ ${#added[@]} -eq 0 ] && { echo "nothing new"; exit 0; }
cd $R
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
if ! ./gradlew -q :core:test --tests '*MasterGames*' > $M/merge-test.txt 2>&1; then
  echo "MasterGamesTest failed:"; grep -v "^\s*at " $M/merge-test.txt | head -20; git checkout -- $T; exit 1
fi
for a in "${added[@]}"; do mv "$M/out/$a.rows" "$M/out/merged/"; done
git add $T && git commit -q -m "Master games: ${added[*]}.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>" && git push -q origin main
echo "merged ${#added[@]}: ${added[*]}; $(grep -c '^GAME' $T) games in all"
if ~/Library/Android/sdk/platform-tools/adb devices | grep -q '^5B100DLCH001K6\s*device'; then
  ./gradlew -q :app:assembleDebug > /dev/null 2>&1 && ~/Library/Android/sdk/platform-tools/adb -s 5B100DLCH001K6 install -r app/build/outputs/apk/debug/app-debug.apk 2>&1 | tail -1
else
  echo "phone not connected: not installed"
fi
