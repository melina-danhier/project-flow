package de.melinadanhier.projectflow.planelement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class DeleteSectionForm {

    @NotNull
    private SectionDeletionMode mode;

    private UUID targetSectionId;
}
