package de.melinadanhier.projectflow.draft.dto.editing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class DraftMilestoneForm {
    @NotNull @PositiveOrZero private Long lockVersion;
    @NotBlank @Size(max = 100) private String title;
    @Size(max = 2000) private String description;
    private UUID draftSectionId;
    private boolean sectionSelectionPresent;
    private LocalDate dueDate;
}
