package de.melinadanhier.projectflow.generation.model.workflow;

import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.common.model.MutableEntity;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import jakarta.persistence.Column;
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

    @Column(name = "pre_check_result", columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS JSONB)")
    private String preCheckResult;

    @Column(name = "generated_plan", columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS JSONB)")
    private String generatedPlan;

    public static AiPlanGenerationWorkflow create(
            Project project,
            String confirmedSnapshot,
            String snapshotVersion,
            UUID completionToken,
            Instant consentConfirmedAt,
            String consentVersion
    ) {
        AiPlanGenerationWorkflow workflow = new AiPlanGenerationWorkflow();
        workflow.project = project;
        workflow.confirmedSnapshot = confirmedSnapshot;
        workflow.snapshotVersion = snapshotVersion;
        workflow.completionToken = completionToken;
        workflow.consentConfirmedAt = consentConfirmedAt;
        workflow.consentVersion = consentVersion;
        return workflow;
    }

    public void restart(
            String confirmedSnapshot,
            UUID completionToken,
            Instant consentConfirmedAt,
            String consentVersion
    ) {
        this.confirmedSnapshot = confirmedSnapshot;
        this.completionToken = completionToken;
        this.consentConfirmedAt = consentConfirmedAt;
        this.consentVersion = consentVersion;
        status = AiPlanGenerationWorkflowStatus.PRE_CHECK_PENDING;
        preCheckRetryCount = 0;
        lastTechnicalError = null;
        preCheckResult = null;
        generatedPlan = null;
    }

    public void clearTechnicalError() {
        lastTechnicalError = null;
    }

    public int recordPreCheckRetry(AiTechnicalErrorCode errorCode) {
        preCheckRetryCount++;
        status = AiPlanGenerationWorkflowStatus.PRE_CHECK_RETRY_PENDING;
        lastTechnicalError = errorCode;
        return preCheckRetryCount;
    }

    public void recordPreCheckResult(String serializedResult, boolean needsReview) {
        preCheckResult = serializedResult;
        lastTechnicalError = null;
        status = needsReview
                ? AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW
                : AiPlanGenerationWorkflowStatus.GENERATION_PENDING;
    }

    public void recordPreCheckFailure(AiTechnicalErrorCode errorCode) {
        status = AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE;
        lastTechnicalError = errorCode;
    }

    public void approvePreCheck() {
        status = AiPlanGenerationWorkflowStatus.GENERATION_PENDING;
    }

    public void recordGeneratedPlan(String serializedPlan) {
        generatedPlan = serializedPlan;
        lastTechnicalError = null;
        status = AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED;
    }

    public void recordGenerationFailure(AiTechnicalErrorCode errorCode) {
        status = AiPlanGenerationWorkflowStatus.GENERATION_FAILED;
        lastTechnicalError = errorCode;
    }

    public void markDraftApplied() {
        status = AiPlanGenerationWorkflowStatus.DRAFT_APPLIED;
    }
}
