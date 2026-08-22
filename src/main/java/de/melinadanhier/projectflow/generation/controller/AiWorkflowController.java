package de.melinadanhier.projectflow.generation.controller;

import de.melinadanhier.projectflow.generation.dto.response.AiWorkflowStatusDto;
import de.melinadanhier.projectflow.generation.model.AiGenerationPreparation;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.model.AiPreCheckReviewSession;
import de.melinadanhier.projectflow.generation.service.precheck.AiPreCheckReviewService;
import de.melinadanhier.projectflow.generation.service.workflow.AiWorkflowQueryService;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import jakarta.servlet.http.HttpSession;
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
    private final AiPreCheckReviewSession reviewSession;

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
        model.addAttribute("workflow", workflow);
        return "generation/ai-status";
    }

    @GetMapping("/problems/{workflowId}")
    public String problems(
            @PathVariable UUID workflowId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model
    ) {
        var ignoredWarnings = reviewSession.ignoredWarnings(workflowId, session);
        var review = reviewService.getReview(workflowId, currentUser.userId(), ignoredWarnings);
        model.addAttribute("review", review);
        return "generation/ai-problems";
    }

    @PostMapping("/problems/{workflowId}/warnings/{problemIndex}/ignore")
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
                    workflowId, currentUser.userId(), ignoredWarnings
            );
            reviewSession.clear(workflowId, session);
            return "redirect:/projects/new/ai/status/" + workflowId;
        }
        return "redirect:/projects/new/ai/problems/" + workflowId;
    }

}
