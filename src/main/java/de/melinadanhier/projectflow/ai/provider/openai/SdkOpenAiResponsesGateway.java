package de.melinadanhier.projectflow.ai.provider.openai;

import com.openai.client.OpenAIClient;
import com.openai.errors.BadRequestException;
import com.openai.errors.InternalServerException;
import com.openai.errors.NotFoundException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.errors.UnprocessableEntityException;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.StructuredResponseOutputMessage;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.prompt.AiPrompt;
import de.melinadanhier.projectflow.ai.provider.AiResponsesGateway;
import lombok.RequiredArgsConstructor;

import static de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode.*;
import static de.melinadanhier.projectflow.ai.provider.AiGatewayErrors.errorCodeForStatusCode;
import static de.melinadanhier.projectflow.ai.provider.AiGatewayErrors.isTimeout;

@RequiredArgsConstructor
public class SdkOpenAiResponsesGateway implements AiResponsesGateway {

    private final OpenAIClient client;
    private final int maxOutputTokens;

    @Override
    public <T> T execute(String model, AiPrompt prompt, Class<T> responseType) {
        try {
            StructuredResponseCreateParams<T> params = buildParams(model, prompt, responseType);
            StructuredResponse<T> response = client.responses().create(params);
            return extractOutput(response);
        } catch (OpenAIException exception) {
            throw translateSdkException(exception);
        }
    }

    private <T> StructuredResponseCreateParams<T> buildParams(String model, AiPrompt prompt, Class<T> responseType) {
        return StructuredResponseCreateParams.<T>builder()
                .model(model)
                .instructions(prompt.systemInstructions())
                .input(prompt.confirmedUserData())
                .text(responseType)
                .maxOutputTokens(maxOutputTokens)
                .store(false)
                .build();
    }

    private AiTechnicalException translateSdkException(OpenAIException exception) {
        if (exception instanceof OpenAIServiceException serviceException) {
            return translateServiceException(serviceException);
        }
        if (exception instanceof OpenAIIoException || exception instanceof OpenAIRetryableException) {
            return translateCommunicationException(exception);
        }
        if (exception instanceof OpenAIInvalidDataException) {
            return new AiOutputValidationException(
                    "OpenAI lieferte keine deserialisierbare strukturierte Antwort.",
                    exception
            );
        }
        return new AiTechnicalException(
                UNKNOWN_AI_ERROR,
                "Das OpenAI-SDK konnte den Aufruf nicht verarbeiten.",
                exception
        );
    }

    private AiTechnicalException translateServiceException(OpenAIServiceException exception) {
        if (exception instanceof UnauthorizedException || exception instanceof PermissionDeniedException
                || exception instanceof BadRequestException || exception instanceof NotFoundException
                || exception instanceof UnprocessableEntityException) {
            return new AiTechnicalException(
                    CLIENT_CONFIGURATION_ERROR,
                    "Die OpenAI-Konfiguration oder der angeforderte Request wurde abgelehnt.",
                    exception
            );
        }
        if (exception instanceof RateLimitException) {
            return new AiTechnicalException(
                    RATE_LIMIT_EXCEEDED,
                    "Das OpenAI-Aufruflimit wurde vorübergehend erreicht.",
                    exception
            );
        }
        if (exception instanceof InternalServerException) {
            AiTechnicalErrorCode errorCode = exception.statusCode() == 504 ? PROVIDER_TIMEOUT : PROVIDER_UNAVAILABLE;
            return new AiTechnicalException(
                    errorCode,
                    "OpenAI ist vorübergehend nicht erreichbar.",
                    exception
            );
        }
        return new AiTechnicalException(
                errorCodeForStatusCode(exception.statusCode()),
                "Der OpenAI-Aufruf ist fehlgeschlagen.",
                exception
        );
    }

    private AiTechnicalException translateCommunicationException(OpenAIException exception) {
        if (hasTimeoutCause(exception)) {
            return new AiTechnicalException(
                    PROVIDER_TIMEOUT, "Der OpenAI-Aufruf hat das Zeitlimit überschritten.", exception);
        }
        return new AiTechnicalException(
                PROVIDER_UNAVAILABLE, "OpenAI ist vorübergehend nicht erreichbar.", exception);
    }

    private boolean hasTimeoutCause(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (isTimeout(cause)) return true;
        }
        return false;
    }

    private <T> T extractOutput(StructuredResponse<T> response) {
        if (response == null) {
            throw new AiOutputValidationException("OpenAI lieferte keine Antwort.");
        }
        validateResponseError(response);
        validateResponseStatus(response);
        return requireSingleOutput(response);
    }

    private void validateResponseError(StructuredResponse<?> response) {
        var error = response.error();
        if (error.isPresent()) {
            throw new AiTechnicalException(
                    errorCodeForResponseError(error.get().code().toString()),
                    "OpenAI konnte die Antwort nicht erzeugen."
            );
        }
    }

    private AiTechnicalErrorCode errorCodeForResponseError(String code) {
        return switch (code) {
            case "server_error" -> PROVIDER_UNAVAILABLE;
            case "rate_limit_exceeded" -> RATE_LIMIT_EXCEEDED;
            case "vector_store_timeout" -> PROVIDER_TIMEOUT;
            case "invalid_prompt" -> CLIENT_CONFIGURATION_ERROR;
            case "image_content_policy_violation" -> AI_REFUSAL;
            default -> UNKNOWN_AI_ERROR;
        };
    }

    private void validateResponseStatus(StructuredResponse<?> response) {
        if (response.incompleteDetails().isPresent() || response.status()
                .filter(status -> !status.toString().equals("completed"))
                .isPresent()
        ) {
            throw new AiOutputValidationException("OpenAI lieferte eine unvollständige Antwort.");
        }
    }

    private <T> T requireSingleOutput(StructuredResponse<T> response) {
        T result = null;
        for (var outputItem : response.output()) {
            if (!outputItem.isMessage()) continue;
            StructuredResponseOutputMessage<T> message = outputItem.asMessage();
            validateMessageStatus(message);
            for (var content : message.content()) {
                if (content.isRefusal()) {
                    throw new AiTechnicalException(AI_REFUSAL, "OpenAI hat die Anfrage abgelehnt.");
                }
                if (!content.isOutputText()) continue;
                if (result != null) {
                    throw new AiOutputValidationException("OpenAI lieferte mehrere strukturierte Antworten.");
                }
                result = content.asOutputText();
            }
        }
        if (result == null) {
            throw new AiOutputValidationException("OpenAI lieferte keine vollständige strukturierte Antwort.");
        }
        return result;
    }

    private void validateMessageStatus(StructuredResponseOutputMessage<?> message) {
        if (!message.status().toString().equals("completed")) {
            throw new AiOutputValidationException("OpenAI lieferte eine unvollständige Nachricht.");
        }
    }
}
