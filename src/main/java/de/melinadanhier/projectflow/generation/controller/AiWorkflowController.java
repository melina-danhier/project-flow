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
import java.util.ArrayList;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestParam;
import de.melinadanhier.projectflow.generation.dto.*;
import de.melinadanhier.projectflow.generation.service.assumption.CriticalAssumptionReviewService;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;

import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.MAX_CRITICAL_ASSUMPTIONS;

@Controller
@RequiredArgsConstructor
@RequestMapping("/projects/new/ai")
public class AiWorkflowController {

    private final AiWorkflowQueryService workflowQueryService;
    private final AiPreCheckReviewService reviewService;
    private final AiGenerationWorkflowService generationWorkflowService;
    private final CriticalAssumptionReviewService assumptionReviewService;

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
        if (workflow.status() == AiPlanGenerationWorkflowStatus.ASSUMPTIONS_REVIEW_PENDING
                || workflow.pendingAssumptionReview()) {
            return "redirect:/projects/new/ai/assumptions/" + workflowId;
        }
        model.addAttribute("workflow", workflow);
        return "generation/ai-status";
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
                                    @RequestParam MultiValueMap<String, String> parameters,
                                    @AuthenticationPrincipal AuthenticatedUser currentUser) {
        var decisions = new ArrayList<AssumptionDecisionRequest>();
        int count;
        try {
            count = Integer.parseInt(parameters.getFirst("assumptionCount"));
        } catch (RuntimeException exception) {
            throw new DomainValidationException("Die Annahmenprüfung ist unvollständig.");
        }
        if (count < 0 || count > MAX_CRITICAL_ASSUMPTIONS) {
            throw new DomainValidationException("Die Annahmenprüfung ist unvollständig.");
        }
        for (int index = 0; index < count; index++) {
            String value = parameters.getFirst("decision." + index);
            AssumptionDecision decision;
            try {
                decision = value == null ? null : AssumptionDecision.valueOf(value);
            } catch (IllegalArgumentException exception) {
                throw new DomainValidationException("Die Annahmenprüfung enthält eine ungültige Bewertung.");
            }
            decisions.add(new AssumptionDecisionRequest(index, decision,
                    parameters.getFirst("correction." + index)));
        }
        assumptionReviewService.submit(
                workflowId, currentUser.userId(), new AssumptionReviewRequest(decisions));
        return "redirect:/projects/new/ai/status/" + workflowId;
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
