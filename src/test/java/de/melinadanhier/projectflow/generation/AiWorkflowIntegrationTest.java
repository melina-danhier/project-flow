package de.melinadanhier.projectflow.generation;

import de.melinadanhier.projectflow.generation.client.AiClient;
import de.melinadanhier.projectflow.generation.client.AiProviderUnavailableException;
import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckProblem;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckResult;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckSeverity;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedElementOrigin;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPlanMetadata;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPlanResponse;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPhase;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedTask;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedMilestone;
import de.melinadanhier.projectflow.generation.model.PlanDraftStatus;
import de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.model.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.generation.repository.PlanDraftRepository;
import de.melinadanhier.projectflow.generation.repository.DraftSectionRepository;
import de.melinadanhier.projectflow.generation.repository.DraftPlanElementRepository;
import de.melinadanhier.projectflow.generation.service.AiSnapshotCodec;
import de.melinadanhier.projectflow.generation.service.AiPreCheckBackoff;
import de.melinadanhier.projectflow.generation.service.AiWorkflowPersistenceService;
import de.melinadanhier.projectflow.generation.service.AiWizardCompletionService;
import de.melinadanhier.projectflow.generation.service.AiWorkflowCompletion;
import de.melinadanhier.projectflow.generation.service.DraftService;
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
import de.melinadanhier.projectflow.generation.dto.request.AiProjectTimeFrameType;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
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
    private DraftSectionRepository draftSectionRepository;

    @Autowired
    private DraftPlanElementRepository draftPlanElementRepository;

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

    @Autowired
    private DraftService draftService;

    @MockitoBean
    private AiClient aiClient;

    @MockitoBean
    private AiPreCheckBackoff backoff;

    @BeforeEach
    void configureGenerationResponse() {
        when(aiClient.generatePlan(any())).thenReturn(generatedPlan());
    }

    @Test
    void persistsDraftOwnerWorkflowAndExactSnapshotBeforeCallingAiAfterCommit() throws Exception {
        User owner = saveUser("ai-workflow-owner@example.org");
        AiWizardSnapshot snapshot = snapshot();
        UUID token = UUID.randomUUID();
        AtomicBoolean transactionActiveDuringAiCall = new AtomicBoolean(true);
        AtomicBoolean committedWorkflowVisibleDuringAiCall = new AtomicBoolean(false);
        long workflowsBefore = workflowRepository.count();
        when(aiClient.preCheck(any())).thenAnswer(invocation -> {
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
                .map(workflow -> workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED)
                .orElse(false));

        AiPlanGenerationWorkflow workflow = workflowRepository.findById(completion.workflowId()).orElseThrow();
        assertThat(workflow.getSnapshotVersion()).isEqualTo("ai-wizard-v2");
        assertThat(workflow.getCompletionToken()).isEqualTo(token);
        assertThat(workflow.getConsentConfirmedAt()).isNotNull();
        assertThat(workflow.getConsentVersion()).isEqualTo(AiWorkflowPersistenceService.CONSENT_VERSION);
        assertThat(snapshotCodec.readSnapshot(workflow.getConfirmedSnapshot())).isEqualTo(snapshot);
        assertThat(workflow.getRetryCount()).isZero();
        assertThat(workflow.getGeneratedPlan()).contains("Erster Schritt");
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
        assertThat(planDraftRepository.count()).isEqualTo(draftsBefore + 1);
        var draft = planDraftRepository.findByProjectId(completion.projectId()).orElseThrow();
        assertThat(draft.getStatus()).isEqualTo(PlanDraftStatus.READY_FOR_REVIEW);
        assertThat(draftSectionRepository.findAllByPlanDraftIdOrderBySortOrderAsc(draft.getId()))
                .extracting("title").containsExactly("Vorbereitung");
        assertThat(draftPlanElementRepository.findAllByPlanDraftIdOrderBySortOrderAsc(draft.getId()))
                .extracting("title")
                .containsExactlyInAnyOrder("Erster Schritt", "Vorbereitung abgeschlossen");

        draftService.apply(completion.projectId(), owner.getId());

        assertThat(planSectionRepository.count()).isEqualTo(sectionsBefore + 1);
        assertThat(taskRepository.count()).isEqualTo(tasksBefore + 1);
        assertThat(milestoneRepository.count()).isEqualTo(milestonesBefore + 1);
        assertThat(planDraftRepository.findById(draft.getId())).get()
                .extracting("status").isEqualTo(PlanDraftStatus.APPLIED);
        assertThat(workflowRepository.findById(completion.workflowId())).get()
                .extracting("status").isEqualTo(AiPlanGenerationWorkflowStatus.DRAFT_APPLIED);
        assertThat(projectRepository.findById(completion.projectId())).get().satisfies(project -> {
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
            assertThat(project.getLocation()).isEqualTo(ProjectLocation.OVERVIEW);
        });

        draftService.apply(completion.projectId(), owner.getId());
        assertThat(taskRepository.count()).isEqualTo(tasksBefore + 1);
    }

    @Test
    void repeatedCompletionTokenReturnsTheExistingWorkflowWithoutDuplicates() throws Exception {
        User owner = saveUser("ai-idempotency@example.org");
        UUID token = UUID.randomUUID();
        when(aiClient.preCheck(any())).thenReturn(AiPreCheckResult.withoutIssues());
        long projectsBefore = projectRepository.count();
        long workflowsBefore = workflowRepository.count();

        AiWorkflowCompletion first = completionService.complete(token, owner.getId(), this::snapshot);
        AiWorkflowCompletion second = completionService.complete(token, owner.getId(), () -> {
            throw new AssertionError("Der Wizard-Snapshot darf beim Retry nicht erneut gelesen werden.");
        });
        await(() -> workflowRepository.findById(first.workflowId())
                .map(workflow -> workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED)
                .orElse(false));

        assertThat(second).isEqualTo(first);
        assertThat(projectRepository.count()).isEqualTo(projectsBefore + 1);
        assertThat(workflowRepository.count()).isEqualTo(workflowsBefore + 1);
        verify(aiClient, times(1)).preCheck(any());
        verify(aiClient, times(1)).generatePlan(any());
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
        verify(aiClient, times(0)).preCheck(any());
    }

    @Test
    void technicalFailuresUseOnlyLimitedRetriesAndKeepProjectDraft() throws Exception {
        User owner = saveUser("ai-retry@example.org");
        when(aiClient.preCheck(any()))
                .thenThrow(new AiProviderUnavailableException("Provider nicht erreichbar"));

        AiWorkflowCompletion completion = completionService.complete(
                UUID.randomUUID(), owner.getId(), this::snapshot);
        await(() -> workflowRepository.findById(completion.workflowId())
                .map(workflow -> workflow.getStatus() == AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE)
                .orElse(false));

        AiPlanGenerationWorkflow workflow = workflowRepository.findById(completion.workflowId()).orElseThrow();
        assertThat(workflow.getRetryCount()).isEqualTo(2);
        assertThat(workflow.getConfirmedSnapshot()).isNotBlank();
        assertThat(workflow.getLastTechnicalError())
                .isEqualTo(AiTechnicalErrorCode.PROVIDER_UNAVAILABLE);
        assertThat(projectRepository.findById(completion.projectId())).get()
                .extracting("status").isEqualTo(ProjectStatus.DRAFT);
        verify(aiClient, times(3)).preCheck(any());
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
        when(aiClient.preCheck(any())).thenReturn(new AiPreCheckResult(
                java.util.List.of(new AiPreCheckProblem(
                        AiPreCheckSeverity.WARNING,
                        "Der Zeitraum wirkt sehr knapp.",
                        "Plane mehr Zeit ein."))));

        AiWorkflowCompletion completion = completionService.complete(
                UUID.randomUUID(), owner.getId(), this::snapshot);
        await(() -> workflowRepository.findById(completion.workflowId())
                .map(workflow -> workflow.getStatus() == AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW)
                .orElse(false));

        AiPlanGenerationWorkflow workflow = workflowRepository.findById(completion.workflowId()).orElseThrow();
        assertThat(workflow.getRetryCount()).isZero();
        assertThat(workflow.getLastTechnicalError()).isNull();
        assertThat(workflow.getPreCheckResult()).contains("Der Zeitraum wirkt sehr knapp.");
        verify(aiClient, times(1)).preCheck(any());
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
                "Kartons sind vorhanden",
                AiProjectTimeFrameType.START_AND_DURATION,
                21
        );
    }

    private GeneratedPlanResponse generatedPlan() {
        return new GeneratedPlanResponse(
                new GeneratedPlanMetadata("Testentwurf", List.of()),
                List.of(new GeneratedPhase(
                        "phase-1", "Vorbereitung", null, null, null, 1,
                        List.of(new GeneratedTask(
                                "task-1", "Erster Schritt", null, 1, null, null,
                                null, GeneratedElementOrigin.AI_INFERRED, 1)),
                        List.of(new GeneratedMilestone(
                                "milestone-1", "Vorbereitung abgeschlossen", null, 2)))));
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
