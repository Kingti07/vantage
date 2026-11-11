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

More documents: [FEATURES.md](FEATURES.md) (feature tracker), [TODO.md](TODO.md) (known gaps),
[CHANGELOG.md](CHANGELOG.md), [TESTPLAN.md](TESTPLAN.md) (manual test checklist) and [AUDIT.md](AUDIT.md)
(launcher audit of November 2026 and what was done about each finding).

---

## The launcher

* **Accounts:** official Microsoft sign-in (device code), several accounts, automatic session refresh. Tokens are
  stored encrypted; the key is protected by Windows DPAPI, the macOS Keychain or the Linux Secret Service.
* **Installations:** Minecraft version (1.7.10 and newer, snapshots optional), Fabric loader, own game folder, memory,
  custom Java, JVM arguments, resolution, "Repair files".
* **Play:** installs the game, libraries, assets and the matching Mojang Java runtime with hash verification,
  installs the Vantage client and Fabric API, then starts the game (optionally straight onto a server). Downloads run
  in parallel, continue after interruptions, never hang on a stalled connection, and show size, speed, remaining
  time and a Cancel button. Without internet, installations played before still start.
* **Mods:** Modrinth search, details, one-click installs with dependency and compatibility checks, updates,
  enable/disable/remove, own jars, performance pack.
* **Console and crashes:** readable, coloured game output with search and a "problems only" filter; after a crash the
  start of the crash report is shown with Copy / Open file.
* **Self-update:** new versions from GitHub Releases are verified with their SHA-256 checksum; if a new version does
  not start, the old one keeps running and the update is not offered again.
* **Comfort:** remembers window size, position and the last page; asks before closing while Minecraft runs;
  keyboard shortcuts (Ctrl+1…6 pages, Ctrl+Enter play, Enter/Escape in dialogs); plain-language error messages with
  a hint and "Copy details" for bug reports.

## Performance

Vantage combines three things for a high, steady frame rate:

* **Performance mods by default.** Sodium, Lithium, ImmediatelyFast, FerriteCore and EntityCulling are installed from
  Modrinth for new installations (never bundled; each under its own license). Installations from 0.1.0 are asked once.
  Turn them off under Installations > Edit > Performance mods.
* **Experimental Vulkan renderer** (VulkanMod) per installation, for the highest frame rates. It disables the mods
  that need OpenGL while it is on; Motion Blur and some mods do not work with it.
* **Launcher tuning** (Settings > Performance): whole heap up front with tuned G1, optional low-latency ZGC, "above
  normal" game priority on Windows, optional dedicated-GPU preference for laptops. In game, the FPS Boost preset sets
  the video options for maximum frame rate.

Measured with `launchBenchmark` on an RTX 4060 Ti at 1280x720 (average FPS, 1% low in brackets):

| Render distance | Without performance mods | Performance mods (default) | VulkanMod (experimental) |
|---|---|---|---|
| 12 | 842 (377) | 905 (444) | 2108 (647) |
| 32 | 414 (206) | 795 (344) | 1290 (471) |

With the 0.2.0 client's cheaper HUD text the performance-mod result at render distance 12 rises to 1008 (568), and to
1066 (600) with the in-game FPS Boost preset. Single 30-second runs; repeated runs vary by a few percent, and runs
while other programs use the GPU are much lower.

On NVIDIA cards Sodium switches off the driver's "threaded optimization" to avoid crashes, so at very high frame rates
and short render distances its gain is small; it grows with the render distance and on slower GPUs.

## Download

Ready-to-run builds are on the [Releases page](https://github.com/Kingti07/vantage/releases):

| File | For |
|---|---|
| `vantage-launcher-<version>-portable-win.zip` | **Windows, recommended.** Unzip anywhere, start `Vantage\Vantage.exe`. Contains its own Java. |
| `vantage-launcher-<version>-portable-mac-aarch64.zip` / `-linux.zip` | macOS (Apple Silicon) / Linux with their own Java |
| `vantage-launcher-<version>-all-<platform>.jar` | Single-file launcher; needs Java 21 (`java -jar <file>`) |
| `vantage-client-<version>+mc1.21.11.jar` | Only the in-game client mod |
| `SHA256SUMS.txt` | Checksums of all files |

Nothing is installed system-wide. To uninstall, delete the launcher folder or jar and, if you want, the data folder
(see below; back up your worlds from `instances/<id>/saves` first).

## Building

Requirements: JDK 21 (any vendor). Everything else is downloaded by Gradle.

```bash
./gradlew build
```

`build` compiles (every javac lint warning is an error in `common` and `launcher`), runs the unit tests and
Checkstyle (`config/checkstyle/checkstyle.xml`).

| Command / output | What it is |
|---|---|
| `launcher/build/libs/vantage-launcher-<version>-all-<platform>.jar` | Runnable launcher (contains the client mod). Cross-build with `-PjavafxPlatform=win\|mac\|mac-aarch64\|linux\|linux-aarch64`. |
| `./gradlew :launcher:portableZip` → `launcher/build/distributions/vantage-launcher-<version>-portable-<platform>.zip` | Launcher with its own Java runtime (jpackage). Must be built on the target OS. |
| `client/build/libs/vantage-client-<version>+mc1.21.11.jar` | The in-game client mod on its own |

Run the launcher with `java -jar launcher/build/libs/vantage-launcher-<version>-all-win.jar` or
`./gradlew :launcher:run`; run the client in a development environment with `./gradlew :client:runClient`.

### Automated checks

| Command | What it verifies |
|---|---|
| `./gradlew test` | 76 unit tests, all offline (local HTTP server and temporary folders): settings loading, repair and migration; account store, encryption and concurrent writes; downloads (resume, hash mismatch, stalled connection, cancel, offline); download queue; installation management; launch arguments; version JSON merging; update selection, checksums, staging and rejection; console log parsing; error messages; mod analysis; version predicates |
| `./gradlew :launcher:launchSmoke -PsmokeDir=<empty dir>` | Full pipeline into a throwaway folder: Mojang version, assets, libraries, Java runtime, Fabric, bundled client and Fabric API, launch, then the in-game smoke test (placeholder account, singleplayer only) |
| `./gradlew :launcher:launchSmoke -PsmokeDir=<dir> -PsmokeMods=sodium,lithium` | The same with other Modrinth mods installed first: compatibility check including the mixin audit |
| `./gradlew :launcher:launchBenchmark -PbenchDir=<dir> -PbenchProfile=<label> -PbenchRd=12 -PbenchMods=sodium,... -PbenchPreset=true` | FPS benchmark in a fixed test world (average, median, 1% and 0.1% low) with the given render distance, mods and optionally the FPS Boost preset; writes `benchmark-<label>.json` |
| `./gradlew :client:runSmokeTest` | Starts Minecraft with Vantage, audits every mixin, opens every Vantage screen, creates a test world and exits |
| `java -Dvantage.dataDir=<tmp> -Dvantage.uiSnapshot=<dir> -jar <launcher jar>` | Opens every launcher page and saves PNG snapshots; add `-Dvantage.uiSnapshot.console=<game output file>` to fill the console |

GitHub Actions runs `build` plus an [OSV](https://osv.dev) check of the launcher's dependencies on every push and pull
request (`.github/workflows/ci.yml`). A pushed `v*` tag builds and publishes a release (`release.yml`).

## Project layout

```
common/     Pure Java library shared by launcher and client (no Minecraft classes)
  auth/       Microsoft → Xbox Live → XSTS → Minecraft sign-in, account store, token encryption (TokenCipher, KeyStorage)
  modrinth/   Modrinth v2 API client + models + search builder
  mods/       Mod manager: scan, analyse, dependency-resolving installs, updates, enable/disable, removal
  version/    SemVer + Fabric version predicates (compatibility checks)
  util/       HTTP (retries, resumable verified downloads), JSON, hashing, safe paths, WebP image decoding

launcher/   JavaFX desktop launcher
  Main          start-up, logging, verified hand-over to a downloaded update
  game/         version metadata, download queue, installer, Java runtimes, launch arguments, LaunchService,
                console log parser, crash reports
  settings/     launcher.json + instances.json: validated, migrated, auto-saved
  update/       self-update via GitHub Releases (checksums, staging, rejection, clean-up)
  ui/           window, pages (Home, Installations, Mods, Accounts, Settings, Console), dialogs, task bar, error texts
  packaging/    app icons for the portable packages

client/     Fabric mod that runs inside Minecraft (main menu, HUD, modules, keybinds, in-game Modrinth browser)
config/checkstyle/   Checkstyle rules for common and launcher
```

The launcher and the client never share Minecraft code; they share only `common`.

## Microsoft sign-in

Microsoft only allows **approved Azure applications** to sign players in to Minecraft. Vantage uses the official
OAuth device-code flow with the id of its own Azure app built in (`VantageInfo.DEFAULT_MSA_CLIENT_ID`); Microsoft's
approval of that app for the Minecraft API is pending. Until it is approved, sign-in fails with a clear message.
Distributions can use their own approved app id under **Settings → Microsoft sign-in** or with
`-Dvantage.msaClientId=<id>` (register at the [Azure portal](https://portal.azure.com) with "Personal Microsoft
accounts only" and "Allow public client flows", then request access at <https://aka.ms/mce-reviewappid>).

Passwords never pass through Vantage. The refresh and access tokens are stored encrypted (AES-256-GCM) in
`accounts.json`; the key lives in `token-key.dpapi` (Windows DPAPI), the macOS Keychain or the Linux Secret Service, so
only the same user on the same computer can read them. Older plaintext files are encrypted automatically.

## Data locations

| | Path |
|---|---|
| Launcher data | Windows `%APPDATA%\.vantage`, macOS `~/Library/Application Support/vantage`, Linux `$XDG_DATA_HOME/vantage` or `~/.local/share/vantage` (override with `-Dvantage.dataDir=` or `VANTAGE_DATA_DIR`) |
| Settings / installations / accounts | `<data>/launcher.json`, `instances.json`, `accounts.json` (+ `token-key.dpapi` on Windows). A damaged file is kept as `*.broken-<date>.json` and you are told on start-up. |
| Game files shared by all installations | `<data>/versions`, `libraries`, `assets`, `runtimes` |
| Game directory of an installation | `<data>/instances/<id>` or a custom folder; contains worlds, mods, the game's own `logs/` and `crash-reports/` |
| Launcher logs | `<data>/logs/launcher.log` plus the four previous runs as `launcher.1.log` … `launcher.4.log` |
| Updates | `<data>/updates/` (verified jar, `staged.json`, `rejected.json`) |
| Client configuration | `<game dir>/config/vantage/` – `profiles/*.json`, `hud-layouts/*.json`, `client.json`, `backgrounds/` |

Nothing needs to be edited by hand; every setting has a UI and saves automatically.

## Releasing & self-update

1. Set the new `project_version` in `gradle.properties` (the only place that holds the version) and add a section to
   `CHANGELOG.md`.
2. Commit, then push a tag `v<version>`. The release workflow builds and tests everything, creates the jars for every
   platform, the portable zips on Windows, macOS and Linux, and `SHA256SUMS.txt`, and publishes the release.
3. Launchers check the release channel (default `Kingti07/vantage`, configurable under **Settings → Updates**). A
   release is only offered if it has a SHA-256 checksum for the platform's jar. It is downloaded into
   `<data>/updates/`, verified and started on the next launch. Once its window is up, it replaces the old jar. If it
   fails to start, the previous version runs instead and the update is never offered again. The client mod is inside
   the launcher jar, so one release updates both.

## Known limitations

* Microsoft sign-in works only after Microsoft approves the Azure app (pending).
* Tested on Windows 11 only; macOS and Linux builds are untested. The macOS app and the Windows exe are not code
  signed, so macOS Gatekeeper ("right-click → Open") and Windows SmartScreen may warn on first start.
* The launcher UI is English only.
* Minecraft versions before 1.7.10 are not supported; the Vantage client mod itself runs on 1.21.11 only.
* The Vulkan renderer is experimental: Minecraft's screenshot function does not capture its image, Motion Blur is
  off, and mods that hook OpenGL do not work with it.
* Like the official launcher, the game's access token is passed on the game's command line (visible to programs of
  the same user); it is masked in all logs.

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
The Hitboxes module, like vanilla F3+B, shows nothing on servers that turn on "reduced debug info". Capes, hats and
pets are drawn only on your own screen.

## Licenses

Vantage source: MIT. Icons in the launcher are Google Material Icons (Apache 2.0). Bundled libraries: TwelveMonkeys
ImageIO (BSD-3-Clause, WebP decoding), JNA (Apache 2.0 / LGPL 2.1, Windows DPAPI), Gson (Apache 2.0), SLF4J (MIT),
OpenJFX (GPL 2.0 with Classpath Exception). Minecraft is © Mojang AB; it is never bundled.
