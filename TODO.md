# TODO / known issues

Ordered roughly by impact. See [FEATURES.md](FEATURES.md) for what is done and [TESTPLAN.md](TESTPLAN.md) for the
manual checks.

## Waiting for others
- [ ] **Microsoft approval of the Azure app for the Minecraft API** (form submitted). Without it nobody can sign in;
      the launcher explains this on the Accounts page.

## Verification gaps
- [ ] Real Microsoft sign-in and joining an online-mode server (blocked on the approval above).
- [ ] Launcher and portable packages on macOS and Linux (built, never run). Mojang runtime paths, the Keychain /
      Secret Service key storage and the `.app` packaging need a real machine.
- [ ] Self-update against a real newer GitHub release (the hand-over itself was tested locally with a good and a
      corrupt jar). Note: 0.1.0 still uses the old update check and only finds releases that are not marked as
      pre-release.
- [ ] First run of the new release workflow with the portable matrix (Windows, macOS, Linux runners) and of the CI
      workflow.
- [ ] Coexistence with Sodium, Iris, Lithium, ImmediatelyFast: Vantage mixins target vanilla methods that some of
      these mods also modify (light texture, fog, item rendering). Needs an in-game test with the performance pack.

## Product gaps
- [ ] Code signing (Windows SmartScreen, macOS Gatekeeper) – needs paid certificates.
- [ ] Windows installer (.msi / .exe) in addition to the portable zip – needs the WiX toolset in the release build.
- [ ] Localisation (UI text is English only; German first).
- [ ] Custom TrueType fonts for the UI/HUD (currently Minecraft default + Unifont).
- [ ] 1.7-style "old animations" (block-hitting, rod position).
- [ ] Custom sky textures (only colours today) and physics-based item drops (only flat items).
- [ ] In-game account switching restarts the game through the launcher (Minecraft fixes the session at start-up).
- [ ] Port the client mod to newer Minecraft versions (26.x is unobfuscated; Mojang mappings were chosen to make
      this mostly a mixin-target review).
