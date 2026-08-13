package com.melina.projectflow.plancontainer.project.service;

import com.melina.projectflow.common.exception.ResourceNotFoundException;
import com.melina.projectflow.plancontainer.project.dto.ProjectCreateForm;
import com.melina.projectflow.plancontainer.project.dto.ProjectDetailsDto;
import com.melina.projectflow.plancontainer.project.dto.ProjectSummaryDto;
import com.melina.projectflow.plancontainer.project.dto.ProjectUpdateForm;
import com.melina.projectflow.plancontainer.project.mapper.ProjectMapper;
import com.melina.projectflow.plancontainer.project.model.CreationType;
import com.melina.projectflow.plancontainer.project.model.Project;
import com.melina.projectflow.plancontainer.project.model.ProjectMember;
import com.melina.projectflow.plancontainer.project.model.ProjectMemberRole;
import com.melina.projectflow.plancontainer.project.model.ProjectStatus;
import com.melina.projectflow.plancontainer.project.repository.ProjectRepository;
import com.melina.projectflow.plancontainer.template.model.Template;
import com.melina.projectflow.plancontainer.template.repository.TemplateRepository;
import com.melina.projectflow.planelement.model.ElementOrigin;
import com.melina.projectflow.planelement.model.Milestone;
import com.melina.projectflow.planelement.model.PlanElement;
import com.melina.projectflow.planelement.model.PlanSection;
import com.melina.projectflow.planelement.model.Task;
import com.melina.projectflow.user.model.User;
import com.melina.projectflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final UserRepository userRepository;
    private final TemplateRepository templateRepository;
    private final ProjectAuthorizationService authorizationService;

    @Transactional
    public ProjectDetailsDto createProject(ProjectCreateForm form, UUID ownerUserId) {
        User owner = userRepository.findById(ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Nutzerkonto wurde nicht gefunden."));
        Project project = initializeProject(form, owner, form.getCreationType());
        return projectMapper.toDetailsDto(projectRepository.save(project));
    }

    @Transactional
    public ProjectDetailsDto createProjectFromTemplate(UUID templateId, ProjectCreateForm form, UUID ownerUserId) {
        User owner = userRepository.findById(ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Nutzerkonto wurde nicht gefunden."));
        Template template = templateRepository.findById(templateId)
                .filter(Template::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Vorlage wurde nicht gefunden."));
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
        project.setCreationType(creationType);
        project.setStatus(creationType == CreationType.AI ? ProjectStatus.DRAFT : ProjectStatus.ACTIVE);
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
            copy.setStartDate(source.getStartDate());
            copy.setEndDate(source.getEndDate());
            copy.setRelativeStartDay(source.getRelativeStartDay());
            copy.setRelativeEndDay(source.getRelativeEndDay());
            copy.setSortOrder(source.getSortOrder());
            copy.setOrigin(ElementOrigin.TEMPLATE);
            copy.setHasCriticalAssumption(source.isHasCriticalAssumption());
            project.addSection(copy);
            sections.put(source, copy);
        }

        Map<PlanElement, PlanElement> elements = new HashMap<>();
        for (PlanElement source : template.getElements()) {
            PlanElement copy = copyElement(source);
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

    private PlanElement copyElement(PlanElement source) {
        PlanElement copy;
        if (source instanceof Task sourceTask) {
            Task task = new Task();
            task.setPriority(sourceTask.getPriority());
            task.setStartDate(sourceTask.getStartDate());
            task.setDueDate(sourceTask.getDueDate());
            task.setRelativeStartDay(sourceTask.getRelativeStartDay());
            task.setRelativeDueDay(sourceTask.getRelativeDueDay());
            copy = task;
        } else if (source instanceof Milestone sourceMilestone) {
            Milestone milestone = new Milestone();
            milestone.setDueDate(sourceMilestone.getDueDate());
            milestone.setRelativeDueDay(sourceMilestone.getRelativeDueDay());
            copy = milestone;
        } else {
            throw new IllegalStateException("Nicht unterstützter Vorlageninhalt.");
        }
        copy.setTitle(source.getTitle());
        copy.setDescription(source.getDescription());
        copy.setSortOrder(source.getSortOrder());
        copy.setOrigin(ElementOrigin.TEMPLATE);
        copy.setHasCriticalAssumption(source.isHasCriticalAssumption());
        return copy;
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryDto> findAccessibleProjects(UUID userId) {
        return projectRepository.findAllAccessibleByUserId(userId).stream()
                .map(projectMapper::toSummaryDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectDetailsDto getProject(UUID projectId, UUID userId) {
        authorizationService.requireMember(projectId, userId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projekt wurde nicht gefunden."));
        return projectMapper.toDetailsDto(project);
    }

    @Transactional
    public ProjectDetailsDto updateProject(UUID projectId, ProjectUpdateForm form, UUID userId) {
        authorizationService.requireOwner(projectId, userId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projekt wurde nicht gefunden."));
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
}
