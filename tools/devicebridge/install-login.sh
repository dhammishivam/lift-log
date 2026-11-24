#!/bin/bash
# Optional: start the bridge automatically when you log in (and restart it if it stops).
# Undo: launchctl unload ~/Library/LaunchAgents/com.krishiv.liftlog.devicebridge.plist && rm that file
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"; P="$HOME/Library/LaunchAgents/com.krishiv.liftlog.devicebridge.plist"
mkdir -p "$HOME/Library/LaunchAgents"
cat > "$P" <<PL
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>com.krishiv.liftlog.devicebridge</string>
  <key>ProgramArguments</key><array><string>/bin/bash</string><string>$HERE/bridge.sh</string></array>
  <key>EnvironmentVariables</key><dict><key>PATH</key><string>$HOME/Library/Android/sdk/platform-tools:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin</string></dict>
  <key>RunAtLoad</key><true/><key>KeepAlive</key><true/>
  <key>StandardOutPath</key><string>/tmp/liftlog-devicebridge.log</string>
  <key>StandardErrorPath</key><string>/tmp/liftlog-devicebridge.log</string>
</dict></plist>
PL
launchctl unload "$P" 2>/dev/null || true
launchctl load "$P"
echo "Bridge will now run in the background at every login. Log: /tmp/liftlog-devicebridge.log"
