package de.melinadanhier.projectflow.generation;

import de.melinadanhier.projectflow.generation.service.AiPreCheckReviewSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiPreCheckReviewSessionTest {

    @Test
    void isolatesAndClearsWarningsByWorkflow() {
        AiPreCheckReviewSession reviewSession = new AiPreCheckReviewSession();
        MockHttpSession session = new MockHttpSession();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        reviewSession.ignore(first, 1, session);
        reviewSession.ignore(second, 2, session);

        assertThat(reviewSession.ignoredWarnings(first, session)).containsExactly(1);
        assertThat(reviewSession.ignoredWarnings(second, session)).containsExactly(2);

        reviewSession.clear(first, session);
        assertThat(reviewSession.ignoredWarnings(first, session)).isEmpty();
        assertThat(reviewSession.ignoredWarnings(second, session)).containsExactly(2);
    }
}
