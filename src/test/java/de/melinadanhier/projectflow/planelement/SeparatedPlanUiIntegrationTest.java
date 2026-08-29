package de.melinadanhier.projectflow.planelement;

import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMemberRole;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectMemberRepository;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.planelement.dto.MilestoneForm;
import de.melinadanhier.projectflow.planelement.dto.SectionDto;
import de.melinadanhier.projectflow.planelement.dto.SectionForm;
import de.melinadanhier.projectflow.planelement.dto.TaskForm;
import de.melinadanhier.projectflow.planelement.model.Milestone;
import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import de.melinadanhier.projectflow.planelement.model.TaskStatus;
import de.melinadanhier.projectflow.planelement.repository.MilestoneRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import de.melinadanhier.projectflow.planelement.repository.TaskRepository;
import de.melinadanhier.projectflow.planelement.service.MilestoneService;
import de.melinadanhier.projectflow.planelement.service.SectionService;
import de.melinadanhier.projectflow.planelement.service.TaskService;
import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
class SeparatedPlanUiIntegrationTest {

    private static final String PASSWORD = "richtiges-passwort";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private PlanSectionRepository sectionRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SectionService sectionService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private MilestoneService milestoneService;

    @Test
    void taskAndMilestoneFormsHaveDedicatedPagesPrefillAllFieldsAndRedirectAfterPosts() throws Exception {
        User owner = saveUser("separate-forms-owner@example.org");
        Project project = saveProject("Getrennte Formulare", owner);
        ProjectMember ownerMembership = projectMemberRepository
                .findByProjectIdAndUserId(project.getId(), owner.getId()).orElseThrow();
        SectionDto section = createSection(project, owner, "Umsetzung");
        MockHttpSession session = login(owner.getEmail());

        mockMvc.perform(get("/projects/{projectId}/tasks/new", project.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/tasks/form"))
                .andExpect(model().attributeExists("taskForm", "sections", "assignees"));

        MvcResult taskCreation = mockMvc.perform(post("/projects/{projectId}/tasks", project.getId())
                        .session(session).with(csrf())
                        .param("title", "Ausarbeitung")
                        .param("description", "Alle Felder bleiben erhalten")
                        .param("priority", "HIGH")
                        .param("status", "IN_PROGRESS")
                        .param("planSectionId", section.getId().toString())
                        .param("startDate", "2026-08-15")
                        .param("dueDate", "2026-08-20")
                        .param("assigneeId", ownerMembership.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        Task task = taskRepository.findPlanTasks(project.getId()).getFirst();
        assertThat(taskCreation.getResponse().getRedirectedUrl())
                .isEqualTo("/projects/" + project.getId() + "/tasks/" + task.getId());
        assertThat(task.getDescription()).isEqualTo("Alle Felder bleiben erhalten");
        assertThat(task.getPlanSection().getId()).isEqualTo(section.getId());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(task.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(task.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(task.getAssignee().getId()).isEqualTo(ownerMembership.getId());

        mockMvc.perform(get("/projects/{projectId}/tasks/{taskId}", project.getId(), task.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/tasks/detail"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ausarbeitung")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Umsetzung")));

        MvcResult taskEdit = mockMvc.perform(get(
                        "/projects/{projectId}/tasks/{taskId}/edit", project.getId(), task.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/tasks/form"))
                .andReturn();
        TaskForm taskForm = (TaskForm) taskEdit.getModelAndView().getModel().get("taskForm");
        assertThat(taskForm.getTitle()).isEqualTo("Ausarbeitung");
        assertThat(taskForm.getDescription()).isEqualTo("Alle Felder bleiben erhalten");
        assertThat(taskForm.getPlanSectionId()).isEqualTo(section.getId());
        assertThat(taskForm.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(taskForm.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(taskForm.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(taskForm.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(taskForm.getAssigneeId()).isEqualTo(ownerMembership.getId());

        mockMvc.perform(post("/projects/{projectId}/tasks/{taskId}", project.getId(), task.getId())
                        .session(session).with(csrf())
                        .param("title", "")
                        .param("description", "Eingabe bleibt sichtbar")
                        .param("priority", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/tasks/form"))
                .andExpect(model().attributeHasFieldErrors("taskForm", "title"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Eingabe bleibt sichtbar")));

        mockMvc.perform(post("/projects/{projectId}/tasks/{taskId}", project.getId(), task.getId())
                        .session(session).with(csrf())
                        .param("title", "Ausarbeitung aktualisiert")
                        .param("description", task.getDescription())
                        .param("priority", task.getPriority().name())
                        .param("status", task.getStatus().name())
                        .param("planSectionId", section.getId().toString())
                        .param("startDate", task.getStartDate().toString())
                        .param("dueDate", task.getDueDate().toString())
                        .param("assigneeId", ownerMembership.getId().toString())
                        .param("sortOrder", String.valueOf(task.getSortOrder()))
                        .param("lockVersion", String.valueOf(task.getLockVersion())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + project.getId() + "/tasks/" + task.getId()));

        mockMvc.perform(get("/projects/{projectId}/milestones/new", project.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/milestones/form"))
                .andExpect(model().attributeExists("milestoneForm", "sections"));

        mockMvc.perform(post("/projects/{projectId}/milestones", project.getId())
                        .session(session).with(csrf())
                        .param("title", "Abnahme")
                        .param("description", "Gemeinsame Prüfung")
                        .param("planSectionId", section.getId().toString())
                        .param("dueDate", "2026-08-21"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + project.getId() + "/plan"));

        Milestone milestone = milestoneRepository.findAll().stream()
                .filter(candidate -> candidate.getPlanContainer().getId().equals(project.getId()))
                .findFirst().orElseThrow();
        MvcResult milestoneEdit = mockMvc.perform(get(
                        "/projects/{projectId}/milestones/{milestoneId}/edit", project.getId(), milestone.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/milestones/form"))
                .andReturn();
        MilestoneForm milestoneForm = (MilestoneForm) milestoneEdit.getModelAndView().getModel().get("milestoneForm");
        assertThat(milestoneForm.getTitle()).isEqualTo("Abnahme");
        assertThat(milestoneForm.getDescription()).isEqualTo("Gemeinsame Prüfung");
        assertThat(milestoneForm.getPlanSectionId()).isEqualTo(section.getId());
        assertThat(milestoneForm.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 21));

        mockMvc.perform(post("/projects/{projectId}/milestones/{milestoneId}",
                                project.getId(), milestone.getId())
                        .session(session).with(csrf())
                        .param("title", "")
                        .param("description", "Nicht verlieren"))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/milestones/form"))
                .andExpect(model().attributeHasFieldErrors("milestoneForm", "title"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nicht verlieren")));

        mockMvc.perform(post("/projects/{projectId}/milestones/{milestoneId}",
                                project.getId(), milestone.getId())
                        .session(session).with(csrf())
                        .param("title", "Abnahme aktualisiert")
                        .param("description", milestone.getDescription())
                        .param("planSectionId", section.getId().toString())
                        .param("dueDate", milestone.getDueDate().toString())
                        .param("sortOrder", String.valueOf(milestone.getSortOrder()))
                        .param("lockVersion", String.valueOf(milestone.getLockVersion())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + project.getId() + "/plan"));
    }

    @Test
    void subordinateIdsAreAlwaysRestrictedToTheProjectFromTheUrl() throws Exception {
        User owner = saveUser("foreign-child-owner@example.org");
        Project firstProject = saveProject("Erstes Projekt", owner);
        Project secondProject = saveProject("Zweites Projekt", owner);
        SectionDto firstSection = createSection(firstProject, owner, "Erste Section");
        SectionDto secondSection = createSection(secondProject, owner, "Zweite Section");
        Task firstTask = createTask(firstProject, owner, firstSection.getId(), "Erste Aufgabe");
        Task secondTask = createTask(secondProject, owner, secondSection.getId(), "Zweite Aufgabe");
        Milestone firstMilestone = createMilestone(firstProject, owner, firstSection.getId(), "Erster Meilenstein");
        MockHttpSession session = login(owner.getEmail());

        mockMvc.perform(get("/projects/{projectId}/tasks/{taskId}", secondProject.getId(), firstTask.getId())
                        .session(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/projects/{projectId}/milestones/{milestoneId}/edit",
                        secondProject.getId(), firstMilestone.getId()).session(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/projects/{projectId}/sections/{sectionId}",
                                secondProject.getId(), firstSection.getId())
                        .session(session).with(csrf())
                        .param("title", "Manipulation"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/projects/{projectId}/tasks/{taskId}/dependencies",
                                firstProject.getId(), firstTask.getId())
                        .session(session).with(csrf())
                        .param("prerequisiteTaskId", secondTask.getId().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void dependenciesAreAddedAndRemovedOnTaskDetailAndCyclesAreRejected() throws Exception {
        User owner = saveUser("dependency-ui-owner@example.org");
        Project project = saveProject("Abhängigkeiten", owner);
        SectionDto section = createSection(project, owner, "Section");
        Task prerequisite = createTask(project, owner, section.getId(), "Vorbereitung");
        Task successor = createTask(project, owner, section.getId(), "Umsetzung");
        MockHttpSession session = login(owner.getEmail());

        mockMvc.perform(post("/projects/{projectId}/tasks/{taskId}/dependencies",
                                project.getId(), successor.getId())
                        .session(session).with(csrf())
                        .param("prerequisiteTaskId", prerequisite.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + project.getId() + "/tasks/" + successor.getId()));

        mockMvc.perform(get("/projects/{projectId}/tasks/{taskId}", project.getId(), successor.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/tasks/detail"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Vorbereitung")));

        mockMvc.perform(post("/projects/{projectId}/tasks/{taskId}/dependencies",
                                project.getId(), prerequisite.getId())
                        .session(session).with(csrf())
                        .param("prerequisiteTaskId", successor.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/tasks/detail"))
                .andExpect(model().attribute("errorMessage",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.blankOrNullString())));
        assertThat(taskService.getTaskDetail(project.getId(), prerequisite.getId(), owner.getId()).getPredecessors())
                .isEmpty();

        mockMvc.perform(post("/projects/{projectId}/tasks/{taskId}/dependencies/{prerequisiteId}/remove",
                                project.getId(), successor.getId(), prerequisite.getId())
                        .session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + project.getId() + "/tasks/" + successor.getId()));
        assertThat(taskService.getTaskDetail(project.getId(), successor.getId(), owner.getId()).getPredecessors())
                .isEmpty();
    }

    @Test
    void memberManagementIsOwnerOnlyActiveAndRemovalClearsTaskAssignments() throws Exception {
        User owner = saveUser("members-ui-owner@example.org");
        User member = saveUser("members-ui-member@example.org");
        Project project = saveProject("Mitgliederseite", owner);
        MockHttpSession ownerSession = login(owner.getEmail());
        MockHttpSession memberSession = login(member.getEmail());

        mockMvc.perform(get("/projects/{projectId}/members", project.getId()).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/members"))
                .andExpect(model().attributeExists("memberForm", "members"));

        mockMvc.perform(post("/projects/{projectId}/members", project.getId())
                        .session(ownerSession).with(csrf())
                        .param("email", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/members"))
                .andExpect(model().attributeHasFieldErrors("memberForm", "email"));

        mockMvc.perform(post("/projects/{projectId}/members", project.getId())
                        .session(ownerSession).with(csrf())
                        .param("email", member.getEmail()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + project.getId() + "/members"));
        ProjectMember membership = projectMemberRepository
                .findByProjectIdAndUserId(project.getId(), member.getId()).orElseThrow();

        mockMvc.perform(get("/projects/{projectId}/members", project.getId()).session(memberSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/projects/{projectId}/members", project.getId())
                        .session(ownerSession).with(csrf())
                        .param("email", member.getEmail()))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/members"))
                .andExpect(model().attribute("errorMessage",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.blankOrNullString())));

        Task assignedTask = createTask(project, owner, null, "Zugewiesene Aufgabe");
        assignedTask.setAssignee(membership);
        taskRepository.saveAndFlush(assignedTask);
        mockMvc.perform(post("/projects/{projectId}/members/{memberId}/remove",
                                project.getId(), membership.getId())
                        .session(ownerSession).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + project.getId() + "/members"));
        assertThat(projectMemberRepository.findById(membership.getId()).orElseThrow().isActive()).isFalse();
        assertThat(taskRepository.findById(assignedTask.getId()).orElseThrow().getAssignee()).isNull();

        project.setLocation(ProjectLocation.ARCHIVE);
        projectRepository.saveAndFlush(project);
        mockMvc.perform(get("/projects/{projectId}/members", project.getId()).session(ownerSession))
                .andExpect(status().isConflict());
    }

    @Test
    void sectionsAreCreatedAndEditedOnPlanWithOnlyTitleAndDescription() throws Exception {
        User owner = saveUser("section-ui-owner@example.org");
        Project project = saveProject("Bereiche direkt", owner);
        MockHttpSession session = login(owner.getEmail());

        mockMvc.perform(post("/projects/{projectId}/sections", project.getId())
                        .session(session).with(csrf())
                        .param("title", "")
                        .param("description", "Eingabe bleibt erhalten"))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/plan"))
                .andExpect(model().attributeHasFieldErrors("sectionForm", "title"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Eingabe bleibt erhalten")));

        mockMvc.perform(post("/projects/{projectId}/sections", project.getId())
                        .session(session).with(csrf())
                        .param("title", "Planung")
                        .param("description", "Direkt im Plan"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + project.getId() + "/plan"));
        var section = sectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(project.getId()).getFirst();
        assertThat(section.getTitle()).isEqualTo("Planung");
        assertThat(section.getDescription()).isEqualTo("Direkt im Plan");

        mockMvc.perform(post("/projects/{projectId}/sections/{sectionId}", project.getId(), section.getId())
                        .session(session).with(csrf())
                        .param("title", "")
                        .param("description", "Ungültige Bearbeitung bleibt sichtbar")
                        .param("lockVersion", String.valueOf(section.getLockVersion())))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/plan"))
                .andExpect(model().attributeHasFieldErrors("sectionForm", "title"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Ungültige Bearbeitung bleibt sichtbar")));

        mockMvc.perform(post("/projects/{projectId}/sections/{sectionId}", project.getId(), section.getId())
                        .session(session).with(csrf())
                        .param("title", "Planung aktualisiert")
                        .param("description", "")
                        .param("sortOrder", String.valueOf(section.getSortOrder()))
                        .param("lockVersion", String.valueOf(section.getLockVersion())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + project.getId() + "/plan"));
        var updated = sectionRepository.findById(section.getId()).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("Planung aktualisiert");
        assertThat(updated.getDescription()).isNull();
    }


    @Test
    void soloProjectHidesGroupFeaturesRejectsAssignmentsAndCanBecomeAGroup() throws Exception {
        var owner = saveUser("solo-ui-owner@example.org");
        var project = saveProject("Solo", owner);
        project.setCollaborationMode(de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode.INDIVIDUAL);
        project.setCategory(de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory.EDUCATION);
        projectRepository.saveAndFlush(project);
        var task = createTask(project, owner, null, "Meine Aufgabe");
        var membership = projectMemberRepository.findByProjectIdAndUserId(project.getId(), owner.getId()).orElseThrow();
        var session = login(owner.getEmail());
        mockMvc.perform(get("/projects/{id}/plan", project.getId()).session(session))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Mitglieder verwalten"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Projektdetails bearbeiten")));
        for (String path : new String[] {"/tasks/new", "/tasks/" + task.getId() + "/edit", "/tasks/" + task.getId()}) {
            mockMvc.perform(get("/projects/" + project.getId() + path).session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Zuständigkeit"))));
        }
        mockMvc.perform(get("/projects/{id}/members", project.getId()).session(session)).andExpect(status().isNotFound());
        mockMvc.perform(post("/projects/{id}/members", project.getId()).session(session).with(csrf())
                        .param("email", owner.getEmail())).andExpect(status().isNotFound());
        mockMvc.perform(post("/projects/{id}/tasks", project.getId()).session(session).with(csrf())
                        .param("title", "Manipuliert").param("priority", "MEDIUM")
                        .param("assigneeId", membership.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("errorMessage"));
        mockMvc.perform(post("/projects/{id}/tasks/{taskId}", project.getId(), task.getId()).session(session).with(csrf())
                        .param("title", "Manipuliert").param("priority", "MEDIUM")
                        .param("assigneeId", membership.getId().toString()))
                .andExpect(model().attributeExists("errorMessage"));
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getAssignee()).isNull();
        assertThat(taskRepository.findPlanTasks(project.getId())).hasSize(1);

        mockMvc.perform(get("/projects/{id}/edit", project.getId()).session(session))
                .andExpect(status().isOk()).andExpect(model().attribute("projectForm",
                        org.hamcrest.Matchers.hasProperty("collaborationMode",
                                org.hamcrest.Matchers.is(de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode.INDIVIDUAL))));
        mockMvc.perform(post("/projects/{id}/edit", project.getId()).session(session).with(csrf())
                        .param("title", "Jetzt gemeinsam").param("category", "EDUCATION").param("collaborationMode", "BOTH"))
                .andExpect(model().attributeHasFieldErrors("projectForm", "projectCollaborationModeValid"));
        mockMvc.perform(post("/projects/{id}/edit", project.getId()).session(session).with(csrf())
                        .param("title", "Jetzt gemeinsam").param("category", "EDUCATION").param("collaborationMode", "GROUP"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/projects/{id}/members", project.getId()).session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/projects/{id}/tasks/new", project.getId()).session(session))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Zuständigkeit")));
    }

    @Test
    void conversionFormRequiresConfirmationAndRemovesFormerMemberAccess() throws Exception {
        var owner = saveUser("convert-ui-owner@example.org");
        var member = saveUser("convert-ui-member@example.org");
        var project = saveProject("Gemeinsames Projekt", owner);
        project.setCategory(de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory.EDUCATION);
        projectRepository.saveAndFlush(project);
        var ownerSession = login(owner.getEmail());
        var memberSession = login(member.getEmail());
        mockMvc.perform(post("/projects/{id}/members", project.getId()).session(ownerSession).with(csrf())
                        .param("email", member.getEmail())).andExpect(status().is3xxRedirection());
        var task = createTask(project, owner, null, "Bleibt erhalten");
        var membership = projectMemberRepository.findByProjectIdAndUserId(project.getId(), member.getId()).orElseThrow();
        task.setAssignee(membership);
        taskRepository.saveAndFlush(task);
        mockMvc.perform(post("/projects/{id}/edit", project.getId()).session(memberSession).with(csrf())
                        .param("title", "Solo").param("category", "EDUCATION")
                        .param("collaborationMode", "INDIVIDUAL").param("confirmIndividualConversion", "true"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/projects/{id}/edit", project.getId()).session(ownerSession).with(csrf())
                        .param("title", "Solo").param("category", "EDUCATION").param("collaborationMode", "INDIVIDUAL"))
                .andExpect(status().isOk()).andExpect(view().name("projects/edit"))
                .andExpect(model().attributeHasErrors("projectForm"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bitte bestätige")));
        assertThat(projectRepository.findById(project.getId()).orElseThrow().isGroupProject()).isTrue();
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getAssignee()).isNotNull();

        mockMvc.perform(post("/projects/{id}/edit", project.getId()).session(ownerSession).with(csrf())
                        .param("title", "Solo").param("category", "EDUCATION")
                        .param("collaborationMode", "INDIVIDUAL").param("confirmIndividualConversion", "true"))
                .andExpect(status().is3xxRedirection());
        assertThat(projectMemberRepository.findById(membership.getId())).isEmpty();
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getAssignee()).isNull();
        mockMvc.perform(get("/projects/{id}/plan", project.getId()).session(memberSession)).andExpect(status().isNotFound());
        mockMvc.perform(get("/projects/{id}/tasks/{taskId}", project.getId(), task.getId()).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Zuständigkeit"))));
    }

    private User saveUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(email.substring(0, email.indexOf('@')));
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setEnabled(true);
        return userRepository.saveAndFlush(user);
    }

    private Project saveProject(String title, User owner) {
        Project project = new Project();
        project.setTitle(title);
        project.setCollaborationMode(de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode.GROUP);
        project.setCreationType(CreationType.EMPTY);
        project.setStatus(ProjectStatus.ACTIVE);
        project.setLocation(ProjectLocation.OVERVIEW);
        ProjectMember ownerMembership = new ProjectMember();
        ownerMembership.setUser(owner);
        ownerMembership.setRole(ProjectMemberRole.OWNER);
        ownerMembership.setActive(true);
        project.addMembership(ownerMembership);
        return projectRepository.saveAndFlush(project);
    }

    private SectionDto createSection(Project project, User owner, String title) {
        SectionForm form = new SectionForm();
        form.setTitle(title);
        return sectionService.createSection(project.getId(), form, owner.getId());
    }

    private Task createTask(Project project, User owner, UUID sectionId, String title) {
        TaskForm form = new TaskForm();
        form.setTitle(title);
        form.setPriority(TaskPriority.MEDIUM);
        form.setPlanSectionId(sectionId);
        UUID taskId = taskService.createTask(project.getId(), form, owner.getId()).getId();
        return taskRepository.findById(taskId).orElseThrow();
    }

    private Milestone createMilestone(Project project, User owner, UUID sectionId, String title) {
        MilestoneForm form = new MilestoneForm();
        form.setTitle(title);
        form.setPlanSectionId(sectionId);
        UUID milestoneId = milestoneService.createMilestone(project.getId(), form, owner.getId()).getId();
        return milestoneRepository.findById(milestoneId).orElseThrow();
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("email", email)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
