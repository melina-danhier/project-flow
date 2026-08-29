package de.melinadanhier.projectflow.planelement.dto;

import java.util.List;

public record TaskCommentSectionDto(
        List<TaskCommentDto> comments,
        boolean groupProject
) {
}
