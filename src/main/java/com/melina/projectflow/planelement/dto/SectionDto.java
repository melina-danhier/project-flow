package com.melina.projectflow.planelement.dto;

import com.melina.projectflow.planelement.model.ElementOrigin;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
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
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer relativeStartDay;
    private Integer relativeEndDay;
    private int sortOrder;
    private ElementOrigin origin;
    private boolean hasCriticalAssumption;
    private List<PlanElementDto> elements = new ArrayList<>();
}
