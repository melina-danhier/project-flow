package de.melinadanhier.projectflow.ai.model.improvement;

import java.util.Objects;

public record AiImprovementRequest(
        AiFeedbackType feedbackType,
        String comment,
        AiImprovementProjectContext project,
        AiImprovementSectionContext section,
        AiImprovementPlanContext plan,
        AiImprovementContent element
) {
    public AiImprovementRequest {
        Objects.requireNonNull(feedbackType, "feedbackType darf nicht null sein");
        Objects.requireNonNull(element, "element darf nicht null sein");
    }

    public AiImprovementRequest(
            AiFeedbackType feedbackType,
            String comment,
            AiImprovementProjectContext project,
            AiImprovementContent element
    ) {
        this(feedbackType, comment, project, null, null, element);
    }
}
