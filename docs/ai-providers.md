# KI-Provider

## Auswahl und Konfiguration

`projectflow.ai.provider` wählt genau einen `AiClient`: `stub`, `openai` oder `gemini`.
Die Umgebungsvariable dafür lautet `PROJECTFLOW_AI_PROVIDER`. Der Standard und das
Testprofil verwenden `stub`. Auch im Entwicklungsprofil lässt sich der Provider per
Umgebungsvariable wechseln. Ein unbekannter Wert verhindert den Start; ein explizit
leerer Wert behält den bisherigen Client mit Konfigurationsfehler bei.

| Provider | Schlüssel | Standardmodell | Weitere Umgebungsvariablen |
| --- | --- | --- | --- |
| `stub` | keiner | deterministische DTOs | Szenarien über `projectflow.ai.stub.*` |
| `openai` | `OPENAI_API_KEY` | `gpt-5-mini` | `OPENAI_PRE_CHECK_MODEL`, `OPENAI_GENERATION_MODEL`, `OPENAI_TIMEOUT`, `OPENAI_MAX_OUTPUT_TOKENS` |
| `gemini` | `GEMINI_API_KEY` | `gemini-2.5-flash` | `GEMINI_PRE_CHECK_MODEL`, `GEMINI_GENERATION_MODEL`, `GEMINI_TIMEOUT`, `GEMINI_MAX_OUTPUT_TOKENS` |

Beispiel ohne Secrets, alternativ zu den Umgebungsvariablen:

```yaml
projectflow:
  ai:
    provider: gemini
    gemini:
      api-key: ${GEMINI_API_KEY}
      pre-check-model: gemini-2.5-flash
      generation-model: gemini-2.5-flash
      timeout: 60s
      max-output-tokens: 16384
```

Für beide realen Provider gelten standardmäßig 60 Sekunden Timeout und 16.384
Output-Tokens. Modell, Timeout und Tokenlimit müssen zum jeweiligen Anbieter und
zum verfügbaren Konto passen. Schlüssel, nichtleere Modellnamen und positive Limits
werden nur beim Start des ausgewählten realen Providers geprüft. Die SDK-Beans sind
lazy und werden ausschließlich von dessen Adapter angefordert; Spring schließt das
initialisierte SDK beim Herunterfahren. Inaktive SDKs werden nicht gestartet.
Gemini nutzt ausdrücklich die Developer API, nicht eine implizite Vertex-Konfiguration.

## Verarbeitung

Gemeinsam bleiben `AiClient`, Request-/Response-DTOs, Prompt-Builder und deren
Versionierung, Validatoren, Fehlercodes sowie Workflow und Retry-Steuerung.
Ein Anbieterwechsel ändert weder Entwurfslebenszyklus noch Transaktionsgrenzen.

Die beiden realen Adapter verwenden denselben technischen Vertrag `AiResponsesGateway`.
Die SDK-Implementierungen bleiben providerspezifisch und unterstützen die Ausgabetypen
ihres jeweiligen Adapters. Das Interface trennt die Prompt-/DTO-Aufbereitung vom
SDK-Aufruf und erlaubt kleine Test-Doubles; es ersetzt nicht `AiClient` als fachliche
Grenze für den Providerwechsel. Der Stub benötigt kein Gateway.

- **OpenAI:** Das vorhandene offizielle SDK 4.39.1 erzeugt das Structured-Output-Schema
  und deserialisiert den Inhalt. Der Pre-Check liefert direkt `AiPreCheckResult`.
  Für Pläne bleibt `OpenAiGenerationOutput` als technisches SDK-Modell mit `Optional`
  für nullable Structured-Output-Felder bestehen. Der Adapter bildet es ohne JSON-
  Zwischenstufe auf `GeneratedPlanResponse` ab. Leere Listenelemente werden als
  ungültige Ausgabe zurückgewiesen.
- **Gemini:** Das offizielle `com.google.genai:google-genai:1.64.0` verwendet
  `models.generateContent` mit `responseMimeType=application/json`,
  `responseJsonSchema`, einem Kandidaten und einem Tokenlimit. Die SDK-Antwort enthält
  den Nutzinhalt als Text. Nach Prüfung von Blockierung, Finish-Reason und Inhalt
  deserialisiert `AiResponseParser.parse(json, Class<T>)` diesen genau einmal.
- **Stub:** Erzeugt direkt gemeinsame DTOs, ohne SDK und ohne Parser. Die vorhandenen
  Pre-Check-Szenarien bleiben erhalten. Das Szenario `without-dates` ist für Anfragen
  ohne Terminierung gedacht; `with-dates` verwendet vorhandene bestätigte Termine
  und liefert ohne Datumsbasis einen Plan ohne Termine.

`AiResponseSchemas` beschreibt die gemeinsamen DTOs als providerneutrales JSON-Schema.
Ein Vertragstest prüft rekursiv Feldnamen und Enum-Werte gegen die Records. Änderungen
am Ausgabeformat müssen Schema, DTOs und die backendseitigen `AiSchemaVersions`
gemeinsam berücksichtigen. Die Schema-Version ist kein vom Modell erzeugtes Feld.

Der generische Parser ersetzt `PreCheckResponseParser` und `GeneratedPlanResponseParser`.
Er weist null, Blank-Text, JSON-null, zusätzliche JSON-Werte, unbekannte Felder und
mehr als 1 MiB UTF-8 zurück. Jackson-Parsingfehler werden als
`AiOutputValidationException` weitergegeben. Die übrige ObjectMapper-Konfiguration
und der separate Persistenz-Codec für gespeicherte/alte Workflow-Daten bleiben unverändert.
Der redundante `OpenAiPreCheckOutput` entfällt ebenfalls.

Nach jeder erfolgreichen Adapterantwort validiert weiterhin `AiPreCheckProcessor`
beziehungsweise `AiPlanGenerationService`. Bean Validation, Text- und Mengengrenzen,
Termine, IDs und Abhängigkeiten werden deshalb unabhängig vom Provider geprüft.
Die globale Mindestanzahl von Aufgaben und bereichsübergreifende Limits bleiben
serverseitige Regeln; ein gültiges JSON-Schema allein reicht nicht aus.

## Fehler und Wiederholungen

| Ursache | Code | Automatischer Retry |
| --- | --- | --- |
| Netzwerkfehler, HTTP 5xx außer Timeout | `PROVIDER_UNAVAILABLE` | ja |
| Timeout-Ursache, HTTP 408/504 | `PROVIDER_TIMEOUT` | ja |
| HTTP 429 / explizites Rate-Limit | `RATE_LIMIT_EXCEEDED` | ja |
| Authentifizierung, Berechtigung, nicht reparierbarer Request/Modellfehler | `CLIENT_CONFIGURATION_ERROR` | nein |
| Explizite Ablehnung/Sicherheitsblockierung | `AI_REFUSAL` | nein |
| Abgebrochene/unvollständige Ausgabe, fehlender Inhalt, Deserialisierungs- oder Validierungsfehler | `INVALID_AI_RESPONSE` über `AiOutputValidationException` | nein |
| Unbekannter SDK-/Response-Fehler | `UNKNOWN_AI_ERROR` | nein |
| Unterbrochene Wartezeit im Backend | `RETRY_INTERRUPTED` | nein |

Die bestehenden Retry-Werte bleiben unverändert. Insbesondere deckt
`INVALID_AI_RESPONSE` weiterhin technische Ausgabe- **und** fachliche Validierungsfehler
ab, da `AiOutputValidationException` diesen Code nutzt. Der Koordinator behandelt die
Exception weiterhin separat. Es wird kein automatischer Reparaturversuch eingeführt.

SDK-Retries sind deaktiviert: OpenAI `maxRetries(0)`, Gemini
`HttpRetryOptions.attempts(1)` einschließlich des ersten Aufrufs. Nur das Backend
steuert Versuchszahl und Backoff. Gefangen werden die SDK-Exception-Familien, nicht
pauschal alle `RuntimeException`s. Unbekannte eigene Programmierfehler bleiben sichtbar.
Geminis `GenAiIOException` wird anhand der Ursache in Transport-, Timeout-, JSON-
oder unbekannte Fehler unterschieden; dessen Basisklasse ist im SDK nicht öffentlich.

## Tests und Grenzen

Die automatisierten Tests verwenden SDK-/Gateway-Mocks und lokale JSON-Fixtures;
sie benötigen keine Schlüssel und rufen keine externen KI-APIs auf. Sie prüfen
Provider-Auswahl, inaktive SDKs, Konfiguration, Prompt/Modell/Schema/Tokenlimit,
Deserialisierung, Refusal, unvollständige und fehlende Antworten, Fehlerzuordnung,
Parser-Strenge, deterministische Stub-Daten sowie gemeinsame Validierung.

Vor Deployment ist ein expliziter Live-Smoke-Test mit freigegebenen Testdaten und
Schlüsseln sinnvoll: Die Tests bestätigen weder Kontofreigaben/Quoten noch die
serverseitige Annahme des Schemas durch ein bestimmtes Modell. Tokenlimits sind keine
Byte-Limits; bei OpenAI wird kein bereits deserialisiertes Ergebnis zur Byteprüfung
erneut serialisiert. Bei Gemini greift die Byteprüfung nach Empfang des JSON-Textes,
nicht als Limit des HTTP-Response-Umschlags. Große Pläne können das Tokenlimit erreichen
und werden dann kontrolliert als unvollständig zurückgewiesen.

Geprüfte Schnittstellen: [Google Gen AI Java SDK 1.64.0](https://github.com/googleapis/java-genai/tree/v1.64.0),
[Gemini JSON-Schema-Ausgaben](https://ai.google.dev/gemini-api/docs/structured-output),
[OpenAI Responses API und Output-Limit](https://developers.openai.com/api/reference/cli/resources/responses/methods/create).
