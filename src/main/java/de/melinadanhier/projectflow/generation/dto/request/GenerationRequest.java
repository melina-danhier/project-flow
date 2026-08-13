package de.melinadanhier.projectflow.generation.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class GenerationRequest {

    @NotNull
    private UUID projectId;
}
