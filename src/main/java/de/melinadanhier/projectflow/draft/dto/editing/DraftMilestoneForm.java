package de.melinadanhier.projectflow.draft.dto.editing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class DraftMilestoneForm {
    @NotNull private Long lockVersion;
    @NotBlank @Size(max = 100) private String title;
    private LocalDate dueDate;
}
