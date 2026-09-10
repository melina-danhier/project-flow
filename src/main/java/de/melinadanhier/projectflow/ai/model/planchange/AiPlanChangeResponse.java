package de.melinadanhier.projectflow.ai.model.planchange;

import java.util.List;

public record AiPlanChangeResponse(
        AiPlanChangeApplicability applicability, String rejectionReason, String summary,
        List<AiSectionChange> sections, List<AiTaskChange> tasks, List<AiMilestoneChange> milestones) {

    public AiPlanChangeResponse(String summary, List<AiSectionChange> sections, List<AiTaskChange> tasks,
                                List<AiMilestoneChange> milestones) {
        this(AiPlanChangeApplicability.APPLICABLE, null, summary, sections, tasks, milestones);
    }
}
