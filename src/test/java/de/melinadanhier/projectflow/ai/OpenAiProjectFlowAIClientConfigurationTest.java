package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.provider.openai.OpenAiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiProjectFlowAIClientConfigurationTest {

    @ParameterizedTest
    @MethodSource("invalidTimeouts")
    void rejectsMissingOrSubMillisecondTimeouts(Duration timeout) {
        OpenAiProperties properties = new OpenAiProperties();
        properties.setApiKey("test-key");
        properties.setTimeout(timeout);

        assertThatThrownBy(properties::validateActiveConfiguration)
                .isInstanceOf(IllegalStateException.class);
    }

    private static Stream<Duration> invalidTimeouts() {
        return Stream.of(null, Duration.ZERO, Duration.ofMillis(-1), Duration.ofNanos(999_999));
    }

    @Test
    void acceptsOneMillisecondTimeout() {
        OpenAiProperties properties = new OpenAiProperties();
        properties.setApiKey("test-key");
        properties.setTimeout(Duration.ofMillis(1));

        assertThatCode(properties::validateActiveConfiguration).doesNotThrowAnyException();
    }
}
