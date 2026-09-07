package de.melinadanhier.projectflow.plancontainer.template.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProjectCategory {
    EDUCATION("Bildung und Studium"),
    SOFTWARE_TECHNOLOGY("Software und Technik"),
    EVENT("Veranstaltung"),
    HOME("Haushalt und Wohnen"),
    CREATIVE("Kreatives"),
    CAREER("Beruf und Karriere"),
    HEALTH_PERSONAL_DEVELOPMENT("Gesundheit und persönliche Entwicklung"),
    TRAVEL("Reise"),
    OTHER("Sonstiges");

    private final String label;
}
