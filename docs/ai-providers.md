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

## Datenschutz, Datenaufbewahrung und Anbietervereinbarungen

Zur Sicherstellung des Datenschutzes und der Vorgaben der DSGVO gelten für die Anbindung externer Provider an ProjectFlow die folgenden Regelungen bezüglich Datenaufbewahrung, Zugriffen, Modelltraining und vertraglichen Vereinbarungen.

### Datenminimierung in ProjectFlow

An externe KI-Provider werden ausschließlich fachlich notwendige Planungsdaten übertragen:
- Projekttitel, Beschreibung, Kategorie und Unterkategorie,
- Geplanter Zeitraum und Arbeitszeitrahmen,
- Vom Nutzer im Wizard beantwortete Leitfragen und optionale Zusatzangaben.

Es werden **keine** Benutzerkontodaten (wie E-Mail-Adressen, Passwörter, Benutzer-IDs, Session-Kennungen oder Klarnamen von Projektbeteiligten) an den Provider übermittelt. Der Nutzer kann vor der Planerstellung in der Wizard-Zusammenfassung einsehen, welche Daten an die KI gesendet werden.

### OpenAI (`openai`)

- **Aufbewahrung (Data Retention):** Bei Aufrufen der OpenAI-API (Platform / API-Endpunkte, im Gegensatz zu Endnutzerprodukten wie ChatGPT) speichert OpenAI Eingaben und generierte Ausgaben standardmäßig für maximal 30 Tage auf sicheren Systemen. Diese temporäre Speicherung dient ausschließlich dem automatisierten Missbrauchs- und Missbrauchserkennungsmonitoring (Abuse Monitoring). Nach Ablauf der Frist werden die Daten gelöscht, sofern keine gesetzliche Aufbewahrungspflicht besteht.
- **Mögliche Zugriffe:** Der Zugriff auf die temporären Monitoring-Daten ist stark beschränkt und erfolgt nur im Verdachtsfall durch autorisiertes Sicherheits- und Supportpersonal von OpenAI.
- **Modelltraining / Weiterverwendung:** OpenAI verwendet über die API übermittelte Kunden- und Projektdaten standardmäßig **nicht** zum Trainieren oder Verbessern eigener Modelle (*„OpenAI does not use customer data submitted via our API to train OpenAI models“*).
- **Kontoeinstellungen:** Für den Betrieb ist ein reguläres OpenAI-Plattformkonto mit hinterlegtem Zahlungsmittel (Pay-as-you-go) ausreichend. In den Organisationseinstellungen ist sicherzustellen, dass kein freiwilliges Data-Sharing/Opt-in für Trainingszwecke aktiviert ist.
- **Vertrag zur Auftragsverarbeitung (DPA):** OpenAI stellt ein standardisiertes *Data Processing Addendum (DPA)* gemäß Art. 28 DSGVO einschließlich der EU-Standardvertragsklauseln (SCC) zur Verfügung. Dieses kann direkt im Dashboard der OpenAI-Plattform unter *Settings → Privacy / Compliance* elektronisch abgeschlossen werden.

### Google Gemini (`gemini`)

- **Aufbewahrung und Modelltraining (Free Tier vs. Paid Tier):**
  - *Google AI Studio Free Tier (unbezahlt):* Bei Nutzung der kostenfreien Kontingente behält sich Google das Recht vor, Eingaben und Ausgaben durch menschliche Prüfer einsehen zu lassen und zur Verbesserung und zum Training von Google-Produkten und maschinellen Lernmodellen zu verwenden.
  - *Google AI Studio / Google Cloud Paid Tier (kostenpflichtig):* Sobald das Google-Cloud-Projekt mit einem Abrechnungskonto (Billing Account) verknüpft ist und die kostenpflichtige Nutzung greift, werden Prompts und Antworten **nicht** für das Modelltraining verwendet (*„When you pay for Gemini API requests, your data is not used to train Google models“*). Zudem findet kein Zugriff durch menschliche Prüfer zur Qualitätsverbesserung statt.
  - **Betriebsvoraussetzung für ProjectFlow:** Für den Produktiv- und Evaluationsbetrieb von ProjectFlow muss zwingend ein Google-Cloud-Projekt mit **aktiviertem Billing (Paid Tier)** verwendet werden, um das Training mit Nutzerdaten auszuschließen.
- **Mögliche Zugriffe:** Bei aktivierter Bezahlfunktion werden Logs temporär nur zur Fehlerdiagnose, Systemstabilität und Missbrauchserkennung vorgehalten; Zugriffe sind auf berechtigte Google-Systemadministratoren im Supportfall beschränkt.
- **Kontoeinstellungen:** Google Cloud Console / Google AI Studio mit verknüpftem Billing Account. Der API-Schlüssel (`GEMINI_API_KEY`) wird in diesem Projekt erzeugt.
- **Vertrag zur Auftragsverarbeitung (DPA):** Für Google Cloud und die über Google Cloud abgerechnete Gemini API gilt das *Google Cloud Data Processing Addendum (CDPA)* gemäß Art. 28 DSGVO mit EU-Standardvertragsklauseln, das in der Google Cloud Console hinterlegt bzw. bestätigt wird.

## Tests

Die automatisierten Provider-Tests verwenden Stubs beziehungsweise Test-Doubles und
rufen keine externen APIs auf. Ein realer Providerzugang, Kontingente und die
Akzeptanz eines Schemas durch ein konkret konfiguriertes Modell müssen bei Bedarf
separat mit freigegebenen Testdaten geprüft werden.
