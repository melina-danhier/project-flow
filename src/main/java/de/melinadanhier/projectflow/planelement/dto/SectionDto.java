package de.melinadanhier.projectflow.planelement.dto;

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
public class SectionDto {

    private UUID id;
    private UUID planContainerId;
    private String title;
    private String description;
    private int sortOrder;
    private ElementOrigin origin;
    private boolean hasCriticalAssumption;
    private List<PlanElementViewDto> elements = new ArrayList<>();
    private int taskCount;
    private int milestoneCount;
    private long lockVersion;
}
