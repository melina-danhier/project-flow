package de.melinadanhier.projectflow.generation.validation;

import de.melinadanhier.projectflow.generation.client.AiOutputValidationException;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedMilestone;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPhase;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPlanResponse;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedTask;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class GeneratedPlanValidator {

    public void validate(GeneratedPlanResponse response) {
        Set<String> tempIds = new HashSet<>();
        for (GeneratedPhase phase : response.phases()) {
            requireUnique(tempIds, phase.tempId());
            for (GeneratedTask task : phase.tasks()) {
                requireUnique(tempIds, task.tempId());
                if (task.startDate() != null && task.dueDate() != null
                        && task.startDate().isAfter(task.dueDate())) {
                    throw new AiOutputValidationException(
                            "Der generierte Plan enthält einen Aufgabenbeginn nach dem Fälligkeitsdatum.");
                }
            }
            for (GeneratedMilestone milestone : phase.milestones()) {
                requireUnique(tempIds, milestone.tempId());
            }
        }
    }

    private void requireUnique(Set<String> tempIds, String tempId) {
        if (!tempIds.add(tempId)) {
            throw new AiOutputValidationException(
                    "Der generierte Plan enthält die temporäre ID mehrfach: " + tempId);
        }
    }
}
