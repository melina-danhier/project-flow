package de.melinadanhier.projectflow.generation.repository;

import de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.time.Instant;
import de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflowStatus;
import org.springframework.transaction.annotation.Transactional;

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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update AiPlanGenerationWorkflow workflow
            set workflow.status = de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING,
                workflow.updatedAt = :now
            where workflow.id = :workflowId
              and workflow.status = de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflowStatus.PRE_CHECK_PENDING
            """)
    int claimPreCheck(@Param("workflowId") UUID workflowId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update AiPlanGenerationWorkflow workflow
            set workflow.status = de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflowStatus.GENERATION_RUNNING,
                workflow.updatedAt = :now
            where workflow.id = :workflowId
              and workflow.status = de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflowStatus.GENERATION_PENDING
            """)
    int claimGeneration(@Param("workflowId") UUID workflowId, @Param("now") Instant now);

    Optional<AiPlanGenerationWorkflow> findByProjectId(UUID projectId);

    @Query("select workflow.id from AiPlanGenerationWorkflow workflow where workflow.status = :status")
    List<UUID> findIdsByStatus(@Param("status") AiPlanGenerationWorkflowStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update AiPlanGenerationWorkflow workflow
            set workflow.status = de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflowStatus.PRE_CHECK_PENDING,
                workflow.updatedAt = :now
            where workflow.updatedAt < :cutoff
              and workflow.status in (
                de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING,
                de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflowStatus.PRE_CHECK_RETRY_PENDING)
            """)
    int releaseStalePreChecks(@Param("cutoff") Instant cutoff, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update AiPlanGenerationWorkflow workflow
            set workflow.status = de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflowStatus.GENERATION_PENDING,
                workflow.updatedAt = :now
            where workflow.updatedAt < :cutoff
              and workflow.status = de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflowStatus.GENERATION_RUNNING
            """)
    int releaseStaleGenerations(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
