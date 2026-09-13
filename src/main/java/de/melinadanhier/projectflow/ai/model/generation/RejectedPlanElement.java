package de.melinadanhier.projectflow.ai.model.generation;

import java.util.Objects;

public record RejectedPlanElement(
        String type,
        String title,
        String description
) {
    public RejectedPlanElement {
        Objects.requireNonNull(type, "type darf nicht null sein");
        Objects.requireNonNull(title, "title darf nicht null sein");
    }
}
