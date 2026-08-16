package de.melinadanhier.projectflow.plancontainer.template.service;

import de.melinadanhier.projectflow.plancontainer.template.mapper.TemplateMapper;
import de.melinadanhier.projectflow.plancontainer.template.repository.TemplateRepository;
import de.melinadanhier.projectflow.plancontainer.template.dto.TemplateSummaryDto;
import de.melinadanhier.projectflow.plancontainer.template.dto.TemplateDetailsDto;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.planelement.dto.TaskDependencyDto;
import de.melinadanhier.projectflow.planelement.mapper.PlanElementMapper;
import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.planelement.repository.MilestoneRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import de.melinadanhier.projectflow.planelement.repository.TaskRepository;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final TemplateMapper templateMapper;
    private final PlanSectionRepository planSectionRepository;
    private final TaskRepository taskRepository;
    private final MilestoneRepository milestoneRepository;
    private final PlanElementMapper planElementMapper;

    @Transactional(readOnly = true)
    public List<TemplateSummaryDto> getTemplates() {
        return templateRepository.findAllByActiveTrueOrderByTitleAsc().stream()
                .map(templateMapper::toSummaryDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<TemplateSummaryDto> findRecommendation(
            TemplateCategory category,
            String projectType
    ) {
        if (category == null) {
            return Optional.empty();
        }
        String normalizedProjectType = normalize(projectType);
        return getTemplates().stream()
                .filter(template -> template.getCategory() == category)
                .sorted((left, right) -> Integer.compare(
                        recommendationScore(right, normalizedProjectType),
                        recommendationScore(left, normalizedProjectType)))
                .findFirst();
    }

    @Transactional(readOnly = true)
    public TemplateDetailsDto getTemplate(UUID templateId) {
        var template = templateRepository.findByIdAndActiveTrue(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Vorlage wurde nicht gefunden."));
        var tasks = taskRepository.findPlanTasks(templateId);
        TemplateDetailsDto dto = templateMapper.toDetailsDto(template);
        dto.setSections(planSectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(templateId).stream()
                .map(planElementMapper::toDto)
                .toList());
        dto.setTasks(tasks.stream().map(planElementMapper::toDetailsDto).toList());
        dto.setMilestones(milestoneRepository.findAllByPlanContainerIdOrderBySortOrderAsc(templateId).stream()
                .map(planElementMapper::toDetailsDto)
                .toList());
        dto.setDependencies(tasks.stream()
                .flatMap(successor -> successor.getPrerequisites().stream()
                        .map(prerequisite -> new TaskDependencyDto(
                                prerequisite.getId(), prerequisite.getTitle(),
                                successor.getId(), successor.getTitle())))
                .toList());
        return dto;
    }

    private int recommendationScore(TemplateSummaryDto template, String normalizedProjectType) {
        if (normalizedProjectType == null) {
            return 1;
        }
        return normalizedProjectType.equals(normalize(template.getProjectType())) ? 2 : 1;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
