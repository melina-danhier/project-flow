package de.melinadanhier.projectflow.generation.dto;

import de.melinadanhier.projectflow.draft.model.DraftPlanStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class GenerationStatusDto {

    private UUID draftId;
    private UUID projectId;
    private DraftPlanStatus status;
    private int attemptCount;
    private Instant generatedAt;
}
