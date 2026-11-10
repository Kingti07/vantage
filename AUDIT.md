# Audit: Vantage Launcher

Stand: 10.11.2026 · Version 0.1.0 · Branch `release/0.1.0`, Commit `ea36873`
Umfang: der Launcher (`launcher/`) und der gemeinsame Code (`common/`), den er benutzt. Den Ingame-Client (`client/`)
habe ich nur dort angesehen, wo der Launcher ihn berührt.
In Phase 0 bis 2 wurde kein Code geändert.

## 1. Kurzfassung

Der Launcher baut ohne Fehler, alle 12 Tests sind grün, und der komplette Ablauf funktioniert: Erststart, Installation,
Spielstart und ein zweiter Start ohne Internet. Er ist also nicht kaputt.
Die wichtigsten Baustellen sind:

1. **Datenverlust:** Ist `instances.json` oder `accounts.json` beschädigt, überschreibt der Launcher sie mit
   Standardwerten. Die Installationsliste und die Konten sind dann weg.
2. **Selbst-Update:** Updates werden ohne Prüfsumme installiert. Ist ein Update kaputt, startet der Launcher nicht mehr.
3. **Tokens:** Die Anmeldedaten liegen im Klartext auf der Festplatte.
4. **Downloads:** Eine hängende Verbindung blockiert endlos. Abgebrochene Downloads werden nicht fortgesetzt,
   und es gibt keinen Abbrechen-Knopf.
5. **Fehlermeldungen:** Sie sind technisch, zum Beispiel „ClosedChannelException“ oder „…: null“. Die Konsole zeigt
   rohes XML statt lesbarer Zeilen.

| Schweregrad | Anzahl |
|---|---|
| Kritisch | 0 |
| Hoch | 6 |
| Mittel | 20 |
| Niedrig | 22 |

Nichts davon ist „kritisch“, weil keiner der Funde den normalen Ablauf auf einem gesunden System verhindert.

## 2. Testergebnisse (Phase 1)

| Prüfung | Ergebnis |
|---|---|
| Sauberer Build (`clean build`, alle Java-Compiler-Warnungen an) | Erfolgreich. Launcher: 0 Warnungen. `common`: 2 (fehlende `serialVersionUID`). Keine veralteten Gradle-Funktionen. |
| Unit-Tests | 12 von 12 grün |
| Erststart in leerem Ordner, Pfad mit Leerzeichen, Umlaut und Klammern (`Smoke Täst (1)`) | Erfolgreich. 700 MB geladen (84 Bibliotheken, 4.591 Spieldateien, Java 21.0.7 mit 402 Dateien, Fabric API). Das Spiel startete nach 20 s, der Ingame-Test lief mit 0 Fehlern, Exit-Code 0. |
| Zweiter Start ohne Internet (Netz blockiert) | Erfolgreich. Spiel nach 9 s gestartet, davon ca. 4,5 s Warten auf vergebliche Netzversuche. |
| Oberfläche auf frischem Datenordner | Alle 9 Seiten und der Installations-Dialog werden korrekt dargestellt. Start bis zum Fenster ca. 3 s. Warnung beim Start: „Unsupported JavaFX configuration“. |
| Oberfläche ohne Internet | Funktioniert, aber die Meldungen sind unverständlich: „Modrinth search failed: ClosedChannelException“, „Could not download the Minecraft version list: null“. Meldungen erscheinen halb verdeckt hinter offenen Dialogen. |
| Bibliotheken auf bekannte Sicherheitslücken (OSV-Datenbank) | Keine Lücken in Gson, SLF4J, JavaFX und TwelveMonkeys. Verfügbare Updates: Gson 2.15.0 (aktuell 2.13.2), JUnit 6.1.3 (nur für Tests). |

**Von Tino manuell zu testen**, weil ich es nicht selbst ausführen kann:
- Microsoft-Login mit echtem Konto (erst nach Microsofts Freigabe möglich) und Beitritt zu einem Online-Server
- Bedienung mit Maus und Tastatur, also echtes Klicken durch alle Seiten
- Selbst-Update mit einer echten neueren Version
- Start per Doppelklick auf einem PC ohne Entwickler-Werkzeuge, mit und ohne installiertes Java
- Internet während eines Downloads trennen, volle Festplatte
- macOS und Linux

## 3. Hauptabläufe (im Code verfolgt)

| Ablauf | Befund |
|---|---|
| Erststart | Legt Ordner und ein Standardprofil „Vantage PvP“ an und prüft im Hintergrund auf Updates. Es gibt keine Einführung, die Startseite sagt „Sign in…“. |
| Login | Offizieller Microsoft-Gerätecode. Abbrechen funktioniert. Die Fehler sind gut übersetzt (Freigabe fehlt, abgelehnt, abgelaufen). Siehe F11. |
| Auswahl | Profil-Auswahl auf der Startseite. Sie wird gespeichert, sobald man auf „Play“ drückt. |
| Download und Installation | Parallel (Standard 8), 3 Versuche mit Pause, SHA-1-Prüfung, vorhandene Dateien werden übersprungen. Siehe F2, P1, U3. |
| Spielstart | Konto wird aufgefrischt, Mods werden vorbereitet, das Spiel startet ohne Shell. Der Token ist im Log maskiert. Siehe G5. |
| Updates | Siehe S2, S3, F3, F4. |
| Einstellungen | Jede Änderung wird sofort und atomar gespeichert, mit temporärer Datei und Umbenennen. Es gibt aber keine Prüfung der Werte, siehe F5. |
| Neustart | Die Einstellungen sind da. Fenstergröße und -position nicht, siehe U5. |

## 4. Funde

Format: **ID · Schweregrad · Titel**, darunter Fundstelle, Problem und Lösungsvorschlag.

### Funktion und Bugs

**F1 · Hoch · Beschädigte Konfiguration löscht Installationen und Konten**
`SettingsStore.java:51-62`, `LauncherContext.java:39-44`
Ist `instances.json` nicht lesbar, legt der Launcher ein neues Standardprofil an und überschreibt die Datei. Alle
Installationen sind dann aus der Liste verschwunden. Bei `accounts.json` gehen die Konten beim nächsten Login verloren.
Lösung: Die defekte Datei als `*.broken-<Datum>.json` sichern und nie überschreiben. Den Nutzer informieren und nur im
Speicher mit Standardwerten weiterarbeiten.

**F2 · Hoch · Hängende Verbindung blockiert den Download endlos**
`Http.java:142-190`
Das Zeitlimit gilt nur bis zur ersten Antwort. Liefert der Server danach keine Daten mehr, wartet der Launcher für immer.
Lösung: Eine Überwachung pro Download, die nach etwa 30 s ohne Daten abbricht und es erneut versucht.

**F3 · Hoch · Ein kaputtes Update sperrt den Launcher aus**
`Main.java:29-35`, `SelfUpdater.java:108-126`
Bei jedem Start wird ein bereitgelegtes Update ungeprüft gestartet und die alte Version beendet sich. Startet das Update
nicht, öffnet sich gar nichts mehr. Dann hilft nur, den Ordner `updates/` von Hand zu löschen.
Lösung: Das Update meldet nach erfolgreichem Start „ok“. Kommt diese Meldung nicht, markiert der Launcher das Update als
fehlerhaft, startet die alte Version und informiert den Nutzer.

**F4 · Mittel · Versionsnummer wird an zwei Stellen gepflegt**
`VantageInfo.java:8` und `gradle.properties`
Vergisst man beim Release eine der beiden Stellen, meldet der Launcher bei jedem Start erneut ein Update.
Lösung: Die Versionsnummer beim Bauen aus `gradle.properties` erzeugen.

**F5 · Mittel · Gespeicherte Werte werden nicht geprüft**
`LauncherSettings.java`, `Instance.java`, `MainWindow.java:227`
Unsinnige Werte wie 0 MB RAM, 0 Download-Threads oder negative Auflösung werden einfach benutzt. Ein unbekannter Wert bei
„nach dem Start“ wird zu `null` und löst nach dem Spielstart einen Fehler aus. Danach hält der Launcher das Spiel für
dauerhaft „laufend“, und „Play“ bleibt bis zum Neustart des Launchers gesperrt.
Lösung: Werte beim Laden zentral prüfen und auf erlaubte Werte begrenzen.

**F6 · Mittel · Schließen während das Spiel läuft**
`LauncherApp.java:21`
Das Fenster schließt ohne Nachfrage. Danach sind die Konsole und der Kontowechsel im Spiel ohne Funktion.
Lösung: Nachfragen („Minecraft läuft noch – trotzdem schließen?“) oder in den Hintergrund wechseln.

**F7 · Mittel · Launcher und Spiel schreiben gleichzeitig `accounts.json`**
`AccountStore.java:44`
Der Launcher behält den alten Stand im Speicher. Wird das Konto im Spiel gewechselt, kann der Launcher diese Änderung
später mit seinem alten Stand überschreiben.
Lösung: Vor jedem Schreiben neu laden und eine Dateisperre verwenden.

**F8 · Mittel · Sehr alte Minecraft-Versionen (bis etwa 1.7.2) starten nicht richtig**
`GameInstaller.java:133-135`
Man kann sie auswählen, aber ihre Spieldateien im alten Format werden nicht angelegt. Es gibt nur eine Warnung im Log.
Lösung: Diese Versionen ausblenden oder die Unterstützung nachrüsten. **Entscheidung nötig, siehe Abschnitt 6.**

**F9 · Niedrig · Eigener Spielordner wird nicht geprüft**
`InstallationsPage.java:314`
Ob der Ordner beschreibbar ist, wird nicht geprüft. Zwei Installationen können denselben Ordner benutzen.
Lösung: Beim Speichern prüfen und warnen.

**F10 · Niedrig · Speicher voll oder fehlende Rechte ergeben rohe Java-Meldungen**
`Ui.java:203`
Lösung: Wird mit U1 gelöst, als verständlicher Text mit Tipp.

**F11 · Niedrig · „Signed in“ erscheint auch, wenn das Speichern fehlschlug**
`AccountsPage.java:153-157`

**F12 · Niedrig · `updates/` wird nie aufgeräumt**
`SelfUpdater.java`
Alte Update-Dateien bleiben für immer liegen.

**F13 · Niedrig · Alte Dateien einer Java-Version bleiben liegen**
`JavaRuntimeManager.java:86-123`
Bringt Mojang eine neue Java-Version heraus, werden die neuen Dateien über die alten gelegt. Veraltete Dateien bleiben.

### Sicherheit

**S1 · Hoch · Anmeldedaten im Klartext**
`AccountStore.java:44`, `FileUtil.java:38`
`accounts.json` enthält den Microsoft-Refresh-Token, der dauerhaft Zugriff auf das Minecraft-Konto gibt. Auf Mac und
Linux ist die Datei nur für den eigenen Benutzer lesbar, auf Windows ist sie nicht zusätzlich geschützt.
Lösung: Die Tokens mit dem Schutz des Betriebssystems verschlüsseln: Windows DPAPI, macOS Schlüsselbund, Linux
libsecret oder als Rückfall Dateirechte. Die bestehende Datei wird automatisch umgestellt.
**Entscheidung nötig, siehe Abschnitt 6.** Das braucht eine neue Bibliothek, und der Ingame-Client muss ein wenig
angepasst werden, weil er die Datei mitliest.

**S2 · Hoch · Updates werden nicht auf Echtheit geprüft**
`SelfUpdater.java:97-105`, `SettingsPage.java:199`
Die Update-Datei wird ohne Prüfsumme und ohne Signatur geladen und ausgeführt. Außerdem kann man in den Einstellungen
jedes beliebige GitHub-Projekt als Update-Quelle eintragen.
Lösung:
- Die SHA-256-Prüfsumme prüfen, die GitHub zu jeder Release-Datei angibt, bzw. `SHA256SUMS.txt`.
- Optional die Datei signieren, mit einem öffentlichen Schlüssel im Launcher.
- Deutlich warnen, wenn nicht die offizielle Quelle eingestellt ist.

**S3 · Mittel · Jede Datei in `updates/` wird ungeprüft gestartet**
`Main.java:31`, `SelfUpdater.java:108`
Es reicht, dass der Dateiname eine höhere Versionsnummer enthält.
Lösung: Nur Updates starten, die beim Download geprüft und mit ihrer Prüfsumme vermerkt wurden.

**S4 · Mittel · Java-Dateiliste von Mojang wird ohne Prüfsumme geladen**
`JavaRuntimeManager.java:88`
Die Prüfsumme steht im Index, wird aber nicht verwendet.
Lösung: Die SHA-1 prüfen, genau wie bei allen anderen Downloads.

**S5 · Niedrig · Skin-Bilder werden auch über unverschlüsseltes http geladen**
`SkinHead.java:27`
Lösung: Adressen immer auf https umschreiben.

**S6 · Niedrig · Dateipfade aus den Versionsdaten werden nicht auf `../` geprüft**
`GameInstaller.java:92,100`
Die Quellen sind Mojang und Fabric über HTTPS, das Risiko ist also gering.
Lösung: Die gleiche Prüfung wie bei den Java-Dateien, die es schon gibt.

**S7 · Niedrig · Der Zugangs-Token steht in der Befehlszeile des Spiels**
`GameLauncher.java:58`
Andere Programme desselben Benutzers können ihn dort sehen. Der offizielle Minecraft-Launcher macht das genauso, und im
Log ist der Token maskiert.
Empfehlung: So lassen und dokumentieren.

**S8 · Niedrig · Keine automatische Prüfung der Bibliotheken auf Sicherheitslücken**
Heute ist alles sauber, siehe Abschnitt 2, aber nichts prüft das laufend.
Lösung: Eine OSV-Abfrage in der CI. Dafür ist keine neue Bibliothek im Launcher nötig.

### Performance

**P1 · Mittel · Abgebrochene Downloads beginnen von vorn**
`Http.java:142`
Lösung: Teil-Dateien behalten und per HTTP-Range fortsetzen.

**P2 · Mittel · Die Konsole kann die Oberfläche bremsen**
`ConsolePage.java:22,75`
Bis zu 1,5 Millionen Zeichen stehen in einem einzigen Textfeld. Bei viel Ausgabe ruckelt das.
Lösung: Eine virtuelle Zeilenliste, die nur sichtbare Zeilen zeichnet, mit einem Limit von 5.000 Zeilen.

**P3 · Niedrig · Ohne Internet unnötige Wartezeit**
`Http.java:87-114`
Jede Abfrage wird dreimal mit Pausen versucht. Ohne Netz kostet das beim Start rund 4,5 s.
Lösung: Einen Verbindungsfehler erkennen und für die übrigen Abfragen sofort den Cache nehmen.

**P4 · Niedrig · Der Fortschrittsbalken springt**
`DownloadQueue.java:69-77`
Feste Anteile pro Phase, und fehlgeschlagene Versuche zählen doppelt.

**P5 · Niedrig · Der Modrinth-Zwischenspeicher wächst ohne Grenze**
`ModrinthApi.java:35`
Abgelaufene Einträge werden nie entfernt.

**P6 · Niedrig · Nach einem Update startet der Launcher jedes Mal zweimal**
`Main.java:29-35`
Erst die alte Version, dann übergibt sie an die neue.
Lösung: Wird mit F3 gelöst, indem die neue Version die alte Datei ersetzt.

### Architektur und Code-Qualität

**A1 · Mittel · Logik steckt in der Oberfläche**
`MainWindow.java:201-285`, `SettingsPage.java:220`
Der Spielstart-Ablauf und die Update-Prüfung liegen in UI-Klassen.
Lösung: In eigene Dienste verschieben (`LaunchController`, `UpdateService`). Die Oberfläche ruft sie nur noch auf.

**A2 · Mittel · Logging ist dünn**
`Main.java:39-49`
- Es wird nur eine alte Log-Datei behalten, und die Größe ist nicht begrenzt.
- Beim Start werden keine Grundinfos geschrieben: Version, Betriebssystem, Java, Datenordner.

Lösung: Eine eigene kleine Rotation (5 Dateien, je 5 MB) und eine Startzeile mit diesen Infos. Dafür ist keine neue
Bibliothek nötig.

**A3 · Mittel · Die Konfiguration hat keine Versionsnummer, keine Umstellung und keine Prüfung**
`LauncherSettings.java`, `Instance.java`
Alle Felder sind öffentlich und können von überall geändert werden.
Lösung: Ein Feld `configVersion`, eine Migrationsfunktion und eine Prüfung beim Laden (siehe F5).

**A4 · Niedrig · Toter Build-Code**
`launcher/build.gradle:62`
Das Skript füllt eine Datei `launcher.properties` aus, die es nicht gibt.

**A5 · Niedrig · Fehler werden uneinheitlich angezeigt**
Mal als Toast, mal als Dialog, mal mit `getMessage()`, mal mit `Ui.message()`.
Lösung: Eine zentrale Fehleranzeige (siehe U1).

**A6 · Niedrig · Feste Zahlen sind im Code verstreut**
Zum Beispiel Zeitlimits, Token-Puffer, Konsolen-Limit und Wiederholversuche.
Lösung: Als benannte Konstanten an einer Stelle sammeln.

**A7 · Niedrig · Kein Linter und kein Formatter**
Der Code ist einheitlich formatiert (Tabs), aber nichts erzwingt das.
Lösung: Checkstyle, ein Plugin, das Gradle schon mitbringt, plus Compiler-Warnungen als Fehler in `launcher` und `common`.
Die 2 vorhandenen Warnungen werden dabei behoben.

### Bedienung

**U1 · Hoch · Fehlermeldungen sind unverständlich**
`Ui.java:203-210`, `GameMeta.java:54`
Ohne Internet heißt es „ClosedChannelException“ oder „…: null“. Es gibt keine Lösungsvorschläge.
Lösung: Typische Fehler in Klartext mit Tipp übersetzen, zum Beispiel „Keine Internetverbindung – bereits installierte
Versionen lassen sich trotzdem starten“. Technische Details nur noch im Log und über „Details kopieren“.

**U2 · Mittel · Meldungen erscheinen hinter offenen Dialogen**
`Toasts.java:32`, `Overlay.java:43`
Lösung: Die Meldungsebene immer ganz oben anzeigen.

**U3 · Mittel · Der Fortschritt zeigt zu wenig**
`TaskBar.java`
Es fehlen Geschwindigkeit, Restzeit, Größe („312 von 700 MB“) und ein Abbrechen-Knopf. Laufen zwei Aufgaben
gleichzeitig, teilen sie sich einen Balken.
Lösung: Diese Angaben ergänzen, Abbrechen einbauen und mehrere Aufgaben getrennt anzeigen.

**U4 · Mittel · Die Konsole zeigt rohes XML**
`LaunchService.java:141-150`
Minecraft schreibt bei Launcher-Start XML, zum Beispiel `<log4j:Message><![CDATA[...]]>`, und der Launcher zeigt es
unverändert an.
Lösung: Das XML einlesen und als lesbare Zeilen zeigen (Zeit, Stufe, Nachricht), farbig nach Stufe und mit Filter.

**U5 · Mittel · Das Fenster merkt sich nichts**
`LauncherApp.java:24`
Größe, Position, maximiert und zuletzt geöffnete Seite werden nicht gespeichert.

**U6 · Niedrig · Die Oberfläche ist nur auf Englisch**
**Entscheidung nötig, siehe Abschnitt 6.**

**U7 · Niedrig · Wenig Tastaturbedienung**
Nur „Esc“ schließt Dialoge.
Lösung:
- `Enter` bestätigt Dialoge.
- `Strg+1…6` wechselt die Seiten.
- `Strg+Enter` startet das Spiel.
- Die Fokus-Rahmen werden sichtbar.

**U8 · Mittel · Nach einem Absturz gibt es keine Hilfe**
`MainWindow.java:282-284`
Es erscheint nur „exit code 1“.
Lösung: Ein Dialog mit Auszug aus dem neuesten Crash-Report, dazu „Ordner öffnen“ und „Kopieren“.

### Spiele-Launcher

**G1 · Mittel · Ohne Internet und mit abgelaufenem Token ist kein Spielstart möglich**
`LaunchService.java:65-72`
Der Token läuft nach etwa 24 h ab. Ohne Netz bricht der Start dann ab, auch für den Einzelspieler-Modus.
Lösung: Bei einem Netzfehler mit dem vorhandenen Token starten und darauf hinweisen, dass Online-Server nicht gehen.

**G2 · Niedrig · Keine Prüfung, ob der RAM-Wert zur Java-Version passt**
Zum Beispiel mehr als 4 GB auf 32-Bit-Java.
Lösung: Beim Speichern warnen.

### Build und Auslieferung

**B1 · Mittel · Kein Installer**
Der Nutzer braucht installiertes Java 21. Ein Doppelklick auf eine `.jar` funktioniert nicht auf jedem PC.
Lösung: Mit `jpackage` (im JDK enthalten, keine neue Bibliothek) eine portable ZIP-Version mit eigener Java-Laufzeit
bauen, optional einen Windows-Installer. **Entscheidung nötig, siehe Abschnitt 6.**

**B2 · Mittel · Keine CI bei normalen Änderungen**
Die Tests laufen nur beim Erstellen eines Releases.
Lösung: Ein Workflow für jeden Push und Pull Request (Build, Tests, Checkstyle, OSV).

**B3 · Niedrig · Kein richtiges App-Icon**
Das Icon wird zur Laufzeit gezeichnet (64 px). Für Installer und Taskleiste fehlt eine `.ico`- bzw. `.icns`-Datei.

**B4 · Niedrig · JavaFX-Warnung beim Start**
„Unsupported JavaFX configuration“ wegen der Ein-Datei-Bauweise. Mit B1 lässt sich das sauber lösen.

**Bereits gut:**
- Builds sind reproduzierbar: Die Release-Datei war byte-gleich mit dem lokalen Build.
- Es gibt keine Geheimnisse im Repository, die Azure-ID ist öffentlich und kein Geheimnis.
- `.gitignore` passt.
- Alle Downloads laufen über HTTPS mit Prüfsumme, außer S4 und S5.
- Es gibt keine Shell-Aufrufe, Links öffnen nur http(s).
- Vor jeder Löschaktion wird nachgefragt.
- Die Einstellungen werden atomar geschrieben.

## 5. Priorisierter Umsetzungsplan

Jeder Schritt bekommt einen eigenen Commit, danach wird gebaut und getestet, und du bekommst eine kurze Statusmeldung.
Bestehende Nutzerdaten werden in jedem Schritt übernommen oder automatisch umgestellt.

0. **Sicherheitsnetz:** Tag `baseline-before-overhaul` auf den jetzigen Stand setzen, neuer Branch `overhaul/launcher`,
   AUDIT.md committen.
1. **Datenverlust verhindern:** F1, F5, A3, F7. Defekte Dateien sichern statt überschreiben. Konfig-Versionsnummer,
   Migration, Prüfung der Werte. Konten vor dem Schreiben neu laden.
2. **Downloads robust machen:** F2, P1, P3, P4, U3. Daten-Zeitlimit, Fortsetzen, schneller Offline-Modus, Abbrechen,
   Geschwindigkeit, Restzeit und Größe.
3. **Selbst-Update absichern:** S2, S3, F3, F4, F12, P6. SHA-256-Prüfung, nur geprüfte Updates starten, Rückfall bei
   kaputtem Update, Aufräumen, Versionsnummer aus einer Quelle, Warnung bei fremder Update-Quelle.
4. **Tokens sicher speichern:** S1, nach deiner Entscheidung.
5. **Verständliche Fehler und lesbare Konsole:** U1, F10, F11, A5, U2, U4, P2, U8. Zentrale Fehlertexte mit Tipps,
   Meldungen über Dialogen, Konsole als lesbare Liste, Absturz-Dialog.
6. **Aufräumen der Architektur:** A1, A2, A4, A6. Spielstart und Updates als Dienste, Logging mit Rotation und
   Startinfos, Konstanten, toter Code raus.
7. **Bedienkomfort:** U5, F6, U7, G1, F9, G2. Fenster merken, Nachfrage beim Schließen, Tastatur, Offline-Start,
   Ordner- und RAM-Prüfung.
8. **Restliche Sicherheitspunkte:** S4, S5, S6, F13.
9. **Qualitätswerkzeuge und CI:** A7, B2, S8. Checkstyle, Warnungen als Fehler, Workflow für Push und PR mit OSV-Check,
   Gson-Update.
10. **Auslieferung:** B1, B3, B4, nach deiner Entscheidung. Portable Version oder Installer, Icon.
11. **Phase 4 und 5:** Tests für Konfiguration, Downloads, Profile, Startargumente und Updates, alles offline. Dazu
    `TESTPLAN.md`, `README.md`, `CHANGELOG.md` und der Abschlussbericht.

## 6. Entscheidungen, die ich von dir brauche

1. **Token-Speicher (S1):** Ich empfehle die Bibliothek JNA (ca. 2 MB). Damit nutzt der Launcher den Schutz des
   Betriebssystems, und Minecraft bringt JNA im Spiel schon mit. Der Ingame-Client braucht eine kleine Anpassung,
   damit er die verschlüsselte Datei lesen kann. Alternative: nur die Dateirechte unter Windows verschärfen. Das ist
   einfacher, aber deutlich schwächer.
2. **Sehr alte Minecraft-Versionen (F8):** Empfehlung: Versionen vor 1.7.10 ausblenden. Nachrüsten lohnt sich für
   einen PvP-Client für 1.21 kaum.
3. **Sprache (U6):** Empfehlung: in dieser Überarbeitung Englisch lassen und Deutsch als eigenes Folgeprojekt umsetzen,
   weil dabei jeder Text angefasst wird.
4. **Auslieferung (B1):** Empfehlung: zusätzlich zur `.jar` eine portable ZIP-Version je Betriebssystem mit eigener
   Java-Laufzeit, kein Installer nötig. Ein Windows-Installer (`.msi`) wäre möglich, braucht aber das WiX-Toolset im
   Build. Eine Code-Signatur braucht ein kostenpflichtiges Zertifikat und ist deshalb nicht Teil des Plans.
5. **Linter (A7):** Empfehlung: Checkstyle, das Gradle schon mitbringt, also kein neues Werkzeug von außen.
