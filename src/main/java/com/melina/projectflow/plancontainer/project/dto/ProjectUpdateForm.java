package com.melina.projectflow.plancontainer.project.dto;

import com.melina.projectflow.plancontainer.model.SortMode;
import com.melina.projectflow.plancontainer.model.StructureMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class ProjectUpdateForm {

    @NotBlank
    @Size(max = 100)
    private String title;

    @Size(max = 2000)
    private String description;

    private LocalDate startDate;
    private LocalDate endDate;
    private StructureMode structureMode;
    private SortMode sortMode;
}
