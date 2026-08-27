package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.draft.model.DraftPlanStatus;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.model.workflow.AiWorkflowCompletion;
import de.melinadanhier.projectflow.generation.model.wizard.AiProjectTimeFrameType;
import de.melinadanhier.projectflow.wizard.service.AiWizardCompletionService;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.draft.repository.PlanDraftRepository;
import de.melinadanhier.projectflow.ai.model.generation.*;
import de.melinadanhier.projectflow.generation.service.retry.AiRetryBackoff;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import de.melinadanhier.projectflow.planelement.repository.TaskRepository;
import de.melinadanhier.projectflow.planelement.repository.MilestoneRepository;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import de.melinadanhier.projectflow.wizard.model.ProjectWizardState;
import de.melinadanhier.projectflow.wizard.service.ProjectWizardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    @Autowired private PlanDraftRepository planDraftRepository;
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
    void noProblemsStartsGenerationImmediately() throws Exception {
        User owner = saveUser("precheck-clear@example.org");
        when(aiClient.preCheck(any())).thenReturn(AiPreCheckResult.withoutIssues());

        UUID workflowId = start(owner);
        awaitStatus(workflowId, AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED);

        verify(aiClient).preCheck(any());
        verify(aiClient).generatePlan(any());
        assertThat(workflowRepository.findById(workflowId).orElseThrow().getGeneratedPlan())
                .contains("Generierter Schritt");
    }

    @Test
    void warningsAreShownAndIgnoredIndividuallyBeforeAutomaticGeneration() throws Exception {
        User owner = saveUser("precheck-warnings@example.org");
        long activeSectionsBefore = planSectionRepository.count();
        long activeTasksBefore = taskRepository.count();
        long activeMilestonesBefore = milestoneRepository.count();
        when(aiClient.preCheck(any())).thenReturn(result(warning("Warnung eins"), warning("Warnung zwei")));
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
                .andExpect(content().string(containsString("Warnung zwei")))
                .andExpect(content().string(containsString("Vorschlag")))
                .andExpect(content().string(containsString(">Hinweis akzeptieren</button>")));
        mockMvc.perform(post(ignoreUrl(workflowId, 0)).session(session).with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(problemsUrl(workflowId)));
        assertThat(workflowRepository.findById(workflowId).orElseThrow()
                .getAcknowledgedWarningIndices()).containsExactly(0);
        mockMvc.perform(post(ignoreUrl(workflowId, 0)).session(new MockHttpSession())
                        .with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(problemsUrl(workflowId)));
        assertThat(workflowRepository.findById(workflowId).orElseThrow()
                .getAcknowledgedWarningIndices()).containsExactly(0);
        mockMvc.perform(get(problemsUrl(workflowId)).session(session).with(user(principal)))
                .andExpect(content().string(not(containsString("Warnung eins"))))
                .andExpect(content().string(containsString("Warnung zwei")));
        verify(aiClient, never()).generatePlan(any());

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
        mockMvc.perform(post(ignoreUrl(workflowId, 1)).session(session).with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(statusUrl(workflowId)));
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)).isLessThan(1000);
        assertThat(generationStarted.await(2, TimeUnit.SECONDS)).isTrue();
        releaseGeneration.countDown();
        awaitStatus(workflowId, AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED);
        verify(aiClient).generatePlan(any());
        assertThat(workflowRepository.findById(workflowId).orElseThrow()).satisfies(workflow -> {
            assertThat(workflow.getAcknowledgedWarningIndices()).containsExactlyInAnyOrder(0, 1);
            assertThat(workflow.getGenerationRoundAttemptCount()).isEqualTo(1);
            assertThat(workflow.getGenerationTotalAttemptCount()).isEqualTo(1);
        });
        UUID projectId = workflowRepository.findById(workflowId).orElseThrow().getProject().getId();
        var draft = planDraftRepository.findByProjectId(projectId).orElseThrow();
        assertThat(draft.getStatus()).isEqualTo(DraftPlanStatus.READY_FOR_REVIEW);
        assertThat(jdbcTemplate.queryForList(
                "select title from draft_sections where plan_draft_id = ? order by sort_order",
                String.class, draft.getId())).containsExactly("Phase");
        assertThat(jdbcTemplate.queryForList(
                "select title from draft_plan_elements where plan_draft_id = ? order by sort_order",
                String.class, draft.getId()))
                .containsExactlyInAnyOrder("Generierter Schritt", "Zweiter Schritt", "Dritter Schritt",
                        "Phasenziel");
        assertThat(planSectionRepository.count()).isEqualTo(activeSectionsBefore);
        assertThat(taskRepository.count()).isEqualTo(activeTasksBefore);
        assertThat(milestoneRepository.count()).isEqualTo(activeMilestonesBefore);

        mockMvc.perform(get("/projects/" + projectId + "/draft").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Generierter Schritt")))
                .andExpect(content().string(containsString("Phasenziel")));
        mockMvc.perform(post("/projects/" + projectId + "/draft/apply").with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + projectId + "/plan"));

        assertThat(planSectionRepository.count()).isEqualTo(activeSectionsBefore + 1);
        assertThat(taskRepository.count()).isEqualTo(activeTasksBefore + 3);
        assertThat(milestoneRepository.count()).isEqualTo(activeMilestonesBefore + 1);
        assertThat(projectRepository.findById(projectId)).get()
                .extracting("status").isEqualTo(ProjectStatus.ACTIVE);
        assertThat(planDraftRepository.findById(draft.getId())).get()
                .extracting("status").isEqualTo(DraftPlanStatus.APPLIED);
        assertThat(workflowRepository.findById(workflowId)).get()
                .extracting("status").isEqualTo(AiPlanGenerationWorkflowStatus.DRAFT_APPLIED);

        mockMvc.perform(post("/projects/" + projectId + "/draft/apply").with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(taskRepository.count()).isEqualTo(activeTasksBefore + 3);
    }

    @Test
    void errorsBlockWhileWarningsRemainIgnorableOnTheSamePage() throws Exception {
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
                .andExpect(content().string(containsString("Knapper Zeitraum")))
                .andExpect(content().string(containsString("Ziel und Rahmen widersprechen sich")))
                .andExpect(content().string(containsString("Dieser Fehler kann nicht akzeptiert werden")))
                .andExpect(content().string(containsString(">Hinweis akzeptieren</button>")));
        mockMvc.perform(post(ignoreUrl(workflowId, 1)).session(session).with(user(principal)).with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(ignoreUrl(workflowId, 0)).session(session).with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(problemsUrl(workflowId)));
        mockMvc.perform(get(problemsUrl(workflowId)).session(session).with(user(principal)))
                .andExpect(content().string(not(containsString("Knapper Zeitraum"))))
                .andExpect(content().string(containsString("Ziel und Rahmen widersprechen sich")))
                .andExpect(content().string(not(containsString(">Hinweis akzeptieren</button>"))));

        assertThat(workflowRepository.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW);
        assertThat(workflowRepository.findById(workflowId).orElseThrow()
                .getAcknowledgedWarningIndices()).containsExactly(0);
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
        awaitStatus(restartedWorkflowId, AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED);
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
        mockMvc.perform(post(ignoreUrl(workflowId, 0)).with(user(outsiderPrincipal)).with(csrf()))
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
        return new AiPreCheckProblem(AiPreCheckSeverity.WARNING, message, "Passe die Planung bei Bedarf an.");
    }

    private AiPreCheckProblem error(String message) {
        return new AiPreCheckProblem(AiPreCheckSeverity.ERROR, message, "Ändere Ziel oder Rahmenbedingungen.");
    }

    private GeneratedPlanResponse generatedPlan() {
        return new GeneratedPlanResponse(
                List.of(new GeneratedPhase(
                        "phase-1", "Phase", null,
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 21), 1,
                        List.of(
                                generatedTask("task-1", "Generierter Schritt", 1),
                                generatedTask("task-2", "Zweiter Schritt", 2),
                                generatedTask("task-3", "Dritter Schritt", 3)),
                        List.of(new GeneratedMilestone(
                                "milestone-1", "Phasenziel", LocalDate.of(2026, 9, 21), 2)))));
    }

    private GeneratedTask generatedTask(String id, String title, int order) {
        return new GeneratedTask(id, title, null, 1,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20),
                null, GeneratedElementOrigin.AI_INFERRED, order);
    }

    private AiWizardSnapshot snapshot() {
        return new AiWizardSnapshot(
                "Umzug planen", "Wohnungswechsel organisieren",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 21),
                CollaborationMode.INDIVIDUAL, TemplateCategory.HOME, "Umzug",
                "Rechtzeitig umziehen", "Budget 2.000 Euro", "Kartons vorhanden",
                AiProjectTimeFrameType.START_AND_DURATION, 21);
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

    private String ignoreUrl(UUID workflowId, int index) {
        return problemsUrl(workflowId) + "/warnings/" + index + "/acknowledge";
    }
}
