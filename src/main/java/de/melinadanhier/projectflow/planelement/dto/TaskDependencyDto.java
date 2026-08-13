package de.melinadanhier.projectflow.planelement.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class TaskDependencyDto {

    private UUID prerequisiteTaskId;
    private String prerequisiteTitle;
    private UUID successorTaskId;
    private String successorTitle;
}
