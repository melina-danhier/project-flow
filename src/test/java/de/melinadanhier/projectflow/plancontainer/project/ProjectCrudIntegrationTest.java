package de.melinadanhier.projectflow.plancontainer.project;

import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.common.exception.ForbiddenOperationException;
import de.melinadanhier.projectflow.common.exception.ProjectNotEditableException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectPlanViewDto;
import de.melinadanhier.projectflow.plancontainer.project.mapper.ProjectMapperImpl;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMemberRole;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectMemberRepository;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectService;
import de.melinadanhier.projectflow.planelement.dto.DeleteSectionForm;
import de.melinadanhier.projectflow.planelement.dto.MilestoneForm;
import de.melinadanhier.projectflow.planelement.dto.PlanElementType;
import de.melinadanhier.projectflow.planelement.dto.PlanElementViewDto;
import de.melinadanhier.projectflow.planelement.dto.SectionDeletionMode;
import de.melinadanhier.projectflow.planelement.dto.SectionDto;
import de.melinadanhier.projectflow.planelement.dto.SectionForm;
import de.melinadanhier.projectflow.planelement.dto.TaskDependencyForm;
import de.melinadanhier.projectflow.planelement.dto.TaskDetailsDto;
import de.melinadanhier.projectflow.planelement.dto.TaskForm;
import de.melinadanhier.projectflow.planelement.mapper.PlanElementMapperImpl;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import de.melinadanhier.projectflow.planelement.model.TaskStatus;
import de.melinadanhier.projectflow.planelement.repository.MilestoneRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import de.melinadanhier.projectflow.planelement.repository.TaskRepository;
import de.melinadanhier.projectflow.planelement.service.MilestoneService;
import de.melinadanhier.projectflow.planelement.service.SectionService;
import de.melinadanhier.projectflow.planelement.service.TaskDependencyService;
import de.melinadanhier.projectflow.planelement.service.TaskService;
import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import({
        ProjectAuthorizationService.class,
        ProjectService.class,
        ProjectMapperImpl.class,
        PlanElementMapperImpl.class,
        TaskService.class,
        MilestoneService.class,
        SectionService.class,
        TaskDependencyService.class
})
class ProjectCrudIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private PlanSectionRepository sectionRepository;
    @Autowired private ProjectService projectService;
    @Autowired private TaskService taskService;
    @Autowired private MilestoneService milestoneService;
    @Autowired private SectionService sectionService;
    @Autowired private TaskDependencyService dependencyService;
    @Autowired private EntityManager entityManager;

    @Test
    void loadsCombinedPlanAndMakesTrashReadOnlyUntilReactivated() {
        User owner = saveUser("plan-owner@example.org");
        Project project = saveProject("Planansicht", owner);
        SectionDto section = sectionService.createSection(project.getId(), sectionForm("Vorbereitung"), owner.getId());
        TaskDetailsDto task = taskService.createTask(
                project.getId(), taskForm("Aufgabe", section.getId()), owner.getId());
        milestoneService.createMilestone(
                project.getId(), milestoneForm("Freigabe", section.getId()), owner.getId());

        ProjectPlanViewDto activePlan = projectService.getProjectPlan(project.getId(), owner.getId());
        assertThat(activePlan.isEditable()).isTrue();
        assertThat(activePlan.getSections()).singleElement().satisfies(phase -> {
            assertThat(phase.getTaskCount()).isEqualTo(1);
            assertThat(phase.getMilestoneCount()).isEqualTo(1);
        });
        assertThat(activePlan.getTasks()).extracting(TaskDetailsDto::getId).containsExactly(task.getId());

        projectService.moveToTrash(project.getId(), owner.getId());
        assertThat(projectService.getProjectPlan(project.getId(), owner.getId()).isEditable()).isFalse();
        assertThatThrownBy(() -> taskService.createTask(
                project.getId(), taskForm("Nicht erlaubt", section.getId()), owner.getId()))
                .isInstanceOf(ProjectNotEditableException.class);

        projectService.reactivateProject(project.getId(), owner.getId());
        assertThat(projectService.getProjectPlan(project.getId(), owner.getId()).isEditable()).isTrue();
    }

    @Test
    void permanentProjectDeletionRequiresTrashAndOwner() {
        User owner = saveUser("delete-owner@example.org");
        User member = saveUser("delete-member@example.org");
        Project project = saveProject("Löschen", owner);
        addMembership(project, member);

        assertThatThrownBy(() -> projectService.deleteProjectPermanently(project.getId(), owner.getId()))
                .isInstanceOf(de.melinadanhier.projectflow.common.exception.ConflictException.class);
        projectService.moveToTrash(project.getId(), owner.getId());
        assertThatThrownBy(() -> projectService.deleteProjectPermanently(project.getId(), member.getId()))
                .isInstanceOf(ForbiddenOperationException.class);

        projectService.deleteProjectPermanently(project.getId(), owner.getId());
        assertThat(projectRepository.findById(project.getId())).isEmpty();
    }

    @Test
    void taskDeletionRemovesIncomingAndOutgoingDependenciesAndRejectsForeignTasks() {
        User owner = saveUser("dependency-owner@example.org");
        Project project = saveProject("Abhängigkeiten", owner);
        TaskDetailsDto first = taskService.createTask(project.getId(), taskForm("A", null), owner.getId());
        TaskDetailsDto middle = taskService.createTask(project.getId(), taskForm("B", null), owner.getId());
        TaskDetailsDto last = taskService.createTask(project.getId(), taskForm("C", null), owner.getId());
        createDependency(project, owner, first.getId(), middle.getId());
        createDependency(project, owner, middle.getId(), last.getId());
        assertThat(taskService.getTaskDetail(project.getId(), middle.getId(), owner.getId())
                .getAffectedDependencyCount()).isEqualTo(2);

        Project otherProject = saveProject("Fremd", owner);
        TaskDetailsDto foreign = taskService.createTask(
                otherProject.getId(), taskForm("Fremd", null), owner.getId());
        TaskDependencyForm invalid = dependencyForm(first.getId(), foreign.getId());
        assertThatThrownBy(() -> dependencyService.createDependency(project.getId(), invalid, owner.getId()))
                .isInstanceOf(de.melinadanhier.projectflow.common.exception.ResourceNotFoundException.class);

        taskService.deleteTask(project.getId(), middle.getId(), owner.getId());
        entityManager.clear();
        assertThat(taskRepository.findById(middle.getId())).isEmpty();
        assertThat(taskService.getTaskDetail(project.getId(), last.getId(), owner.getId()).getPredecessors())
                .isEmpty();
    }

    @Test
    void sectionDeletionMovesContentsAndRejectsTargetFromAnotherProject() {
        User owner = saveUser("section-owner@example.org");
        Project project = saveProject("Phasen", owner);
        SectionDto source = sectionService.createSection(project.getId(), sectionForm("Quelle"), owner.getId());
        SectionDto target = sectionService.createSection(project.getId(), sectionForm("Ziel"), owner.getId());
        TaskDetailsDto task = taskService.createTask(
                project.getId(), taskForm("Verschieben", source.getId()), owner.getId());
        milestoneService.createMilestone(
                project.getId(), milestoneForm("Auch verschieben", source.getId()), owner.getId());

        Project otherProject = saveProject("Anderes Projekt", owner);
        SectionDto foreignTarget = sectionService.createSection(
                otherProject.getId(), sectionForm("Fremdes Ziel"), owner.getId());
        DeleteSectionForm invalid = deleteSectionForm(SectionDeletionMode.MOVE_CONTENT, foreignTarget.getId());
        assertThatThrownBy(() -> sectionService.deleteSection(
                project.getId(), source.getId(), invalid, owner.getId()))
                .isInstanceOf(DomainValidationException.class);

        sectionService.deleteSection(project.getId(), source.getId(),
                deleteSectionForm(SectionDeletionMode.MOVE_CONTENT, target.getId()), owner.getId());
        entityManager.flush();
        entityManager.clear();
        assertThat(sectionRepository.findById(source.getId())).isEmpty();
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getPlanSection().getId())
                .isEqualTo(target.getId());
        assertThat(projectService.getProjectPlan(project.getId(), owner.getId()).getSections())
                .singleElement().satisfies(phase -> {
                    assertThat(phase.getSortOrder()).isZero();
                    assertThat(phase.getTaskCount()).isEqualTo(1);
                    assertThat(phase.getMilestoneCount()).isEqualTo(1);
                });
    }

    @Test
    void milestoneCanBeCreatedUpdatedAndDeleted() {
        User owner = saveUser("milestone-owner@example.org");
        Project project = saveProject("Meilensteine", owner);
        MilestoneForm form = milestoneForm("Entwurf", null);
        var created = milestoneService.createMilestone(project.getId(), form, owner.getId());
        form.setTitle("Final");
        form.setCompleted(true);
        form.setLockVersion(created.getLockVersion());

        var updated = milestoneService.updateMilestone(project.getId(), created.getId(), form, owner.getId());
        assertThat(updated.getTitle()).isEqualTo("Final");
        assertThat(updated.isCompleted()).isTrue();
        milestoneService.deleteMilestone(project.getId(), created.getId(), owner.getId());
        assertThat(milestoneRepository.findById(created.getId())).isEmpty();
    }

    @Test
    void fullUpdatesPreserveValuesAndBlankOptionalFieldsCanBeCleared() {
        User owner = saveUser("update-owner@example.org");
        User member = saveUser("update-member@example.org");
        Project project = saveProject("Bearbeiten", owner);
        ProjectMember membership = addMembership(project, member, true);

        SectionForm sectionCreate = sectionForm("Analyse");
        sectionCreate.setDescription("Ausführliche Phase");
        sectionCreate.setStartDate(LocalDate.of(2026, 8, 14));
        sectionCreate.setEndDate(LocalDate.of(2026, 8, 18));
        SectionDto section = sectionService.createSection(project.getId(), sectionCreate, owner.getId());

        TaskForm taskCreate = taskForm("Recherche", section.getId());
        taskCreate.setDescription("Quellen sammeln");
        taskCreate.setStatus(TaskStatus.IN_PROGRESS);
        taskCreate.setStartDate(LocalDate.of(2026, 8, 15));
        taskCreate.setDueDate(LocalDate.of(2026, 8, 17));
        taskCreate.setAssigneeId(membership.getId());
        TaskDetailsDto task = taskService.createTask(project.getId(), taskCreate, owner.getId());

        TaskForm taskUpdate = taskFormFrom(task);
        taskUpdate.setTitle("Recherche aktualisiert");
        TaskDetailsDto updatedTask = taskService.updateTask(project.getId(), task.getId(), taskUpdate, owner.getId());
        assertThat(updatedTask.getDescription()).isEqualTo("Quellen sammeln");
        assertThat(updatedTask.getPlanSectionId()).isEqualTo(section.getId());
        assertThat(updatedTask.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(updatedTask.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(updatedTask.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(updatedTask.getAssigneeId()).isEqualTo(membership.getId());

        entityManager.flush();
        TaskDetailsDto currentTask = taskService.getTaskDetail(project.getId(), task.getId(), owner.getId());
        TaskForm taskClear = taskFormFrom(currentTask);
        taskClear.setDescription(" ");
        taskClear.setDueDate(null);
        TaskDetailsDto clearedTask = taskService.updateTask(project.getId(), task.getId(), taskClear, owner.getId());
        assertThat(clearedTask.getDescription()).isNull();
        assertThat(clearedTask.getDueDate()).isNull();
        assertThat(clearedTask.getRelativeStartDay()).isNull();
        assertThat(clearedTask.getRelativeDueDay()).isNull();

        MilestoneForm milestoneCreate = milestoneForm("Abnahme", section.getId());
        milestoneCreate.setDescription("Gemeinsame Prüfung");
        milestoneCreate.setDueDate(LocalDate.of(2026, 8, 18));
        milestoneCreate.setCompleted(true);
        var milestone = milestoneService.createMilestone(project.getId(), milestoneCreate, owner.getId());
        MilestoneForm milestoneUpdate = milestoneFormFrom(milestone);
        milestoneUpdate.setTitle("Abnahme aktualisiert");
        var updatedMilestone = milestoneService.updateMilestone(
                project.getId(), milestone.getId(), milestoneUpdate, owner.getId());
        assertThat(updatedMilestone.getDescription()).isEqualTo("Gemeinsame Prüfung");
        assertThat(updatedMilestone.getPlanSectionId()).isEqualTo(section.getId());
        assertThat(updatedMilestone.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 18));
        assertThat(updatedMilestone.isCompleted()).isTrue();

        entityManager.flush();
        var currentMilestone = milestoneService.getMilestoneDetail(
                project.getId(), milestone.getId(), owner.getId());
        MilestoneForm milestoneClear = milestoneFormFrom(currentMilestone);
        milestoneClear.setDescription("");
        milestoneClear.setDueDate(null);
        var clearedMilestone = milestoneService.updateMilestone(
                project.getId(), milestone.getId(), milestoneClear, owner.getId());
        assertThat(clearedMilestone.getDescription()).isNull();
        assertThat(clearedMilestone.getDueDate()).isNull();
        assertThat(clearedMilestone.getRelativeDueDay()).isNull();

        SectionForm sectionUpdate = sectionFormFrom(section);
        sectionUpdate.setTitle("Analyse aktualisiert");
        SectionDto updatedSection = sectionService.updateSection(
                project.getId(), section.getId(), sectionUpdate, owner.getId());
        assertThat(updatedSection.getDescription()).isEqualTo("Ausführliche Phase");
        assertThat(updatedSection.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 14));
        assertThat(updatedSection.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 18));

        entityManager.flush();
        var sectionEntity = sectionRepository.findById(section.getId()).orElseThrow();
        SectionForm sectionClear = new SectionForm();
        sectionClear.setTitle(sectionEntity.getTitle());
        sectionClear.setSortOrder(sectionEntity.getSortOrder());
        sectionClear.setLockVersion(sectionEntity.getLockVersion());
        SectionDto clearedSection = sectionService.updateSection(
                project.getId(), section.getId(), sectionClear, owner.getId());
        assertThat(clearedSection.getDescription()).isNull();
        assertThat(clearedSection.getStartDate()).isNull();
        assertThat(clearedSection.getEndDate()).isNull();
        assertThat(clearedSection.getRelativeStartDay()).isNull();
        assertThat(clearedSection.getRelativeEndDay()).isNull();
    }

    @Test
    void rejectsForeignSectionsAndInactiveOrForeignAssignees() {
        User owner = saveUser("validation-owner@example.org");
        User inactiveUser = saveUser("inactive-assignee@example.org");
        User foreignUser = saveUser("foreign-assignee@example.org");
        Project project = saveProject("Validierung", owner);
        Project otherProject = saveProject("Fremdprojekt", owner);
        SectionDto foreignSection = sectionService.createSection(
                otherProject.getId(), sectionForm("Fremde Phase"), owner.getId());
        ProjectMember inactiveMember = addMembership(project, inactiveUser, false);
        ProjectMember foreignMember = addMembership(otherProject, foreignUser, true);

        TaskForm foreignSectionForm = taskForm("Ungültige Phase", foreignSection.getId());
        assertThatThrownBy(() -> taskService.createTask(project.getId(), foreignSectionForm, owner.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        MilestoneForm foreignMilestoneForm = milestoneForm("Ungültiger Meilenstein", foreignSection.getId());
        assertThatThrownBy(() -> milestoneService.createMilestone(
                project.getId(), foreignMilestoneForm, owner.getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        TaskForm inactiveAssigneeForm = taskForm("Inaktiv", null);
        inactiveAssigneeForm.setAssigneeId(inactiveMember.getId());
        assertThatThrownBy(() -> taskService.createTask(project.getId(), inactiveAssigneeForm, owner.getId()))
                .isInstanceOf(DomainValidationException.class);
        TaskForm foreignAssigneeForm = taskForm("Projektfremd", null);
        foreignAssigneeForm.setAssigneeId(foreignMember.getId());
        assertThatThrownBy(() -> taskService.createTask(project.getId(), foreignAssigneeForm, owner.getId()))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void combinesTasksAndMilestonesBySortOrderPerSectionAndWithoutSection() {
        User owner = saveUser("ordering-owner@example.org");
        Project project = saveProject("Sortierung", owner);
        SectionDto firstSection = sectionService.createSection(
                project.getId(), sectionForm("Erste Phase"), owner.getId());
        SectionDto secondSection = sectionService.createSection(
                project.getId(), sectionForm("Zweite Phase"), owner.getId());

        taskService.createTask(project.getId(), taskForm("A1", firstSection.getId()), owner.getId());
        taskService.createTask(project.getId(), taskForm("A3", firstSection.getId()), owner.getId());
        MilestoneForm middle = milestoneForm("A2", firstSection.getId());
        middle.setSortOrder(1);
        milestoneService.createMilestone(project.getId(), middle, owner.getId());

        milestoneService.createMilestone(project.getId(), milestoneForm("B2", secondSection.getId()), owner.getId());
        TaskForm firstInSecond = taskForm("B1", secondSection.getId());
        firstInSecond.setSortOrder(0);
        taskService.createTask(project.getId(), firstInSecond, owner.getId());

        taskService.createTask(project.getId(), taskForm("Ohne 2", null), owner.getId());
        MilestoneForm firstUnsectioned = milestoneForm("Ohne 1", null);
        firstUnsectioned.setSortOrder(0);
        milestoneService.createMilestone(project.getId(), firstUnsectioned, owner.getId());

        ProjectPlanViewDto plan = projectService.getProjectPlan(project.getId(), owner.getId());
        assertThat(plan.getSections()).extracting(SectionDto::getTitle)
                .containsExactly("Erste Phase", "Zweite Phase");
        assertThat(plan.getSections().get(0).getElements())
                .extracting(PlanElementViewDto::getTitle, PlanElementViewDto::getType)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("A1", PlanElementType.TASK),
                        org.assertj.core.groups.Tuple.tuple("A2", PlanElementType.MILESTONE),
                        org.assertj.core.groups.Tuple.tuple("A3", PlanElementType.TASK));
        assertThat(plan.getSections().get(1).getElements())
                .extracting(PlanElementViewDto::getTitle)
                .containsExactly("B1", "B2");
        assertThat(plan.getUnsectionedElements())
                .extracting(PlanElementViewDto::getTitle, PlanElementViewDto::getType)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Ohne 1", PlanElementType.MILESTONE),
                        org.assertj.core.groups.Tuple.tuple("Ohne 2", PlanElementType.TASK));
    }

    private void createDependency(Project project, User owner, UUID prerequisiteId, UUID successorId) {
        dependencyService.createDependency(
                project.getId(), dependencyForm(prerequisiteId, successorId), owner.getId());
    }

    private TaskDependencyForm dependencyForm(UUID prerequisiteId, UUID successorId) {
        TaskDependencyForm form = new TaskDependencyForm();
        form.setPrerequisiteTaskId(prerequisiteId);
        form.setSuccessorTaskId(successorId);
        return form;
    }

    private DeleteSectionForm deleteSectionForm(SectionDeletionMode mode, UUID targetId) {
        DeleteSectionForm form = new DeleteSectionForm();
        form.setMode(mode);
        form.setTargetSectionId(targetId);
        return form;
    }

    private TaskForm taskForm(String title, UUID sectionId) {
        TaskForm form = new TaskForm();
        form.setTitle(title);
        form.setPriority(TaskPriority.MEDIUM);
        form.setPlanSectionId(sectionId);
        return form;
    }

    private MilestoneForm milestoneForm(String title, UUID sectionId) {
        MilestoneForm form = new MilestoneForm();
        form.setTitle(title);
        form.setPlanSectionId(sectionId);
        return form;
    }

    private SectionForm sectionForm(String title) {
        SectionForm form = new SectionForm();
        form.setTitle(title);
        return form;
    }

    private TaskForm taskFormFrom(TaskDetailsDto task) {
        TaskForm form = taskForm(task.getTitle(), task.getPlanSectionId());
        form.setDescription(task.getDescription());
        form.setSortOrder(task.getSortOrder());
        form.setStatus(task.getStatus());
        form.setPriority(task.getPriority());
        form.setStartDate(task.getStartDate());
        form.setDueDate(task.getDueDate());
        form.setAssigneeId(task.getAssigneeId());
        form.setLockVersion(task.getLockVersion());
        return form;
    }

    private MilestoneForm milestoneFormFrom(de.melinadanhier.projectflow.planelement.dto.MilestoneDetailsDto milestone) {
        MilestoneForm form = milestoneForm(milestone.getTitle(), milestone.getPlanSectionId());
        form.setDescription(milestone.getDescription());
        form.setSortOrder(milestone.getSortOrder());
        form.setDueDate(milestone.getDueDate());
        form.setCompleted(milestone.isCompleted());
        form.setLockVersion(milestone.getLockVersion());
        return form;
    }

    private SectionForm sectionFormFrom(SectionDto section) {
        SectionForm form = sectionForm(section.getTitle());
        form.setDescription(section.getDescription());
        form.setStartDate(section.getStartDate());
        form.setEndDate(section.getEndDate());
        form.setSortOrder(section.getSortOrder());
        form.setLockVersion(section.getLockVersion());
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
        project.setCreationType(CreationType.EMPTY);
        project.setStatus(ProjectStatus.ACTIVE);
        ProjectMember membership = new ProjectMember();
        membership.setUser(owner);
        membership.setRole(ProjectMemberRole.OWNER);
        membership.setActive(true);
        project.addMembership(membership);
        return projectRepository.saveAndFlush(project);
    }

    private void addMembership(Project project, User member) {
        addMembership(project, member, true);
    }

    private ProjectMember addMembership(Project project, User member, boolean active) {
        ProjectMember membership = new ProjectMember();
        membership.setProject(project);
        membership.setUser(member);
        membership.setRole(ProjectMemberRole.MEMBER);
        membership.setActive(active);
        return projectMemberRepository.saveAndFlush(membership);
    }
}
