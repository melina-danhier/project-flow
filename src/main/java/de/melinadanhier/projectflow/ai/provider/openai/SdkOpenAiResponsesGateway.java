package de.melinadanhier.projectflow.ai.provider.openai;

import com.openai.client.OpenAIClient;
import com.openai.errors.BadRequestException;
import com.openai.errors.InternalServerException;
import com.openai.errors.NotFoundException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.errors.UnprocessableEntityException;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.prompt.AiPrompt;

public class SdkOpenAiResponsesGateway implements OpenAiResponsesGateway {

    private final OpenAIClient client;

    public SdkOpenAiResponsesGateway(OpenAIClient client) {
        this.client = client;
    }

    @Override
    public <T> T execute(String model, AiPrompt prompt, Class<T> responseType) {
        try {
            StructuredResponseCreateParams<T> params = StructuredResponseCreateParams.<T>builder()
                    .model(model)
                    .instructions(prompt.systemInstructions())
                    .input(prompt.confirmedUserData())
                    .text(responseType)
                    .store(false)
                    .build();
            return extract(client.responses().create(params));
        } catch (UnauthorizedException | PermissionDeniedException | BadRequestException
                 | NotFoundException | UnprocessableEntityException exception) {
            throw new AiTechnicalException(
                    AiTechnicalErrorCode.CLIENT_CONFIGURATION_ERROR,
                    "Die OpenAI-Konfiguration oder der angeforderte Request wurde abgelehnt.", exception);
        } catch (RateLimitException exception) {
            throw new AiTechnicalException(
                    AiTechnicalErrorCode.RATE_LIMIT_EXCEEDED,
                    "Das OpenAI-Aufruflimit wurde vorübergehend erreicht.", exception);
        } catch (InternalServerException exception) {
            throw new AiTechnicalException(
                    AiTechnicalErrorCode.PROVIDER_UNAVAILABLE,
                    "OpenAI ist vorübergehend nicht erreichbar.", exception);
        } catch (OpenAIIoException | OpenAIRetryableException exception) {
            if (hasTimeoutCause(exception)) {
                throw new AiTechnicalException(
                        AiTechnicalErrorCode.PROVIDER_TIMEOUT,
                        "Der OpenAI-Aufruf hat das Zeitlimit überschritten.", exception);
            }
            throw new AiTechnicalException(
                    AiTechnicalErrorCode.PROVIDER_UNAVAILABLE,
                    "OpenAI ist vorübergehend nicht erreichbar.", exception);
        } catch (OpenAIInvalidDataException exception) {
            throw new AiOutputValidationException(
                    "OpenAI lieferte keine deserialisierbare strukturierte Antwort.", exception);
        }
    }

    private boolean hasTimeoutCause(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException
                    || current instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private <T> T extract(StructuredResponse<T> response) {
        if (response.error().isPresent()) {
            throw new AiTechnicalException(
                    AiTechnicalErrorCode.PROVIDER_UNAVAILABLE,
                    "OpenAI konnte die Antwort nicht erzeugen.");
        }
        if (response.incompleteDetails().isPresent()) {
            throw new AiOutputValidationException("OpenAI lieferte eine unvollständige Antwort.");
        }
        for (var outputItem : response.output()) {
            if (!outputItem.isMessage()) {
                continue;
            }
            for (var content : outputItem.asMessage().content()) {
                if (content.isRefusal()) {
                    throw new AiTechnicalException(
                            AiTechnicalErrorCode.AI_REFUSAL,
                            "OpenAI hat die Anfrage abgelehnt.");
                }
                if (content.isOutputText()) {
                    return content.asOutputText();
                }
            }
        }
        throw new AiOutputValidationException("OpenAI lieferte keine vollständige strukturierte Antwort.");
    }
}
