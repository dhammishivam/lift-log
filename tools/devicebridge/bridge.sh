#!/bin/bash
# Lift Log device bridge: runs adb on this Mac for Claude, without Unity.
#
# Claude's sandbox can't reach USB, but it shares this repo folder. It writes adb command lines to
# .devicebridge/in/<id>.cmd; this script runs them with this Mac's adb and writes the output to
# .devicebridge/out/<id>.log, then <id>.done with the exit code. Only adb runs, and only against the
# allowed phone serial(s), so nothing else on the Mac can be touched through here.
#
# Start:  bash tools/devicebridge/bridge.sh        Stop: Ctrl+C
# Allow other phones: LIFTLOG_SERIALS="SERIAL1 SERIAL2" bash tools/devicebridge/bridge.sh
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
B="$ROOT/.devicebridge"; mkdir -p "$B/in" "$B/out" "$B/files"
ADB="$(command -v adb || true)"; [ -n "$ADB" ] || ADB="$HOME/Library/Android/sdk/platform-tools/adb"
[ -x "$ADB" ] || { echo "adb not found. Install Android platform-tools or add adb to PATH."; exit 1; }
SERIALS="${LIFTLOG_SERIALS:-10MEBJFDZA0019X}"
cd "$ROOT"
echo "Lift Log bridge running in $ROOT"
echo "adb: $ADB · phones: $SERIALS · Ctrl+C to stop"
n=0
while true; do
  date +%s > "$B/alive"
  for f in "$B"/in/*.cmd; do
    [ -e "$f" ] || continue
    id="$(basename "$f" .cmd)"; out="$B/out/$id.log"; : > "$out"; rc=0
    echo "$(date '+%H:%M:%S') running $id"
    while IFS= read -r line || [ -n "$line" ]; do
      [ -z "$line" ] && continue
      case "$line" in "#sleep "*) sleep "${line#\#sleep }"; continue;; esac
      ok=0; for s in $SERIALS; do case "$line" in "-s $s "*) ok=1;; esac; done
      if [ $ok = 0 ]; then echo "REFUSED (must start with -s <allowed serial>): $line" >> "$out"; rc=2; continue; fi
      echo "\$ adb $line" >> "$out"
      # xargs splits the line like a shell would (quotes kept together) but runs adb directly, never a shell.
      printf '%s\n' "$line" | xargs "$ADB" >> "$out" 2>&1; c=$?
      echo "(exit $c)" >> "$out"; [ $c -ne 0 ] && rc=$c
    done < "$f"
    rm -f "$f"; echo "$rc" > "$B/out/$id.done"
  done
  n=$((n + 1)); if [ $((n % 10000)) -eq 0 ]; then find "$B/out" "$B/files" -type f -mtime +2 -delete 2>/dev/null; fi
  sleep 0.3
done
