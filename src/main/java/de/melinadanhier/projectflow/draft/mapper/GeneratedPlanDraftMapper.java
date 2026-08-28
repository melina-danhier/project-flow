package de.melinadanhier.projectflow.draft.mapper;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.draft.model.DraftMilestone;
import de.melinadanhier.projectflow.draft.model.DraftPlanElement;
import de.melinadanhier.projectflow.draft.model.DraftSection;
import de.melinadanhier.projectflow.draft.model.DraftTask;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
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

        for (var phase : response.phases()) {
            DraftSection section = new DraftSection();
            section.setTitle(phase.title());
            section.setDescription(phase.description());
            section.setStartDate(phase.startDate());
            section.setEndDate(phase.endDate());
            section.setSortOrder(phase.order());
            sections.add(section);
            // Phase/milestone keys are optional in the existing response schema.
            if (phase.tempId() != null && !phase.tempId().isBlank()) {
                register(sectionsByKey, phase.tempId(), section);
            }
        }

        for (int index = 0; index < response.phases().size(); index++) {
            var phase = response.phases().get(index);
            DraftSection section = sections.get(index);
            for (var generated : phase.tasks()) {
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
            for (var generated : phase.milestones()) {
                DraftMilestone milestone = new DraftMilestone();
                milestone.setTitle(generated.title());
                milestone.setDueDate(generated.date());
                milestone.setSortOrder(generated.order());
                section.addElement(milestone);
                elements.add(milestone);
                if (generated.tempId() != null && !generated.tempId().isBlank()) {
                    register(milestonesByKey, generated.tempId(), milestone);
                }
            }
        }

        response.phases().stream().flatMap(phase -> phase.tasks().stream()).forEach(generated -> {
            DraftTask successor = tasksByKey.get(generated.tempId());
            for (String prerequisiteKey : generated.prerequisiteTaskTempIds()) {
                DraftTask prerequisite = tasksByKey.get(prerequisiteKey);
                if (prerequisite == null) {
                    throw new AiOutputValidationException("Eine Aufgabenreferenz kann nicht aufgelöst werden.");
                }
                successor.addPrerequisite(prerequisite);
            }
        });
        return new MappedDraft(sections, elements);
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
