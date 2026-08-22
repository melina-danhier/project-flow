package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.config.AiClientConfiguration;
import de.melinadanhier.projectflow.ai.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.PreCheckPromptBuilder;
import de.melinadanhier.projectflow.ai.provider.openai.OpenAiProjectFlowAIClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenAiProjectFlowAIClientConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiClientConfiguration.class)
            .withBean(PreCheckPromptBuilder.class, () -> mock(PreCheckPromptBuilder.class))
            .withBean(GenerationPromptBuilder.class, () -> mock(GenerationPromptBuilder.class));

    @Test
    void selectsOpenAiClientWithEnvironmentBackedConfiguration() {
        contextRunner.withPropertyValues(
                        "projectflow.ai.provider=openai",
                        "projectflow.ai.openai.api-key=test-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AiClient.class))
                            .isInstanceOf(OpenAiProjectFlowAIClient.class);
                });
    }

    @Test
    void missingApiKeyPreventsOpenAiStartup() {
        contextRunner.withPropertyValues("projectflow.ai.provider=openai")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
                });
    }
}
