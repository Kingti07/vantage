Vantage is a Minecraft PvP client with its own launcher. Sign in with your Microsoft account, press Play, done.

### What's new
- **More FPS out of the box:** new installations get Sodium, Lithium, ImmediatelyFast, FerriteCore and EntityCulling automatically (up to about +90% FPS in our benchmark at far render distance). Installations from 0.1.0 are asked once.
- **Experimental Vulkan renderer** (VulkanMod) per installation: in our benchmark 2.5 to 3 times the frame rate, but not every mod works with it.
- **Smoother games:** tuned Java memory settings, optional low-latency garbage collector, higher game priority and an option to use the dedicated graphics card on laptops.
- **A much more robust launcher:** downloads resume after interruptions and never hang. Your data is backed up instead of overwritten when something goes wrong. Updates are verified and fall back automatically. Sign-in data is encrypted, the console is readable and a crash dialog explains crashes.
- **No Java needed:** portable packages with their own Java runtime.

### Which file do I need?
| File | For |
|---|---|
| `vantage-launcher-*-portable-win.zip` | **Windows, recommended.** Unzip anywhere and start `Vantage\Vantage.exe`. No Java needed. |
| `vantage-launcher-*-portable-mac-aarch64.zip` | macOS on Apple Silicon. Unzip and open `Vantage.app` (the first time: right-click → Open, the app is not signed). |
| `vantage-launcher-*-portable-linux.zip` | Linux (x64). Unzip and start `Vantage/bin/Vantage`. |
| `vantage-launcher-*-all-<platform>.jar` | Single-file launcher for Windows (`win`), macOS (`mac`, `mac-aarch64`) or Linux (`linux`); needs Java 21 (`java -jar <file>`). |
| `vantage-client-*.jar` | Only the in-game client mod (Fabric 1.21.11, needs Fabric API) |

The launcher installs Minecraft, Fabric and the Vantage client by itself and updates itself from future releases; every update is checked against the SHA-256 checksums in `SHA256SUMS.txt`. Launchers of version 0.1.0 offer this update automatically. The Windows builds are tested; macOS and Linux builds are new and not yet tested on real machines.

See README.md and CHANGELOG.md for all details.
