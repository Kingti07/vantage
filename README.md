# Vantage – Minecraft PvP Client

Vantage is a standalone Minecraft PvP client: its own **JavaFX launcher**, a **Fabric** in-game client with a custom
main menu, HUD editor, PvP / camera / visual / performance modules, a keybind manager, and an integrated **Modrinth**
mod browser in both the launcher and the game.

* Minecraft **1.21.11**, Fabric Loader 0.20.1, Fabric API 0.141.6
* Java 21, Gradle 9.8 (wrapper included), Fabric Loom 1.17
* Official Mojang mappings (the same names Minecraft itself uses from 26.1 on, which keeps porting cheap)

> Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft. Vantage never
> redistributes Minecraft: the launcher downloads the game from Mojang's servers onto the player's machine, the same
> way the official launcher does. Mods are downloaded from Modrinth under their own licenses.

See [FEATURES.md](FEATURES.md) for the feature tracker and [TODO.md](TODO.md) for known gaps.

---

## Project layout

```
common/     Pure Java library shared by launcher and client (no Minecraft classes)
  auth/       Microsoft → Xbox Live → XSTS → Minecraft sign-in (device code flow), account storage
  modrinth/   Modrinth v2 API client + models + search builder
  mods/       Mod manager: scan, analyse, dependency-resolving installs, updates, enable/disable, removal
  version/    SemVer + Fabric version predicates (compatibility checks)
  util/       HTTP (retries, verified downloads), JSON, hashing, WebP-capable image decoding

launcher/   JavaFX desktop launcher
  game/       Version manifest, Fabric meta, library/asset/runtime installer, launch-argument builder, LaunchService
  settings/   launcher.json + instances.json (auto-saved)
  update/     Self-update via GitHub Releases
  ui/         Main window, pages (Home, Installations, Mods, Accounts, Settings, Console), Modrinth browser, theme CSS

client/     Fabric mod that runs inside Minecraft
  module/     Module system; hud/, pvp/, camera/, visual/, performance/ feature modules + Interface (cosmetics)
  hud/        HUD framework (anchors, scale, shared style settings) + renderer
  keybind/    Keybind manager (keyboard + mouse, multiple keys per action, conflict detection)
  config/     JSON profiles, HUD layouts, auto-save
  combat/     Client-side combat statistics (CPS, reach, combo, hits, kills, W-taps, target)
  ui/         Widget toolkit and screens (main menu, modules, settings, cosmetics, keybinds, accounts,
              HUD editor, Modrinth browser, project page, installed mods, performance pack)
  modrinth/   In-game mod service and remote image textures
  mixin/      Minecraft hooks (one mixin per target class, each documented)
```

The launcher and the client never share Minecraft code; they share only `common`.

## Building

Requirements: JDK 21 (any vendor). Everything else is downloaded by Gradle.

```bash
./gradlew build
```

Outputs:

| File | What it is |
|---|---|
| `launcher/build/libs/vantage-launcher-<version>-all-<platform>.jar` | Runnable launcher (contains the client mod) |
| `client/build/libs/vantage-client-<version>+mc1.21.11.jar` | The in-game client mod on its own |

JavaFX ships per-platform native code. The fat jar is built for the current OS; cross-build with
`-PjavafxPlatform=win|mac|mac-aarch64|linux|linux-aarch64`.

Run the launcher:

```bash
java -jar launcher/build/libs/vantage-launcher-0.1.0-all-win.jar
```

or during development:

```bash
./gradlew :launcher:run
```

Run the client directly in a development environment (offline dev account, singleplayer only):

```bash
./gradlew :client:runClient
```

### Automated checks

| Command | What it verifies |
|---|---|
| `./gradlew test` | Unit tests: version predicates, mod analysis (missing deps, wrong loader, duplicates, `breaks`), enable/disable/remove, launch arguments, version JSON merging |
| `./gradlew :client:runSmokeTest` | Starts Minecraft with Vantage, audits every mixin, opens every Vantage screen, creates a test world with several modules enabled, saves screenshots to `client/run-smoke/screenshots/` and exits |
| `./gradlew :launcher:launchSmoke -PsmokeDir=<empty dir>` | Full launcher pipeline into a throwaway folder: Mojang version + assets + libraries, Java runtime, Fabric, bundled client + Fabric API, launch – then runs the in-game smoke test (placeholder account: singleplayer only) |
| `java -Dvantage.dataDir=<tmp> -Dvantage.uiSnapshot=<dir> -jar <launcher jar>` | Opens every launcher page, saves PNG snapshots and exits |

## Microsoft sign-in setup (required once per distribution)

Microsoft only allows **approved Azure applications** to sign players in to Minecraft. Vantage implements the
official OAuth device-code flow, but you must supply your own application id:

1. In the [Azure portal](https://portal.azure.com) → *App registrations* → *New registration*.
   Supported account types: **Personal Microsoft accounts only**.
2. *Authentication* → enable **Allow public client flows**.
3. Request Minecraft API access for the application id at <https://aka.ms/mce-reviewappid>.
4. Paste the *Application (client) ID* in the launcher under **Settings → Microsoft sign-in**, or bake it into a
   distribution with `-Dvantage.msaClientId=<id>`.

Until the application is approved, Minecraft services answer sign-in with HTTP 403; the launcher reports this
clearly. Tokens are stored in `accounts.json` in the launcher data folder (owner-only permissions on
macOS/Linux; inside the user profile on Windows). Passwords never pass through Vantage.

## Data locations

| | Path |
|---|---|
| Launcher data (versions, libraries, assets, runtimes, instances, settings, logs) | Windows `%APPDATA%\.vantage`, macOS `~/Library/Application Support/vantage`, Linux `$XDG_DATA_HOME/vantage` (override with `-Dvantage.dataDir=` or `VANTAGE_DATA_DIR`) |
| Game directory of an installation | `<data>/instances/<id>` (or a custom folder chosen in the installation editor) |
| Client configuration | `<game dir>/config/vantage/` – `profiles/*.json`, `hud-layouts/*.json`, `client.json`, `backgrounds/` |

Nothing needs to be edited by hand; every setting has a UI and saves automatically.

## Releasing & self-update

1. Bump `project_version` in `gradle.properties` and `VantageInfo.VERSION`.
2. `./gradlew build` once per platform (`-PjavafxPlatform=...`).
3. Publish a GitHub release tagged `v<version>` with the `vantage-launcher-<version>-all-<platform>.jar` assets.
4. Launchers with **Settings → Updates → Release channel** set to `owner/repo` download the new jar into
   `<data>/updates/` and switch to it on the next start. The client mod is inside the launcher jar, so one release
   updates both.

## In-game controls (defaults, all rebindable in **Keybinds**)

| Action | Key |
|---|---|
| HUD editor | Right Shift |
| Vantage module menu | Right Ctrl |
| Zoom (hold, scroll to adjust) | C |
| Freelook (hold) | Left Alt |
| Perspective (hold) | V |
| Toggle sprint / toggle sneak | Left Ctrl / Left Shift (when Toggle Sprint is enabled) |

## Fair play

Vantage contains no cheats: no reach/velocity/aim changes, no automation, no packet manipulation, no anti-cheat
bypasses. All modules are visual, informational or quality-of-life and work only with information the vanilla
client already has. Some servers restrict certain client-side features (for example freelook); respect their rules.

## Licenses

Vantage source: MIT. Icons in the launcher are Google Material Icons (Apache 2.0). The bundled WebP decoder is
TwelveMonkeys ImageIO (BSD-3-Clause). Minecraft is © Mojang AB; it is never bundled.
