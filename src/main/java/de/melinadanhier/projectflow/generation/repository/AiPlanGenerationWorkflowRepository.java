package de.melinadanhier.projectflow.generation.repository;

import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiPlanGenerationWorkflowRepository
        extends JpaRepository<AiPlanGenerationWorkflow, UUID> {

    @Query("select workflow.project.id from AiPlanGenerationWorkflow workflow where workflow.id = :workflowId")
    Optional<UUID> findProjectIdById(@Param("workflowId") UUID workflowId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select workflow from AiPlanGenerationWorkflow workflow where workflow.id = :workflowId")
    Optional<AiPlanGenerationWorkflow> findByIdForUpdate(@Param("workflowId") UUID workflowId);

    @Query("""
            select workflow
            from AiPlanGenerationWorkflow workflow
            join workflow.project.memberships membership
            where workflow.id = :workflowId
              and membership.user.id = :userId
              and membership.active = true
              and membership.role = de.melinadanhier.projectflow.plancontainer.project.model.ProjectMemberRole.OWNER
            """)
    Optional<AiPlanGenerationWorkflow> findOwnedById(
            @Param("workflowId") UUID workflowId,
            @Param("userId") UUID userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select workflow
            from AiPlanGenerationWorkflow workflow
            join workflow.project.memberships membership
            where workflow.id = :workflowId
              and membership.user.id = :userId
              and membership.active = true
              and membership.role = de.melinadanhier.projectflow.plancontainer.project.model.ProjectMemberRole.OWNER
            """)
    Optional<AiPlanGenerationWorkflow> findOwnedByIdForUpdate(
            @Param("workflowId") UUID workflowId,
            @Param("userId") UUID userId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update AiPlanGenerationWorkflow workflow
            set workflow.status = de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING,
                workflow.updatedAt = :now
            where workflow.id = :workflowId
              and workflow.activeRunId = :runId
              and workflow.runExpiresAt > :now
              and workflow.status in (
                de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus.PRE_CHECK_PENDING,
                de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus.PRE_CHECK_RETRY_PENDING)
            """)
    int claimPreCheck(@Param("workflowId") UUID workflowId, @Param("runId") UUID runId,
                      @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update AiPlanGenerationWorkflow workflow
            set workflow.status = de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus.GENERATION_RUNNING,
                workflow.updatedAt = :now
            where workflow.id = :workflowId
              and workflow.activeRunId = :runId
              and workflow.runExpiresAt > :now
              and workflow.status = de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus.GENERATION_PENDING
            """)
    int claimGeneration(@Param("workflowId") UUID workflowId, @Param("runId") UUID runId,
                        @Param("now") Instant now);

    Optional<AiPlanGenerationWorkflow> findByProjectId(UUID projectId);

    @Query("""
            select workflow
            from AiPlanGenerationWorkflow workflow
            join workflow.project.memberships membership
            where workflow.project.id = :projectId
              and membership.user.id = :userId
              and membership.active = true
              and membership.role = de.melinadanhier.projectflow.plancontainer.project.model.ProjectMemberRole.OWNER
            """)
    Optional<AiPlanGenerationWorkflow> findOwnedByProjectId(
            @Param("projectId") UUID projectId,
            @Param("userId") UUID userId
    );

    @Query("select workflow.id from AiPlanGenerationWorkflow workflow where workflow.status = :status")
    List<UUID> findIdsByStatus(@Param("status") AiPlanGenerationWorkflowStatus status);

    @Query("select workflow.id from AiPlanGenerationWorkflow workflow where workflow.runExpiresAt <= :now and workflow.status in :statuses")
    List<UUID> findExpiredIds(@Param("now") Instant now,
                              @Param("statuses") List<AiPlanGenerationWorkflowStatus> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update AiPlanGenerationWorkflow workflow
            set workflow.status = de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus.PRE_CHECK_PENDING,
                workflow.updatedAt = :now
            where workflow.updatedAt < :cutoff
              and workflow.status = de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING
            """)
    int releaseStalePreChecks(@Param("cutoff") Instant cutoff, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update AiPlanGenerationWorkflow workflow
            set workflow.status = de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus.GENERATION_PENDING,
                workflow.updatedAt = :now
            where workflow.updatedAt < :cutoff
              and workflow.status = de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus.GENERATION_RUNNING
            """)
    int releaseStaleGenerations(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
