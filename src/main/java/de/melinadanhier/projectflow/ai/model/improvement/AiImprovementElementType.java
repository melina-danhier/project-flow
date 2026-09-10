package de.melinadanhier.projectflow.ai.model.improvement;

public enum AiImprovementElementType {
    SECTION("Projektbereich"),
    TASK("Aufgabe"),
    MILESTONE("Meilenstein");

    private final String label;

    AiImprovementElementType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
