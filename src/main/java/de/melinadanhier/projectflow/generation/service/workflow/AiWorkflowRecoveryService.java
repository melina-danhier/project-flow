package de.melinadanhier.projectflow.generation.service.workflow;

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
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final AiWorkflowControlService controlService;

    @Scheduled(fixedDelayString = "${projectflow.ai.recovery-delay:30s}")
    @Transactional
    public void recover() {
        // Noch nicht beanspruchte Arbeit wird erneut signalisiert. Bereits laufende Arbeit
        // bleibt bis zu ihrer absoluten Ablaufzeit gesperrt und wird danach fehlgeschlagen.
        Instant now = Instant.now(clock);
        workflowRepository.findExpiredIds(now, java.util.List.of(
                        AiPlanGenerationWorkflowStatus.PRE_CHECK_PENDING,
                        AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING,
                        AiPlanGenerationWorkflowStatus.PRE_CHECK_RETRY_PENDING,
                        AiPlanGenerationWorkflowStatus.GENERATION_PENDING,
                        AiPlanGenerationWorkflowStatus.GENERATION_RUNNING))
                .forEach(controlService::expire);
        workflowRepository.findIdsByStatus(AiPlanGenerationWorkflowStatus.PRE_CHECK_PENDING)
                .forEach(id -> workflowRepository.findById(id).ifPresent(workflow ->
                        eventPublisher.publishEvent(new AiPreCheckRequestedEvent(id, workflow.getActiveRunId()))));
        workflowRepository.findIdsByStatus(AiPlanGenerationWorkflowStatus.PRE_CHECK_RETRY_PENDING)
                .forEach(id -> workflowRepository.findById(id).ifPresent(workflow ->
                        eventPublisher.publishEvent(new AiPreCheckRequestedEvent(id, workflow.getActiveRunId()))));
        workflowRepository.findIdsByStatus(AiPlanGenerationWorkflowStatus.GENERATION_PENDING)
                .forEach(id -> workflowRepository.findById(id).ifPresent(workflow ->
                        eventPublisher.publishEvent(new AiGenerationRequestedEvent(id, workflow.getActiveRunId()))));
    }
}
