package de.melinadanhier.projectflow.plancontainer.project;

import de.melinadanhier.projectflow.plancontainer.project.model.ProjectSubCategory;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.common.exception.ForbiddenOperationException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.common.exception.ProjectNotEditableException;
import de.melinadanhier.projectflow.draft.model.DraftPlan;
import de.melinadanhier.projectflow.draft.repository.PlanDraftRepository;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectCreateForm;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectUpdateForm;
import de.melinadanhier.projectflow.plancontainer.project.mapper.ProjectMapperImpl;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMemberRole;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectMemberRepository;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectMembershipService;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectService;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectStateService;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.Template;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import de.melinadanhier.projectflow.plancontainer.template.repository.TemplateRepository;
import de.melinadanhier.projectflow.planelement.mapper.PlanElementMapperImpl;
import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.planelement.model.TaskStatus;
import de.melinadanhier.projectflow.planelement.model.PlanSection;
import de.melinadanhier.projectflow.planelement.repository.TaskRepository;
import de.melinadanhier.projectflow.planelement.repository.MilestoneRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import de.melinadanhier.projectflow.planelement.service.PlanElementService;
import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({
        ProjectAuthorizationService.class,
        ProjectMembershipService.class,
        ProjectService.class,
        ProjectStateService.class,
        ProjectMapperImpl.class,
        PlanElementService.class,
        PlanElementMapperImpl.class
})
class ProjectSecurityIntegrationTest {

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private PlanSectionRepository planSectionRepository;

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
    private ProjectStateService projectStateService;

    @Autowired
    private PlanElementService planElementService;

    @Test
    void projectCreationCreatesExactlyOneActiveOwner() {
        User owner = saveUser("create-owner@example.org");
        ProjectCreateForm form = new ProjectCreateForm();
        form.setTitle(" Neues Projekt ");
        form.setCreationType(CreationType.EMPTY);
        form.setCategory(TemplateCategory.HOME);
        form.setSubcategory(ProjectSubCategory.MOVING);
        form.setCollaborationMode(CollaborationMode.INDIVIDUAL);

        UUID projectId = projectService.createProject(form, owner.getId()).getId();

        List<ProjectMember> memberships = projectRepository.findById(projectId).orElseThrow()
                .getMemberships().stream().toList();
        assertThat(memberships).singleElement().satisfies(membership -> {
            assertThat(membership.getUser().getId()).isEqualTo(owner.getId());
            assertThat(membership.getRole()).isEqualTo(ProjectMemberRole.OWNER);
            assertThat(membership.isActive()).isTrue();
        });
        Project project = projectRepository.findById(projectId).orElseThrow();
        assertThat(project.getCreationType()).isEqualTo(CreationType.EMPTY);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(project.getLocation()).isEqualTo(ProjectLocation.OVERVIEW);
        assertThat(project.getCategory()).isEqualTo(TemplateCategory.HOME);
        assertThat(project.getSubcategory()).isEqualTo(ProjectSubCategory.MOVING);
        assertThat(project.getCollaborationMode()).isEqualTo(CollaborationMode.INDIVIDUAL);
        assertThat(planSectionRepository.count()).isZero();
        assertThat(taskRepository.count()).isZero();
        assertThat(milestoneRepository.count()).isZero();
    }

    @Test
    void regularProjectCreationAcceptsOnlyEmptyCreationType() {
        User owner = saveUser("blocked-creation@example.org");
        ProjectCreateForm form = new ProjectCreateForm();
        form.setTitle("Nicht direkt speichern");
        form.setCreationType(CreationType.AI);

        assertThatThrownBy(() -> projectService.createProject(form, owner.getId()))
                .isInstanceOf(DomainValidationException.class);
        form.setCreationType(CreationType.TEMPLATE);
        assertThatThrownBy(() -> projectService.createProject(form, owner.getId()))
                .isInstanceOf(DomainValidationException.class);
        assertThat(projectRepository.findAll()).isEmpty();
        assertThat(projectMemberRepository.count()).isZero();
        assertThat(planDraftRepository.count()).isZero();
    }

    @Test
    void directServiceCreationRejectsForeignSubcategoriesWithoutPersistingAnything() {
        User owner = saveUser("invalid-subcategory@example.org");
        ProjectCreateForm form = new ProjectCreateForm();
        form.setTitle("Nicht übernehmen");
        form.setCreationType(CreationType.EMPTY);
        form.setCollaborationMode(CollaborationMode.INDIVIDUAL);
        form.setSubcategory(ProjectSubCategory.MOVING);
        for (TemplateCategory category : java.util.List.of(TemplateCategory.EDUCATION, TemplateCategory.OTHER)) {
            form.setCategory(category);
            form.setOtherProjectTypeDescription("Besonderes Vorhaben");
            assertThatThrownBy(() -> projectService.createProject(form, owner.getId()))
                    .isInstanceOf(DomainValidationException.class).hasMessageContaining("Unterkategorie");
        }
        assertThat(projectRepository.count()).isZero();
        assertThat(projectMemberRepository.count()).isZero();
    }

    @Test
    void creationAndLocationEnumsContainOnlyTheSpecifiedValues() {
        assertThat(CreationType.values()).containsExactly(
                CreationType.EMPTY, CreationType.TEMPLATE, CreationType.AI);
        assertThat(ProjectStatus.values()).containsExactly(
                ProjectStatus.DRAFT, ProjectStatus.ACTIVE, ProjectStatus.COMPLETED);
        assertThat(ProjectLocation.values()).containsExactly(
                ProjectLocation.OVERVIEW, ProjectLocation.DRAFT,
                ProjectLocation.TRASH, ProjectLocation.ARCHIVE);
    }

    @Test
    void draftStatusAndLocationAreChangedOnlyAsAConsistentPair() {
        Project project = new Project();

        projectStateService.changeState(project, ProjectStatus.DRAFT, ProjectLocation.DRAFT);
        assertThat(project.isDraftStateConsistent()).isTrue();

        assertThatThrownBy(() -> projectStateService.changeState(
                project, ProjectStatus.DRAFT, ProjectLocation.OVERVIEW))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> projectStateService.changeState(
                project, ProjectStatus.ACTIVE, ProjectLocation.DRAFT))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void overviewAndDraftQueriesUseOnlyTheirLocation() {
        User owner = saveUser("location-owner@example.org");
        Project overview = saveProject("Aktiv", owner);
        Project draft = saveProject("Entwurf", owner);
        projectStateService.changeState(draft, ProjectStatus.DRAFT, ProjectLocation.DRAFT);
        projectRepository.flush();

        assertThat(projectRepository.findAllAccessibleByUserId(owner.getId()))
                .extracting(Project::getId).containsExactly(overview.getId());
        assertThat(projectRepository.findAllDraftsAccessibleByUserId(owner.getId()))
                .extracting(Project::getId).containsExactly(draft.getId());
    }

    @Test
    void draftProjectsCannotUseActiveProjectManagementServices() {
        User owner = saveUser("draft-block-owner@example.org");
        User member = saveUser("draft-block-member@example.org");
        Project draft = saveProject("Gesperrter Entwurf", owner);
        projectStateService.changeState(draft, ProjectStatus.DRAFT, ProjectLocation.DRAFT);
        projectRepository.flush();

        assertThatThrownBy(() -> authorizationService.requireEditableOwner(draft.getId(), owner.getId()))
                .isInstanceOf(ProjectNotEditableException.class);
        assertThatThrownBy(() -> membershipService.addMember(draft.getId(), member.getEmail(), owner.getId()))
                .isInstanceOf(ProjectNotEditableException.class);
        assertThatThrownBy(() -> projectService.moveToTrash(draft.getId(), owner.getId()))
                .isInstanceOf(ProjectNotEditableException.class);
    }

    @Test
    void templateCreationCopiesContentsWithoutChangingTemplate() {
        User owner = saveUser("template-owner@example.org");
        Template template = new Template();
        template.setTitle("Studienprojekt");
        template.setDescription("Unveränderte Vorlage");
        template.setCategory(TemplateCategory.EDUCATION);
        template.setSubcategory(ProjectSubCategory.PRESENTATION_OR_REPORT);
        template.setCollaborationMode(CollaborationMode.BOTH);
        PlanSection sourceSection = new PlanSection();
        sourceSection.setTitle("Vorbereitung");
        sourceSection.setOrigin(ElementOrigin.TEMPLATE);
        template.addSection(sourceSection);
        Task sourceFirst = templateTask("Thema wählen", 0);
        Task sourceSecond = templateTask("Folien erstellen", 1);
        sourceFirst.setRelativeStartDay(1);
        sourceFirst.setRelativeDueDay(2);
        sourceSecond.addPrerequisite(sourceFirst);
        template.addElement(sourceFirst);
        template.addElement(sourceSecond);
        sourceSection.addElement(sourceFirst);
        sourceSection.addElement(sourceSecond);
        templateRepository.saveAndFlush(template);

        ProjectCreateForm form = new ProjectCreateForm();
        form.setTitle("Meine Präsentation");
        form.setCategory(null);
        form.setCreationType(CreationType.TEMPLATE);
        form.setStartDate(LocalDate.of(2026, 9, 1));
        UUID projectId = projectService.createProjectFromTemplate(template.getId(), form, owner.getId()).getId();

        Project copy = projectRepository.findById(projectId).orElseThrow();
        assertThat(copy.getSections()).singleElement().satisfies(section -> {
            assertThat(section.getTitle()).isEqualTo("Vorbereitung");
        });
        assertThat(copy.getElements()).hasSize(2)
                .allSatisfy(element -> {
                    assertThat(element.getId()).isNotIn(sourceFirst.getId(), sourceSecond.getId());
                    assertThat(element.getOrigin()).isEqualTo(ElementOrigin.TEMPLATE);
                });
        Task copiedSecond = copy.getElements().stream()
                .filter(element -> element.getTitle().equals("Folien erstellen"))
                .map(Task.class::cast)
                .findFirst().orElseThrow();
        Task copiedFirst = copy.getElements().stream()
                .filter(element -> element.getTitle().startsWith("Thema"))
                .map(Task.class::cast)
                .findFirst().orElseThrow();
        assertThat(copiedFirst.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(copiedFirst.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(copiedFirst.getRelativeStartDay()).isNull();
        assertThat(copiedFirst.getRelativeDueDay()).isNull();
        assertThat(copiedSecond.getPrerequisites()).singleElement()
                .extracting(Task::getTitle).isEqualTo("Thema wählen");

        Template unchanged = templateRepository.findById(template.getId()).orElseThrow();
        assertThat(unchanged.getDescription()).isEqualTo("Unveränderte Vorlage");
        assertThat(unchanged.getSections()).hasSize(1);
        assertThat(unchanged.getElements()).hasSize(2);
        assertThat(unchanged.getSections().getFirst().getTitle()).isEqualTo("Vorbereitung");
        assertThat(((Task) unchanged.getElements().getFirst()).getRelativeStartDay()).isEqualTo(1);
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
        DraftPlan draft = new DraftPlan();
        draft.setProject(project);
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
                .isInstanceOf(ConflictException.class);
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


    @Test
    void convertingToIndividualRemovesAllOtherMembershipsAndAssignmentsButKeepsOwnerAndContents() {
        var owner = saveUser("convert-owner@example.org");
        var member = saveUser("convert-member@example.org");
        var inactive = saveUser("convert-inactive@example.org");
        var project = saveProject("Gruppenprojekt", owner);
        var ownerMembership = projectMemberRepository.findByProjectIdAndUserId(project.getId(), owner.getId()).orElseThrow();
        var membership = addMembership(project, member, ProjectMemberRole.MEMBER, true);
        var inactiveMembership = addMembership(project, inactive, ProjectMemberRole.MEMBER, false);
        var open = saveTask(project, "Offen", TaskStatus.OPEN, membership);
        var done = saveTask(project, "Erledigt", TaskStatus.COMPLETED, ownerMembership);
        var completionTime = done.getCompletedAt();
        var otherProject = saveProject("Anderes Gruppenprojekt", owner);
        var otherMembership = addMembership(otherProject, member, ProjectMemberRole.MEMBER, true);
        var otherTask = saveTask(otherProject, "Unverändert", TaskStatus.OPEN, otherMembership);

        var form = updateForm(CollaborationMode.INDIVIDUAL);
        assertThatThrownBy(() -> projectService.updateProject(project.getId(), form, owner.getId()))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("bestätige");
        assertThat(project.getCollaborationMode()).isEqualTo(CollaborationMode.GROUP);
        assertThat(taskRepository.findById(open.getId()).orElseThrow().getAssignee()).isEqualTo(membership);

        form.setConfirmIndividualConversion(true);
        projectService.updateProject(project.getId(), form, owner.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(projectRepository.findById(project.getId()).orElseThrow().getCollaborationMode())
                .isEqualTo(CollaborationMode.INDIVIDUAL);
        assertThat(projectMemberRepository.findById(membership.getId())).isEmpty();
        assertThat(projectMemberRepository.findById(inactiveMembership.getId())).isEmpty();
        assertThat(projectMemberRepository.findAllByProjectId(project.getId())).singleElement()
                .satisfies(remaining -> {
                    assertThat(remaining.getId()).isEqualTo(ownerMembership.getId());
                    assertThat(remaining.isActive()).isTrue();
                });
        assertThat(taskRepository.findPlanTasks(project.getId())).hasSize(2)
                .allSatisfy(task -> assertThat(task.getAssignee()).isNull());
        assertThat(taskRepository.findById(done.getId()).orElseThrow().getCompletedAt())
                .isCloseTo(completionTime, within(1, ChronoUnit.MICROS));
        assertThat(taskRepository.findById(done.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(taskRepository.findById(otherTask.getId()).orElseThrow().getAssignee().getId())
                .isEqualTo(otherMembership.getId());
        assertThatThrownBy(() -> authorizationService.requireMember(project.getId(), member.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(authorizationService.requireOwner(project.getId(), owner.getId())).isNotNull();

        projectService.updateProject(project.getId(), updateForm(CollaborationMode.GROUP), owner.getId());
        entityManager.flush();
        entityManager.clear();
        assertThat(projectMemberRepository.findAllByProjectId(project.getId())).hasSize(1);
        assertThat(taskRepository.findPlanTasks(project.getId())).allSatisfy(task -> assertThat(task.getAssignee()).isNull());
        assertThat(projectRepository.findById(project.getId()).orElseThrow().isGroupProject()).isTrue();
    }

    @Test
    void individualProjectsRejectMemberManagementAssignmentsAndInvalidProjectModes() {
        var owner = saveUser("solo-guard-owner@example.org");
        var other = saveUser("solo-guard-other@example.org");
        var project = saveProject("Einzelprojekt", owner);
        project.setCollaborationMode(CollaborationMode.INDIVIDUAL);
        projectRepository.flush();
        var task = saveTask(project, "Eigene Aufgabe", TaskStatus.OPEN, null);
        assertThatThrownBy(() -> membershipService.getMembersForManagement(project.getId(), owner.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> membershipService.addMember(project.getId(), other.getEmail(), owner.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> planElementService.assignTask(project.getId(), task.getId(), owner.getId(), owner.getId()))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> projectService.updateProject(project.getId(), updateForm(CollaborationMode.BOTH), owner.getId()))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> projectService.updateProject(project.getId(), updateForm(null), owner.getId()))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> projectService.updateProject(project.getId(), updateForm(CollaborationMode.GROUP), other.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(projectMemberRepository.findAllByProjectId(project.getId())).hasSize(1);
    }

    private ProjectUpdateForm updateForm(CollaborationMode mode) {
        var form = new ProjectUpdateForm();
        form.setTitle("Projekt aktualisiert");
        form.setCategory(TemplateCategory.EDUCATION);
        form.setCollaborationMode(mode);
        return form;
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
        project.setCollaborationMode(de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode.GROUP);
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
