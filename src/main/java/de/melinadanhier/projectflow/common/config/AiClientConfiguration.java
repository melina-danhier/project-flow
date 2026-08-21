package de.melinadanhier.projectflow.common.config;

import de.melinadanhier.projectflow.generation.client.AiClient;
import de.melinadanhier.projectflow.generation.client.AiProviderUnavailableException;
import de.melinadanhier.projectflow.generation.client.AiStubProperties;
import de.melinadanhier.projectflow.generation.client.StubAiClient;
import de.melinadanhier.projectflow.generation.dto.request.AiGenerationRequest;
import de.melinadanhier.projectflow.generation.dto.request.AiPreCheckRequest;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckResult;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPlanResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiStubProperties.class)
public class AiClientConfiguration {

    @Bean
    public AiClient aiClient(
            @Value("${projectflow.ai.provider:}") String configuredProvider,
            AiStubProperties properties
    ) {
        String provider = configuredProvider.trim().toLowerCase(java.util.Locale.ROOT);
        if (provider.equals("stub")) {
            return new StubAiClient(properties);
        }
        if (!provider.isEmpty()) {
            throw new IllegalStateException(
                    "Unbekannter AI-Provider '" + configuredProvider + "'. Unterstützt wird derzeit: stub.");
        }
        return new AiClient() {
            @Override
            public AiPreCheckResult preCheck(AiPreCheckRequest request) {
                throw new AiProviderUnavailableException("Es ist noch kein AI-Provider konfiguriert.");
            }

            @Override
            public GeneratedPlanResponse generatePlan(AiGenerationRequest request) {
                throw new AiProviderUnavailableException("Es ist noch kein AI-Provider konfiguriert.");
            }
        };
    }
}
