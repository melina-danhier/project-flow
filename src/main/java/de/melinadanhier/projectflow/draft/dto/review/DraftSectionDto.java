package de.melinadanhier.projectflow.draft.dto.review;

import de.melinadanhier.projectflow.draft.model.DraftReviewStatus;
import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class DraftSectionDto {

    private UUID id;
    private String title;
    private String description;
    private int sortOrder;
    private DraftReviewStatus reviewStatus;
    private ElementOrigin origin;
    private List<DraftPlanElementDto> elements = new ArrayList<>();

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
