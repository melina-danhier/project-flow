package de.melinadanhier.projectflow.ai;

import com.google.genai.Models;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.*;
import de.melinadanhier.projectflow.ai.exception.*;
import de.melinadanhier.projectflow.ai.model.AiResponseSchemas;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.parser.AiResponseParser;
import de.melinadanhier.projectflow.ai.prompt.AiPrompt;
import de.melinadanhier.projectflow.ai.provider.gemini.SdkGeminiResponsesGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SdkGeminiResponsesGatewayTest {
    private final Models models = mock(Models.class);
    private final AiResponseParser parser = spy(new AiResponseParser(JsonMapper.builder().build()));
    private final SdkGeminiResponsesGateway gateway = new SdkGeminiResponsesGateway(models, parser, 8192);
    private final AiPrompt prompt = new AiPrompt("v1", "instructions", "confirmed input");

    @Test
    void forwardsPromptModelSchemaAndLimitAndParsesExactlyOnce() {
        String json = "{\"problems\":[]}";
        when(models.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
                .thenReturn(response("STOP", json));
        assertThat(execute().problems()).isEmpty();
        var config = ArgumentCaptor.forClass(GenerateContentConfig.class);
        verify(models).generateContent(eq("model"), eq("confirmed input"), config.capture());
        assertThat(config.getValue().systemInstruction().orElseThrow().text()).isEqualTo("instructions");
        assertThat(config.getValue().maxOutputTokens()).contains(8192);
        assertThat(config.getValue().candidateCount()).contains(1);
        assertThat(config.getValue().responseMimeType()).contains("application/json");
        assertThat(config.getValue().responseJsonSchema()).contains(AiResponseSchemas.forType(AiPreCheckResult.class));
        verify(parser).parse(json, AiPreCheckResult.class);
        verifyNoMoreInteractions(parser);
    }

    @Test
    void deserializesPlanToSharedDtoAndLeavesDomainValidationToService() {
        when(models.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
                .thenReturn(response("STOP", "{\"phases\":[]}"));
        assertThat(gateway.execute("model", prompt, GeneratedPlanResponse.class).phases()).isEmpty();
        verify(parser).parse("{\"phases\":[]}", GeneratedPlanResponse.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII",
            "IMAGE_SAFETY", "IMAGE_PROHIBITED_CONTENT"})
    void mapsRefusalWithoutParsing(String reason) {
        when(models.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
                .thenReturn(response(reason, "ignored"));
        assertCode(AiTechnicalErrorCode.AI_REFUSAL);
        verifyNoInteractions(parser);
    }

    @Test
    void mapsPromptBlockBeforeMissingCandidates() {
        when(models.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
                .thenReturn(GenerateContentResponse.builder().promptFeedback(
                        GenerateContentResponsePromptFeedback.builder().blockReason(new BlockedReason("SAFETY")).build()).build());
        assertCode(AiTechnicalErrorCode.AI_REFUSAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MAX_TOKENS", "OTHER", "FINISH_REASON_UNSPECIFIED", "FUTURE_REASON"})
    void rejectsIncompleteOrUnknownCompletionEvenWithParseableText(String reason) {
        when(models.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
                .thenReturn(response(reason, "{\"problems\":[]}"));
        assertCode(AiTechnicalErrorCode.INVALID_AI_RESPONSE);
        verifyNoInteractions(parser);
    }

    @Test
    void rejectsMissingOutputAndMalformedJson() {
        for (var response : java.util.Arrays.asList(null, GenerateContentResponse.builder().build(),
                response("STOP", ""), response("STOP", "{not-json"), response("STOP", "null"))) {
            when(models.generateContent(anyString(), anyString(), any(GenerateContentConfig.class))).thenReturn(response);
            assertCode(AiTechnicalErrorCode.INVALID_AI_RESPONSE);
        }
    }

    @Test
    void rejectsMultipleCandidatesWithoutParsing() {
        Candidate candidate = response("STOP", "{\"problems\":[]}").candidates().orElseThrow().getFirst();
        when(models.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
                .thenReturn(GenerateContentResponse.builder().candidates(List.of(candidate, candidate)).build());

        assertCode(AiTechnicalErrorCode.INVALID_AI_RESPONSE);
        verifyNoInteractions(parser);
    }

    @Test
    void rejectsMissingFinishReasonWithoutParsing() {
        Candidate candidate = Candidate.builder().content(Content.fromParts(Part.fromText("{\"problems\":[]}"))).build();
        when(models.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
                .thenReturn(GenerateContentResponse.builder().candidates(List.of(candidate)).build());

        assertCode(AiTechnicalErrorCode.INVALID_AI_RESPONSE);
        verifyNoInteractions(parser);
    }

    @ParameterizedTest
    @MethodSource("unexpectedParts")
    void rejectsNonTextPartsEvenAlongsideValidJson(Part unexpectedPart) {
        when(models.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
                .thenReturn(response("STOP", Part.fromText("{\"problems\":[]}"), unexpectedPart));

        assertCode(AiTechnicalErrorCode.INVALID_AI_RESPONSE);
        verifyNoInteractions(parser);
    }

    private static Stream<Part> unexpectedParts() {
        FunctionCall functionCall = FunctionCall.builder().name("unexpected_tool").build();
        return Stream.of(
                Part.builder().build(),
                Part.builder().functionCall(functionCall).build(),
                Part.builder().text("extra text").functionCall(functionCall).build(),
                Part.builder().text("extra text").inlineData(Blob.builder().mimeType("image/png").build()).build(),
                Part.builder().text("extra text").fileData(FileData.builder().fileUri("gs://example/file").build()).build()
        );
    }

    @Test
    void excludesThoughtPartsAndCombinesAnswerTextBeforeParsing() {
        when(models.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
                .thenReturn(response("STOP",
                        Part.builder().thought(true).text("internal reasoning").build(),
                        Part.fromText("{\"problems\":"), Part.fromText("[]}")));

        assertThat(execute().problems()).isEmpty();
        verify(parser).parse("{\"problems\":[]}", AiPreCheckResult.class);
        verifyNoMoreInteractions(parser);
    }

    @ParameterizedTest
    @CsvSource({"408,PROVIDER_TIMEOUT", "504,PROVIDER_TIMEOUT", "429,RATE_LIMIT_EXCEEDED",
            "500,PROVIDER_UNAVAILABLE", "503,PROVIDER_UNAVAILABLE", "400,CLIENT_CONFIGURATION_ERROR",
            "401,CLIENT_CONFIGURATION_ERROR", "403,CLIENT_CONFIGURATION_ERROR", "404,CLIENT_CONFIGURATION_ERROR",
            "422,CLIENT_CONFIGURATION_ERROR", "499,CLIENT_CONFIGURATION_ERROR", "599,PROVIDER_UNAVAILABLE",
            "600,UNKNOWN_AI_ERROR", "302,UNKNOWN_AI_ERROR"})
    void translatesEverySdkApiException(int status, AiTechnicalErrorCode expected) {
        var exception = new ApiException(status, "status", "provider detail");
        when(models.generateContent(anyString(), anyString(), any(GenerateContentConfig.class))).thenThrow(exception);
        assertCode(expected);
        assertThatThrownBy(this::execute).hasCause(exception);
    }

    @Test
    void mapsTypedNetworkTimeoutAndSdkDeserializationFailures() {
        var exceptions = List.of(new GenAiIOException(new SocketTimeoutException()),
                new GenAiIOException(new IOException("connection reset")),
                new GenAiIOException(new com.fasterxml.jackson.core.JsonParseException(null, "broken envelope")),
                new GenAiIOException("unknown SDK failure"), new GenAiIOException(new java.io.InterruptedIOException()));
        var codes = List.of(AiTechnicalErrorCode.PROVIDER_TIMEOUT, AiTechnicalErrorCode.PROVIDER_UNAVAILABLE,
                AiTechnicalErrorCode.INVALID_AI_RESPONSE, AiTechnicalErrorCode.UNKNOWN_AI_ERROR, AiTechnicalErrorCode.PROVIDER_TIMEOUT);
        for (int i = 0; i < exceptions.size(); i++) {
            doThrow(exceptions.get(i)).when(models).generateContent(anyString(), anyString(), any(GenerateContentConfig.class));
            assertCode(codes.get(i));
        }
    }

    @Test
    void recognizesTimeoutsInsideWrappedIoExceptions() {
        var exception = new GenAiIOException(new IOException("wrapped", new HttpTimeoutException("timeout")));
        when(models.generateContent(anyString(), anyString(), any(GenerateContentConfig.class))).thenThrow(exception);

        assertThatThrownBy(this::execute)
                .hasCause(exception)
                .isInstanceOfSatisfying(AiTechnicalException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo(AiTechnicalErrorCode.PROVIDER_TIMEOUT));
        verifyNoInteractions(parser);
    }

    @Test
    void preservesThreadInterruptionAndDoesNotClassifyItAsTimeout() {
        when(models.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
                .thenThrow(new GenAiIOException(new InterruptedIOException()));

        Thread.currentThread().interrupt();
        try {
            assertCode(AiTechnicalErrorCode.PROVIDER_UNAVAILABLE);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        verifyNoInteractions(parser);
    }

    @Test
    void doesNotHideProgrammingErrors() {
        var bug = new IllegalStateException("bug");
        when(models.generateContent(anyString(), anyString(), any(GenerateContentConfig.class))).thenThrow(bug);
        assertThatThrownBy(this::execute).isSameAs(bug);
    }

    private AiPreCheckResult execute() {
        return gateway.execute("model", prompt, AiPreCheckResult.class);
    }

    private void assertCode(AiTechnicalErrorCode expected) {
        assertThatThrownBy(this::execute).isInstanceOfSatisfying(AiTechnicalException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    private GenerateContentResponse response(String reason, String text) {
        return response(reason, Part.fromText(text));
    }

    private GenerateContentResponse response(String reason, Part... parts) {
        return GenerateContentResponse.builder().candidates(List.of(Candidate.builder()
                .finishReason(new FinishReason(reason)).content(Content.fromParts(parts)).build())).build();
    }
}
