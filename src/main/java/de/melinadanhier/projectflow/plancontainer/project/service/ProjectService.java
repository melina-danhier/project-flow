package de.melinadanhier.projectflow.plancontainer.project.service;

import de.melinadanhier.projectflow.plancontainer.project.validation.ProjectClassificationValidator;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.draft.repository.DraftRepository;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectCreateForm;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectDetailsDto;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectSummaryDto;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectUpdateForm;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectPlanViewDto;
import de.melinadanhier.projectflow.plancontainer.project.mapper.ProjectMapper;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMemberRole;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectMemberRepository;
import de.melinadanhier.projectflow.plancontainer.template.model.Template;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.repository.TemplateRepository;
import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import de.melinadanhier.projectflow.planelement.model.Milestone;
import de.melinadanhier.projectflow.planelement.model.PlanElement;
import de.melinadanhier.projectflow.planelement.model.PlanSection;
import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.planelement.dto.MilestoneDetailsDto;
import de.melinadanhier.projectflow.planelement.dto.SectionDto;
import de.melinadanhier.projectflow.planelement.dto.TaskDependencyDto;
import de.melinadanhier.projectflow.planelement.dto.TaskDetailsDto;
import de.melinadanhier.projectflow.planelement.dto.PlanElementType;
import de.melinadanhier.projectflow.planelement.dto.PlanElementViewDto;
import de.melinadanhier.projectflow.planelement.mapper.PlanElementMapper;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import de.melinadanhier.projectflow.planelement.repository.TaskRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanElementRepository;
import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDate;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final UserRepository userRepository;
    private final TemplateRepository templateRepository;
    private final ProjectAuthorizationService authorizationService;
    private final ProjectMemberRepository projectMemberRepository;
    private final PlanSectionRepository planSectionRepository;
    private final TaskRepository taskRepository;
    private final PlanElementMapper planElementMapper;
    private final DraftRepository draftRepository;
    private final PlanElementRepository planElementRepository;
    private final ProjectStateService projectStateService;

    @Transactional
    public ProjectDetailsDto createProject(ProjectCreateForm form, UUID ownerUserId) {
        if (form.getCreationType() != CreationType.EMPTY) {
            throw new DomainValidationException(
                    "Über diesen Schritt kann nur ein Projekt ohne anfängliche Aufgaben gespeichert werden."
            );
        }
        validateGeneralProjectData(form);
        User owner = userRepository.findById(ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Nutzerkonto wurde nicht gefunden."));
        Project project = initializeProject(form, owner, CreationType.EMPTY);
        return projectMapper.toDetailsDto(projectRepository.save(project));
    }

    @Transactional
    public ProjectDetailsDto createProjectFromTemplate(UUID templateId, ProjectCreateForm form, UUID ownerUserId) {
        User owner = userRepository.findById(ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Nutzerkonto wurde nicht gefunden."));
        Template template = templateRepository.findById(templateId)
                .filter(Template::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Vorlage wurde nicht gefunden."));
        if (form.getCategory() == null) {
            form.setCategory(template.getCategory());
            form.setSubcategory(template.getSubcategory());
            form.setOtherProjectTypeDescription(template.getOtherProjectTypeDescription());
        }
        if (form.getCollaborationMode() == null) {
            form.setCollaborationMode(template.getCollaborationMode() == CollaborationMode.BOTH
                    ? CollaborationMode.INDIVIDUAL
                    : template.getCollaborationMode());
        }
        validateGeneralProjectData(form);
        Project project = initializeProject(form, owner, CreationType.TEMPLATE);
        if (form.getDescription() == null) {
            project.setDescription(template.getDescription());
        }
        if (form.getStructureMode() == null) {
            project.setStructureMode(template.getStructureMode());
        }
        if (form.getSortMode() == null) {
            project.setSortMode(template.getSortMode());
        }
        copyTemplateContents(template, project);
        return projectMapper.toDetailsDto(projectRepository.save(project));
    }

    private Project initializeProject(ProjectCreateForm form, User owner, CreationType creationType) {
        Project project = new Project();
        project.setTitle(form.getTitle().trim());
        project.setDescription(form.getDescription());
        project.setStartDate(form.getStartDate());
        project.setEndDate(form.getEndDate());
        project.setCategory(form.getCategory());
        project.setSubcategory(form.getSubcategory());
        project.setOtherProjectTypeDescription(form.isOtherCategory()
                ? normalizeOptionalText(form.getOtherProjectTypeDescription()) : null);
        project.setCollaborationMode(form.getCollaborationMode());
        project.setCreationType(creationType);
        projectStateService.changeState(project, ProjectStatus.ACTIVE, ProjectLocation.OVERVIEW);
        if (form.getStructureMode() != null) {
            project.setStructureMode(form.getStructureMode());
        }
        if (form.getSortMode() != null) {
            project.setSortMode(form.getSortMode());
        }

        ProjectMember ownerMembership = new ProjectMember();
        ownerMembership.setUser(owner);
        ownerMembership.setRole(ProjectMemberRole.OWNER);
        ownerMembership.setActive(true);
        project.addMembership(ownerMembership);
        return project;
    }

    private void copyTemplateContents(Template template, Project project) {
        Map<PlanSection, PlanSection> sections = new HashMap<>();
        for (PlanSection source : template.getSections()) {
            PlanSection copy = new PlanSection();
            copy.setTitle(source.getTitle());
            copy.setDescription(source.getDescription());
            copy.setSortOrder(source.getSortOrder());
            copy.setOrigin(ElementOrigin.TEMPLATE);
            project.addSection(copy);
            sections.put(source, copy);
        }

        Map<PlanElement, PlanElement> elements = new HashMap<>();
        for (PlanElement source : template.getElements()) {
            PlanElement copy = copyElement(source, project);
            project.addElement(copy);
            if (source.getPlanSection() != null) {
                sections.get(source.getPlanSection()).addElement(copy);
            }
            elements.put(source, copy);
        }
        for (PlanElement source : template.getElements()) {
            if (source instanceof Task sourceTask && elements.get(source) instanceof Task copiedTask) {
                sourceTask.getPrerequisites().stream()
                        .map(elements::get)
                        .filter(Task.class::isInstance)
                        .map(Task.class::cast)
                        .forEach(copiedTask::addPrerequisite);
            }
        }
    }

    private PlanElement copyElement(PlanElement source, Project project) {
        PlanElement copy;
        if (source instanceof Task sourceTask) {
            Task task = new Task();
            task.setPriority(sourceTask.getPriority());
            task.setStartDate(toAbsoluteDate(project, sourceTask.getStartDate(), sourceTask.getRelativeStartDay()));
            task.setDueDate(toAbsoluteDate(project, sourceTask.getDueDate(), sourceTask.getRelativeDueDay()));
            task.setRelativeStartDay(null);
            task.setRelativeDueDay(null);
            copy = task;
        } else if (source instanceof Milestone sourceMilestone) {
            Milestone milestone = new Milestone();
            milestone.setDueDate(toAbsoluteDate(
                    project, sourceMilestone.getDueDate(), sourceMilestone.getRelativeDueDay()));
            milestone.setRelativeDueDay(null);
            copy = milestone;
        } else {
            throw new IllegalStateException("Nicht unterstützter Vorlageninhalt.");
        }
        copy.setTitle(source.getTitle());
        copy.setDescription(source.getDescription());
        copy.setSortOrder(source.getSortOrder());
        copy.setOrigin(ElementOrigin.TEMPLATE);
        return copy;
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryDto> findAccessibleProjects(UUID userId) {
        return findAccessibleProjects(ProjectLocation.OVERVIEW, userId);
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryDto> findAccessibleProjects(ProjectLocation location, UUID userId) {
        ProjectLocation selectedLocation = location == null ? ProjectLocation.OVERVIEW : location;
        return projectRepository.findAllAccessibleByUserIdAndLocation(userId, selectedLocation).stream()
                .map(projectMapper::toSummaryDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryDto> findDraftProjects(UUID userId) {
        return projectRepository.findAllDraftsAccessibleByUserId(userId).stream()
                .map(projectMapper::toSummaryDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectDetailsDto getProject(UUID projectId, UUID userId) {
        authorizationService.requireMember(projectId, userId);
        Project project = projectRepository.findPlanProjectById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projekt wurde nicht gefunden."));
        requireRegularProject(project);
        return projectMapper.toDetailsDto(project);
    }

    @Transactional(readOnly = true)
    public ProjectPlanViewDto getProjectPlan(UUID projectId, UUID userId) {
        ProjectMember currentMembership = authorizationService.requireMember(projectId, userId);
        Project project = projectRepository.findPlanProjectById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projekt wurde nicht gefunden."));
        if (project.getStatus() == ProjectStatus.DRAFT) {
            throw new DraftProjectPlanAccessException(projectId);
        }
        requireRegularProject(project);
        List<PlanElement> planElements = planElementRepository.findPlanElements(projectId);
        List<Task> tasks = taskRepository.findPlanTasks(projectId);
        List<Milestone> milestones = planElements.stream()
                .filter(Milestone.class::isInstance)
                .map(Milestone.class::cast)
                .toList();

        ProjectPlanViewDto view = new ProjectPlanViewDto();
        view.setProject(projectMapper.toDetailsDto(project));
        view.setEditable(authorizationService.isEditable(currentMembership));
        view.setOwner(currentMembership.getRole() == ProjectMemberRole.OWNER);
        view.setActiveMembers(project.isGroupProject()
                ? projectMemberRepository.findActiveByProjectIdWithUser(projectId).stream()
                        .map(projectMapper::toMemberDto).toList()
                : List.of());

        List<TaskDetailsDto> taskDtos = tasks.stream().map(planElementMapper::toDetailsDto).toList();
        List<MilestoneDetailsDto> milestoneDtos = milestones.stream().map(planElementMapper::toDetailsDto).toList();
        taskDtos.forEach(task -> task.setEditable(view.isEditable()));
        milestoneDtos.forEach(milestone -> milestone.setEditable(view.isEditable()));
        view.setTasks(taskDtos);
        view.setMilestones(milestoneDtos);
        view.setDependencies(tasks.stream()
                .flatMap(successor -> successor.getPrerequisites().stream()
                        .map(prerequisite -> new TaskDependencyDto(
                                prerequisite.getId(), prerequisite.getTitle(),
                                successor.getId(), successor.getTitle()
                        )))
                .toList());

        List<PlanElementViewDto> orderedElements = planElements.stream()
                .map(this::toViewElement)
                .sorted(Comparator.comparingInt(PlanElementViewDto::getSortOrder)
                .thenComparing(PlanElementViewDto::getType)
                .thenComparing(PlanElementViewDto::getId))
                .toList();

        Map<UUID, Long> taskCounts = tasks.stream()
                .filter(task -> task.getPlanSection() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        task -> task.getPlanSection().getId(), java.util.stream.Collectors.counting()));
        Map<UUID, Long> milestoneCounts = milestones.stream()
                .filter(milestone -> milestone.getPlanSection() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        milestone -> milestone.getPlanSection().getId(), java.util.stream.Collectors.counting()));
        List<SectionDto> sections = planSectionRepository
                .findAllByPlanContainerIdOrderBySortOrderAsc(projectId).stream()
                .map(section -> {
                    SectionDto dto = planElementMapper.toDto(section);
                    dto.setTaskCount(taskCounts.getOrDefault(section.getId(), 0L).intValue());
                    dto.setMilestoneCount(milestoneCounts.getOrDefault(section.getId(), 0L).intValue());
                    dto.setElements(orderedElements.stream()
                            .filter(element -> section.getId().equals(element.getPlanSectionId()))
                            .toList());
                    return dto;
                })
                .toList();
        view.setSections(sections);
        view.setUnsectionedElements(orderedElements.stream()
                .filter(element -> element.getPlanSectionId() == null)
                .toList());
        return view;
    }

    @Transactional
    public ProjectDetailsDto updateProject(UUID projectId, ProjectUpdateForm form, UUID userId) {
        ProjectMember actingOwner = authorizationService.requireEditableOwnerForUpdate(projectId, userId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projekt wurde nicht gefunden."));
        requireCurrentVersion(project.getLockVersion(), form.getLockVersion());
        if (form.getCollaborationMode() == null || form.getCollaborationMode() == CollaborationMode.BOTH) {
            throw new DomainValidationException("Bitte wähle Einzel- oder Gruppenprojekt aus.");
        }
        boolean convertToIndividual = form.getCollaborationMode() == CollaborationMode.INDIVIDUAL
                && project.getCollaborationMode() != CollaborationMode.INDIVIDUAL;
        if (convertToIndividual && !form.isConfirmIndividualConversion()) {
            throw new DomainValidationException(
                    "Bitte bestätige den Wechsel zum Einzelprojekt: Alle weiteren Projektmitglieder, ihre Beiträge und sämtliche Aufgabenzuständigkeiten werden entfernt.");
        }
        if (form.getCategory() == null) {
            throw new DomainValidationException("Bitte wähle eine Oberkategorie aus.");
        }
        ProjectClassificationValidator.requireValid(form.getCategory(), form.getSubcategory(),
                form.getOtherProjectTypeDescription());
        validateDateRange(form.getStartDate(), form.getEndDate());
        if (convertToIndividual) {
            taskRepository.findPlanTasks(projectId).forEach(task -> task.setAssignee(null));
            taskRepository.flush();
            var otherMemberships = projectMemberRepository.findAllByProjectId(projectId).stream()
                    .filter(membership -> membership.getRole() != ProjectMemberRole.OWNER).toList();
            project.getMemberships().removeAll(otherMemberships);
            projectMemberRepository.deleteAll(otherMemberships);
        }
        project.setCollaborationMode(form.getCollaborationMode());
        project.setCategory(form.getCategory());
        project.setSubcategory(form.getSubcategory());
        project.setOtherProjectTypeDescription(form.isOtherCategory()
                ? normalizeOptionalText(form.getOtherProjectTypeDescription()) : null);
        project.setTitle(form.getTitle().trim());
        project.setDescription(form.getDescription());
        project.setStartDate(form.getStartDate());
        project.setEndDate(form.getEndDate());
        if (form.getStructureMode() != null) {
            project.setStructureMode(form.getStructureMode());
        }
        if (form.getSortMode() != null) {
            project.setSortMode(form.getSortMode());
        }
        return projectMapper.toDetailsDto(project);
    }

    @Transactional
    public void moveToTrash(UUID projectId, UUID userId) {
        ProjectMember owner = authorizationService.requireEditableOwner(projectId, userId);
        projectStateService.changeState(owner.getProject(), ProjectStatus.ACTIVE, ProjectLocation.TRASH);
    }

    @Transactional
    public void reactivateProject(UUID projectId, UUID userId) {
        ProjectMember owner = authorizationService.requireOwner(projectId, userId);
        Project project = owner.getProject();
        if (project.getLocation() != ProjectLocation.TRASH
                && project.getLocation() != ProjectLocation.ARCHIVE) {
            throw new ConflictException("Nur archivierte Projekte oder Projekte im Papierkorb können reaktiviert werden.");
        }
        if (project.getStatus() == ProjectStatus.DRAFT) {
            throw new ConflictException("Ein KI-Entwurf muss über den vorgesehenen Prüf- und Bestätigungsfluss übernommen werden.");
        }
        projectStateService.changeState(project, ProjectStatus.ACTIVE, ProjectLocation.OVERVIEW);
    }

    @Transactional
    public void deleteProjectPermanently(UUID projectId, UUID userId) {
        ProjectMember owner = authorizationService.requireOwner(projectId, userId);
        Project project = owner.getProject();
        if (project.getLocation() != ProjectLocation.TRASH) {
            throw new ConflictException("Ein Projekt kann nur aus dem Papierkorb endgültig gelöscht werden.");
        }
        List<Task> tasks = taskRepository.findPlanTasks(projectId);
        tasks.forEach(task -> task.getPrerequisites().clear());
        taskRepository.flush();
        planElementRepository.deleteAll(
                planElementRepository.findAllByPlanContainerIdOrderBySortOrderAsc(projectId));
        planElementRepository.flush();
        planSectionRepository.deleteAll(
                planSectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(projectId));
        planSectionRepository.flush();
        if (project.getCurrentDraft() != null) {
            draftRepository.delete(project.getCurrentDraft());
            project.setCurrentDraft(null);
            draftRepository.flush();
        }
        projectMemberRepository.deleteAllByProjectId(projectId);
        Project projectToDelete = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projekt wurde nicht gefunden."));
        projectRepository.delete(projectToDelete);
        projectRepository.flush();
    }

    private void validateDateRange(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new DomainValidationException("Das Projektende darf nicht vor dem Projektstart liegen.");
        }
    }

    private void validateGeneralProjectData(ProjectCreateForm form) {
        if (form.getCategory() == de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory.OTHER
                && (form.getDescription() == null || form.getDescription().isBlank())
                && (form.getOtherProjectTypeDescription() == null
                    || form.getOtherProjectTypeDescription().isBlank())) {
            throw new DomainValidationException("Bitte beschreibe dein sonstiges Projekt.");
        }
        ProjectClassificationValidator.requireValid(form.getCategory(), form.getSubcategory(),
                form.getCategory() == de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory.OTHER
                        && (form.getOtherProjectTypeDescription() == null
                            || form.getOtherProjectTypeDescription().isBlank())
                        ? "Sonstiges Projekt" : form.getOtherProjectTypeDescription());
        validateDateRange(form.getStartDate(), form.getEndDate());
        if (form.getCategory() == null) {
            throw new DomainValidationException("Bitte wähle eine Oberkategorie aus.");
        }
        if (form.getCollaborationMode() == null
                || form.getCollaborationMode()
                == CollaborationMode.BOTH) {
            throw new DomainValidationException("Bitte wähle Einzel- oder Gruppenprojekt aus.");
        }
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void requireCurrentVersion(long actualVersion, Long submittedVersion) {
        if (submittedVersion == null) {
            throw new DomainValidationException("Die Versionsnummer des Projekts fehlt.");
        }
        if (submittedVersion != actualVersion) {
            throw new ConflictException("Das Projekt wurde zwischenzeitlich geändert. Bitte lade die Seite neu.");
        }
    }

    private void requireRegularProject(Project project) {
        if (project.getStatus() == ProjectStatus.DRAFT) {
            throw new ResourceNotFoundException("Projekt wurde nicht gefunden.");
        }
    }

    private LocalDate toAbsoluteDate(Project project, LocalDate absoluteDate, Integer relativeDay) {
        if (absoluteDate != null || relativeDay == null) {
            return absoluteDate;
        }
        if (project.getStartDate() == null) {
            throw new DomainValidationException(
                    "Für eine Vorlage mit relativen Terminen muss ein Projektstartdatum angegeben werden."
            );
        }
        return project.getStartDate().plusDays(relativeDay);
    }

    private PlanElementViewDto toViewElement(PlanElement element) {
        if (element instanceof Task task) {
            return toViewElement(task);
        }
        if (element instanceof Milestone milestone) {
            return toViewElement(milestone);
        }
        throw new IllegalStateException("Nicht unterstützter Planelementtyp: " + element.getClass().getName());
    }

    private PlanElementViewDto toViewElement(Task task) {
        PlanElementViewDto dto = baseViewElement(task, PlanElementType.TASK);
        dto.setRelevantDate(task.getDueDate());
        dto.setTaskStatus(task.getStatus());
        dto.setTaskPriority(task.getPriority());
        return dto;
    }

    private PlanElementViewDto toViewElement(Milestone milestone) {
        PlanElementViewDto dto = baseViewElement(milestone, PlanElementType.MILESTONE);
        dto.setRelevantDate(milestone.getDueDate());
        dto.setMilestoneCompleted(milestone.isCompleted());
        return dto;
    }

    private PlanElementViewDto baseViewElement(PlanElement element, PlanElementType type) {
        PlanElementViewDto dto = new PlanElementViewDto();
        dto.setId(element.getId());
        dto.setType(type);
        dto.setTitle(element.getTitle());
        dto.setDescription(element.getDescription());
        dto.setPlanSectionId(element.getPlanSection() == null ? null : element.getPlanSection().getId());
        dto.setSortOrder(element.getSortOrder());
        return dto;
    }
}
