package de.melinadanhier.projectflow.generation.service.workflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiWorkflowRecoveryTriggerTest {

    private final AiWorkflowRecoveryService recoveryService = mock(AiWorkflowRecoveryService.class);
    private AiWorkflowRecoveryTrigger trigger;

    @BeforeEach
    void setUp() {
        trigger = new AiWorkflowRecoveryTrigger(recoveryService);
    }

    @Test
    void applicationStartupRunsRecovery() {
        trigger.recoverOnStartup();

        verify(recoveryService).recover();
    }

    @Test
    void configuredPeriodicFallbackRunsRecovery() {
        trigger.recoverPeriodically();

        verify(recoveryService).recover();
    }
}
