package de.melinadanhier.projectflow.ai.provider;

import de.melinadanhier.projectflow.ai.prompt.AiPrompt;

/** Technischer Aufrufvertrag; unterstützt die Ausgabetypen des jeweils zugehörigen Provider-Adapters. */
public interface AiResponsesGateway {
    <T> T execute(String model, AiPrompt prompt, Class<T> responseType);
}
