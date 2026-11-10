Vantage is a Minecraft PvP client with its own launcher.

**Pre-release:** signing in with a Microsoft account starts working once Microsoft approves the launcher's Azure app for the Minecraft API (review pending). Until then the launcher shows "not approved yet".

### Which file do I need?
| File | For |
|---|---|
| `vantage-launcher-*-portable-win.zip` | **Windows, recommended.** Unzip anywhere and start `Vantage\Vantage.exe`. No Java needed. |
| `vantage-launcher-*-portable-mac-aarch64.zip` | macOS on Apple Silicon. Unzip and open `Vantage.app` (the first time: right-click → Open, the app is not signed). |
| `vantage-launcher-*-portable-linux.zip` | Linux (x64). Unzip and start `Vantage/bin/Vantage`. |
| `vantage-launcher-*-all-<platform>.jar` | Single-file launcher for Windows (`win`), macOS (`mac`, `mac-aarch64`) or Linux (`linux`); needs Java 21 (`java -jar <file>`). |
| `vantage-client-*.jar` | Only the in-game client mod (Fabric 1.21.11, needs Fabric API) |

The launcher installs Minecraft, Fabric and the Vantage client by itself and updates itself from future releases; every update is checked against the SHA-256 checksums in `SHA256SUMS.txt`. Only the Windows builds have been tested so far.

### Highlights
- Launcher: Microsoft sign-in with encrypted token storage, installations (version, Fabric, RAM, Java), Mojang Java runtimes, resumable downloads with speed and remaining time, Modrinth browser with dependency-checked installs and updates, readable console and crash reports, verified self-update
- In game: Vantage main menu, drag-and-drop HUD editor with 20 elements, PvP/camera/visual/performance modules, keybind manager with conflict detection, in-game Modrinth browser

See README.md, CHANGELOG.md, TESTPLAN.md and TODO.md for details and known gaps.
