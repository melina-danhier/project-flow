package de.melinadanhier.projectflow.security;

import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
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
import static org.hamcrest.Matchers.not;
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

        mockMvc.perform(post("/projects")
                        .session(session)
                        .param("title", "Controller-Projekt")
                        .param("creationType", "EMPTY"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/projects")
                        .session(session)
                        .with(csrf())
                        .param("title", "Controller-Projekt")
                        .param("creationType", "EMPTY"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/projects/*"));
        assertThat(projectRepository.findAllAccessibleByUserId(user.getId()))
                .singleElement().extracting("title").isEqualTo("Controller-Projekt");

        UUID projectId = projectRepository.findAllAccessibleByUserId(user.getId()).getFirst().getId();
        mockMvc.perform(get("/projects/{projectId}", projectId).session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/plan"));
        mockMvc.perform(post("/projects/{projectId}/tasks", projectId)
                        .session(session)
                        .with(csrf())
                        .param("title", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void regularCreateUiOnlyOffersEmptyProjectsAndRejectsManipulatedAiType() throws Exception {
        String email = "blocked-ai-controller@example.org";
        User user = saveUser(email, "richtiges-passwort", true);
        MockHttpSession session = login(email, "richtiges-passwort");

        mockMvc.perform(get("/projects/new").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"creationType\"")))
                .andExpect(content().string(containsString("value=\"EMPTY\"")))
                .andExpect(content().string(not(containsString("value=\"AI\""))))
                .andExpect(content().string(not(containsString("KI-Unterstützung"))));

        mockMvc.perform(post("/projects")
                        .session(session)
                        .with(csrf())
                        .param("title", "Manipuliertes KI-Projekt")
                        .param("creationType", "AI"))
                .andExpect(status().isBadRequest());
        assertThat(projectRepository.findAllAccessibleByUserId(user.getId())).isEmpty();
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

        String html = mockMvc.perform(get("/projects/{projectId}", projectId).session(session))
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
}
