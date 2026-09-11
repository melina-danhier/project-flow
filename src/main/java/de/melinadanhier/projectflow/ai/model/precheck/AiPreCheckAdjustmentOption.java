package de.melinadanhier.projectflow.ai.model.precheck;

public enum AiPreCheckAdjustmentOption {
    EXTEND_TIMEFRAME("Zeitraum verlängern"),
    INCREASE_AVAILABLE_TIME("Mehr Zeit pro Tag einplanen"),
    REDUCE_SCOPE("Weniger Inhalte einplanen");

    private final String displayText;

    AiPreCheckAdjustmentOption(String displayText) {
        this.displayText = displayText;
    }

    public String displayText() {
        return displayText;
    }
}
