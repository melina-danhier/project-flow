package com.melina.projectflow.generation.dto.response;

import com.melina.projectflow.generation.model.ReviewStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class DraftPlanElementDto {

    private UUID id;
    private UUID draftSectionId;
    private String title;
    private String description;
    private int sortOrder;
    private ReviewStatus reviewStatus;
    private boolean userModified;
    private boolean hasCriticalAssumption;
}
