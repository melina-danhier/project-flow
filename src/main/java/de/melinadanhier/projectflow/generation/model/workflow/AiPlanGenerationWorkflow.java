package de.melinadanhier.projectflow.generation.model.workflow;

import de.melinadanhier.projectflow.ai.exception.AiTechnicalError;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.model.AiOperation;
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

    @Column(name = "active_run_id")
    private UUID activeRunId;

    @Column(name = "run_expires_at")
    private Instant runExpiresAt;

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

    @Column(name = "generation_assumption_context", columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS JSONB)")
    private String generationAssumptionContext;

    @Column(name = "pending_assumption_review", columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS JSONB)")
    private String pendingAssumptionReview;

    @Size(max = 100)
    @Column(name = "pre_check_prompt_version", length = 100)
    private String preCheckPromptVersion;

    @Size(max = 100)
    @Column(name = "generation_prompt_version", length = 100)
    private String generationPromptVersion;

    @Size(max = 100)
    @Column(name = "model_name", length = 100)
    private String modelName;

    @Size(max = 50)
    @Column(name = "pre_check_schema_version", length = 50)
    private String preCheckSchemaVersion;

    @Size(max = 50)
    @Column(name = "generation_schema_version", length = 50)
    private String generationSchemaVersion;

    @PositiveOrZero
    @Column(name = "generation_round_attempt_count", nullable = false)
    private int generationRoundAttemptCount;

    @PositiveOrZero
    @Column(name = "generation_total_attempt_count", nullable = false)
    private int generationTotalAttemptCount;

    @Column(name = "last_error_retryable")
    private Boolean lastErrorRetryable;

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
            UUID runId,
            Instant expiresAt
    ) {
        AiPlanGenerationWorkflow workflow = new AiPlanGenerationWorkflow();
        workflow.project = project;
        workflow.confirmedSnapshot = confirmedSnapshot;
        workflow.snapshotVersion = snapshotVersion;
        workflow.completionToken = completionToken;
        workflow.consentConfirmedAt = consentConfirmedAt;
        workflow.consentVersion = consentVersion;
        workflow.activeRunId = runId;
        workflow.runExpiresAt = expiresAt;
        return workflow;
    }

    public static AiPlanGenerationWorkflow create(
            Project project, String confirmedSnapshot, String snapshotVersion,
            UUID completionToken, Instant consentConfirmedAt, String consentVersion
    ) {
        return create(project, confirmedSnapshot, snapshotVersion, completionToken,
                consentConfirmedAt, consentVersion,
                UUID.randomUUID(), consentConfirmedAt.plusSeconds(300));
    }

    public boolean isActiveRun(UUID runId, Instant now, AiPlanGenerationWorkflowStatus runningStatus) {
        return status == runningStatus && activeRunId != null && activeRunId.equals(runId)
                && runExpiresAt != null && now.isBefore(runExpiresAt);
    }

    public UUID startGeneration(UUID runId, Instant expiresAt) {
        boolean draftRegeneration = status == AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED;
        if (status != AiPlanGenerationWorkflowStatus.PRE_CHECK_SUCCEEDED
                && status != AiPlanGenerationWorkflowStatus.GENERATION_CANCELLED
                && status != AiPlanGenerationWorkflowStatus.GENERATION_FAILED
                && status != AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE
                && status != AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED) {
            throw new IllegalStateException("Die Generierung kann in diesem Zustand nicht gestartet werden.");
        }
        if (preCheckResult == null && !draftRegeneration) {
            throw new IllegalStateException("Vor der Generierung ist eine erfolgreiche Vorprüfung erforderlich.");
        }
        generationRoundAttemptCount = 0;
        clearError();
        activeRunId = runId;
        runExpiresAt = expiresAt;
        status = AiPlanGenerationWorkflowStatus.GENERATION_PENDING;
        return runId;
    }

    public void activatePendingGenerationRun(UUID runId, Instant expiresAt) {
        requireStatus(AiPlanGenerationWorkflowStatus.GENERATION_PENDING);
        activeRunId = java.util.Objects.requireNonNull(runId);
        runExpiresAt = java.util.Objects.requireNonNull(expiresAt);
    }

    public boolean cancel(UUID runId, AiPlanGenerationWorkflowStatus running,
                          AiPlanGenerationWorkflowStatus cancelled) {
        if (status != running || !java.util.Objects.equals(activeRunId, runId)) {
            return false;
        }
        status = cancelled;
        return true;
    }

    public boolean cancelPreCheckRun(UUID runId) {
        return cancel(runId, AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING,
                AiPlanGenerationWorkflowStatus.PRE_CHECK_CANCELLED);
    }

    public boolean cancelGenerationRun(UUID runId) {
        return cancel(runId, AiPlanGenerationWorkflowStatus.GENERATION_RUNNING,
                AiPlanGenerationWorkflowStatus.GENERATION_CANCELLED);
    }

    public boolean expire(UUID runId, Instant now, AiTechnicalError error) {
        boolean preCheck = status == AiPlanGenerationWorkflowStatus.PRE_CHECK_PENDING
                || status == AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING
                || status == AiPlanGenerationWorkflowStatus.PRE_CHECK_RETRY_PENDING;
        boolean generation = status == AiPlanGenerationWorkflowStatus.GENERATION_PENDING
                || status == AiPlanGenerationWorkflowStatus.GENERATION_RUNNING;
        if ((!preCheck && !generation) || !java.util.Objects.equals(activeRunId, runId)
                || runExpiresAt == null || now.isBefore(runExpiresAt)) {
            return false;
        }
        status = AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE;
        recordError(error);
        return true;
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
                : AiPlanGenerationWorkflowStatus.PRE_CHECK_SUCCEEDED;
    }

    public void recordPreCheckFailure(AiTechnicalError error) {
        requireStatus(AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING);
        requireOperation(error, AiOperation.PRE_CHECK);
        status = AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE;
        recordError(error);
    }

    public void approvePreCheck() {
        requireStatus(AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW);
        status = AiPlanGenerationWorkflowStatus.PRE_CHECK_SUCCEEDED;
    }

    public boolean acknowledgeWarning(int problemIndex) {
        requireStatus(AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW);
        return acknowledgedWarningIndices.add(problemIndex);
    }

    public void recordPreCheckAttempt(String promptVersion, String schemaVersion) {
        requireStatus(AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING);
        preCheckPromptVersion = requireVersion(promptVersion, "Pre-Check-Prompt-Version");
        preCheckSchemaVersion = requireVersion(schemaVersion, "Pre-Check-Schema-Version");
    }

    public void recordGenerationAttempt(String promptVersion, String schemaVersion) {
        requireStatus(AiPlanGenerationWorkflowStatus.GENERATION_RUNNING);
        generationPromptVersion = requireVersion(promptVersion, "Generierungs-Prompt-Version");
        generationSchemaVersion = requireVersion(schemaVersion, "Generierungs-Schema-Version");
        generationRoundAttemptCount++;
        generationTotalAttemptCount++;
    }

    private String requireVersion(String version, String field) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException(field + " darf nicht leer sein.");
        }
        return version;
    }

    public void recordGenerationCompleted(String serializedPlan, boolean assumptionsNeedReview) {
        requireStatus(AiPlanGenerationWorkflowStatus.GENERATION_RUNNING);
        generatedPlan = serializedPlan;
        pendingAssumptionReview = null;
        clearError();
        status = assumptionsNeedReview
                ? AiPlanGenerationWorkflowStatus.ASSUMPTIONS_REVIEW_PENDING
                : AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED;
    }

    public void confirmAssumptions() {
        requireStatus(AiPlanGenerationWorkflowStatus.ASSUMPTIONS_REVIEW_PENDING);
        pendingAssumptionReview = null;
        status = AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED;
    }

    public void confirmAssumptionsAfterFailedRegeneration() {
        requireFailedAssumptionRegeneration();
        pendingAssumptionReview = null;
        clearError();
        status = AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED;
    }

    public void prepareAssumptionRegeneration(String serializedContext, String serializedReview) {
        requireStatus(AiPlanGenerationWorkflowStatus.ASSUMPTIONS_REVIEW_PENDING);
        generationAssumptionContext = serializedContext;
        pendingAssumptionReview = serializedReview;
        generationRoundAttemptCount = 0;
        clearError();
        status = AiPlanGenerationWorkflowStatus.GENERATION_PENDING;
    }

    public void prepareFailedAssumptionRegeneration(String serializedContext, String serializedReview) {
        requireFailedAssumptionRegeneration();
        generationAssumptionContext = serializedContext;
        pendingAssumptionReview = serializedReview;
        generationRoundAttemptCount = 0;
        clearError();
        status = AiPlanGenerationWorkflowStatus.GENERATION_PENDING;
    }

    private void requireFailedAssumptionRegeneration() {
        if ((status != AiPlanGenerationWorkflowStatus.GENERATION_FAILED
                && status != AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE)
                || pendingAssumptionReview == null) {
            throw new IllegalStateException("Es liegt keine fehlgeschlagene Annahmen-Neugenerierung vor.");
        }
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

    public void prepareDraftRegeneration() {
        requireStatus(AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED);
        generationRoundAttemptCount = 0;
        clearError();
        status = AiPlanGenerationWorkflowStatus.PRE_CHECK_SUCCEEDED;
    }

    private void clearError() {
        lastTechnicalError = null;
        lastAiOperation = null;
        lastErrorRetryable = null;
    }

    private void recordError(AiTechnicalError error) {
        lastTechnicalError = error.errorCode();
        lastAiOperation = error.operation();
        lastErrorRetryable = error.isRetryable();
    }

    private void requireOperation(AiTechnicalError error, AiOperation expected) {
        if (error.operation() != expected) {
            throw new IllegalArgumentException(
                    "Ungültige KI-Operation " + error.operation() + "; erwartet wurde " + expected + ".");
        }
    }

    private void requireStatus(AiPlanGenerationWorkflowStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Ungültiger KI-Workflow-Übergang aus " + status + "; erwartet wurde " + expected + ".");
        }
    }
}
