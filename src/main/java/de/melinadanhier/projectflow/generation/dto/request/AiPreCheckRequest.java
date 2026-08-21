package de.melinadanhier.projectflow.generation.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.Objects;

public record AiPreCheckRequest(@NotNull AiWizardSnapshot confirmedWizardData) {
    public AiPreCheckRequest {
        Objects.requireNonNull(confirmedWizardData, "confirmedWizardData darf nicht null sein");
    }
}
