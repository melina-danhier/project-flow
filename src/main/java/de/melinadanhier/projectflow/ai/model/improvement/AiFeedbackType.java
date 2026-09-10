package de.melinadanhier.projectflow.ai.model.improvement;

public enum AiFeedbackType {
    IMPROVE("Verbessern"),
    EXPAND("Ausführlicher beschreiben"),
    SIMPLIFY("Kürzen / vereinfachen"),
    REPLAN("Neu planen"),
    ESTIMATE_EFFORT("Aufwand schätzen");

    private final String label;

    AiFeedbackType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean supports(AiImprovementElementType elementType) {
        return switch (this) {
            case IMPROVE, EXPAND, SIMPLIFY -> true;
            case REPLAN -> elementType == AiImprovementElementType.TASK
                    || elementType == AiImprovementElementType.MILESTONE;
            case ESTIMATE_EFFORT -> elementType == AiImprovementElementType.TASK;
        };
    }
}
