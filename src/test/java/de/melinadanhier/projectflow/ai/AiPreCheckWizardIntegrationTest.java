package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectSubCategory;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblemType;
import de.melinadanhier.projectflow.ai.provider.AiClient;
import de.melinadanhier.projectflow.draft.model.DraftPlanStatus;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.model.workflow.AiWorkflowCompletion;
import de.melinadanhier.projectflow.wizard.service.AiWizardCompletionService;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.draft.repository.DraftRepository;
import de.melinadanhier.projectflow.ai.model.generation.*;
import de.melinadanhier.projectflow.generation.service.retry.AiRetryBackoff;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import de.melinadanhier.projectflow.planelement.repository.TaskRepository;
import de.melinadanhier.projectflow.planelement.repository.MilestoneRepository;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import de.melinadanhier.projectflow.wizard.model.ProjectWizardState;
import de.melinadanhier.projectflow.wizard.service.ProjectWizardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiPreCheckWizardIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AiWizardCompletionService completionService;
    @Autowired private AiPlanGenerationWorkflowRepository workflowRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DraftRepository draftRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlanSectionRepository planSectionRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectRepository projectRepository;

    @MockitoBean private AiClient aiClient;
    @MockitoBean private AiRetryBackoff backoff;

    @BeforeEach
    void setUp() {
        reset(aiClient, backoff);
        when(aiClient.generatePlan(any())).thenReturn(generatedPlan());
    }

    @Test
    void noProblemsWaitsForExplicitGenerationStart() throws Exception {
        User owner = saveUser("precheck-clear@example.org");
        when(aiClient.preCheck(any())).thenReturn(AiPreCheckResult.withoutIssues());

        UUID workflowId = start(owner);
        awaitStatus(workflowId, AiPlanGenerationWorkflowStatus.PRE_CHECK_COMPLETED);
        verify(aiClient, never()).generatePlan(any());
        mockMvc.perform(post(statusUrl(workflowId) + "/generate")
                        .with(user(principal(owner))).with(csrf()))
                .andExpect(status().is3xxRedirection());
        awaitStatus(workflowId, AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED);

        verify(aiClient).preCheck(any());
        verify(aiClient).generatePlan(any());
        assertThat(workflowRepository.findById(workflowId).orElseThrow().getGeneratedPlan())
                .contains("sections").doesNotContain("criticalAssumptions");
        UUID projectId = workflowRepository.findById(workflowId).orElseThrow().getProject().getId();
        mockMvc.perform(get(statusUrl(workflowId)).with(user(new AuthenticatedUser(
                        owner.getId(), owner.getEmail(), owner.getPasswordHash(), true))))
                .andExpect(redirectedUrl("/projects/" + projectId + "/draft/review"));
    }

    @Test
    void warningsAreShownAndGenerationStartsOnlyAfterExplicitPost() throws Exception {
        User owner = saveUser("precheck-warnings@example.org");
        long activeSectionsBefore = planSectionRepository.count();
        long activeTasksBefore = taskRepository.count();
        long activeMilestonesBefore = milestoneRepository.count();
        when(aiClient.preCheck(any())).thenReturn(result(
                warning("Warnung eins"), assumption("Annahme zwei")));
        UUID workflowId = start(owner);
        awaitStatus(workflowId, AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW);
        MockHttpSession session = new MockHttpSession();
        AuthenticatedUser principal = principal(owner);

        mockMvc.perform(get(statusUrl(workflowId)).session(session).with(user(principal)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(problemsUrl(workflowId)));
        mockMvc.perform(get(problemsUrl(workflowId)).session(session).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("generation/ai-problems"))
                .andExpect(content().string(containsString("Warnung eins")))
                .andExpect(content().string(containsString("Annahme zwei")))
                .andExpect(content().string(containsString("action=\"" + acceptUrl(workflowId, 0) + "\"")))
                .andExpect(content().string(containsString("action=\"" + acceptUrl(workflowId, 1) + "\"")))
                .andExpect(content().string(containsString("action=\"" + confirmUrl(workflowId, 1) + "\"")))
                .andExpect(content().string(containsString("Welche Planungsgrundlage soll für Annahme zwei gelten?")));
        mockMvc.perform(post(acceptUrl(workflowId, 0)).session(session).with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(problemsUrl(workflowId)));
        assertThat(workflowRepository.findById(workflowId).orElseThrow()
                .getAcceptedOpenPointIndices()).containsExactly(0);
        mockMvc.perform(post(acceptUrl(workflowId, 0)).session(new MockHttpSession())
                        .with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(problemsUrl(workflowId)));
        assertThat(workflowRepository.findById(workflowId).orElseThrow()
                .getAcceptedOpenPointIndices()).containsExactly(0);
        mockMvc.perform(get(problemsUrl(workflowId)).session(session).with(user(principal)))
                .andExpect(content().string(not(containsString("Warnung eins"))))
                .andExpect(content().string(containsString("Annahme zwei")));
        verify(aiClient, never()).generatePlan(any());

        String invalidContext = "Zu ausführliche Planungsgrundlage ".repeat(40);
        mockMvc.perform(post(confirmUrl(workflowId, 1)).session(session).with(user(principal)).with(csrf())
                        .param("planningContext", invalidContext))
                .andExpect(status().isOk())
                .andExpect(view().name("generation/ai-problems"))
                .andExpect(model().attributeHasFieldErrors(
                        "openPointConfirmationForm", "planningContext"))
                .andExpect(content().string(containsString(
                        org.springframework.web.util.HtmlUtils.htmlEscape(invalidContext))))
                .andExpect(content().string(containsString(
                        "Die Planungsgrundlage darf höchstens 1000 Zeichen lang sein.")));
        assertThat(workflowRepository.findById(workflowId).orElseThrow()
                .getAcceptedOpenPointIndices()).containsExactly(0);
        assertThat(workflowRepository.findById(workflowId).orElseThrow()
                .getCustomOpenPointInterpretations()).doesNotContainKey(1);

        CountDownLatch generationStarted = new CountDownLatch(1);
        CountDownLatch releaseGeneration = new CountDownLatch(1);
        when(aiClient.generatePlan(any())).thenAnswer(invocation -> {
            generationStarted.countDown();
            if (!releaseGeneration.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Generierung wurde im Test nicht freigegeben.");
            }
            return generatedPlan();
        });
        long startedAt = System.nanoTime();
        mockMvc.perform(post(confirmUrl(workflowId, 1)).session(session).with(user(principal)).with(csrf())
                        .param("planningContext", "Die Umsetzung erfolgt ohne die vermutete Voraussetzung."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(statusUrl(workflowId)));
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)).isLessThan(1000);
        assertThat(workflowRepository.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(AiPlanGenerationWorkflowStatus.PRE_CHECK_COMPLETED);
        mockMvc.perform(post(statusUrl(workflowId) + "/generate")
                        .session(session).with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(generationStarted.await(2, TimeUnit.SECONDS)).isTrue();
        releaseGeneration.countDown();
        awaitStatus(workflowId, AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED);
        ArgumentCaptor<de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest> requestCaptor =
                ArgumentCaptor.forClass(de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest.class);
        verify(aiClient).generatePlan(requestCaptor.capture());
        assertThat(requestCaptor.getValue().acceptedOpenPoints())
                .extracting(AiPreCheckProblem::acceptedInterpretation)
                .containsExactly("Verbindliche Auslegung: Warnung eins",
                        "Die Umsetzung erfolgt ohne die vermutete Voraussetzung.");
        assertThat(workflowRepository.findById(workflowId).orElseThrow()).satisfies(workflow -> {
            assertThat(workflow.getAcceptedOpenPointIndices()).containsExactlyInAnyOrder(0, 1);
            assertThat(workflow.getCustomOpenPointInterpretations())
                    .containsEntry(1, "Die Umsetzung erfolgt ohne die vermutete Voraussetzung.")
                    .doesNotContainKey(0);
            assertThat(workflow.getGenerationRoundAttemptCount()).isEqualTo(1);
            assertThat(workflow.getGenerationTotalAttemptCount()).isEqualTo(1);
        });
        UUID projectId = workflowRepository.findById(workflowId).orElseThrow().getProject().getId();
        var draft = draftRepository.findByProjectId(projectId).orElseThrow();
        assertThat(draft.getStatus()).isEqualTo(DraftPlanStatus.READY_FOR_REVIEW);
        assertThat(jdbcTemplate.queryForList(
                "select title from draft_sections where plan_draft_id = ? order by sort_order",
                String.class, draft.getId())).containsExactly("Section");
        assertThat(jdbcTemplate.queryForList(
                "select title from draft_plan_elements where plan_draft_id = ? order by sort_order",
                String.class, draft.getId()))
                .containsExactlyInAnyOrder("Generierter Schritt", "Zweiter Schritt", "Dritter Schritt",
                        "Bereichsziel");
        assertThat(planSectionRepository.count()).isEqualTo(activeSectionsBefore);
        assertThat(taskRepository.count()).isEqualTo(activeTasksBefore);
        assertThat(milestoneRepository.count()).isEqualTo(activeMilestonesBefore);

        mockMvc.perform(get("/projects/" + projectId + "/draft").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Generierter Schritt")))
                .andExpect(content().string(containsString("Bereichsziel")));
        mockMvc.perform(post("/projects/" + projectId + "/draft/apply").with(user(principal)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("generation/draft-pending-confirmation"));
        mockMvc.perform(post("/projects/" + projectId + "/draft/continue-with-pending")
                        .param("draftId", draft.getId().toString())
                        .param("lockVersion", String.valueOf(draft.getLockVersion()))
                        .with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + projectId + "/plan"));

        assertThat(planSectionRepository.count()).isEqualTo(activeSectionsBefore + 1);
        assertThat(taskRepository.count()).isEqualTo(activeTasksBefore + 3);
        assertThat(milestoneRepository.count()).isEqualTo(activeMilestonesBefore + 1);
        assertThat(projectRepository.findById(projectId)).get()
                .extracting("location").isEqualTo(ProjectLocation.OVERVIEW);
        assertThat(draftRepository.findById(draft.getId())).get()
                .extracting("status").isEqualTo(DraftPlanStatus.APPLIED);
        assertThat(workflowRepository.findById(workflowId)).get()
                .extracting("status").isEqualTo(AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED);

        mockMvc.perform(post("/projects/" + projectId + "/draft/apply").with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(taskRepository.count()).isEqualTo(activeTasksBefore + 3);
    }

    @Test
    void errorsHideOpenPointsAndCannotBeAccepted() throws Exception {
        User owner = saveUser("precheck-error@example.org");
        when(aiClient.preCheck(any())).thenReturn(result(
                warning("Knapper Zeitraum"),
                error("Ziel und Rahmen widersprechen sich")));
        UUID workflowId = start(owner);
        awaitStatus(workflowId, AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW);
        MockHttpSession session = new MockHttpSession();
        AuthenticatedUser principal = principal(owner);

        mockMvc.perform(get(problemsUrl(workflowId)).session(session).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Knapper Zeitraum"))))
                .andExpect(content().string(containsString("Ziel und Rahmen widersprechen sich")))
                .andExpect(content().string(not(containsString("/open-points/"))));
        mockMvc.perform(post(acceptUrl(workflowId, 1)).session(session).with(user(principal)).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(workflowRepository.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW);
        assertThat(workflowRepository.findById(workflowId).orElseThrow()
                .getAcceptedOpenPointIndices()).isEmpty();
        verify(aiClient, never()).generatePlan(any());
    }

    @Test
    void editingReturnsToSummaryAndASecondConfirmationRunsANewPreCheck() throws Exception {
        User owner = saveUser("precheck-edit@example.org");
        when(aiClient.preCheck(any()))
                .thenReturn(result(warning("Bitte Eingaben prüfen")))
                .thenReturn(AiPreCheckResult.withoutIssues());
        UUID oldWorkflowId = start(owner);
        awaitStatus(oldWorkflowId, AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW);
        UUID originalCompletionToken = workflowRepository.findById(oldWorkflowId)
                .orElseThrow().getCompletionToken();
        MockHttpSession session = new MockHttpSession();
        AuthenticatedUser principal = principal(owner);

        mockMvc.perform(post(problemsUrl(oldWorkflowId) + "/edit")
                        .session(session).with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/new/ai/summary"));
        assertThat(workflowRepository.findById(oldWorkflowId)).isPresent();
        assertThat(session.getAttribute(ProjectWizardService.SESSION_ATTRIBUTE))
                .isInstanceOf(ProjectWizardState.class);

        mockMvc.perform(get("/projects/new/ai/summary").session(session).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Umzug planen")))
                .andExpect(content().string(not(containsString("Bitte Eingaben prüfen"))));
        ProjectWizardState restored = (ProjectWizardState) session.getAttribute(
                ProjectWizardService.SESSION_ATTRIBUTE);
        assertThat(restored.getCompletionToken()).isNotNull();

        String redirect = mockMvc.perform(post("/projects/new/ai/confirm")
                        .session(session).with(user(principal)).with(csrf())
                        .param("consent", "true")
                        .param("completionToken", restored.getCompletionToken().toString()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        UUID restartedWorkflowId = UUID.fromString(redirect.substring(redirect.lastIndexOf('/') + 1));
        assertThat(restartedWorkflowId).isNotEqualTo(oldWorkflowId);
        awaitStatus(restartedWorkflowId, AiPlanGenerationWorkflowStatus.PRE_CHECK_COMPLETED);
        assertThat(workflowRepository.findById(oldWorkflowId)).get()
                .extracting("status")
                .isEqualTo(AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW);
        assertThat(completionService.complete(
                originalCompletionToken,
                owner.getId(),
                () -> { throw new IllegalStateException("Verspäteter Request darf keinen Snapshot benötigen"); }))
                .extracting(AiWorkflowCompletion::workflowId)
                .isEqualTo(oldWorkflowId);
        verify(aiClient, times(2)).preCheck(any());
    }

    @Test
    void foreignWorkflowCannotBeViewedOrChanged() throws Exception {
        User owner = saveUser("precheck-owner@example.org");
        User outsider = saveUser("precheck-outsider@example.org");
        when(aiClient.preCheck(any())).thenReturn(result(warning("Nur für Besitzer")));
        UUID workflowId = start(owner);
        awaitStatus(workflowId, AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW);
        AuthenticatedUser outsiderPrincipal = principal(outsider);

        mockMvc.perform(get(problemsUrl(workflowId)).with(user(outsiderPrincipal)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(acceptUrl(workflowId, 0)).with(user(outsiderPrincipal)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(problemsUrl(workflowId) + "/edit").with(user(outsiderPrincipal)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(statusUrl(workflowId) + "/retry")
                        .with(user(outsiderPrincipal)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    private UUID start(User owner) {
        return completionService.complete(UUID.randomUUID(), owner.getId(), this::snapshot).workflowId();
    }

    private void awaitStatus(UUID workflowId, AiPlanGenerationWorkflowStatus expected) throws Exception {
        await(() -> workflowRepository.findById(workflowId)
                .map(workflow -> workflow.getStatus() == expected)
                .orElse(false));
    }

    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private AiPreCheckResult result(AiPreCheckProblem... problems) {
        return new AiPreCheckResult(List.of(problems));
    }

    private AiPreCheckProblem warning(String message) {
        return new AiPreCheckProblem(AiPreCheckSeverity.WARNING, AiPreCheckProblemType.RISK,
                message, "Passe die Planung bei Bedarf an.",
                "Welche Planungsgrundlage soll für " + message + " gelten?",
                "Verbindliche Auslegung: " + message);
    }

    private AiPreCheckProblem assumption(String message) {
        return new AiPreCheckProblem(AiPreCheckSeverity.WARNING, AiPreCheckProblemType.ASSUMPTION,
                message, "Ergänze die Angabe bei Bedarf.",
                "Welche Planungsgrundlage soll für " + message + " gelten?",
                "Verbindliche Auslegung: " + message);
    }

    private AiPreCheckProblem error(String message) {
        return new AiPreCheckProblem(AiPreCheckSeverity.ERROR, message, "Ändere Ziel oder Rahmenbedingungen.");
    }

    private GeneratedPlanResponse generatedPlan() {
        return new GeneratedPlanResponse(
                List.of(new GeneratedSection(
                        "section-1", "Section", null, 1,
                        List.of(
                                generatedTask("task-1", "Generierter Schritt", 1),
                                generatedTask("task-2", "Zweiter Schritt", 2),
                                generatedTask("task-3", "Dritter Schritt", 3)),
                        List.of(new GeneratedMilestone(
                                "milestone-1", "Bereichsziel", LocalDate.of(2026, 9, 21), 2)))));
    }

    private GeneratedTask generatedTask(String id, String title, int order) {
        return new GeneratedTask(id, title, null, 1,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20),
                GeneratedElementOrigin.AI_INFERRED, order);
    }

    private AiWizardSnapshot snapshot() {
        return new AiWizardSnapshot(
                "Umzug planen", "Wohnungswechsel organisieren",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 21),
                CollaborationMode.INDIVIDUAL, ProjectCategory.HOME, ProjectSubCategory.MOVING, null,
                "Rechtzeitig umziehen", "Budget 2.000 Euro", "Kartons vorhanden",
                21, "Etwa 8 Stunden pro Woche");
    }

    private User saveUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("Pre-Check Test");
        user.setPasswordHash("$2a$12$test-hash");
        user.setEnabled(true);
        return userRepository.saveAndFlush(user);
    }

    private AuthenticatedUser principal(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getPasswordHash(), true);
    }

    private String statusUrl(UUID workflowId) {
        return "/projects/new/ai/status/" + workflowId;
    }

    private String problemsUrl(UUID workflowId) {
        return "/projects/new/ai/problems/" + workflowId;
    }

    private String acceptUrl(UUID workflowId, int index) {
        return problemsUrl(workflowId) + "/open-points/" + index + "/accept";
    }

    private String confirmUrl(UUID workflowId, int index) {
        return problemsUrl(workflowId) + "/open-points/" + index + "/confirm";
    }
}
