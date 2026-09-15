package de.melinadanhier.projectflow.generation.service.workflow;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiWorkflowRecoveryTrigger {

    private final AiWorkflowRecoveryService recoveryService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        recoveryService.recover();
    }

    @Scheduled(cron = "${projectflow.ai.recovery-cron:-}")
    public void recoverPeriodically() {
        recoveryService.recover();
    }
}
