# Changelog

All notable changes to Vantage. Versions follow [Semantic Versioning](https://semver.org); IDs such as F1 or S2 refer
to the findings in [AUDIT.md](AUDIT.md).

## Unreleased – launcher overhaul (November 2026)

A complete review and overhaul of the launcher after its first release. Settings, installations and accounts of
0.1.0 keep working and are migrated automatically. The in-game client is unchanged apart from the shared code it
uses for accounts.

### Reliability and user data
- Damaged `launcher.json`, `instances.json` or `accounts.json` files are kept as `*.broken-<date>.json` instead of
  being overwritten, and the user is told on start-up (F1).
- Saved values are validated and repaired (memory, resolution, thread count, colours, unknown options, unsafe or
  duplicate installation ids); `launcher.json` has a schema version with migrations (F5, A3).
- The launcher and the in-game account switcher no longer overwrite each other's changes to `accounts.json` (F7).
- Downloads use a 30 s read timeout, so a stalled connection fails instead of hanging forever (F2).
- Interrupted downloads continue where they stopped, also after a restart (P1). Unreachable hosts are not retried, so
  offline start-ups are about 4.5 s faster (P3).
- Launches, repairs, mod installs and update downloads can be cancelled; the task bar shows size, speed and remaining
  time for each running operation (U3, P4).
- Without internet, an installation played before starts with the saved session for singleplayer (G1).
- Minecraft versions before 1.7.10 are no longer offered (they never worked) (F8).

### Security
- Sign-in tokens are stored encrypted (AES-256-GCM); the key is protected by Windows DPAPI, the macOS Keychain or the
  Linux Secret Service. Existing plaintext files are encrypted automatically (S1). New dependency: JNA 5.17.0.
- Self-updates are only offered with a published SHA-256 checksum and verified after download. Only verified staged
  updates are started; unofficial update repositories are flagged (S2, S3).
- The Java runtime file list is verified with its SHA-1 (S4). All paths built from downloaded metadata are confined
  to their folders (S6).
- New CI workflow runs the build, tests, Checkstyle and an OSV vulnerability check of the dependencies on every push
  and pull request (S8, B2).

### Self-update
- If a new version does not open its window within 60 s or exits early, the previous version starts instead, tells
  the user and never offers that update again (F3).
- After a successful update the new jar replaces the old one, so later starts need no hand-over (P6). Options such
  as `-Dvantage.dataDir` are passed on.
- The version number is defined only in `gradle.properties` (F4). Old update files are cleaned up (F12).

### User interface
- Plain-language error messages with a hint instead of exception names; "Copy details" for bug reports (U1, F10, A5).
- Messages are always shown above dialogs (U2).
- The console shows readable, coloured lines instead of raw log4j XML, with search and a "problems only" filter, in a
  list that stays fast with long output (U4, P2).
- After a crash a dialog shows the start of the crash report or the last errors; "Stop game" is not a crash; "close
  after launch" still comes back after a crash (U8).
- Window size, position and the last page are remembered (U5). Closing while Minecraft runs or is being prepared
  asks first (F6).
- Keyboard: Ctrl+1…6 switch pages, Ctrl+Enter plays, Enter/Escape in dialogs, focus ring for Tab navigation (U7).
- Custom game folders are checked; shared folders and very high memory settings are pointed out (F9, G2).
- Real app icon in all sizes (B3); no JavaFX warning on start (B4).

### Distribution
- Portable zips with their own Java runtime for Windows, macOS (Apple Silicon) and Linux; no Java installation
  needed (B1). Built by the release workflow on each OS.

### Code quality
- Launch and update logic moved out of UI classes (A1); log rotation over five files with a start-up line naming
  version, OS, Java and data folder (A2); dead build code removed (A4); named constants (A6).
- Checkstyle and `-Xlint:all -Werror` for the launcher and the shared code (A7, A8).
- Unit tests grew from 12 to 67, all offline.
- Gson 2.13.2 → 2.15.0.

### Fixed during the overhaul
- The self-updater ignored pre-releases, so 0.1.0 could never find an update.
- A broken update jar made Windows Java show an error dialog that blocked the hand-over; the launcher now starts
  updates with `java.exe` and detects the failure immediately.

## 0.1.0 – 2026-11-10

First public pre-release: JavaFX launcher with Microsoft sign-in, installations, Mojang Java runtimes, Modrinth
browser and self-update; Fabric client for Minecraft 1.21.11 with main menu, HUD editor, PvP, camera, visual and
performance modules, keybind manager and in-game Modrinth browser.
