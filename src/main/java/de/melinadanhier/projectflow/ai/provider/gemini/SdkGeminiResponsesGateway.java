package de.melinadanhier.projectflow.ai.provider.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.genai.Models;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.*;
import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.model.AiResponseSchemas;
import de.melinadanhier.projectflow.ai.parser.AiResponseParser;
import de.melinadanhier.projectflow.ai.prompt.AiPrompt;
import de.melinadanhier.projectflow.ai.provider.AiResponsesGateway;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode.*;
import static de.melinadanhier.projectflow.ai.provider.AiGatewayErrors.errorCodeForStatusCode;
import static de.melinadanhier.projectflow.ai.provider.AiGatewayErrors.isTimeout;

@RequiredArgsConstructor
public class SdkGeminiResponsesGateway implements AiResponsesGateway {

    private static final Set<String> REFUSAL_REASONS = Set.of(
            "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII",
            "IMAGE_SAFETY", "IMAGE_PROHIBITED_CONTENT");

    private final Models models;
    private final AiResponseParser parser;
    private final int maxOutputTokens;

    @Override
    public <T> T execute(String model, AiPrompt prompt, Class<T> responseType) {
        GenerateContentConfig config = buildConfig(prompt.systemInstructions(), responseType);
        GenerateContentResponse response = requestContent(model, prompt.confirmedUserData(), config);
        String json = extractJson(response);
        return parser.parse(json, responseType);
    }

    private GenerateContentConfig buildConfig(String systemInstructions, Class<?> responseType) {
        return GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(systemInstructions)))
                .responseMimeType("application/json")
                .responseJsonSchema(AiResponseSchemas.forType(responseType))
                .candidateCount(1)
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    private GenerateContentResponse requestContent(String model, String userData, GenerateContentConfig config) {
        try {
            return models.generateContent(model, userData, config);
        } catch (ApiException exception) {
            throw new AiTechnicalException(
                    errorCodeForStatusCode(exception.code()),
                    "Der Gemini-Aufruf ist fehlgeschlagen.",
                    exception
            );
        } catch (GenAiIOException exception) {
            throw translateCommunicationException(exception);
        }
    }

    private AiTechnicalException translateCommunicationException(GenAiIOException exception) {
        boolean hasIoCause = false;
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof JsonProcessingException) {
                return new AiOutputValidationException(
                        "Gemini lieferte eine unlesbare Antwort.",
                        exception
                );
            }
            if (isTimeout(cause)) {
                return new AiTechnicalException(
                        PROVIDER_TIMEOUT,
                        "Der Gemini-Aufruf hat das Zeitlimit überschritten.",
                        exception
                );
            }
            hasIoCause |= cause instanceof IOException;
        }
        AiTechnicalErrorCode errorCode = hasIoCause ? PROVIDER_UNAVAILABLE : UNKNOWN_AI_ERROR;
        return new AiTechnicalException(
                errorCode,
                "Die Gemini-Kommunikation ist fehlgeschlagen.",
                exception
        );
    }

    private String extractJson(GenerateContentResponse response) {
        if (response == null) {
            throw new AiOutputValidationException("Gemini lieferte keine Antwort.");
        }
        validatePromptFeedback(response);
        Candidate candidate = requireSingleCandidate(response);
        validateFinishReason(candidate);
        validateTextParts(candidate);
        return response.text();
    }

    private void validatePromptFeedback(GenerateContentResponse response) {
        Optional<BlockedReason> blockReason = response.promptFeedback()
                .flatMap(GenerateContentResponsePromptFeedback::blockReason);
        if (blockReason.isPresent() && !blockReason.get().toString().equals("BLOCKED_REASON_UNSPECIFIED")) {
            throw new AiTechnicalException(
                    AI_REFUSAL,
                    "Gemini hat die Anfrage abgelehnt."
            );
        }
    }

    private Candidate requireSingleCandidate(GenerateContentResponse response) {
        List<Candidate> candidates = response.candidates().orElse(List.of());
        if (candidates.size() != 1) {
            throw new AiOutputValidationException("Gemini lieferte nicht genau eine strukturierte Antwort.");
        }
        Candidate candidate = candidates.getFirst();
        if (candidate == null) {
            throw new AiOutputValidationException("Gemini lieferte einen leeren Kandidaten.");
        }
        return candidate;
    }

    private void validateFinishReason(Candidate candidate) {
        String finishReason = candidate.finishReason().map(Object::toString).orElse("");
        if (REFUSAL_REASONS.contains(finishReason)) {
            throw new AiTechnicalException(AI_REFUSAL, "Gemini hat die Anfrage abgelehnt.");
        }
        if (!finishReason.equals("STOP")) {
            throw new AiOutputValidationException("Gemini lieferte eine unvollständige Antwort.");
        }
    }

    private void validateTextParts(Candidate candidate) {
        List<Part> parts = candidate.content().flatMap(Content::parts).orElse(List.of());
        if (parts.stream().anyMatch(this::isUnexpectedPart)) {
            throw new AiOutputValidationException("Gemini lieferte unerwartete Nicht-Text-Inhalte.");
        }
    }

    private boolean isUnexpectedPart(Part part) {
        if (part == null) return true;
        if (part.thought().isPresent()) return false;
        return part.text().isEmpty()
                || part.functionCall().isPresent()
                || part.inlineData().isPresent()
                || part.fileData().isPresent();
    }
}
