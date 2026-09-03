package de.melinadanhier.projectflow.wizard;

import de.melinadanhier.projectflow.plancontainer.project.model.ProjectSubCategory;
import de.melinadanhier.projectflow.ai.provider.AiClient;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.draft.repository.DraftRepository;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import de.melinadanhier.projectflow.wizard.dto.ProjectTimeFrameType;
import de.melinadanhier.projectflow.wizard.model.ProjectWizardState;
import de.melinadanhier.projectflow.wizard.service.ProjectWizardService;
import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiWizardSummaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private DraftRepository draftRepository;

    @Autowired
    private AiPlanGenerationWorkflowRepository workflowRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private AiClient aiClient;

    @BeforeEach
    void configureAiClient() {
        when(aiClient.preCheck(any())).thenReturn(AiPreCheckResult.withoutIssues());
    }

    @Test
    void rendersSeparatedGeneralAndAiOnlyDataWithCalculatedAbsoluteDates() throws Exception {
        WizardRequest request = wizardRequest(true);

        mockMvc.perform(get("/projects/new/ai/summary")
                        .session(request.session()).with(user(request.user())))
                .andExpect(status().isOk())
                .andExpect(view().name("generation/ai-summary"))
                .andExpect(model().attributeExists("summary", "aiProcessingConsentForm"))
                .andExpect(content().string(containsString("aria-labelledby=\"general-project-data\"")))
                .andExpect(content().string(containsString("Umzug planen")))
                .andExpect(content().string(containsString("Wohnungswechsel organisieren")))
                .andExpect(content().string(containsString("01.09.2026 bis 21.09.2026")))
                .andExpect(content().string(containsString("Haushalt und Wohnen – Umzug")))
                .andExpect(content().string(containsString("aria-labelledby=\"ai-only-data\"")))
                .andExpect(content().string(containsString("Bis zum Monatsende umziehen")))
                .andExpect(content().string(containsString("Budget 2.000 Euro")))
                .andExpect(content().string(containsString("aria-labelledby=\"ai-processing-information\"")))
                .andExpect(content().string(not(containsString("21 Tage"))));
    }

    @Test
    void omitsEmptyOptionalRowsAndFormatsAFixedDateAsOneAbsoluteDate() throws Exception {
        WizardRequest request = wizardRequest(false);
        ProjectWizardState state = request.state();
        state.setDescription(null);
        state.setStartDate(LocalDate.of(2026, 10, 4));
        state.setEndDate(LocalDate.of(2026, 10, 4));
        state.setProjectGoal(null);
        state.setConstraints(null);
        state.setAdditionalInformation(null);

        mockMvc.perform(get("/projects/new/ai/summary")
                        .session(request.session()).with(user(request.user())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("04.10.2026")))
                .andExpect(content().string(matchesPattern(
                        "(?s).*<section[^>]*aria-labelledby=\"ai-only-data\"[^>]*>(?:(?!</section>).)*<dl>\\s*</dl>.*")))
                .andExpect(content().string(not(matchesPattern("(?s).*<dd>\\s*</dd>.*"))));
    }

    @Test
    void editingAiDetailsKeepsAllOtherWizardDataAndPrefillsSavedValues() throws Exception {
        WizardRequest request = wizardRequest(true);

        mockMvc.perform(get("/projects/new/ai/details")
                        .session(request.session()).with(user(request.user())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bis zum Monatsende umziehen")))
                .andExpect(content().string(containsString("Budget 2.000 Euro")));

        mockMvc.perform(post("/projects/new/ai/details")
                        .session(request.session()).with(user(request.user())).with(csrf())
                        .param("projectGoal", "  Neuer Zieltext  ")
                        .param("constraints", "  ")
                        .param("additionalInformation", "Helfer sind verfügbar"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/new/ai/summary"));

        assertThat(request.state()).satisfies(state -> {
            assertThat(state.getTitle()).isEqualTo("Umzug planen");
            assertThat(state.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 21));
            assertThat(state.getProjectGoal()).isEqualTo("Neuer Zieltext");
            assertThat(state.getConstraints()).isNull();
            assertThat(state.getAdditionalInformation()).isEqualTo("Helfer sind verfügbar");
        });
        mockMvc.perform(get("/projects/new/ai/summary")
                        .session(request.session()).with(user(request.user())))
                .andExpect(content().string(containsString("href=\"/projects/new/ai/details\"")))
                .andExpect(content().string(containsString("href=\"/projects/new\"")));
    }

    @Test
    void rejectsMissingConsentOnTheServerAndDoesNotPersistAnything() throws Exception {
        WizardRequest request = wizardRequest(true);
        mockMvc.perform(get("/projects/new/ai/summary")
                        .session(request.session()).with(user(request.user())))
                .andExpect(status().isOk());
        long projectsBefore = projectRepository.count();
        long draftsBefore = draftRepository.count();

        mockMvc.perform(post("/projects/new/ai/confirm")
                        .session(request.session()).with(user(request.user())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("generation/ai-summary"))
                .andExpect(model().attributeHasFieldErrors("aiProcessingConsentForm", "consent"))
                .andExpect(content().string(containsString("role=\"alert\"")));

        assertThat(request.session().getAttribute(ProjectWizardService.SESSION_ATTRIBUTE)).isSameAs(request.state());
        assertThat(projectRepository.count()).isEqualTo(projectsBefore);
        assertThat(draftRepository.count()).isEqualTo(draftsBefore);
    }

    @Test
    void summaryProvidesAServerGeneratedCompletionTokenAndClientSideSubmitGuard() throws Exception {
        WizardRequest request = wizardRequest(true);

        mockMvc.perform(get("/projects/new/ai/summary")
                        .session(request.session()).with(user(request.user())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"completionToken\"")))
                .andExpect(content().string(matchesPattern(
                        "(?s).*<button\\b(?=[^>]*\\bid=\"ai-confirmation-submit\")(?=[^>]*\\bdisabled(?:\\s|=|>))[^>]*>.*")));

        assertThat(request.state().getCompletionToken()).isNotNull();
    }

    @Test
    void cancelLeavesTheAiWizardAndRemovesItsTemporaryState() throws Exception {
        WizardRequest request = wizardRequest(true);

        mockMvc.perform(post("/projects/new/cancel")
                        .session(request.session()).with(user(request.user())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects"));

        assertThat(request.session().getAttribute(ProjectWizardService.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    void successfulConsentClearsSessionOnlyAfterPersistenceAndRedirectsToStatus() throws Exception {
        User owner = saveUser("wizard-completion@example.org");
        WizardRequest request = wizardRequest(owner.getId());
        mockMvc.perform(get("/projects/new/ai/summary")
                        .session(request.session()).with(user(request.user())))
                .andExpect(status().isOk());
        UUID completionToken = request.state().getCompletionToken();
        long projectsBefore = projectRepository.count();
        long workflowsBefore = workflowRepository.count();

        String statusUrl = mockMvc.perform(post("/projects/new/ai/confirm")
                        .session(request.session()).with(user(request.user())).with(csrf())
                        .param("consent", "true")
                        .param("completionToken", completionToken.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/projects/new/ai/status/*"))
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(request.session().getAttribute(ProjectWizardService.SESSION_ATTRIBUTE)).isNull();
        assertThat(projectRepository.count()).isEqualTo(projectsBefore + 1);
        assertThat(workflowRepository.count()).isEqualTo(workflowsBefore + 1);
        mockMvc.perform(get(statusUrl).session(request.session()).with(user(request.user())))
                .andExpect(status().isOk())
                .andExpect(view().name("generation/ai-status"))
                .andExpect(model().attributeExists("workflow"));
        AuthenticatedUser outsider = new AuthenticatedUser(
                UUID.randomUUID(), "outsider@example.org", "ignored", true);
        mockMvc.perform(get(statusUrl).with(user(outsider)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/projects/new/ai/confirm")
                        .session(request.session()).with(user(request.user())).with(csrf())
                        .param("consent", "true")
                        .param("completionToken", completionToken.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(statusUrl));
        assertThat(projectRepository.count()).isEqualTo(projectsBefore + 1);
        assertThat(workflowRepository.count()).isEqualTo(workflowsBefore + 1);
    }

    @Test
    void persistenceFailureKeepsTheWizardStateInTheSession() throws Exception {
        WizardRequest request = wizardRequest(true);
        mockMvc.perform(get("/projects/new/ai/summary")
                        .session(request.session()).with(user(request.user())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/projects/new/ai/confirm")
                        .session(request.session()).with(user(request.user())).with(csrf())
                        .param("consent", "true")
                        .param("completionToken", request.state().getCompletionToken().toString()))
                .andExpect(status().isNotFound());

        assertThat(request.session().getAttribute(ProjectWizardService.SESSION_ATTRIBUTE))
                .isSameAs(request.state());
    }

    private WizardRequest wizardRequest(boolean groupProject) {
        return wizardRequest(UUID.randomUUID(), groupProject);
    }

    private WizardRequest wizardRequest(UUID userId) {
        return wizardRequest(userId, true);
    }

    private WizardRequest wizardRequest(UUID userId, boolean groupProject) {
        AuthenticatedUser user = new AuthenticatedUser(userId, "wizard@example.org", "ignored", true);
        ProjectWizardState state = new ProjectWizardState();
        state.setUserId(userId);
        state.setTitle("Umzug planen");
        state.setDescription("Wohnungswechsel organisieren");
        state.setCategory(TemplateCategory.HOME);
        state.setSubcategory(ProjectSubCategory.MOVING);
        state.setCollaborationMode(groupProject ? CollaborationMode.GROUP : CollaborationMode.INDIVIDUAL);
        state.setCreationType(CreationType.AI);
        state.setTimeFrameType(ProjectTimeFrameType.START_AND_DURATION);
        state.setDurationDays(21);
        state.setStartDate(LocalDate.of(2026, 9, 1));
        state.setEndDate(LocalDate.of(2026, 9, 21));
        state.setProjectGoal("Bis zum Monatsende umziehen");
        state.setConstraints("Budget 2.000 Euro");
        state.setAdditionalInformation("Kartons sind vorhanden");
        state.setAiDetailsCompleted(true);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(ProjectWizardService.SESSION_ATTRIBUTE, state);
        return new WizardRequest(user, session, state);
    }

    private User saveUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("Wizard Test");
        user.setPasswordHash("$2a$12$test-hash");
        user.setEnabled(true);
        return userRepository.saveAndFlush(user);
    }

    private record WizardRequest(
            AuthenticatedUser user,
            MockHttpSession session,
            ProjectWizardState state
    ) { }
}
