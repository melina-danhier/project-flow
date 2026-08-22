package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.provider.stub.StubAiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "projectflow.ai.provider=stub")
@ActiveProfiles("test")
class AiClientConfigurationTest {

    @Autowired
    private AiClient aiClient;

    @Test
    void selectsStubClientThroughProviderProperty() {
        assertThat(aiClient).isInstanceOf(StubAiClient.class);
    }
}
