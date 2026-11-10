# Vantage – Minecraft PvP client and launcher

My own Minecraft PvP client: a JavaFX launcher plus a Fabric mod that runs in the game.
Minecraft 1.21.11, Fabric Loader 0.20.1, Java 21, Gradle wrapper, Official Mojang mappings.
Details: `README.md` (setup, data paths, releasing), `FEATURES.md` (what is done), `TODO.md` (what is open).

## Layout
- `common/`   pure Java, shared by both sides: `auth/` (Microsoft sign-in), `modrinth/`, `mods/`, `version/`, `util/`.
  No Minecraft classes and no JavaFX in here.
- `launcher/` JavaFX desktop app: `game/` (install + launch), `settings/`, `update/`, `ui/` (pages, theme CSS).
- `client/`   Fabric mod: `module/` (hud, pvp, camera, visual, performance), `hud/`, `keybind/`, `config/`,
  `combat/`, `ui/` (screens + widgets), `modrinth/`, `mixin/`.
- Launcher and client share only `common`. The launcher jar bundles the finished client jar.
- Versions of Minecraft, Fabric and all libraries: `gradle.properties`.

## Commands
- Build everything: `./gradlew build`
- Unit tests: `./gradlew test`
- Run the launcher: `./gradlew :launcher:run`
- Run the game with the mod (offline dev account): `./gradlew :client:runClient`
- In-game smoke test (audits every mixin, opens every screen, saves screenshots): `./gradlew :client:runSmokeTest`
- Full install + launch check: `./gradlew :launcher:launchSmoke -PsmokeDir=<empty dir>`

## Rules
- No cheats, ever: no reach, velocity or aim changes, no automation, no packet tricks, no anti-cheat bypass.
  Modules are visual, informational or quality-of-life only.
- Never bundle or redistribute Minecraft. The launcher downloads it from Mojang on the player's machine.
- One mixin per target class, each with a comment saying what it hooks and why. After touching a mixin,
  run the smoke test.
- Every setting gets UI and saves by itself. Nobody should have to edit a file by hand.
- Tokens and account data stay out of logs and out of Git.
- Version bump: `project_version` in `gradle.properties` and `VantageInfo.VERSION` together.
- When a feature is finished or changes status, update `FEATURES.md`. New known gaps go into `TODO.md`.
- Say clearly what you could not test (real Microsoft sign-in, online servers, anything that needs keyboard or
  mouse input) instead of calling it done.

## Current state
- 2026-11-08: first version of client and launcher committed. Built and tested on Windows only.
- Microsoft sign-in needs an Azure app id approved for the Minecraft API (see `TODO.md`, "Needs your action").
- Open points live in `TODO.md`. Keep only a short summary here.

## Keep this file current
- At the end of every session: update "Current state" below (what changed, what is open, date).
  Keep it short and replace old lines instead of adding more.
- When the layout, commands or rules change, fix the matching section above in the same commit.
- A new rule I tell you during a session ("always …", "never …") goes into "Rules".
