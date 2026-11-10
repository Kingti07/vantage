# Startcheckliste: Vantage 0.2.0

Diese Liste ist für den Tag, an dem Microsoft die Azure-App „Vantage Launcher“ für Minecraft freigibt. Der Code
ist dafür fertig: Die App-ID ist eingebaut, es muss nichts mehr programmiert werden. Plane etwa eine Stunde ein.

## 0. Woran du die Freigabe erkennst
- Microsoft schickt eine E-Mail zu deinem Antrag (Formular „Minecraft Services API – App ID Review“).
- Oder du probierst es: Launcher → **Accounts** → **Add Microsoft account**. Kommt nicht mehr „This Azure application
  is not approved for Minecraft sign-in yet“ und du bist danach angemeldet, ist die App freigegeben.

## 1. Anmeldung und Online-Spiel testen (ca. 15 Minuten)
1. Launcher starten: am besten die portable Version (`Vantage.exe`) oder
   `launcher/build/libs/vantage-launcher-0.2.0-all-win.jar` vom Branch `feature/fps-boost`.
2. **Accounts → Add Microsoft account** → „Copy code & open microsoft.com/link“ → Code eingeben → mit dem
   Microsoft-Konto anmelden, dem Minecraft gehört.
   **Erwartet:** Dein Name und dein Kopf erscheinen unten links.
3. **Play** drücken. **Erwartet:** Minecraft startet mit deinem Namen. Bei einer neuen Installation installiert der
   Launcher vorher automatisch die Leistungs-Mods (Sodium, Lithium, ImmediatelyFast, FerriteCore, EntityCulling). Bei
   einer Installation aus 0.1.0 fragt er einmal, ob er sie installieren soll.
4. Im Spiel **Multiplayer** → einem bekannten Online-Server beitreten.
   **Erwartet:** Du kommst auf den Server, ohne „Invalid session“ oder „Failed to verify username“.
5. Mit zwei Konten: im Spiel **Accounts** → anderes Konto → „Switch & restart“.
   **Erwartet:** Das Spiel startet mit dem anderen Konto neu.
6. Abschnitt 2 in [TESTPLAN.md](TESTPLAN.md) abhaken.

Wenn hier etwas nicht klappt: `<Datenordner>/logs/launcher.log` sichern und **nicht** weitermachen. Der Datenordner
liegt unter `%APPDATA%\.vantage`.

## 2. Code nach `main` übernehmen
1. Auf GitHub den Pull Request `overhaul/launcher → main` öffnen, kurz durchsehen und **Merge** drücken.
2. Einen Pull Request `feature/fps-boost → main` anlegen:
   <https://github.com/Kingti07/vantage/compare/main...feature/fps-boost>. Nach dem ersten Merge zeigt er nur noch die
   FPS-Änderungen. Prüfen, dass die automatische Prüfung grün ist, dann **Merge**.

## 3. Release 0.2.0 veröffentlichen
Die Version 0.2.0 ist schon eingetragen. In `CHANGELOG.md` beim Abschnitt 0.2.0 noch das Datum ergänzen.

Dann den Versions-Tag auf `main` setzen, entweder selbst:
```bash
git checkout main
git pull
git tag v0.2.0
git push origin v0.2.0
```
oder in Claude Code: „Setze den Tag v0.2.0 auf main und pushe ihn.“

Danach:
1. GitHub → **Actions** → „Release“ beobachten. Nach etwa 10–15 Minuten sollte er grün sein.
2. GitHub → **Releases** → „Vantage 0.2.0“ prüfen:
   - 3 portable ZIPs (win, mac-aarch64, linux)
   - 4 Launcher-Jars
   - die Client-Jar
   - `SHA256SUMS.txt`
   - **kein** „Pre-release“-Etikett, weil der Tag keinen Bindestrich hat
3. Den Hinweis „Pre-release: … approval pending“ gibt es in den Release-Notes nicht mehr. Bitte trotzdem einmal
   durchlesen.

## 4. Nach dem Release prüfen
1. Den alten Launcher 0.1.0 starten. **Erwartet:** „Vantage 0.2.0 is available“. „Download update“ drücken, den
   Launcher schließen und neu öffnen. **Erwartet:** Im Fenstertitel steht 0.2.0.
2. Die portable ZIP auf einem PC ohne Java entpacken und starten.
3. Den Rest von [TESTPLAN.md](TESTPLAN.md) durchgehen.

## 5. Falls etwas schiefgeht
- **Release fehlerhaft:** GitHub → Releases → „Vantage 0.2.0“ → **Delete**. Dann `git push --delete origin v0.2.0`.
  Fehler beheben und mit einer neuen Nummer wieder taggen, z. B. `v0.2.1`.
- **Update startet bei jemandem nicht:** Der Launcher fällt von selbst auf die alte Version zurück und bietet das
  Update nicht erneut an. Nutzer verlieren nichts.
- **Erst testen, dann für alle:** Ein Tag mit Bindestrich, z. B. `v0.2.0-beta.1`, wird eine Vorabversion. Die finden
  nur Launcher ab 0.2.0, nicht 0.1.0.
