# Reproduktionsstand – ProjectFlow

Stand: 04.10.2026. Technischer Prototyp zur Bachelorarbeit; Anwendungsversion `1.0.0`.

## Referenzstand

- Finaler Release-Commit: `c6963312c8b0117eda66a13b9fb0230bf960fa90`.
- Annotierter Git-Tag: `bachelor-thesis-final` (Commit-Ziel geprüft).
- Die Dokumentation wurde nach dem Release erstellt und beschreibt diesen Codezustand.

## Laufzeit und Werkzeuge

| Komponente | Dokumentierter Stand |
| --- | --- |
| Java | JDK 21 erforderlich; lokal Oracle JDK `21.0.9`, Build `21.0.9+7-LTS-338` |
| Spring Boot | `4.1.0` gemäß `pom.xml` |
| Maven | lokal `3.9.11`; Docker-Build über Maven Wrapper `3.9.16` |
| PostgreSQL | Compose: `postgres:18.4-alpine`; manueller Release-Start: `18.1`; Migrationstests: `postgres:16-alpine` |
| Docker | CLI/Engine `29.8.1`; Compose `v5.5.1` |
| Docker-Java | Build: `eclipse-temurin:21-jdk-alpine`; Laufzeit: `eclipse-temurin:21-jre-alpine` |
| Node.js | lokal `v24.2.0`, für JavaScript-Tests |
| Abschließender Testbrowser | Mozilla Firefox `157.0` (64-Bit), Nutzerangabe |

Benötigt werden die Java-Anwendung und PostgreSQL. Docker Compose enthält nur den
Service `postgres` (Host-Port `5433`, Container-Port `5432`, Volume `projectflow_data`).
Docker wird außerdem für Testcontainers benötigt. Reale KI-Funktionen benötigen den
gewählten externen Provider; SMTP ist nur für optionale Studien-Abbruchmeldungen nötig.
Deployment: Docker-Web-Service auf Render, externe PostgreSQL-Datenbank auf Neon;
deren genaue Serverversion wurde hier nicht verifiziert. Flyway umfasst V1–V10.

## KI-Provider und Versionen

| Provider | Modellbezeichnungen und Nachweis |
| --- | --- |
| OpenAI (Responses API) | lokale Konfiguration: `gpt-5.6-terra` für Vorprüfung und Generierung; Repository-Standard: `gpt-5-mini` |
| Google Gemini (Developer API) | Repository-Standard: `gemini-2.5-flash` für Vorprüfung und Generierung |
| Stub | lokale deterministische Testantworten, kein externes Modell |

Lokal ist OpenAI ausgewählt. SDK-Versionen: OpenAI Java `4.39.1`, Google GenAI
`1.64.0`. Die tatsächlich während der Studie bzw. auf Render verwendeten Modelle
sind durch die lokale Konfiguration nicht belegt; maßgeblich sind die gespeicherten
Workflow-Metadaten und die damalige Deploymentkonfiguration.

| Funktion | Prompt-Version | Schema-Version |
| --- | --- | --- |
| Vorprüfung | `precheck-v20` | `precheck-schema-v7` |
| Plangenerierung | `generation-v11` | `generation-schema-v4` |
| Elementverbesserung | `element-improvement-v11` | `element-improvement-schema-v6` |
| Planänderung | `plan-change-v5` | `plan-change-schema-v3` |

Quellen: `AiPromptVersions.java`, `AiSchemaVersions.java`, `pom.xml` und
`src/main/resources/application*.yml`.

## Umgebungsvariablen (ausschließlich Namen)

- Lokale Datenbank / Compose: `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_PORT`.
- Produktivbetrieb: `SPRING_PROFILES_ACTIVE`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `PORT`, `PROJECTFLOW_AI_PROVIDER`.
- Reale Provider, je nach Auswahl: `OPENAI_API_KEY`, `GEMINI_API_KEY`.
- Optionale Modell-/Providersteuerung: `OPENAI_PRE_CHECK_MODEL`, `OPENAI_GENERATION_MODEL`, `OPENAI_TIMEOUT`, `OPENAI_MAX_OUTPUT_TOKENS`, `GEMINI_PRE_CHECK_MODEL`, `GEMINI_GENERATION_MODEL`, `GEMINI_TIMEOUT`, `GEMINI_MAX_OUTPUT_TOKENS`.
- Optionale KI-Ausführung: `PROJECTFLOW_AI_MAX_ATTEMPTS`, `PROJECTFLOW_AI_RETRY_INITIAL_DELAY`, `PROJECTFLOW_AI_GENERATION_TEMPERATURE`, `PROJECTFLOW_AI_MAX_RUN_TIME`, `AI_RECOVERY_CRON`, `AI_STALE_WORKFLOW_TIMEOUT`, `PROJECTFLOW_AI_STUB_PRE_CHECK_SCENARIO`.
- Optionale Entwicklung / Studie / Mail: `SHOW_SQL`, `PROJECTFLOW_STUDY_QUESTIONNAIRE_URL`, `STUDY_ABORT_MAIL_TO`, `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`.

Das Entwicklungsprofil lädt optional eine lokale, ignorierte `.env`-Datei.
Für Compose muss `DB_PORT` zum oben genannten Host-Port passen. Das Produktivprofil
benötigt eine externe Datenbankverbindung und HTTPS wegen sicherer Session-Cookies.

## Starten und testen

Im Repositoryverzeichnis ausführen, nachdem die benötigte Konfiguration gesetzt ist.
Standardprofil für den lokalen Start: `dev`; Standardprovider ohne Override: Stub.

```powershell
# Referenzstand prüfen
git rev-parse 'bachelor-thesis-final^{commit}'

# Datenbank und lokale Anwendung starten
docker compose up -d postgres
mvn.cmd spring-boot:run

# Automatisierte Tests
mvn.cmd test
node --test src/test/js/date-picker.test.cjs src/test/js/plan-drag.test.cjs src/test/js/project-classification.test.cjs
mvn.cmd '-Dtest=BaselinePostgresMigrationTest' test

# Release-JAR bauen und mit vorbereiteter Konfiguration starten
mvn.cmd clean verify
java -jar target/projectflow-1.0.0.jar

# Anwendungsimage bauen; Datenbankverbindung separat konfigurieren
docker build -t projectflow:reproduction .

# Compose-Datenbank stoppen, Daten erhalten
docker compose stop postgres
```

Unter Linux/macOS entsprechend `mvn` verwenden. Lokal keinen Maven Wrapper verwenden
(bekanntes Zertifikatsproblem); der Docker-Build nutzt ihn weiterhin. Lokale Anwendung:
`http://localhost:8080`, HTTP-Erreichbarkeitsprüfung: `/ping`.

## Prüfstand und Einschränkungen

- Laut Releaseprotokoll vom 04.10.2026: 631 Java-Testfälle im vollständigen Lauf erfolgreich; die zwei zunächst übersprungenen PostgreSQL-Testfälle anschließend separat mit Docker erfolgreich. Sieben JavaScript-Tests und Docker-Image-Build erfolgreich. Diese Läufe wurden für diese Dokumentation nicht wiederholt.
- Manueller Release-Start mit PostgreSQL 18.1: Migrationen V1–V10 und Hibernate-Schemavalidierung erfolgreich. Browserprüfung der geänderten öffentlichen Seiten auf Desktop und bei 390 × 844 Pixel; manuelle Nachkontrolle vom Nutzer bestätigt. Kein zusätzlicher vollständiger manueller Durchlauf aller authentifizierten Ansichten.
- H2-Tests prüfen keine Flyway-Migrationen. Ohne Docker werden PostgreSQL-Testcontainerfälle übersprungen; PostgreSQL 16 im Test ersetzt keine Prüfung der Compose-Version 18.4.
- Automatisierte KI-Tests verwenden Stubs/Test-Doubles. Reale KI- und Render-Prüfungen stützen sich auf frühere Tests und den Studieneinsatz, vom Nutzer bestätigt; keine erneuten externen Provideraufrufe im Release-Lauf.
- Externe Modelle und Modell-Aliase können sich ändern; identische Eingaben garantieren keine identischen KI-Ergebnisse. Historische Modell-/Prompt-/Schema-Metadaten müssen für die konkrete Reproduktion herangezogen werden.
- Docker-Basisimages sind nicht über Digests fixiert; PostgreSQL-Testimage und Java-Images können sich unter demselben Tag ändern. Der Docker-Build überspringt Tests; kein Smoke-Test des laufenden Release-Images dokumentiert.
- Maven kann in der Sandbox wegen Cache-/Zertifikatszugriff scheitern. Der Release-Test lief erfolgreich mit vorhandenem Benutzer-Cache; kein TLS-Bypass.
- Frühere Entwicklungsschemata V1–V42 benötigen eine gesonderte Datenübernahme. Der Studieneinstieg ist in der Abgabeversion geschlossen.
