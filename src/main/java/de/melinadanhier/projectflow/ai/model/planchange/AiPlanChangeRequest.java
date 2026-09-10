package de.melinadanhier.projectflow.ai.model.planchange;

import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementPlanContext;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementProjectContext;

public record AiPlanChangeRequest(
        String changeRequest, AiImprovementProjectContext project,
        AiImprovementPlanContext currentPlan) { }
