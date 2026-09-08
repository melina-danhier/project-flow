package de.melinadanhier.projectflow.generation.controller;

import de.melinadanhier.projectflow.generation.dto.workflow.AiWorkflowStatusDto;
import de.melinadanhier.projectflow.generation.dto.precheck.OpenPointConfirmationForm;
import de.melinadanhier.projectflow.generation.service.precheck.AiPreCheckReviewService;
import de.melinadanhier.projectflow.generation.service.workflow.AiGenerationWorkflowService;
import de.melinadanhier.projectflow.generation.service.workflow.AiWorkflowControlService;
import de.melinadanhier.projectflow.generation.service.workflow.AiWorkflowQueryService;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.validation.BindingResult;
import jakarta.validation.Valid;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/projects/new/ai")
public class AiWorkflowController {

    private final AiWorkflowQueryService workflowQueryService;
    private final AiPreCheckReviewService preCheckReviewService;
    private final AiGenerationWorkflowService generationWorkflowService;
    private final AiWorkflowControlService workflowControlService;

    @GetMapping("/status/{workflowId}")
    public String status(
            @PathVariable UUID workflowId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model
    ) {
        AiWorkflowStatusDto workflow = workflowQueryService.getStatus(
                workflowId, currentUser.userId()
        );
        switch (workflow.status()) {
            case PRE_CHECK_NEEDS_REVIEW, PRE_CHECK_COMPLETED, GENERATION_CANCELLED -> {
                return preCheckReviewRedirect(workflowId);
            }
            case GENERATION_COMPLETED -> {
                return "redirect:/projects/" + workflow.projectId() + "/draft/review";
            }
            default -> { }
        }
        model.addAttribute("workflow", workflow);
        return "generation/ai-status";
    }

    @PostMapping("/status/{workflowId}/generate")
    public String startGeneration(@PathVariable UUID workflowId,
                                  @AuthenticationPrincipal AuthenticatedUser currentUser) {
        workflowControlService.startGeneration(workflowId, currentUser.userId());
        return "redirect:/projects/new/ai/status/" + workflowId;
    }

    @GetMapping("/problems/{workflowId}")
    public String preCheckReview(
            @PathVariable UUID workflowId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model
    ) {
        var review = preCheckReviewService.getReview(workflowId, currentUser.userId());
        model.addAttribute("review", review);
        return "generation/ai-problems";
    }

    @PostMapping("/problems/{workflowId}/open-points/{problemIndex}/accept")
    public String acceptOpenPoint(
            @PathVariable UUID workflowId,
            @PathVariable int problemIndex,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        if (preCheckReviewService.acceptOpenPoint(workflowId, currentUser.userId(), problemIndex)) {
            return "redirect:/projects/new/ai/status/" + workflowId;
        }
        return preCheckReviewRedirect(workflowId);
    }

    @PostMapping("/problems/{workflowId}/open-points/{problemIndex}/confirm")
    public String confirmOpenPointContext(
            @PathVariable UUID workflowId,
            @PathVariable int problemIndex,
            @Valid @ModelAttribute("openPointConfirmationForm") OpenPointConfirmationForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("review", preCheckReviewService.getReview(workflowId, currentUser.userId()));
            model.addAttribute("editingProblemIndex", problemIndex);
            return "generation/ai-problems";
        }
        if (preCheckReviewService.confirmOpenPointContext(
                workflowId, currentUser.userId(), problemIndex, form.getPlanningContext())) {
            return "redirect:/projects/new/ai/status/" + workflowId;
        }
        return preCheckReviewRedirect(workflowId);
    }

    @PostMapping("/status/{workflowId}/retry")
    public String retryGeneration(
            @PathVariable UUID workflowId,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        generationWorkflowService.retry(workflowId, currentUser.userId());
        return "redirect:/projects/new/ai/status/" + workflowId;
    }

    private String preCheckReviewRedirect(UUID workflowId) {
        return "redirect:/projects/new/ai/problems/" + workflowId;
    }
}
