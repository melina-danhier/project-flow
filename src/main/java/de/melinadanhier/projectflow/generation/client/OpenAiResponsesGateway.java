package de.melinadanhier.projectflow.generation.client;

import de.melinadanhier.projectflow.generation.prompt.AiPrompt;

public interface OpenAiResponsesGateway {

    <T> T execute(String model, AiPrompt prompt, Class<T> responseType);
}
