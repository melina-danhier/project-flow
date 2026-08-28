package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.model.AiOperation;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.draft.service.DraftApplicationService;
import de.melinadanhier.projectflow.draft.model.DraftPlanStatus;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.draft.repository.PlanDraftRepository;
import de.melinadanhier.projectflow.ai.model.generation.*;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.model.workflow.AiWorkflowCompletion;
import de.melinadanhier.projectflow.generation.model.wizard.AiProjectTimeFrameType;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec;
import de.melinadanhier.projectflow.generation.service.retry.AiRetryBackoff;
import de.melinadanhier.projectflow.generation.service.workflow.AiWorkflowInitializationService;
import de.melinadanhier.projectflow.generation.service.workflow.AiGenerationWorkflowService;
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
import de.melinadanhier.projectflow.wizard.service.AiWizardCompletionService;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlanSectionRepository planSectionRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AiWorkflowPayloadCodec snapshotCodec;

    @Autowired
    private DraftApplicationService draftApplicationService;

    @Autowired
    private AiGenerationWorkflowService generationWorkflowService;

    @MockitoBean
    private AiClient aiClient;

    @MockitoBean
    private AiRetryBackoff backoff;

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
        AtomicBoolean transactionActiveDuringGenerationCall = new AtomicBoolean(true);
        AtomicBoolean committedWorkflowVisibleDuringAiCall = new AtomicBoolean(false);
        long workflowsBefore = workflowRepository.count();
        long draftsBefore = planDraftRepository.count();
        when(aiClient.preCheck(any())).thenAnswer(invocation -> {
            transactionActiveDuringAiCall.set(
                    TransactionSynchronizationManager.isActualTransactionActive());
            committedWorkflowVisibleDuringAiCall.set(workflowRepository.count() == workflowsBefore + 1);
            assertThat(planDraftRepository.count()).isEqualTo(draftsBefore);
            return AiPreCheckResult.withoutIssues();
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            transactionActiveDuringGenerationCall.set(
                    TransactionSynchronizationManager.isActualTransactionActive());
            assertThat(planDraftRepository.count()).isEqualTo(draftsBefore);
            return generatedPlan();
        }).when(aiClient).generatePlan(any());

        long sectionsBefore = planSectionRepository.count();
        long tasksBefore = taskRepository.count();
        long milestonesBefore = milestoneRepository.count();
        AiWorkflowCompletion completion = completionService.complete(token, owner.getId(), () -> snapshot);
        await(() -> workflowRepository.findById(completion.workflowId())
                .map(workflow -> workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED)
                .orElse(false));

        AiPlanGenerationWorkflow workflow = workflowRepository.findById(completion.workflowId()).orElseThrow();
        assertThat(workflow.getSnapshotVersion()).isEqualTo("ai-wizard-v2");
        assertThat(workflow.getCompletionToken()).isEqualTo(token);
        assertThat(workflow.getConsentConfirmedAt()).isNotNull();
        assertThat(workflow.getConsentVersion()).isEqualTo(AiWorkflowInitializationService.CONSENT_VERSION);
        assertThat(workflow.getPreCheckSchemaVersion()).isEqualTo("1.0");
        assertThat(workflow.getGenerationSchemaVersion()).isEqualTo("1.0");
        assertThat(snapshotCodec.readSnapshot(workflow.getConfirmedSnapshot())).isEqualTo(snapshot);
        assertThat(workflow.getPreCheckRetryCount()).isZero();
        assertThat(workflow.getGeneratedPlan()).isNull();
        assertThat(transactionActiveDuringAiCall).isFalse();
        assertThat(transactionActiveDuringGenerationCall).isFalse();
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
        assertThat(draft.getStatus()).isEqualTo(DraftPlanStatus.READY_FOR_REVIEW);
        assertThat(jdbcTemplate.queryForList(
                "select title from draft_sections where plan_draft_id = ? order by sort_order",
                String.class, draft.getId())).containsExactly("Vorbereitung");
        assertThat(jdbcTemplate.queryForList(
                "select title from draft_plan_elements where plan_draft_id = ? order by sort_order",
                String.class, draft.getId()))
                .containsExactlyInAnyOrder("Erster Schritt", "Zweiter Schritt", "Dritter Schritt",
                        "Vorbereitung abgeschlossen");

        draftApplicationService.apply(completion.projectId(), owner.getId());

        assertThat(planSectionRepository.count()).isEqualTo(sectionsBefore + 1);
        assertThat(taskRepository.count()).isEqualTo(tasksBefore + 3);
        assertThat(milestoneRepository.count()).isEqualTo(milestonesBefore + 1);
        assertThat(planDraftRepository.findById(draft.getId())).get()
                .extracting("status").isEqualTo(DraftPlanStatus.APPLIED);
        assertThat(workflowRepository.findById(completion.workflowId())).get()
                .extracting("status").isEqualTo(AiPlanGenerationWorkflowStatus.DRAFT_APPLIED);
        assertThat(generationWorkflowService.retry(completion.workflowId(), owner.getId())).isFalse();
        assertThat(projectRepository.findById(completion.projectId())).get().satisfies(project -> {
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
            assertThat(project.getLocation()).isEqualTo(ProjectLocation.OVERVIEW);
        });

        draftApplicationService.apply(completion.projectId(), owner.getId());
        assertThat(taskRepository.count()).isEqualTo(tasksBefore + 3);
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
                .thenThrow(new AiTechnicalException(
                        AiTechnicalErrorCode.PROVIDER_UNAVAILABLE, "Provider nicht erreichbar"));

        AiWorkflowCompletion completion = completionService.complete(
                UUID.randomUUID(), owner.getId(), this::snapshot);
        await(() -> workflowRepository.findById(completion.workflowId())
                .map(workflow -> workflow.getStatus() == AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE)
                .orElse(false));

        AiPlanGenerationWorkflow workflow = workflowRepository.findById(completion.workflowId()).orElseThrow();
        assertThat(workflow.getPreCheckRetryCount()).isEqualTo(2);
        assertThat(workflow.getConfirmedSnapshot()).isNotBlank();
        assertThat(workflow.getLastTechnicalError())
                .isEqualTo(AiTechnicalErrorCode.PROVIDER_UNAVAILABLE);
        assertThat(workflow.getLastAiOperation()).isEqualTo(AiOperation.PRE_CHECK);
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
        assertThat(workflow.getPreCheckRetryCount()).isZero();
        assertThat(workflow.getLastTechnicalError()).isNull();
        assertThat(workflow.getPreCheckResult()).contains("Der Zeitraum wirkt sehr knapp.");
        verify(aiClient, times(1)).preCheck(any());
    }

    @Test
    void invalidOutputStopsImmediatelyAsNonRetryableGenerationFailure() throws Exception {
        User owner = saveUser("ai-invalid-output@example.org");
        when(aiClient.preCheck(any())).thenReturn(AiPreCheckResult.withoutIssues());
        org.mockito.Mockito.doReturn(new GeneratedPlanResponse(List.of()))
                .when(aiClient).generatePlan(any());

        AiWorkflowCompletion completion = completionService.complete(
                UUID.randomUUID(), owner.getId(), this::snapshot);
        await(() -> workflowRepository.findById(completion.workflowId())
                .map(workflow -> workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_FAILED)
                .orElse(false));

        AiPlanGenerationWorkflow workflow = workflowRepository.findById(completion.workflowId()).orElseThrow();
        assertThat(workflow.getGenerationRoundAttemptCount()).isEqualTo(1);
        assertThat(workflow.getGenerationTotalAttemptCount()).isEqualTo(1);
        assertThat(workflow.getLastTechnicalError()).isEqualTo(AiTechnicalErrorCode.INVALID_AI_RESPONSE);
        assertThat(workflow.getLastAiOperation()).isEqualTo(AiOperation.PLAN_GENERATION);
        assertThat(workflow.getLastErrorRetryable()).isFalse();
        assertThat(planDraftRepository.findByProjectId(completion.projectId())).isEmpty();
        assertThat(projectRepository.findById(completion.projectId())).get()
                .extracting("status").isEqualTo(ProjectStatus.DRAFT);
    }

    @Test
    void nonRetryableClientConfigurationFailureStopsAfterFirstCall() throws Exception {
        User owner = saveUser("ai-configuration-failure@example.org");
        when(aiClient.preCheck(any())).thenReturn(AiPreCheckResult.withoutIssues());
        org.mockito.Mockito.doThrow(new AiTechnicalException(
                        AiTechnicalErrorCode.CLIENT_CONFIGURATION_ERROR, "Konfiguration ungültig"))
                .when(aiClient).generatePlan(any());

        AiWorkflowCompletion completion = completionService.complete(
                UUID.randomUUID(), owner.getId(), this::snapshot);
        await(() -> workflowRepository.findById(completion.workflowId())
                .map(workflow -> workflow.getStatus() == AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE)
                .orElse(false));

        AiPlanGenerationWorkflow workflow = workflowRepository.findById(completion.workflowId()).orElseThrow();
        assertThat(workflow.getGenerationRoundAttemptCount()).isEqualTo(1);
        assertThat(workflow.getGenerationTotalAttemptCount()).isEqualTo(1);
        assertThat(workflow.getLastTechnicalError())
                .isEqualTo(AiTechnicalErrorCode.CLIENT_CONFIGURATION_ERROR);
        assertThat(planDraftRepository.findByProjectId(completion.projectId())).isEmpty();
        assertThat(workflow.getLastAiOperation()).isEqualTo(AiOperation.PLAN_GENERATION);
        assertThat(workflow.getLastErrorRetryable()).isFalse();
        assertThat(generationWorkflowService.retry(completion.workflowId(), owner.getId())).isFalse();

        org.mockito.Mockito.doReturn(generatedPlan()).when(aiClient).generatePlan(any());
        assertThat(generationWorkflowService.retryAfterAdministrativeFix(completion.workflowId())).isTrue();
        await(() -> workflowRepository.findById(completion.workflowId())
                .map(current -> current.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED)
                .orElse(false));
        AiPlanGenerationWorkflow recovered = workflowRepository.findById(completion.workflowId()).orElseThrow();
        assertThat(recovered.getGenerationRoundAttemptCount()).isEqualTo(1);
        assertThat(recovered.getGenerationTotalAttemptCount()).isEqualTo(2);
    }

    @Test
    void manualRetryResetsRoundCounterKeepsTotalAndIsAtomic() throws Exception {
        User owner = saveUser("ai-manual-retry@example.org");
        User outsider = saveUser("ai-manual-retry-outsider@example.org");
        when(aiClient.preCheck(any())).thenReturn(AiPreCheckResult.withoutIssues());
        when(aiClient.generatePlan(any()))
                .thenThrow(new AiTechnicalException(
                        AiTechnicalErrorCode.PROVIDER_UNAVAILABLE,
                        "Provider vorübergehend nicht erreichbar"));

        AiWorkflowCompletion completion = completionService.complete(
                UUID.randomUUID(), owner.getId(), this::snapshot);
        await(() -> workflowRepository.findById(completion.workflowId())
                .map(workflow -> workflow.getStatus() == AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE)
                .orElse(false));

        AiPlanGenerationWorkflow failed = workflowRepository.findById(completion.workflowId()).orElseThrow();
        assertThat(failed.getGenerationRoundAttemptCount()).isEqualTo(3);
        assertThat(failed.getGenerationTotalAttemptCount()).isEqualTo(3);
        assertThat(failed.getLastTechnicalError()).isEqualTo(AiTechnicalErrorCode.PROVIDER_UNAVAILABLE);
        assertThat(failed.getLastAiOperation()).isEqualTo(AiOperation.PLAN_GENERATION);
        assertThat(failed.getLastErrorRetryable()).isTrue();
        assertThat(planDraftRepository.findByProjectId(completion.projectId())).isEmpty();
        assertThat(generationWorkflowService.retry(completion.workflowId(), outsider.getId())).isFalse();

        CountDownLatch generationStarted = new CountDownLatch(1);
        CountDownLatch releaseGeneration = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            generationStarted.countDown();
            if (!releaseGeneration.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Manueller Retry wurde im Test nicht freigegeben.");
            }
            return generatedPlan();
        }).when(aiClient).generatePlan(any());

        assertThat(generationWorkflowService.retry(completion.workflowId(), owner.getId())).isTrue();
        assertThat(generationStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(planDraftRepository.findByProjectId(completion.projectId())).isEmpty();
        assertThat(generationWorkflowService.retry(completion.workflowId(), owner.getId())).isFalse();
        releaseGeneration.countDown();
        await(() -> workflowRepository.findById(completion.workflowId())
                .map(workflow -> workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED)
                .orElse(false));

        AiPlanGenerationWorkflow completed = workflowRepository.findById(completion.workflowId()).orElseThrow();
        assertThat(completed.getGenerationRoundAttemptCount()).isEqualTo(1);
        assertThat(completed.getGenerationTotalAttemptCount()).isEqualTo(4);
        assertThat(completed.getLastTechnicalError()).isNull();
        assertThat(completed.getLastErrorRetryable()).isNull();
        assertThat(planDraftRepository.findByProjectId(completion.projectId())).get()
                .extracting("status").isEqualTo(DraftPlanStatus.READY_FOR_REVIEW);
        assertThat(generationWorkflowService.retry(completion.workflowId(), owner.getId())).isFalse();
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
                List.of(new GeneratedPhase(
                        "phase-1", "Vorbereitung", null,
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 21), 1,
                        List.of(
                                generatedTask("task-1", "Erster Schritt", 1),
                                generatedTask("task-2", "Zweiter Schritt", 2),
                                generatedTask("task-3", "Dritter Schritt", 3)),
                        List.of(new GeneratedMilestone(
                                "milestone-1", "Vorbereitung abgeschlossen",
                                LocalDate.of(2026, 9, 21), 2)))));
    }

    private GeneratedTask generatedTask(String id, String title, int order) {
        return new GeneratedTask(id, title, null, 1,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20),
                null, GeneratedElementOrigin.AI_INFERRED, order);
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
