package de.melinadanhier.projectflow.planelement.dto;

import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import de.melinadanhier.projectflow.planelement.model.PlanReviewStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class SectionDto {

    private UUID id;
    private UUID planContainerId;
    private String title;
    private String description;
    private int sortOrder;
    private ElementOrigin origin;
    private PlanReviewStatus reviewStatus;
    private List<PlanElementViewDto> elements = new ArrayList<>();
    private int taskCount;
    private int milestoneCount;
    private long lockVersion;

    public String getOriginLabel() {
        if (origin == null) return null;
        return switch (origin) {
            case USER -> "Nutzereingabe";
            case TEMPLATE -> "Vorlage";
            case TEMPLATE_MODIFIED -> "Vorlage · bearbeitet";
            case AI -> "KI-Vorschlag";
            case AI_MODIFIED -> "KI-Vorschlag · bearbeitet";
        };
    }

    public String getReviewStatusLabel() {
        if (reviewStatus == null) return null;
        return reviewStatus == PlanReviewStatus.CONFIRMED ? "Geprüft" : "Prüfung offen";
    }

    public boolean isReviewWarning() {
        return reviewStatus == PlanReviewStatus.UNREVIEWED;
    }
}
