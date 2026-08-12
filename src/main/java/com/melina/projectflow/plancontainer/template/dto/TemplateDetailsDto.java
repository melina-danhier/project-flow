package com.melina.projectflow.plancontainer.template.dto;

import com.melina.projectflow.plancontainer.model.SortMode;
import com.melina.projectflow.plancontainer.model.StructureMode;
import com.melina.projectflow.plancontainer.template.model.CollaborationMode;
import com.melina.projectflow.plancontainer.template.model.TemplateCategory;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class TemplateDetailsDto {

    private UUID id;
    private String title;
    private String description;
    private StructureMode structureMode;
    private SortMode sortMode;
    private TemplateCategory category;
    private String projectType;
    private Integer recommendedDurationDays;
    private CollaborationMode collaborationMode;
    private boolean active;
    private int version;
}
