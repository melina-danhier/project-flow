package de.melinadanhier.projectflow.ai.validation;

public final class AiResponseLimits {

    public static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    public static final int MIN_PHASES = 1;
    public static final int MIN_TASKS = 3;
    public static final int MAX_PHASES = 20;
    public static final int MAX_TASKS = 200;
    public static final int MAX_MILESTONES = 100;
    public static final int MAX_DEPENDENCIES = 500;
    public static final int MAX_ESTIMATED_HOURS = 10_000;
    public static final int MAX_PRE_CHECK_PROBLEMS = 100;
    public static final int MAX_ASSUMPTIONS = 100;
    // Persistierte Projekt-, Draft- und Planelementtitel sind im aktuellen Modell auf 100 begrenzt.
    public static final int MAX_TITLE_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;

    private AiResponseLimits() {
    }
}
