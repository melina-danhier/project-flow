package de.melinadanhier.projectflow.generation.model.workflow;

import de.melinadanhier.projectflow.common.model.MutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

@Entity
@Table(name = "ai_workflow_completion_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiWorkflowCompletionToken extends MutableEntity {

    /** Durable idempotency lookup; the schema intentionally permits historical aliases per workflow. */
    @Column(name = "completion_token", nullable = false, unique = true, updatable = false)
    private UUID completionToken;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AiPlanGenerationWorkflow workflow;

    private AiWorkflowCompletionToken(UUID completionToken, AiPlanGenerationWorkflow workflow) {
        this.completionToken = completionToken;
        this.workflow = workflow;
    }

    public static AiWorkflowCompletionToken create(
            UUID completionToken,
            AiPlanGenerationWorkflow workflow
    ) {
        return new AiWorkflowCompletionToken(completionToken, workflow);
    }
}
