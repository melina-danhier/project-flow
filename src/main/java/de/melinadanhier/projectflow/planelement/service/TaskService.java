package de.melinadanhier.projectflow.planelement.service;

import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.plancontainer.project.mapper.ProjectMapper;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectMemberRepository;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.planelement.dto.TaskDetailsDto;
import de.melinadanhier.projectflow.planelement.dto.TaskForm;
import de.melinadanhier.projectflow.planelement.dto.TaskReferenceDto;
import de.melinadanhier.projectflow.planelement.mapper.PlanElementMapper;
import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import de.melinadanhier.projectflow.planelement.model.PlanElement;
import de.melinadanhier.projectflow.planelement.model.PlanSection;
import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.planelement.model.TaskStatus;
import de.melinadanhier.projectflow.planelement.repository.PlanElementRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import de.melinadanhier.projectflow.planelement.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final PlanElementRepository planElementRepository;
    private final PlanSectionRepository planSectionRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAuthorizationService authorizationService;
    private final PlanElementMapper planElementMapper;
    private final ProjectMapper projectMapper;

    @Transactional
    public TaskDetailsDto createTask(UUID projectId, TaskForm form, UUID userId) {
        Project project = authorizationService.requireEditableMemberForUpdate(projectId, userId).getProject();
        validateDates(form.getStartDate(), form.getDueDate());
        PlanSection section = resolveSection(projectId, form.getPlanSectionId());
        ProjectMember assignee = resolveAssignee(project, form.getAssigneeId());

        Task task = new Task();
        task.setPlanContainer(project);
        task.setPlanSection(section);
        task.setOrigin(ElementOrigin.USER);
        task.setRelativeStartDay(null);
        task.setRelativeDueDay(null);
        apply(task, form, assignee);
        insertAtRequestedPosition(task, projectId, section, form.getSortOrder());
        TaskDetailsDto dto = planElementMapper.toDetailsDto(taskRepository.save(task));
        dto.setGroupProject(project.isGroupProject());
        return dto;
    }

    @Transactional(readOnly = true)
    public TaskDetailsDto getTaskDetail(UUID projectId, UUID taskId, UUID userId) {
        ProjectMember membership = authorizationService.requireMember(projectId, userId);
        return buildTaskDetail(projectId, taskId, authorizationService.isEditable(membership), membership.getProject().isGroupProject());
    }

    @Transactional(readOnly = true)
    public TaskDetailsDto getTaskForEditing(UUID projectId, UUID taskId, UUID userId) {
        var project = authorizationService.requireEditableMember(projectId, userId).getProject();
        return buildTaskDetail(projectId, taskId, true, project.isGroupProject());
    }

    @Transactional(readOnly = true)
    public TaskDetailsDto getTaskCreationContext(UUID projectId, UUID userId) {
        var project = authorizationService.requireEditableMember(projectId, userId).getProject();
        TaskDetailsDto dto = new TaskDetailsDto();
        dto.setGroupProject(project.isGroupProject());
        dto.setPlanContainerId(projectId);
        dto.setEditable(true);
        populateOptions(dto, projectId);
        return dto;
    }

    private TaskDetailsDto buildTaskDetail(UUID projectId, UUID taskId, boolean editable, boolean groupProject) {
        Task task = taskRepository.findByIdAndPlanContainerId(taskId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Aufgabe wurde nicht gefunden."));
        List<Task> successors = taskRepository.findSuccessors(projectId, taskId);
        TaskDetailsDto dto = planElementMapper.toDetailsDto(task);
        dto.setGroupProject(groupProject);
        dto.setPredecessors(task.getPrerequisites().stream()
                .map(predecessor -> new TaskReferenceDto(predecessor.getId(), predecessor.getTitle()))
                .toList());
        dto.setSuccessors(successors.stream()
                .map(successor -> new TaskReferenceDto(successor.getId(), successor.getTitle()))
                .toList());
        dto.setAffectedDependencyCount(dto.getPredecessors().size() + dto.getSuccessors().size());
        populateOptions(dto, projectId);
        dto.setAvailablePrerequisites(taskRepository.findPlanTasks(projectId).stream()
                .filter(candidate -> !candidate.getId().equals(taskId))
                .filter(candidate -> task.getPrerequisites().stream()
                        .noneMatch(prerequisite -> prerequisite.getId().equals(candidate.getId())))
                .filter(candidate -> !dependsOn(candidate, taskId, new HashSet<>()))
                .map(candidate -> new TaskReferenceDto(candidate.getId(), candidate.getTitle()))
                .toList());
        dto.setEditable(editable);
        return dto;
    }

    private void populateOptions(TaskDetailsDto dto, UUID projectId) {
        dto.setAvailableAssignees(dto.isGroupProject()
                ? projectMemberRepository.findActiveByProjectIdWithUser(projectId).stream()
                        .map(projectMapper::toMemberDto).toList()
                : List.of());
        dto.setAvailableSections(planSectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(projectId).stream()
                .map(planElementMapper::toDto)
                .toList());
    }

    private boolean dependsOn(Task task, UUID possiblePrerequisiteId, Set<UUID> visited) {
        if (!visited.add(task.getId())) {
            return false;
        }
        return task.getPrerequisites().stream().anyMatch(prerequisite ->
                prerequisite.getId().equals(possiblePrerequisiteId)
                        || dependsOn(prerequisite, possiblePrerequisiteId, visited));
    }

    @Transactional
    public TaskDetailsDto updateTask(UUID projectId, UUID taskId, TaskForm form, UUID userId) {
        Project project = authorizationService.requireEditableMemberForUpdate(projectId, userId).getProject();
        validateDates(form.getStartDate(), form.getDueDate());
        Task task = taskRepository.findByIdAndPlanContainerId(taskId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Aufgabe wurde nicht gefunden."));
        requireCurrentVersion(task.getLockVersion(), form.getLockVersion());
        PlanSection oldSection = task.getPlanSection();
        PlanSection newSection = resolveSection(projectId, form.getPlanSectionId());
        ProjectMember assignee = resolveAssignee(project, form.getAssigneeId());
        apply(task, form, assignee);
        moveToRequestedPosition(task, projectId, oldSection, newSection, form.getSortOrder());
        TaskDetailsDto dto = planElementMapper.toDetailsDto(task);
        dto.setGroupProject(project.isGroupProject());
        return dto;
    }

    @Transactional
    public void deleteTask(UUID projectId, UUID taskId, UUID userId) {
        authorizationService.requireEditableMember(projectId, userId);
        Task task = taskRepository.findByIdAndPlanContainerId(taskId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Aufgabe wurde nicht gefunden."));
        PlanSection section = task.getPlanSection();
        taskRepository.findSuccessors(projectId, taskId)
                .forEach(successor -> successor.removePrerequisite(task));
        task.getPrerequisites().clear();
        taskRepository.flush();
        taskRepository.delete(task);
        taskRepository.flush();
        resequence(loadSiblings(projectId, section));
    }

    private void apply(Task task, TaskForm form, ProjectMember assignee) {
        task.setTitle(form.getTitle().trim());
        task.setDescription(normalizeOptional(form.getDescription()));
        task.setPriority(form.getPriority());
        task.setStartDate(form.getStartDate());
        task.setDueDate(form.getDueDate());
        task.setRelativeStartDay(null);
        task.setRelativeDueDay(null);
        task.setAssignee(assignee);
        task.setStatus(form.getStatus() == null ? defaultStatus(task) : form.getStatus());
    }

    private TaskStatus defaultStatus(Task task) {
        return task.getId() == null ? TaskStatus.OPEN : task.getStatus();
    }

    private PlanSection resolveSection(UUID projectId, UUID sectionId) {
        if (sectionId == null) {
            return null;
        }
        return planSectionRepository.findByIdAndPlanContainerId(sectionId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projektbereich wurde nicht gefunden."));
    }

    private ProjectMember resolveAssignee(Project project, UUID membershipId) {
        if (membershipId == null) {
            return null;
        }
        if (!project.isGroupProject()) {
            throw new DomainValidationException("Aufgabenzuständigkeiten sind nur bei Gruppenprojekten möglich.");
        }
        return projectMemberRepository.findByIdAndProjectIdAndActiveTrue(membershipId, project.getId())
                .orElseThrow(() -> new DomainValidationException(
                        "Die Aufgabe kann nur einem aktiven Mitglied dieses Projekts zugewiesen werden."
                ));
    }

    private void insertAtRequestedPosition(Task task, UUID projectId, PlanSection section, Integer requested) {
        List<PlanElement> siblings = loadSiblings(projectId, section);
        int position = boundedPosition(requested, siblings.size());
        siblings.add(position, task);
        resequence(siblings);
    }

    private void moveToRequestedPosition(
            Task task,
            UUID projectId,
            PlanSection oldSection,
            PlanSection newSection,
            Integer requested
    ) {
        List<PlanElement> oldSiblings = loadSiblings(projectId, oldSection);
        oldSiblings.removeIf(element -> element.getId().equals(task.getId()));
        resequence(oldSiblings);

        List<PlanElement> targetSiblings = sameSection(oldSection, newSection)
                ? oldSiblings
                : loadSiblings(projectId, newSection);
        task.setPlanSection(newSection);
        int position = boundedPosition(requested, targetSiblings.size());
        targetSiblings.add(position, task);
        resequence(targetSiblings);
    }

    private boolean sameSection(PlanSection first, PlanSection second) {
        return first == null ? second == null : second != null && first.getId().equals(second.getId());
    }

    private List<PlanElement> loadSiblings(UUID projectId, PlanSection section) {
        return new ArrayList<>(section == null
                ? planElementRepository.findAllByPlanContainerIdAndPlanSectionIsNullOrderBySortOrderAsc(projectId)
                : planElementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
                        projectId, section.getId()));
    }

    private int boundedPosition(Integer requested, int size) {
        return requested == null ? size : Math.min(requested, size);
    }

    private void resequence(List<PlanElement> elements) {
        for (int index = 0; index < elements.size(); index++) {
            elements.get(index).setSortOrder(index);
        }
    }

    private void validateDates(LocalDate start, LocalDate due) {
        if (start != null && due != null && due.isBefore(start)) {
            throw new DomainValidationException("Das Fälligkeitsdatum darf nicht vor dem Startdatum liegen.");
        }
    }

    private void requireCurrentVersion(long actualVersion, Long submittedVersion) {
        if (submittedVersion != null && submittedVersion != actualVersion) {
            throw new ConflictException("Die Aufgabe wurde zwischenzeitlich geändert. Bitte lade die Seite neu.");
        }
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
