package de.melinadanhier.projectflow.planelement;

import de.melinadanhier.projectflow.common.exception.ForbiddenOperationException;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMemberRole;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectMemberRepository;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.planelement.dto.TaskCommentForm;
import de.melinadanhier.projectflow.planelement.dto.TaskForm;
import de.melinadanhier.projectflow.planelement.dto.DeleteSectionForm;
import de.melinadanhier.projectflow.planelement.dto.SectionDeletionMode;
import de.melinadanhier.projectflow.planelement.dto.SectionForm;
import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import de.melinadanhier.projectflow.planelement.repository.TaskCommentRepository;
import de.melinadanhier.projectflow.planelement.repository.TaskRepository;
import de.melinadanhier.projectflow.planelement.service.TaskCommentService;
import de.melinadanhier.projectflow.planelement.service.TaskService;
import de.melinadanhier.projectflow.planelement.service.SectionService;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
class TaskCommentIntegrationTest {

    private static final String PASSWORD = "richtiges-passwort";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskCommentRepository taskCommentRepository;
    @Autowired private TaskService taskService;
    @Autowired private TaskCommentService taskCommentService;
    @Autowired private SectionService sectionService;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void groupMembersAddCommentsAndCanDeleteOnlyTheirOwn() throws Exception {
        User owner = saveUser("comment-owner@example.org", "Eigentümerin");
        User member = saveUser("comment-member@example.org", "Mitglied");
        Project project = saveProject("Kommentiertes Projekt", owner, CollaborationMode.GROUP);
        addMember(project, member);
        Task task = saveTask(project, owner, "Abstimmung");
        MockHttpSession ownerSession = login(owner.getEmail());
        MockHttpSession memberSession = login(member.getEmail());

        mockMvc.perform(post("/projects/{projectId}/tasks/{taskId}/comments", project.getId(), task.getId())
                        .session(memberSession).with(csrf()).param("content", "Ist der Termin schon bestätigt?"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + project.getId() + "/tasks/" + task.getId()));

        var comment = taskCommentRepository.findAllForTask(project.getId(), task.getId()).getFirst();
        assertThat(comment.getAuthor().getUser().getId()).isEqualTo(member.getId());
        assertThat(comment.getCreatedAt()).isNotNull();
        mockMvc.perform(get("/projects/{projectId}/tasks/{taskId}", project.getId(), task.getId())
                        .session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Kommentare")))
                .andExpect(content().string(containsString("Ist der Termin schon bestätigt?")))
                .andExpect(content().string(containsString("Mitglied")));

        assertThatThrownBy(() -> taskCommentService.deleteOwnComment(
                project.getId(), task.getId(), comment.getId(), owner.getId()))
                .isInstanceOf(ForbiddenOperationException.class);
        taskCommentService.deleteOwnComment(project.getId(), task.getId(), comment.getId(), member.getId());
        assertThat(taskCommentRepository.findAllForTask(project.getId(), task.getId())).isEmpty();
    }

    @Test
    void individualProjectShowsTheSameFeatureAsNotesAndValidatesEmptyContent() throws Exception {
        User owner = saveUser("note-owner@example.org", "Allein arbeitend");
        Project project = saveProject("Einzelprojekt", owner, CollaborationMode.INDIVIDUAL);
        Task task = saveTask(project, owner, "Eigene Aufgabe");
        MockHttpSession session = login(owner.getEmail());

        mockMvc.perform(post("/projects/{projectId}/tasks/{taskId}/comments", project.getId(), task.getId())
                        .session(session).with(csrf()).param("content", "Material im Keller prüfen"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/projects/{projectId}/tasks/{taskId}", project.getId(), task.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Notizen")))
                .andExpect(content().string(containsString("Material im Keller prüfen")))
                .andExpect(content().string(not(containsString("<h2 id=\"task-comments-heading\">Kommentare</h2>"))));

        mockMvc.perform(post("/projects/{projectId}/tasks/{taskId}/comments", project.getId(), task.getId())
                        .session(session).with(csrf()).param("content", "  "))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/tasks/detail"))
                .andExpect(model().attributeHasFieldErrors("commentForm", "content"));
    }

    @Test
    void deletingOtherMembershipRemovesItsCommentsButKeepsOwnerNotes() {
        User owner = saveUser("conversion-comment-owner@example.org", "Eigentümerin");
        User member = saveUser("conversion-comment-member@example.org", "Mitglied");
        Project project = saveProject("Noch Gruppe", owner, CollaborationMode.GROUP);
        ProjectMember memberMembership = addMember(project, member);
        Task task = saveTask(project, owner, "Gemeinsame Aufgabe");
        addComment(project, task, owner, "Eigene Information");
        addComment(project, task, member, "Information des Mitglieds");

        projectMemberRepository.delete(memberMembership);
        projectMemberRepository.flush();

        assertThat(projectMemberRepository.findById(memberMembership.getId())).isEmpty();
        assertThat(taskCommentRepository.findAllForTask(project.getId(), task.getId()))
                .singleElement().extracting(comment -> comment.getContent()).isEqualTo("Eigene Information");
    }

    @Test
    void deletingTaskAlsoDeletesItsComments() {
        User owner = saveUser("delete-comment-owner@example.org", "Eigentümerin");
        Project project = saveProject("Aufräumen", owner, CollaborationMode.INDIVIDUAL);
        Task task = saveTask(project, owner, "Zu löschende Aufgabe");
        addComment(project, task, owner, "Wird mit der Aufgabe entfernt");

        taskService.deleteTask(project.getId(), task.getId(), owner.getId());

        assertThat(taskRepository.findById(task.getId())).isEmpty();
        assertThat(taskCommentRepository.findAllForTask(project.getId(), task.getId())).isEmpty();
    }

    @Test
    void deletingSectionContentsAlsoDeletesTaskComments() {
        User owner = saveUser("delete-section-comment-owner@example.org", "Eigentümerin");
        Project project = saveProject("Section aufräumen", owner, CollaborationMode.INDIVIDUAL);
        SectionForm sectionForm = new SectionForm();
        sectionForm.setTitle("Zu löschende Section");
        UUID sectionId = sectionService.createSection(project.getId(), sectionForm, owner.getId()).getId();
        Task task = saveTask(project, owner, sectionId, "Aufgabe in Section");
        addComment(project, task, owner, "Wird zusammen mit der Section entfernt");
        DeleteSectionForm deleteForm = new DeleteSectionForm();
        deleteForm.setMode(SectionDeletionMode.DELETE_CONTENT);

        sectionService.deleteSection(project.getId(), sectionId, deleteForm, owner.getId());

        assertThat(taskRepository.findById(task.getId())).isEmpty();
        assertThat(taskCommentRepository.findAllForTask(project.getId(), task.getId())).isEmpty();
    }

    private void addComment(Project project, Task task, User author, String content) {
        TaskCommentForm form = new TaskCommentForm();
        form.setContent(content);
        taskCommentService.addComment(project.getId(), task.getId(), form, author.getId());
    }

    private User saveUser(String email, String displayName) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setEnabled(true);
        return userRepository.saveAndFlush(user);
    }

    private Project saveProject(String title, User owner, CollaborationMode mode) {
        Project project = new Project();
        project.setTitle(title);
        project.setCollaborationMode(mode);
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

    private ProjectMember addMember(Project project, User user) {
        ProjectMember membership = new ProjectMember();
        membership.setProject(project);
        membership.setUser(user);
        membership.setRole(ProjectMemberRole.MEMBER);
        membership.setActive(true);
        return projectMemberRepository.saveAndFlush(membership);
    }

    private Task saveTask(Project project, User owner, String title) {
        return saveTask(project, owner, null, title);
    }

    private Task saveTask(Project project, User owner, UUID sectionId, String title) {
        TaskForm form = new TaskForm();
        form.setTitle(title);
        form.setPriority(TaskPriority.MEDIUM);
        form.setPlanSectionId(sectionId);
        UUID taskId = taskService.createTask(project.getId(), form, owner.getId()).getId();
        return taskRepository.findById(taskId).orElseThrow();
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("email", email).param("password", PASSWORD).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
