package de.melinadanhier.projectflow.planelement.dto.improvement;

/** Ein einzelnes, nutzerlesbar formatiertes Feld im KI-Review. */
public record AiImprovementFieldChange(
        String key,
        String label,
        String originalValue,
        String proposedValue
) { }
