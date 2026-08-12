package com.melina.projectflow.planelement.dto;

import com.melina.projectflow.planelement.model.ElementOrigin;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PlanElementDto {

    private UUID id;
    private UUID planContainerId;
    private UUID planSectionId;
    private String title;
    private String description;
    private int sortOrder;
    private ElementOrigin origin;
    private boolean hasCriticalAssumption;
}
