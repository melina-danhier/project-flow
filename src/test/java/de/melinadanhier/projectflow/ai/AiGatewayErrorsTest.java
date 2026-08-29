package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static de.melinadanhier.projectflow.ai.provider.AiGatewayErrors.errorCodeForStatusCode;
import static org.assertj.core.api.Assertions.assertThat;

class AiGatewayErrorsTest {

    @ParameterizedTest
    @CsvSource({
            "408,PROVIDER_TIMEOUT", "504,PROVIDER_TIMEOUT", "429,RATE_LIMIT_EXCEEDED",
            "400,CLIENT_CONFIGURATION_ERROR", "401,CLIENT_CONFIGURATION_ERROR",
            "403,CLIENT_CONFIGURATION_ERROR", "404,CLIENT_CONFIGURATION_ERROR",
            "422,CLIENT_CONFIGURATION_ERROR", "499,CLIENT_CONFIGURATION_ERROR",
            "500,PROVIDER_UNAVAILABLE", "503,PROVIDER_UNAVAILABLE",
            "599,PROVIDER_UNAVAILABLE", "600,UNKNOWN_AI_ERROR", "302,UNKNOWN_AI_ERROR"
    })
    void classifiesProviderStatusCodesCentrally(int status, AiTechnicalErrorCode expected) {
        assertThat(errorCodeForStatusCode(status)).isEqualTo(expected);
    }
}
