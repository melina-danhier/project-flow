package com.melina.projectflow.generation.dto.response;

import com.melina.projectflow.generation.model.PlanDraftStatus;
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
    private PlanDraftStatus status;
    private int attemptCount;
    private Instant generatedAt;
}
