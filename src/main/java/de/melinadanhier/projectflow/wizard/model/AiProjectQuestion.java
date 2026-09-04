package de.melinadanhier.projectflow.wizard.model;

public record AiProjectQuestion(
        String key,
        String label,
        String helpText,
        AiQuestionType type,
        boolean required,
        int maxLength
) { }
