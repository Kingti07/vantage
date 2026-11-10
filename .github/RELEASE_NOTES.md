First public build of Vantage, a Minecraft PvP client with its own launcher.

**Pre-release:** signing in with a Microsoft account starts working once Microsoft approves the launcher's Azure app for the Minecraft API (review pending). Until then the launcher shows "not approved yet".

### Which file do I need?
| File | For |
|---|---|
| `vantage-launcher-*-all-win.jar` | Windows |
| `vantage-launcher-*-all-mac-aarch64.jar` | macOS on Apple Silicon (M1 and newer) |
| `vantage-launcher-*-all-mac.jar` | macOS on Intel |
| `vantage-launcher-*-all-linux.jar` | Linux (x64) |
| `vantage-client-*.jar` | Only the in-game client mod (Fabric 1.21.11, needs Fabric API) |

Start the launcher with Java 21 (double-click or `java -jar <file>`). It installs Minecraft 1.21.11, Fabric and the Vantage client by itself and updates itself from future releases. Only the Windows launcher has been tested so far.

### Highlights
- Launcher: Microsoft sign-in, installations (version, Fabric, RAM, Java), Mojang Java runtimes, Modrinth browser with dependency-checked installs and updates, performance pack, console, self-update
- In game: Vantage main menu, drag-and-drop HUD editor with 20 elements, PvP/camera/visual/performance modules, keybind manager with conflict detection, in-game Modrinth browser

See README.md, FEATURES.md and TODO.md for details and known gaps. Checksums are in `SHA256SUMS.txt`.
