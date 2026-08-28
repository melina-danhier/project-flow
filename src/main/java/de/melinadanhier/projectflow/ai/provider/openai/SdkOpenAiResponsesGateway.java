package de.melinadanhier.projectflow.ai.provider.openai;

import de.melinadanhier.projectflow.ai.provider.AiResponsesGateway;

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
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.prompt.AiPrompt;

public class SdkOpenAiResponsesGateway implements AiResponsesGateway {

    private final OpenAIClient client;
    private final int maxOutputTokens;

    public SdkOpenAiResponsesGateway(OpenAIClient client, int maxOutputTokens) {
        this.client = client;
        this.maxOutputTokens = maxOutputTokens;
    }

    @Override
    public <T> T execute(String model, AiPrompt prompt, Class<T> responseType) {
        try {
            StructuredResponseCreateParams<T> params = StructuredResponseCreateParams.<T>builder()
                    .model(model)
                    .instructions(prompt.systemInstructions())
                    .input(prompt.confirmedUserData())
                    .text(responseType)
                    .maxOutputTokens(maxOutputTokens)
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
                    exception.statusCode() == 504 ? AiTechnicalErrorCode.PROVIDER_TIMEOUT : AiTechnicalErrorCode.PROVIDER_UNAVAILABLE,
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
        } catch (OpenAIServiceException exception) {
            int status = exception.statusCode();
            AiTechnicalErrorCode code = status == 408 || status == 504 ? AiTechnicalErrorCode.PROVIDER_TIMEOUT
                    : status == 429 ? AiTechnicalErrorCode.RATE_LIMIT_EXCEEDED
                    : status >= 500 && status < 600 ? AiTechnicalErrorCode.PROVIDER_UNAVAILABLE
                    : status >= 400 && status < 500 ? AiTechnicalErrorCode.CLIENT_CONFIGURATION_ERROR
                    : AiTechnicalErrorCode.UNKNOWN_AI_ERROR;
            throw new AiTechnicalException(code, "Der OpenAI-Aufruf ist fehlgeschlagen.", exception);
        } catch (OpenAIException exception) {
            throw new AiTechnicalException(AiTechnicalErrorCode.UNKNOWN_AI_ERROR,
                    "Das OpenAI-SDK konnte den Aufruf nicht verarbeiten.", exception);
        }
    }

    private boolean hasTimeoutCause(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException
                    || current instanceof java.net.http.HttpTimeoutException
                    || current instanceof java.io.InterruptedIOException && !Thread.currentThread().isInterrupted()) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private <T> T extract(StructuredResponse<T> response) {
        if (response == null) throw new AiOutputValidationException("OpenAI lieferte keine Antwort.");
        if (response.error().isPresent()) {
            String code = response.error().get().code().toString();
            AiTechnicalErrorCode errorCode = switch (code) {
                case "server_error" -> AiTechnicalErrorCode.PROVIDER_UNAVAILABLE;
                case "rate_limit_exceeded" -> AiTechnicalErrorCode.RATE_LIMIT_EXCEEDED;
                case "vector_store_timeout" -> AiTechnicalErrorCode.PROVIDER_TIMEOUT;
                case "invalid_prompt" -> AiTechnicalErrorCode.CLIENT_CONFIGURATION_ERROR;
                case "image_content_policy_violation" -> AiTechnicalErrorCode.AI_REFUSAL;
                default -> AiTechnicalErrorCode.UNKNOWN_AI_ERROR;
            };
            throw new AiTechnicalException(
                    errorCode,
                    "OpenAI konnte die Antwort nicht erzeugen.");
        }
        if (response.incompleteDetails().isPresent()
                || response.status().filter(status -> !"completed".equals(status.toString())).isPresent()) {
            throw new AiOutputValidationException("OpenAI lieferte eine unvollständige Antwort.");
        }
        T result = null;
        for (var outputItem : response.output()) {
            if (!outputItem.isMessage()) {
                continue;
            }
            if (!"completed".equals(outputItem.asMessage().status().toString())) {
                throw new AiOutputValidationException("OpenAI lieferte eine unvollständige Nachricht.");
            }
            for (var content : outputItem.asMessage().content()) {
                if (content.isRefusal()) {
                    throw new AiTechnicalException(
                            AiTechnicalErrorCode.AI_REFUSAL,
                            "OpenAI hat die Anfrage abgelehnt.");
                }
                if (content.isOutputText()) {
                    if (result != null) {
                        throw new AiOutputValidationException("OpenAI lieferte mehrere strukturierte Antworten.");
                    }
                    result = content.asOutputText();
                }
            }
        }
        if (result != null) return result;
        throw new AiOutputValidationException("OpenAI lieferte keine vollständige strukturierte Antwort.");
    }
}
