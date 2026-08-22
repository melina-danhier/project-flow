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
import de.melinadanhier.projectflow.ai.exception.*;
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
            throw new AiProviderConfigurationException(
                    "Die OpenAI-Konfiguration oder der angeforderte Request wurde abgelehnt.");
        } catch (RateLimitException | InternalServerException | OpenAIIoException
                 | OpenAIRetryableException exception) {
            throw new AiProviderUnavailableException("OpenAI ist vorübergehend nicht erreichbar.");
        } catch (OpenAIInvalidDataException exception) {
            // SDK-Exceptions können die Rohantwort enthalten und werden deshalb nicht weitergereicht.
            throw new AiOutputValidationException(
                    "OpenAI lieferte keine deserialisierbare strukturierte Antwort.");
        }
    }

    private <T> T extract(StructuredResponse<T> response) {
        if (response.error().isPresent()) {
            throw new AiProviderUnavailableException("OpenAI konnte die Antwort nicht erzeugen.");
        }
        if (response.incompleteDetails().isPresent()) {
            throw new AiIncompleteResponseException("OpenAI lieferte eine unvollständige Antwort.");
        }
        for (var outputItem : response.output()) {
            if (!outputItem.isMessage()) {
                continue;
            }
            for (var content : outputItem.asMessage().content()) {
                if (content.isRefusal()) {
                    throw new AiRequestRefusedException("OpenAI hat die Anfrage abgelehnt.");
                }
                if (content.isOutputText()) {
                    return content.asOutputText();
                }
            }
        }
        throw new AiIncompleteResponseException(
                "OpenAI lieferte keine vollständige strukturierte Antwort.");
    }
}
