#!/bin/bash
# Applies every data-cache/masters/enrich/<id>.new (checked) to master-games.tsv, runs MasterGamesTest, commits, pushes
# and installs on the phone if connected. A merged file moves to enrich/merged/.
R=/Users/ciaranwhyte/git/chessnutApp; M=$R/data-cache/masters; T=$R/core/src/main/resources/master-games.tsv
mkdir -p $M/enrich/merged
cp $T $M/enrich/master-games.backup
applied=(); total=0
for f in $M/enrich/*.new; do
  [ -e "$f" ] || continue
  id=$(basename "$f" .new)
  if python3 -I $M/tools/enrich.py check "$id" "$f" > /dev/null 2> $M/merge-err.txt; then
    n=$(python3 -I $M/tools/enrich.py apply "$id" "$f" | sed -E 's/.*: ([0-9]+) of.*/\1/')
    applied+=("$id"); total=$((total + n))
  else
    echo "rejected $id: $(tail -1 $M/merge-err.txt)"
  fi
done
[ ${#applied[@]} -eq 0 ] && { echo "nothing new"; exit 0; }
cd $R
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
if ! ./gradlew -q :core:test --tests '*MasterGames*' > $M/merge-test.txt 2>&1; then
  echo "MasterGamesTest failed:"; grep -v "^\s*at " $M/merge-test.txt | head -10; cp $M/enrich/master-games.backup $T; exit 1
fi
for id in "${applied[@]}"; do mv "$M/enrich/$id.new" "$M/enrich/merged/"; done
git add $T && git commit -q -m "Master games: $total generic notes in ${#applied[@]} games rewritten with the idea of the move.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>" && git push -q origin main
echo "applied $total notes in ${#applied[@]} games"
if ~/Library/Android/sdk/platform-tools/adb devices | grep -q '^5B100DLCH001K6\s*device'; then
  ./gradlew -q :app:assembleDebug > /dev/null 2>&1 && ~/Library/Android/sdk/platform-tools/adb -s 5B100DLCH001K6 install -r app/build/outputs/apk/debug/app-debug.apk 2>&1 | tail -1
else
  echo "phone not connected: not installed"
fi
