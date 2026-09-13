package de.melinadanhier.projectflow.planelement.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TaskReferenceDto {

    private UUID id;
    private String title;
    private UUID planSectionId;
    private String planSectionTitle;

    public TaskReferenceDto(UUID id, String title) {
        this.id = id;
        this.title = title;
        this.planSectionId = null;
        this.planSectionTitle = null;
    }
}
