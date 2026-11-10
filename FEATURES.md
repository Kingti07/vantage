# Feature tracker

Status legend: **Done** = implemented and exercised by the automated checks or unit tests ·
**Done (untested live)** = implemented and compiles, but not yet exercised against real servers/accounts ·
**Partial** = works, with the stated limitation · **Via mod** = provided by installing an independent mod from
Modrinth through Vantage's dependency-checked installer · **Not done**.

## Launcher overhaul (2026-11-10)

The launcher was audited and overhauled; see [AUDIT.md](AUDIT.md) and [CHANGELOG.md](CHANGELOG.md). Verified on
Windows 11 with Java 21: 67 unit tests, `launchSmoke` on fresh folders (including a path with spaces, umlaut and
parentheses) and offline, the update hand-over to a good and to a corrupt jar, the portable Windows package, and UI
snapshots of every page online and offline.

## What the automated runs verified (2026-11-08, Windows 11, Java 21)

* `./gradlew test` – 10 unit tests pass (version predicates, mod analysis, enable/disable/remove, launch arguments,
  version JSON merging).
* `./gradlew :launcher:launchSmoke` – the real launcher pipeline downloaded Minecraft 1.21.11 (84 libraries,
  4,591 assets), Mojang's Java 21 runtime and Fabric Loader, installed the bundled client and Fabric API from
  Modrinth, launched the production (remapped) jar, and the in-game smoke test passed with 0 failures.
* `./gradlew :client:runSmokeTest` (also run inside the launch above) – all 20 mixins applied (mixin audit), every
  Vantage screen opened, Modrinth search with WebP icons, a test world created with Fullbright, Custom Time, Item
  Physics, Custom Crosshair, Block Overlay, Performance Monitor and Motion Blur enabled; HUD rendered in-world; no
  shader or runtime errors.
* `-Dvantage.uiSnapshot` – every launcher page and the installation editor rendered and were reviewed.
* Live Modrinth: the install plan for Mod Menu resolved Fabric API + Text Placeholder API, downloaded with hash
  verification, analysis reported no problems, update check ran.

Not covered automatically: real Microsoft sign-in (needs an approved Azure app id), joining online servers, and
features that need keyboard/mouse input or combat (zoom, freelook, perspective, toggle sprint, CPS/reach/combo,
damage indicators, hit particles). Those are marked "Done (untested live)".

## 1. General

| Requirement | Status | Notes |
|---|---|---|
| Standalone client with own launcher | Done | `launcher` (JavaFX) + `client` (Fabric mod bundled inside the launcher jar) |
| Java + Fabric | Done | Fabric Loader 0.20.1, Fabric API 0.141.6, Loom 1.17 |
| Multiple Minecraft versions, starting with 1.21.11 | Partial | Launcher installs and launches any release/snapshot with Fabric; the Vantage client mod targets 1.21.11 only (other versions run plain Fabric + your mods) |
| Modular architecture | Done | `common` / `launcher` / `client`; modules, HUD, keybinds, config, UI, mixins separated |
| Multiplayer + singleplayer | Done | Vanilla world and server screens are used unchanged |
| Everything configurable, no manual file editing | Done | Every setting has UI and auto-saves |
| Official authentication, licensing respected | Done | Microsoft device-code OAuth; Minecraft downloaded from Mojang on the user's machine, never bundled |

## 2. Launcher

| Requirement | Status | Notes |
|---|---|---|
| Modern, customizable UI | Done | Dark theme, accent colours, animations toggle |
| Microsoft authentication | Done (untested live) | Needs the Azure app approved for the Minecraft API (pending). Tokens stored encrypted (DPAPI / Keychain / Secret Service) |
| Account management / switching | Done (untested live) | Add, remove, select; in-game switch restarts through the launcher |
| Minecraft version selection | Done | Mojang manifest, releases (+ snapshots toggle) |
| Fabric version selection | Done | Fabric meta, "latest stable" or a pinned version |
| RAM allocation | Done | Slider bounded by system memory |
| Java runtime management | Done | Mojang runtimes per version (download, verify, delete) or custom path with version test |
| Launch button, status, progress bar | Done | Task bar per operation with size, speed, remaining time and Cancel; readable console with filter; crash dialog |
| Game directory management | Done | Per installation, custom folder, open folder |
| Installed mod overview | Done | Icons, versions, source, problems, enable/disable, remove |
| Download & update management | Done | Resumable, stall-proof downloads; mod update detection + "Update all"; "Repair files" re-verifies every game file |
| Client settings | Done | Settings page (appearance, launching, sign-in, Java, updates, storage) |
| Automatic self-update | Done (hand-over tested locally) | SHA-256-verified GitHub releases; staged jar takes over on next start and replaces the old jar; falls back if it does not start. Not yet run against a real newer GitHub release |
| Quick join a server | Done | Uses Minecraft's Quick Play |

## 3. Main client interface

| Requirement | Status | Notes |
|---|---|---|
| Singleplayer / Multiplayer / Mods / Settings / Cosmetics / Accounts / Exit | Done | Smoke test opens every screen |
| Animations, typography, backgrounds, transitions | Done | Staggered slide-in, hover animations, gradient / panorama / custom image backgrounds |

## 4. PvP features

### Combat HUD
| Feature | Status | Notes |
|---|---|---|
| CPS (left/right) | Done | |
| FPS | Done | |
| Ping | Done | From the player list latency |
| Coordinates | Done | Decimals, facing, nether/overworld conversion, vertical/horizontal |
| Keystrokes | Done | WASD, LMB/RMB with CPS, space, animated |
| Armor status | Done | Number/percent, vertical/horizontal, held item |
| Potion effects | Done | Icons, levels, durations, blink when ending |
| Durability | Done | Main/offhand |
| Direction | Done | Scrolling compass + degrees |
| Biome | Done | |
| Speed | Done | Blocks/second |
| Reach | Done (untested live) | Eye-to-hitbox distance of the last landed hit |
| Combo counter | Done (untested live) | |
| Hit counter | Done | Landed hits per session |
| Kill counter | Done (untested live) | Deaths of entities you hit within 5 s |
| W-tap counter | Done (untested live) | Landed hits preceded by a sprint reset |
| Sprint status | Done | Shows toggled/vanilla sprint & sneak |
| Target HUD | Done (untested live) | Face, health bar, distance, armour, blocking |
| Crosshair customization | Done | 6 styles, size, gap, thickness, outline, target colour, invert blending, cooldown bar |
| Custom hit color | Done | Repaints the entity hurt overlay |
| Damage indicators | Done (untested live) | Floating numbers (optional healing), through walls |

### Movement & visual
| Feature | Status | Notes |
|---|---|---|
| Motion blur | Done | Post-processing chain with a persistent frame buffer, 9 strengths |
| Freelook | Done (untested live) | Hold/toggle, invert |
| Zoom | Done (untested live) | Hold/toggle, scroll, smooth, sensitivity reduction, cinematic |
| Toggle sprint / toggle sneak | Done (untested live) | |
| Perspective mod | Done (untested live) | Hold to peek front/back |
| Custom FOV | Done | 30–140° |
| Dynamic FOV toggle | Done | On/off + strength |
| Fullbright | Done | Toggle key |
| Low fire | Done | |
| Custom animations | Partial | Item offset/scale, swing duration, no re-equip dip. No 1.7-style block-hit animations |
| Custom particles / particle customization | Done | Hit particle multiplier, always-crit/sharpness, per-type disabling, particle budget |
| Custom sky | Partial | Sky, fog and cloud colours; no custom sky textures |
| Custom weather | Done | Clear/rain/thunder (client only) |
| Custom time | Done | Fixed or real-time clock (client only) |
| Item physics | Partial | Items lie flat / rest without bobbing; no rolling or physics simulation |
| Block overlay | Done | Outline colour/width + translucent fill |
| Clear water | Done | Fog distance + overlay removal |
| No hurt cam | Done | 0–100% |

### Performance
| Feature | Status | Notes |
|---|---|---|
| FPS optimization | Partial | FPS-boost preset for vanilla options + performance pack |
| Entity culling | Partial | Distance/type culling built in; occlusion culling via EntityCulling (Via mod) |
| Dynamic FPS | Done | Unfocused, minimised and menu caps |
| Sodium / Lithium / ImmediatelyFast integration | Via mod | One-click, compatibility-checked install. Coexistence of Vantage mixins with these mods not yet tested |
| Memory optimization | Via mod | FerriteCore in the pack; memory shown in the performance monitor |
| Better chunk loading | Via mod | C2ME in the pack (optional) |
| Configurable particle rendering | Done | Particle Control module |
| Performance monitoring | Done | FPS, frame-time graph, memory, entities, particles |

## 5. HUD editor
| Requirement | Status |
|---|---|
| Drag & drop, resize (corner handle / scroll wheel), positions, snapping guides, arrow-key nudging | Done |
| Colours, fonts (Minecraft / Unifont), size (scale), transparency, padding, alignment, borders, corner radius | Done |
| Enable/disable elements, real-time preview | Done |
| Multiple saved HUD layouts, reset element, reset all | Done |

## 6. Keybind manager
| Requirement | Status | Notes |
|---|---|---|
| Custom keybind for every feature | Done | Every switchable module has a toggle bind (unbound by default) plus action binds |
| Keyboard + mouse, multiple keys per action, removal | Done | |
| Conflict detection | Done | Against other Vantage binds and all Minecraft/mod key mappings |
| Searchable list, auto-save | Done | |

## 7. Modrinth integration (launcher and in-game)
| Requirement | Status | Notes |
|---|---|---|
| Search, categories, descriptions, screenshots, authors, downloads | Done | WebP decoded via TwelveMonkeys |
| Ratings | Not available | Modrinth has no ratings; follower counts are shown instead |
| Filter by version and Fabric/Forge/NeoForge/Quilt | Done | Only Fabric versions for the instance's Minecraft version can be installed |
| Sort by relevance/downloads/popularity/newest/updated | Done | |
| One-click install, automatic dependencies, compatibility checks | Done | Live-tested: Mod Menu pulled in Fabric API + Text Placeholder API |
| Updates, removal, enable/disable, installed versions | Done | Changes to loaded jars are queued and applied before the next launch |
| Incompatible mods / dependency conflicts / installation errors | Done | fabric.mod.json analysis (game version, missing/incorrect deps, `breaks`, duplicates, wrong loader) + Modrinth incompatibility declarations; failed installs roll back |

## 8–11. Multiplayer, settings, cosmetics, UI
| Requirement | Status |
|---|---|
| Worlds: create/load/delete; servers: add/edit/remove/icons/ping/join | Done (vanilla screens) |
| Settings hub (general, video, performance, PvP, HUD, keybinds, mods, audio, launcher, account) | Done |
| Themes, menu backgrounds, UI colours, GUI scale, fonts, HUD style presets, profiles | Done (fonts limited to Minecraft + Unifont) |
| Consistent dark design across launcher and game | Done |
