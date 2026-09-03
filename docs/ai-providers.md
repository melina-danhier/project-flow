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
Szenarien werden im Entwicklungsprofil über `projectflow.ai.stub.*` gesetzt.

## Konfiguration realer Provider

Die folgenden optionalen Umgebungsvariablen überschreiben die Standardwerte:

| OpenAI | Gemini | Bedeutung |
| --- | --- | --- |
| `OPENAI_PRE_CHECK_MODEL` | `GEMINI_PRE_CHECK_MODEL` | Modell für die Vorprüfung |
| `OPENAI_GENERATION_MODEL` | `GEMINI_GENERATION_MODEL` | Modell für die Plangenerierung |
| `OPENAI_TIMEOUT` | `GEMINI_TIMEOUT` | Zeitlimit, standardmäßig `60s` |
| `OPENAI_MAX_OUTPUT_TOKENS` | `GEMINI_MAX_OUTPUT_TOKENS` | Ausgabelimit, standardmäßig `16384` |

Zusätzlich steuern `PROJECTFLOW_AI_MAX_ATTEMPTS`,
`PROJECTFLOW_AI_RETRY_INITIAL_DELAY`, `AI_STALE_WORKFLOW_TIMEOUT`,
`PROJECTFLOW_AI_MAX_RUN_TIME` und `AI_RECOVERY_DELAY` die anbieterunabhängige
Ausführung. Die Werte und Standardwerte sind in `application.yml` definiert.

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

## Tests

Die automatisierten Provider-Tests verwenden Stubs beziehungsweise Test-Doubles und
rufen keine externen APIs auf. Ein realer Providerzugang, Kontingente und die
Akzeptanz eines Schemas durch ein konkret konfiguriertes Modell müssen bei Bedarf
separat mit freigegebenen Testdaten geprüft werden.
