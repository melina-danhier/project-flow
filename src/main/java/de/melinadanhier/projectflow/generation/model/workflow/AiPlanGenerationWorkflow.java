package de.melinadanhier.projectflow.generation.model.workflow;

import de.melinadanhier.projectflow.ai.exception.AiTechnicalError;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.model.AiOperation;
import de.melinadanhier.projectflow.ai.prompt.AiPromptVersions;
import de.melinadanhier.projectflow.ai.model.AiSchemaVersions;
import de.melinadanhier.projectflow.common.model.MutableEntity;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "ai_plan_generation_workflows",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ai_workflows_project", columnNames = "project_id"),
                @UniqueConstraint(name = "uk_ai_workflows_completion_token", columnNames = "completion_token")
        }
)
@Getter
@NoArgsConstructor
@DynamicUpdate
public class AiPlanGenerationWorkflow extends MutableEntity {

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Project project;

    @NotBlank
    @Column(name = "confirmed_snapshot", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS JSONB)")
    private String confirmedSnapshot;

    @NotBlank
    @Size(max = 50)
    @Column(name = "snapshot_version", nullable = false, updatable = false, length = 50)
    private String snapshotVersion;

    @NotNull
    @Column(name = "completion_token", nullable = false)
    private UUID completionToken;

    @NotNull
    @Column(name = "consent_confirmed_at", nullable = false)
    private Instant consentConfirmedAt;

    @NotBlank
    @Size(max = 20)
    @Column(name = "consent_version", nullable = false, length = 20)
    private String consentVersion;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private AiPlanGenerationWorkflowStatus status = AiPlanGenerationWorkflowStatus.PRE_CHECK_PENDING;

    @PositiveOrZero
    @Column(name = "pre_check_retry_count", nullable = false)
    private int preCheckRetryCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_technical_error", length = 50)
    private AiTechnicalErrorCode lastTechnicalError;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_ai_operation", length = 30)
    private AiOperation lastAiOperation;

    @Column(name = "pre_check_result", columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS JSONB)")
    private String preCheckResult;

    @Column(name = "generated_plan", columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS JSONB)")
    private String generatedPlan;

    @NotBlank
    @Size(max = 100)
    @Column(name = "generation_prompt_version", nullable = false, updatable = false, length = 100)
    private String generationPromptVersion;

    @NotBlank
    @Size(max = 20)
    @Column(name = "pre_check_schema_version", nullable = false, updatable = false, length = 20)
    private String preCheckSchemaVersion;

    @NotBlank
    @Size(max = 20)
    @Column(name = "generation_schema_version", nullable = false, updatable = false, length = 20)
    private String generationSchemaVersion;

    @PositiveOrZero
    @Column(name = "generation_round_attempt_count", nullable = false)
    private int generationRoundAttemptCount;

    @PositiveOrZero
    @Column(name = "generation_total_attempt_count", nullable = false)
    private int generationTotalAttemptCount;

    @Column(name = "last_error_retryable")
    private Boolean lastErrorRetryable;

    @Size(max = 500)
    @Column(name = "last_error_diagnosis", length = 500)
    private String lastErrorDiagnosis;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "ai_workflow_acknowledged_warnings",
            joinColumns = @JoinColumn(name = "workflow_id")
    )
    @Column(name = "problem_index", nullable = false)
    private Set<Integer> acknowledgedWarningIndices = new HashSet<>();

    public static AiPlanGenerationWorkflow create(
            Project project,
            String confirmedSnapshot,
            String snapshotVersion,
            UUID completionToken,
            Instant consentConfirmedAt,
            String consentVersion,
            String generationPromptVersion
    ) {
        AiPlanGenerationWorkflow workflow = new AiPlanGenerationWorkflow();
        workflow.project = project;
        workflow.confirmedSnapshot = confirmedSnapshot;
        workflow.snapshotVersion = snapshotVersion;
        workflow.completionToken = completionToken;
        workflow.consentConfirmedAt = consentConfirmedAt;
        workflow.consentVersion = consentVersion;
        workflow.generationPromptVersion = generationPromptVersion;
        workflow.preCheckSchemaVersion = AiSchemaVersions.PRE_CHECK;
        workflow.generationSchemaVersion = AiSchemaVersions.GENERATION;
        return workflow;
    }

    public static AiPlanGenerationWorkflow create(
            Project project,
            String confirmedSnapshot,
            String snapshotVersion,
            UUID completionToken,
            Instant consentConfirmedAt,
            String consentVersion
    ) {
        return create(project, confirmedSnapshot, snapshotVersion, completionToken,
                consentConfirmedAt, consentVersion, AiPromptVersions.GENERATION_PROMPT);
    }

    public Set<Integer> getAcknowledgedWarningIndices() {
        return Set.copyOf(acknowledgedWarningIndices);
    }

    public void clearTechnicalError() {
        clearError();
    }

    public int recordPreCheckRetry(AiTechnicalError error) {
        requireStatus(AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING);
        requireOperation(error, AiOperation.PRE_CHECK);
        preCheckRetryCount++;
        status = AiPlanGenerationWorkflowStatus.PRE_CHECK_RETRY_PENDING;
        recordError(error);
        return preCheckRetryCount;
    }

    public void recordPreCheckResult(String serializedResult, boolean needsReview) {
        requireStatus(AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING);
        preCheckResult = serializedResult;
        acknowledgedWarningIndices.clear();
        clearError();
        status = needsReview
                ? AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW
                : AiPlanGenerationWorkflowStatus.GENERATION_PENDING;
    }

    public void recordPreCheckFailure(AiTechnicalError error) {
        requireStatus(AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING);
        requireOperation(error, AiOperation.PRE_CHECK);
        status = AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE;
        recordError(error);
    }

    public void approvePreCheck() {
        requireStatus(AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW);
        status = AiPlanGenerationWorkflowStatus.GENERATION_PENDING;
    }

    public boolean acknowledgeWarning(int problemIndex) {
        requireStatus(AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW);
        return acknowledgedWarningIndices.add(problemIndex);
    }

    public void recordGenerationAttempt() {
        requireStatus(AiPlanGenerationWorkflowStatus.GENERATION_RUNNING);
        generationRoundAttemptCount++;
        generationTotalAttemptCount++;
    }

    public void recordGeneratedPlan(String serializedPlan) {
        requireStatus(AiPlanGenerationWorkflowStatus.GENERATION_RUNNING);
        generatedPlan = serializedPlan;
        clearError();
        status = AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED;
    }

    public void recordGenerationFailure(AiTechnicalError error) {
        requireStatus(AiPlanGenerationWorkflowStatus.GENERATION_RUNNING);
        requireOperation(error, AiOperation.PLAN_GENERATION);
        status = AiPlanGenerationWorkflowStatus.GENERATION_FAILED;
        recordError(error);
    }

    public void recordTechnicalFailure(AiTechnicalError error) {
        requireStatus(AiPlanGenerationWorkflowStatus.GENERATION_RUNNING);
        requireOperation(error, AiOperation.PLAN_GENERATION);
        status = AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE;
        recordError(error);
    }

    public void prepareManualGenerationRetry() {
        if (status != AiPlanGenerationWorkflowStatus.GENERATION_FAILED
                && status != AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE) {
            throw new IllegalStateException("Der Workflow kann in diesem Zustand nicht erneut gestartet werden.");
        }
        generationRoundAttemptCount = 0;
        clearError();
        status = AiPlanGenerationWorkflowStatus.GENERATION_PENDING;
    }

    public void markDraftApplied() {
        requireStatus(AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED);
        status = AiPlanGenerationWorkflowStatus.DRAFT_APPLIED;
    }

    private void clearError() {
        lastTechnicalError = null;
        lastAiOperation = null;
        lastErrorRetryable = null;
        lastErrorDiagnosis = null;
    }

    private void recordError(AiTechnicalError error) {
        lastTechnicalError = error.errorCode();
        lastAiOperation = error.operation();
        lastErrorRetryable = error.isRetryable();
        lastErrorDiagnosis = sanitizeDiagnosis(error.diagnosis());
    }

    private void requireOperation(AiTechnicalError error, AiOperation expected) {
        if (error.operation() != expected) {
            throw new IllegalArgumentException(
                    "Ungültige KI-Operation " + error.operation() + "; erwartet wurde " + expected + ".");
        }
    }

    private String sanitizeDiagnosis(String diagnosis) {
        if (diagnosis == null || diagnosis.isBlank()) {
            return null;
        }
        String compact = diagnosis.replaceAll("[\\r\\n\\t]+", " ").trim();
        return compact.substring(0, Math.min(compact.length(), 500));
    }

    private void requireStatus(AiPlanGenerationWorkflowStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Ungültiger KI-Workflow-Übergang aus " + status + "; erwartet wurde " + expected + ".");
        }
    }
}
