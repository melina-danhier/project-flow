# KI-Provider

## Auswahl

`PROJECTFLOW_AI_PROVIDER` wählt den aktiven Provider. Unterstützt werden `stub`,
`openai` und `gemini`; der Standardwert ist `stub`. Ein unbekannter Wert verhindert
den Anwendungsstart.

| Provider | Erforderlich | Standardmodelle |
| --- | --- | --- |
| `stub` | kein Schlüssel | lokale, deterministische Antworten |
| `openai` | `OPENAI_API_KEY` | `gpt-5-mini` |
| `gemini` | `GEMINI_API_KEY` | `gemini-2.5-flash` |

Der Stub ist für lokale Entwicklung und automatisierte Tests vorgesehen. Seine
Szenarien werden im Entwicklungsprofil über `projectflow.ai.stub.*` gesetzt:

- `projectflow.ai.stub.pre-check-scenario`:
  - `critical-assumption` (Standard im Entwicklungsprofil): Kritische Annahme mit konkretem maschinenlesbaren Änderungsvorschlag (`proposedInputChanges`), der direkt im Wizard übernommen werden kann.
  - `no-problems`: Vorprüfung ohne gefundene Probleme (überspringt den Review-Schritt direkt zur Generierung).
  - `risk` / `warning`: Fachliches oder organisatorisches Planungsrisiko (Severity `WARNING`, keine Eingabeänderung).
  - `assumption`: Planungsrelevante Annahme/Grundlage (Severity `WARNING`, keine Eingabeänderung).
  - `conflict` / `error`: Blockierender Ziel- oder Rahmenbedingungskonflikt (Severity `ERROR`).
  - `multiple-warnings`: Kombination aus Risiko, Annahme und kritischer Annahme zur gemeinsamen Überprüfung im UI.
  - `multiple-issues`: Kombination aus Warnung und blockierendem Fehler.
  - `dynamic`: Automatische Erkennung passender Hinweise anhand von Schlüsselwörtern in Titel, Ziel oder Rahmenbedingungen.

- `projectflow.ai.stub.generation-scenario`:
  - `with-dates` (Standard): Vollständiger Projektplan mit passgenauen Terminen innerhalb des Projektzeitraums, Aufwandsschätzungen in Minuten, Aufgabenprioritäten und Abhängigkeiten. Bildet für Umzug und Bildung/Präsentation spezifische Szenarien ab.
  - `without-dates`: Planstruktur ohne feste Datumsbindung.


## Konfiguration realer Provider

Die folgenden optionalen Umgebungsvariablen überschreiben die Standardwerte:

| OpenAI | Gemini | Bedeutung |
| --- | --- | --- |
| `OPENAI_PRE_CHECK_MODEL` | `GEMINI_PRE_CHECK_MODEL` | Modell für die Vorprüfung |
| `OPENAI_GENERATION_MODEL` | `GEMINI_GENERATION_MODEL` | Modell für die Plangenerierung |
| `OPENAI_TIMEOUT` | `GEMINI_TIMEOUT` | Zeitlimit, standardmäßig `90s` |
| `OPENAI_MAX_OUTPUT_TOKENS` | `GEMINI_MAX_OUTPUT_TOKENS` | Ausgabelimit, standardmäßig `16384` |

Zusätzlich steuern `PROJECTFLOW_AI_MAX_ATTEMPTS`,
`PROJECTFLOW_AI_RETRY_INITIAL_DELAY`, `AI_STALE_WORKFLOW_TIMEOUT`,
`PROJECTFLOW_AI_MAX_RUN_TIME` und `AI_RECOVERY_CRON` die anbieterunabhängige
Ausführung. Die Werte und Standardwerte sind in `application.yml` definiert.

Im Produktivprofil ist periodische Recovery standardmäßig deaktiviert (`-`);
die Recovery beim Start bleibt aktiv. `AI_RECOVERY_CRON` kann bei Bedarf einen
Cron-Ausdruck setzen. Leere Providerwerte aktivieren einen Fehleradapter;
für ein Deployment den Provider ausdrücklich setzen.

API-Schlüssel gehören ausschließlich in Umgebungsvariablen oder eine ignorierte
lokale Konfiguration. Sie dürfen nicht eingecheckt werden.

## Verarbeitung und Fehlerverhalten

Beide realen Provider verwenden strukturierte Ausgaben. Providerantworten werden
geparst und anschließend unabhängig vom Provider serverseitig validiert. Ungültige,
unvollständige oder unerwartete Antworten werden nicht in einen Projektplan
übernommen.

Der Workflow speichert den bestätigten Eingabestand und die tatsächlich verwendeten
Prompt-, Schema- und Modellversionen. Provideraufrufe laufen außerhalb langer
Datenbanktransaktionen. Ein validiertes Ergebnis wird zunächst als separater Entwurf
materialisiert; die Übernahme in das Projekt erfolgt in einem eigenen bestätigten
Schritt.

Automatische Wiederholungen sind auf vorübergehende Transport-, Timeout- und
Rate-Limit-Fehler begrenzt. Konfigurationsfehler, Ablehnungen und ungültige Antworten
werden nicht automatisch wiederholt. SDK-eigene Retries sind deaktiviert, damit die
Anwendung Versuchszahl und Backoff zentral steuert.

## Datenschutz und Anbieterbedingungen

ProjectFlow überträgt fachliche Planungsdaten: Wizard-Angaben bei der Erstellung
und bei Änderungen zusätzlich den notwendigen Plan-/Elementkontext sowie den
Änderungswunsch. Interne Konto-, Benutzer- und Studienkennungen werden nicht als
Metadaten an den Provider gesendet. Freitext kann trotzdem personenbezogene Daten
enthalten; die Anwendung anonymisiert ihn nicht automatisch. Die Wizard-Zusammenfassung
zeigt die Angaben zur erstmaligen Planerstellung, keine allgemeine Datenvorschau
für jeden späteren KI-Aufruf.

Der OpenAI-Adapter setzt bei Responses-Aufrufen `store(false)`. Das verhindert die
reguläre Speicherung als abrufbarer Response-Zustand, ist aber keine Zusage einer
vollständigen Nullaufbewahrung: gesonderte Aufbewahrung für Missbrauchsüberwachung
und weitere Bedingungen können gelten. Die API verwendet Daten standardmäßig nicht
zum Training, sofern keine freiwillige Freigabe erfolgt. Maßgeblich sind die
[offiziellen OpenAI-Datenkontrollen](https://developers.openai.com/api/docs/guides/your-data).

Für Gemini sind bezahlte und unbezahlte Dienste sowie die jeweils anwendbaren
regionalen Bedingungen zu unterscheiden. Für den Produktivbetrieb ist ein Konto
mit aktivierter Abrechnung vorgesehen. Auch bei bezahlten Diensten kann begrenzte
Aufbewahrung zur Missbrauchserkennung gelten. Maßgeblich sind die
[Gemini-API-Bedingungen](https://ai.google.dev/gemini-api/terms).

Providerkonto, Datenfreigaben, Aufbewahrung und erforderliche vertragliche Vereinbarungen
müssen für das tatsächlich verwendete Konto geprüft werden. Der Code bestätigt weder
Kontoeinstellungen noch abgeschlossene Verträge oder eine pauschale DSGVO-Konformität.
Für technische Tests ausschließlich fiktive, freigegebene Planungsdaten verwenden.
Die Anbieterquellen wurden für die Release-Dokumentation am 04.10.2026 geprüft.

## Tests

Die automatisierten Provider-Tests verwenden Stubs beziehungsweise Test-Doubles und
rufen keine externen APIs auf. Ein realer Providerzugang, Kontingente und die
Akzeptanz eines Schemas durch ein konkret konfiguriertes Modell müssen bei Bedarf
separat mit freigegebenen Testdaten geprüft werden.
