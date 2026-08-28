package de.melinadanhier.projectflow.ai.provider.gemini;

import de.melinadanhier.projectflow.ai.prompt.AiPrompt;

public interface GeminiResponsesGateway {
    <T> T execute(String model, AiPrompt prompt, Class<T> responseType);
}
