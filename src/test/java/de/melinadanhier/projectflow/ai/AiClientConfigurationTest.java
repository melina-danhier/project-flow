package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.provider.AiClient;
import de.melinadanhier.projectflow.ai.provider.stub.StubAiClient;
import de.melinadanhier.projectflow.ai.config.AiClientConfiguration;
import de.melinadanhier.projectflow.ai.parser.AiResponseParser;
import de.melinadanhier.projectflow.ai.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.PreCheckPromptBuilder;
import de.melinadanhier.projectflow.ai.provider.gemini.GeminiAiClient;
import de.melinadanhier.projectflow.ai.provider.openai.OpenAiProjectFlowAIClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiClientConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AiClientConfiguration.class)
            .withBean(PreCheckPromptBuilder.class, () -> mock(PreCheckPromptBuilder.class))
            .withBean(GenerationPromptBuilder.class, () -> mock(GenerationPromptBuilder.class))
            .withBean(AiResponseParser.class, () -> new AiResponseParser(JsonMapper.builder().build()));

    @Test
    void unknownProviderPreventsSpringContextStartup() {
        runner.withPropertyValues("projectflow.ai.provider=stbu")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"stub", "openai", "gemini"})
    void selectsExactlyOneClientAndInitializesOnlyItsSdk(String provider) {
        runner.withPropertyValues("projectflow.ai.provider=" + provider,
                        "projectflow.ai." + provider + ".api-key=test-key")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(AiClient.class);
                    Class<?> expected = switch (provider) {
                        case "openai" -> OpenAiProjectFlowAIClient.class;
                        case "gemini" -> GeminiAiClient.class;
                        default -> StubAiClient.class;
                    };
                    assertThat(context.getBean(AiClient.class)).isInstanceOf(expected);
                    var beans = context.getBeanFactory();
                    assertThat(beans.containsSingleton("openAiSdk")).isEqualTo(provider.equals("openai"));
                    assertThat(beans.containsSingleton("geminiSdk")).isEqualTo(provider.equals("gemini"));
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"openai", "gemini"})
    void activeProviderRequiresKeyAndValidLimits(String provider) {
        runner.withPropertyValues("projectflow.ai.provider=" + provider).run(context -> assertThat(context).hasFailed());
        for (String invalid : new String[]{"max-output-tokens=0", "max-output-tokens=-1", "timeout=0ms",
                "timeout=-1ms", "pre-check-model=", "generation-model="}) {
            runner.withPropertyValues("projectflow.ai.provider=" + provider,
                            "projectflow.ai." + provider + ".api-key=test-key",
                            "projectflow.ai." + provider + "." + invalid)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void stubDoesNotValidateInactiveProviderConfiguration() {
        runner.withPropertyValues("projectflow.ai.provider=stub",
                        "projectflow.ai.openai.pre-check-model=", "projectflow.ai.gemini.generation-model=",
                        "projectflow.ai.openai.max-output-tokens=0", "projectflow.ai.gemini.timeout=0ms")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(AiClient.class));
    }
}
