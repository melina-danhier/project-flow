package de.melinadanhier.projectflow.generation.service;

import de.melinadanhier.projectflow.generation.client.AiIncompleteResponseException;
import de.melinadanhier.projectflow.generation.client.AiOutputValidationException;
import de.melinadanhier.projectflow.generation.client.AiProviderConfigurationException;
import de.melinadanhier.projectflow.generation.client.AiProviderUnavailableException;
import de.melinadanhier.projectflow.generation.client.AiRequestRefusedException;
import de.melinadanhier.projectflow.generation.model.AiTechnicalErrorCode;
import org.springframework.stereotype.Component;

@Component
public class AiTechnicalErrorClassifier {

    public AiTechnicalErrorCode classify(RuntimeException exception) {
        if (exception instanceof AiRequestRefusedException) {
            return AiTechnicalErrorCode.AI_REFUSAL;
        }
        if (exception instanceof AiIncompleteResponseException) {
            return AiTechnicalErrorCode.INCOMPLETE_AI_RESPONSE;
        }
        if (exception instanceof AiOutputValidationException) {
            return AiTechnicalErrorCode.INVALID_AI_RESPONSE;
        }
        if (exception instanceof AiProviderConfigurationException) {
            return AiTechnicalErrorCode.PROVIDER_CONFIGURATION_ERROR;
        }
        if (exception instanceof AiProviderUnavailableException) {
            return AiTechnicalErrorCode.PROVIDER_UNAVAILABLE;
        }
        return AiTechnicalErrorCode.UNKNOWN_AI_ERROR;
    }
}
