package de.melinadanhier.projectflow.security;

import de.melinadanhier.projectflow.plancontainer.project.model.ProjectSubCategory;
import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectMemberRepository;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.draft.repository.PlanDraftRepository;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMemberRole;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import de.melinadanhier.projectflow.wizard.dto.ProjectBasicsForm;
import de.melinadanhier.projectflow.wizard.model.ProjectWizardState;
import de.melinadanhier.projectflow.wizard.service.ProjectWizardService;
import de.melinadanhier.projectflow.planelement.dto.MilestoneForm;
import de.melinadanhier.projectflow.planelement.dto.SectionDto;
import de.melinadanhier.projectflow.planelement.dto.SectionForm;
import de.melinadanhier.projectflow.planelement.dto.TaskForm;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import de.melinadanhier.projectflow.planelement.service.MilestoneService;
import de.melinadanhier.projectflow.planelement.service.SectionService;
import de.melinadanhier.projectflow.planelement.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;

import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private PlanDraftRepository planDraftRepository;

    @Autowired
    private SectionService sectionService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private MilestoneService milestoneService;

    @Test
    void anonymousRequestIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/projects"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/projects/new/ai"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/projects/new/ai/details"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginCreatesAuthenticatedSessionAndLogoutInvalidatesIt() throws Exception {
        String email = "session@example.org";
        saveUser(email, "richtiges-passwort", true);

        MvcResult login = mockMvc.perform(post("/login")
                        .param("email", email)
                        .param("password", "richtiges-passwort")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects"))
                .andExpect(authenticated().withUsername(email))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(unauthenticated());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void unknownEmailWrongPasswordAndDisabledUserHaveSameExternalFailure() throws Exception {
        saveUser("wrong-password@example.org", "richtiges-passwort", true);
        saveUser("disabled@example.org", "richtiges-passwort", false);

        assertLoginFailure("unknown@example.org", "irgendein-passwort");
        assertLoginFailure("wrong-password@example.org", "falsches-passwort");
        assertLoginFailure("disabled@example.org", "richtiges-passwort");
    }

    @Test
    void registrationRequiresCsrfAndValidConfirmation() throws Exception {
        mockMvc.perform(post("/register")
                        .param("displayName", "CSRF Test")
                        .param("email", "csrf@example.org")
                        .param("password", "sicheres-passwort")
                        .param("passwordConfirmation", "sicheres-passwort"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/register")
                        .param("displayName", "Validation Test")
                        .param("email", "validation@example.org")
                        .param("password", "sicheres-passwort")
                        .param("passwordConfirmation", "anderes-passwort")
                        .with(csrf()))
                .andExpect(status().isOk());
        assertThat(userRepository.existsByEmail("validation@example.org")).isFalse();
    }

    @Test
    void validRegistrationStoresVerifiableBcryptHash() throws Exception {
        String email = "register@example.org";
        mockMvc.perform(post("/register")
                        .param("displayName", "  Register User  ")
                        .param("email", "  REGISTER@Example.ORG ")
                        .param("password", "sicheres-passwort")
                        .param("passwordConfirmation", "sicheres-passwort")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        User saved = userRepository.findByEmail(email).orElseThrow();
        assertThat(saved.getDisplayName()).isEqualTo("Register User");
        assertThat(saved.getPasswordHash()).isNotEqualTo("sicheres-passwort");
        assertThat(passwordEncoder.matches("sicheres-passwort", saved.getPasswordHash())).isTrue();
    }

    @Test
    void authenticatedProjectFormsUseCsrfAndServerSideValidation() throws Exception {
        String email = "crud-controller@example.org";
        User user = saveUser(email, "richtiges-passwort", true);
        MockHttpSession session = login(email, "richtiges-passwort");

        mockMvc.perform(post("/projects/new")
                        .session(session)
                        .param("title", "Controller-Projekt")
                        .param("category", "HOME")
                        .param("collaborationMode", "INDIVIDUAL")
                        .param("timeFrameType", "NONE"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/projects/new")
                        .session(session)
                        .with(csrf())
                        .param("title", "Controller-Projekt")
                        .param("category", "HOME")
                        .param("collaborationMode", "INDIVIDUAL")
                        .param("timeFrameType", "NONE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/new/method"));
        mockMvc.perform(post("/projects/new/method")
                        .session(session)
                        .with(csrf())
                        .param("creationType", "EMPTY"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/projects/*/plan"));
        assertThat(session.getAttribute(ProjectWizardService.SESSION_ATTRIBUTE)).isNull();
        assertThat(projectRepository.findAllAccessibleByUserId(user.getId()))
                .singleElement().extracting("title").isEqualTo("Controller-Projekt");

        UUID projectId = projectRepository.findAllAccessibleByUserId(user.getId()).getFirst().getId();
        mockMvc.perform(get("/projects/{projectId}/plan", projectId).session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/plan"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model()
                        .attributeDoesNotExist("taskForm", "milestoneForm", "dependencyForm", "memberForm"));
        mockMvc.perform(post("/projects/{projectId}/tasks", projectId)
                        .session(session)
                        .with(csrf())
                        .param("title", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/tasks/form"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model()
                        .attributeHasFieldErrors("taskForm", "title", "priority"));
    }

    @Test
    void aiCreationStoresOnlyOwnedSessionDataAndUsesWizardWithoutProjectId() throws Exception {
        String ownerEmail = "ai-controller-owner@example.org";
        String outsiderEmail = "ai-controller-outsider@example.org";
        saveUser(ownerEmail, "richtiges-passwort", true);
        saveUser(outsiderEmail, "richtiges-passwort", true);
        MockHttpSession ownerSession = login(ownerEmail, "richtiges-passwort");
        MockHttpSession outsiderSession = login(outsiderEmail, "richtiges-passwort");

        mockMvc.perform(get("/projects/new").session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(view().name("wizard/basics"))
                .andExpect(model().attributeExists("projectBasicsForm"));

        long projectsBefore = projectRepository.count();
        long membersBefore = projectMemberRepository.count();
        long draftsBefore = planDraftRepository.count();
        mockMvc.perform(post("/projects/new")
                        .session(ownerSession)
                        .with(csrf())
                        .param("title", "MVC KI-Projekt")
                        .param("description", "Nur temporär")
                        .param("category", "EDUCATION")
                        .param("subcategory", "THESIS")
                        .param("collaborationMode", "INDIVIDUAL")
                        .param("timeFrameType", "START_AND_DURATION")
                        .param("startDate", "2026-09-01")
                        .param("durationDays", "21"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/new/method"));

        assertThat(projectRepository.count()).isEqualTo(projectsBefore);
        assertThat(projectMemberRepository.count()).isEqualTo(membersBefore);
        assertThat(planDraftRepository.count()).isEqualTo(draftsBefore);
        assertThat(ownerSession.getAttribute(ProjectWizardService.SESSION_ATTRIBUTE))
                .isInstanceOfSatisfying(ProjectWizardState.class, state -> {
                    assertThat(state.getCreationType()).isNull();
                    assertThat(state.getTitle()).isEqualTo("MVC KI-Projekt");
                    assertThat(state.getSubcategory()).isEqualTo(ProjectSubCategory.THESIS);
                    assertThat(state.getEndDate()).hasToString("2026-09-21");
                });

        mockMvc.perform(get("/projects/new/method").session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(view().name("wizard/method"))
                .andExpect(content().string(containsString("value=\"EMPTY\"")))
                .andExpect(content().string(containsString("value=\"TEMPLATE\"")))
                .andExpect(content().string(containsString("value=\"AI\"")));

        mockMvc.perform(post("/projects/new/method")
                        .session(ownerSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("wizard/method"))
                .andExpect(model().attributeHasFieldErrors("creationMethodForm", "creationType"));

        mockMvc.perform(post("/projects/new/method")
                        .session(ownerSession)
                        .with(csrf())
                        .param("creationType", "AI"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/new/ai/details"));

        assertThat(projectRepository.count()).isEqualTo(projectsBefore);
        assertThat(projectMemberRepository.count()).isEqualTo(membersBefore);
        assertThat(planDraftRepository.count()).isEqualTo(draftsBefore);
        assertThat(ownerSession.getAttribute(ProjectWizardService.SESSION_ATTRIBUTE))
                .isInstanceOfSatisfying(ProjectWizardState.class,
                        state -> assertThat(state.getCreationType()).isEqualTo(CreationType.AI));

        mockMvc.perform(get("/projects/new/ai/details").session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(view().name("generation/ai-details"))
                .andExpect(content().string(containsString("MVC KI-Projekt")));
        mockMvc.perform(get("/projects/new").session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"2026-09-01\"")))
                .andExpect(content().string(containsString("value=\"21\"")));
        mockMvc.perform(get("/projects/new/ai/details").session(outsiderSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void aiWizardTreatsOtherAsDefaultAndRequiresItsDescription() throws Exception {
        User user = saveUser("wizard-other@example.org", "richtiges-passwort", true);
        MockHttpSession session = login(user.getEmail(), "richtiges-passwort");

        mockMvc.perform(get("/projects/new").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(matchesPattern(
                        "(?s).*<option\\b(?=[^>]*\\bvalue=\"OTHER\")(?=[^>]*\\bselected(?:\\s|=|>))[^>]*>.*")))
                .andExpect(content().string(matchesPattern(
                        "(?s).*<select\\b(?=[^>]*\\bid=\"subcategory\")(?=[^>]*\\bdisabled(?:\\s|=|>))[^>]*>.*")))
                .andExpect(content().string(matchesPattern(
                        "(?s).*<input\\b(?=[^>]*\\bname=\"otherProjectTypeDescription\")(?=[^>]*\\brequired(?:\\s|=|>))[^>]*>.*")));

        long projectsBefore = projectRepository.count();
        mockMvc.perform(post("/projects/new")
                        .session(session)
                        .with(csrf())
                        .param("title", "Anderes Projekt")
                        .param("category", "OTHER")
                        .param("collaborationMode", "INDIVIDUAL")
                        .param("timeFrameType", "NONE"))
                .andExpect(status().isOk())
                .andExpect(view().name("wizard/basics"))
                .andExpect(model().attributeHasFieldErrors(
                        "projectBasicsForm", "otherProjectTypeDescription"));

        mockMvc.perform(post("/projects/new")
                        .session(session)
                        .with(csrf())
                        .param("title", "Anderes Projekt")
                        .param("category", "OTHER")
                        .param("otherProjectTypeDescription", "Privaten Flohmarkt organisieren")
                        .param("collaborationMode", "INDIVIDUAL")
                        .param("timeFrameType", "NONE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/new/method"));

        assertThat(projectRepository.count()).isEqualTo(projectsBefore);
        assertThat(session.getAttribute(ProjectWizardService.SESSION_ATTRIBUTE))
                .isInstanceOfSatisfying(ProjectWizardState.class, saved -> {
                    assertThat(saved.getCategory()).isEqualTo(TemplateCategory.OTHER);
                    assertThat(saved.getOtherProjectTypeDescription()).isEqualTo("Privaten Flohmarkt organisieren");
                });
        mockMvc.perform(get("/projects/new").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Privaten Flohmarkt organisieren")));
    }

    @Test
    void invalidAiBasicsStayOnTheStepAndKeepTheSubmittedValues() throws Exception {
        String email = "ai-invalid-basics@example.org";
        saveUser(email, "richtiges-passwort", true);
        MockHttpSession session = login(email, "richtiges-passwort");
        mockMvc.perform(post("/projects/new")
                        .session(session)
                        .with(csrf())
                        .param("title", "Ursprünglicher Titel")
                        .param("category", "EDUCATION")
                        .param("collaborationMode", "INDIVIDUAL")
                        .param("timeFrameType", "NONE"))
                .andExpect(status().is3xxRedirection());

        long projectsBefore = projectRepository.count();
        MvcResult result = mockMvc.perform(post("/projects/new")
                        .session(session)
                        .with(csrf())
                        .param("title", "Eingegebener Titel")
                        .param("category", "EDUCATION")
                        .param("subcategory", "PRESENTATION_OR_REPORT")
                        .param("collaborationMode", "INDIVIDUAL")
                        .param("timeFrameType", "START_AND_END")
                        .param("startDate", "2026-09-20")
                        .param("endDate", "2026-09-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("wizard/basics"))
                .andExpect(model().attributeHasFieldErrors("projectBasicsForm", "endDate"))
                .andReturn();

        ProjectBasicsForm submitted = (ProjectBasicsForm) result.getModelAndView()
                .getModel().get("projectBasicsForm");
        assertThat(submitted.getTitle()).isEqualTo("Eingegebener Titel");
        assertThat(submitted.getSubcategory()).isEqualTo(ProjectSubCategory.PRESENTATION_OR_REPORT);
        assertThat(submitted.getStartDate()).hasToString("2026-09-20");
        assertThat(submitted.getEndDate()).hasToString("2026-09-01");
        assertThat(projectRepository.count()).isEqualTo(projectsBefore);

        assertThat(session.getAttribute(ProjectWizardService.SESSION_ATTRIBUTE))
                .isInstanceOfSatisfying(ProjectWizardState.class, state ->
                        assertThat(state.getTitle()).isEqualTo("Ursprünglicher Titel"));
    }

    @Test
    void templateCreationEntryStoresGeneralDataWithoutCreatingAProject() throws Exception {
        String email = "template-flow@example.org";
        saveUser(email, "richtiges-passwort", true);
        MockHttpSession session = login(email, "richtiges-passwort");
        long projectsBefore = projectRepository.count();
        long membersBefore = projectMemberRepository.count();

        mockMvc.perform(post("/projects/new")
                        .session(session)
                        .with(csrf())
                        .param("title", "Vorlagenprojekt")
                        .param("category", "EVENT")
                        .param("collaborationMode", "GROUP")
                        .param("timeFrameType", "NONE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/new/method"));
        mockMvc.perform(post("/projects/new/method")
                        .session(session)
                        .with(csrf())
                        .param("creationType", "TEMPLATE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/new/template"));

        assertThat(projectRepository.count()).isEqualTo(projectsBefore);
        assertThat(projectMemberRepository.count()).isEqualTo(membersBefore);
        assertThat(session.getAttribute(ProjectWizardService.SESSION_ATTRIBUTE))
                .isInstanceOfSatisfying(ProjectWizardState.class, state -> {
                    assertThat(state.getTitle()).isEqualTo("Vorlagenprojekt");
                    assertThat(state.getCategory()).isEqualTo(TemplateCategory.EVENT);
                    assertThat(state.getCollaborationMode()).isEqualTo(CollaborationMode.GROUP);
                    assertThat(state.getCreationType()).isEqualTo(CreationType.TEMPLATE);
                });
        mockMvc.perform(get("/projects/new/template").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("wizard/template-catalog"))
                .andExpect(model().attributeExists("templates", "wizardState"));
    }

    @Test
    void cancelRemovesOnlyTheTemporaryCreationState() throws Exception {
        String email = "cancel-flow@example.org";
        User user = saveUser(email, "richtiges-passwort", true);
        MockHttpSession session = login(email, "richtiges-passwort");

        mockMvc.perform(post("/projects/new")
                        .session(session)
                        .with(csrf())
                        .param("title", "Bestehendes Projekt")
                        .param("category", "HOME")
                        .param("collaborationMode", "INDIVIDUAL")
                        .param("timeFrameType", "NONE"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/projects/new/method")
                        .session(session)
                        .with(csrf())
                        .param("creationType", "EMPTY"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/projects/new")
                        .session(session)
                        .with(csrf())
                        .param("title", "Abbrechen")
                        .param("category", "OTHER")
                        .param("otherProjectTypeDescription", "Privates Vorhaben")
                        .param("collaborationMode", "INDIVIDUAL")
                        .param("timeFrameType", "NONE"))
                .andExpect(status().is3xxRedirection());
        assertThat(session.getAttribute(ProjectWizardService.SESSION_ATTRIBUTE)).isNotNull();
        session.setAttribute("unrelated-session-data", "bleibt erhalten");

        long projectsBefore = projectRepository.count();
        mockMvc.perform(post("/projects/new/cancel").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects"));

        assertThat(session.getAttribute(ProjectWizardService.SESSION_ATTRIBUTE)).isNull();
        assertThat(session.getAttribute("unrelated-session-data")).isEqualTo("bleibt erhalten");
        assertThat(projectRepository.count()).isEqualTo(projectsBefore);
        assertThat(projectRepository.findAllAccessibleByUserId(user.getId()))
                .singleElement()
                .extracting(Project::getTitle)
                .isEqualTo("Bestehendes Projekt");
    }

    @Test
    void draftProjectCannotBeManagedThroughDirectActiveProjectUrls() throws Exception {
        String email = "draft-direct-url@example.org";
        User owner = saveUser(email, "richtiges-passwort", true);
        MockHttpSession session = login(email, "richtiges-passwort");
        Project draft = saveDraftProject(owner);

        mockMvc.perform(get("/projects/{projectId}/plan", draft.getId()).session(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/projects/{projectId}/members", draft.getId()).session(session))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/projects/{projectId}/edit", draft.getId())
                        .session(session).with(csrf()).param("title", "Manipuliert").param("category", "EDUCATION").param("collaborationMode", "INDIVIDUAL"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/projects/{projectId}/trash", draft.getId())
                        .session(session).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/projects/{projectId}/sections", draft.getId())
                        .session(session).with(csrf()).param("title", "Manipulierte Section"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/projects/{projectId}/tasks", draft.getId())
                        .session(session).with(csrf())
                        .param("title", "Manipulierte Aufgabe")
                        .param("priority", "MEDIUM"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/projects/{projectId}/milestones", draft.getId())
                        .session(session).with(csrf()).param("title", "Manipulierter Meilenstein"))
                .andExpect(status().isConflict());

        assertThat(projectRepository.findById(draft.getId())).get().satisfies(project -> {
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.DRAFT);
            assertThat(project.getLocation()).isEqualTo(ProjectLocation.DRAFT);
            assertThat(project.getTitle()).isEqualTo("Direkt gesperrter Entwurf");
        });
    }

    @Test
    void projectPlanRendersTasksAndMilestonesInTheirCombinedOrder() throws Exception {
        String email = "render-order@example.org";
        User user = saveUser(email, "richtiges-passwort", true);
        MockHttpSession session = login(email, "richtiges-passwort");
        mockMvc.perform(post("/projects/new")
                        .session(session)
                        .with(csrf())
                        .param("title", "Darstellungsprojekt")
                        .param("category", "EDUCATION")
                        .param("collaborationMode", "INDIVIDUAL")
                        .param("timeFrameType", "NONE"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/projects/new/method")
                        .session(session)
                        .with(csrf())
                        .param("creationType", "EMPTY"))
                .andExpect(status().is3xxRedirection());
        UUID projectId = projectRepository.findAllAccessibleByUserId(user.getId()).getFirst().getId();

        SectionForm sectionForm = new SectionForm();
        sectionForm.setTitle("Umsetzung");
        SectionDto section = sectionService.createSection(projectId, sectionForm, user.getId());
        taskService.createTask(projectId, taskForm("Reihenfolge 1", section.getId()), user.getId());
        taskService.createTask(projectId, taskForm("Reihenfolge 3", section.getId()), user.getId());
        MilestoneForm milestoneForm = new MilestoneForm();
        milestoneForm.setTitle("Reihenfolge 2");
        milestoneForm.setPlanSectionId(section.getId());
        milestoneForm.setSortOrder(1);
        milestoneService.createMilestone(projectId, milestoneForm, user.getId());

        String html = mockMvc.perform(get("/projects/{projectId}/plan", projectId).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html.indexOf("Reihenfolge 1")).isLessThan(html.indexOf("Reihenfolge 2"));
        assertThat(html.indexOf("Reihenfolge 2")).isLessThan(html.indexOf("Reihenfolge 3"));
    }


    @Test
    void subcategoryBindingRejectsManipulationAndPreservesValidSelections() throws Exception {
        var user = saveUser("subcategory-binding@example.org", "richtiges-passwort", true);
        var session = login(user.getEmail(), "richtiges-passwort");
        mockMvc.perform(post("/projects/new").session(session).with(csrf())
                        .param("title", "Abschlussarbeit").param("category", "EDUCATION")
                        .param("subcategory", "THESIS").param("collaborationMode", "INDIVIDUAL"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/projects/new").session(session))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(subcategoryDropdown(result).split("<option")).hasSize(8))
                .andExpect(result -> assertThat(subcategoryDropdown(result)).containsPattern(
                        "<option[^>]*value=\"THESIS\"[^>]*selected=\"selected\""))
                .andExpect(result -> assertThat(subcategoryDropdown(result)).doesNotContain("MOVING"))
                .andExpect(result -> assertThat(subcategoryDropdown(result)).contains(">Abschlussarbeit</option>"));
        for (String[] input : new String[][] {
                {"EDUCATION", "MOVING"}, {"OTHER", "THESIS"}, {"EDUCATION", "manipuliert"}}) {
            mockMvc.perform(post("/projects/new").session(session).with(csrf())
                            .param("title", "Nicht übernehmen").param("category", input[0])
                            .param("subcategory", input[1]).param("otherProjectTypeDescription", "Privates Vorhaben")
                            .param("collaborationMode", "INDIVIDUAL"))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrors("projectBasicsForm", "subcategory"))
                    .andExpect(content().string(containsString("Bitte wähle eine")));
            assertThat(((ProjectWizardState) session.getAttribute(ProjectWizardService.SESSION_ATTRIBUTE))
                    .getSubcategory()).isEqualTo(ProjectSubCategory.THESIS);
        }
        mockMvc.perform(post("/projects/new").session(session).with(csrf())
                        .param("title", "").param("category", "EDUCATION")
                        .param("subcategory", "THESIS").param("collaborationMode", "INDIVIDUAL"))
                .andExpect(model().attributeHasFieldErrors("projectBasicsForm", "title"))
                .andExpect(result -> assertThat(subcategoryDropdown(result)).containsPattern(
                        "<option[^>]*value=\"THESIS\"[^>]*selected=\"selected\""));
        mockMvc.perform(post("/projects/new").session(session).with(csrf())
                        .param("title", "Wohnprojekt").param("category", "HOME")
                        .param("subcategory", "").param("collaborationMode", "INDIVIDUAL"))
                .andExpect(status().is3xxRedirection());
        assertThat(((ProjectWizardState) session.getAttribute(ProjectWizardService.SESSION_ATTRIBUTE))
                .getSubcategory()).isNull();
        mockMvc.perform(get("/projects/new").session(session))
                .andExpect(result -> assertThat(subcategoryDropdown(result).split("<option")).hasSize(7))
                .andExpect(result -> assertThat(subcategoryDropdown(result)).doesNotContain("selected=\"selected\""));
    }

    @Test
    void projectEditingRestoresAndValidatesTypedClassification() throws Exception {
        var user = saveUser("subcategory-edit@example.org", "richtiges-passwort", true);
        var session = login(user.getEmail(), "richtiges-passwort");
        mockMvc.perform(post("/projects/new").session(session).with(csrf())
                        .param("title", "Mein Umzug").param("category", "HOME")
                        .param("subcategory", "MOVING").param("collaborationMode", "INDIVIDUAL"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/projects/new/method").session(session).with(csrf())
                        .param("creationType", "EMPTY"))
                .andExpect(status().is3xxRedirection());
        var id = projectRepository.findAllAccessibleByUserId(user.getId()).getFirst().getId();
        mockMvc.perform(get("/projects/{id}/edit", id).session(session))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(subcategoryDropdown(result)).containsPattern(
                        "<option[^>]*value=\"MOVING\"[^>]*selected=\"selected\""));
        mockMvc.perform(post("/projects/{id}/edit", id).session(session).with(csrf()).param("collaborationMode", "INDIVIDUAL")
                        .param("title", "Mein Umzug").param("category", "HOME").param("subcategory", "THESIS"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("projectForm", "subcategory"));
        assertThat(projectRepository.findById(id).orElseThrow().getSubcategory()).isEqualTo(ProjectSubCategory.MOVING);
        mockMvc.perform(post("/projects/{id}/edit", id).session(session).with(csrf()).param("collaborationMode", "INDIVIDUAL")
                        .param("title", "Meine Arbeit").param("category", "EDUCATION").param("subcategory", "THESIS"))
                .andExpect(status().is3xxRedirection());
        assertThat(projectRepository.findById(id).orElseThrow().getSubcategory()).isEqualTo(ProjectSubCategory.THESIS);
        mockMvc.perform(post("/projects/{id}/edit", id).session(session).with(csrf()).param("collaborationMode", "INDIVIDUAL")
                        .param("title", "Anderes").param("category", "OTHER").param("subcategory", ""))
                .andExpect(model().attributeHasFieldErrors("projectForm", "otherProjectTypeDescription"));
        mockMvc.perform(post("/projects/{id}/edit", id).session(session).with(csrf()).param("collaborationMode", "INDIVIDUAL")
                        .param("title", "Anderes").param("category", "OTHER").param("subcategory", "")
                        .param("otherProjectTypeDescription", "Besonderes Vorhaben"))
                .andExpect(status().is3xxRedirection());
        assertThat(projectRepository.findById(id).orElseThrow().getSubcategory()).isNull();
    }

    private String subcategoryDropdown(MvcResult result) throws Exception {
        var matcher = java.util.regex.Pattern.compile(
                "<select[^>]*id=\"subcategory\"[^>]*>(.*?)</select>",
                java.util.regex.Pattern.DOTALL).matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private TaskForm taskForm(String title, UUID sectionId) {
        TaskForm form = new TaskForm();
        form.setTitle(title);
        form.setPriority(TaskPriority.MEDIUM);
        form.setPlanSectionId(sectionId);
        return form;
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/login")
                        .param("email", email)
                        .param("password", password)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private void assertLoginFailure(String email, String password) throws Exception {
        mockMvc.perform(post("/login")
                        .param("email", email)
                        .param("password", password)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    private User saveUser(String email, String password, boolean enabled) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("Security Test");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEnabled(enabled);
        return userRepository.saveAndFlush(user);
    }

    private Project saveDraftProject(User owner) {
        Project project = new Project();
        project.setTitle("Direkt gesperrter Entwurf");
        project.setCategory(TemplateCategory.EDUCATION);
        project.setSubcategory(ProjectSubCategory.PRESENTATION_OR_REPORT);
        project.setCollaborationMode(CollaborationMode.INDIVIDUAL);
        project.setCreationType(CreationType.AI);
        project.setStatus(ProjectStatus.DRAFT);
        project.setLocation(ProjectLocation.DRAFT);
        ProjectMember membership = new ProjectMember();
        membership.setUser(owner);
        membership.setRole(ProjectMemberRole.OWNER);
        project.addMembership(membership);
        return projectRepository.saveAndFlush(project);
    }
}
