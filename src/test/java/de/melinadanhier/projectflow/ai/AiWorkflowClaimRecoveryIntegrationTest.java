package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import de.melinadanhier.projectflow.generation.service.workflow.AiWorkflowControlService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "projectflow.ai.recovery-delay=1h")
@ActiveProfiles("test")
class AiWorkflowClaimRecoveryIntegrationTest {

    @Autowired AiPlanGenerationWorkflowRepository workflowRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AiWorkflowControlService controlService;

    @Test
    void preCheckClaimIsAtomicAndRejectsDuplicateClaim() {
        var workflow = pendingWorkflow();
        assertThat(workflowRepository.claimPreCheck(
                workflow.getId(), workflow.getActiveRunId(), Instant.now())).isEqualTo(1);
        assertThat(workflowRepository.claimPreCheck(
                workflow.getId(), workflow.getActiveRunId(), Instant.now())).isZero();
        assertThat(workflowRepository.findById(workflow.getId())).get()
                .extracting("status").isEqualTo(AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING);
    }

    @Test
    void parallelPreCheckClaimsHaveExactlyOneWinner() throws Exception {
        var workflow = pendingWorkflow();
        UUID runId = workflow.getActiveRunId();
        var barrier = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return workflowRepository.claimPreCheck(workflow.getId(), runId, Instant.now());
            });
            var second = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return workflowRepository.claimPreCheck(workflow.getId(), runId, Instant.now());
            });

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(1, 0);
        }
    }

    @Test
    void stalePreCheckRunCannotBeClaimed() {
        var workflow = pendingWorkflow();

        assertThat(workflowRepository.claimPreCheck(
                workflow.getId(), UUID.randomUUID(), Instant.now())).isZero();
        assertThat(workflowRepository.findById(workflow.getId())).get()
                .extracting("status").isEqualTo(AiPlanGenerationWorkflowStatus.PRE_CHECK_PENDING);
    }

    @Test
    void releasesStaleWorkButNeverCompletedWorkflows() {
        var stale = pendingWorkflow();
        assertThat(workflowRepository.claimPreCheck(
                stale.getId(), stale.getActiveRunId(), Instant.now())).isEqualTo(1);
        Instant old = Instant.now().minus(1, ChronoUnit.HOURS);
        jdbcTemplate.update("update ai_plan_generation_workflows set updated_at = ? where id = ?", old, stale.getId());

        var completed = pendingWorkflow();
        workflowRepository.claimPreCheck(
                completed.getId(), completed.getActiveRunId(), Instant.now());
        completed = workflowRepository.findById(completed.getId()).orElseThrow();
        completed.recordPreCheckResult("{}", false);
        completed.startGeneration(UUID.randomUUID(), Instant.now().plusSeconds(300));
        workflowRepository.saveAndFlush(completed);
        workflowRepository.claimGeneration(
                completed.getId(), completed.getActiveRunId(), Instant.now());
        completed = workflowRepository.findById(completed.getId()).orElseThrow();
        completed.recordGenerationCompleted("{\"sections\":[],\"criticalAssumptions\":[]}", false);
        workflowRepository.saveAndFlush(completed);
        jdbcTemplate.update("update ai_plan_generation_workflows set updated_at = ? where id = ?", old, completed.getId());

        jdbcTemplate.update("update ai_plan_generation_workflows set run_expires_at = ? where id = ?",
                Instant.now().minusSeconds(1), stale.getId());
        boolean released = controlService.expire(stale.getId());

        assertThat(released).isTrue();
        assertThat(workflowRepository.findById(stale.getId())).get()
                .extracting("status").isEqualTo(AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE);
        assertThat(workflowRepository.findById(completed.getId())).get()
                .extracting("status").isEqualTo(AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED);
    }

    @Test
    void generationClaimIsAtomic() {
        var workflow = pendingWorkflow();
        workflowRepository.claimPreCheck(
                workflow.getId(), workflow.getActiveRunId(), Instant.now());
        workflow = workflowRepository.findById(workflow.getId()).orElseThrow();
        workflow.recordPreCheckResult("{}", false);
        workflow.startGeneration(UUID.randomUUID(), Instant.now().plusSeconds(300));
        workflowRepository.saveAndFlush(workflow);
        assertThat(workflowRepository.claimGeneration(
                workflow.getId(), workflow.getActiveRunId(), Instant.now())).isEqualTo(1);
        assertThat(workflowRepository.claimGeneration(
                workflow.getId(), workflow.getActiveRunId(), Instant.now())).isZero();
    }

    @Test
    void parallelGenerationClaimsHaveExactlyOneWinner() throws Exception {
        var workflow = generationPendingWorkflow();
        UUID runId = workflow.getActiveRunId();
        var barrier = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return workflowRepository.claimGeneration(workflow.getId(), runId, Instant.now());
            });
            var second = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return workflowRepository.claimGeneration(workflow.getId(), runId, Instant.now());
            });

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(1, 0);
        }
    }

    @Test
    void staleGenerationRunCannotBeClaimed() {
        var workflow = generationPendingWorkflow();

        assertThat(workflowRepository.claimGeneration(
                workflow.getId(), UUID.randomUUID(), Instant.now())).isZero();
        assertThat(workflowRepository.findById(workflow.getId())).get()
                .extracting("status").isEqualTo(AiPlanGenerationWorkflowStatus.GENERATION_PENDING);
    }

    @Test
    void newPreCheckResultClearsPersistedWarningAcknowledgements() {
        var workflow = pendingWorkflow();
        workflowRepository.claimPreCheck(
                workflow.getId(), workflow.getActiveRunId(), Instant.now());
        workflow = workflowRepository.findById(workflow.getId()).orElseThrow();
        workflow.recordPreCheckResult("{}", true);
        workflow.acknowledgeWarning(0);
        workflowRepository.saveAndFlush(workflow);
        assertThat(workflowRepository.findById(workflow.getId()).orElseThrow()
                .getAcknowledgedWarningIndices()).containsExactly(0);

        jdbcTemplate.update("update ai_plan_generation_workflows set status = 'PRE_CHECK_RUNNING' where id = ?",
                workflow.getId());
        workflow = workflowRepository.findById(workflow.getId()).orElseThrow();
        workflow.recordPreCheckResult("{}", true);
        workflowRepository.saveAndFlush(workflow);

        assertThat(workflowRepository.findById(workflow.getId()).orElseThrow()
                .getAcknowledgedWarningIndices()).isEmpty();
    }

    @Test
    void releasesOnlyStaleRunningGenerations() {
        var stale = generationRunningWorkflow();
        var recent = generationRunningWorkflow();
        jdbcTemplate.update("update ai_plan_generation_workflows set run_expires_at = ? where id = ?",
                Instant.now().minusSeconds(1), stale.getId());

        boolean released = controlService.expire(stale.getId());

        assertThat(released).isTrue();
        assertThat(workflowRepository.findById(stale.getId())).get()
                .extracting("status").isEqualTo(AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE);
        assertThat(workflowRepository.findById(recent.getId())).get()
                .extracting("status").isEqualTo(AiPlanGenerationWorkflowStatus.GENERATION_RUNNING);
    }

    private AiPlanGenerationWorkflow generationRunningWorkflow() {
        var workflow = generationPendingWorkflow();
        workflowRepository.claimGeneration(
                workflow.getId(), workflow.getActiveRunId(), Instant.now());
        return workflowRepository.findById(workflow.getId()).orElseThrow();
    }

    private AiPlanGenerationWorkflow generationPendingWorkflow() {
        var workflow = pendingWorkflow();
        workflowRepository.claimPreCheck(
                workflow.getId(), workflow.getActiveRunId(), Instant.now());
        workflow = workflowRepository.findById(workflow.getId()).orElseThrow();
        workflow.recordPreCheckResult("{}", false);
        workflow.startGeneration(UUID.randomUUID(), Instant.now().plusSeconds(300));
        workflowRepository.saveAndFlush(workflow);
        return workflowRepository.findById(workflow.getId()).orElseThrow();
    }

    private AiPlanGenerationWorkflow pendingWorkflow() {
        Project project = new Project();
        project.setTitle("Claim " + UUID.randomUUID());
        project.setCreationType(CreationType.AI);
        project.setStatus(ProjectStatus.DRAFT);
        project.setLocation(ProjectLocation.DRAFT);
        project.setCategory(TemplateCategory.OTHER);
        project.setCollaborationMode(CollaborationMode.INDIVIDUAL);
        projectRepository.saveAndFlush(project);
        return workflowRepository.saveAndFlush(AiPlanGenerationWorkflow.create(
                project, "{}", "ai-wizard-v2", UUID.randomUUID(), Instant.now(), "v1"));
    }
}
