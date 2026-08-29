package de.melinadanhier.projectflow.planelement.model;

public enum ElementOrigin {
    USER,
    TEMPLATE,
    TEMPLATE_MODIFIED,
    AI_MODIFIED,
    AI

    ;

    public ElementOrigin modifiedByUser() {
        return switch (this) {
            case AI, AI_MODIFIED -> AI_MODIFIED;
            case TEMPLATE, TEMPLATE_MODIFIED -> TEMPLATE_MODIFIED;
            case USER -> USER;
        };
    }
}
