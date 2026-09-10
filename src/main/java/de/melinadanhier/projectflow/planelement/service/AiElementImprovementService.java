package de.melinadanhier.projectflow.planelement.service;

import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementContent;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementElementType;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementProjectContext;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementPlanContext;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementRequest;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementResponse;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementSectionContext;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementTemporalContext;
import de.melinadanhier.projectflow.ai.model.improvement.AiReplanPlacementResponse;
import de.melinadanhier.projectflow.ai.model.improvement.AiFeedbackType;
import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.provider.AiClient;
import de.melinadanhier.projectflow.ai.validation.improvement.AiImprovementResponseValidator;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.planelement.dto.improvement.AiImprovementForm;
import de.melinadanhier.projectflow.planelement.dto.improvement.AiImprovementProposal;
import de.melinadanhier.projectflow.planelement.dto.improvement.AiReplanPlacementProposal;
import de.melinadanhier.projectflow.planelement.model.Milestone;
import de.melinadanhier.projectflow.planelement.model.PlanElement;
import de.melinadanhier.projectflow.planelement.model.PlanSection;
import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.planelement.repository.MilestoneRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanElementRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import de.melinadanhier.projectflow.planelement.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiElementImprovementService {

    private final ProjectAuthorizationService authorizationService;
    private final PlanSectionRepository sectionRepository;
    private final TaskRepository taskRepository;
    private final MilestoneRepository milestoneRepository;
    private final PlanElementRepository planElementRepository;
    private final AiClient aiClient;
    private final AiImprovementResponseValidator responseValidator;

    @Transactional(readOnly = true)
    public void requireImprovementAccess(UUID projectId, UUID userId) {
        authorizationService.requireEditableMember(projectId, userId);
    }

    @Transactional(readOnly = true)
    public void requireImprovementAccess(
            UUID projectId, AiImprovementElementType elementType, UUID elementId, UUID userId) {
        authorizationService.requireEditableMember(projectId, userId);
        loadContent(projectId, elementType, elementId);
    }

    @Transactional(readOnly = true)
    public AiImprovementProposal propose(
            UUID projectId,
            AiImprovementElementType elementType,
            UUID elementId,
            AiImprovementForm form,
            UUID userId
    ) {
        if (form == null || form.getFeedbackType() == null) {
            throw new DomainValidationException("Für die KI-Verbesserung ist ein Feedback-Typ erforderlich.");
        }
        if (!form.getFeedbackType().supports(elementType)) {
            throw new DomainValidationException("Die gewählte KI-Aktion ist für dieses Planelement nicht verfügbar.");
        }
        String comment = normalizeComment(form.getComment());
        Project project = authorizationService.requireEditableMember(projectId, userId).getProject();
        AiImprovementContent original = loadContent(projectId, elementType, elementId);
        long version = loadVersion(projectId, elementType, elementId);
        AiImprovementPlanContext currentPlan = form.getFeedbackType() == AiFeedbackType.REPLAN
                ? planContext(projectId, elementId, project.getSortMode()) : null;
        var request = new AiImprovementRequest(
                form.getFeedbackType(),
                comment,
                projectContext(project, form.getFeedbackType()),
                sectionContext(projectId, elementType, elementId, form.getFeedbackType()),
                currentPlan,
                original
        );
        AiImprovementResponse response = preserveDatesWithoutTemporalBasis(
                request, aiClient.improveElement(request));
        AiImprovementContent proposed = responseValidator.validate(
                elementType, original, form.getFeedbackType(), comment, response);
        String explanation = responseValidator.validateExplanation(form.getFeedbackType(), response);
        AiReplanPlacementProposal placement = form.getFeedbackType() == AiFeedbackType.REPLAN
                ? validatePlacement(projectId, elementId, response.placement(), proposed.dueDate(),
                        project.getSortMode(), currentPlan.sortMode() == SortMode.DATE, currentPlan)
                : null;
        return new AiImprovementProposal(
                UUID.randomUUID(), projectId, elementId, elementType, version,
                form.getFeedbackType(), comment, explanation, placement, original, proposed);
    }

    private AiImprovementResponse preserveDatesWithoutTemporalBasis(
            AiImprovementRequest request, AiImprovementResponse response) {
        if (request.feedbackType() != AiFeedbackType.REPLAN
                || AiImprovementTemporalContext.isAvailable(request)) {
            return response;
        }
        AiImprovementContent original = request.element();
        return new AiImprovementResponse(
                response.elementType(), response.title(), response.description(), response.priority(),
                response.estimatedHours(), original.startDate(), original.dueDate(), response.placement(),
                response.explanation());
    }

    @Transactional
    public void confirm(AiImprovementProposal proposal, UUID userId) {
        Objects.requireNonNull(proposal, "proposal darf nicht null sein");
        Project project = authorizationService.requireEditableMemberForUpdate(proposal.projectId(), userId).getProject();
        if (proposal.feedbackType() == AiFeedbackType.REPLAN
                && proposal.placement() != null
                && proposal.placement().sortMode() != project.getSortMode()) {
            throw stalePlacement("Der Sortiermodus wurde seit dem KI-Vorschlag geändert.");
        }
        AiImprovementContent proposed = Objects.requireNonNull(
                proposal.proposed(), "Vorschlagsinhalt darf nicht null sein");
        responseValidator.validate(
                proposal.elementType(),
                Objects.requireNonNull(proposal.original(), "Ursprünglicher Vorschlagsinhalt darf nicht null sein"),
                proposal.feedbackType(),
                proposal.comment(),
                new de.melinadanhier.projectflow.ai.model.improvement.AiImprovementResponse(
                        proposed.elementType(), proposed.title(), proposed.description(), proposed.priority(),
                        proposed.estimatedHours(), proposed.startDate(), proposed.dueDate(),
                        placementResponse(proposal.placement()), proposal.explanation()));
        switch (proposal.elementType()) {
            case SECTION -> applySection(proposal);
            case TASK -> applyTask(proposal);
            case MILESTONE -> applyMilestone(proposal);
        }
    }

    private void applySection(AiImprovementProposal proposal) {
        PlanSection section = sectionRepository.findByIdAndPlanContainerId(
                        proposal.elementId(), proposal.projectId())
                .orElseThrow(this::notFound);
        requireVersion(section.getLockVersion(), proposal.elementVersion());
        AiImprovementContent content = proposal.proposed();
        if (!sameCommon(section.getTitle(), section.getDescription(), content)) {
            section.setTitle(content.title());
            section.setDescription(content.description());
            section.setOrigin(section.getOrigin().modifiedByAi());
        }
        sectionRepository.flush();
    }

    private void applyTask(AiImprovementProposal proposal) {
        Task task = taskRepository.findByIdAndPlanContainerId(proposal.elementId(), proposal.projectId())
                .orElseThrow(this::notFound);
        requireVersion(task.getLockVersion(), proposal.elementVersion());
        AiImprovementContent content = proposal.proposed();
        boolean changed = switch (proposal.feedbackType()) {
            case IMPROVE, EXPAND, SIMPLIFY -> !sameCommon(task.getTitle(), task.getDescription(), content);
            case REPLAN -> !Objects.equals(task.getStartDate(), content.startDate())
                    || !Objects.equals(task.getDueDate(), content.dueDate())
                    || proposal.placement().changePlacement();
            case ESTIMATE_EFFORT -> !Objects.equals(task.getEstimatedHours(), content.estimatedHours());
        };
        if (changed) {
            switch (proposal.feedbackType()) {
                case IMPROVE, EXPAND, SIMPLIFY -> {
                    task.setTitle(content.title());
                    task.setDescription(content.description());
                }
                case REPLAN -> {
                    task.setStartDate(content.startDate());
                    task.setDueDate(content.dueDate());
                    applyPlacement(proposal, task, content.dueDate());
                }
                case ESTIMATE_EFFORT -> task.setEstimatedHours(content.estimatedHours());
            }
            task.setOrigin(task.getOrigin().modifiedByAi());
        }
        taskRepository.flush();
    }

    private void applyMilestone(AiImprovementProposal proposal) {
        Milestone milestone = milestoneRepository.findByIdAndPlanContainerId(
                        proposal.elementId(), proposal.projectId())
                .orElseThrow(this::notFound);
        requireVersion(milestone.getLockVersion(), proposal.elementVersion());
        AiImprovementContent content = proposal.proposed();
        boolean changed = proposal.feedbackType() == AiFeedbackType.REPLAN
                ? !Objects.equals(milestone.getDueDate(), content.dueDate())
                        || proposal.placement().changePlacement()
                : !sameCommon(milestone.getTitle(), milestone.getDescription(), content);
        if (changed) {
            if (proposal.feedbackType() == AiFeedbackType.REPLAN) {
                milestone.setDueDate(content.dueDate());
                applyPlacement(proposal, milestone, content.dueDate());
            } else {
                milestone.setTitle(content.title());
                milestone.setDescription(content.description());
            }
            milestone.setOrigin(milestone.getOrigin().modifiedByAi());
        }
        milestoneRepository.flush();
    }

    private AiReplanPlacementProposal validatePlacement(
            UUID projectId,
            UUID selectedElementId,
            AiReplanPlacementResponse response,
            java.time.LocalDate proposedDueDate,
            SortMode sortMode,
            boolean dateOrderingActive,
            AiImprovementPlanContext currentPlan
    ) {
        PlanElement selected = planElementRepository.findByIdAndPlanContainerId(selectedElementId, projectId)
                .orElseThrow(this::notFound);
        String originalSectionLabel = sectionLabel(selected.getPlanSection());
        String originalPositionLabel = currentPositionLabel(projectId, selected, dateOrderingActive);
        if (!response.changePlacement()) {
            return new AiReplanPlacementProposal(false,
                    sectionId(selected.getPlanSection()), originalSectionLabel, originalPositionLabel,
                    sectionId(selected.getPlanSection()), originalSectionLabel, "Unverändert", null, null,
                    sortMode, dateOrderingActive);
        }

        UUID targetSectionId = parseAiId(response.targetSectionId(), "targetSectionId");
        requireAllowlistedSection(currentPlan, targetSectionId);
        PlanSection targetSection = targetSectionId == null ? null
                : sectionRepository.findByIdAndPlanContainerId(targetSectionId, projectId)
                        .orElseThrow(() -> invalidPlacement("Die vorgeschlagene Ziel-Section ist ungültig."));
        UUID beforeId = parseAiId(response.beforeElementId(), "beforeElementId");
        UUID afterId = parseAiId(response.afterElementId(), "afterElementId");
        requireAllowlistedElement(currentPlan, beforeId);
        requireAllowlistedElement(currentPlan, afterId);
        PlanElement reference = resolveAiReference(projectId, selectedElementId, targetSection, beforeId, afterId);
        if (dateOrderingActive && reference != null
                && !Objects.equals(date(reference), proposedDueDate)) {
            throw invalidPlacement(
                    "Im Datumsmodus muss das Referenzelement dasselbe Fälligkeitsdatum besitzen.");
        }
        String proposedPositionLabel = beforeId != null
                ? "vor \"" + reference.getTitle() + "\""
                : afterId != null ? "nach \"" + reference.getTitle() + "\""
                : fallbackTargetPositionLabel(projectId, selected, targetSection,
                        dateOrderingActive, proposedDueDate);
        return new AiReplanPlacementProposal(true,
                sectionId(selected.getPlanSection()), originalSectionLabel, originalPositionLabel,
                targetSectionId, sectionLabel(targetSection), proposedPositionLabel, beforeId, afterId,
                sortMode, dateOrderingActive);
    }

    private void applyPlacement(
            AiImprovementProposal proposal,
            PlanElement moved,
            java.time.LocalDate proposedDueDate
    ) {
        AiReplanPlacementProposal placement = Objects.requireNonNull(
                proposal.placement(), "REPLAN-Platzierung darf nicht null sein");
        if (!placement.changePlacement()) return;

        PlanSection targetSection = placement.targetSectionId() == null ? null
                : sectionRepository.findByIdAndPlanContainerId(placement.targetSectionId(), proposal.projectId())
                        .orElseThrow(() -> stalePlacement("Die vorgeschlagene Ziel-Section existiert nicht mehr."));
        PlanElement reference = resolveCurrentReference(
                proposal.projectId(), moved.getId(), targetSection,
                placement.beforeElementId(), placement.afterElementId());
        if (placement.dateOrderingActive() && reference != null
                && !Objects.equals(date(reference), proposedDueDate)) {
            throw stalePlacement("Das Referenzelement gehört nicht mehr zur passenden Datumsgruppe.");
        }

        List<PlanElement> candidates = loadSiblings(proposal.projectId(), targetSection);
        candidates.removeIf(element -> element.getId().equals(moved.getId()));
        if (placement.dateOrderingActive()) {
            candidates.removeIf(element -> !Objects.equals(date(element), proposedDueDate));
        }
        int targetPosition;
        if (placement.beforeElementId() != null) {
            targetPosition = indexOf(candidates, placement.beforeElementId());
        } else if (placement.afterElementId() != null) {
            targetPosition = indexOf(candidates, placement.afterElementId()) + 1;
        } else {
            targetPosition = candidates.size();
        }
        moved.setPlanSection(targetSection);
        PlanOrdering.place(candidates, moved, targetPosition,
                PlanElement::getSortOrder, PlanElement::setSortOrder);
    }

    private PlanElement resolveAiReference(
            UUID projectId, UUID selectedElementId, PlanSection targetSection, UUID beforeId, UUID afterId) {
        UUID referenceId = beforeId != null ? beforeId : afterId;
        if (referenceId == null) return null;
        if (referenceId.equals(selectedElementId)) {
            throw invalidPlacement("Das ausgewählte Element darf nicht sich selbst referenzieren.");
        }
        PlanElement reference = planElementRepository.findByIdAndPlanContainerId(referenceId, projectId)
                .orElseThrow(() -> invalidPlacement("Das vorgeschlagene Referenzelement ist ungültig."));
        if (!sameSection(reference.getPlanSection(), targetSection)) {
            throw invalidPlacement("Das Referenzelement gehört nicht zur vorgeschlagenen Ziel-Section.");
        }
        return reference;
    }

    private PlanElement resolveCurrentReference(
            UUID projectId, UUID selectedElementId, PlanSection targetSection, UUID beforeId, UUID afterId) {
        UUID referenceId = beforeId != null ? beforeId : afterId;
        if (referenceId == null) return null;
        if (referenceId.equals(selectedElementId)) {
            throw stalePlacement("Das ausgewählte Element darf nicht sich selbst referenzieren.");
        }
        PlanElement reference = planElementRepository.findByIdAndPlanContainerId(referenceId, projectId)
                .orElseThrow(() -> stalePlacement("Das vorgeschlagene Referenzelement existiert nicht mehr."));
        if (!sameSection(reference.getPlanSection(), targetSection)) {
            throw stalePlacement("Das Referenzelement gehört nicht mehr zur vorgeschlagenen Ziel-Section.");
        }
        return reference;
    }

    private String currentPositionLabel(
            UUID projectId,
            PlanElement selected,
            boolean dateOrderingActive
    ) {
        List<PlanElement> siblings = loadSiblings(projectId, selected.getPlanSection());
        if (dateOrderingActive) {
            java.time.LocalDate selectedDate = date(selected);
            siblings.removeIf(element -> !Objects.equals(date(element), selectedDate));
        }
        int index = indexOf(siblings, selected.getId());
        if (index + 1 < siblings.size()) {
            return "vor \"" + siblings.get(index + 1).getTitle() + "\"";
        }
        if (index > 0) {
            return "nach \"" + siblings.get(index - 1).getTitle() + "\"";
        }
        return "an den Anfang der Section";
    }

    private String fallbackTargetPositionLabel(
            UUID projectId,
            PlanElement selected,
            PlanSection targetSection,
            boolean dateOrderingActive,
            java.time.LocalDate proposedDueDate
    ) {
        List<PlanElement> candidates = loadSiblings(projectId, targetSection);
        candidates.removeIf(element -> element.getId().equals(selected.getId()));
        if (dateOrderingActive) {
            candidates.removeIf(element -> !Objects.equals(date(element), proposedDueDate));
        }
        return candidates.isEmpty() ? "an den Anfang der Section" : "ans Ende der Section";
    }

    private List<PlanElement> loadSiblings(UUID projectId, PlanSection section) {
        return new ArrayList<>(section == null
                ? planElementRepository.findAllByPlanContainerIdAndPlanSectionIsNullOrderBySortOrderAsc(projectId)
                : planElementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
                        projectId, section.getId()));
    }

    private int indexOf(List<PlanElement> elements, UUID id) {
        int index = findIndex(elements, id);
        if (index >= 0) return index;
        throw stalePlacement("Das Referenzelement ist in der Ziel-Section nicht mehr verfügbar.");
    }

    private int findIndex(List<PlanElement> elements, UUID id) {
        for (int index = 0; index < elements.size(); index++) {
            if (elements.get(index).getId().equals(id)) return index;
        }
        return -1;
    }

    private java.time.LocalDate date(PlanElement element) {
        if (element instanceof Task task) return task.getDueDate();
        if (element instanceof Milestone milestone) return milestone.getDueDate();
        return null;
    }

    private boolean sameSection(PlanSection first, PlanSection second) {
        return Objects.equals(sectionId(first), sectionId(second));
    }

    private UUID sectionId(PlanSection section) {
        return section == null ? null : section.getId();
    }

    private String sectionLabel(PlanSection section) {
        return section == null ? "Ohne Phase" : section.getTitle();
    }

    private UUID parseAiId(String value, String field) {
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw invalidPlacement("Die Angabe " + field + " enthält keine gültige ID.");
        }
    }

    private void requireAllowlistedSection(AiImprovementPlanContext currentPlan, UUID sectionId) {
        if (sectionId == null) return;
        boolean allowed = currentPlan.sections().stream()
                .map(AiImprovementPlanContext.Section::reference)
                .filter(Objects::nonNull)
                .anyMatch(sectionId.toString()::equals);
        if (!allowed) throw invalidPlacement("Die Ziel-Section stammt nicht aus dem übermittelten Plan.");
    }

    private void requireAllowlistedElement(AiImprovementPlanContext currentPlan, UUID elementId) {
        if (elementId == null) return;
        String reference = elementId.toString();
        boolean allowed = currentPlan.sections().stream().anyMatch(section ->
                section.elements().stream().anyMatch(element -> reference.equals(element.reference())));
        if (!allowed) throw invalidPlacement("Das Referenzelement stammt nicht aus dem übermittelten Plan.");
    }

    private AiReplanPlacementResponse placementResponse(AiReplanPlacementProposal placement) {
        if (placement == null) return null;
        return new AiReplanPlacementResponse(
                placement.changePlacement(),
                placement.changePlacement() && placement.targetSectionId() != null
                        ? placement.targetSectionId().toString() : null,
                placement.beforeElementId() == null ? null : placement.beforeElementId().toString(),
                placement.afterElementId() == null ? null : placement.afterElementId().toString());
    }

    private AiOutputValidationException invalidPlacement(String issue) {
        return new AiOutputValidationException(
                "Der KI-Vorschlag enthält eine ungültige Platzierung.",
                List.of("IMPROVEMENT_PLACEMENT | $.placement | " + issue));
    }

    private ConflictException stalePlacement(String message) {
        return new ConflictException(message + " Bitte erzeuge einen neuen KI-Vorschlag.");
    }

    private boolean sameCommon(String title, String description, AiImprovementContent content) {
        return Objects.equals(title, content.title()) && Objects.equals(description, content.description());
    }

    private AiImprovementProjectContext projectContext(Project project, AiFeedbackType action) {
        return switch (action) {
            case SIMPLIFY -> null;
            case IMPROVE, EXPAND -> new AiImprovementProjectContext(project.getTitle(), null, null, null);
            case ESTIMATE_EFFORT, REPLAN -> new AiImprovementProjectContext(
                    project.getTitle(), project.getDescription(), project.getStartDate(), project.getEndDate());
        };
    }

    private AiImprovementSectionContext sectionContext(
            UUID projectId, AiImprovementElementType type, UUID elementId, AiFeedbackType action) {
        if (action != AiFeedbackType.ESTIMATE_EFFORT || type != AiImprovementElementType.TASK) return null;
        Task task = taskRepository.findByIdAndPlanContainerId(elementId, projectId).orElseThrow(this::notFound);
        PlanSection section = task.getPlanSection();
        return section == null ? null : new AiImprovementSectionContext(section.getTitle(), section.getDescription());
    }

    private AiImprovementPlanContext planContext(UUID projectId, UUID selectedElementId, SortMode sortMode) {
        PlanElementCollection planElements = PlanElementCollection.copyOf(
                planElementRepository.findPlanElements(projectId));
        SortMode effectiveSortMode = effectiveSortMode(sortMode, planElements.all());
        List<AiImprovementPlanContext.Section> sections = new ArrayList<>();
        List<PlanSection> planSections = sectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(projectId);
        for (int index = 0; index < planSections.size(); index++) {
            sections.add(planSection(planSections.get(index), index + 1, planElements, effectiveSortMode));
        }
        List<PlanElement> unassigned = planElements.displayInSection(null, effectiveSortMode);
        if (!unassigned.isEmpty()) {
            sections.add(new AiImprovementPlanContext.Section(null, null, null, sections.size() + 1,
                    planElements(unassigned)));
        }
        return new AiImprovementPlanContext(effectiveSortMode, selectedElementId.toString(), List.copyOf(sections));
    }

    private SortMode effectiveSortMode(SortMode configuredSortMode, List<PlanElement> elements) {
        if (configuredSortMode != SortMode.DATE) return configuredSortMode;
        return elements.stream().anyMatch(element -> date(element) != null) ? SortMode.DATE : SortMode.MANUAL;
    }

    private AiImprovementPlanContext.Section planSection(
            PlanSection section, int position, PlanElementCollection planElements, SortMode sortMode) {
        List<PlanElement> elements = planElements.displayInSection(section.getId(), sortMode);
        return new AiImprovementPlanContext.Section(section.getId().toString(), section.getTitle(),
                section.getDescription(), position, planElements(elements));
    }

    private List<AiImprovementPlanContext.Element> planElements(List<PlanElement> displayOrder) {
        List<AiImprovementPlanContext.Element> result = new ArrayList<>(displayOrder.size());
        for (int index = 0; index < displayOrder.size(); index++) {
            result.add(planElement(displayOrder.get(index), index + 1));
        }
        return List.copyOf(result);
    }

    private AiImprovementPlanContext.Element planElement(PlanElement element, int position) {
        if (element instanceof Task task) {
            return new AiImprovementPlanContext.Element(AiImprovementElementType.TASK,
                    task.getId().toString(), task.getTitle(), task.getDescription(), position,
                    task.getPriority(), task.getEstimatedHours(), task.getStatus(), task.getStartDate(),
                    task.getDueDate(), null, task.getPrerequisites().stream()
                            .map(prerequisite -> prerequisite.getId().toString()).sorted().toList());
        }
        Milestone milestone = (Milestone) element;
        return new AiImprovementPlanContext.Element(AiImprovementElementType.MILESTONE,
                milestone.getId().toString(), milestone.getTitle(), milestone.getDescription(), position,
                null, null, null, null, milestone.getDueDate(), milestone.isCompleted(), List.of());
    }

    private AiImprovementContent loadContent(
            UUID projectId, AiImprovementElementType type, UUID elementId) {
        return switch (type) {
            case SECTION -> {
                PlanSection section = sectionRepository.findByIdAndPlanContainerId(elementId, projectId)
                        .orElseThrow(this::notFound);
                yield new AiImprovementContent(type, section.getTitle(), section.getDescription(),
                        null, null, null, null);
            }
            case TASK -> {
                Task task = taskRepository.findByIdAndPlanContainerId(elementId, projectId)
                        .orElseThrow(this::notFound);
                yield new AiImprovementContent(type, task.getTitle(), task.getDescription(), task.getPriority(),
                        task.getEstimatedHours(), task.getStartDate(), task.getDueDate());
            }
            case MILESTONE -> {
                Milestone milestone = milestoneRepository.findByIdAndPlanContainerId(elementId, projectId)
                        .orElseThrow(this::notFound);
                yield new AiImprovementContent(type, milestone.getTitle(), milestone.getDescription(),
                        null, null, null, milestone.getDueDate());
            }
        };
    }

    private long loadVersion(UUID projectId, AiImprovementElementType type, UUID elementId) {
        return switch (type) {
            case SECTION -> sectionRepository.findByIdAndPlanContainerId(elementId, projectId)
                    .orElseThrow(this::notFound).getLockVersion();
            case TASK -> taskRepository.findByIdAndPlanContainerId(elementId, projectId)
                    .orElseThrow(this::notFound).getLockVersion();
            case MILESTONE -> milestoneRepository.findByIdAndPlanContainerId(elementId, projectId)
                    .orElseThrow(this::notFound).getLockVersion();
        };
    }

    private String normalizeComment(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.trim();
        if (normalized.length() > AiImprovementForm.MAX_COMMENT_LENGTH) {
            throw new DomainValidationException("Der Kommentar darf höchstens 500 Zeichen lang sein.");
        }
        return normalized;
    }

    private void requireVersion(long actual, long proposed) {
        if (actual != proposed) {
            throw new ConflictException(
                    "Das Planelement wurde seit dem KI-Vorschlag geändert. Bitte erzeuge einen neuen Vorschlag.");
        }
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Das ausgewählte Planelement wurde nicht gefunden.");
    }
}
