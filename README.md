# ProjectFlow

ProjectFlow ist ein entstehender Prototyp eines KI-gestützten Projektplanungstools für Einzelpersonen und kleine Gruppen. Geplant sind eine geführte Projekterstellung, generierte Projektstrukturen sowie die Verwaltung von Projekten und Aufgaben.

Das Projekt befindet sich in Entwicklung und entsteht im Rahmen einer Bachelorarbeit mit dem vorläufigen Titel **„Konzeption und Evaluation eines KI-gestützten Projektplanungstools zur Generierung einfacher Projektstrukturen“**. Die im Folgenden beschriebenen Funktionen und Technologien geben den vorgesehenen Umfang des Prototyps wieder.

## Ziel

Die Webanwendung soll Einzelpersonen und Kleingruppen bei der Erstellung und Verwaltung einfacher Projektstrukturen unterstützen. Sie richtet sich an kleinere private, studentische oder vergleichbare Projekte. Im Mittelpunkt steht ein Wizard, der mit wenigen gezielten Fragen Informationen zum geplanten Projekt erfasst. Auf dieser Grundlage soll die KI Bereiche, Aufgaben und weitere Hinweise vorschlagen.

Die KI dient dabei als unterstützendes Werkzeug. Generierte Vorschläge sollen vor der Übernahme geprüft und angepasst werden können.

Die zentrale Forschungsfrage lautet:

> Wie kann KI-Unterstützung in einem Projektplanungstool eingesetzt werden, sodass die erzeugten Aufgaben und Projektstrukturen für Einzelpersonen und Kleingruppen bei kleineren Projekten hilfreich, verständlich und erwartungskonform sind?

## Geplanter Funktionsumfang

- Projektübersicht
- geführte Projekterstellung über einen Wizard
- Start mit einem leeren Projekt, einem statischen Template oder einem KI-generierten Projektplan
- Verwaltung von Aufgaben und Projektbereichen
- einfache gemeinsame Bearbeitung von Projekten in Kleingruppen
- Generierung von Projektplänen aus den Wizard-Eingaben
- Beantwortung einfacher Fragen zu einem bestehenden Projekt
- Feedback zu KI-generierten Vorschlägen
- Registrierung und Login

## Technische Umsetzung

**Vorgesehener Tech-Stack:** Java, Spring Boot, Thymeleaf, Spring Data JPA, PostgreSQL und Flyway.

Das Frontend soll zunächst einfach gehalten und serverseitig mit Thymeleaf umgesetzt werden. Für Login und Registrierung ist Spring Security vorgesehen.

Die KI-Integration soll über eine externe Schnittstelle zu einem Large Language Model erfolgen. Die Antworten sollen möglichst strukturiert, beispielsweise als JSON, verarbeitet werden.

Die Backend-Anbindung unterstützt OpenAI, Gemini und einen lokalen Stub über
`projectflow.ai.provider`. Konfiguration, Verarbeitung und Fehlerbehandlung sind in
[KI-Provider](docs/ai-providers.md) dokumentiert.

## Evaluation

Der Prototyp soll im Rahmen einer kleinen Nutzerstudie getestet werden. Untersucht werden insbesondere:

- Passgenauigkeit und Verständlichkeit der erzeugten Projektstrukturen,
- Nützlichkeit der Vorschläge,
- Vertrauen in die KI-Unterstützung,
- Bedienbarkeit der Anwendung.

## Abgrenzung

ProjectFlow soll kein vollständiges Projektmanagementsystem und kein autonomer KI-Agent werden. Eine komplexe Teamverwaltung, differenzierte Rollen- und Rechtekonzepte, organisationsübergreifende Zusammenarbeit, mobile Apps, komplexe Automatisierungen und professionelle Unternehmensplanung sind nicht Teil des geplanten Prototyps.

## Betrieb und Migrationen

Vor dem Deployment von Migration V16: [Draft-Lebenszyklus, PostgreSQL-Prüfung und Umgang mit Alt-Drafts](docs/draft-lifecycle-migration.md).
