package de.melinadanhier.projectflow.planelement.dto.planchange;

import de.melinadanhier.projectflow.ai.model.planchange.AiPlanChangeOperation;
import java.util.List;

public record PlanChangeReview(String summary, List<Section> sections) {
    public record Section(String title, AiPlanChangeOperation operation, boolean sectionChanged,
                          List<FieldChange> fields, List<Element> elements) { }
    public record Element(String typeLabel, String title, AiPlanChangeOperation operation,
                          List<FieldChange> fields, String explanation) { }
    public record FieldChange(String label, String before, String after) { }
}
