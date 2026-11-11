# Testplan: Vantage Launcher (manuell)

Diese Liste ist für Tests von Hand gedacht. Die automatischen Tests (`./gradlew build`) prüfen die Logik. Hier geht es um
das, was nur ein Mensch am echten PC prüfen kann. Hake jeden Punkt ab und notiere Abweichungen mit Datum.

**Vorbereitung**
- Launcher-Datei: `vantage-launcher-<version>-all-win.jar` aus dem GitHub-Release oder aus `launcher/build/libs/`.
- Datenordner: `%APPDATA%\.vantage` (Windows), `~/Library/Application Support/vantage` (macOS),
  `~/.local/share/vantage` (Linux).
- Für einen „sauberen“ Test den Datenordner vorher umbenennen, zum Beispiel in `.vantage-alt`. Er enthält Konten,
  Installationen und Welten.
- Logs: `<Datenordner>/logs/launcher.log`. Bei Problemen diese Datei mitschicken.

---

## 1. Erststart auf einem sauberen System
| # | Was du tust | Erwartetes Ergebnis |
|---|---|---|
| 1.1 | Datenordner umbenennen, Launcher per Doppelklick starten | Fenster erscheint nach wenigen Sekunden. Startseite „Welcome to Vantage“, unten links „No account“. |
| 1.2 | Datenordner öffnen | Ordner mit `launcher.json`, `instances.json`, `logs/` und weiteren Unterordnern wurde angelegt. |
| 1.3 | `logs/launcher.log` öffnen | Erste Zeile nennt Vantage-Version, Windows-Version, Java-Version und Datenordner. |
| 1.4 | Auf einem PC **ohne** installiertes Java die `.jar` doppelklicken | Windows kann die Datei nicht öffnen. Bekannte Einschränkung, siehe README. Die portable ZIP-Version funktioniert ohne Java. |
| 1.5 | Portable ZIP entpacken (Ordner mit Leerzeichen, z. B. `C:\Spiele\Vantage Test`) und `Vantage.exe` starten | Launcher startet ohne installiertes Java. |

## 2. Microsoft-Login (erst nach Microsofts Freigabe möglich)
| # | Was du tust | Erwartetes Ergebnis |
|---|---|---|
| 2.1 | Accounts → „Add Microsoft account“ | Dialog mit Code und Knopf „Copy code & open microsoft.com/link“. |
| 2.2 | Knopf drücken, Code im Browser eingeben, anmelden | Dialog schließt sich, Meldung „Signed in as <Name>“, unten links erscheint dein Kopf und Name. |
| 2.3 | Während des Wartens „Cancel“ drücken | Dialog schließt sich, kein Fehler. |
| 2.4 | `accounts.json` im Datenordner öffnen | Die Tokens stehen nicht im Klartext da, sondern als `enc:v1:…`. Daneben liegt `token-key.dpapi`. |
| 2.5 | Zweites Konto hinzufügen, zwischen den Konten wechseln, eines abmelden | Auswahl und Abmeldung funktionieren, beim Abmelden kommt eine Nachfrage. |
| 2.6 | Vor der Freigabe: Login versuchen | Klare Meldung, dass die App noch nicht für Minecraft freigegeben ist. |

## 3. Installation und Download
| # | Was du tust | Erwartetes Ergebnis |
|---|---|---|
| 3.1 | Home → „Play“ (erster Start) | Unten erscheint eine Leiste mit Text, Fortschritt in %, „… MB of … MB“, Geschwindigkeit und Restzeit sowie einem „Cancel“-Knopf. |
| 3.2 | Warten bis Minecraft startet | Das Spiel öffnet mit dem Vantage-Hauptmenü. Etwa 700 MB wurden geladen. |
| 3.3 | Installations → „New installation“, eigenen Namen, anderen RAM-Wert, eigenen Ordner mit Leerzeichen und Umlaut wählen, „Create“ | Installation erscheint in der Liste und startet mit dem gewählten Ordner. |
| 3.4 | Als Spielordner eine Datei oder einen relativen Pfad eintragen | Fehlermeldung, nichts wird gespeichert. |
| 3.5 | Versionsliste öffnen | Älteste angebotene Version ist 1.7.10. Snapshots nur mit eingeschaltetem Schalter. |
| 3.6 | Installation bearbeiten → „Repair files“ | Alle Dateien werden geprüft, Meldung „All game files verified“. |

## 4. Spielstart und Spielende
| # | Was du tust | Erwartetes Ergebnis |
|---|---|---|
| 4.1 | Spiel starten, dann Console öffnen | Lesbare Zeilen wie `[12:34:56] [Render thread/INFO]: …`, Fehler rot, Warnungen gelb, kein XML. |
| 4.2 | In der Console „Problems only“ einschalten und etwas suchen | Nur Fehler und Warnungen bzw. passende Zeilen werden angezeigt. |
| 4.3 | „Stop game“ drücken | Das Spiel wird beendet, **kein** Absturz-Dialog. |
| 4.4 | Einen Absturz erzwingen: im Spiel F3 + C etwa 10 Sekunden gedrückt halten | Dialog „Minecraft crashed“ mit dem Anfang des Crash-Reports, „Copy“ und „Open file“. |
| 4.5 | Einstellung „After launch: Close“, Spiel starten und normal beenden | Launcher beendet sich mit dem Spiel. Nach einem Absturz kommt er stattdessen zurück und zeigt den Dialog. |
| 4.6 | Strg+Enter auf der Startseite | Die ausgewählte Installation startet. |

## 5. Abbrechen
| # | Was du tust | Erwartetes Ergebnis |
|---|---|---|
| 5.1 | Neue Installation mit anderer Minecraft-Version starten, während des Downloads „Cancel“ | Meldung „Launch cancelled. Downloaded files are kept for next time.“ |
| 5.2 | Danach erneut „Play“ | Der Download macht dort weiter, wo er aufgehört hat. |
| 5.3 | Während des Downloads das Fenster schließen | Nachfrage „Minecraft is being prepared“. „Close launcher“ beendet, „Cancel“ lässt weiterladen. |
| 5.4 | Während das Spiel läuft das Fenster schließen | Nachfrage „Minecraft is still running“. |

## 6. Ohne Internet
| # | Was du tust | Erwartetes Ergebnis |
|---|---|---|
| 6.1 | WLAN/LAN trennen, Launcher starten | Startet normal, keine Fehlermeldung (die Update-Prüfung bleibt still). |
| 6.2 | „Play“ mit einer schon gespielten Installation | Spiel startet. Einzelspieler funktioniert. Hinweis, dass du offline bist, falls die Sitzung erneuert werden musste. |
| 6.3 | Mods → Browse | Meldung „No internet connection, or the server could not be reached.“ mit Tipp, kein Fachbegriff. |
| 6.4 | Während eines Downloads das Internet trennen | Nach spätestens etwa 30 Sekunden kommt eine verständliche Fehlermeldung, der Launcher hängt nicht. Mit Internet erneut „Play“: Download wird fortgesetzt. |

## 7. Updates
| # | Was du tust | Erwartetes Ergebnis |
|---|---|---|
| 7.1 | Settings → Updates → „Check now“ auf der neuesten Version | „You are on the latest version“. |
| 7.2 | Ältere Version starten, wenn auf GitHub eine neuere liegt | Angebot „Vantage x.y.z is available“, nach dem Download „Update ready … downloaded and verified“. |
| 7.3 | Launcher schließen und neu öffnen | Die neue Version startet, im Titel steht die neue Versionsnummer. Beim übernächsten Start ist kein Umweg mehr nötig. |
| 7.4 | Eigene Update-Quelle eintragen (z. B. `jemand/irgendwas`) und prüfen | Falls dort ein Update liegt: deutliche Warnung, dass es nicht die offizielle Quelle ist. |

## 8. Einstellungen und Neustart
| # | Was du tust | Erwartetes Ergebnis |
|---|---|---|
| 8.1 | Akzentfarbe, Animationen und parallele Downloads ändern, Launcher neu starten | Alles ist noch so eingestellt. |
| 8.2 | Fenster verschieben, Größe ändern, Seite „Mods“ öffnen, Launcher schließen und öffnen | Fenster erscheint an gleicher Stelle und in gleicher Größe auf der Seite „Mods“. |
| 8.3 | Launcher schließen, in `instances.json` absichtlich ein Zeichen löschen (Datei kaputt machen), neu starten | Hinweis „Some saved data had to be repaired“. Die kaputte Datei liegt als `instances.broken-<Datum>.json` daneben. |
| 8.4 | Tastatur: Strg+1 bis Strg+6, Tab durch Dialoge, Enter und Esc | Seiten wechseln, Fokus ist sichtbar, Enter bestätigt, Esc schließt. Bei Löschen-Dialogen bestätigt Enter **nicht**. |

## 9. Mods
| # | Was du tust | Erwartetes Ergebnis |
|---|---|---|
| 9.1 | Mods → Browse → einen Mod installieren (z. B. Mod Menu) | Abhängigkeiten werden mitinstalliert, Fortschritt mit Abbrechen-Knopf. |
| 9.2 | Mod deaktivieren, entfernen, „Update all“ | Funktioniert, beim Entfernen kommt eine Nachfrage. |

## 10. Deinstallation
| # | Was du tust | Erwartetes Ergebnis |
|---|---|---|
| 10.1 | Launcher-Datei bzw. portablen Ordner löschen | Nichts weiter ist installiert, es gibt keine Registry-Einträge. |
| 10.2 | Datenordner löschen | Alle Spieldateien, Konten und Logs sind weg. **Vorher Welten aus `instances/<id>/saves` sichern.** |
| 10.3 | Nur unter Windows: Anmeldeschlüssel | Steckt in `token-key.dpapi` im Datenordner und ist mit ihm gelöscht. |

## 11. Leistung (ab 0.2.0)
| # | Was du tust | Erwartetes Ergebnis |
|---|---|---|
| 11.1 | Neue Installation anlegen und „Play“ | Vor dem Spielstart werden Sodium, Lithium, ImmediatelyFast, FerriteCore und EntityCulling installiert; in Mods → Installed stehen sie. |
| 11.2 | Mit einem Datenordner von 0.1.0 starten | Einmalige Frage „More FPS with performance mods?“. „Install on next start“ installiert sie beim nächsten „Play“. Die Frage kommt nie wieder. |
| 11.3 | Installation bearbeiten → „Performance mods“ aus, „Play“ | Die Leistungs-Mods sind deaktiviert (nicht gelöscht). Wieder an → beim nächsten Start aktiv. |
| 11.4 | „Vulkan renderer“ an, „Play“ | VulkanMod wird installiert, Sodium und ImmediatelyFast sind aus. Das Spiel läuft. Bitte prüfen: Sieht die Welt richtig aus, ist das Vantage-HUD da? |
| 11.5 | „Vulkan renderer“ wieder aus, „Play“ | Sodium und ImmediatelyFast sind wieder an, VulkanMod ist aus. |
| 11.6 | Settings → Performance → „Use the high-performance graphics card“ an, „Play“ (Laptop mit zwei Grafikchips) | Windows-Einstellungen → System → Anzeige → Grafik: Für `javaw.exe` des Minecraft-Java steht „Hohe Leistung“. Aus → Eintrag verschwindet. |
| 11.7 | Im Spiel: Vantage-Menü → Performance → FPS Boost Preset → „Apply“ | Videoeinstellungen: Grafik „Schnell“, keine Wolken, kein FPS-Limit, kein V-Sync. |
| 11.8 | Settings → Performance → „Low-latency memory management“ an, „Play“ | Spiel startet normal. In der Console steht beim Start `-XX:+UseZGC` im Startbefehl (Launcher-Log). |

## 12. Im Spiel (ab 0.2.0)
| # | Was du tust | Erwartetes Ergebnis |
|---|---|---|
| 12.1 | HUD-Elemente einschalten (z. B. FPS), dann F3+B drücken | Die Hitboxen erscheinen und das Vantage-HUD bleibt sichtbar. Nur das echte F3-Menü blendet es aus. |
| 12.2 | Vantage-Menü → PvP → „Hitboxes“ an, auf einem Server zu einem Spieler oder Tier gehen | Weiße Boxen; in Schlagreichweite wird die Box grün. Mit „Hittable means: In reach and aimed at“ nur, wenn du auch darauf zielst. |
| 12.3 | Settings → „Capes, hats & pets“ | Vorschau dreht sich. Umhang, Hut und Tier wählen: Die Vorschau und dein Spieler (F5) zeigen sie. |
| 12.4 | Einen Helm aufsetzen | Der Hut verschwindet (außer „Hide hat under helmet“ ist aus). |
| 12.5 | Einen Freund fragen, ob er deinen Umhang sieht | Nein: Die Cosmetics sieht nur, wer sie eingestellt hat. |

## Nicht auf Windows getestet (bitte auf macOS und Linux wiederholen)
Punkte 1, 2.4 (Schlüssel im macOS-Schlüsselbund bzw. Linux-Schlüsselbund statt `token-key.dpapi`), 3 und 4.
