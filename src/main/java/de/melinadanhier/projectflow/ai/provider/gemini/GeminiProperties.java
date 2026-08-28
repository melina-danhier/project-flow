package de.melinadanhier.projectflow.ai.provider.gemini;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "projectflow.ai.gemini")
public class GeminiProperties {
    private String apiKey;
    private String preCheckModel = "gemini-2.5-flash";
    private String generationModel = "gemini-2.5-flash";
    private Duration timeout = Duration.ofSeconds(60);
    private int maxOutputTokens = 16384;

    public void validateActiveConfiguration() {
        if (apiKey == null || apiKey.isBlank()
                || preCheckModel == null || preCheckModel.isBlank()
                || generationModel == null || generationModel.isBlank()
                || timeout == null || timeout.toMillis() < 1 || timeout.toMillis() > Integer.MAX_VALUE
                || maxOutputTokens < 1) {
            throw new IllegalStateException("Für projectflow.ai.provider=gemini sind api-key, Modellnamen, "
                    + "ein positives Timeout in Millisekunden (max. 2147483647) und max-output-tokens erforderlich.");
        }
    }
}
