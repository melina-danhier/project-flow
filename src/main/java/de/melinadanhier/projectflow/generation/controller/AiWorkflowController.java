package de.melinadanhier.projectflow.generation.controller;

import de.melinadanhier.projectflow.generation.dto.response.AiWorkflowStatusDto;
import de.melinadanhier.projectflow.generation.service.AiWorkflowQueryService;
import de.melinadanhier.projectflow.generation.service.AiGenerationPreparation;
import de.melinadanhier.projectflow.generation.service.AiPreCheckReviewService;
import de.melinadanhier.projectflow.generation.service.AiPreCheckReviewSession;
import de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class AiWorkflowController {

    private final AiWorkflowQueryService workflowQueryService;
    private final AiPreCheckReviewService reviewService;
    private final AiPreCheckReviewSession reviewSession;

    @GetMapping("/projects/new/ai/status/{workflowId}")
    public String status(
            @PathVariable UUID workflowId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model
    ) {
        AiWorkflowStatusDto workflow = workflowQueryService.getOwnedStatus(
                workflowId, currentUser.userId());
        if (workflow.status() == AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW) {
            return "redirect:/projects/new/ai/problems/" + workflowId;
        }
        model.addAttribute("workflow", workflow);
        return "generation/ai-status";
    }

    @GetMapping("/projects/new/ai/problems/{workflowId}")
    public String problems(
            @PathVariable UUID workflowId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model
    ) {
        model.addAttribute("review", reviewService.getReview(
                workflowId, currentUser.userId(), reviewSession.ignoredWarnings(workflowId, session)));
        return "generation/ai-problems";
    }

    @PostMapping("/projects/new/ai/problems/{workflowId}/warnings/{problemIndex}/ignore")
    public String ignoreWarning(
            @PathVariable UUID workflowId,
            @PathVariable int problemIndex,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session
    ) {
        reviewService.requireIgnorableWarning(workflowId, currentUser.userId(), problemIndex);
        var ignoredWarnings = reviewSession.ignore(workflowId, problemIndex, session);
        var review = reviewService.getReview(workflowId, currentUser.userId(), ignoredWarnings);
        if (!review.hasWarnings() && !review.hasErrors()) {
            AiGenerationPreparation preparation = reviewService.prepareGeneration(
                    workflowId, currentUser.userId(), ignoredWarnings);
            reviewSession.clear(workflowId, session);
            return "redirect:/projects/new/ai/status/" + workflowId;
        }
        return "redirect:/projects/new/ai/problems/" + workflowId;
    }

}
