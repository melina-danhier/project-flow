package de.melinadanhier.projectflow.generation;

import de.melinadanhier.projectflow.common.config.AiClientConfiguration;
import de.melinadanhier.projectflow.generation.client.OpenAiClient;
import de.melinadanhier.projectflow.generation.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.generation.prompt.PreCheckPromptBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenAiClientConfigurationTest {

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
                    assertThat(context.getBean(de.melinadanhier.projectflow.generation.client.AiClient.class))
                            .isInstanceOf(OpenAiClient.class);
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
