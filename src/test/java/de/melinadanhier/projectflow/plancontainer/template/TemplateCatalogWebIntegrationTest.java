package de.melinadanhier.projectflow.plancontainer.template;

import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import de.melinadanhier.projectflow.planelement.model.Milestone;
import de.melinadanhier.projectflow.planelement.model.PlanSection;
import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.Template;
import de.melinadanhier.projectflow.plancontainer.template.repository.TemplateRepository;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.planelement.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpSession;
import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import de.melinadanhier.projectflow.wizard.model.ProjectWizardState;
import de.melinadanhier.projectflow.wizard.service.ProjectWizardService;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TemplateCatalogWebIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TemplateRepository templateRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ProjectRepository projectRepository;
    @Autowired TaskRepository taskRepository;

    private UUID templateId;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        projectRepository.deleteAll();
        templateRepository.deleteAll();
        Template template = new Template();
        template.setTitle("Umzug kompakt planen");
        template.setDescription("Eine Checkliste für den Wohnungswechsel");
        template.setCategory(ProjectCategory.HOME);
        template.setCollaborationMode(CollaborationMode.BOTH);
        template.setRecommendedDurationDays(4);
        PlanSection section = new PlanSection();
        section.setTitle("Vorbereitung");
        section.setSortOrder(100);
        section.setOrigin(ElementOrigin.TEMPLATE);
        template.addSection(section);
        Task task = new Task();
        task.setTitle("Kartons besorgen");
        task.setSortOrder(100);
        task.setOrigin(ElementOrigin.TEMPLATE);
        task.setRelativeDueDay(3);
        task.setEstimatedHours(2);
        template.addElement(task);
        section.addElement(task);
        Milestone milestone = new Milestone();
        milestone.setTitle("Umzug abgeschlossen");
        milestone.setSortOrder(200);
        milestone.setOrigin(ElementOrigin.TEMPLATE);
        template.addElement(milestone);
        section.addElement(milestone);
        templateId = templateRepository.saveAndFlush(template).getId();
    }

    @Test
    void catalogSearchAndPreviewArePublicAndReadOnly() throws Exception {
        mockMvc.perform(get("/templates").param("category", "HOME"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Umzug kompakt planen")))
                .andExpect(content().string(containsString("1 Aufgabe")))
                .andExpect(content().string(containsString("1 Meilenstein")));
        mockMvc.perform(get("/templates/search").param("q", "WOHNUNGS"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Umzug kompakt planen")));
        mockMvc.perform(get("/templates/{id}", templateId).param("q", "Wohnung"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vorlage · schreibgeschützt")))
                .andExpect(content().string(containsString("Kartons besorgen")))
                .andExpect(content().string(containsString("Tag 3 ab Projektstart")))
                .andExpect(content().string(containsString("Aufwand: 2 Std.")))
                .andExpect(content().string(not(containsString("Aufgabe hinzufügen"))))
                .andExpect(content().string(containsString("Anmelden und Vorlage verwenden")));
    }

    @Test
    void usingPublicTemplateRequiresLoginAndKeepsTemplateInSavedRequest() throws Exception {
        var result = mockMvc.perform(get("/projects/new").param("templateId", templateId.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andReturn();
        SavedRequest savedRequest = (SavedRequest) result.getRequest().getSession()
                .getAttribute("SPRING_SECURITY_SAVED_REQUEST");
        org.assertj.core.api.Assertions.assertThat(savedRequest.getParameterValues("templateId"))
                .containsExactly(templateId.toString());
    }

    @Test
    void standaloneSelectionIsRetainedByTheExistingWizardSession() throws Exception {
        User user = new User();
        user.setEmail("template-selection-" + UUID.randomUUID() + "@example.org");
        user.setDisplayName("Template Test");
        user.setPasswordHash(passwordEncoder.encode("richtiges-passwort"));
        user.setEnabled(true);
        userRepository.saveAndFlush(user);
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/login")
                        .param("email", user.getEmail()).param("password", "richtiges-passwort").with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn().getRequest().getSession(false);

        mockMvc.perform(get("/projects/new").param("templateId", templateId.toString()).session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("wizard/basics"))
                .andExpect(model().attributeExists("selectedTemplate"));
        org.assertj.core.api.Assertions.assertThat(session.getAttribute(ProjectWizardService.SESSION_ATTRIBUTE))
                .isInstanceOfSatisfying(ProjectWizardState.class, state -> {
                    org.assertj.core.api.Assertions.assertThat(state.getSelectedTemplateId()).isEqualTo(templateId);
                    org.assertj.core.api.Assertions.assertThat(state.getCreationType())
                            .isEqualTo(de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.CreationType.TEMPLATE);
                });
    }

    @Test
    void directCatalogSelectionUsesAnEndDateAndSkipsConfirmationWhenConversionIsPossible() throws Exception {
        MockHttpSession session = login("direct-template@example.org");
        ProjectWizardState state = wizardState(null, java.time.LocalDate.of(2026, 9, 20));
        session.setAttribute(ProjectWizardService.SESSION_ATTRIBUTE, state);

        mockMvc.perform(get("/projects/new/template").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Direkt verwenden")));
        mockMvc.perform(post("/projects/new/template/{id}", templateId).session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/projects/*/plan"));

        Project project = projectRepository.findAll().getFirst();
        org.assertj.core.api.Assertions.assertThat(project.getStartDate()).isNull();
        org.assertj.core.api.Assertions.assertThat(project.getEndDate())
                .isEqualTo(java.time.LocalDate.of(2026, 9, 20));
        org.assertj.core.api.Assertions.assertThat(taskRepository.findPlanTasks(project.getId())).singleElement()
                .satisfies(task -> {
                    org.assertj.core.api.Assertions.assertThat(task.getDueDate())
                            .isEqualTo(java.time.LocalDate.of(2026, 9, 20));
                    org.assertj.core.api.Assertions.assertThat(task.getRelativeDueDay()).isNull();
                });
    }

    @Test
    void durationConflictRequiresConfirmationOrChangedProjectInputs() throws Exception {
        MockHttpSession session = login("conflict-template@example.org");
        ProjectWizardState state = wizardState(
                java.time.LocalDate.of(2026, 9, 1), java.time.LocalDate.of(2026, 9, 2));
        session.setAttribute(ProjectWizardService.SESSION_ATTRIBUTE, state);

        mockMvc.perform(post("/projects/new/template/{id}", templateId).session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/new/template/confirm"));
        mockMvc.perform(get("/projects/new/template/confirm").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vorlage benötigt <strong>4</strong> Tage")))
                .andExpect(content().string(containsString("Projektzeitraum umfasst aber nur <strong>2</strong> Tage")))
                .andExpect(content().string(containsString("Projektangaben ändern")));

        mockMvc.perform(post("/projects/new/template/confirm").session(session).with(csrf())
                        .param("confirmed", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/projects/*/plan"));

        Project project = projectRepository.findAll().getFirst();
        org.assertj.core.api.Assertions.assertThat(project.getStartDate())
                .isEqualTo(java.time.LocalDate.of(2026, 9, 1));
        org.assertj.core.api.Assertions.assertThat(project.getEndDate())
                .isEqualTo(java.time.LocalDate.of(2026, 9, 2));
        org.assertj.core.api.Assertions.assertThat(taskRepository.findPlanTasks(project.getId())).singleElement()
                .satisfies(task -> {
                    org.assertj.core.api.Assertions.assertThat(task.getDueDate()).isNull();
                    org.assertj.core.api.Assertions.assertThat(task.getRelativeDueDay()).isNull();
                });
    }

    @Test
    void templateWithoutRelativeDatesIsCreatedDirectlyWithoutConfirmation() throws Exception {
        var tasks = taskRepository.findPlanTasks(templateId);
        tasks.forEach(task -> task.setRelativeDueDay(null));
        taskRepository.saveAllAndFlush(tasks);
        MockHttpSession session = login("undated-template@example.org");
        session.setAttribute(ProjectWizardService.SESSION_ATTRIBUTE, wizardState(null, null));

        mockMvc.perform(post("/projects/new/template/{id}", templateId).session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/projects/*/plan"));
    }

    @Test
    void missingProjectDatesWarnsAndRequiresExplicitIgnoreConfirmation() throws Exception {
        MockHttpSession session = login("missing-dates-template@example.org");
        session.setAttribute(ProjectWizardService.SESSION_ATTRIBUTE, wizardState(null, null));

        mockMvc.perform(post("/projects/new/template/{id}", templateId).session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/new/template/confirm"));
        mockMvc.perform(get("/projects/new/template/confirm").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ohne Start- oder Enddatum")));
        mockMvc.perform(post("/projects/new/template/confirm").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bitte bestätige die Übernahme")));
        org.assertj.core.api.Assertions.assertThat(projectRepository.count()).isZero();

        mockMvc.perform(post("/projects/new/template/confirm").session(session).with(csrf())
                        .param("confirmed", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/projects/*/plan"));
        Project project = projectRepository.findAll().getFirst();
        org.assertj.core.api.Assertions.assertThat(taskRepository.findPlanTasks(project.getId())).singleElement()
                .satisfies(task -> {
                    org.assertj.core.api.Assertions.assertThat(task.getDueDate()).isNull();
                    org.assertj.core.api.Assertions.assertThat(task.getRelativeDueDay()).isNull();
                });
    }

    @Test
    void directTemplateAdoptionRequiresLoginAndCsrf() throws Exception {
        mockMvc.perform(post("/projects/new/template/{id}", templateId).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        MockHttpSession session = login("protected-template@example.org");
        session.setAttribute(ProjectWizardService.SESSION_ATTRIBUTE,
                wizardState(null, java.time.LocalDate.of(2026, 9, 20)));
        mockMvc.perform(post("/projects/new/template/{id}", templateId).session(session))
                .andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(projectRepository.count()).isZero();
    }

    private MockHttpSession login(String email) throws Exception {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("Template Test");
        user.setPasswordHash(passwordEncoder.encode("richtiges-passwort"));
        user.setEnabled(true);
        currentUserId = userRepository.saveAndFlush(user).getId();
        return (MockHttpSession) mockMvc.perform(post("/login")
                        .param("email", email).param("password", "richtiges-passwort").with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn().getRequest().getSession(false);
    }

    private ProjectWizardState wizardState(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        ProjectWizardState state = new ProjectWizardState();
        state.setUserId(currentUserId);
        state.setTitle("Projekt aus Vorlage");
        state.setCategory(ProjectCategory.HOME);
        state.setCollaborationMode(CollaborationMode.INDIVIDUAL);
        state.setCreationType(CreationType.TEMPLATE);
        state.setStartDate(startDate);
        state.setEndDate(endDate);
        return state;
    }
}
