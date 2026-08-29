# Typsichere Unterkategorien (V17)

`ProjectSubCategory` verwendet die bestehenden Oberkategorien aus `TemplateCategory`.
Die 56 Werte, deutschen Bezeichnungen und ihre Reihenfolge werden zur Laufzeit nur
im Enum gepflegt. `subcategory` ist optional; `OTHER` hat keine Unterkategorie und
verwendet das separate Feld `otherProjectTypeDescription`.

## Migration und Betrieb

V17 trennt das bisher überladene `project_type` in `subcategory` und
`other_project_type_description`, sowohl in `projects` als auch in `plan_templates`.
Deutsche Enum-Bezeichnungen und technische Konstanten werden unabhängig von
Groß-/Kleinschreibung und umgebenden Leerzeichen zugeordnet, ausschließlich in der
passenden Oberkategorie. Zusätzlich sind die bisherigen Bezeichnungen
`Präsentation`, `Referat`, `Gruppenpräsentation`, `Bachelorarbeit`, `Masterarbeit`,
`Hausarbeit` und `Seminararbeit` berücksichtigt. Leere Werte bleiben ohne Unterkategorie.

Unbekannte oder zur Kategorie unpassende nichtleere Werte werden nicht geraten:
Die ursprünglichen Texte, Kategorie und Datensatz-ID bleiben in
`project_subcategory_migration_issues` erhalten, während `subcategory` NULL bleibt.
Diese Tabelle ist nur zur manuellen Nachbearbeitung bestimmt, wird nicht an die KI
übermittelt und sollte nach abgeschlossener Klärung entsprechend der lokalen
Datenaufbewahrung bereinigt werden. Prüfabfrage:

```sql
SELECT source_table, source_id, category, legacy_value
FROM project_subcategory_migration_issues
ORDER BY source_table, source_id;
```

Gespeicherte `confirmed_snapshot`-JSONB-Daten werden ebenfalls umgestellt und als
`ai-wizard-v3` gekennzeichnet; ältere, zusätzlich als JSON-String verpackte Objekte
werden unterstützt. Unerwartete JSON-Grundformen brechen die Migration ab, statt
Daten zu überschreiben. Nicht zuordenbare Snapshot-Werte müssen vor erneuter
Generierung fachlich geprüft werden; die Migration ersetzt sie durch NULL.

Die Spaltenumbenennung erfordert einen koordinierten Neustart, keine parallele
Ausführung alter und neuer Anwendungsversionen. Laufende Wizard-Sessions werden
nicht aus dem früheren String-Format übernommen (neue Serialisierungs-Version);
gespeicherte KI-Snapshots bleiben dagegen über die Migration wiederherstellbar.
Die produktive/lokale Projektdatenbank wird durch die Tests nicht migriert.

## Verifikation

`mvn.cmd test` prüft Formulare, Servicegrenzen, MVC-Bindung, gespeicherte Auswahl,
Wizard-/Snapshot-Wiederherstellung, Enum-Persistenz und Vorlagenzuordnung.
`node --test src/test/js/project-classification.test.cjs` prüft Kategorienwechsel
und das Zurücksetzen der Auswahl ohne zusätzliche Frontend-Abhängigkeiten.

`DraftPostgresMigrationTest` ist nur bei expliziter Angabe von
`projectflow.test.postgres.url` aktiviert. Ausschließlich eine wegwerfbare
Testdatenbank verwenden: Der Anwendungskontext migriert deren Standardschema.
Der zusätzliche V16→V17-Test verwendet ein eigenes temporäres Schema und prüft
alle 56 Werte als deutsche Bezeichnung und technische Konstante, Legacy-Aliase,
unbekannte und widersprüchliche Werte sowie normale/verpackte JSONB-Snapshots.
