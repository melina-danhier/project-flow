# Datenbankmigrationen

Das PostgreSQL-Schema wird ausschließlich über die versionierten Flyway-Skripte in
`src/main/resources/db/migration` geändert. Die vor der Evaluation konsolidierte
Baseline umfasst die Migrationen V1 bis V5. Die Abgabeversion enthält zusätzlich
V6 bis V10 und damit insgesamt zehn Migrationen. V6 entkoppelt Studiensitzungen,
V7 ergänzt Unterkategorien, V8 die geplante Projektdauer, V9 wandelt Aufwand in
Minuten um und V10 ergänzt kaskadierendes Löschen von Entwürfen und Workflows.
Eine leere Datenbank erhält alle zehn Migrationen. Datenbanken mit der früheren
Entwicklungshistorie V1 bis V42 benötigen eine separat geplante Datenübernahme;
benötigte Daten dürfen nicht durch ungeprüftes Löschen verloren gehen.

Ab dieser Baseline dürfen angewendete Migrationen nicht nachträglich verändert
werden; Schemaänderungen erhalten eine neue Migration.

In den Profilen `dev` und `prod` ist Flyway aktiviert und Hibernate validiert das
resultierende Schema. Vor einer Migration bestehender Daten ist ein geprüftes Backup
erforderlich. Anwendungsversionen mit unterschiedlichen Schemaständen sollen nicht
parallel auf derselben Datenbank betrieben werden.

## Automatisierte Tests

Die meisten Integrationstests verwenden H2 und deaktivieren Flyway.
`BaselinePostgresMigrationTest` startet dagegen mit Testcontainers selbst eine isolierte
PostgreSQL-16-Instanz, führt V1–V10 aus und validiert anschließend das JPA-Schema.
Er prüft außerdem 26 Anwendungstabellen und die statische Projektvorlage.

Für den Test ist ein laufender Docker-Daemon erforderlich:

```powershell
mvn.cmd '-Dtest=BaselinePostgresMigrationTest' test
```

Ohne Docker werden die beiden Testfälle übersprungen. Der Test nimmt keine externe
Datenbank-URL entgegen. Die Compose-Konfiguration verwendet PostgreSQL 18; für dieses
Ziel zusätzlich den Anwendungsstart gegen eine isolierte leere PostgreSQL-18-Datenbank
prüfen. Dafür das Produktivprofil mit KI-Stub verwenden und erfolgreiche Migrationen
sowie Hibernate-Validierung kontrollieren. Niemals produktive Datenbanken dafür nutzen.
Zugangsdaten dürfen nicht in Git oder die Befehlszeile übernommen werden.

## Deployment

Vor einem Deployment mit neuen Migrationen sind mindestens diese Schritte nötig:

1. Migrationen auf einer leeren PostgreSQL-Testdatenbank ausführen.
2. Bei relevanten Bestandsdaten zusätzlich eine anonymisierte oder freigegebene
   Datenkopie prüfen.
3. Backup und Wiederherstellung testen.
4. Anwendung und schreibende Worker während inkompatibler Schemaänderungen anhalten.
5. Nach der Migration den Anwendungsstart mit Hibernate-Validierung und den
   betroffenen Geschäftsablauf prüfen.

Besondere Datenkorrekturen oder einmalige Prüfungen gehören in die Beschreibung der
jeweiligen neuen Migration beziehungsweise in den Deployment-Plan der betroffenen
Version, nicht dauerhaft in diese allgemeine Projektübersicht.
