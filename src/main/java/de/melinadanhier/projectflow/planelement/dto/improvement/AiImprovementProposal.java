package de.melinadanhier.projectflow.planelement.dto.improvement;

import de.melinadanhier.projectflow.ai.model.improvement.AiFeedbackType;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementContent;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementElementType;

import java.io.Serializable;
import java.util.UUID;

public record AiImprovementProposal(
        UUID proposalId,
        UUID projectId,
        UUID elementId,
        AiImprovementElementType elementType,
        long elementVersion,
        AiFeedbackType feedbackType,
        String comment,
        String explanation,
        AiReplanPlacementProposal placement,
        AiImprovementContent original,
        AiImprovementContent proposed
) implements Serializable {
    public AiImprovementProposal(
            UUID proposalId,
            UUID projectId,
            UUID elementId,
            AiImprovementElementType elementType,
            long elementVersion,
            AiFeedbackType feedbackType,
            String comment,
            AiImprovementContent original,
            AiImprovementContent proposed
    ) {
        this(proposalId, projectId, elementId, elementType, elementVersion, feedbackType,
                comment, null, null, original, proposed);
    }

    public AiImprovementProposal(
            UUID proposalId,
            UUID projectId,
            UUID elementId,
            AiImprovementElementType elementType,
            long elementVersion,
            AiFeedbackType feedbackType,
            String comment,
            String explanation,
            AiImprovementContent original,
            AiImprovementContent proposed
    ) {
        this(proposalId, projectId, elementId, elementType, elementVersion, feedbackType,
                comment, explanation, null, original, proposed);
    }
}
