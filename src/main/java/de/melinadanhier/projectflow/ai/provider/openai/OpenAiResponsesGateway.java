package de.melinadanhier.projectflow.ai.provider.openai;

import de.melinadanhier.projectflow.ai.prompt.AiPrompt;

public interface OpenAiResponsesGateway {

    <T> T execute(String model, AiPrompt prompt, Class<T> responseType);
}
