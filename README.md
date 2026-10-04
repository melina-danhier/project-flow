# ProjectFlow v1

ProjectFlow ist ein KI-gestütztes Projektplanungstool für Einzelpersonen und kleine Gruppen. Die serverseitig gerenderte Webanwendung unterstützt bei der strukturierten Erstellung und Verwaltung kleiner privater, studentischer und vergleichbarer Projekte.

Das Projekt entsteht als Prototyp im Rahmen der Bachelorarbeit **„Konzeption und Evaluation eines KI-gestützten Projektplanungstools zur Generierung einfacher Projektstrukturen“**.

Die Abgabeversion trägt die Maven-Version `1.0.0`. Der Git-Tag `bachelor-thesis-final`
ist für den nach Prüfung freigegebenen Commit vorgesehen. Ablauf und Abnahmekriterien
stehen in der [Release-Checkliste](docs/release-checklist.md).

## Ziel und Forschungsfrage

Im Mittelpunkt steht die Frage:

> Wie kann KI-Unterstützung in einem Projektplanungstool eingesetzt werden, sodass die erzeugten Aufgaben und Projektstrukturen für Einzelpersonen und Kleingruppen bei kleineren Projekten hilfreich, verständlich und erwartungskonform sind?

Die KI unterstützt die Planung, handelt aber nicht autonom. Generierte Inhalte werden zunächst als separater Entwurf angezeigt. Nutzerinnen und Nutzer können Vorschläge prüfen, bearbeiten, ablehnen oder ausdrücklich in ihr Projekt übernehmen.

## Funktionsumfang

- Registrierung und Anmeldung
- Projektübersicht mit Suche, Archiv, Papierkorb und angehefteten Projekten
- geführte Projekterstellung als leeres Projekt, aus einer statischen Vorlage oder mit KI-Unterstützung
- an Projektart und Erstellungsweg angepasste Wizard-Fragen
- KI-Vorprüfung von Eingaben und strukturierte Generierung eines Projektplans
- separater Entwurfs- und Reviewprozess vor der Übernahme KI-generierter Inhalte
- Verwaltung und Sortierung von Bereichen, Aufgaben und Meilensteinen
- Aufgaben mit Terminen, Priorität, Aufwand, Status, Abhängigkeiten und Verantwortlichen
- einfache Zusammenarbeit über Projektmitglieder sowie Aufgaben- und Meilensteinkommentare
- lokale KI-Verbesserung einzelner Planelemente sowie überprüfbare Änderungen am Gesamtplan
- Notizen zu Aufgaben und Meilensteinen im Einzelmodus sowie Feedback zu KI-Vorschlägen
- erhaltene Evaluationsfunktionen bei geschlossenem Studieneinstieg

## Technischer Überblick

- Java 21 und Spring Boot 4.1
- Spring MVC, Thymeleaf und Bootstrap
- Spring Security
- Spring Data JPA und Bean Validation
- PostgreSQL und Flyway
- OpenAI- und Gemini-SDK hinter einer anbieterunabhängigen Anwendungsschnittstelle
- Maven, JUnit und Spring-Testunterstützung

Die Anwendung ist fachlich modular aufgebaut. Zentrale Bereiche sind unter anderem Projekterstellung, Projekte und Vorlagen, Planelemente, Entwürfe, KI-Generierung, Feedback, Studie und Benutzerverwaltung. Externe Modellausgaben werden strukturiert verarbeitet und serverseitig validiert, bevor sie gespeichert oder übernommen werden.

## Lokale Entwicklung

### Voraussetzungen

- JDK 21
- lokal installiertes Maven
- PostgreSQL oder Docker mit Docker Compose

Der Maven Wrapper sollte für die lokale Entwicklung wegen eines bekannten Zertifikatsproblems nicht verwendet werden. Die folgenden Befehle verwenden daher `mvn.cmd`. Der isolierte Docker-Build verwendet den Wrapper weiterhin, um eine reproduzierbare Maven-Version bereitzustellen.

### Datenbank starten

Für die mitgelieferte Compose-Konfiguration werden mindestens diese Umgebungsvariablen benötigt:

```powershell
$env:DB_NAME = "projectflow"
$env:DB_USERNAME = "projectflow"
$env:DB_PASSWORD = "lokales-passwort"
docker compose up -d postgres
```

Der Container stellt PostgreSQL auf dem Host-Port `5433` bereit. Alternativ kann eine lokal installierte PostgreSQL-Instanz verwendet werden.

### Anwendung konfigurieren

Das standardmäßig aktive Profil `dev` lädt optional eine nicht eingecheckte `.env`-Datei im Projektverzeichnis. Für die Compose-Datenbank kann sie beispielsweise so aussehen:

```properties
DB_NAME=projectflow
DB_USERNAME=projectflow
DB_PASSWORD=lokales-passwort
DB_PORT=5433
PROJECTFLOW_AI_PROVIDER=stub
```

Flyway führt beim Anwendungsstart alle ausstehenden Migrationen aus. Hibernate validiert anschließend das Schema.

### Anwendung starten

```powershell
mvn.cmd spring-boot:run
```

Anschließend ist ProjectFlow standardmäßig unter [http://localhost:8080](http://localhost:8080) erreichbar. Für die lokale Entwicklung ist der deterministische KI-Stub voreingestellt; ein externer API-Schlüssel ist daher nicht erforderlich.

Das Docker-Image exponiert ebenfalls Port `8080`. Im Produktionsprofil kann der tatsächliche Anwendungsport weiterhin über die Umgebungsvariable `PORT` gesetzt werden.

## KI-Provider

Der aktive Provider wird über `PROJECTFLOW_AI_PROVIDER` gewählt:

| Wert | Zusätzliche Konfiguration | Zweck |
| --- | --- | --- |
| `stub` | keine | lokale Entwicklung und automatisierte Tests |
| `openai` | `OPENAI_API_KEY` | OpenAI Responses API |
| `gemini` | `GEMINI_API_KEY` | Gemini Developer API |

Modellnamen, Timeouts, Ausgabelimits und Wiederholungsversuche können über weitere Umgebungsvariablen angepasst werden. Details stehen in der Dokumentation zu den [KI-Providern](docs/ai-providers.md).

API-Schlüssel und andere Zugangsdaten dürfen nicht eingecheckt werden.

## Tests

Die vollständige Java-Testsuite wird mit der lokal installierten Maven-Version ausgeführt:

```powershell
mvn.cmd test
```

Die automatisierten Tests verwenden Stubs beziehungsweise Test-Doubles und rufen keine externen KI-APIs auf. Die PostgreSQL-Migrationstests benötigen einen laufenden Docker-Daemon; ohne Docker werden sie übersprungen. Die übrigen Integrationstests verwenden H2 und prüfen keine Flyway-Migrationen. Zusätzlich prüfen JavaScript-Tests Projektklassifikation, Datumsauswahl und responsives Drag-and-drop (Node.js erforderlich):

```powershell
node --test src/test/js/date-picker.test.cjs src/test/js/plan-drag.test.cjs src/test/js/project-classification.test.cjs
```

Ein ausführbares Release-JAR entsteht mit `mvn.cmd clean verify` unter
`target/projectflow-1.0.0.jar`. Dieser Befehl führt auch die Java-Tests aus.

## Deployment auf Render

Die Anwendung wird als Docker-Web-Service aus dem mitgelieferten `Dockerfile`
betrieben. Deploymentadresse: [ProjectFlow auf Render](https://project-flow-vqbv.onrender.com/).
Der Container startet das ausführbare JAR; der Build überspringt Tests,
deshalb müssen sie vor der Freigabe separat erfolgreich laufen.

Für den Betrieb sind folgende Umgebungsvariablen zu setzen:

| Variable | Wert/Zweck |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | JDBC-URL der PostgreSQL-Datenbank, z. B. `jdbc:postgresql://host:5432/projectflow` (TLS gemäß Datenbankvorgabe) |
| `DB_USERNAME`, `DB_PASSWORD` | Datenbankzugangsdaten |
| `PROJECTFLOW_AI_PROVIDER` | explizit `openai`, `gemini` oder für eine Demo `stub` |
| `OPENAI_API_KEY` / `GEMINI_API_KEY` | Schlüssel für den gewählten realen Provider |
| `PORT` | von Render vorgegebener Port; Standard `8080` |

Das Produktionsprofil aktiviert sichere Session-Cookies, verarbeitet Forwarded-Header,
bindet auf `0.0.0.0` und validiert nach Flyway das Datenbankschema. Als Health-Check
kann `GET /ping` verwendet werden; dieser prüft die HTTP-Erreichbarkeit, keine
externen KI-Provider. Produktionsdaten und Secrets gehören nicht in das Image oder Git.

Nach einem Deployment sind Anmeldung, Projekterstellung, Entwurfsübernahme und
`/study/start` gemäß [Release-Checkliste](docs/release-checklist.md) zu prüfen.

## Datenbankmigrationen

Änderungen am Datenbankschema erfolgen ausschließlich über neue, versionierte Flyway-Migrationen in `src/main/resources/db/migration`. Hinweise zum Vorgehen und zu PostgreSQL-spezifischen Prüfungen enthält die Dokumentation zu [Datenbankmigrationen](docs/database-migrations.md).

## Abgrenzung

ProjectFlow ist kein vollständiges Unternehmens-Projektmanagementsystem und kein autonomer KI-Projektmanager. Komplexe Rollen- und Rechtekonzepte, organisationsübergreifende Zusammenarbeit, mobile Apps, umfassende Automatisierung und professionelle Portfolioplanung gehören nicht zum Umfang des Prototyps.

## Evaluation

Die begleitende Nutzerstudie ist beendet. Bewertet wurden insbesondere Passgenauigkeit,
Verständlichkeit und Nützlichkeit der erzeugten Projektstrukturen, das Vertrauen in
die KI-Unterstützung sowie die Bedienbarkeit der Anwendung.

`/study` führt zu `/study/start`. Dort erscheint in v1 nur eine kurze Abschlussinfo;
auch ein direkter POST erzeugt keine neue Teilnahme. Die Einstellung
`projectflow.study.enrollment-open` ist standardmäßig `false`. Studienlogik,
Studieninformationen und Migrationen bleiben zur Nachvollziehbarkeit erhalten.
Die Schließung löscht keine vorhandenen Studiendaten.
