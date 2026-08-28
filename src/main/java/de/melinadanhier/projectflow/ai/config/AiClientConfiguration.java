package de.melinadanhier.projectflow.ai.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import de.melinadanhier.projectflow.ai.AiClient;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.PreCheckPromptBuilder;
import de.melinadanhier.projectflow.ai.provider.openai.OpenAiProjectFlowAIClient;
import de.melinadanhier.projectflow.ai.provider.openai.OpenAiProperties;
import de.melinadanhier.projectflow.ai.provider.openai.SdkOpenAiResponsesGateway;
import de.melinadanhier.projectflow.ai.provider.gemini.GeminiAiClient;
import de.melinadanhier.projectflow.ai.provider.gemini.GeminiProperties;
import de.melinadanhier.projectflow.ai.provider.gemini.SdkGeminiResponsesGateway;
import de.melinadanhier.projectflow.ai.parser.AiResponseParser;
import de.melinadanhier.projectflow.ai.provider.stub.StubAiClient;
import de.melinadanhier.projectflow.ai.provider.stub.StubAiProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;

@Configuration
@EnableConfigurationProperties({
        StubAiProperties.class,
        OpenAiProperties.class,
        GeminiProperties.class,
        AiExecutionProperties.class
})
public class AiClientConfiguration {

    @Bean
    public AiClient aiClient(
            @Value("${projectflow.ai.provider:}") String configuredProvider,
            StubAiProperties stubProperties,
            OpenAiProperties openAiProperties,
            GeminiProperties geminiProperties,
            ObjectProvider<OpenAIClient> openAiSdk,
            ObjectProvider<Client> geminiSdk,
            ObjectProvider<AiResponseParser> parser,
            ObjectProvider<PreCheckPromptBuilder> preCheckPromptBuilder,
            ObjectProvider<GenerationPromptBuilder> generationPromptBuilder
    ) {
        String provider = configuredProvider.trim().toLowerCase(java.util.Locale.ROOT);
        if (provider.equals("stub")) {
            return new StubAiClient(stubProperties);
        }
        else if (provider.equals("openai")) {
            return new OpenAiProjectFlowAIClient(
                    new SdkOpenAiResponsesGateway(openAiSdk.getObject(), openAiProperties.getMaxOutputTokens()),
                    openAiProperties, preCheckPromptBuilder.getObject(), generationPromptBuilder.getObject());
        }
        else if (provider.equals("gemini")) {
            return new GeminiAiClient(
                    new SdkGeminiResponsesGateway(geminiSdk.getObject().models, parser.getObject(),
                            geminiProperties.getMaxOutputTokens()),
                    geminiProperties, preCheckPromptBuilder.getObject(), generationPromptBuilder.getObject());
        }
        else if (!provider.isEmpty()) {
            throw new IllegalStateException(
                    "Unbekannter AI-Provider '" + configuredProvider + "'. Unterstützt werden: stub, openai, gemini."
            );
        }
        else return new AiClient() {
            @Override
            public AiPreCheckResult preCheck(AiPreCheckRequest request) {
                throw new AiTechnicalException(
                        AiTechnicalErrorCode.CLIENT_CONFIGURATION_ERROR,
                        "Es ist noch kein AI-Provider konfiguriert."
                );
            }

            @Override
            public GeneratedPlanResponse generatePlan(AiGenerationRequest request) {
                throw new AiTechnicalException(
                        AiTechnicalErrorCode.CLIENT_CONFIGURATION_ERROR,
                        "Es ist noch kein AI-Provider konfiguriert."
                );
            }
        };
    }

    // Lazy: ausschließlich der ausgewählte Adapter fordert sein SDK an. Spring schließt es beim Shutdown.
    @Bean(destroyMethod = "close")
    @Lazy
    public OpenAIClient openAiSdk(OpenAiProperties openAiProperties) {
        openAiProperties.validateActiveConfiguration();
        return OpenAIOkHttpClient.builder()
                .apiKey(openAiProperties.requireApiKey())
                .timeout(openAiProperties.getTimeout())
                .maxRetries(0) // Retry wird durch das Backend gesteuert
                .build();
    }

    @Bean(destroyMethod = "close")
    @Lazy
    public Client geminiSdk(GeminiProperties properties) {
        properties.validateActiveConfiguration();
        return Client.builder().vertexAI(false).apiKey(properties.getApiKey())
                .httpOptions(HttpOptions.builder()
                        .timeout(Math.toIntExact(properties.getTimeout().toMillis()))
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build()).build())
                .build();
    }
}
