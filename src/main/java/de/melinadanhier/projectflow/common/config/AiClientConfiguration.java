package de.melinadanhier.projectflow.common.config;

import de.melinadanhier.projectflow.generation.client.AiClient;
import de.melinadanhier.projectflow.generation.client.AiProviderUnavailableException;
import de.melinadanhier.projectflow.generation.client.AiExecutionProperties;
import de.melinadanhier.projectflow.generation.client.AiStubProperties;
import de.melinadanhier.projectflow.generation.client.OpenAiClient;
import de.melinadanhier.projectflow.generation.client.OpenAiProperties;
import de.melinadanhier.projectflow.generation.client.StubAiClient;
import de.melinadanhier.projectflow.generation.client.SdkOpenAiResponsesGateway;
import de.melinadanhier.projectflow.generation.dto.request.AiGenerationRequest;
import de.melinadanhier.projectflow.generation.dto.request.AiPreCheckRequest;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckResult;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPlanResponse;
import de.melinadanhier.projectflow.generation.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.generation.prompt.PreCheckPromptBuilder;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

@Configuration
@EnableConfigurationProperties({AiStubProperties.class, OpenAiProperties.class, AiExecutionProperties.class})
public class AiClientConfiguration {

    @Bean
    public AiClient aiClient(
            @Value("${projectflow.ai.provider:}") String configuredProvider,
            AiStubProperties stubProperties,
            OpenAiProperties openAiProperties,
            ObjectProvider<PreCheckPromptBuilder> preCheckPromptBuilder,
            ObjectProvider<GenerationPromptBuilder> generationPromptBuilder
    ) {
        String provider = configuredProvider.trim().toLowerCase(java.util.Locale.ROOT);
        if (provider.equals("stub")) {
            return new StubAiClient(stubProperties);
        }
        if (provider.equals("openai")) {
            var sdkClient = OpenAIOkHttpClient.builder()
                    .apiKey(openAiProperties.requireApiKey())
                    .timeout(openAiProperties.getTimeout())
                    // Retries werden bewusst im providerunabhängigen Workflow ausgeführt.
                    .maxRetries(0)
                    .build();
            return new OpenAiClient(
                    new SdkOpenAiResponsesGateway(sdkClient),
                    openAiProperties,
                    preCheckPromptBuilder.getObject(),
                    generationPromptBuilder.getObject());
        }
        if (!provider.isEmpty()) {
            throw new IllegalStateException(
                    "Unbekannter AI-Provider '" + configuredProvider
                            + "'. Unterstützt werden: stub, openai.");
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
