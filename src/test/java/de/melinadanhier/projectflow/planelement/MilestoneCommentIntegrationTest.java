package de.melinadanhier.projectflow.planelement;

import de.melinadanhier.projectflow.common.exception.ForbiddenOperationException;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.membership.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.model.membership.ProjectMemberRole;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectMemberRepository;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.planelement.dto.MilestoneCommentForm;
import de.melinadanhier.projectflow.planelement.dto.MilestoneForm;
import de.melinadanhier.projectflow.planelement.model.Milestone;
import de.melinadanhier.projectflow.planelement.repository.MilestoneCommentRepository;
import de.melinadanhier.projectflow.planelement.repository.MilestoneRepository;
import de.melinadanhier.projectflow.planelement.service.MilestoneCommentService;
import de.melinadanhier.projectflow.planelement.service.MilestoneService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
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
class MilestoneCommentIntegrationTest {

    private static final String PASSWORD = "richtiges-passwort";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private MilestoneCommentRepository milestoneCommentRepository;
    @Autowired private MilestoneService milestoneService;
    @Autowired private MilestoneCommentService milestoneCommentService;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void groupMembersAddCommentsAndCanDeleteOnlyTheirOwn() throws Exception {
        User owner = saveUser("ms-comment-owner@example.org", "Eigentümerin");
        User member = saveUser("ms-comment-member@example.org", "Mitglied");
        Project project = saveProject("Kommentierter Meilenstein", owner, CollaborationMode.GROUP);
        addMember(project, member);
        Milestone milestone = saveMilestone(project, owner, "Zwischenabgabe");
        MockHttpSession ownerSession = login(owner.getEmail());
        MockHttpSession memberSession = login(member.getEmail());

        mockMvc.perform(post("/projects/{projectId}/milestones/{milestoneId}/comments",
                        project.getId(), milestone.getId())
                        .session(memberSession).with(csrf()).param("content", "Unterlagen liegen bereit."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + project.getId() + "/milestones/" + milestone.getId()));

        var comment = milestoneCommentRepository.findAllForMilestone(project.getId(), milestone.getId()).getFirst();
        assertThat(comment.getAuthor().getUser().getId()).isEqualTo(member.getId());
        assertThat(comment.getCreatedAt()).isNotNull();

        assertThatThrownBy(() -> milestoneCommentService.deleteOwnComment(
                project.getId(), milestone.getId(), comment.getId(), owner.getId()))
                .isInstanceOf(ForbiddenOperationException.class);
        milestoneCommentService.deleteOwnComment(project.getId(), milestone.getId(), comment.getId(), member.getId());
        assertThat(milestoneCommentRepository.findAllForMilestone(project.getId(), milestone.getId())).isEmpty();
    }

    @Test
    void authorCanEditOwnMilestoneComment() throws Exception {
        User owner = saveUser("ms-edit-owner@example.org", "Eigentümerin");
        Project project = saveProject("Meilenstein Notizen", owner, CollaborationMode.INDIVIDUAL);
        Milestone milestone = saveMilestone(project, owner, "Startschuss");
        MilestoneCommentForm addForm = new MilestoneCommentForm();
        addForm.setContent("Alter Stand");
        var created = milestoneCommentService.addComment(project.getId(), milestone.getId(), addForm, owner.getId());

        mockMvc.perform(post("/projects/{projectId}/milestones/{milestoneId}/comments/{commentId}",
                        project.getId(), milestone.getId(), created.id())
                        .session(login(owner.getEmail())).with(csrf())
                        .param("content", "Neuer Stand")
                        .param("lockVersion", String.valueOf(created.lockVersion())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + project.getId() + "/milestones/" + milestone.getId()));

        var updated = milestoneCommentRepository.findForMilestone(project.getId(), milestone.getId(), created.id()).orElseThrow();
        assertThat(updated.getContent()).isEqualTo("Neuer Stand");
    }

    @Test
    void deletingMilestoneAlsoDeletesItsComments() {
        User owner = saveUser("ms-delete-owner@example.org", "Eigentümerin");
        Project project = saveProject("Milestone aufräumen", owner, CollaborationMode.INDIVIDUAL);
        Milestone milestone = saveMilestone(project, owner, "Zu entfernender Meilenstein");
        MilestoneCommentForm addForm = new MilestoneCommentForm();
        addForm.setContent("Wird mit gelöscht");
        milestoneCommentService.addComment(project.getId(), milestone.getId(), addForm, owner.getId());

        milestoneService.deleteMilestone(project.getId(), milestone.getId(), owner.getId());

        assertThat(milestoneRepository.findById(milestone.getId())).isEmpty();
        assertThat(milestoneCommentRepository.findAllForMilestone(project.getId(), milestone.getId())).isEmpty();
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

    private Milestone saveMilestone(Project project, User owner, String title) {
        MilestoneForm form = new MilestoneForm();
        form.setTitle(title);
        form.setDueDate(LocalDate.now().plusDays(10));
        UUID milestoneId = milestoneService.createMilestone(project.getId(), form, owner.getId()).getId();
        return milestoneRepository.findById(milestoneId).orElseThrow();
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("email", email).param("password", PASSWORD).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
