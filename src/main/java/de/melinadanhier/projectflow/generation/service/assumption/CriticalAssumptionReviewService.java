package de.melinadanhier.projectflow.generation.service.assumption;

import de.melinadanhier.projectflow.ai.model.generation.RejectedCriticalAssumption;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.generation.dto.*;
import de.melinadanhier.projectflow.generation.event.AiGenerationRequestedEvent;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.model.workflow.GenerationAssumptionContext;
import de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CriticalAssumptionReviewService {

    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final AiWorkflowPayloadCodec payloadCodec;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public AssumptionReviewDto getReview(UUID workflowId, UUID userId) {
        var workflow = workflowRepository.findOwnedById(workflowId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
        boolean failedRegeneration = workflow.getPendingAssumptionReview() != null
                && (workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_FAILED
                || workflow.getStatus() == AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE);
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.ASSUMPTIONS_REVIEW_PENDING
                && !failedRegeneration) {
            throw new ConflictException("Für diesen KI-Workflow ist keine Annahmenprüfung offen.");
        }
        var plan = payloadCodec.readGeneratedPlan(workflow.getGeneratedPlan());
        var saved = failedRegeneration
                ? payloadCodec.readAssumptionReview(workflow.getPendingAssumptionReview())
                : new AssumptionReviewRequest(List.of());
        Map<Integer, AssumptionDecisionRequest> decisions = new HashMap<>();
        saved.decisions().forEach(decision -> decisions.put(decision.assumptionIndex(), decision));
        var assumptions = new ArrayList<CriticalAssumptionReviewDto>();
        for (int index = 0; index < plan.criticalAssumptions().size(); index++) {
            var assumption = plan.criticalAssumptions().get(index);
            var decision = decisions.get(index);
            assumptions.add(new CriticalAssumptionReviewDto(index, assumption.statement(),
                    assumption.correctionRequiredIfRejected(),
                    decision == null ? null : decision.decision(),
                    decision == null ? null : decision.correction()));
        }
        String error = failedRegeneration
                ? "Die Neugenerierung ist fehlgeschlagen. Deine Bewertungen bleiben erhalten und können erneut gesendet werden."
                : null;
        return new AssumptionReviewDto(workflowId, workflow.getProject().getId(), assumptions, error);
    }

    @Transactional
    public boolean submit(UUID workflowId, UUID userId, AssumptionReviewRequest request) {
        var workflow = workflowRepository.findOwnedByIdForUpdate(workflowId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
        boolean failedRegeneration = workflow.getPendingAssumptionReview() != null
                && (workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_FAILED
                || workflow.getStatus() == AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE);
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.ASSUMPTIONS_REVIEW_PENDING
                && !failedRegeneration) {
            throw new ConflictException("Die Annahmenprüfung kann in diesem Zustand nicht abgeschlossen werden.");
        }
        var plan = payloadCodec.readGeneratedPlan(workflow.getGeneratedPlan());
        var decisions = validate(request, plan.criticalAssumptions());
        List<String> confirmed = new ArrayList<>();
        List<RejectedCriticalAssumption> rejected = new ArrayList<>();
        for (int index = 0; index < plan.criticalAssumptions().size(); index++) {
            var assumption = plan.criticalAssumptions().get(index);
            var decision = decisions.get(index);
            if (decision.decision() == AssumptionDecision.CONFIRMED) {
                confirmed.add(assumption.statement());
            } else {
                rejected.add(new RejectedCriticalAssumption(assumption.statement(), decision.correction()));
            }
        }
        if (rejected.isEmpty()) {
            if (failedRegeneration) workflow.confirmAssumptionsAfterFailedRegeneration();
            else workflow.confirmAssumptions();
            return false;
        }
        var previous = payloadCodec.readAssumptionContext(workflow.getGenerationAssumptionContext());
        List<String> allConfirmed = new ArrayList<>(previous.confirmedAssumptions());
        List<RejectedCriticalAssumption> allRejected = new ArrayList<>(previous.rejectedAssumptions());
        if (failedRegeneration) {
            removePreviousAttempt(allConfirmed, allRejected,
                    payloadCodec.readAssumptionReview(workflow.getPendingAssumptionReview()),
                    plan.criticalAssumptions());
        }
        allConfirmed.addAll(confirmed);
        allRejected.addAll(rejected);
        String context = payloadCodec.writeAssumptionContext(
                new GenerationAssumptionContext(allConfirmed, allRejected));
        String review = payloadCodec.writeAssumptionReview(request);
        if (failedRegeneration) workflow.prepareFailedAssumptionRegeneration(context, review);
        else workflow.prepareAssumptionRegeneration(context, review);
        eventPublisher.publishEvent(new AiGenerationRequestedEvent(workflowId));
        return true;
    }

    private void removePreviousAttempt(List<String> confirmed,
                                       List<RejectedCriticalAssumption> rejected,
                                       AssumptionReviewRequest previousReview,
                                       List<de.melinadanhier.projectflow.ai.model.generation.GeneratedCriticalAssumption> assumptions) {
        for (var decision : previousReview.decisions()) {
            if (decision.assumptionIndex() < 0 || decision.assumptionIndex() >= assumptions.size()) continue;
            String statement = assumptions.get(decision.assumptionIndex()).statement();
            if (decision.decision() == AssumptionDecision.CONFIRMED) {
                removeLast(confirmed, statement);
            } else if (decision.decision() == AssumptionDecision.REJECTED) {
                for (int index = rejected.size() - 1; index >= 0; index--) {
                    if (Objects.equals(rejected.get(index).statement(), statement)) {
                        rejected.remove(index);
                        break;
                    }
                }
            }
        }
    }

    private void removeLast(List<String> values, String value) {
        for (int index = values.size() - 1; index >= 0; index--) {
            if (Objects.equals(values.get(index), value)) {
                values.remove(index);
                return;
            }
        }
    }

    private Map<Integer, AssumptionDecisionRequest> validate(
            AssumptionReviewRequest request,
            List<de.melinadanhier.projectflow.ai.model.generation.GeneratedCriticalAssumption> assumptions) {
        if (request == null || request.decisions().size() != assumptions.size()) {
            throw new DomainValidationException("Bitte bewerte jede kritische Annahme genau einmal.");
        }
        Map<Integer, AssumptionDecisionRequest> byIndex = new HashMap<>();
        for (var decision : request.decisions()) {
            if (decision.assumptionIndex() < 0 || decision.assumptionIndex() >= assumptions.size()
                    || decision.decision() == null || byIndex.putIfAbsent(decision.assumptionIndex(), decision) != null) {
                throw new DomainValidationException("Bitte bewerte jede kritische Annahme genau einmal.");
            }
            String correction = decision.correction();
            if (correction != null && correction.length() > 2000) {
                throw new DomainValidationException("Eine Korrektur darf höchstens 2000 Zeichen enthalten.");
            }
            var assumption = assumptions.get(decision.assumptionIndex());
            if (decision.decision() == AssumptionDecision.REJECTED
                    && assumption.correctionRequiredIfRejected()
                    && (correction == null || correction.isBlank())) {
                throw new DomainValidationException("Bitte ergänze die erforderliche Korrektur.");
            }
            if (decision.decision() == AssumptionDecision.CONFIRMED
                    && correction != null && !correction.isBlank()) {
                throw new DomainValidationException("Bestätigte Annahmen dürfen keine Korrektur enthalten.");
            }
            if (decision.decision() == AssumptionDecision.REJECTED
                    && !assumption.correctionRequiredIfRejected()
                    && correction != null && !correction.isBlank()) {
                throw new DomainValidationException("Diese Ablehnung benötigt keine Korrektur.");
            }
        }
        return byIndex;
    }
}
