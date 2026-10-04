# ProjectFlow v1 – Freigabecheckliste

Diese Checkliste beschreibt die Abgabeversion `1.0.0`. Testergebnisse und offene Punkte
werden im separaten Release-Arbeitsplan außerhalb des Repositorys festgehalten.

## Lokale Abnahme

- Sauberen Git-Ausgangsstand prüfen; keine Secrets, Studiendaten, Exporte oder Build-Artefakte committen.
- `mvn.cmd clean verify` erfolgreich ausführen; übersprungene Tests ausdrücklich prüfen.
- `node --test src/test/js/date-picker.test.cjs src/test/js/plan-drag.test.cjs src/test/js/project-classification.test.cjs` ausführen.
- JavaScript-Syntax und `git diff --check` prüfen.
- V1–V10 auf einer isolierten leeren PostgreSQL-Datenbank ausführen und JPA-Schema validieren.
- Desktop und mobile Ansicht prüfen: Home, Anmeldung, Registrierung, geschlossene Studie.
- Kernabläufe prüfen: leeres Projekt, Vorlage, KI-Stub, Entwurf bearbeiten/verwerfen/übernehmen,
  Aufgaben/Bereiche/Meilensteine, Planansichten, Archiv und einfache Zusammenarbeit.
- Berechtigungs-, KI-Validierungs- und Entwurfsregressionstests müssen erfolgreich sein.

## Betrieb

- Render: Dockerfile, `SPRING_PROFILES_ACTIVE=prod`, PostgreSQL-Zugang und Provider explizit konfigurieren.
- Vor Zugriff mit echten Planungsdaten Providerkonto, Datenverarbeitung und Kontingente prüfen;
  reale Provideraufrufe nur mit freigegebenen Testdaten testen.
- Nach Deployment: `/ping`, Anmeldung und ein kleines Projekt einschließlich Entwurfsübernahme prüfen.
- `/study/start` muss die Abschlussinfo zeigen; ein alter Teilnahme-POST darf keine neue Einschreibung erzeugen.
- Kein Override von `projectflow.study.enrollment-open` auf `true` für die Abgabeversion.
- Bestehende Studiendaten unabhängig vom Release nach dem vereinbarten Aufbewahrungskonzept behandeln.

## Git-Freigabe

1. Offene Rückfragen klären; betroffene Änderungen vorher nicht committen.
2. Geprüfte Änderungen in thematischen Conventional Commits speichern und finalen Diff prüfen.
3. Commit und verbleibende Einschränkungen zur ausdrücklichen Freigabe von Tag und Push vorlegen.
4. Erst nach dieser Freigabe den annotierten Tag `bachelor-thesis-final` auf den geprüften Commit setzen.
5. Branch und Tag ausdrücklich pushen; bestehende Tags nicht überschreiben und keinen Force-Push verwenden.

Ein bestandener H2-Testlauf ersetzt keine PostgreSQL-Prüfung. Ein lokaler Build ersetzt
keinen Docker-Build oder Smoke-Test auf Render. Externe KI-Provider sind in der
automatisierten Testsuite durch Stubs/Test-Doubles ersetzt.
