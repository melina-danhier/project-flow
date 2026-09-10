package de.melinadanhier.projectflow.planelement.dto.planchange;

import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementPlanContext;
import de.melinadanhier.projectflow.ai.model.planchange.AiPlanChangeResponse;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record PlanChangeProposal(UUID proposalId, UUID projectId, String projectTitle, String changeRequest,
                                 Instant createdAt, AiImprovementPlanContext originalPlan,
                                 AiPlanChangeResponse changes) implements Serializable { }
