package com.melina.projectflow.plancontainer.project.dto;

import com.melina.projectflow.plancontainer.model.SortMode;
import com.melina.projectflow.plancontainer.model.StructureMode;
import com.melina.projectflow.plancontainer.project.model.CreationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class ProjectCreateForm {

    @NotBlank
    @Size(max = 100)
    private String title;

    @Size(max = 2000)
    private String description;

    private LocalDate startDate;
    private LocalDate endDate;

    @NotNull
    private CreationType creationType;

    private StructureMode structureMode;
    private SortMode sortMode;
}
