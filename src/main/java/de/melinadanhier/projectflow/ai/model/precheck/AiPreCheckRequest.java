package de.melinadanhier.projectflow.ai.model.precheck;

import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

public record AiPreCheckRequest(@NotNull AiWizardSnapshot confirmedWizardData) {
    public AiPreCheckRequest {
        Objects.requireNonNull(confirmedWizardData, "confirmedWizardData darf nicht null sein");
    }
}
