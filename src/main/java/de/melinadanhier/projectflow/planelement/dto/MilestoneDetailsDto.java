package de.melinadanhier.projectflow.planelement.dto;

import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class MilestoneDetailsDto {

    private UUID id;
    private UUID planContainerId;
    private UUID planSectionId;
    private String title;
    private String description;
    private int sortOrder;
    private ElementOrigin origin;
    private LocalDate dueDate;
    private Integer relativeDueDay;
    private boolean completed;
    private boolean editable;
    private long lockVersion;
    private List<SectionDto> availableSections = new ArrayList<>();

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
}
