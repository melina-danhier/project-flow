package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.exception.*;
import de.melinadanhier.projectflow.ai.model.AiOperation;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class AiTechnicalErrorTest {

    @Test
    void knownCodeOperationAndCauseArePreserved() {
        var providerCause = new SocketTimeoutException("Provider-Timeout");
        var exception = new AiTechnicalException(
                AiTechnicalErrorCode.PROVIDER_TIMEOUT, "Timeout", providerCause);

        AiTechnicalError preCheck = AiTechnicalError.from(exception, AiOperation.PRE_CHECK);
        AiTechnicalError generation = AiTechnicalError.from(exception, AiOperation.PLAN_GENERATION);

        assertThat(preCheck.operation()).isEqualTo(AiOperation.PRE_CHECK);
        assertThat(generation.operation()).isEqualTo(AiOperation.PLAN_GENERATION);
        assertThat(preCheck.errorCode()).isEqualTo(AiTechnicalErrorCode.PROVIDER_TIMEOUT);
        assertThat(preCheck.cause()).isSameAs(providerCause);
        assertThat(preCheck.message()).isEqualTo("Timeout");
        assertThat(preCheck.isRetryable()).isTrue();
        assertThat(preCheck.diagnosis()).isEqualTo(
                AiTechnicalErrorCode.PROVIDER_TIMEOUT.getDiagnosis());
    }

    @Test
    void unknownRuntimeExceptionUsesFallbackCodeAndNonEmptyMessage() {
        RuntimeException exception = new RuntimeException(" ");

        AiTechnicalError error = AiTechnicalError.from(exception, AiOperation.PRE_CHECK);

        assertThat(error.errorCode()).isEqualTo(AiTechnicalErrorCode.UNKNOWN_AI_ERROR);
        assertThat(error.message()).isNotBlank();
        assertThat(error.cause()).isSameAs(exception);
        assertThat(error.isRetryable()).isFalse();
    }

    @Test
    void retriesOnlyTransientProviderErrors() {
        EnumSet<AiTechnicalErrorCode> retryable = EnumSet.noneOf(AiTechnicalErrorCode.class);
        for (AiTechnicalErrorCode code : AiTechnicalErrorCode.values()) {
            if (code.isRetryable()) {
                retryable.add(code);
            }
        }

        assertThat(retryable).containsExactlyInAnyOrder(
                AiTechnicalErrorCode.PROVIDER_UNAVAILABLE,
                AiTechnicalErrorCode.PROVIDER_TIMEOUT,
                AiTechnicalErrorCode.RATE_LIMIT_EXCEEDED);
    }

}
