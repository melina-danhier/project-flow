package de.melinadanhier.projectflow.generation.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "projectflow.ai.openai")
public class OpenAiProperties {

    private String apiKey;
    private String preCheckModel = "gpt-5-mini";
    private String generationModel = "gpt-5-mini";
    private Duration timeout = Duration.ofSeconds(60);

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @NotBlank
    public String getPreCheckModel() {
        return preCheckModel;
    }

    public void setPreCheckModel(String preCheckModel) {
        this.preCheckModel = preCheckModel;
    }

    @NotBlank
    public String getGenerationModel() {
        return generationModel;
    }

    public void setGenerationModel(String generationModel) {
        this.generationModel = generationModel;
    }

    @NotNull
    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public String requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Für projectflow.ai.provider=openai muss projectflow.ai.openai.api-key gesetzt sein.");
        }
        return apiKey;
    }
}
