# TODO / known issues

Ordered roughly by impact. See [FEATURES.md](FEATURES.md) for what is done.

## Needs your action
- [ ] **Register an Azure application and get it approved for the Minecraft API** (README → Microsoft sign-in).
      Without it nobody can sign in; the launcher explains this on the Accounts page.
- [ ] Choose a GitHub repository for releases and set it as the update channel default.

## Verification gaps
- [ ] Real Microsoft sign-in and joining an online-mode server (blocked on the Azure approval above).
- [ ] Launcher on macOS and Linux (built and tested on Windows only). Mojang runtime paths differ on macOS.
- [ ] Coexistence with Sodium, Iris, Lithium, ImmediatelyFast: Vantage mixins target vanilla methods that some of
      these mods also modify (light texture, fog, item rendering). Needs an in-game test with the performance pack.
- [ ] Self-update end to end against a real GitHub release.

## Product gaps
- [ ] Custom TrueType fonts for the UI/HUD (currently Minecraft default + Unifont).
- [ ] 1.7-style "old animations" (block-hitting, rod position).
- [ ] Custom sky textures (only colours today) and physics-based item drops (only flat items).
- [ ] Native installers (jpackage / MSI / DMG) and code signing.
- [ ] Tokens are stored in plain JSON with owner-only file permissions; use the OS keychain (DPAPI, Keychain,
      libsecret) instead.
- [ ] In-game account switching restarts the game through the launcher (Minecraft fixes the session at start-up).
- [ ] Port the client mod to newer Minecraft versions (26.x is unobfuscated; Mojang mappings were chosen to make
      this mostly a mixin-target review).
- [ ] Localisation (UI text is English only).
