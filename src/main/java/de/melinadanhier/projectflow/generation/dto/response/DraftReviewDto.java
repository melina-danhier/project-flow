package de.melinadanhier.projectflow.generation.dto.response;

import de.melinadanhier.projectflow.generation.model.PlanDraftStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class DraftReviewDto {

    private UUID id;
    private UUID projectId;
    private PlanDraftStatus status;
    private int attemptCount;
    private String modelName;
    private String promptVersion;
    private String schemaVersion;
    private Instant generatedAt;
    private String summary;
    private String assumptions;
    private List<DraftSectionDto> sections = new ArrayList<>();
    private List<DraftPlanElementDto> elements = new ArrayList<>();
}
