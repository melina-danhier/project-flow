package de.melinadanhier.projectflow.ai.provider.openai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "projectflow.ai.openai")
public class OpenAiProperties {

    private String apiKey;
    private String preCheckModel = "gpt-5-mini";
    private String generationModel = "gpt-5-mini";
    private Duration timeout = Duration.ofSeconds(60);
    private int maxOutputTokens = 16384;

    public void validateActiveConfiguration() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Für OpenAi muss api-key gesetzt sein.");
        }
        if (preCheckModel == null || preCheckModel.isBlank()
                || generationModel == null || generationModel.isBlank()
                || timeout == null || timeout.compareTo(Duration.ofMillis(1)) < 0
                || maxOutputTokens < 1) {
            throw new IllegalStateException("Für OpenAi sind Modellnamen, " +
                    "ein positives Timeout und max-output-tokens erforderlich.");
        }
    }
}
