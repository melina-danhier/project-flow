package de.melinadanhier.projectflow.planelement.dto.planchange;

import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementPlanContext;
import de.melinadanhier.projectflow.ai.model.planchange.AiPlanChangeResponse;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PlanChangeProposal(UUID proposalId, UUID projectId, String projectTitle, String changeRequest,
                                 Instant createdAt, AiImprovementPlanContext originalPlan,
                                 AiPlanChangeResponse changes, long projectVersion,
                                 Map<UUID, Long> sectionVersions,
                                 Map<UUID, Long> elementVersions) implements Serializable {
    public PlanChangeProposal {
        sectionVersions = Map.copyOf(sectionVersions);
        elementVersions = Map.copyOf(elementVersions);
    }
}
