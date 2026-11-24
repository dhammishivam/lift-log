# Device bridge

Lets Claude run `adb` against the test phone from its sandbox, which can't reach USB but can see this folder.

- **Start:** `bash tools/devicebridge/bridge.sh` from the repo root (Ctrl+C to stop), or once:
  `bash tools/devicebridge/install-login.sh` to run it in the background at every login.
- **How it works:** Claude writes adb command lines to `.devicebridge/in/`; `bridge.sh` runs them with this
  Mac's adb and writes the output to `.devicebridge/out/`. `.devicebridge/` is git-ignored.
- **Limits:** only `adb`, and only lines that target an allowed serial (`-s 10MEBJFDZA0019X` by default;
  set `LIFTLOG_SERIALS` for others). It never runs a shell on the Mac.
