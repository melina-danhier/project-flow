package de.melinadanhier.projectflow.ai;

import com.openai.client.OpenAIClient;
import com.openai.errors.BadRequestException;
import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.RateLimitException;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.services.blocking.ResponseService;
import de.melinadanhier.projectflow.ai.exception.*;
import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.prompt.AiPrompt;
import de.melinadanhier.projectflow.ai.provider.openai.SdkOpenAiResponsesGateway;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SdkOpenAiResponsesGatewayTest {

    @Test
    void mapsSdkTimeoutByCauseType() {
        var cause = new SocketTimeoutException("read timed out");
        assertMapping(new OpenAIIoException("I/O", cause), AiTechnicalErrorCode.PROVIDER_TIMEOUT, cause);
    }

    @Test
    void mapsSdkRateLimitToDedicatedCause() {
        assertMapping(mock(RateLimitException.class), AiTechnicalErrorCode.RATE_LIMIT_EXCEEDED, null);
    }

    @Test
    void mapsConnectionAndServerFailuresToUnavailable() {
        assertMapping(new OpenAIIoException("connection reset"), AiTechnicalErrorCode.PROVIDER_UNAVAILABLE, null);
        assertMapping(mock(InternalServerException.class), AiTechnicalErrorCode.PROVIDER_UNAVAILABLE, null);
    }

    @Test
    void mapsRequestAndConfigurationFailuresToClientConfiguration() {
        assertMapping(mock(BadRequestException.class), AiTechnicalErrorCode.CLIENT_CONFIGURATION_ERROR, null);
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
        var gateway = new SdkOpenAiResponsesGateway(client);

        var assertion = assertThatThrownBy(() -> gateway.execute(
                "model", new AiPrompt("v1", "instructions", "input"), TestOutput.class))
                .isInstanceOf(expectedType)
                .isInstanceOfSatisfying(AiTechnicalException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(expectedCode));
        if (expectedOriginalCause != null) {
            assertion.hasRootCause(expectedOriginalCause);
        }
    }

    private record TestOutput(String value) { }
}
