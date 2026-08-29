package de.melinadanhier.projectflow.draft;

import de.melinadanhier.projectflow.ai.exception.*;
import de.melinadanhier.projectflow.ai.model.AiOperation;
import de.melinadanhier.projectflow.ai.model.generation.*;
import de.melinadanhier.projectflow.ai.model.precheck.*;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.draft.mapper.GeneratedPlanDraftMapper;
import de.melinadanhier.projectflow.draft.model.*;
import de.melinadanhier.projectflow.draft.repository.PlanDraftRepository;
import de.melinadanhier.projectflow.draft.service.PlanDraftMaterializationService;
import de.melinadanhier.projectflow.generation.model.workflow.*;
import de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.generation.service.coordination.AiPlanGenerationCoordinator;
import de.melinadanhier.projectflow.generation.service.plan.AiPlanGenerationService;
import de.melinadanhier.projectflow.generation.service.workflow.AiGenerationWorkflowService;
import de.melinadanhier.projectflow.plancontainer.project.model.*;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** No test transaction: every assertion observes a committed or rolled-back service transaction. */
@SpringBootTest(properties = "projectflow.ai.recovery-delay=1h")
@ActiveProfiles("test")
class PlanDraftMaterializationIntegrationTest {
    @Autowired AiGenerationWorkflowService workflowService;
    @Autowired AiPlanGenerationCoordinator coordinator;
    @Autowired AiPlanGenerationWorkflowRepository workflows;
    @Autowired ProjectRepository projects;
    @Autowired AiWorkflowPayloadCodec codec;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlanDraftRepository drafts;
    @MockitoSpyBean PlanDraftMaterializationService storage;
    @MockitoSpyBean GeneratedPlanDraftMapper mapper;
    @MockitoBean AiPlanGenerationService generation;

    @Test
    void persistsCompleteGraphAndBothStatusesWithoutCopyingWorkflowData() {
        Fixture f = runningWorkflow();
        String snapshot = workflows.findById(f.workflowId()).orElseThrow().getConfirmedSnapshot();
        assertThat(drafts.findByProjectId(f.projectId())).isEmpty();
        assertThat(workflowService.recordSuccess(f.workflowId(), generatedPlan())).isTrue();

        assertCompleteDraft(f);
        var workflow = workflows.findById(f.workflowId()).orElseThrow();
        assertThat(workflow.getStatus()).isEqualTo(AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED);
        assertThat(workflow.getConfirmedSnapshot()).isEqualTo(snapshot);
        assertThat(workflow.getGeneratedPlan()).isNull();
        assertThat(workflow.getPreCheckResult()).contains("Nur im Pre-Check");
        assertThat(workflow.getAcknowledgedWarningIndices()).containsExactly(0);
        assertNoActivePlan(f);
    }

    @Test
    void mapsOutsideTransactionsEvenWhenCallerHasOne() {
        Fixture f = runningWorkflow();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return invocation.callRealMethod();
        }).when(mapper).map(any());
        inTransaction(() -> assertThat(workflowService.recordSuccess(f.workflowId(), generatedPlan())).isTrue());
        assertCompleteDraft(f);
    }

    @Test
    void directMaterializationJoinsCallerTransactionAndRollsBackWithIt() {
        Fixture f = runningWorkflow();
        var contents = mapper.map(generatedPlan());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(storage.materialize(f.workflowId(), contents)).isTrue();
            assertThat(drafts.findByProjectId(f.projectId())).isPresent();
            assertThat(workflows.findById(f.workflowId()).orElseThrow().getStatus())
                    .isEqualTo(AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED);
            status.setRollbackOnly();
        });
        assertNoDraft(f);
        assertNoActivePlan(f);
        assertThat(workflows.findById(f.workflowId()).orElseThrow().getStatus())
                .isEqualTo(AiPlanGenerationWorkflowStatus.GENERATION_RUNNING);
    }

    @Test
    void realDatabaseFailureRollsBackFlushedDraftGraphAndSuccessBeforeRecordingFailure() {
        Fixture f = runningWorkflow();
        AtomicBoolean graphWasFlushed = new AtomicBoolean();
        doAnswer(invocation -> {
            boolean result = (boolean) invocation.callRealMethod();
            DraftPlan persisted = drafts.findByProjectId(f.projectId()).orElseThrow();
            assertThat(jdbc.queryForObject(
                    "select count(*) from draft_plan_elements where plan_draft_id = ?", Integer.class,
                    persisted.getId())).isEqualTo(5);
            assertThat(jdbc.queryForObject(
                    "select status from ai_plan_generation_workflows where id = ?", String.class,
                    f.workflowId())).isEqualTo("GENERATION_COMPLETED");
            assertThat(jdbc.queryForObject(
                    "select count(*) from draft_task_prerequisites p join draft_plan_elements e "
                            + "on e.id = p.successor_draft_task_id where e.plan_draft_id = ?",
                    Integer.class, persisted.getId())).isEqualTo(2);
            graphWasFlushed.set(true);
            // Real database constraint violation AFTER the complete graph and statuses were flushed.
            jdbc.update("insert into draft_task_prerequisites "
                    + "(successor_draft_task_id, prerequisite_draft_task_id) values (null, null)");
            return result;
        }).when(storage).materialize(eq(f.workflowId()), any());
        stubGeneration(generatedPlan());

        // The failure transaction must commit independently, even if an outer caller rolls back.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            coordinator.generateClaimed(work(f));
            status.setRollbackOnly();
        });

        assertThat(graphWasFlushed).isTrue();
        assertNoDraft(f);
        assertNoActivePlan(f);
        var workflow = workflows.findById(f.workflowId()).orElseThrow();
        assertThat(workflow.getStatus()).isEqualTo(AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE);
        assertThat(workflow.getLastTechnicalError()).isEqualTo(AiTechnicalErrorCode.UNKNOWN_AI_ERROR);
        assertThat(workflow.getLastAiOperation()).isEqualTo(AiOperation.PLAN_GENERATION);

        // After an explicit manual repair/retry, only the existing workflow is reused.
        doCallRealMethod().when(storage).materialize(eq(f.workflowId()), any());
        prepareRetry(f);
        assertThat(workflowService.recordSuccess(f.workflowId(), generatedPlan())).isTrue();
        assertCompleteDraft(f);
    }

    @Test
    void mappingFailureLeavesNoDraftAndRecordsValidationCode() {
        Fixture f = runningWorkflow();
        stubGeneration(new GeneratedPlanResponse(List.of(new GeneratedSection(
                "p", "Section", null, 1,
                List.of(task("t", "Aufgabe", 1, null, List.of("missing"))), List.of()))));
        coordinator.generateClaimed(work(f));
        assertNoDraft(f);
        var workflow = workflows.findById(f.workflowId()).orElseThrow();
        assertThat(workflow.getStatus()).isEqualTo(AiPlanGenerationWorkflowStatus.GENERATION_FAILED);
        assertThat(workflow.getLastTechnicalError()).isEqualTo(AiTechnicalErrorCode.INVALID_AI_RESPONSE);
        assertNoActivePlan(f);
    }

    @Test
    void providerFailureAndRetryUseOnlyWorkflowUntilFirstSuccess() {
        Fixture f = runningWorkflow();
        when(generation.generatePlan(any(), anyList(), anyInt(), anyString(), any(Runnable.class)))
                .thenThrow(new AiTechnicalException(AiTechnicalErrorCode.PROVIDER_UNAVAILABLE, "Nicht erreichbar"));
        coordinator.generateClaimed(work(f));
        assertNoDraft(f);
        assertThat(workflows.findById(f.workflowId()).orElseThrow().getLastTechnicalError())
                .isEqualTo(AiTechnicalErrorCode.PROVIDER_UNAVAILABLE);
        prepareRetry(f);
        stubGeneration(generatedPlan());
        coordinator.generateClaimed(work(f));
        assertCompleteDraft(f);
    }

    @Test
    void completedWorkflowRejectsDuplicateSuccessAndLateFailure() {
        Fixture f = runningWorkflow();
        assertThat(workflowService.recordSuccess(f.workflowId(), generatedPlan())).isTrue();
        var draft = drafts.findByProjectId(f.projectId()).orElseThrow();
        long version = workflows.findById(f.workflowId()).orElseThrow().getLockVersion();
        assertThat(workflowService.recordSuccess(f.workflowId(), generatedPlan())).isFalse();
        assertThat(workflowService.recordTechnicalFailure(f.workflowId(), AiTechnicalError.from(
                new IllegalStateException("Verspäteter Fehler"), AiOperation.PLAN_GENERATION))).isFalse();
        assertThat(drafts.findByProjectId(f.projectId()).orElseThrow().getId()).isEqualTo(draft.getId());
        assertThat(workflows.findById(f.workflowId()).orElseThrow().getLockVersion()).isEqualTo(version);
        assertCompleteDraft(f);
    }

    @Test
    void parallelCompletionsProduceExactlyOneDraft() throws Exception {
        Fixture f = runningWorkflow();
        CountDownLatch mapped = new CountDownLatch(2);
        doAnswer(invocation -> {
            var result = invocation.callRealMethod();
            mapped.countDown();
            assertThat(mapped.await(10, TimeUnit.SECONDS)).isTrue();
            return result;
        }).when(mapper).map(any());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> workflowService.recordSuccess(f.workflowId(), generatedPlan()));
            var second = executor.submit(() -> workflowService.recordSuccess(f.workflowId(), generatedPlan()));
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertCompleteDraft(f);
        assertNoActivePlan(f);
    }

    @ParameterizedTest
    @EnumSource(DraftPlanStatus.class)
    void existingDraftIsNeverOverwritten(DraftPlanStatus status) {
        Fixture f = runningWorkflow();
        inTransaction(() -> {
            DraftPlan existing = new DraftPlan();
            existing.setProject(projects.findById(f.projectId()).orElseThrow());
            existing.setStatus(status);
            DraftSection section = new DraftSection();
            section.setTitle("Bestehender Inhalt");
            existing.addSection(section);
            drafts.saveAndFlush(existing);
        });
        UUID draftId = drafts.findByProjectId(f.projectId()).orElseThrow().getId();
        assertThatThrownBy(() -> workflowService.recordSuccess(f.workflowId(), generatedPlan()))
                .isInstanceOf(ConflictException.class);
        readDraft(f, draft -> {
            assertThat(draft.getId()).isEqualTo(draftId);
            assertThat(draft.getStatus()).isEqualTo(status);
            assertThat(draft.getSections()).extracting(DraftSection::getTitle).containsExactly("Bestehender Inhalt");
            assertThat(draft.getElements()).isEmpty();
        });
    }

    private Fixture runningWorkflow() {
        UUID workflowId = new TransactionTemplate(transactionManager).execute(status -> {
            Project project = new Project();
            project.setTitle("Atomarer Plan");
            project.setCreationType(CreationType.AI);
            project.setStatus(ProjectStatus.DRAFT);
            project.setLocation(ProjectLocation.DRAFT);
            projects.saveAndFlush(project);
            var workflow = AiPlanGenerationWorkflow.create(project, "{\"title\":\"Unveränderlich\"}",
                    "ai-wizard-v2", UUID.randomUUID(), Instant.now(), "v1");
            return workflows.saveAndFlush(workflow).getId();
        });
        workflows.claimPreCheck(workflowId, Instant.now());
        inTransaction(() -> {
            var workflow = workflows.findById(workflowId).orElseThrow();
            workflow.recordPreCheckResult(codec.writePreCheckResult(new AiPreCheckResult(List.of(
                    new AiPreCheckProblem(AiPreCheckSeverity.WARNING, "Nur im Pre-Check", "Ignoriert")))), true);
            workflow.acknowledgeWarning(0);
            workflow.approvePreCheck();
        });
        assertThat(workflows.claimGeneration(workflowId, Instant.now())).isEqualTo(1);
        var workflow = workflows.findById(workflowId).orElseThrow();
        return new Fixture(workflow.getId(), workflow.getProject().getId());
    }

    private void prepareRetry(Fixture f) {
        inTransaction(() -> workflows.findById(f.workflowId()).orElseThrow().prepareManualGenerationRetry());
        assertNoDraft(f);
        assertThat(workflows.claimGeneration(f.workflowId(), Instant.now())).isEqualTo(1);
        assertNoDraft(f);
    }

    private void stubGeneration(GeneratedPlanResponse response) {
        doReturn(response).when(generation).generatePlan(any(), anyList(), anyInt(), anyString(), any(Runnable.class));
    }

    private AiGenerationWork work(Fixture f) {
        return new AiGenerationWork(f.workflowId(), null, List.of(), 0, "generation-v1");
    }

    private void assertCompleteDraft(Fixture f) {
        assertThat(jdbc.queryForObject("select count(*) from plan_drafts where project_id = ?",
                Integer.class, f.projectId())).isEqualTo(1);
        readDraft(f, draft -> {
            assertThat(draft.getStatus()).isEqualTo(DraftPlanStatus.READY_FOR_REVIEW);
            assertThat(draft.getGeneratedAt()).isNotNull();
            assertThat(draft.getSections()).extracting(DraftSection::getSortOrder).containsExactly(1, 2);
            assertThat(draft.getSections()).extracting(DraftSection::getTitle).containsExactly("Vorbereitung", "Abschluss");
            assertThat(draft.getSections()).extracting(DraftSection::getReviewStatus)
                    .containsOnly(DraftReviewStatus.PENDING);
            assertThat(draft.getElements()).hasSize(5).allSatisfy(element -> {
                assertThat(element.getId()).isNotNull();
                assertThat(element.getDraftPlan().getId()).isEqualTo(draft.getId());
                assertThat(element.getDraftSection()).isNotNull();
                assertThat(element.getReviewStatus()).isEqualTo(DraftReviewStatus.PENDING);
                assertThat(element.isUserModified()).isFalse();
            });
            List<DraftTask> tasks = draft.getElements().stream().filter(DraftTask.class::isInstance)
                    .map(DraftTask.class::cast).toList();
            DraftTask first = tasks.stream().filter(t -> t.getTitle().equals("Erste Aufgabe")).findFirst().orElseThrow();
            DraftTask second = tasks.stream().filter(t -> t.getTitle().equals("Zweite Aufgabe")).findFirst().orElseThrow();
            DraftTask last = tasks.stream().filter(t -> t.getTitle().equals("Letzte Aufgabe")).findFirst().orElseThrow();
            assertThat(first.getPrerequisites()).isEmpty();
            assertThat(second.getPrerequisites()).extracting(DraftTask::getId).containsExactly(first.getId());
            assertThat(last.getPrerequisites()).extracting(DraftTask::getId).containsExactly(second.getId());
            assertThat(first.getPriority()).isEqualTo(TaskPriority.MEDIUM);
            assertThat(last.getPriority()).isEqualTo(TaskPriority.HIGH);
            assertThat(first.getCriticalAssumption()).isEqualTo("Material verfügbar.\nUnverändert übernehmen.");
            assertThat(first.isHasCriticalAssumption()).isTrue();
            assertThat(first.getAiOrigin()).isEqualTo(GeneratedElementOrigin.USER_INPUT);
            assertThat(last.getAiOrigin()).isEqualTo(GeneratedElementOrigin.AI_INFERRED);
            assertThat(first.getEstimatedHours()).isEqualTo(4);
            assertThat(first.getDescription()).isEqualTo("Beschreibung");
            assertThat(first.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(first.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 10));
            assertThat(first.getDraftSection().getElements().stream().filter(DraftTask.class::isInstance))
                    .extracting(DraftPlanElement::getTitle).containsExactly("Erste Aufgabe", "Zweite Aufgabe");
            assertThat(last.getDraftSection().getTitle()).isEqualTo("Abschluss");
            assertThat(draft.getElements().stream().filter(DraftMilestone.class::isInstance)
                    .map(DraftMilestone.class::cast).filter(m -> m.getTitle().equals("Fertig")))
                    .singleElement().satisfies(m -> {
                assertThat(m.getTitle()).isEqualTo("Fertig");
                assertThat(m.getSortOrder()).isEqualTo(3);
                assertThat(m.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 20));
                assertThat(m.getDraftSection().getTitle()).isEqualTo("Abschluss");
            });
            assertThat(draft.getElements()).noneSatisfy(e ->
                    assertThat(e.getCriticalAssumption()).contains("Nur im Pre-Check"));
        });
    }

    private void assertNoDraft(Fixture f) {
        assertThat(drafts.findByProjectId(f.projectId())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from draft_sections s left join plan_drafts d "
                + "on d.id = s.plan_draft_id where d.id is null", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from draft_plan_elements e left join plan_drafts d "
                + "on d.id = e.plan_draft_id where d.id is null", Integer.class)).isZero();
    }

    private void assertNoActivePlan(Fixture f) {
        assertThat(jdbc.queryForObject("select count(*) from plan_sections where plan_container_id = ?",
                Integer.class, f.projectId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from plan_elements where plan_container_id = ?",
                Integer.class, f.projectId())).isZero();
    }

    private void readDraft(Fixture f, Consumer<DraftPlan> assertion) {
        inTransaction(() -> assertion.accept(drafts.findByProjectId(f.projectId()).orElseThrow()));
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private GeneratedPlanResponse generatedPlan() {
        return new GeneratedPlanResponse(List.of(
                new GeneratedSection("last", "Abschluss", null, 2,
                        List.of(task("last", "Letzte Aufgabe", 2, TaskPriority.HIGH, List.of("second"))),
                        List.of(new GeneratedMilestone("done", "Fertig", LocalDate.of(2026, 9, 20), 3))),
                new GeneratedSection("first", "Vorbereitung", "Bereichsbeschreibung", 1,
                        List.of(task("second", "Zweite Aufgabe", 2, null, List.of("first")),
                                new GeneratedTask("first", "Erste Aufgabe", "Beschreibung", 4,
                                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10),
                                        "Material verfügbar.\nUnverändert übernehmen.", GeneratedElementOrigin.USER_INPUT, 1)),
                        List.of(new GeneratedMilestone("ready", "Bereit", null, 3)))));
    }

    private GeneratedTask task(String key, String title, int order, TaskPriority priority, List<String> prerequisites) {
        return new GeneratedTask(key, title, null, null, null, null, null,
                GeneratedElementOrigin.AI_INFERRED, order, prerequisites, priority);
    }

    private record Fixture(UUID workflowId, UUID projectId) { }
}
