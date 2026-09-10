package de.melinadanhier.projectflow.planelement.service;

import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import de.melinadanhier.projectflow.planelement.model.Milestone;
import de.melinadanhier.projectflow.planelement.model.PlanElement;
import de.melinadanhier.projectflow.planelement.model.Task;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical server-side view of all task and milestone elements in one plan.
 * Type-specific lists are projections of this collection, never independent plan orders.
 */
public final class PlanElementCollection {

    private final List<PlanElement> elements;

    private PlanElementCollection(Collection<? extends PlanElement> elements) {
        this.elements = List.copyOf(elements);
    }

    public static PlanElementCollection copyOf(Collection<? extends PlanElement> elements) {
        return new PlanElementCollection(Objects.requireNonNull(elements));
    }

    public List<PlanElement> all() {
        return elements;
    }

    public List<Task> tasks() {
        return inPlanOrder().stream()
                .filter(Task.class::isInstance)
                .map(Task.class::cast)
                .toList();
    }

    public List<Milestone> milestones() {
        return inPlanOrder().stream()
                .filter(Milestone.class::isInstance)
                .map(Milestone.class::cast)
                .toList();
    }

    public List<PlanElement> displayInSection(UUID sectionId, SortMode sortMode) {
        List<PlanElement> manualOrder = elements.stream()
                .filter(element -> Objects.equals(sectionId, sectionId(element)))
                .sorted(PlanOrdering.manual(PlanElement::getSortOrder, PlanElement::getId))
                .toList();
        return PlanOrdering.display(manualOrder, sortMode, PlanElementCollection::relevantDate);
    }

    public int taskCount(UUID sectionId) {
        return count(sectionId, Task.class);
    }

    public int milestoneCount(UUID sectionId) {
        return count(sectionId, Milestone.class);
    }

    public static LocalDate relevantDate(PlanElement element) {
        if (element instanceof Task task) {
            return task.getDueDate();
        }
        if (element instanceof Milestone milestone) {
            return milestone.getDueDate();
        }
        return null;
    }

    private List<PlanElement> inPlanOrder() {
        Comparator<PlanElement> order = Comparator
                .comparingInt((PlanElement element) -> element.getPlanSection() == null
                        ? Integer.MAX_VALUE : element.getPlanSection().getSortOrder())
                .thenComparing(PlanOrdering.manual(PlanElement::getSortOrder, PlanElement::getId));
        return elements.stream().sorted(order).toList();
    }

    private int count(UUID sectionId, Class<? extends PlanElement> type) {
        return Math.toIntExact(elements.stream()
                .filter(type::isInstance)
                .filter(element -> Objects.equals(sectionId, sectionId(element)))
                .count());
    }

    private static UUID sectionId(PlanElement element) {
        return element.getPlanSection() == null ? null : element.getPlanSection().getId();
    }
}
