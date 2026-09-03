package de.melinadanhier.projectflow.draft.mapper;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedMilestone;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedSection;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedTask;
import de.melinadanhier.projectflow.draft.model.DraftMilestone;
import de.melinadanhier.projectflow.draft.model.DraftPlanElement;
import de.melinadanhier.projectflow.draft.model.DraftSection;
import de.melinadanhier.projectflow.draft.model.DraftTask;
import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Maps validated output to transient entities without accessing persistence. */
@Component
public class GeneratedPlanDraftMapper {

    public MappedDraft map(GeneratedPlanResponse response) {
        List<DraftSection> sections = new ArrayList<>();
        List<DraftPlanElement> elements = new ArrayList<>();
        Map<String, DraftTask> tasksByKey = new HashMap<>();

        List<GeneratedSection> orderedSections = new ArrayList<>(response.sections());
        orderedSections.sort(Comparator.comparingInt(GeneratedSection::order));

        for (int sectionPosition = 0; sectionPosition < orderedSections.size(); sectionPosition++) {
            GeneratedSection generatedSection = orderedSections.get(sectionPosition);
            DraftSection section = createSection(generatedSection);
            section.setSortOrder(sectionPosition);
            sections.add(section);

            for (GeneratedTask generatedTask : generatedSection.tasks()) {
                DraftTask task = createTask(generatedTask);
                attach(task, section, elements);
                registerTask(tasksByKey, generatedTask.tempId(), task);
            }

            for (GeneratedMilestone generatedMilestone : generatedSection.milestones()) {
                attach(createMilestone(generatedMilestone), section, elements);
            }

            normalizeElementOrder(section);
        }

        orderedSections.stream()
                .flatMap(section -> section.tasks().stream())
                .forEach(generated -> addDependencies(tasksByKey, generated));

        return new MappedDraft(sections, elements);
    }

    private static DraftSection createSection(GeneratedSection generated) {
        DraftSection section = new DraftSection();
        section.setTitle(generated.title());
        section.setDescription(generated.description());
        section.setOrigin(ElementOrigin.AI);
        return section;
    }

    private static DraftTask createTask(GeneratedTask generated) {
        DraftTask task = new DraftTask();
        task.setTitle(generated.title());
        task.setDescription(generated.description());
        task.setStartDate(generated.startDate());
        task.setDueDate(generated.dueDate());
        task.setEstimatedHours(generated.estimatedHours());
        task.setPriority(generated.priority() == null ? TaskPriority.MEDIUM : generated.priority());
        task.setSortOrder(generated.order());
        task.setAiOrigin(generated.origin());
        return task;
    }

    private static DraftMilestone createMilestone(GeneratedMilestone generated) {
        DraftMilestone milestone = new DraftMilestone();
        milestone.setTitle(generated.title());
        milestone.setDueDate(generated.date());
        milestone.setSortOrder(generated.order());
        milestone.setOrigin(ElementOrigin.AI);
        return milestone;
    }

    private static void attach(
            DraftPlanElement element,
            DraftSection section,
            List<DraftPlanElement> elements
    ) {
        section.addElement(element);
        elements.add(element);
    }

    private static void normalizeElementOrder(DraftSection section) {
        List<DraftPlanElement> orderedElements = new ArrayList<>(section.getElements());
        orderedElements.sort(Comparator.comparingInt(DraftPlanElement::getSortOrder));
        section.getElements().clear();
        section.getElements().addAll(orderedElements);
        for (int position = 0; position < orderedElements.size(); position++) {
            orderedElements.get(position).setSortOrder(position);
        }
    }

    private static void registerTask(Map<String, DraftTask> tasksByKey, String key, DraftTask task) {
        if (key == null || key.isBlank() || tasksByKey.putIfAbsent(key, task) != null) {
            throw new AiOutputValidationException("Ein temporärer Aufgabenschlüssel fehlt oder ist mehrdeutig.");
        }
    }

    private static void addDependencies(Map<String, DraftTask> tasksByKey, GeneratedTask generated) {
        DraftTask successor = tasksByKey.get(generated.tempId());
        for (String prerequisiteKey : generated.prerequisiteTaskTempIds()) {
            DraftTask prerequisite = tasksByKey.get(prerequisiteKey);
            if (prerequisite == null) {
                throw new AiOutputValidationException("Eine Aufgabenreferenz kann nicht aufgelöst werden.");
            }
            successor.addPrerequisite(prerequisite);
        }
    }

    public record MappedDraft(List<DraftSection> sections, List<DraftPlanElement> elements) {
        public MappedDraft {
            sections = List.copyOf(sections);
            elements = List.copyOf(elements);
        }
    }
}
