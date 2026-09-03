package de.melinadanhier.projectflow.planelement.service;

import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.planelement.dto.MilestoneDetailsDto;
import de.melinadanhier.projectflow.planelement.dto.MilestoneForm;
import de.melinadanhier.projectflow.planelement.mapper.PlanElementMapper;
import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import de.melinadanhier.projectflow.planelement.model.Milestone;
import de.melinadanhier.projectflow.planelement.model.PlanElement;
import de.melinadanhier.projectflow.planelement.model.PlanSection;
import de.melinadanhier.projectflow.planelement.repository.MilestoneRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanElementRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final PlanElementRepository planElementRepository;
    private final PlanSectionRepository planSectionRepository;
    private final ProjectAuthorizationService authorizationService;
    private final PlanElementMapper planElementMapper;

    @Transactional
    public MilestoneDetailsDto createMilestone(UUID projectId, MilestoneForm form, UUID userId) {
        Project project = authorizationService.requireEditableMemberForUpdate(projectId, userId).getProject();
        PlanSection section = resolveSection(projectId, form.getPlanSectionId());
        Milestone milestone = new Milestone();
        milestone.setPlanContainer(project);
        milestone.setPlanSection(section);
        milestone.setOrigin(ElementOrigin.USER);
        milestone.setRelativeDueDay(null);
        apply(milestone, form);
        List<PlanElement> siblings = loadSiblings(projectId, section);
        siblings.add(form.getSortOrder() == null
                ? siblings.size() : Math.min(form.getSortOrder(), siblings.size()), milestone);
        resequence(siblings);
        return planElementMapper.toDetailsDto(milestoneRepository.save(milestone));
    }

    @Transactional(readOnly = true)
    public MilestoneDetailsDto getMilestoneDetail(UUID projectId, UUID milestoneId, UUID userId) {
        ProjectMember membership = authorizationService.requireMember(projectId, userId);
        return buildMilestoneDetail(projectId, milestoneId, authorizationService.isEditable(membership));
    }

    @Transactional(readOnly = true)
    public MilestoneDetailsDto getMilestoneForEditing(UUID projectId, UUID milestoneId, UUID userId) {
        authorizationService.requireEditableMember(projectId, userId);
        return buildMilestoneDetail(projectId, milestoneId, true);
    }

    @Transactional(readOnly = true)
    public MilestoneDetailsDto getMilestoneCreationContext(UUID projectId, UUID userId) {
        authorizationService.requireEditableMember(projectId, userId);
        MilestoneDetailsDto dto = new MilestoneDetailsDto();
        dto.setPlanContainerId(projectId);
        dto.setEditable(true);
        populateSections(dto, projectId);
        return dto;
    }

    private MilestoneDetailsDto buildMilestoneDetail(UUID projectId, UUID milestoneId, boolean editable) {
        Milestone milestone = requireMilestone(projectId, milestoneId);
        MilestoneDetailsDto dto = planElementMapper.toDetailsDto(milestone);
        dto.setEditable(editable);
        populateSections(dto, projectId);
        return dto;
    }

    private void populateSections(MilestoneDetailsDto dto, UUID projectId) {
        dto.setAvailableSections(planSectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(projectId).stream()
                .map(planElementMapper::toDto)
                .toList());
    }

    @Transactional
    public MilestoneDetailsDto updateMilestone(
            UUID projectId,
            UUID milestoneId,
            MilestoneForm form,
            UUID userId
    ) {
        authorizationService.requireEditableMemberForUpdate(projectId, userId);
        Milestone milestone = requireMilestone(projectId, milestoneId);
        requireCurrentVersion(milestone.getLockVersion(), form.getLockVersion());
        PlanSection oldSection = milestone.getPlanSection();
        PlanSection newSection = resolveSection(projectId, form.getPlanSectionId());
        apply(milestone, form);

        List<PlanElement> oldSiblings = loadSiblings(projectId, oldSection);
        oldSiblings.removeIf(element -> element.getId().equals(milestone.getId()));
        resequence(oldSiblings);
        boolean sectionUnchanged = oldSection == null
                ? newSection == null
                : newSection != null && oldSection.getId().equals(newSection.getId());
        List<PlanElement> targetSiblings = sectionUnchanged
                ? oldSiblings : loadSiblings(projectId, newSection);
        milestone.setPlanSection(newSection);
        targetSiblings.add(form.getSortOrder() == null
                ? targetSiblings.size() : Math.min(form.getSortOrder(), targetSiblings.size()), milestone);
        resequence(targetSiblings);
        return planElementMapper.toDetailsDto(milestone);
    }

    @Transactional
    public void deleteMilestone(UUID projectId, UUID milestoneId, UUID userId) {
        authorizationService.requireEditableMemberForUpdate(projectId, userId);
        Milestone milestone = requireMilestone(projectId, milestoneId);
        PlanSection section = milestone.getPlanSection();
        milestoneRepository.delete(milestone);
        milestoneRepository.flush();
        resequence(loadSiblings(projectId, section));
    }

    private Milestone requireMilestone(UUID projectId, UUID milestoneId) {
        return milestoneRepository.findByIdAndPlanContainerId(milestoneId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Meilenstein wurde nicht gefunden."));
    }

    private PlanSection resolveSection(UUID projectId, UUID sectionId) {
        if (sectionId == null) {
            return null;
        }
        return planSectionRepository.findByIdAndPlanContainerId(sectionId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projektbereich wurde nicht gefunden."));
    }

    private void apply(Milestone milestone, MilestoneForm form) {
        String title = form.getTitle().trim();
        String description = form.getDescription() == null || form.getDescription().isBlank()
                ? null : form.getDescription().trim();
        if (!java.util.Objects.equals(milestone.getTitle(), title)
                || !java.util.Objects.equals(milestone.getDescription(), description)
                || !java.util.Objects.equals(milestone.getDueDate(), form.getDueDate())) {
            milestone.setOrigin(milestone.getOrigin().modifiedByUser());
        }
        milestone.setTitle(title);
        milestone.setDescription(description);
        milestone.setDueDate(form.getDueDate());
        milestone.setRelativeDueDay(null);
        milestone.setCompleted(form.isCompleted());
    }

    private List<PlanElement> loadSiblings(UUID projectId, PlanSection section) {
        return new ArrayList<>(section == null
                ? planElementRepository.findAllByPlanContainerIdAndPlanSectionIsNullOrderBySortOrderAsc(projectId)
                : planElementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
                        projectId, section.getId()));
    }

    private void resequence(List<PlanElement> elements) {
        for (int index = 0; index < elements.size(); index++) {
            elements.get(index).setSortOrder(index);
        }
    }

    private void requireCurrentVersion(long actualVersion, Long submittedVersion) {
        if (submittedVersion == null) {
            throw new DomainValidationException("Die Versionsnummer des Meilensteins fehlt.");
        }
        if (submittedVersion != actualVersion) {
            throw new ConflictException("Der Meilenstein wurde zwischenzeitlich geändert. Bitte lade die Seite neu.");
        }
    }
}
