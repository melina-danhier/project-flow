package de.melinadanhier.projectflow.draft.service;

import de.melinadanhier.projectflow.draft.model.DraftMilestone;
import de.melinadanhier.projectflow.draft.model.DraftPlan;
import de.melinadanhier.projectflow.draft.model.DraftPlanElement;
import de.melinadanhier.projectflow.draft.model.DraftReviewStatus;
import de.melinadanhier.projectflow.draft.model.DraftSection;
import de.melinadanhier.projectflow.draft.model.DraftTask;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.planelement.model.Milestone;
import de.melinadanhier.projectflow.planelement.model.PlanElement;
import de.melinadanhier.projectflow.planelement.model.PlanReviewStatus;
import de.melinadanhier.projectflow.planelement.model.PlanSection;
import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.planelement.service.PlanOrdering;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class DraftPlanAdoptionFactory {

    private static final Comparator<DraftSection> SECTION_ORDER = PlanOrdering
            .manual(DraftSection::getSortOrder, DraftSection::getId);
    private static final Comparator<DraftPlanElement> ELEMENT_ORDER = PlanOrdering
            .manual(DraftPlanElement::getSortOrder, DraftPlanElement::getId);

    public void adopt(DraftPlan draft, Project project) {
        Map<DraftSection, PlanSection> adoptedSections = new HashMap<>();
        List<DraftSection> includedSections = draft.getSections().stream()
                .filter(this::included)
                .sorted(SECTION_ORDER)
                .toList();
        for (int position = 0; position < includedSections.size(); position++) {
            DraftSection source = includedSections.get(position);
            PlanSection target = new PlanSection();
            target.setTitle(source.getTitle());
            target.setDescription(source.getDescription());
            target.setOrigin(source.getOrigin());
            target.setReviewStatus(reviewStatus(source.getReviewStatus()));
            target.setSortOrder(source.getSortOrder());
            project.addSection(target);
            adoptedSections.put(source, target);
        }

        Map<UUID, Task> adoptedTasks = new HashMap<>();
        for (DraftSection sourceSection : includedSections) {
            List<DraftPlanElement> children = sourceSection.getElements().stream()
                    .filter(this::included)
                    .sorted(ELEMENT_ORDER)
                    .toList();
            for (DraftPlanElement child : children) {
                adoptElement(child, project, adoptedSections.get(sourceSection),
                        child.getSortOrder(), adoptedTasks);
            }
        }

        List<DraftPlanElement> originallyUnsectioned = draft.getElements().stream()
                .filter(this::included)
                .filter(element -> element.getDraftSection() == null)
                .sorted(ELEMENT_ORDER)
                .toList();
        List<DraftPlanElement> fromRejectedSections = draft.getSections().stream()
                .filter(section -> !included(section))
                .sorted(SECTION_ORDER)
                .flatMap(section -> section.getElements().stream().filter(this::included).sorted(ELEMENT_ORDER))
                .toList();
        for (DraftPlanElement source : originallyUnsectioned) {
            adoptElement(source, project, null, source.getSortOrder(), adoptedTasks);
        }
        int nextUnsectionedOrder = originallyUnsectioned.stream()
                .mapToInt(DraftPlanElement::getSortOrder).max().orElse(0);
        for (DraftPlanElement source : fromRejectedSections) {
            nextUnsectionedOrder = Math.addExact(nextUnsectionedOrder, PlanOrdering.GAP);
            adoptElement(source, project, null, nextUnsectionedOrder, adoptedTasks);
        }

        draft.getElements().stream()
                .filter(DraftTask.class::isInstance)
                .map(DraftTask.class::cast)
                .filter(this::included)
                .forEach(successor -> {
                    Task adoptedSuccessor = adoptedTasks.get(successor.getId());
                    if (adoptedSuccessor == null) {
                        throw inconsistentDependency();
                    }
                    successor.getPrerequisites().stream()
                            .filter(this::included)
                            .map(prerequisite -> {
                                Task adoptedPrerequisite = adoptedTasks.get(prerequisite.getId());
                                if (adoptedPrerequisite == null) throw inconsistentDependency();
                                return adoptedPrerequisite;
                            })
                            .forEach(adoptedSuccessor::addPrerequisite);
                });
    }

    private void adoptElement(DraftPlanElement source, Project project, PlanSection section,
                              int position, Map<UUID, Task> adoptedTasks) {
        PlanElement target = copy(source);
        target.setSortOrder(position);
        project.addElement(target);
        if (section != null) {
            section.addElement(target);
        }
        if (source instanceof DraftTask && target instanceof Task task) {
            adoptedTasks.put(source.getId(), task);
        }
    }

    private PlanElement copy(DraftPlanElement source) {
        PlanElement target;
        if (source instanceof DraftTask draftTask) {
            Task task = new Task();
            task.setPriority(draftTask.getPriority());
            task.setStartDate(draftTask.getStartDate());
            task.setDueDate(draftTask.getDueDate());
            task.setEstimatedHours(draftTask.getEstimatedHours());
            target = task;
        } else if (source instanceof DraftMilestone draftMilestone) {
            Milestone milestone = new Milestone();
            milestone.setDueDate(draftMilestone.getDueDate());
            target = milestone;
        } else {
            throw new IllegalStateException("Nicht unterstütztes Entwurfselement.");
        }
        target.setTitle(source.getTitle());
        target.setDescription(source.getDescription());
        target.setOrigin(source.getOrigin());
        target.setReviewStatus(reviewStatus(source.getReviewStatus()));
        return target;
    }

    private PlanReviewStatus reviewStatus(DraftReviewStatus status) {
        return status == DraftReviewStatus.ACCEPTED
                ? PlanReviewStatus.CONFIRMED
                : PlanReviewStatus.UNREVIEWED;
    }

    private boolean included(DraftSection section) {
        return included(section.getReviewStatus());
    }

    private boolean included(DraftPlanElement element) {
        return included(element.getReviewStatus());
    }

    private boolean included(DraftReviewStatus status) {
        return status == DraftReviewStatus.ACCEPTED || status == DraftReviewStatus.PENDING;
    }

    private IllegalStateException inconsistentDependency() {
        return new IllegalStateException("Der Entwurf enthält eine unbekannte Aufgabenabhängigkeit.");
    }
}
