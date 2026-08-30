package de.melinadanhier.projectflow.ai.model.generation;

public record RejectedCriticalAssumption(String statement, String correction) {
    public RejectedCriticalAssumption {
        statement = normalize(statement);
        correction = normalize(correction);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
