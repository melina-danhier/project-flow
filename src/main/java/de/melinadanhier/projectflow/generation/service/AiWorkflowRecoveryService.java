package de.melinadanhier.projectflow.generation.service;

import de.melinadanhier.projectflow.generation.client.AiExecutionProperties;
import de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AiWorkflowRecoveryService {

    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final AiExecutionProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedDelayString = "${projectflow.ai.recovery-delay:30s}")
    @Transactional
    public void recover() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(properties.getStaleWorkflowTimeout());
        workflowRepository.releaseStalePreChecks(cutoff, now);
        workflowRepository.releaseStaleGenerations(cutoff, now);
        workflowRepository.findIdsByStatus(AiPlanGenerationWorkflowStatus.PRE_CHECK_PENDING)
                .forEach(id -> eventPublisher.publishEvent(new AiPreCheckRequestedEvent(id)));
        workflowRepository.findIdsByStatus(AiPlanGenerationWorkflowStatus.GENERATION_PENDING)
                .forEach(id -> eventPublisher.publishEvent(new AiGenerationRequestedEvent(id)));
    }
}
