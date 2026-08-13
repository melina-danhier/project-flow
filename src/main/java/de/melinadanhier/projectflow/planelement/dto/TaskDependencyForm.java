package de.melinadanhier.projectflow.planelement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class TaskDependencyForm {

    @NotNull
    private UUID prerequisiteTaskId;

    @NotNull
    private UUID successorTaskId;
}
