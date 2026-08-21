package de.melinadanhier.projectflow.generation.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "projectflow.ai.stub")
public class AiStubProperties {

    private StubPreCheckScenario preCheckScenario = StubPreCheckScenario.NO_PROBLEMS;
    private StubGenerationScenario generationScenario = StubGenerationScenario.WITH_DATES;

    public StubPreCheckScenario getPreCheckScenario() {
        return preCheckScenario;
    }

    public void setPreCheckScenario(StubPreCheckScenario preCheckScenario) {
        this.preCheckScenario = preCheckScenario;
    }

    public StubGenerationScenario getGenerationScenario() {
        return generationScenario;
    }

    public void setGenerationScenario(StubGenerationScenario generationScenario) {
        this.generationScenario = generationScenario;
    }
}
