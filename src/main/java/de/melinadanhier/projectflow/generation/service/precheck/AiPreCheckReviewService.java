package de.melinadanhier.projectflow.generation.service.precheck;

import de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckInputChange;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.generation.dto.precheck.AiPreCheckProblemDto;
import de.melinadanhier.projectflow.generation.dto.precheck.AiPreCheckReviewDto;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.event.AiPreCheckRequestedEvent;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.ai.config.AiExecutionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiPreCheckReviewService {

    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final AiWorkflowPayloadCodec snapshotCodec;
    private final AiExecutionProperties executionProperties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AiPreCheckReviewDto getReview(UUID workflowId, UUID userId) {
        AiPlanGenerationWorkflow workflow = requireOwned(workflowId, userId);
        requireReviewable(workflow);
        AiPreCheckResult result = readResult(workflow);
        AiWizardSnapshot snapshot = snapshotCodec.readSnapshot(workflow.getConfirmedSnapshot());
        List<AiPreCheckProblemDto> problems = new ArrayList<>();
        for (int index = 0; index < result.problems().size(); index++) {
            AiPreCheckProblem problem = result.problems().get(index);
            problems.add(new AiPreCheckProblemDto(
                    index, problem.severity(), problem.type(), problem.message(),
                    problem.suggestedUserAction(), problem.acceptedInterpretation(),
                    workflow.getAcceptedOpenPointIndices().contains(index), problem.proposedInputChanges(),
                    proposedChangesMatchSnapshot(snapshot, problem.proposedInputChanges()),
                    problem.adjustmentOptions()));
        }
        return new AiPreCheckReviewDto(workflowId, workflow.getProject().getId(), problems);
    }

    @Transactional
    public boolean acceptOpenPoint(UUID workflowId, UUID userId, int problemIndex) {
        AiPlanGenerationWorkflow workflow = workflowRepository.findOwnedByIdForUpdate(workflowId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW) {
            boolean alreadyAccepted = workflow.getAcceptedOpenPointIndices().contains(problemIndex);
            boolean generationStarted = workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_PENDING
                    || workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_RUNNING
                    || workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED
                    || workflow.getStatus() == AiPlanGenerationWorkflowStatus.PRE_CHECK_COMPLETED;
            if (alreadyAccepted && generationStarted) {
                return true;
            }
        }
        requireReviewable(workflow);
        AiPreCheckResult result = readResult(workflow);
        List<AiPreCheckProblem> problems = result.problems();
        if (problemIndex < 0 || problemIndex >= problems.size()
                || problems.get(problemIndex).severity() != AiPreCheckSeverity.WARNING) {
            throw new ResourceNotFoundException("Die Warnung wurde nicht gefunden.");
        }
        AiPreCheckProblem problem = problems.get(problemIndex);
        if (problem.type() == de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblemType.CRITICAL_ASSUMPTION) {
            AiWizardSnapshot snapshot = snapshotCodec.readSnapshot(workflow.getConfirmedSnapshot());
            if (!proposedChangesMatchSnapshot(snapshot, problem.proposedInputChanges())) {
                throw new ConflictException("Die vorgeschlagene Änderung ist nicht konkret oder nicht mehr aktuell.");
            }
            restartPreCheck(workflow, applyProposedChanges(snapshot, problem.proposedInputChanges()));
            return true;
        }
        workflow.acceptOpenPoint(problemIndex);
        return completeReviewIfPossible(workflow, result);
    }

    @Transactional
    public boolean confirmOpenPointContext(
            UUID workflowId, UUID userId, int problemIndex, String confirmedContext) {
        AiPlanGenerationWorkflow workflow = workflowRepository.findOwnedByIdForUpdate(workflowId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
        if (confirmedContext == null || confirmedContext.isBlank() || confirmedContext.trim().length() > 1000) {
            throw new DomainValidationException(
                    "Die eigene Planungsgrundlage muss zwischen 1 und 1000 Zeichen enthalten.");
        }
        String normalizedContext = confirmedContext.trim();
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW) {
            boolean sameContextAlreadyConfirmed = workflow.getAcceptedOpenPointIndices().contains(problemIndex)
                    && normalizedContext.equals(
                            workflow.getCustomOpenPointInterpretations().get(problemIndex));
            boolean generationStarted = workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_PENDING
                    || workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_RUNNING
                    || workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED
                    || workflow.getStatus() == AiPlanGenerationWorkflowStatus.PRE_CHECK_COMPLETED;
            if (sameContextAlreadyConfirmed && generationStarted) {
                return true;
            }
        }
        requireReviewable(workflow);
        AiPreCheckResult result = readResult(workflow);
        requireOpenPoint(result, problemIndex);
        AiWizardSnapshot updatedSnapshot = withUserCorrection(
                snapshotCodec.readSnapshot(workflow.getConfirmedSnapshot()), normalizedContext);
        restartPreCheck(workflow, updatedSnapshot);
        return true;
    }

    @Transactional
    public void regenerate(UUID workflowId, UUID userId) {
        AiPlanGenerationWorkflow workflow = workflowRepository.findOwnedByIdForUpdate(workflowId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
        requireReviewable(workflow);
        restartPreCheck(workflow, workflow.getConfirmedSnapshot());
    }

    private void restartPreCheck(AiPlanGenerationWorkflow workflow, AiWizardSnapshot updatedSnapshot) {
        restartPreCheck(workflow, snapshotCodec.writeSnapshot(updatedSnapshot));
    }

    private void restartPreCheck(AiPlanGenerationWorkflow workflow, String confirmedSnapshot) {
        UUID runId = UUID.randomUUID();
        Instant now = Instant.now(clock);
        workflow.restartPreCheck(confirmedSnapshot, runId,
                now.plus(executionProperties.getMaxRunTime()));
        events.publishEvent(new AiPreCheckRequestedEvent(workflow.getId(), runId));
    }

    private AiWizardSnapshot applyProposedChanges(AiWizardSnapshot snapshot, List<AiPreCheckInputChange> proposedChanges) {
        Map<String, String> changes = new LinkedHashMap<>();
        proposedChanges.forEach(change -> changes.put(change.field(), change.newValue()));
        Map<String, String> answers = new LinkedHashMap<>(snapshot.projectSpecificAnswers());
        changes.forEach((key, value) -> {
            if (key.startsWith("projectSpecificAnswers.")) {
                answers.put(key.substring("projectSpecificAnswers.".length()), value);
            }
        });
        return new AiWizardSnapshot(
                changes.getOrDefault("title", snapshot.title()),
                changes.getOrDefault("description", snapshot.description()),
                parseDate(changes, "startDate", snapshot.startDate()),
                parseDate(changes, "endDate", snapshot.endDate()),
                snapshot.collaborationMode(), snapshot.category(), snapshot.subcategory(),
                changes.getOrDefault("otherProjectTypeDescription", snapshot.otherProjectTypeDescription()),
                changes.getOrDefault("projectGoal", snapshot.projectGoal()),
                changes.getOrDefault("constraints", snapshot.constraints()),
                changes.getOrDefault("additionalInformation", snapshot.additionalInformation()),
                parseInteger(changes, "durationDays", snapshot.durationDays()),
                changes.getOrDefault("availableWorkingTime", snapshot.availableWorkingTime()), answers);
    }

    private java.time.LocalDate parseDate(Map<String, String> changes, String key, java.time.LocalDate fallback) {
        return changes.containsKey(key) ? java.time.LocalDate.parse(changes.get(key)) : fallback;
    }

    private Integer parseInteger(Map<String, String> changes, String key, Integer fallback) {
        return changes.containsKey(key) ? Integer.valueOf(changes.get(key)) : fallback;
    }

    private boolean proposedChangesMatchSnapshot(
            AiWizardSnapshot snapshot, List<AiPreCheckInputChange> proposedChanges) {
        return !proposedChanges.isEmpty()
                && proposedChanges.stream().allMatch(change ->
                        change.previousValue().equals(currentValue(snapshot, change.field())))
                && !(requiresCompleteScope(snapshot) && proposedChanges.stream().anyMatch(this::changesScope));
    }

    private boolean requiresCompleteScope(AiWizardSnapshot snapshot) {
        String input = String.join(" ", java.util.stream.Stream.of(
                        snapshot.description(), snapshot.projectGoal(), snapshot.constraints(),
                        snapshot.additionalInformation(), String.join(" ", snapshot.projectSpecificAnswers().values()))
                .filter(java.util.Objects::nonNull).toList()).toLowerCase(java.util.Locale.GERMAN);
        return input.contains("kein thema auslassen") || input.contains("keine themen auslassen")
                || input.contains("alle themen behandeln") || input.contains("vollständiger themenumfang");
    }

    private boolean changesScope(AiPreCheckInputChange change) {
        String field = change.field().toLowerCase(java.util.Locale.GERMAN);
        return field.equals("projectgoal") || field.contains("scope") || field.contains("themen");
    }

    private String currentValue(AiWizardSnapshot snapshot, String field) {
        Object value = switch (field) {
            case "title" -> snapshot.title();
            case "description" -> snapshot.description();
            case "startDate" -> snapshot.startDate();
            case "endDate" -> snapshot.endDate();
            case "otherProjectTypeDescription" -> snapshot.otherProjectTypeDescription();
            case "projectGoal" -> snapshot.projectGoal();
            case "constraints" -> snapshot.constraints();
            case "additionalInformation" -> snapshot.additionalInformation();
            case "durationDays" -> snapshot.durationDays();
            case "availableWorkingTime" -> snapshot.availableWorkingTime();
            default -> field.startsWith("projectSpecificAnswers.")
                    ? snapshot.projectSpecificAnswers().get(field.substring("projectSpecificAnswers.".length()))
                    : null;
        };
        return value == null ? "nicht angegeben" : value.toString();
    }

    private AiWizardSnapshot withUserCorrection(AiWizardSnapshot snapshot, String correction) {
        Map<String, String> answers = new LinkedHashMap<>(snapshot.projectSpecificAnswers());
        int number = 1;
        while (answers.containsKey("userPreCheckCorrection" + number)) {
            number++;
        }
        answers.put("userPreCheckCorrection" + number, correction);
        return new AiWizardSnapshot(
                snapshot.title(), snapshot.description(), snapshot.startDate(), snapshot.endDate(),
                snapshot.collaborationMode(), snapshot.category(), snapshot.subcategory(),
                snapshot.otherProjectTypeDescription(), snapshot.projectGoal(), snapshot.constraints(),
                snapshot.additionalInformation(), snapshot.durationDays(), snapshot.availableWorkingTime(), answers);
    }

    private boolean completeReviewIfPossible(
            AiPlanGenerationWorkflow workflow, AiPreCheckResult result) {
        if (result.hasErrors()) {
            return false;
        }
        boolean allAcknowledged = true;
        for (int index = 0; index < result.problems().size(); index++) {
            AiPreCheckProblem problem = result.problems().get(index);
            if (problem.severity() == AiPreCheckSeverity.WARNING
                    && !workflow.getAcceptedOpenPointIndices().contains(index)) {
                allAcknowledged = false;
                break;
            }
        }
        if (!allAcknowledged) {
            return false;
        }
        workflow.approvePreCheck();
        return true;
    }

    private void requireOpenPoint(AiPreCheckResult result, int problemIndex) {
        List<AiPreCheckProblem> problems = result.problems();
        if (problemIndex < 0 || problemIndex >= problems.size()
                || problems.get(problemIndex).severity() != AiPreCheckSeverity.WARNING) {
            throw new ResourceNotFoundException("Der offene Punkt wurde nicht gefunden.");
        }
    }

    @Transactional
    public de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot returnToWizard(
            UUID workflowId, UUID userId) {
        AiPlanGenerationWorkflow workflow = requireOwned(workflowId, userId);
        if (workflow.getConfirmedSnapshot() == null) {
            throw new ConflictException("Für diesen KI-Workflow liegen keine gespeicherten Eingaben vor.");
        }
        return snapshotCodec.readSnapshot(workflow.getConfirmedSnapshot());
    }

    private AiPlanGenerationWorkflow requireOwned(UUID workflowId, UUID userId) {
        return workflowRepository.findOwnedById(workflowId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
    }

    private void requireReviewable(AiPlanGenerationWorkflow workflow) {
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW
                && workflow.getStatus() != AiPlanGenerationWorkflowStatus.PRE_CHECK_COMPLETED
                && workflow.getStatus() != AiPlanGenerationWorkflowStatus.GENERATION_CANCELLED) {
            throw new ConflictException("Für diesen KI-Workflow liegen keine aktuellen Hinweise vor.");
        }
    }

    private AiPreCheckResult readResult(AiPlanGenerationWorkflow workflow) {
        if (workflow.getPreCheckResult() == null) {
            throw new ConflictException("Das Ergebnis der KI-Prüfung liegt noch nicht vor.");
        }
        return snapshotCodec.readPreCheckResult(workflow.getPreCheckResult());
    }
}
