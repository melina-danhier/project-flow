package de.melinadanhier.projectflow.ai;

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
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.*;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIServiceException;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.provider.openai.OpenAiGenerationOutput;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import com.openai.services.blocking.ResponseService;
import de.melinadanhier.projectflow.ai.exception.*;
import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.prompt.AiPrompt;
import de.melinadanhier.projectflow.ai.provider.openai.SdkOpenAiResponsesGateway;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class SdkOpenAiResponsesGatewayTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void usesSdkDeserializationAndForwardsPromptModelResponseTypeAndLimit() {
        OpenAIClient client = mock(OpenAIClient.class);
        ResponseService responses = mock(ResponseService.class);
        when(client.responses()).thenReturn(responses);
        Response raw = rawResponse("{\"problems\":[]}");
        var rawOutput = raw.output();
        when(raw._output()).thenReturn(com.openai.core.JsonField.of(rawOutput));
        var structured = new StructuredResponse<>(AiPreCheckResult.class, raw);
        when(responses.create(any(StructuredResponseCreateParams.class))).thenReturn(structured);
        var gateway = new SdkOpenAiResponsesGateway(client, 8192);
        var result = gateway.execute("model", new AiPrompt("v1", "instructions", "input"), AiPreCheckResult.class);
        assertThat(result.problems()).isEmpty();
        assertThat(result).isSameAs(structured.output().getFirst().asMessage().content().getFirst().asOutputText());
        var captor = ArgumentCaptor.forClass(StructuredResponseCreateParams.class);
        verify(responses).create(captor.capture());
        assertThat(captor.getValue().responseType()).isEqualTo(AiPreCheckResult.class);
        var params = captor.getValue().rawParams();
        assertThat(params.model().orElseThrow().asString()).isEqualTo("model");
        assertThat(params.instructions()).contains("instructions");
        assertThat(params.input().orElseThrow().asText()).isEqualTo("input");
        assertThat(params.maxOutputTokens()).contains(8192L);
        assertThat(params.store()).contains(false);
    }

    @Test
    void sdkDeserializesGenerationWithOptionalFields() {
        String json = """
                {"sections":[{
                  "tempId":null,"title":"Section","description":null,"order":1,
                  "tasks":[{
                    "tempId":"task-1","title":"Aufgabe","description":null,"estimatedHours":null,
                    "startDate":null,"dueDate":null,"criticalAssumption":null,"origin":"AI_INFERRED",
                    "order":1,"prerequisiteTaskTempIds":[],"priority":null
                  }],
                  "milestones":[{"tempId":null,"title":"Meilenstein","date":null,"order":1}]
                }]}
                """;
        var output = executeRaw(rawResponse(json), OpenAiGenerationOutput.class);
        assertThat(output.sections()).singleElement().satisfies(section -> {
            assertThat(section.tempId()).isEmpty();
            assertThat(section.description()).isEmpty();
            assertThat(section.tasks()).singleElement().satisfies(task -> {
                assertThat(task.description()).isEmpty();
                assertThat(task.estimatedHours()).isEmpty();
                assertThat(task.startDate()).isEmpty();
                assertThat(task.dueDate()).isEmpty();
                assertThat(task.criticalAssumption()).isEmpty();
                assertThat(task.priority()).isEmpty();
            });
            assertThat(section.milestones()).singleElement().satisfies(milestone -> {
                assertThat(milestone.tempId()).isEmpty();
                assertThat(milestone.date()).isEmpty();
            });
        });
    }

    @Test
    void rejectsRefusalIncompleteMissingAndUndeserializableOutputs() {
        Response refused = mock(Response.class);
        when(refused.output()).thenReturn(List.of(ResponseOutputItem.ofMessage(ResponseOutputMessage.builder()
                .id("message").status(ResponseOutputMessage.Status.COMPLETED).addRefusalContent("refused").build())));
        assertThatThrownBy(() -> executeRaw(refused, AiPreCheckResult.class))
                .isInstanceOfSatisfying(AiTechnicalException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(AiTechnicalErrorCode.AI_REFUSAL));
        Response incomplete = rawResponse("{\"problems\":[]}");
        when(incomplete.status()).thenReturn(Optional.of(ResponseStatus.INCOMPLETE));
        for (Response response : java.util.Arrays.asList(null, mock(Response.class), incomplete,
                rawResponse("{not-json"), rawResponse("{\"problems\":[null]}"))) {
            assertThatThrownBy(() -> executeRaw(response, AiPreCheckResult.class))
                    .isInstanceOf(AiOutputValidationException.class);
        }
    }

    @Test
    void rejectsIncompleteDetailsEvenWhenStatusIsCompleted() {
        Response raw = rawResponse("{\"problems\":[]}");
        when(raw.status()).thenReturn(Optional.of(ResponseStatus.COMPLETED));
        when(raw.incompleteDetails()).thenReturn(Optional.of(mock(Response.IncompleteDetails.class)));

        assertThatThrownBy(() -> executeRaw(raw, AiPreCheckResult.class))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"in_progress", "incomplete"})
    void rejectsUnfinishedMessagesEvenWithValidJson(String status) {
        var message = ResponseOutputMessage.builder().id("message").status(ResponseOutputMessage.Status.of(status))
                .addContent(ResponseOutputText.builder().text("{\"problems\":[]}").annotations(List.of()).build()).build();

        assertThatThrownBy(() -> executeRaw(rawResponse(ResponseOutputItem.ofMessage(message)), AiPreCheckResult.class))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsMultipleStructuredOutputsWithinOrAcrossMessages(boolean separateMessages) {
        var text = ResponseOutputText.builder().text("{\"problems\":[]}").annotations(List.of()).build();
        var message = ResponseOutputMessage.builder().id("message").status(ResponseOutputMessage.Status.COMPLETED)
                .addContent(text);
        Response raw = separateMessages
                ? rawResponse(ResponseOutputItem.ofMessage(message.build()), ResponseOutputItem.ofMessage(message.build()))
                : rawResponse(ResponseOutputItem.ofMessage(message.addContent(text).build()));

        assertThatThrownBy(() -> executeRaw(raw, AiPreCheckResult.class))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void doesNotReturnValidOutputWhenFollowedByRefusal() {
        var message = ResponseOutputMessage.builder().id("message").status(ResponseOutputMessage.Status.COMPLETED)
                .addContent(ResponseOutputText.builder().text("{\"problems\":[]}").annotations(List.of()).build())
                .addRefusalContent("refused").build();

        assertThatThrownBy(() -> executeRaw(rawResponse(ResponseOutputItem.ofMessage(message)), AiPreCheckResult.class))
                .isInstanceOfSatisfying(AiTechnicalException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(AiTechnicalErrorCode.AI_REFUSAL));
    }

    @ParameterizedTest
    @CsvSource({"server_error,PROVIDER_UNAVAILABLE", "rate_limit_exceeded,RATE_LIMIT_EXCEEDED",
            "invalid_prompt,CLIENT_CONFIGURATION_ERROR", "vector_store_timeout,PROVIDER_TIMEOUT",
            "image_content_policy_violation,AI_REFUSAL", "future_code,UNKNOWN_AI_ERROR"})
    void mapsErrorResponseWithoutMakingEveryFailureRetryable(String code, AiTechnicalErrorCode expected) {
        Response raw = mock(Response.class);
        when(raw.error()).thenReturn(Optional.of(ResponseError.builder()
                .code(ResponseError.Code.of(code)).message("provider detail").build()));
        assertThatThrownBy(() -> executeRaw(raw, AiPreCheckResult.class))
                .isInstanceOfSatisfying(AiTechnicalException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void mapsSdkServiceExceptionUsingSharedStatusClassification() {
        var exception = mock(OpenAIServiceException.class);
        when(exception.statusCode()).thenReturn(499);
        assertMapping(exception, AiTechnicalErrorCode.CLIENT_CONFIGURATION_ERROR, null);
    }

    @Test
    void mapsUnknownSdkErrorWithoutHidingProgrammingErrors() {
        assertMapping(new OpenAIException("future SDK error"), AiTechnicalErrorCode.UNKNOWN_AI_ERROR, null);
        OpenAIClient client = mock(OpenAIClient.class);
        var bug = new IllegalStateException("bug");
        when(client.responses()).thenThrow(bug);
        assertThatThrownBy(() -> new SdkOpenAiResponsesGateway(client, 8192).execute(
                "model", new AiPrompt("v1", "instructions", "input"), TestOutput.class)).isSameAs(bug);
    }

    private Response rawResponse(String json) {
        return rawResponse(ResponseOutputItem.ofMessage(ResponseOutputMessage.builder()
                .id("message").status(ResponseOutputMessage.Status.COMPLETED)
                .addContent(ResponseOutputText.builder().text(json).annotations(List.of()).build()).build()));
    }

    private Response rawResponse(ResponseOutputItem... items) {
        Response raw = mock(Response.class);
        when(raw.output()).thenReturn(List.of(items));
        return raw;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> T executeRaw(Response raw, Class<T> type) {
        if (raw != null) {
            var rawOutput = raw.output();
            when(raw._output()).thenReturn(com.openai.core.JsonField.of(rawOutput));
        }
        OpenAIClient client = mock(OpenAIClient.class);
        ResponseService responses = mock(ResponseService.class);
        when(client.responses()).thenReturn(responses);
        when(responses.create(any(StructuredResponseCreateParams.class)))
                .thenReturn(raw == null ? null : new StructuredResponse<>(type, raw));
        return new SdkOpenAiResponsesGateway(client, 8192)
                .execute("model", new AiPrompt("v1", "instructions", "input"), type);
    }

    @Test
    void mapsSdkTimeoutByCauseType() {
        var cause = new SocketTimeoutException("read timed out");
        assertMapping(new OpenAIIoException("I/O", cause), AiTechnicalErrorCode.PROVIDER_TIMEOUT, cause);
    }

    @Test
    void mapsNestedTimeoutInsideRetryableSdkException() {
        var timeout = new HttpTimeoutException("request timed out");
        var exception = new OpenAIRetryableException("retryable", new IOException("wrapped", timeout));
        assertMapping(exception, AiTechnicalErrorCode.PROVIDER_TIMEOUT, timeout);
    }

    @Test
    void preservesThreadInterruptionInsteadOfTreatingItAsTimeout() {
        var cause = new InterruptedIOException("interrupted");
        var exception = new OpenAIIoException("I/O", cause);
        assertMapping(exception, AiTechnicalErrorCode.PROVIDER_TIMEOUT, cause);

        Thread.currentThread().interrupt();
        try {
            assertMapping(exception, AiTechnicalErrorCode.PROVIDER_UNAVAILABLE, cause);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void mapsSdkRateLimitToDedicatedCause() {
        assertMapping(mock(RateLimitException.class), AiTechnicalErrorCode.RATE_LIMIT_EXCEEDED, null);
    }

    @Test
    void mapsConnectionAndServerFailuresToUnavailable() {
        assertMapping(new OpenAIIoException("connection reset"), AiTechnicalErrorCode.PROVIDER_UNAVAILABLE, null);
        assertMapping(new OpenAIRetryableException("retryable"), AiTechnicalErrorCode.PROVIDER_UNAVAILABLE, null);
        assertMapping(mock(InternalServerException.class), AiTechnicalErrorCode.PROVIDER_UNAVAILABLE, null);
    }

    @Test
    void mapsTypedGatewayTimeoutToTimeout() {
        var exception = mock(InternalServerException.class);
        when(exception.statusCode()).thenReturn(504);
        assertMapping(exception, AiTechnicalErrorCode.PROVIDER_TIMEOUT, null);
    }

    @ParameterizedTest
    @MethodSource("configurationFailures")
    void mapsRequestAndConfigurationFailuresToClientConfiguration(OpenAIServiceException exception) {
        assertMapping(exception, AiTechnicalErrorCode.CLIENT_CONFIGURATION_ERROR, null);
    }

    private static Stream<OpenAIServiceException> configurationFailures() {
        return Stream.of(mock(BadRequestException.class), mock(UnauthorizedException.class),
                mock(PermissionDeniedException.class), mock(NotFoundException.class), mock(UnprocessableEntityException.class));
    }

    @Test
    void mapsUndeserializableResponseToInvalidResponse() {
        assertMapping(mock(OpenAIInvalidDataException.class), AiTechnicalErrorCode.INVALID_AI_RESPONSE, null,
                AiOutputValidationException.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertMapping(
            RuntimeException sdkException,
            AiTechnicalErrorCode expectedCode,
            Throwable expectedOriginalCause
    ) {
        assertMapping(sdkException, expectedCode, expectedOriginalCause, AiTechnicalException.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertMapping(
            RuntimeException sdkException,
            AiTechnicalErrorCode expectedCode,
            Throwable expectedOriginalCause,
            Class<? extends AiTechnicalException> expectedType
    ) {
        OpenAIClient client = mock(OpenAIClient.class);
        ResponseService responses = mock(ResponseService.class);
        when(client.responses()).thenReturn(responses);
        doThrow(sdkException).when(responses)
                .create(any(StructuredResponseCreateParams.class));
        var gateway = new SdkOpenAiResponsesGateway(client, 16384);

        var assertion = assertThatThrownBy(() -> gateway.execute(
                "model", new AiPrompt("v1", "instructions", "input"), TestOutput.class))
                .isInstanceOf(expectedType)
                .hasCause(sdkException)
                .isInstanceOfSatisfying(AiTechnicalException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(expectedCode));
        if (expectedOriginalCause != null) {
            assertion.hasRootCause(expectedOriginalCause);
        }
    }

    private record TestOutput(String value) { }
}
