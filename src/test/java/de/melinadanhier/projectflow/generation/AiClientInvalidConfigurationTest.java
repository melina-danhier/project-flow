package de.melinadanhier.projectflow.generation;

import de.melinadanhier.projectflow.common.config.AiClientConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AiClientInvalidConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiClientConfiguration.class);

    @Test
    void unknownProviderPreventsSpringContextStartup() {
        contextRunner
                .withPropertyValues("projectflow.ai.provider=stbu")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .rootCause()
                            .hasMessageContaining("Unbekannter AI-Provider 'stbu'");
                });
    }
}
