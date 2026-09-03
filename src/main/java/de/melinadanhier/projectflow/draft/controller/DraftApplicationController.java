package de.melinadanhier.projectflow.draft.controller;

import de.melinadanhier.projectflow.draft.dto.application.DraftApplyResult;
import de.melinadanhier.projectflow.draft.service.DraftApplicationPersistenceException;
import de.melinadanhier.projectflow.draft.service.DraftApplicationService;
import de.melinadanhier.projectflow.generation.service.workflow.AiGenerationWorkflowService;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;
import java.util.function.Supplier;

@Controller
@RequiredArgsConstructor
public class DraftApplicationController {

    private final DraftApplicationService draftApplicationService;
    private final AiGenerationWorkflowService generationWorkflowService;

    @PostMapping("/projects/{projectId}/draft/apply")
    public String apply(@PathVariable UUID projectId,
                        @AuthenticationPrincipal AuthenticatedUser currentUser,
                        RedirectAttributes redirectAttributes,
                        Model model) {
        DraftApplyResult result = executeApplication(
                () -> draftApplicationService.apply(projectId, currentUser.userId()));
        return switch (result.status()) {
            case APPLIED -> appliedRedirect(projectId, redirectAttributes);
            case PENDING_CONFIRMATION_REQUIRED, EMPTY_DRAFT_CONFIRMATION_REQUIRED -> {
                model.addAttribute("summary", result.summary());
                yield "generation/draft-pending-confirmation";
            }
        };
    }

    @PostMapping("/projects/{projectId}/draft/continue-with-pending")
    public String continueWithPending(@PathVariable UUID projectId,
                                      @RequestParam UUID draftId,
                                      @RequestParam long lockVersion,
                                      @AuthenticationPrincipal AuthenticatedUser currentUser,
                                      RedirectAttributes redirectAttributes) {
        executeApplication(() -> draftApplicationService.continueWithPending(
                projectId, draftId, currentUser.userId(), lockVersion));
        return appliedRedirect(projectId, redirectAttributes);
    }

    @PostMapping("/projects/{projectId}/draft/confirm-empty")
    public String confirmEmpty(@PathVariable UUID projectId,
                               @RequestParam UUID draftId,
                               @RequestParam long lockVersion,
                               @AuthenticationPrincipal AuthenticatedUser currentUser,
                               RedirectAttributes redirectAttributes) {
        executeApplication(() -> draftApplicationService.confirmEmpty(
                projectId, draftId, currentUser.userId(), lockVersion));
        return appliedRedirect(projectId, redirectAttributes);
    }

    @PostMapping("/projects/{projectId}/draft/regenerate")
    public String regenerate(@PathVariable UUID projectId,
                             @RequestParam UUID draftId,
                             @RequestParam long lockVersion,
                             @AuthenticationPrincipal AuthenticatedUser currentUser) {
        UUID workflowId = generationWorkflowService.regenerateDraft(
                projectId, draftId, currentUser.userId(), lockVersion);
        return "redirect:/projects/new/ai/status/" + workflowId;
    }

    private String appliedRedirect(UUID projectId, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("successMessage",
                "Der KI-Entwurf wurde übernommen.");
        return "redirect:/projects/" + projectId + "/plan";
    }

    private <T> T executeApplication(Supplier<T> application) {
        try {
            return application.get();
        } catch (DataAccessException exception) {
            throw new DraftApplicationPersistenceException(exception);
        }
    }
}
