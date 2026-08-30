package de.melinadanhier.projectflow.draft.dto;

import de.melinadanhier.projectflow.ai.model.generation.GeneratedElementOrigin;
import de.melinadanhier.projectflow.draft.model.DraftReviewStatus;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class DraftPlanElementDto {

    private UUID id;
    private UUID draftSectionId;
    private String title;
    private String description;
    private int sortOrder;
    private int manualPosition;
    private DraftReviewStatus reviewStatus;
    private ElementOrigin origin;
    private String type;
    private LocalDate startDate;
    private LocalDate dueDate;
    private Integer estimatedHours;
    private TaskPriority priority;

    public GeneratedElementOrigin getAiOrigin() {
        return origin == ElementOrigin.USER ? GeneratedElementOrigin.USER_INPUT : GeneratedElementOrigin.AI_INFERRED;
    }

    public boolean isUserModified() {
        return origin == ElementOrigin.AI_MODIFIED || origin == ElementOrigin.TEMPLATE_MODIFIED;
    }

    public String getOriginLabel() {
        return switch (origin) {
            case AI -> "KI-Vorschlag";
            case AI_MODIFIED -> "KI-Vorschlag, bearbeitet";
            case TEMPLATE -> "Vorlage";
            case TEMPLATE_MODIFIED -> "Vorlage, bearbeitet";
            case USER -> "Nutzereingabe";
        };
    }
}
