package de.melinadanhier.projectflow.generation.service.workflow;

import de.melinadanhier.projectflow.ai.config.AiExecutionProperties;
import de.melinadanhier.projectflow.generation.event.AiGenerationRequestedEvent;
import de.melinadanhier.projectflow.generation.event.AiPreCheckRequestedEvent;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class AiWorkflowRecoveryService {

    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final AiExecutionProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${projectflow.ai.recovery-delay:30s}")
    @Transactional
    public void recover() {
        // Gegenüber dem externen Provider ist keine echte Exactly-once-Garantie möglich:
        // Nach einem Prozessabsturz kann ein veralteter Claim erneut ausgeführt werden.
        Instant now = Instant.now(clock);
        Instant cutoff = now.minus(properties.getStaleWorkflowTimeout());
        workflowRepository.releaseStalePreChecks(cutoff, now);
        workflowRepository.releaseStaleGenerations(cutoff, now);
        workflowRepository.findIdsByStatus(AiPlanGenerationWorkflowStatus.PRE_CHECK_PENDING)
                .forEach(id -> eventPublisher.publishEvent(new AiPreCheckRequestedEvent(id)));
        workflowRepository.findIdsByStatus(AiPlanGenerationWorkflowStatus.PRE_CHECK_RETRY_PENDING)
                .forEach(id -> eventPublisher.publishEvent(new AiPreCheckRequestedEvent(id)));
        workflowRepository.findIdsByStatus(AiPlanGenerationWorkflowStatus.GENERATION_PENDING)
                .forEach(id -> eventPublisher.publishEvent(new AiGenerationRequestedEvent(id)));
    }
}
