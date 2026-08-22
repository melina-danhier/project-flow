package de.melinadanhier.projectflow.ai.exception;

import org.springframework.stereotype.Component;

@Component
public class AiTechnicalErrorClassifier {

    public AiTechnicalErrorCode classify(RuntimeException exception) {
        return exception instanceof AiClientTechnicalException technicalException
                ? technicalException.getErrorCode()
                : AiTechnicalErrorCode.UNKNOWN_AI_ERROR;
    }
}
