# Datenbankmigrationen

Das PostgreSQL-Schema wird ausschließlich über die versionierten Flyway-Skripte in
`src/main/resources/db/migration` geändert. Der aktuelle Stand umfasst die
Migrationen V1 bis V25. Bereits angewendete Migrationen dürfen nicht nachträglich
verändert werden; Schemaänderungen erhalten eine neue Migration.

In den Profilen `dev` und `prod` ist Flyway aktiviert und Hibernate validiert das
resultierende Schema. Vor einer Migration bestehender Daten ist ein geprüftes Backup
erforderlich. Anwendungsversionen mit unterschiedlichen Schemaständen sollen nicht
parallel auf derselben Datenbank betrieben werden.

## Automatisierte Tests

`mvn.cmd test` verwendet H2 und deaktiviert Flyway. Der optionale
`DraftPostgresMigrationTest` führt die Migrationen gegen eine ausdrücklich angegebene
PostgreSQL-Datenbank aus und validiert anschließend das JPA-Schema.

Der Test verändert die angegebene Datenbank. Deshalb darf ausschließlich eine
wegwerfbare, isolierte Testdatenbank verwendet werden:

```powershell
mvn.cmd '-Dtest=DraftPostgresMigrationTest' '-Dprojectflow.test.postgres.url=jdbc:postgresql://127.0.0.1:55432/postgres' '-Dprojectflow.test.postgres.username=projectflow_migration_test' test
```

URL und Benutzer sind lokal anzupassen. Ein erforderliches Passwort kann über die
Umgebungsvariable `PROJECTFLOW_TEST_POSTGRES_PASSWORD` bereitgestellt werden. Ohne
`projectflow.test.postgres.url` wird dieser optionale Test übersprungen. Zugangsdaten
dürfen nicht in das Repository oder die Befehlszeile übernommen werden.

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
