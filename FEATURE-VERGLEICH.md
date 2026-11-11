# Vantage im Vergleich mit Lunar, Badlion, Feather, Modrinth, Essential & Co.

Stand: 11.11.2026, Branch `feature/modpacks-keyboard` (baut auf Version 0.2.0 auf).

Diese Datei prüft die lange Feature-Liste für einen „modernen Minecraft-Client“ Punkt für Punkt gegen Vantage. Die
Liste stammte von ChatGPT; Fehler darin sind unten korrigiert. Was sinnvoll und fair war, wurde am 11.11.2026
ergänzt. Alles, was hier „vorhanden“ oder „neu“ heißt, ist gebaut und durch die automatischen Tests gelaufen. Dazu
gehört ein echter Spielstart mit dem Modpack *Fabulously Optimized*. Was nur ein Mensch prüfen kann, steht in
[TESTPLAN.md](TESTPLAN.md).

## Legende

| Zeichen | Bedeutung |
|---|---|
| ✅ | War schon vorhanden |
| 🆕 | Am 11.11.2026 neu dazugekommen |
| 📦 | Per Mod mit einem Klick (Launcher → Mods → Browse). Geprüft: Für Minecraft 1.21.11 mit Fabric verfügbar |
| 🗓️ | Machbar, noch nicht gebaut (Vorschlag für später) |
| 🔒 | Braucht eigene Server von Vantage (Konten, Datenbank, Kosten), bevor man es bauen kann |
| ⛔ | Bewusst nicht: unfairer Vorteil, auf vielen Servern verboten, oder rechtlich/technisch nicht sinnvoll |

## Korrekturen an der ChatGPT-Liste

- **Modrinth ist kein Client**, sondern eine Plattform mit eigener App. Das steht in der Liste richtig. Vantage nutzt
  Modrinth für Mods, Modpacks, Resource Packs und Shader.
- **LazyDFU** gibt es für 1.21.11 nicht. **Enhanced Block Entities** und **ModernFix** (Fabric) gibt es für 1.21.11
  auch nicht. Sie gehören deshalb nicht ins Leistungspaket.
- **„Projectile Trajectory“, „Enemy Highlight“, „Player Tracking“, „Structure Finder“, „Auto Tool Selection“,
  „Chat-Makros“**: Das sind auf vielen PvP-Servern verbotene Vorteile. Auch Lunar und Badlion bieten sie dort nicht
  an. Vantage baut sie nicht ein (siehe „Fair Play“ in [README.md](README.md)).
- **„Server TPS“, „CPU-/GPU-Temperatur“**: Ein normaler Client bekommt diese Werte nicht zuverlässig. TPS schickt der
  Server nicht. Temperaturen kann Java nicht auf allen Systemen lesen.
- **CurseForge**: Die CurseForge-API braucht einen API-Schlüssel mit eigenen Nutzungsbedingungen. Viele Mods
  verbieten dort, dass andere Programme sie herunterladen. Vantage bleibt deshalb bei Modrinth.
- **Dawn Client**: Seine Funktionen ließen sich nicht verlässlich nachprüfen, deshalb wird nichts davon behauptet.
- **Forge/NeoForge** brauchen einen ganz anderen Installationsweg (Installer mit Verarbeitungsschritten). Der
  Vantage-Client selbst ist eine Fabric-Mod. Modpacks für Forge, NeoForge und Quilt zeigt der Launcher deshalb an,
  installiert sie aber noch nicht.

---

## 1. PvP & Competitive

### 1.1 Combat-HUD
| Funktion | Status | Hinweis |
|---|---|---|
| Keystrokes, CPS (links/rechts), FPS, Ping, Koordinaten, Richtung, Biom, Geschwindigkeit (BPS) | ✅ | Alles im HUD-Editor frei platzierbar |
| Reichweite, Combo, Treffer, Kills, W-Taps, Ziel-HUD (Kopf, Leben, Rüstung, Entfernung) | ✅ | |
| Rüstung, gehaltenes Item, Tränke mit Dauer, Haltbarkeit | ✅ | |
| Tode, Totems, Pfeile | 🆕 | Pfeile nur mit Bogen/Armbrust (einstellbar) |
| Warnungen: wenig Leben, Rüstung/Item kurz vor dem Kaputtgehen, Effekt läuft ab | 🆕 | Ein HUD-Element „Warnings“, Grenzen einstellbar |
| Sitzungszeit, Server-Adresse | 🆕 | |
| Treffer-Hitbox in anderer Farbe, wenn man treffen kann | ✅ | Modul „Hitboxes“ (Version 0.2.0) |
| Schadenszahlen, Trefferfarbe | ✅ | |
| CPS-Graph, Ping-Graph, S-Tap-, Blockhit-, Crit-, Projektil-Zähler, Combo-Dauer, Gegner-Verlauf | 🗓️ | Machbar aus Daten, die der Client hat |
| Ping des Ziels, Totem-Pops des Gegners | 🗓️ | Beides schickt der Server an alle Spieler; fair |
| Combat-Timer | ⛔ | Kennt nur der Server; Schätzungen wären oft falsch |
| Flugbahn von Projektilen, Gegner hervorheben, Spieler-Tracking | ⛔ | Unfairer Vorteil |

### 1.2 Fadenkreuz
| Funktion | Status | Hinweis |
|---|---|---|
| Größe, Dicke, Abstand, Umriss, Farbe, 6 Formen, Punkt, Zielfarbe, Invertieren, Abklingbalken | ✅ | |
| Regenbogen/Chroma, Treffer-Farbe, eigene Texturen, Presets, Import/Export | 🗓️ | Import/Export geht heute schon über „Profil kopieren“ |

### 1.3 Gameplay-Komfort
| Funktion | Status | Hinweis |
|---|---|---|
| Toggle Sprint/Sneak, Sprint-Status | ✅ | |
| Block-Umrandung und -Füllung, Low Fire, weniger Wackeln bei Treffern, Item-Animationen | ✅ | |
| Alte 1.7-Animationen (Blockhit) | 🗓️ | Steht in [TODO.md](TODO.md) |
| Auto Tool Selection | ⛔ | Automatisierung, auf PvP-Servern verboten |

### 1.4 Training
| Funktion | Status | Hinweis |
|---|---|---|
| CPS-Test, Reaktionstest, persönliche Bestwerte | 🆕 | Einstellungen → „Practice“. Läuft nur im Menü, nie im Spiel |
| Aim-, W-Tap-, Bridging-Trainer, Trainingsverlauf | 🗓️ | Bräuchte eigene Trainingswelten |

## 2. HUD & Overlays
| Funktion | Status | Hinweis |
|---|---|---|
| 26 HUD-Elemente (davon 6 neu), Leistungsmonitor (FPS, Frame-Zeit-Graph, Speicher, Entities, Partikel) | ✅/🆕 | |
| HUD-Editor: Drag & Drop, Größe, Einrasten, Hilfslinien, Farben, Schrift, Transparenz, Rahmen, Ecken, Schatten, Chroma | ✅ | |
| Mehrere gespeicherte HUD-Layouts, Stil-Vorlagen | ✅ | |
| HUD-Profile (alles: Module, Positionen, Farben, Tasten) | ✅ | Unter Cosmetics → Profiles |
| Profil automatisch je Server | 🆕 | „Use on this server“, wechselt beim Betreten und Verlassen |
| Profile teilen (kopieren/einfügen als Text) | 🆕 | „Copy profile“ / „Paste profile“ |
| Rotation von HUD-Elementen, Layer-Reihenfolge | 🗓️ | |
| Inventar-HUD, Wegpunkt-HUD | 🗓️ / 📦 | Wegpunkte z. B. mit Xaero's Minimap |

## 3. Leistung (FPS)
| Funktion | Status | Hinweis |
|---|---|---|
| Sodium, Lithium, ImmediatelyFast, FerriteCore, EntityCulling automatisch | ✅ | Leistungspaket ab 0.2.0 |
| More Culling, C2ME | ✅ 📦 | Im Leistungspaket wählbar |
| Dynamic FPS, Partikel-Steuerung, Entity Culling (eingebaut) | ✅ | |
| Vulkan-Renderer (VulkanMod), optimierte Java-Einstellungen, hohe Priorität, starke Grafikkarte | ✅ | 0.2.0, mit Messwerten in [CHANGELOG.md](CHANGELOG.md) |
| FPS-Boost-Voreinstellung | ✅ | |
| Leistungsmonitor mit Frame-Zeit-Graph | ✅ | 1 %-Low/0,1 %-Low im Spiel: 🗓️ |
| Automatische Profile je nach PC (Low-End/High-End) | 🗓️ | |
| Distant Horizons, Bobby (große Sichtweite) | 📦 | |

## 4. Modrinth & Mod-Verwaltung
| Funktion | Status | Hinweis |
|---|---|---|
| Mod-Browser im Launcher und im Spiel: Suche, Kategorien, Bilder, Autoren, Downloads, Sortierung, Filter | ✅ | |
| Ein-Klick-Installation, Abhängigkeiten, Kompatibilitätsprüfung, Konflikte, Updates, „Update all“, an/aus, entfernen | ✅ | |
| **Modpacks direkt aus Modrinth installieren** (Mods, Configs, Einstellungen, Resource/Shader Packs) | 🆕 | Neue Seite „Modpacks“ im Launcher |
| `.mrpack`-Datei importieren | 🆕 | „Import .mrpack file“ |
| Resource Packs und Shader aus Modrinth installieren | 🆕 | Mods → Browse → Typ wählen. Iris wird bei Shadern angeboten |
| Modpack exportieren/teilen, Modpack-Updates | 🗓️ | Nächster sinnvoller Schritt |
| Forge-, NeoForge-, Quilt-Installationen | 🗓️ | Großer Umbau (siehe oben) |
| CurseForge | ⛔ | API-Schlüssel und Nutzungsbedingungen |

## 5. Optik
| Funktion | Status | Hinweis |
|---|---|---|
| Motion Blur, Zoom (weich, Scrollen), Freelook, Perspektive, FOV, dynamisches FOV | ✅ | |
| Fullbright, eigener Himmel (Farben), Wetter, Tageszeit, klares Wasser, kein Hurt-Cam | ✅ | |
| Partikel-Multiplikator, Crit/Sharpness immer, Partikel abschalten | ✅ | |
| Item-Physik (einfach) | ✅ | Echte Physik: 🗓️ |
| Eigene Himmels-Texturen, eigene Sonne/Mond | 🗓️ | |
| Dynamisches Licht, verbundene Texturen | 📦 | LambDynamicLights, Continuity |
| Shader | 📦 🆕 | Iris + Shader direkt aus dem Launcher |

## 6. Social & Multiplayer
| Funktion | Status | Hinweis |
|---|---|---|
| Serverliste, Favoriten, Ping, Icons, direkt verbinden (Quick Play) | ✅ | Minecraft-Bildschirme und Launcher |
| Server-spezifische Profile | 🆕 | |
| Chat: Zeitstempel, gleiche Nachrichten stapeln, längerer Verlauf (bis 2000) | 🆕 | Modul „Chat Tweaks“ |
| Chat-Suche, Chat-Tabs, Filter, Nachricht kopieren | 🗓️ | |
| Freundesliste, Partys, Client-Chat, Online-Status | 🔒 | Braucht Vantage-Server und Konten |
| Sprachchat | 📦 | Simple Voice Chat (muss auch auf dem Server installiert sein) |
| Chat-Berichte abschalten | 📦 | No Chat Reports |

## 7. Cosmetics
| Funktion | Status | Hinweis |
|---|---|---|
| 7 Umhänge, 5 Hüte, Schultertiere (5 Papageien, Biene), Vorschau | ✅ | 0.2.0. Nur für dich sichtbar |
| Themes, Menü-Hintergründe, Schriften, GUI-Größe | ✅ | |
| Emotes | 📦 | Emotecraft (andere sehen sie nur mit derselben Mod) |
| Flügel, Rucksäcke, animierte Umhänge, mehr Hüte | 🗓️ | Lokal machbar |
| Cosmetics für andere sichtbar, Shop, Handel | 🔒 | Braucht Vantage-Server. Ein Shop muss Mojangs Regeln einhalten (kein Pay-to-Win) |
| Skin wechseln/hochladen | 🗓️ | Geht über die offizielle Minecraft-API mit dem Login |

## 8. Replay, Aufnahme, Screenshots
| Funktion | Status | Hinweis |
|---|---|---|
| Replay-System mit Kamerafahrten | 📦 | ReplayMod |
| Screenshot-Galerie im Launcher | 🗓️ | |
| Streamer-Modus (Name, IP, Koordinaten verbergen) | 🗓️ | |
| Bildschirmaufnahme | ⛔ | OBS kann das besser; ein eigener Recorder wäre viel Code ohne Mehrwert |

## 9. Welt & Einzelspieler
| Funktion | Status | Hinweis |
|---|---|---|
| Todeskoordinaten im Chat | 🆕 | Modul „Death Coordinates“ |
| Minimap, Weltkarte, Wegpunkte, Todespunkt-Wegpunkte | 📦 | Xaero's Minimap + World Map oder JourneyMap |
| Shulker-Vorschau, Essens-/Sättigungsanzeige | 📦 | ShulkerBoxTooltip, AppleSkin |
| Inventar sortieren, Maus-Komfort | 📦 | Inventory Profiles Next, Mouse Tweaks |
| Rezept-Anzeige | 📦 | JEI (EMI gibt es für 1.21.11 nicht) |
| Schematics, Bauhilfen | 📦 | Litematica, MiniHUD |
| Lichtlevel-Anzeige | 📦 | MiniHUD |

## 10. Einstellungen & Tasten
| Funktion | Status | Hinweis |
|---|---|---|
| **Tastatur-Menü: Tastatur sehen, jede Taste anklicken und belegen** | 🆕 | Einstellungen → Keybinds |
| Alle Vantage-, Minecraft- und Mod-Tasten an einer Stelle, Suche, Konflikte sichtbar | 🆕 | Farbe zeigt, wie viele Aktionen eine Taste hat |
| Maustasten 1–5, mehrere Tasten pro Vantage-Aktion | ✅ | |
| Halten/Umschalten (Zoom, Freelook, Sprint …) | ✅ | Je Modul |
| Alles zurücksetzen, Listenansicht | ✅ | |
| Tastenkombinationen (Strg+Taste), Doppeltippen, langes Drücken | 🗓️ | |
| Tasten-Profile, Import/Export | ✅ | Teil der Profile (inkl. „Profil kopieren“) |
| Video-, Audio-, alle Minecraft-Optionen | ✅ | Über das Vantage-Einstellungsmenü erreichbar |

## 11. Launcher & Konten
| Funktion | Status | Hinweis |
|---|---|---|
| Microsoft-Login, mehrere Konten, Wechsel, verschlüsselte Tokens | ✅ | |
| Versionen (alle Releases, Snapshots), Fabric-Version, Java automatisch oder eigenes, RAM, JVM-Argumente, Auflösung | ✅ | |
| Downloads mit Prüfsummen, Fortsetzen, Abbrechen, „Dateien reparieren“ | ✅ | |
| Selbst-Update mit Prüfsumme und automatischem Zurückfallen | ✅ | |
| Konsole mit Filter, Absturz-Dialog | ✅ | |
| Modpacks-Seite | 🆕 | |
| News-Feed, Server-Entdecken-Seite | 🗓️ | |

## 12. Erweiterungen, Sicherheit, Besonderes
| Funktion | Status | Hinweis |
|---|---|---|
| Downloads nur verifiziert, Pfade können den Ordner nicht verlassen, beschädigte Dateien werden gesichert | ✅ | Gilt jetzt auch für Modpacks |
| Profile in der Cloud, öffentliche Profil-Bibliothek, Marketplace | 🔒 | Heute: Profil als Text kopieren und teilen |
| Plugin-API für andere Entwickler | 🗓️ | Fabric-Mods erfüllen diese Rolle schon |
| Befehls-Palette (alles durchsuchen) | 🗓️ | |

## 13. Design
| Funktion | Status |
|---|---|
| Dunkles Design, Akzentfarben, Animationen, abgerundete Ecken, eigene Hintergründe, Suche in Listen | ✅ |
| Rechts-Shift öffnet den HUD-Editor, Rechts-Strg das Modul-Menü | ✅ |
| Heller Modus, Glas-Effekt | 🗓️ |

---

## Was am 11.11.2026 dazugekommen ist

1. **Modpacks aus Modrinth** direkt im Launcher. Suchen, Version wählen, Inhalt ansehen, installieren: Mods, Configs,
   Einstellungen, Resource und Shader Packs. Dazu `.mrpack`-Import. Getestet mit *Fabulously Optimized 12.3.0*
   (48 Mods). Dabei ist ein echter Fehler aufgefallen und behoben worden: Packs mit älterem Fabric Loader starteten
   mit Vantage nicht.
2. **Resource Packs und Shader** im Launcher-Browser. Bei Shadern wird Iris angeboten.
3. **Tastatur-Menü** mit allen Tasten, Maustasten und Belegungen. Die Beschriftung kommt vom System, also z. B. das
   Schweizer Layout.
4. **Neue HUD-Elemente:** Sitzungszeit, Server, Tode, Totems, Pfeile, Warnungen.
5. **Utility:** Todeskoordinaten, Chat-Verbesserungen (Zeitstempel, Stapeln, Verlauf).
6. **Profile:** automatisch je Server und als Text teilbar.
7. **Training:** CPS-Test und Reaktionstest mit Bestwerten.

## Vorschlag für die nächsten Schritte

1. Modpack-Updates und Modpack-Export (eigene Installation als `.mrpack` teilen).
2. Screenshot-Galerie im Launcher und Streamer-Modus.
3. Ping des Ziels, Totem-Pop-Zähler, CPS-/Ping-Graph, Fadenkreuz-Presets mit Chroma.
4. Tastenkombinationen (Strg/Alt/Shift + Taste).
5. Quilt-, danach Forge/NeoForge-Installationen.
6. Erst wenn Vantage eigene Server haben soll: Freunde, Partys, für andere sichtbare Cosmetics, Cloud-Profile. Das
   kostet laufend Geld und braucht Datenschutz-Regeln.
