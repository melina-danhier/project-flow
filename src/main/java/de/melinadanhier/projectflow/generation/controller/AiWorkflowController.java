package de.melinadanhier.projectflow.generation.controller;

import de.melinadanhier.projectflow.generation.dto.response.AiWorkflowStatusDto;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.service.precheck.AiPreCheckReviewService;
import de.melinadanhier.projectflow.generation.service.workflow.AiWorkflowQueryService;
import de.melinadanhier.projectflow.generation.service.workflow.AiGenerationWorkflowService;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/projects/new/ai")
public class AiWorkflowController {

    private final AiWorkflowQueryService workflowQueryService;
    private final AiPreCheckReviewService reviewService;
    private final AiGenerationWorkflowService generationWorkflowService;

    @GetMapping("/status/{workflowId}")
    public String status(
            @PathVariable UUID workflowId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model
    ) {
        AiWorkflowStatusDto workflow = workflowQueryService.getOwnedStatus(
                workflowId, currentUser.userId()
        );
        if (workflow.status() == AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW) {
            return "redirect:/projects/new/ai/problems/" + workflowId;
        }
        if (workflow.status() == AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED) {
            return "redirect:/projects/" + workflow.projectId() + "/draft/review";
        }
        model.addAttribute("workflow", workflow);
        return "generation/ai-status";
    }

    @GetMapping("/problems/{workflowId}")
    public String problems(
            @PathVariable UUID workflowId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model
    ) {
        var review = reviewService.getReview(workflowId, currentUser.userId());
        model.addAttribute("review", review);
        return "generation/ai-problems";
    }

    @PostMapping("/problems/{workflowId}/warnings/{problemIndex}/acknowledge")
    public String acknowledgeWarning(
            @PathVariable UUID workflowId,
            @PathVariable int problemIndex,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        if (reviewService.acknowledgeWarning(workflowId, currentUser.userId(), problemIndex)) {
            return "redirect:/projects/new/ai/status/" + workflowId;
        }
        return "redirect:/projects/new/ai/problems/" + workflowId;
    }

    @PostMapping("/status/{workflowId}/retry")
    public String retryGeneration(
            @PathVariable UUID workflowId,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        workflowQueryService.getOwnedStatus(workflowId, currentUser.userId());
        if (!generationWorkflowService.retry(workflowId, currentUser.userId())) {
            throw new ConflictException("Die Generierung kann in diesem Zustand nicht erneut gestartet werden.");
        }
        return "redirect:/projects/new/ai/status/" + workflowId;
    }

}
