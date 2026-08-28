package de.melinadanhier.projectflow.ai.prompt;

public record AiPrompt(
        String version,
        String systemInstructions,
        String confirmedUserData
) { }