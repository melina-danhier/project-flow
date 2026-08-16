package de.melinadanhier.projectflow.security;

import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectMemberRepository;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectCreationFlowState;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectCreationFlowService;
import de.melinadanhier.projectflow.generation.repository.PlanDraftRepository;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMemberRole;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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
        ProjectCreationFlowState staleState = new ProjectCreationFlowState();
        staleState.setUserId(user.getId());
        staleState.setCreationType(CreationType.AI);
        session.setAttribute(ProjectCreationFlowService.SESSION_ATTRIBUTE, staleState);

        mockMvc.perform(post("/projects")
                        .session(session)
                        .param("title", "Controller-Projekt")
                        .param("category", "HOME")
                        .param("collaborationMode", "INDIVIDUAL")
                        .param("creationType", "EMPTY"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/projects")
                        .session(session)
                        .with(csrf())
                        .param("title", "Controller-Projekt")
                        .param("category", "HOME")
                        .param("collaborationMode", "INDIVIDUAL")
                        .param("creationType", "EMPTY"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/projects/*/plan"));
        assertThat(session.getAttribute(ProjectCreationFlowService.SESSION_ATTRIBUTE)).isNull();
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
                .andExpect(content().string(containsString("name=\"creationType\"")))
                .andExpect(content().string(containsString("value=\"EMPTY\"")))
                .andExpect(content().string(containsString("value=\"TEMPLATE\"")))
                .andExpect(content().string(containsString("value=\"AI\"")))
                .andExpect(content().string(containsString("Projekt ohne anfängliche Aufgaben")))
                .andExpect(content().string(containsString("Vorlage auswählen")))
                .andExpect(content().string(containsString("Weiter zu den KI-Angaben")));

        long projectsBefore = projectRepository.count();
        long membersBefore = projectMemberRepository.count();
        long draftsBefore = planDraftRepository.count();
        mockMvc.perform(post("/projects")
                        .session(ownerSession)
                        .with(csrf())
                        .param("title", "MVC KI-Projekt")
                        .param("description", "Nur temporär")
                        .param("category", "EDUCATION")
                        .param("projectType", "Bachelorarbeit")
                        .param("collaborationMode", "INDIVIDUAL")
                        .param("creationType", "AI"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/new/ai"));

        assertThat(projectRepository.count()).isEqualTo(projectsBefore);
        assertThat(projectMemberRepository.count()).isEqualTo(membersBefore);
        assertThat(planDraftRepository.count()).isEqualTo(draftsBefore);
        assertThat(ownerSession.getAttribute(ProjectCreationFlowService.SESSION_ATTRIBUTE))
                .isInstanceOfSatisfying(ProjectCreationFlowState.class, state -> {
                    assertThat(state.getCreationType()).isEqualTo(CreationType.AI);
                    assertThat(state.getTitle()).isEqualTo("MVC KI-Projekt");
                    assertThat(state.getProjectType()).isEqualTo("Bachelorarbeit");
                });

        mockMvc.perform(get("/projects/new/ai").session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(view().name("generation/ai-wizard"))
                .andExpect(content().string(containsString("Grundangaben zum Projekt")))
                .andExpect(content().string(containsString("Bachelorarbeit")));

        mockMvc.perform(post("/projects/new/ai")
                        .session(ownerSession)
                        .with(csrf())
                        .param("title", "Überarbeitete Präsentation")
                        .param("category", "EDUCATION")
                        .param("subcategory", "Präsentation")
                        .param("timeFrameType", "START_AND_DURATION")
                        .param("startDate", "2026-09-01")
                        .param("durationDays", "21"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/new/ai/details"));

        assertThat(projectRepository.count()).isEqualTo(projectsBefore);
        assertThat(projectMemberRepository.count()).isEqualTo(membersBefore);
        assertThat(planDraftRepository.count()).isEqualTo(draftsBefore);
        assertThat(ownerSession.getAttribute(ProjectCreationFlowService.SESSION_ATTRIBUTE))
                .isInstanceOfSatisfying(ProjectCreationFlowState.class, state -> {
                    assertThat(state.getTitle()).isEqualTo("Überarbeitete Präsentation");
                    assertThat(state.getProjectType()).isEqualTo("Präsentation");
                    assertThat(state.getStartDate()).hasToString("2026-09-01");
                    assertThat(state.getEndDate()).hasToString("2026-09-21");
                    assertThat(state.getDurationDays()).isEqualTo(21);
                });

        mockMvc.perform(get("/projects/new/ai/details").session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(view().name("generation/ai-details"))
                .andExpect(content().string(containsString("Überarbeitete Präsentation")));
        mockMvc.perform(get("/projects/new/ai").session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"2026-09-01\"")))
                .andExpect(content().string(containsString("value=\"21\"")));
        mockMvc.perform(get("/projects/new/ai").session(outsiderSession))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/projects/new/ai/details").session(outsiderSession))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/projects/new/ai")
                        .session(outsiderSession)
                        .with(csrf())
                        .param("title", "Fremder Zugriff")
                        .param("category", "EDUCATION")
                        .param("timeFrameType", "NONE"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aiWizardTreatsOtherAsDefaultAndRequiresItsDescription() throws Exception {
        User user = saveUser("ai-other@example.org", "richtiges-passwort", true);
        MockHttpSession session = login(user.getEmail(), "richtiges-passwort");
        ProjectCreationFlowState state = new ProjectCreationFlowState();
        state.setUserId(user.getId());
        state.setTitle("Anderes Projekt");
        state.setCreationType(CreationType.AI);
        state.setCollaborationMode(CollaborationMode.INDIVIDUAL);
        session.setAttribute(ProjectCreationFlowService.SESSION_ATTRIBUTE, state);

        mockMvc.perform(get("/projects/new/ai").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "<option value=\"OTHER\" selected=\"selected\">Sonstiges</option>")))
                .andExpect(content().string(containsString(
                        "id=\"subcategory-fields\" hidden=\"hidden\"")))
                .andExpect(content().string(containsString(
                        "id=\"subcategory\" maxlength=\"100\" disabled=\"disabled\"")))
                .andExpect(content().string(containsString(
                        "placeholder=\"z. B. privater Flohmarkt\"")))
                .andExpect(content().string(containsString(
                        "required=\"required\" name=\"otherProjectTypeDescription\"")));

        long projectsBefore = projectRepository.count();
        mockMvc.perform(post("/projects/new/ai")
                        .session(session)
                        .with(csrf())
                        .param("title", "Anderes Projekt")
                        .param("category", "OTHER")
                        .param("timeFrameType", "NONE"))
                .andExpect(status().isOk())
                .andExpect(view().name("generation/ai-wizard"))
                .andExpect(model().attributeHasFieldErrors("projectBasicsForm", "projectTypeValid"));

        mockMvc.perform(post("/projects/new/ai")
                        .session(session)
                        .with(csrf())
                        .param("title", "Anderes Projekt")
                        .param("category", "OTHER")
                        .param("subcategory", "Wird verworfen")
                        .param("otherProjectTypeDescription", "Privaten Flohmarkt organisieren")
                        .param("timeFrameType", "NONE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/new/ai/details"));

        assertThat(projectRepository.count()).isEqualTo(projectsBefore);
        assertThat(session.getAttribute(ProjectCreationFlowService.SESSION_ATTRIBUTE))
                .isInstanceOfSatisfying(ProjectCreationFlowState.class, saved -> {
                    assertThat(saved.getCategory()).isEqualTo(TemplateCategory.OTHER);
                    assertThat(saved.getProjectType()).isEqualTo("Privaten Flohmarkt organisieren");
                });
    }

    @Test
    void templateCreationEntryStoresGeneralDataWithoutCreatingAProject() throws Exception {
        String email = "template-flow@example.org";
        saveUser(email, "richtiges-passwort", true);
        MockHttpSession session = login(email, "richtiges-passwort");
        long projectsBefore = projectRepository.count();
        long membersBefore = projectMemberRepository.count();

        mockMvc.perform(post("/projects")
                        .session(session)
                        .with(csrf())
                        .param("title", "Vorlagenprojekt")
                        .param("category", "EVENT")
                        .param("collaborationMode", "GROUP")
                        .param("creationType", "TEMPLATE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/new/template"));

        assertThat(projectRepository.count()).isEqualTo(projectsBefore);
        assertThat(projectMemberRepository.count()).isEqualTo(membersBefore);
        mockMvc.perform(get("/projects/new/template").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("templates/overview"));
    }

    @Test
    void cancelRemovesOnlyTheTemporaryCreationState() throws Exception {
        String email = "cancel-flow@example.org";
        saveUser(email, "richtiges-passwort", true);
        MockHttpSession session = login(email, "richtiges-passwort");
        mockMvc.perform(post("/projects")
                        .session(session)
                        .with(csrf())
                        .param("title", "Abbrechen")
                        .param("category", "OTHER")
                        .param("collaborationMode", "INDIVIDUAL")
                        .param("creationType", "AI"))
                .andExpect(status().is3xxRedirection());
        assertThat(session.getAttribute(ProjectCreationFlowService.SESSION_ATTRIBUTE)).isNotNull();

        long projectsBefore = projectRepository.count();
        mockMvc.perform(post("/projects/new/cancel").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects"));

        assertThat(session.getAttribute(ProjectCreationFlowService.SESSION_ATTRIBUTE)).isNull();
        assertThat(projectRepository.count()).isEqualTo(projectsBefore);
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
                        .session(session).with(csrf()).param("title", "Manipuliert"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/projects/{projectId}/trash", draft.getId())
                        .session(session).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/projects/{projectId}/sections", draft.getId())
                        .session(session).with(csrf()).param("title", "Manipulierte Phase"))
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
        mockMvc.perform(post("/projects")
                        .session(session)
                        .with(csrf())
                        .param("title", "Darstellungsprojekt")
                        .param("category", "EDUCATION")
                        .param("collaborationMode", "INDIVIDUAL")
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
        project.setProjectType("Präsentation");
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
