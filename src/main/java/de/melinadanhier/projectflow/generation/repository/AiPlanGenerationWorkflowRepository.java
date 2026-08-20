package de.melinadanhier.projectflow.generation.repository;

import de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AiPlanGenerationWorkflowRepository
        extends JpaRepository<AiPlanGenerationWorkflow, UUID> {

    @Query("""
            select workflow
            from AiPlanGenerationWorkflow workflow
            join workflow.project.memberships membership
            where workflow.completionToken = :completionToken
              and membership.user.id = :userId
              and membership.active = true
            """)
    Optional<AiPlanGenerationWorkflow> findOwnedByCompletionToken(
            @Param("completionToken") UUID completionToken,
            @Param("userId") UUID userId
    );

    @Query("""
            select workflow
            from AiPlanGenerationWorkflow workflow
            join workflow.project.memberships membership
            where workflow.id = :workflowId
              and membership.user.id = :userId
              and membership.active = true
            """)
    Optional<AiPlanGenerationWorkflow> findOwnedById(
            @Param("workflowId") UUID workflowId,
            @Param("userId") UUID userId
    );
}
