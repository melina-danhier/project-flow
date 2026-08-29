package de.melinadanhier.projectflow.draft.mapper;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedMilestone;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedSection;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedTask;
import de.melinadanhier.projectflow.draft.model.DraftMilestone;
import de.melinadanhier.projectflow.draft.model.DraftPlanElement;
import de.melinadanhier.projectflow.draft.model.DraftSection;
import de.melinadanhier.projectflow.draft.model.DraftTask;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Maps validated output to transient entities without accessing persistence. */
@Component
public class GeneratedPlanDraftMapper {

    public MappedDraft map(GeneratedPlanResponse response) {
        List<DraftSection> sections = new ArrayList<>();
        List<DraftPlanElement> elements = new ArrayList<>();
        Map<String, DraftSection> sectionsByKey = new HashMap<>();
        Map<String, DraftTask> tasksByKey = new HashMap<>();
        Map<String, DraftMilestone> milestonesByKey = new HashMap<>();

        response.sections().forEach(section ->
                createSection(sections, sectionsByKey, section)
        );

        for (int index = 0; index < response.sections().size(); index++) {
            GeneratedSection generatedSection = response.sections().get(index);
            DraftSection section = sections.get(index);

            for (var generated : generatedSection.tasks()) {
                createTask(generated, section, elements, tasksByKey);
            }

            for (var generated : generatedSection.milestones()) {
                createMilestone(generated, section, elements, milestonesByKey);
            }

            if (section.getElements().stream().map(DraftPlanElement::getSortOrder).distinct().count()
                    != section.getElements().size()) {
                List<DraftPlanElement> sharedOrder = new ArrayList<>(section.getElements());
                sharedOrder.sort(java.util.Comparator.comparingInt(DraftPlanElement::getSortOrder));
                section.getElements().clear();
                section.getElements().addAll(sharedOrder);
                for (int position = 0; position < sharedOrder.size(); position++) {
                    sharedOrder.get(position).setSortOrder(position + 1);
                }
            }

        }

        response.sections().stream()
                .flatMap(section -> section.tasks().stream())
                .forEach(generated -> createDependency(tasksByKey, generated));

        return new MappedDraft(sections, elements);
    }

    private static void createDependency(Map<String, DraftTask> tasksByKey, GeneratedTask generated) {
        DraftTask successor = tasksByKey.get(generated.tempId());
        for (String prerequisiteKey : generated.prerequisiteTaskTempIds()) {
            DraftTask prerequisite = tasksByKey.get(prerequisiteKey);
            if (prerequisite == null) {
                throw new AiOutputValidationException("Eine Aufgabenreferenz kann nicht aufgelöst werden.");
            }
            successor.addPrerequisite(prerequisite);
        }
    }

    private void createSection(
            List<DraftSection> sections,
            Map<String, DraftSection> sectionsByKey,
            GeneratedSection generatedSection
    ) {
        DraftSection section = new DraftSection();
        section.setTitle(generatedSection.title());
        section.setDescription(generatedSection.description());
        section.setSortOrder(generatedSection.order());
        section.setOrigin(ElementOrigin.AI);
        sections.add(section);
        if (generatedSection.tempId() != null && !generatedSection.tempId().isBlank()) {
            register(sectionsByKey, generatedSection.tempId(), section);
        }
    }

    private void createTask(GeneratedTask generated, DraftSection section, List<DraftPlanElement> elements, Map<String, DraftTask> tasksByKey) {
        DraftTask task = new DraftTask();
        task.setTitle(generated.title());
        task.setDescription(generated.description());
        task.setStartDate(generated.startDate());
        task.setDueDate(generated.dueDate());
        task.setEstimatedHours(generated.estimatedHours());
        task.setPriority(generated.priority() == null ? TaskPriority.MEDIUM : generated.priority());
        task.setSortOrder(generated.order());
        task.setCriticalAssumption(generated.criticalAssumption());
        task.setAiOrigin(generated.origin());
        section.addElement(task);
        elements.add(task);
        register(tasksByKey, generated.tempId(), task);
    }

    private void createMilestone(GeneratedMilestone generated, DraftSection section, List<DraftPlanElement> elements, Map<String, DraftMilestone> milestonesByKey) {
        DraftMilestone milestone = new DraftMilestone();
        milestone.setTitle(generated.title());
        milestone.setDueDate(generated.date());
        milestone.setSortOrder(generated.order());
        milestone.setOrigin(ElementOrigin.AI);
        section.addElement(milestone);
        elements.add(milestone);
        if (generated.tempId() != null && !generated.tempId().isBlank()) {
            register(milestonesByKey, generated.tempId(), milestone);
        }
    }

    private <T> void register(Map<String, T> entities, String key, T entity) {
        if (key == null || key.isBlank() || entities.putIfAbsent(key, entity) != null) {
            throw new AiOutputValidationException("Ein temporärer Schlüssel fehlt oder ist mehrdeutig.");
        }
    }

    public record MappedDraft(List<DraftSection> sections, List<DraftPlanElement> elements) {
        public MappedDraft {
            sections = List.copyOf(sections);
            elements = List.copyOf(elements);
        }
    }
}
