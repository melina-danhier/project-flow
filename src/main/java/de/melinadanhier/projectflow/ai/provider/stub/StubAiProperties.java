package de.melinadanhier.projectflow.ai.provider.stub;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "projectflow.ai.stub")
public class StubAiProperties {

    private StubAiPreCheckScenario preCheckScenario = StubAiPreCheckScenario.CRITICAL_ASSUMPTION;

    private StubAiGenerationScenario generationScenario = StubAiGenerationScenario.WITH_DATES;

}
