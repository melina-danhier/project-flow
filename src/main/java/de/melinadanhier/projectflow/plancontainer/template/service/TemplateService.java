package de.melinadanhier.projectflow.plancontainer.template.service;

import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.template.mapper.TemplateMapper;
import de.melinadanhier.projectflow.plancontainer.template.repository.TemplateRepository;
import de.melinadanhier.projectflow.plancontainer.template.dto.TemplateSummaryDto;
import de.melinadanhier.projectflow.plancontainer.template.dto.TemplateDetailsDto;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.planelement.dto.TaskDependencyDto;
import de.melinadanhier.projectflow.planelement.mapper.PlanElementMapper;
import de.melinadanhier.projectflow.planelement.repository.PlanElementRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import de.melinadanhier.projectflow.planelement.service.PlanElementCollection;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
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
    private final PlanElementRepository planElementRepository;
    private final PlanElementMapper planElementMapper;

    @Transactional(readOnly = true)
    public List<TemplateSummaryDto> getTemplates() {
        return templateRepository.findAllByActiveTrueOrderByTitleAsc().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TemplateSummaryDto> getTemplates(ProjectCategory category) {
        ProjectCategory selected = category == null ? ProjectCategory.OTHER : category;
        return getTemplates().stream()
                .filter(template -> template.getCategory() == selected
                        || (selected == ProjectCategory.OTHER && template.getCategory() == null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TemplateSummaryDto> search(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.GERMAN);
        if (normalized.isEmpty()) {
            return List.of();
        }
        return getTemplates().stream()
                .filter(template -> contains(template.getTitle(), normalized)
                        || contains(template.getDescription(), normalized))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TemplateSummaryDto> recommendations(
            ProjectCategory category,
            ProjectSubCategory subcategory
    ) {
        if (category == null) {
            return List.of();
        }
        List<TemplateSummaryDto> categoryMatches = getTemplates().stream()
                .filter(template -> template.getCategory() == category)
                .toList();
        if (subcategory == null) {
            return categoryMatches;
        }
        List<TemplateSummaryDto> exactMatches = categoryMatches.stream()
                .filter(template -> template.getSubcategory() == subcategory)
                .toList();
        return exactMatches.isEmpty() ? categoryMatches : exactMatches;
    }

    @Transactional(readOnly = true)
    public Optional<TemplateSummaryDto> findRecommendation(
            ProjectCategory category,
            ProjectSubCategory subcategory
    ) {
        if (category == null) {
            return Optional.empty();
        }
        return getTemplates().stream()
                .filter(template -> template.getCategory() == category)
                .sorted((left, right) -> Integer.compare(
                        recommendationScore(right, subcategory),
                        recommendationScore(left, subcategory)))
                .findFirst();
    }

    @Transactional(readOnly = true)
    public TemplateDetailsDto getTemplate(UUID templateId) {
        var template = templateRepository.findByIdAndActiveTrue(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Vorlage wurde nicht gefunden."));
        PlanElementCollection elements = PlanElementCollection.copyOf(
                planElementRepository.findPlanElements(templateId));
        var tasks = elements.tasks();
        var milestones = elements.milestones();
        TemplateDetailsDto dto = templateMapper.toDetailsDto(template);
        dto.setSections(planSectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(templateId).stream()
                .map(planElementMapper::toDto)
                .toList());
        dto.setTasks(tasks.stream().map(planElementMapper::toDetailsDto).toList());
        dto.setMilestones(milestones.stream().map(planElementMapper::toDetailsDto).toList());
        dto.setDependencies(tasks.stream()
                .flatMap(successor -> successor.getPrerequisites().stream()
                        .map(prerequisite -> new TaskDependencyDto(
                                prerequisite.getId(), prerequisite.getTitle(),
                                successor.getId(), successor.getTitle())))
                .toList());
        return dto;
    }

    @Transactional(readOnly = true)
    public TemplateDateAssessment assessRelativeDates(UUID templateId, java.time.LocalDate projectStartDate) {
        return assessRelativeDates(templateId, projectStartDate, null);
    }

    @Transactional(readOnly = true)
    public TemplateDateAssessment assessRelativeDates(
            UUID templateId,
            java.time.LocalDate projectStartDate,
            java.time.LocalDate projectEndDate
    ) {
        var template = templateRepository.findByIdAndActiveTrue(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Vorlage wurde nicht gefunden."));
        return TemplateDatePolicy.assess(template, projectStartDate, projectEndDate);
    }

    private TemplateSummaryDto toSummary(de.melinadanhier.projectflow.plancontainer.template.model.Template template) {
        TemplateSummaryDto summary = templateMapper.toSummaryDto(template);
        summary.setTaskCount((int) template.getElements().stream()
                .filter(de.melinadanhier.projectflow.planelement.model.Task.class::isInstance).count());
        summary.setMilestoneCount((int) template.getElements().stream()
                .filter(de.melinadanhier.projectflow.planelement.model.Milestone.class::isInstance).count());
        return summary;
    }

    private boolean contains(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.GERMAN).contains(normalizedQuery);
    }

    private int recommendationScore(TemplateSummaryDto template, ProjectSubCategory subcategory) {
        if (subcategory == null) {
            return 1;
        }
        return subcategory == template.getSubcategory() ? 2 : 1;
    }

}
