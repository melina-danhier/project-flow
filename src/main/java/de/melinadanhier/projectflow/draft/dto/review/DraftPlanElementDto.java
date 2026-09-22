package de.melinadanhier.projectflow.draft.dto.review;

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
    private Integer estimatedMinutes;

    public String getFormattedEffort() {
        return de.melinadanhier.projectflow.common.util.EffortFormatter.formatMinutes(estimatedMinutes);
    }
    private TaskPriority priority;

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

    public String getVisibleOriginLabel() {
        if (origin == ElementOrigin.AI_MODIFIED || origin == ElementOrigin.TEMPLATE_MODIFIED) {
            return "Bearbeitet";
        }
        if (origin == ElementOrigin.TEMPLATE) {
            return "Vorlage";
        }
        if (origin == ElementOrigin.USER) {
            return "Nutzereingabe";
        }
        return null;
    }
}
