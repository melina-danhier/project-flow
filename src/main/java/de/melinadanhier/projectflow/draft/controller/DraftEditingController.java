package de.melinadanhier.projectflow.draft.controller;

import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.draft.dto.editing.DraftElementMoveForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftMilestoneForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftSectionForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftSectionMoveForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftTaskForm;
import de.melinadanhier.projectflow.draft.service.DraftReviewService;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.ui.Model;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class DraftEditingController {

    private final DraftReviewService draftReviewService;

    @PostMapping("/projects/{projectId}/draft/sections/{sectionId}")
    public String updateSection(@PathVariable UUID projectId,
                                @PathVariable UUID sectionId,
                                @Valid @ModelAttribute DraftSectionForm sectionForm,
                                BindingResult bindingResult,
                                @AuthenticationPrincipal AuthenticatedUser currentUser,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return renderInvalidSection(projectId, sectionId, currentUser.userId(), model);
        }
        try {
            draftReviewService.updateSection(projectId, sectionId, currentUser.userId(), sectionForm);
        } catch (DomainValidationException exception) {
            bindingResult.reject("draftSection", exception.getMessage());
            return renderInvalidSection(projectId, sectionId, currentUser.userId(), model);
        }
        redirectAttributes.addFlashAttribute("successMessage", "Der Bereich wurde aktualisiert.");
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/tasks/{taskId}")
    public String updateTask(@PathVariable UUID projectId, @PathVariable UUID taskId,
                             @Valid @ModelAttribute DraftTaskForm taskForm,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal AuthenticatedUser currentUser,
                             Model model) {
        if (bindingResult.hasErrors()) {
            return renderInvalidTask(projectId, taskId, currentUser.userId(), model);
        }
        try {
            draftReviewService.updateTask(projectId, taskId, currentUser.userId(), taskForm);
        } catch (DomainValidationException exception) {
            bindingResult.reject("draftTask", exception.getMessage());
            return renderInvalidTask(projectId, taskId, currentUser.userId(), model);
        }
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/milestones/{milestoneId}")
    public String updateMilestone(@PathVariable UUID projectId, @PathVariable UUID milestoneId,
                                  @Valid @ModelAttribute DraftMilestoneForm milestoneForm,
                                  BindingResult bindingResult,
                                  @AuthenticationPrincipal AuthenticatedUser currentUser,
                                  Model model) {
        if (bindingResult.hasErrors()) {
            return renderInvalidMilestone(projectId, milestoneId, currentUser.userId(), model);
        }
        try {
            draftReviewService.updateMilestone(projectId, milestoneId, currentUser.userId(), milestoneForm);
        } catch (DomainValidationException exception) {
            bindingResult.reject("draftMilestone", exception.getMessage());
            return renderInvalidMilestone(projectId, milestoneId, currentUser.userId(), model);
        }
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/elements/{elementId}/move")
    public String moveElement(@PathVariable UUID projectId, @PathVariable UUID elementId,
                              @Valid @ModelAttribute DraftElementMoveForm moveForm,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (bindingResult.hasErrors()) {
            throw new DomainValidationException("Die Zielposition ist ungültig.");
        }
        draftReviewService.moveElement(projectId, elementId, currentUser.userId(), moveForm);
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/sections/{sectionId}/move")
    public String moveSection(@PathVariable UUID projectId, @PathVariable UUID sectionId,
                              @Valid @ModelAttribute DraftSectionMoveForm moveForm,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (bindingResult.hasErrors()) {
            throw new DomainValidationException("Die Zielposition ist ungültig.");
        }
        draftReviewService.moveSection(projectId, sectionId, currentUser.userId(), moveForm);
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/tasks/{taskId}/delete")
    public String deleteTask(@PathVariable UUID projectId, @PathVariable UUID taskId,
                             @RequestParam long lockVersion,
                             @AuthenticationPrincipal AuthenticatedUser currentUser) {
        draftReviewService.deleteTask(projectId, taskId, currentUser.userId(), lockVersion);
        return reviewRedirect(projectId);
    }

    private String reviewRedirect(UUID projectId) {
        return "redirect:/projects/" + projectId + "/draft/review";
    }

    private String renderInvalidSection(UUID projectId, UUID sectionId, UUID userId, Model model) {
        model.addAttribute("editingDraftSectionId", sectionId);
        return renderReview(projectId, userId, model);
    }

    private String renderInvalidTask(UUID projectId, UUID taskId, UUID userId, Model model) {
        model.addAttribute("editingDraftTaskId", taskId);
        return renderReview(projectId, userId, model);
    }

    private String renderInvalidMilestone(UUID projectId, UUID milestoneId, UUID userId, Model model) {
        model.addAttribute("editingDraftMilestoneId", milestoneId);
        return renderReview(projectId, userId, model);
    }

    private String renderReview(UUID projectId, UUID userId, Model model) {
        model.addAttribute("draft", draftReviewService.review(projectId, userId, null));
        return "generation/draft-review";
    }
}
