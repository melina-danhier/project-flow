package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.generation.event.AiGenerationRequestedEvent;
import de.melinadanhier.projectflow.generation.event.AiPreCheckRequestedEvent;
import de.melinadanhier.projectflow.generation.event.listener.AiGenerationRequestedEventListener;
import de.melinadanhier.projectflow.generation.event.listener.AiPreCheckRequestedEventListener;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "projectflow.ai.recovery-delay=1h")
@ActiveProfiles("test")
class AiRequestedEventTransactionIntegrationTest {

    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean AiGenerationRequestedEventListener generationListener;
    @MockitoBean AiPreCheckRequestedEventListener preCheckListener;

    @Test
    void requestedWorkIsDispatchedOnlyAfterCommit() {
        var generationEvent = new AiGenerationRequestedEvent(UUID.randomUUID(), UUID.randomUUID());
        var preCheckEvent = new AiPreCheckRequestedEvent(UUID.randomUUID(), UUID.randomUUID());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(generationEvent);
            events.publishEvent(preCheckEvent);
            verify(generationListener, never()).onGenerationRequested(generationEvent);
            verify(preCheckListener, never()).onPreCheckRequested(preCheckEvent);
        });

        verify(generationListener, timeout(1_000)).onGenerationRequested(generationEvent);
        verify(preCheckListener, timeout(1_000)).onPreCheckRequested(preCheckEvent);
    }

    @Test
    void rolledBackWorkIsNeverDispatched() {
        var generationEvent = new AiGenerationRequestedEvent(UUID.randomUUID(), UUID.randomUUID());
        var preCheckEvent = new AiPreCheckRequestedEvent(UUID.randomUUID(), UUID.randomUUID());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(generationEvent);
            events.publishEvent(preCheckEvent);
            status.setRollbackOnly();
        });

        verify(generationListener, never()).onGenerationRequested(generationEvent);
        verify(preCheckListener, never()).onPreCheckRequested(preCheckEvent);
    }

    @Test
    void eventsPublishedWithoutTransactionAreIgnored() {
        var generationEvent = new AiGenerationRequestedEvent(UUID.randomUUID(), UUID.randomUUID());
        var preCheckEvent = new AiPreCheckRequestedEvent(UUID.randomUUID(), UUID.randomUUID());

        events.publishEvent(generationEvent);
        events.publishEvent(preCheckEvent);

        verify(generationListener, after(250).never()).onGenerationRequested(generationEvent);
        verify(preCheckListener, after(250).never()).onPreCheckRequested(preCheckEvent);
    }
}
