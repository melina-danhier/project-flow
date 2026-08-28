package de.melinadanhier.projectflow.ai.provider.openai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Setter
@ConfigurationProperties(prefix = "projectflow.ai.openai")
public class OpenAiProperties {

    @Getter
    private String apiKey;
    private String preCheckModel = "gpt-5-mini";
    private String generationModel = "gpt-5-mini";
    private Duration timeout = Duration.ofSeconds(60);
    @Getter
    private int maxOutputTokens = 16384;

    public void validateActiveConfiguration() {
        requireApiKey();
        if (preCheckModel == null || preCheckModel.isBlank() || generationModel == null || generationModel.isBlank()
                || timeout == null || timeout.toMillis() < 1 || maxOutputTokens < 1) {
            throw new IllegalStateException("Für projectflow.ai.provider=openai sind Modellnamen, "
                    + "ein positives Timeout und max-output-tokens erforderlich.");
        }
    }

    @NotBlank
    public String getPreCheckModel() {
        return preCheckModel;
    }

    @NotBlank
    public String getGenerationModel() {
        return generationModel;
    }

    @NotNull
    public Duration getTimeout() {
        return timeout;
    }

    public String requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Für projectflow.ai.provider=openai muss projectflow.ai.openai.api-key gesetzt sein.");
        }
        return apiKey;
    }
}
