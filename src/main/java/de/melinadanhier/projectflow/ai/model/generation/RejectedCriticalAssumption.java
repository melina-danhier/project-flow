package de.melinadanhier.projectflow.ai.model.generation;

public record RejectedCriticalAssumption(String statement, String correction) {
    public RejectedCriticalAssumption {
        statement = statement == null || statement.isBlank() ? null : statement.strip();
        correction = correction == null || correction.isBlank() ? null : correction.strip();
    }
}
