package de.melinadanhier.projectflow.planelement.dto;

import java.time.Instant;
import java.util.UUID;

public record MilestoneCommentDto(
        UUID id,
        String content,
        String authorDisplayName,
        Instant createdAt,
        long lockVersion,
        boolean deletable
) {
}
