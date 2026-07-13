# ProjectFlow

ProjectFlow ist der Prototyp eines minimalistischen, KI-gestützten Projektplanungstools für Einzelpersonen und kleine Projekte. Das Projekt entsteht im Rahmen einer Bachelorarbeit mit dem vorläufigen Titel **„Konzeption und Evaluation eines KI-gestützten Projektplanungstools zur Generierung einfacher Projektstrukturen“**.

## Ziel

Die Webanwendung soll Nutzer bei der Erstellung einfacher Projektstrukturen unterstützen. Im Mittelpunkt steht ein Wizard, der mit wenigen gezielten Fragen Informationen zum geplanten Projekt erfasst. Auf dieser Grundlage soll die KI Aufgaben, Projektphasen und weitere Hinweise vorschlagen.

Die KI dient dabei als unterstützendes Werkzeug. Generierte Vorschläge sollen vor der Übernahme geprüft und angepasst werden können.

Die zentrale Forschungsfrage lautet:

> Wie kann KI-Unterstützung in einem Projektplanungstool eingesetzt werden, sodass die erzeugten Aufgaben und Projektstrukturen für Nutzer kleiner Projekte hilfreich, verständlich und erwartungskonform sind?

## Geplanter Funktionsumfang

- Projektübersicht
- geführte Projekterstellung über einen Wizard
- Start mit einem leeren Projekt, einem statischen Template oder einem KI-generierten Projektplan
- Verwaltung von Aufgaben und Projektphasen
- Generierung von Projektplänen aus den Wizard-Eingaben
- Beantwortung einfacher Fragen zu einem bestehenden Projekt
- Feedback zu KI-generierten Vorschlägen
- Registrierung und Login

## Technische Umsetzung

Die Anwendung ist als Webanwendung mit Java und Spring Boot vorgesehen. Das Frontend soll serverseitig mit Thymeleaf umgesetzt werden. Für die Datenhaltung sind PostgreSQL und Spring Data JPA geplant, für die Authentifizierung Spring Security.

Die KI-Integration soll über eine externe Schnittstelle zu einem Large Language Model erfolgen. Die Antworten sollen möglichst strukturiert, beispielsweise als JSON, verarbeitet werden.

## Evaluation

Der Prototyp soll im Rahmen einer kleinen Nutzerstudie getestet werden. Untersucht werden insbesondere:

- Passgenauigkeit und Verständlichkeit der erzeugten Projektstrukturen,
- Nützlichkeit der Vorschläge,
- Vertrauen in die KI-Unterstützung,
- Bedienbarkeit der Anwendung.

## Abgrenzung

ProjectFlow soll kein vollständiges Projektmanagementsystem und kein autonomer KI-Agent werden. Teamverwaltung, Rollenverteilung, komplexe Automatisierungen, eine mobile App und professionelle Unternehmensplanung sind nicht Teil des geplanten Prototyps.
