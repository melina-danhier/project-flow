package de.melinadanhier.projectflow.plancontainer.template;

import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import de.melinadanhier.projectflow.planelement.model.Milestone;
import de.melinadanhier.projectflow.planelement.model.PlanSection;
import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.Template;
import de.melinadanhier.projectflow.plancontainer.template.repository.TemplateRepository;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TemplateCatalogWebIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TemplateRepository templateRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private UUID templateId;

    @BeforeEach
    void setUp() {
        templateRepository.deleteAll();
        Template template = new Template();
        template.setTitle("Umzug kompakt planen");
        template.setDescription("Eine Checkliste für den Wohnungswechsel");
        template.setCategory(ProjectCategory.HOME);
        template.setCollaborationMode(CollaborationMode.BOTH);
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
}
