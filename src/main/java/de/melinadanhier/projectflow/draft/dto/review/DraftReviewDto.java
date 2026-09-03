package de.melinadanhier.projectflow.draft.dto.review;

import de.melinadanhier.projectflow.draft.model.DraftPlanStatus;
import de.melinadanhier.projectflow.draft.model.DraftReviewStatus;
import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class DraftReviewDto {

    private UUID id;
    private UUID projectId;
    private String projectTitle;
    private String categoryLabel;
    private LocalDate projectStartDate;
    private LocalDate projectEndDate;
    private long lockVersion;
    private DraftPlanStatus status;
    private Instant generatedAt;
    private SortMode sortMode;
    private DraftReviewStatus activeReviewStatus;
    private int reviewedElementCount;
    private int totalElementCount;
    private List<DraftSectionDto> sections = new ArrayList<>();
    private List<DraftPlanElementDto> elements = new ArrayList<>();
    private List<DraftPlanElementDto> unsectionedElements = new ArrayList<>();

    public int getPendingElementCount() {
        return totalElementCount - reviewedElementCount;
    }

}
