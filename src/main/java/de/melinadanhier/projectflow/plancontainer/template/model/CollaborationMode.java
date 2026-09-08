package de.melinadanhier.projectflow.plancontainer.template.model;

public enum CollaborationMode {
    INDIVIDUAL("Einzelprojekt"),
    GROUP("Gruppenprojekt"),
    BOTH("Einzel- oder Gruppenprojekt");

    private final String label;

    CollaborationMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
