package de.melinadanhier.projectflow.generation.controller;

import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.generation.dto.AssumptionReviewForm;
import de.melinadanhier.projectflow.generation.dto.response.AiWorkflowStatusDto;
import de.melinadanhier.projectflow.generation.service.assumption.CriticalAssumptionReviewService;
import de.melinadanhier.projectflow.generation.service.precheck.AiPreCheckReviewService;
import de.melinadanhier.projectflow.generation.service.workflow.AiGenerationWorkflowService;
import de.melinadanhier.projectflow.generation.service.workflow.AiWorkflowControlService;
import de.melinadanhier.projectflow.generation.service.workflow.AiWorkflowQueryService;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/projects/new/ai")
public class AiWorkflowController {

    private final AiWorkflowQueryService workflowQueryService;
    private final AiPreCheckReviewService preCheckReviewService;
    private final AiGenerationWorkflowService generationWorkflowService;
    private final CriticalAssumptionReviewService assumptionReviewService;
    private final AiWorkflowControlService workflowControlService;

    @GetMapping("/status/{workflowId}")
    public String status(
            @PathVariable UUID workflowId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model
    ) {
        AiWorkflowStatusDto workflow = workflowQueryService.getOwnedStatus(
                workflowId, currentUser.userId()
        );
        switch (workflow.status()) {
            case PRE_CHECK_NEEDS_REVIEW, PRE_CHECK_SUCCEEDED, GENERATION_CANCELLED -> {
                return problemsRedirect(workflowId);
            }
            case GENERATION_COMPLETED -> {
                return "redirect:/projects/" + workflow.projectId() + "/draft/review";
            }
            case ASSUMPTIONS_REVIEW_PENDING -> {
                return assumptionsRedirect(workflowId);
            }
            default -> {
                if (workflow.failedAssumptionRegeneration()) {
                    return assumptionsRedirect(workflowId);
                }
            }
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

    @GetMapping("/assumptions/{workflowId}")
    public String assumptions(@PathVariable UUID workflowId,
                              @AuthenticationPrincipal AuthenticatedUser currentUser,
                              Model model) {
        model.addAttribute("review", assumptionReviewService.getReview(workflowId, currentUser.userId()));
        return "generation/assumption-review";
    }

    @PostMapping("/assumptions/{workflowId}")
    public String submitAssumptions(@PathVariable UUID workflowId,
                                    @Valid @ModelAttribute AssumptionReviewForm form,
                                    BindingResult bindingResult,
                                    @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (bindingResult.hasErrors()) {
            throw new DomainValidationException("Die Annahmenprüfung ist unvollständig.");
        }
        assumptionReviewService.submit(
                workflowId, currentUser.userId(), form.toRequest());
        return "redirect:/projects/new/ai/status/" + workflowId;
    }

    @GetMapping("/problems/{workflowId}")
    public String problems(
            @PathVariable UUID workflowId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model
    ) {
        var review = preCheckReviewService.getReview(workflowId, currentUser.userId());
        model.addAttribute("review", review);
        return "generation/ai-problems";
    }

    @PostMapping("/problems/{workflowId}/warnings/{problemIndex}/acknowledge")
    public String acknowledgeWarning(
            @PathVariable UUID workflowId,
            @PathVariable int problemIndex,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        if (preCheckReviewService.acknowledgeWarning(workflowId, currentUser.userId(), problemIndex)) {
            return "redirect:/projects/new/ai/status/" + workflowId;
        }
        return "redirect:/projects/new/ai/problems/" + workflowId;
    }

    @PostMapping("/status/{workflowId}/retry")
    public String retryGeneration(
            @PathVariable UUID workflowId,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        generationWorkflowService.retry(workflowId, currentUser.userId());
        return "redirect:/projects/new/ai/status/" + workflowId;
    }

    private String problemsRedirect(UUID workflowId) {
        return "redirect:/projects/new/ai/problems/" + workflowId;
    }

    private String assumptionsRedirect(UUID workflowId) {
        return "redirect:/projects/new/ai/assumptions/" + workflowId;
    }

}
