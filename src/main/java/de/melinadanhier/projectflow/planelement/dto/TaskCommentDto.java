package de.melinadanhier.projectflow.planelement.dto;

import java.time.Instant;
import java.util.UUID;

public record TaskCommentDto(
        UUID id,
        String content,
        String authorDisplayName,
        Instant createdAt,
        boolean deletable
) {
}
