#!/bin/bash
# ui.sh texts | tap "regex" | desc "regex"   (emulator; on-screen text and content descriptions with bounds)
ADB=~/Library/Android/sdk/platform-tools/adb; S="-s emulator-5554"
dump() { $ADB $S shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; $ADB $S shell cat /sdcard/ui.xml | python3 -c '
import sys,re,html
x=sys.stdin.read()
for m in re.finditer(r"<node [^>]*>",x):
    n=m.group(0); t=re.search(r" text=\"([^\"]*)\"",n); d=re.search(r" content-desc=\"([^\"]*)\"",n); b=re.search(r" bounds=\"([^\"]*)\"",n)
    s=(t.group(1) if t and t.group(1) else (d.group(1) if d else ""))
    if s and b: print(html.unescape(s)+"  "+b.group(1))
'; }
case "$1" in
  texts) dump ;;
  tap) l=$(dump | grep -E "^$2  \[" | head -1); [ -z "$l" ] && { echo "missing: $2"; exit 1; }
       read x1 y1 x2 y2 <<<$(echo "$l" | sed -E 's/.*\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]$/\1 \2 \3 \4/')
       $ADB $S shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )) ;;
esac
