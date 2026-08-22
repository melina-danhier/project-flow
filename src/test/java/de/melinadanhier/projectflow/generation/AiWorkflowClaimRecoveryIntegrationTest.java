package de.melinadanhier.projectflow.generation;

import de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "projectflow.ai.recovery-delay=1h")
@ActiveProfiles("test")
class AiWorkflowClaimRecoveryIntegrationTest {

    @Autowired AiPlanGenerationWorkflowRepository workflowRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void preCheckClaimIsAtomicAndRejectsDuplicateClaim() {
        var workflow = pendingWorkflow();
        assertThat(workflowRepository.claimPreCheck(workflow.getId(), Instant.now())).isEqualTo(1);
        assertThat(workflowRepository.claimPreCheck(workflow.getId(), Instant.now())).isZero();
        assertThat(workflowRepository.findById(workflow.getId())).get()
                .extracting("status").isEqualTo(AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING);
    }

    @Test
    void releasesStaleWorkButNeverCompletedWorkflows() {
        var stale = pendingWorkflow();
        assertThat(workflowRepository.claimPreCheck(stale.getId(), Instant.now())).isEqualTo(1);
        Instant old = Instant.now().minus(1, ChronoUnit.HOURS);
        jdbcTemplate.update("update ai_plan_generation_workflows set updated_at = ? where id = ?", old, stale.getId());

        var completed = pendingWorkflow();
        completed.setStatus(AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED);
        workflowRepository.saveAndFlush(completed);
        jdbcTemplate.update("update ai_plan_generation_workflows set updated_at = ? where id = ?", old, completed.getId());

        int released = workflowRepository.releaseStalePreChecks(
                Instant.now().minus(5, ChronoUnit.MINUTES), Instant.now());

        assertThat(released).isEqualTo(1);
        assertThat(workflowRepository.findById(stale.getId())).get()
                .extracting("status").isEqualTo(AiPlanGenerationWorkflowStatus.PRE_CHECK_PENDING);
        assertThat(workflowRepository.findById(completed.getId())).get()
                .extracting("status").isEqualTo(AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED);
    }

    @Test
    void generationClaimIsAtomic() {
        var workflow = pendingWorkflow();
        workflow.setStatus(AiPlanGenerationWorkflowStatus.GENERATION_PENDING);
        workflowRepository.saveAndFlush(workflow);
        assertThat(workflowRepository.claimGeneration(workflow.getId(), Instant.now())).isEqualTo(1);
        assertThat(workflowRepository.claimGeneration(workflow.getId(), Instant.now())).isZero();
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
