package de.melinadanhier.projectflow.planelement.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class TaskReferenceDto {

    private UUID id;
    private String title;
}
