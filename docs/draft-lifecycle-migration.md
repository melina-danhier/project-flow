# Draft-Lebenszyklus und Migration V16

## Transaktionsgrenzen

Der Generierungsworkflow wird vor dem KI-Aufruf gespeichert und atomar beansprucht.
Der Koordinator und das Mapping in `AiGenerationWorkflowService.recordSuccess`
laufen mit `NOT_SUPPORTED`: Ein eventuell vorhandener Transaktionskontext wird
bewusst suspendiert. Provider-Aufruf und Mapping halten keine Datenbanktransaktion offen.
Diese Einstiegspunkte sind keine Bestandteile einer übergeordneten atomaren Geschäftsoperation.

`PlanDraftMaterializationService.materialize` verwendet normales `@Transactional`
(`REQUIRED`). Im vorgesehenen Ablauf beginnt dort die kurze Transaktion: Workflow
sperren, Status und vorhandenen Draft prüfen, vollständigen Graphen und beide
Erfolgsstatus speichern. Bei einem direkten Aufruf aus einer bestehenden Transaktion
entscheidet dagegen deren Commit/Rollback auch über den Draft.

Der Koordinator behandelt Speicherfehler erst nach dem Rollback. Die Fehlerstatusmethoden
behalten `REQUIRES_NEW`, damit der Workflowfehler unabhängig gespeichert wird.
Ein fehlgeschlagener Versuch hinterlässt keinen Draft. Bestehende Retry-Regeln und
der bestätigte Snapshot bleiben unverändert.

## Vorprüfung vorhandener Daten (Schema bis V15)

1. Anwendung und Generierungsworker anhalten; keine parallelen Schreibzugriffe zulassen.
2. Vollständiges Datenbankbackup erstellen und dessen Wiederherstellbarkeit prüfen.
   Das Backup umfasst auch die durch V16 entfernten Draft-Metadaten.
3. Auf einer Kopie des Bestands zunächst folgende rein lesende Abfrage ausführen:

```sql
SELECT d.id AS draft_id, d.project_id, d.status AS draft_status,
       w.id AS workflow_id, w.status AS workflow_status,
       w.last_technical_error,
       (SELECT count(*) FROM draft_sections s
        WHERE s.plan_draft_id = d.id) AS sections,
       (SELECT count(*) FROM draft_plan_elements e
        WHERE e.plan_draft_id = d.id) AS elements,
       (SELECT count(*) FROM draft_tasks t
        JOIN draft_plan_elements e ON e.id = t.id
        WHERE e.plan_draft_id = d.id) AS tasks,
       (SELECT count(*) FROM draft_milestones m
        JOIN draft_plan_elements e ON e.id = m.id
        WHERE e.plan_draft_id = d.id) AS milestones,
       (SELECT count(*) FROM draft_task_prerequisites p
        JOIN draft_plan_elements e ON e.id = p.successor_draft_task_id
        WHERE e.plan_draft_id = d.id) AS dependencies
FROM plan_drafts d
LEFT JOIN ai_plan_generation_workflows w ON w.project_id = d.project_id
ORDER BY d.project_id;
```

Besonders zu prüfen sind `GENERATING`/`FAILED` mit Inhalten, fehlende Workflows
sowie Widersprüche zwischen sichtbarem Draft und Workflowstatus.

## Sichere Bereinigung

- **Leere Platzhalter in GENERATING/FAILED:** V16 entfernt sie selbst, sofern weder
  Phasen noch Elemente existieren. Projekt, Workflow und Snapshot bleiben erhalten.
- **Bereits gültige sichtbare Drafts:** V16 erhält deren Graphen und Reviewstatus.
  Die vorhandene Modellangabe wird in den Workflow übertragen. Prompt-/Schemaversionen,
  Fehler und Versuchszähler im Workflow bleiben maßgeblich und werden nicht überschrieben.
- **Befüllte Drafts in GENERATING/FAILED:** Deployment stoppen und jeden Fall anhand
  der konkreten Draft-ID fachlich prüfen. V16 bricht bei diesen Daten am neuen
  Status-Constraint ab; auf PostgreSQL wird die gesamte Migration zurückgerollt.
  Niemals pauschal auf `READY_FOR_REVIEW` setzen, nur um die Migration auszuführen.

Ist ein solcher Alt-Draft nach erneuter vollständiger Struktur- und Fachvalidierung
nachweislich vollständig, dürfen ein Administrator und der fachlich Verantwortliche
seine Wiederherstellung freigeben. Draft-Reviewstatus und Workflow-Erfolgsstatus müssen
dann zusammen in einer Transaktion konsistent gesetzt werden; Snapshot unverändert lassen.

Ist der Graph unvollständig oder nicht mehr verwendbar, zuerst einschließlich Metadaten
exportieren und die Verwerfung ausdrücklich freigeben lassen. Die Bereinigung erfolgt
gezielt für diese Draft-ID in einer Transaktion: eingehende und ausgehende
`draft_task_prerequisites`, zugehörige `draft_tasks`/`draft_milestones`,
`draft_plan_elements`, `draft_sections`, schließlich `plan_drafts`.
Unerwartete Verweise aus anderen Drafts müssen vor einer Löschung untersucht werden.
Aktive Plan-Tabellen, Projekt, Owner und bestätigten Snapshot nicht löschen.
Den zugehörigen Workflowfehler konsistent dokumentieren; die erneute Generierung
erfolgt erst nach der Migration über einen zulässigen Workflow-Retry.

Danach die Vorprüfung wiederholen, V16 zunächst auf der Datenkopie und anschließend
im Wartungsfenster ausführen. Bei Fehlern Ursache beheben und regulär erneut migrieren;
weder alte Migrationen umschreiben noch die fehlgeschlagene Migration als erfolgreich markieren.

## PostgreSQL-Prüfung vor Deployment

Nur eine ausdrücklich bereitgestellte, wegwerfbare Testdatenbank verwenden.
Die Tests schreiben Daten; niemals Entwicklungs- oder Produktivdaten als Testziel angeben.
Die folgende PowerShell-Zeile führt sowohl Flyway/Hibernate-Validierung als auch
Materialisierung, Rollback, äußeres Rollback und parallele Abschlüsse gegen PostgreSQL aus:

```powershell
mvn.cmd '-Dtest=DraftPostgresMigrationTest,PlanDraftMaterializationIntegrationTest' '-Dprojectflow.test.postgres.url=jdbc:postgresql://127.0.0.1:55432/postgres' '-Dprojectflow.test.postgres.username=draft_review_test' '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55432/postgres' '-Dspring.datasource.username=draft_review_test' '-Dspring.datasource.driver-class-name=org.postgresql.Driver' '-Dspring.flyway.enabled=true' '-Dspring.jpa.hibernate.ddl-auto=validate' test
```

URL und Benutzer an den isolierten Testcluster anpassen. Falls ein Passwort benötigt
wird, lokal über `SPRING_DATASOURCE_PASSWORD` und `PROJECTFLOW_TEST_POSTGRES_PASSWORD`
bereitstellen; keine Secrets in Repository oder Shell-Befehlszeilen schreiben.
Ohne die Property `projectflow.test.postgres.url` wird der optionale PostgreSQL-Test
übersprungen. Der normale Lauf `mvn.cmd test` verwendet weiterhin H2.

Prüfstand 28.08.2026: Gegen einen isolierten lokalen PostgreSQL-18.1-Cluster wurden
alle 16 Flyway-Migrationen auf leerer Datenbank sowie 13 Migrations- und
Materialisierungstests erfolgreich ausgeführt, ohne übersprungene Tests.
Der Cluster wurde danach gestoppt. Das ersetzt nicht die Vorprüfung einer Kopie
der tatsächlich zu migrierenden Bestandsdaten.
