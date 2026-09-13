package de.melinadanhier.projectflow.planelement.dto;

import java.util.List;

public record MilestoneCommentSectionDto(
        List<MilestoneCommentDto> comments,
        boolean groupProject
) {
}
