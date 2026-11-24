#!/bin/bash
# Claude's side of the bridge: sends adb lines to the Mac and prints what they returned.
# Usage: adbx.sh [-t seconds] '-s SERIAL shell getprop ro.product.model' ['#sleep 2'] ...
D="$(cd "$(dirname "$0")/../.." && pwd)/.devicebridge"; T=120
[ "${1:-}" = "-t" ] && { T=$2; shift 2; }
mkdir -p "$D/in" "$D/out"
alive=$(cat "$D/alive" 2>/dev/null || echo 0)
if [ $(( $(date +%s) - alive )) -gt 15 ]; then
  echo "BRIDGE NOT RUNNING: on the Mac, run: bash tools/devicebridge/bridge.sh (in the lift-log folder)"; exit 3
fi
id="$(date +%s)-$$-$RANDOM"
printf '%s\n' "$@" > "$D/in/.$id.tmp" && mv "$D/in/.$id.tmp" "$D/in/$id.cmd"
i=0; while [ ! -e "$D/out/$id.done" ] && [ $i -lt $((T * 2)) ]; do sleep 0.5; i=$((i + 1)); done
cat "$D/out/$id.log" 2>/dev/null
[ -e "$D/out/$id.done" ] || { echo "TIMED OUT after ${T}s (is the bridge still running?)"; exit 4; }
exit "$(cat "$D/out/$id.done")"
