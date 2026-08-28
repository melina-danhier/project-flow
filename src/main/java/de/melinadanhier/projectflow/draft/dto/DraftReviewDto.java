package de.melinadanhier.projectflow.draft.dto;

import de.melinadanhier.projectflow.draft.model.DraftPlanStatus;
import de.melinadanhier.projectflow.draft.model.DraftReviewStatus;
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
    private long lockVersion;
    private DraftPlanStatus status;
    private Instant generatedAt;
    private List<DraftSectionDto> sections = new ArrayList<>();
    private List<DraftPlanElementDto> elements = new ArrayList<>();

    public List<DraftPlanElementDto> getUncheckedCriticalTasks() {
        return elements.stream()
                .filter(element -> "TASK".equals(element.getType()))
                .filter(element -> element.getReviewStatus() != DraftReviewStatus.ACCEPTED)
                .filter(element -> element.getCriticalAssumption() != null
                        && !element.getCriticalAssumption().isBlank())
                .toList();
    }
}
