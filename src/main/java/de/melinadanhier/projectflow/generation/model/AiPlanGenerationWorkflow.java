package de.melinadanhier.projectflow.generation.model;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
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
@Setter
@NoArgsConstructor
public class AiPlanGenerationWorkflow extends MutableEntity {

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Project project;

    @NotBlank
    @Setter(AccessLevel.NONE)
    @Column(name = "confirmed_snapshot", nullable = false, updatable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS JSONB)")
    private String confirmedSnapshot;

    @NotBlank
    @Size(max = 50)
    @Setter(AccessLevel.NONE)
    @Column(name = "snapshot_version", nullable = false, updatable = false, length = 50)
    private String snapshotVersion;

    @NotNull
    @Setter(AccessLevel.NONE)
    @Column(name = "completion_token", nullable = false, updatable = false, unique = true)
    private UUID completionToken;

    @NotNull
    @Setter(AccessLevel.NONE)
    @Column(name = "consent_confirmed_at", nullable = false, updatable = false)
    private Instant consentConfirmedAt;

    @NotBlank
    @Size(max = 20)
    @Setter(AccessLevel.NONE)
    @Column(name = "consent_version", nullable = false, updatable = false, length = 20)
    private String consentVersion;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private AiPlanGenerationWorkflowStatus status = AiPlanGenerationWorkflowStatus.PRE_CHECK_PENDING;

    @PositiveOrZero
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Size(max = 2000)
    @Column(name = "last_technical_error", length = 2000)
    private String lastTechnicalError;

    @Column(name = "pre_check_result", columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS JSONB)")
    private String preCheckResult;

    public static AiPlanGenerationWorkflow create(
            Project project,
            String confirmedSnapshot,
            String snapshotVersion,
            UUID completionToken,
            Instant consentConfirmedAt,
            String consentVersion
    ) {
        AiPlanGenerationWorkflow workflow = new AiPlanGenerationWorkflow();
        workflow.setProject(project);
        workflow.confirmedSnapshot = confirmedSnapshot;
        workflow.snapshotVersion = snapshotVersion;
        workflow.completionToken = completionToken;
        workflow.consentConfirmedAt = consentConfirmedAt;
        workflow.consentVersion = consentVersion;
        return workflow;
    }
}
