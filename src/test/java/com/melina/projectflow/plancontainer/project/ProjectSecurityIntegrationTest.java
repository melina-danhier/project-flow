package com.melina.projectflow.plancontainer.project;

import com.melina.projectflow.common.exception.ConflictException;
import com.melina.projectflow.common.exception.ForbiddenOperationException;
import com.melina.projectflow.common.exception.ResourceNotFoundException;
import com.melina.projectflow.generation.model.PlanDraft;
import com.melina.projectflow.generation.repository.PlanDraftRepository;
import com.melina.projectflow.plancontainer.project.dto.ProjectCreateForm;
import com.melina.projectflow.plancontainer.project.dto.ProjectUpdateForm;
import com.melina.projectflow.plancontainer.project.mapper.ProjectMapperImpl;
import com.melina.projectflow.plancontainer.project.model.CreationType;
import com.melina.projectflow.plancontainer.project.model.Project;
import com.melina.projectflow.plancontainer.project.model.ProjectMember;
import com.melina.projectflow.plancontainer.project.model.ProjectMemberRole;
import com.melina.projectflow.plancontainer.project.model.ProjectStatus;
import com.melina.projectflow.plancontainer.project.repository.ProjectMemberRepository;
import com.melina.projectflow.plancontainer.project.repository.ProjectRepository;
import com.melina.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import com.melina.projectflow.plancontainer.project.service.ProjectMembershipService;
import com.melina.projectflow.plancontainer.project.service.ProjectService;
import com.melina.projectflow.plancontainer.template.model.CollaborationMode;
import com.melina.projectflow.plancontainer.template.model.Template;
import com.melina.projectflow.plancontainer.template.model.TemplateCategory;
import com.melina.projectflow.plancontainer.template.repository.TemplateRepository;
import com.melina.projectflow.planelement.mapper.PlanElementMapperImpl;
import com.melina.projectflow.planelement.model.ElementOrigin;
import com.melina.projectflow.planelement.model.Task;
import com.melina.projectflow.planelement.model.TaskStatus;
import com.melina.projectflow.planelement.model.PlanSection;
import com.melina.projectflow.planelement.repository.TaskRepository;
import com.melina.projectflow.planelement.service.PlanElementService;
import com.melina.projectflow.user.model.User;
import com.melina.projectflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@ActiveProfiles("test")
@Import({
        ProjectAuthorizationService.class,
        ProjectMembershipService.class,
        ProjectService.class,
        ProjectMapperImpl.class,
        PlanElementService.class,
        PlanElementMapperImpl.class
})
class ProjectSecurityIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private PlanDraftRepository planDraftRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private ProjectAuthorizationService authorizationService;

    @Autowired
    private ProjectMembershipService membershipService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private PlanElementService planElementService;

    @Test
    void projectCreationCreatesExactlyOneActiveOwner() {
        User owner = saveUser("create-owner@example.org");
        ProjectCreateForm form = new ProjectCreateForm();
        form.setTitle(" Neues Projekt ");
        form.setCreationType(CreationType.EMPTY);

        UUID projectId = projectService.createProject(form, owner.getId()).getId();

        List<ProjectMember> memberships = projectRepository.findById(projectId).orElseThrow()
                .getMemberships().stream().toList();
        assertThat(memberships).singleElement().satisfies(membership -> {
            assertThat(membership.getUser().getId()).isEqualTo(owner.getId());
            assertThat(membership.getRole()).isEqualTo(ProjectMemberRole.OWNER);
            assertThat(membership.isActive()).isTrue();
        });
    }

    @Test
    void templateCreationCopiesContentsWithoutChangingTemplate() {
        User owner = saveUser("template-owner@example.org");
        Template template = new Template();
        template.setTitle("Studienprojekt");
        template.setDescription("Unveränderte Vorlage");
        template.setCategory(TemplateCategory.EDUCATION);
        template.setProjectType("Präsentation");
        template.setCollaborationMode(CollaborationMode.BOTH);
        PlanSection sourceSection = new PlanSection();
        sourceSection.setTitle("Vorbereitung");
        sourceSection.setOrigin(ElementOrigin.TEMPLATE);
        template.addSection(sourceSection);
        Task sourceFirst = templateTask("Thema wählen", 0);
        Task sourceSecond = templateTask("Folien erstellen", 1);
        sourceSecond.addPrerequisite(sourceFirst);
        template.addElement(sourceFirst);
        template.addElement(sourceSecond);
        sourceSection.addElement(sourceFirst);
        sourceSection.addElement(sourceSecond);
        templateRepository.saveAndFlush(template);

        ProjectCreateForm form = new ProjectCreateForm();
        form.setTitle("Meine Präsentation");
        form.setCreationType(CreationType.TEMPLATE);
        UUID projectId = projectService.createProjectFromTemplate(template.getId(), form, owner.getId()).getId();

        Project copy = projectRepository.findById(projectId).orElseThrow();
        assertThat(copy.getSections()).singleElement()
                .extracting(PlanSection::getTitle).isEqualTo("Vorbereitung");
        assertThat(copy.getElements()).hasSize(2)
                .allSatisfy(element -> {
                    assertThat(element.getId()).isNotIn(sourceFirst.getId(), sourceSecond.getId());
                    assertThat(element.getOrigin()).isEqualTo(ElementOrigin.TEMPLATE);
                });
        Task copiedSecond = copy.getElements().stream()
                .filter(element -> element.getTitle().equals("Folien erstellen"))
                .map(Task.class::cast)
                .findFirst().orElseThrow();
        assertThat(copiedSecond.getPrerequisites()).singleElement()
                .extracting(Task::getTitle).isEqualTo("Thema wählen");

        Template unchanged = templateRepository.findById(template.getId()).orElseThrow();
        assertThat(unchanged.getDescription()).isEqualTo("Unveränderte Vorlage");
        assertThat(unchanged.getSections()).hasSize(1);
        assertThat(unchanged.getElements()).hasSize(2);
    }

    @Test
    void authorizationDistinguishesOwnerMemberOutsiderAndInactiveMember() {
        User owner = saveUser("auth-owner@example.org");
        User member = saveUser("auth-member@example.org");
        User inactive = saveUser("auth-inactive@example.org");
        User outsider = saveUser("auth-outsider@example.org");
        Project project = saveProject("Autorisierung", owner);
        addMembership(project, member, ProjectMemberRole.MEMBER, true);
        addMembership(project, inactive, ProjectMemberRole.MEMBER, false);

        assertThat(authorizationService.requireOwner(project.getId(), owner.getId()).getRole())
                .isEqualTo(ProjectMemberRole.OWNER);
        assertThat(authorizationService.requireMember(project.getId(), member.getId()).isActive()).isTrue();
        assertThatThrownBy(() -> authorizationService.requireOwner(project.getId(), member.getId()))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> authorizationService.requireMember(project.getId(), outsider.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> authorizationService.requireMember(project.getId(), inactive.getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        ProjectUpdateForm update = new ProjectUpdateForm();
        update.setTitle("Verbotene Änderung");
        assertThatThrownBy(() -> projectService.updateProject(project.getId(), update, member.getId()))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> membershipService.addMember(
                project.getId(), outsider.getEmail(), member.getId()))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void onlyOwnerCanAccessDraftAndIdsFromAnotherProjectAreRejected() {
        User owner = saveUser("draft-owner@example.org");
        User member = saveUser("draft-member@example.org");
        Project project = saveProject("KI-Projekt", owner);
        addMembership(project, member, ProjectMemberRole.MEMBER, true);
        PlanDraft draft = new PlanDraft();
        draft.setProject(project);
        draft.setPromptVersion("prompt-v1");
        draft.setSchemaVersion("schema-v1");
        planDraftRepository.saveAndFlush(draft);

        assertThat(authorizationService.requireDraftOwner(draft.getId(), owner.getId()).getId())
                .isEqualTo(draft.getId());
        assertThatThrownBy(() -> authorizationService.requireDraftOwner(draft.getId(), member.getId()))
                .isInstanceOf(ForbiddenOperationException.class);

        Project otherProject = saveProject("Fremdes Projekt", owner);
        Task otherTask = saveTask(otherProject, "Fremde Aufgabe", TaskStatus.OPEN, null);
        assertThatThrownBy(() -> authorizationService.requirePlanElement(
                project.getId(), otherTask.getId(), owner.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addsMemberRejectsDuplicateAndReactivatesSameRecord() {
        User owner = saveUser("member-owner@example.org");
        User member = saveUser("member-target@example.org");
        Project project = saveProject("Mitglieder", owner);

        ProjectMember added = membershipService.addMember(
                project.getId(), " MEMBER-TARGET@Example.ORG ", owner.getId());
        UUID membershipId = added.getId();
        assertThat(added.getRole()).isEqualTo(ProjectMemberRole.MEMBER);
        assertThat(added.isActive()).isTrue();

        assertThatThrownBy(() -> membershipService.addMember(
                project.getId(), member.getEmail(), owner.getId()))
                .isInstanceOf(ConflictException.class);

        membershipService.removeMember(project.getId(), member.getId(), owner.getId());
        ProjectMember reactivated = membershipService.addMember(
                project.getId(), member.getEmail(), owner.getId());
        assertThat(reactivated.getId()).isEqualTo(membershipId);
        assertThat(reactivated.isActive()).isTrue();
    }

    @Test
    void enforcesLimitOfTenActiveMembersIncludingOwner() {
        User owner = saveUser("limit-owner@example.org");
        Project project = saveProject("Limit", owner);
        for (int index = 1; index <= 9; index++) {
            User member = saveUser("limit-member-" + index + "@example.org");
            membershipService.addMember(project.getId(), member.getEmail(), owner.getId());
        }
        User eleventh = saveUser("limit-member-10@example.org");

        assertThat(projectMemberRepository.countByProjectIdAndActiveTrue(project.getId())).isEqualTo(10);
        assertThatThrownBy(() -> membershipService.addMember(
                project.getId(), eleventh.getEmail(), owner.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("höchstens zehn");
    }

    @Test
    void ownerCannotBeRemovedOrLeave() {
        User owner = saveUser("fixed-owner@example.org");
        Project project = saveProject("Owner bleibt", owner);

        assertThatThrownBy(() -> membershipService.removeMember(
                project.getId(), owner.getId(), owner.getId()))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> membershipService.leaveProject(project.getId(), owner.getId()))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThat(projectMemberRepository.findByProjectIdAndUserId(project.getId(), owner.getId()))
                .get().extracting(ProjectMember::isActive).isEqualTo(true);
    }

    @Test
    void removingMemberClearsAllAssignmentsWithoutChangingTaskState() {
        User owner = saveUser("remove-owner@example.org");
        User member = saveUser("remove-member@example.org");
        Project project = saveProject("Freigabe", owner);
        ProjectMember membership = addMembership(project, member, ProjectMemberRole.MEMBER, true);
        Task openTask = saveTask(project, "Offen", TaskStatus.OPEN, membership);
        Task completedTask = saveTask(project, "Erledigt", TaskStatus.COMPLETED, membership);
        Instant completedAt = completedTask.getCompletedAt();

        membershipService.removeMember(project.getId(), member.getId(), owner.getId());

        Task reloadedOpen = taskRepository.findById(openTask.getId()).orElseThrow();
        Task reloadedCompleted = taskRepository.findById(completedTask.getId()).orElseThrow();
        assertThat(reloadedOpen.getAssignee()).isNull();
        assertThat(reloadedOpen.getStatus()).isEqualTo(TaskStatus.OPEN);
        assertThat(reloadedCompleted.getAssignee()).isNull();
        assertThat(reloadedCompleted.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(reloadedCompleted.getCompletedAt())
                .isCloseTo(completedAt, within(1, ChronoUnit.MICROS));
        assertThat(projectMemberRepository.findById(membership.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void activeMemberCanAssignOnlyTasksAndMembersFromSameProject() {
        User owner = saveUser("assign-owner@example.org");
        User member = saveUser("assign-member@example.org");
        User inactive = saveUser("assign-inactive@example.org");
        Project project = saveProject("Zuweisung", owner);
        addMembership(project, member, ProjectMemberRole.MEMBER, true);
        addMembership(project, inactive, ProjectMemberRole.MEMBER, false);
        Task task = saveTask(project, "Zuweisen", TaskStatus.OPEN, null);

        planElementService.assignTask(project.getId(), task.getId(), member.getId(), member.getId());
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getAssignee().getUser().getId())
                .isEqualTo(member.getId());
        assertThatThrownBy(() -> planElementService.assignTask(
                project.getId(), task.getId(), inactive.getId(), member.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private User saveUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("Test User");
        user.setPasswordHash("$2a$12$test-hash");
        return userRepository.saveAndFlush(user);
    }

    private Project saveProject(String title, User owner) {
        Project project = new Project();
        project.setTitle(title);
        project.setCreationType(CreationType.EMPTY);
        project.setStatus(ProjectStatus.ACTIVE);
        ProjectMember ownerMembership = new ProjectMember();
        ownerMembership.setUser(owner);
        ownerMembership.setRole(ProjectMemberRole.OWNER);
        ownerMembership.setActive(true);
        project.addMembership(ownerMembership);
        return projectRepository.saveAndFlush(project);
    }

    private ProjectMember addMembership(
            Project project,
            User user,
            ProjectMemberRole role,
            boolean active
    ) {
        ProjectMember membership = new ProjectMember();
        membership.setProject(project);
        membership.setUser(user);
        membership.setRole(role);
        membership.setActive(active);
        return projectMemberRepository.saveAndFlush(membership);
    }

    private Task saveTask(Project project, String title, TaskStatus status, ProjectMember assignee) {
        Task task = new Task();
        task.setTitle(title);
        task.setOrigin(ElementOrigin.USER);
        task.setStatus(status);
        task.setAssignee(assignee);
        task.setPlanContainer(project);
        return taskRepository.saveAndFlush(task);
    }

    private Task templateTask(String title, int sortOrder) {
        Task task = new Task();
        task.setTitle(title);
        task.setSortOrder(sortOrder);
        task.setOrigin(ElementOrigin.TEMPLATE);
        return task;
    }
}
