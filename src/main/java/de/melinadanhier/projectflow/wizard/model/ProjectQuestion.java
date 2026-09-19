package de.melinadanhier.projectflow.wizard.model;

/**
 * Lightweight question definition loaded from the YAML catalog.
 * Unlike {@link AiProjectQuestion}, this record focuses on the display-relevant
 * fields only: key, label and placeholder.
 */
public record ProjectQuestion(
        String key,
        String label,
        String placeholder
) {}
