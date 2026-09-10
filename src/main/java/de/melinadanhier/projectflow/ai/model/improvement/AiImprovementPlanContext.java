package de.melinadanhier.projectflow.ai.model.improvement;

import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import java.time.LocalDate;
import java.util.List;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import de.melinadanhier.projectflow.planelement.model.TaskStatus;

/** Vollständiger, nur lesender Plankontext für REPLAN. */
public record AiImprovementPlanContext(
        SortMode sortMode,
        String selectedElementReference,
        List<Section> sections
) {
    public AiImprovementPlanContext(List<Section> sections) {
        this(null, null, sections);
    }

    public AiImprovementPlanContext(SortMode sortMode, List<Section> sections) {
        this(sortMode, null, sections);
    }
    public record Section(
            String reference,
            String title,
            String description,
            int position,
            List<Element> elements
    ) { }

    /** Gemeinsame, sichtbare Reihenfolge von Tasks und Milestones innerhalb einer Section. */
    public record Element(
            AiImprovementElementType elementType,
            String reference,
            String title,
            String description,
            int position,
            TaskPriority priority,
            Integer estimatedHours,
            TaskStatus status,
            LocalDate startDate,
            LocalDate dueDate,
            Boolean completed,
            List<String> prerequisiteReferences
    ) { }
}
