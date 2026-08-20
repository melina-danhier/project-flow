package de.melinadanhier.projectflow.common.config;

import de.melinadanhier.projectflow.generation.client.AiGenerationClient;
import de.melinadanhier.projectflow.generation.client.AiPreCheckTechnicalException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiClientConfiguration {

    @Bean
    @ConditionalOnMissingBean(AiGenerationClient.class)
    public AiGenerationClient unavailableAiGenerationClient() {
        return snapshot -> {
            throw new AiPreCheckTechnicalException("Es ist noch kein KI-Anbieter konfiguriert.");
        };
    }
}
