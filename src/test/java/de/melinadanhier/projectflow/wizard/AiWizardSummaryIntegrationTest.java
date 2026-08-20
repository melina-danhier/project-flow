package de.melinadanhier.projectflow.wizard;

import de.melinadanhier.projectflow.generation.repository.PlanDraftRepository;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import de.melinadanhier.projectflow.wizard.dto.ProjectTimeFrameType;
import de.melinadanhier.projectflow.wizard.model.ProjectWizardState;
import de.melinadanhier.projectflow.wizard.service.ProjectWizardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
class AiWizardSummaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private PlanDraftRepository planDraftRepository;

    @Test
    void rendersSeparatedGeneralAndAiOnlyDataWithCalculatedAbsoluteDates() throws Exception {
        WizardRequest request = wizardRequest(true);

        mockMvc.perform(get("/projects/new/ai/summary")
                        .session(request.session()).with(user(request.user())))
                .andExpect(status().isOk())
                .andExpect(view().name("generation/ai-summary"))
                .andExpect(model().attributeExists("summary", "aiProcessingConsentForm"))
                .andExpect(content().string(containsString("Allgemeine Projektdaten")))
                .andExpect(content().string(containsString("Umzug planen")))
                .andExpect(content().string(containsString("Wohnungswechsel organisieren")))
                .andExpect(content().string(containsString("01.09.2026 bis 21.09.2026")))
                .andExpect(content().string(containsString("Haushalt und Wohnen – Umzug")))
                .andExpect(content().string(containsString("Zusätzliche Angaben für die KI")))
                .andExpect(content().string(containsString("nur für die KI-Verarbeitung bestimmt")))
                .andExpect(content().string(containsString("Bis zum Monatsende umziehen")))
                .andExpect(content().string(containsString("Budget 2.000 Euro")))
                .andExpect(content().string(containsString("Mitgliederdaten werden nicht übermittelt")))
                .andExpect(content().string(containsString("überprüfbarer Entwurf")))
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
                .andExpect(content().string(containsString("Keine zusätzlichen Angaben gemacht.")))
                .andExpect(content().string(not(containsString("<dt>Beschreibung</dt>"))))
                .andExpect(content().string(not(containsString("<dt>Projektziel</dt>"))))
                .andExpect(content().string(not(containsString("<dt>Rahmenbedingungen</dt>"))))
                .andExpect(content().string(not(containsString("<dt>Weitere Hinweise</dt>"))));
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
                .andExpect(content().string(containsString("href=\"/projects/new/ai/details\">Zurück")))
                .andExpect(content().string(containsString("href=\"/projects/new\">Allgemeine Projektdaten bearbeiten")));
    }

    @Test
    void rejectsMissingConsentOnTheServerAndDoesNotPersistAnything() throws Exception {
        WizardRequest request = wizardRequest(true);
        long projectsBefore = projectRepository.count();
        long draftsBefore = planDraftRepository.count();

        mockMvc.perform(post("/projects/new/ai/confirm")
                        .session(request.session()).with(user(request.user())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("generation/ai-summary"))
                .andExpect(model().attributeHasFieldErrors("aiProcessingConsentForm", "consent"))
                .andExpect(content().string(containsString("Bitte stimme der beschriebenen KI-Verarbeitung zu")));

        assertThat(request.state().isAiProcessingConfirmed()).isFalse();
        assertThat(projectRepository.count()).isEqualTo(projectsBefore);
        assertThat(planDraftRepository.count()).isEqualTo(draftsBefore);
    }

    @Test
    void acceptsExplicitConsentOnceInTheUiWithoutCreatingAProjectOrDraft() throws Exception {
        WizardRequest request = wizardRequest(true);
        long projectsBefore = projectRepository.count();
        long draftsBefore = planDraftRepository.count();

        mockMvc.perform(post("/projects/new/ai/confirm")
                        .session(request.session()).with(user(request.user())).with(csrf())
                        .param("consent", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/new/ai/summary?confirmed"));

        assertThat(request.state().isAiProcessingConfirmed()).isTrue();
        mockMvc.perform(get("/projects/new/ai/summary?confirmed")
                        .session(request.session()).with(user(request.user())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Deine Zustimmung wurde geprüft")))
                .andExpect(content().string(not(containsString("id=\"ai-confirmation-form\""))));
        assertThat(projectRepository.count()).isEqualTo(projectsBefore);
        assertThat(planDraftRepository.count()).isEqualTo(draftsBefore);
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

    private WizardRequest wizardRequest(boolean groupProject) {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "wizard@example.org", "ignored", true);
        ProjectWizardState state = new ProjectWizardState();
        state.setUserId(userId);
        state.setTitle("Umzug planen");
        state.setDescription("Wohnungswechsel organisieren");
        state.setCategory(TemplateCategory.HOME);
        state.setProjectType("Umzug");
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

    private record WizardRequest(
            AuthenticatedUser user,
            MockHttpSession session,
            ProjectWizardState state
    ) { }
}
