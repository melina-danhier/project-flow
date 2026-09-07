package de.melinadanhier.projectflow.planelement.dto;

import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import de.melinadanhier.projectflow.planelement.model.PlanReviewStatus;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import de.melinadanhier.projectflow.planelement.model.TaskStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PlanElementViewDto {

    private UUID id;
    private PlanElementType type;
    private String title;
    private String description;
    private UUID planSectionId;
    private int sortOrder;
    private LocalDate relevantDate;
    private TaskStatus taskStatus;
    private TaskPriority taskPriority;
    private boolean milestoneCompleted;
    private ElementOrigin origin;
    private PlanReviewStatus reviewStatus;

    public String getTypeLabel() {
        return type == PlanElementType.MILESTONE ? "Meilenstein" : "Aufgabe";
    }

    public String getDateLabel() {
        if (relevantDate == null) return null;
        String prefix = type == PlanElementType.MILESTONE ? "Termin: " : "Fällig: ";
        return prefix + relevantDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    public String getStateLabel() {
        if (type == PlanElementType.MILESTONE) {
            return milestoneCompleted ? "Erreicht" : "Offen";
        }
        if (taskStatus == null) return null;
        return switch (taskStatus) {
            case OPEN -> "Offen";
            case IN_PROGRESS -> "In Bearbeitung";
            case COMPLETED -> "Erledigt";
        };
    }

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
