package de.melinadanhier.projectflow.ai.provider.gemini;

import de.melinadanhier.projectflow.ai.provider.AiResponsesGateway;

import com.google.genai.Models;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.model.AiResponseSchemas;
import de.melinadanhier.projectflow.ai.parser.AiResponseParser;
import de.melinadanhier.projectflow.ai.prompt.AiPrompt;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Set;

import static de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode.*;

@RequiredArgsConstructor
public class SdkGeminiResponsesGateway implements AiResponsesGateway {
    private static final Set<String> REFUSAL_REASONS = Set.of(
            "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY",
            "IMAGE_PROHIBITED_CONTENT");
    private final Models models;
    private final AiResponseParser parser;
    private final int maxOutputTokens;

    @Override
    public <T> T execute(String model, AiPrompt prompt, Class<T> responseType) {
        var config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(prompt.systemInstructions())))
                .responseMimeType("application/json")
                .responseJsonSchema(AiResponseSchemas.forType(responseType))
                .candidateCount(1)
                .maxOutputTokens(maxOutputTokens)
                .build();
        final GenerateContentResponse response;
        try {
            response = models.generateContent(model, prompt.confirmedUserData(), config);
        } catch (ApiException exception) {
            throw new AiTechnicalException(codeForStatus(exception.code()),
                    "Der Gemini-Aufruf ist fehlgeschlagen.", exception);
        } catch (GenAiIOException exception) {
            boolean ioFailure = false;
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                // Das SDK verwendet Jackson 2 für den Response-Umschlag; der Inhalt wird erst unten geparst.
                if (cause instanceof com.fasterxml.jackson.core.JsonProcessingException) {
                    throw new AiOutputValidationException("Gemini lieferte eine unlesbare Antwort.", exception);
                }
                if (cause instanceof java.net.SocketTimeoutException
                        || cause instanceof java.net.http.HttpTimeoutException
                        || cause instanceof java.io.InterruptedIOException && !Thread.currentThread().isInterrupted()) {
                    throw new AiTechnicalException(PROVIDER_TIMEOUT, "Der Gemini-Aufruf hat das Zeitlimit überschritten.", exception);
                }
                ioFailure |= cause instanceof java.io.IOException;
            }
            throw new AiTechnicalException(ioFailure ? PROVIDER_UNAVAILABLE : UNKNOWN_AI_ERROR,
                    "Die Gemini-Kommunikation ist fehlgeschlagen.", exception);
        }
        return parser.parse(extract(response), responseType);
    }

    private AiTechnicalErrorCode codeForStatus(int status) {
        if (status == 408 || status == 504) return PROVIDER_TIMEOUT;
        if (status == 429) return RATE_LIMIT_EXCEEDED;
        if (status >= 500 && status < 600) return PROVIDER_UNAVAILABLE;
        if (status >= 400 && status < 500) return CLIENT_CONFIGURATION_ERROR;
        return UNKNOWN_AI_ERROR;
    }

    private String extract(GenerateContentResponse response) {
        if (response == null) throw new AiOutputValidationException("Gemini lieferte keine Antwort.");
        var blockReason = response.promptFeedback().flatMap(feedback -> feedback.blockReason());
        if (blockReason.isPresent() && !"BLOCKED_REASON_UNSPECIFIED".equals(blockReason.get().toString())) {
            throw new AiTechnicalException(AI_REFUSAL, "Gemini hat die Anfrage abgelehnt.");
        }
        var candidates = response.candidates().orElse(List.of());
        if (candidates.size() != 1) {
            throw new AiOutputValidationException("Gemini lieferte nicht genau eine strukturierte Antwort.");
        }
        var candidate = candidates.getFirst();
        if (candidate == null) throw new AiOutputValidationException("Gemini lieferte einen leeren Kandidaten.");
        String finishReason = candidate.finishReason().map(Object::toString).orElse("");
        if (REFUSAL_REASONS.contains(finishReason)) {
            throw new AiTechnicalException(AI_REFUSAL, "Gemini hat die Anfrage abgelehnt.");
        }
        if (!"STOP".equals(finishReason)) {
            throw new AiOutputValidationException("Gemini lieferte eine unvollständige Antwort.");
        }
        var parts = candidate.content().flatMap(Content::parts).orElse(List.of());
        // Keine Tool-/Binär-Ausgaben stillschweigend als Textantwort akzeptieren.
        if (parts.stream().anyMatch(part -> part == null
                || (!part.thought().orElse(false) && (part.text().isEmpty() || part.functionCall().isPresent()
                || part.inlineData().isPresent() || part.fileData().isPresent())))) {
            throw new AiOutputValidationException("Gemini lieferte unerwartete Nicht-Text-Inhalte.");
        }
        return response.text();
    }
}
