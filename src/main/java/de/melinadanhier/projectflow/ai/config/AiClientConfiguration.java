package de.melinadanhier.projectflow.ai.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import de.melinadanhier.projectflow.ai.AiClient;
import de.melinadanhier.projectflow.ai.exception.AiProviderUnavailableException;
import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.PreCheckPromptBuilder;
import de.melinadanhier.projectflow.ai.provider.openai.OpenAiProjectFlowAIClient;
import de.melinadanhier.projectflow.ai.provider.openai.OpenAiProperties;
import de.melinadanhier.projectflow.ai.provider.openai.SdkOpenAiResponsesGateway;
import de.melinadanhier.projectflow.ai.provider.openai.OpenAiResponsesGateway;
import de.melinadanhier.projectflow.ai.provider.stub.StubAiClient;
import de.melinadanhier.projectflow.ai.provider.stub.StubAiProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

@Configuration
@EnableConfigurationProperties({
        StubAiProperties.class,
        OpenAiProperties.class,
        AiExecutionProperties.class
})
public class AiClientConfiguration {

    @Bean
    public AiClient aiClient(
            @Value("${projectflow.ai.provider:}") String configuredProvider,
            StubAiProperties stubProperties,
            OpenAiProperties openAiProperties,
            ObjectProvider<PreCheckPromptBuilder> preCheckPromptBuilder,
            ObjectProvider<GenerationPromptBuilder> generationPromptBuilder
    ) {
        String provider = configuredProvider.trim().toLowerCase(java.util.Locale.ROOT);
        if (provider.equals("stub")) {
            return new StubAiClient(stubProperties);
        }
        else if (provider.equals("openai")) {
            return createOpenAiClientWithConfig(openAiProperties,preCheckPromptBuilder,generationPromptBuilder);
        }
        else if (!provider.isEmpty()) {
            throw new IllegalStateException(
                    "Unbekannter AI-Provider '" + configuredProvider + "'. Unterstützt werden: stub, openai."
            );
        }
        else return new AiClient() {
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

    private AiClient createOpenAiClientWithConfig(
            OpenAiProperties openAiProperties,
            ObjectProvider<PreCheckPromptBuilder> preCheckPromptBuilder,
            ObjectProvider<GenerationPromptBuilder> generationPromptBuilder
    ) {
        OpenAIClient sdkClient = OpenAIOkHttpClient.builder()
                .apiKey(openAiProperties.requireApiKey())
                .timeout(openAiProperties.getTimeout())
                .maxRetries(0) // Retry wird durch das Backend gesteuert
                .build();

        OpenAiResponsesGateway responsesGateway =
                new SdkOpenAiResponsesGateway(sdkClient);

        return new OpenAiProjectFlowAIClient(
                responsesGateway,
                openAiProperties,
                preCheckPromptBuilder.getObject(),
                generationPromptBuilder.getObject()
        );
    }
}
