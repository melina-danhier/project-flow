package de.melinadanhier.projectflow.generation;

import de.melinadanhier.projectflow.generation.client.AiGenerationClient;
import de.melinadanhier.projectflow.generation.client.AiPreCheckTechnicalException;
import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckResult;
import de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.model.AiPreCheckErrorCode;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.generation.repository.PlanDraftRepository;
import de.melinadanhier.projectflow.generation.service.AiSnapshotCodec;
import de.melinadanhier.projectflow.generation.service.AiPreCheckBackoff;
import de.melinadanhier.projectflow.generation.service.AiWorkflowPersistenceService;
import de.melinadanhier.projectflow.generation.service.AiWizardCompletionService;
import de.melinadanhier.projectflow.generation.service.AiWorkflowCompletion;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMemberRole;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectMemberRepository;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import de.melinadanhier.projectflow.planelement.repository.MilestoneRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import de.melinadanhier.projectflow.planelement.repository.TaskRepository;
import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class AiWorkflowIntegrationTest {

    @Autowired
    private AiWizardCompletionService completionService;

    @Autowired
    private AiPlanGenerationWorkflowRepository workflowRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private PlanDraftRepository planDraftRepository;

    @Autowired
    private PlanSectionRepository planSectionRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AiSnapshotCodec snapshotCodec;

    @MockitoBean
    private AiGenerationClient aiGenerationClient;

    @MockitoBean
    private AiPreCheckBackoff backoff;

    @Test
    void persistsDraftOwnerWorkflowAndExactSnapshotBeforeCallingAiAfterCommit() throws Exception {
        User owner = saveUser("ai-workflow-owner@example.org");
        AiWizardSnapshot snapshot = snapshot();
        UUID token = UUID.randomUUID();
        AtomicBoolean transactionActiveDuringAiCall = new AtomicBoolean(true);
        AtomicBoolean committedWorkflowVisibleDuringAiCall = new AtomicBoolean(false);
        long workflowsBefore = workflowRepository.count();
        when(aiGenerationClient.preCheck(any())).thenAnswer(invocation -> {
            transactionActiveDuringAiCall.set(
                    TransactionSynchronizationManager.isActualTransactionActive());
            committedWorkflowVisibleDuringAiCall.set(workflowRepository.count() == workflowsBefore + 1);
            return AiPreCheckResult.withoutIssues();
        });

        long sectionsBefore = planSectionRepository.count();
        long tasksBefore = taskRepository.count();
        long milestonesBefore = milestoneRepository.count();
        long draftsBefore = planDraftRepository.count();
        AiWorkflowCompletion completion = completionService.complete(token, owner.getId(), () -> snapshot);
        await(() -> workflowRepository.findById(completion.workflowId())
                .map(workflow -> workflow.getStatus() == AiPlanGenerationWorkflowStatus.PRE_CHECK_PASSED)
                .orElse(false));

        AiPlanGenerationWorkflow workflow = workflowRepository.findById(completion.workflowId()).orElseThrow();
        assertThat(workflow.getSnapshotVersion()).isEqualTo("ai-wizard-v1");
        assertThat(workflow.getCompletionToken()).isEqualTo(token);
        assertThat(workflow.getConsentConfirmedAt()).isNotNull();
        assertThat(workflow.getConsentVersion()).isEqualTo(AiWorkflowPersistenceService.CONSENT_VERSION);
        assertThat(snapshotCodec.readSnapshot(workflow.getConfirmedSnapshot())).isEqualTo(snapshot);
        assertThat(workflow.getRetryCount()).isZero();
        assertThat(transactionActiveDuringAiCall).isFalse();
        assertThat(committedWorkflowVisibleDuringAiCall).isTrue();

        assertThat(projectRepository.findById(completion.projectId())).get().satisfies(project -> {
            assertThat(project.getTitle()).isEqualTo(snapshot.title());
            assertThat(project.getDescription()).isEqualTo(snapshot.description());
            assertThat(project.getStartDate()).isEqualTo(snapshot.startDate());
            assertThat(project.getEndDate()).isEqualTo(snapshot.endDate());
            assertThat(project.getCategory()).isEqualTo(snapshot.category());
            assertThat(project.getProjectType()).isEqualTo(snapshot.projectType());
            assertThat(project.getCollaborationMode()).isEqualTo(snapshot.collaborationMode());
            assertThat(project.getCreationType()).isEqualTo(CreationType.AI);
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.DRAFT);
            assertThat(project.getLocation()).isEqualTo(ProjectLocation.DRAFT);
        });
        assertThat(projectMemberRepository.findByProjectIdAndUserId(completion.projectId(), owner.getId()))
                .get().satisfies(membership -> {
                    assertThat(membership.getRole()).isEqualTo(ProjectMemberRole.OWNER);
                    assertThat(membership.isActive()).isTrue();
                });
        assertThat(planSectionRepository.count()).isEqualTo(sectionsBefore);
        assertThat(taskRepository.count()).isEqualTo(tasksBefore);
        assertThat(milestoneRepository.count()).isEqualTo(milestonesBefore);
        assertThat(planDraftRepository.count()).isEqualTo(draftsBefore);
    }

    @Test
    void repeatedCompletionTokenReturnsTheExistingWorkflowWithoutDuplicates() throws Exception {
        User owner = saveUser("ai-idempotency@example.org");
        UUID token = UUID.randomUUID();
        when(aiGenerationClient.preCheck(any())).thenReturn(AiPreCheckResult.withoutIssues());
        long projectsBefore = projectRepository.count();
        long workflowsBefore = workflowRepository.count();

        AiWorkflowCompletion first = completionService.complete(token, owner.getId(), this::snapshot);
        AiWorkflowCompletion second = completionService.complete(token, owner.getId(), () -> {
            throw new AssertionError("Der Wizard-Snapshot darf beim Retry nicht erneut gelesen werden.");
        });
        await(() -> workflowRepository.findById(first.workflowId())
                .map(workflow -> workflow.getStatus() == AiPlanGenerationWorkflowStatus.PRE_CHECK_PASSED)
                .orElse(false));

        assertThat(second).isEqualTo(first);
        assertThat(projectRepository.count()).isEqualTo(projectsBefore + 1);
        assertThat(workflowRepository.count()).isEqualTo(workflowsBefore + 1);
        verify(aiGenerationClient, times(1)).preCheck(any());
    }

    @Test
    void persistenceFailureRollsBackProjectOwnerAndWorkflowAndDoesNotStartAi() {
        User owner = saveUser("ai-rollback@example.org");
        AiWizardSnapshot invalid = new AiWizardSnapshot(
                "Rollback", "x".repeat(2001), LocalDate.now(), LocalDate.now(),
                CollaborationMode.INDIVIDUAL, TemplateCategory.OTHER, "Test",
                "Nur Snapshot", null, null);
        long projectsBefore = projectRepository.count();
        long membersBefore = projectMemberRepository.count();
        long workflowsBefore = workflowRepository.count();

        assertThatThrownBy(() -> completionService.complete(
                UUID.randomUUID(), owner.getId(), () -> invalid))
                .isInstanceOf(ConstraintViolationException.class);

        assertThat(projectRepository.count()).isEqualTo(projectsBefore);
        assertThat(projectMemberRepository.count()).isEqualTo(membersBefore);
        assertThat(workflowRepository.count()).isEqualTo(workflowsBefore);
        verify(aiGenerationClient, times(0)).preCheck(any());
    }

    @Test
    void technicalFailuresUseOnlyLimitedRetriesAndKeepProjectDraft() throws Exception {
        User owner = saveUser("ai-retry@example.org");
        when(aiGenerationClient.preCheck(any()))
                .thenThrow(new AiPreCheckTechnicalException("Provider nicht erreichbar"));

        AiWorkflowCompletion completion = completionService.complete(
                UUID.randomUUID(), owner.getId(), this::snapshot);
        await(() -> workflowRepository.findById(completion.workflowId())
                .map(workflow -> workflow.getStatus() == AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE)
                .orElse(false));

        AiPlanGenerationWorkflow workflow = workflowRepository.findById(completion.workflowId()).orElseThrow();
        assertThat(workflow.getRetryCount()).isEqualTo(2);
        assertThat(workflow.getConfirmedSnapshot()).isNotBlank();
        assertThat(workflow.getLastTechnicalError())
                .isEqualTo(AiPreCheckErrorCode.AI_PROVIDER_UNAVAILABLE.name());
        assertThat(projectRepository.findById(completion.projectId())).get()
                .extracting("status").isEqualTo(ProjectStatus.DRAFT);
        verify(aiGenerationClient, times(3)).preCheck(any());
        try {
            verify(backoff).waitBeforeRetry(1);
            verify(backoff).waitBeforeRetry(2);
        } catch (InterruptedException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void plausibilityIssuesAreStoredWithoutRetryOrTechnicalFailure() throws Exception {
        User owner = saveUser("ai-plausibility@example.org");
        when(aiGenerationClient.preCheck(any())).thenReturn(
                new AiPreCheckResult(true, List.of("Der Zeitraum wirkt sehr knapp.")));

        AiWorkflowCompletion completion = completionService.complete(
                UUID.randomUUID(), owner.getId(), this::snapshot);
        await(() -> workflowRepository.findById(completion.workflowId())
                .map(workflow -> workflow.getStatus() == AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW)
                .orElse(false));

        AiPlanGenerationWorkflow workflow = workflowRepository.findById(completion.workflowId()).orElseThrow();
        assertThat(workflow.getRetryCount()).isZero();
        assertThat(workflow.getLastTechnicalError()).isNull();
        assertThat(workflow.getPreCheckResult()).contains("Der Zeitraum wirkt sehr knapp.");
        verify(aiGenerationClient, times(1)).preCheck(any());
    }

    private AiWizardSnapshot snapshot() {
        return new AiWizardSnapshot(
                "Umzug planen",
                "Wohnungswechsel organisieren",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 21),
                CollaborationMode.GROUP,
                TemplateCategory.HOME,
                "Umzug",
                "Bis zum Monatsende umziehen",
                "Budget 2.000 Euro",
                "Kartons sind vorhanden"
        );
    }

    private User saveUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("AI Workflow Test");
        user.setPasswordHash("$2a$12$test-hash");
        user.setEnabled(true);
        return userRepository.saveAndFlush(user);
    }

    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
