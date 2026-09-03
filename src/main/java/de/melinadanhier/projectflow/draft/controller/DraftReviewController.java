package de.melinadanhier.projectflow.draft.controller;

import de.melinadanhier.projectflow.draft.model.DraftReviewStatus;
import de.melinadanhier.projectflow.draft.service.DraftReviewService;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class DraftReviewController {

    private final DraftReviewService draftReviewService;
    private final AiPlanGenerationWorkflowRepository workflowRepository;

    @GetMapping({"/projects/{projectId}/draft", "/projects/{projectId}/draft/review"})
    public String review(@PathVariable UUID projectId,
                         @AuthenticationPrincipal AuthenticatedUser currentUser,
                         @RequestParam(required = false) DraftReviewStatus reviewStatus,
                         Model model) {
        var workflow = workflowRepository.findOwnedByProjectId(projectId, currentUser.userId()).orElse(null);
        if (workflow != null && workflow.getStatus() != AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED) {
            return "redirect:/projects/new/ai/status/" + workflow.getId();
        }
        var draft = draftReviewService.review(projectId, currentUser.userId(), reviewStatus);
        model.addAttribute("draft", draft);
        return "generation/draft-review";
    }

    @PostMapping("/projects/{projectId}/draft/elements/{elementId}/accept")
    public String acceptElement(@PathVariable UUID projectId, @PathVariable UUID elementId,
                                @RequestParam long lockVersion,
                                @AuthenticationPrincipal AuthenticatedUser currentUser) {
        draftReviewService.acceptElement(projectId, elementId, currentUser.userId(), lockVersion);
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/sections/{sectionId}/accept")
    public String acceptSection(@PathVariable UUID projectId, @PathVariable UUID sectionId,
                                @RequestParam long lockVersion,
                                @AuthenticationPrincipal AuthenticatedUser currentUser) {
        draftReviewService.acceptSection(projectId, sectionId, currentUser.userId(), lockVersion);
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/elements/{elementId}/reject")
    public String rejectElement(@PathVariable UUID projectId, @PathVariable UUID elementId,
                                @RequestParam long lockVersion,
                                @AuthenticationPrincipal AuthenticatedUser currentUser) {
        draftReviewService.rejectElement(projectId, elementId, currentUser.userId(), lockVersion);
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/elements/{elementId}/reset")
    public String resetElement(@PathVariable UUID projectId, @PathVariable UUID elementId,
                               @RequestParam long lockVersion,
                               @AuthenticationPrincipal AuthenticatedUser currentUser) {
        draftReviewService.resetElement(projectId, elementId, currentUser.userId(), lockVersion);
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/sections/{sectionId}/reject")
    public String rejectSection(@PathVariable UUID projectId, @PathVariable UUID sectionId,
                                @RequestParam long lockVersion,
                                @AuthenticationPrincipal AuthenticatedUser currentUser) {
        draftReviewService.rejectSection(projectId, sectionId, currentUser.userId(), lockVersion);
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/sections/{sectionId}/reset")
    public String resetSection(@PathVariable UUID projectId, @PathVariable UUID sectionId,
                               @RequestParam long lockVersion,
                               @AuthenticationPrincipal AuthenticatedUser currentUser) {
        draftReviewService.resetSection(projectId, sectionId, currentUser.userId(), lockVersion);
        return reviewRedirect(projectId);
    }

    private String reviewRedirect(UUID projectId) {
        return "redirect:/projects/" + projectId + "/draft/review";
    }
}
