package de.melinadanhier.projectflow.draft.controller;

import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.draft.dto.editing.DraftElementMoveForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftMilestoneForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftSectionForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftSectionMoveForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftSortModeForm;
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
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            throw new DomainValidationException("Bitte prüfe Titel und Beschreibung des Bereichs.");
        }
        draftReviewService.updateSection(projectId, sectionId, currentUser.userId(), sectionForm);
        redirectAttributes.addFlashAttribute("successMessage", "Der Bereich wurde aktualisiert.");
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/tasks/{taskId}")
    public String updateTask(@PathVariable UUID projectId, @PathVariable UUID taskId,
                             @Valid @ModelAttribute DraftTaskForm taskForm,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (bindingResult.hasErrors()) {
            throw new DomainValidationException(
                    "Bitte prüfe die Aufgabenangaben, insbesondere Datums- und Zahlenfelder."
            );
        }
        draftReviewService.updateTask(projectId, taskId, currentUser.userId(), taskForm);
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/milestones/{milestoneId}")
    public String updateMilestone(@PathVariable UUID projectId, @PathVariable UUID milestoneId,
                                  @Valid @ModelAttribute DraftMilestoneForm milestoneForm,
                                  BindingResult bindingResult,
                                  @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (bindingResult.hasErrors()) {
            throw new DomainValidationException("Bitte prüfe die Meilensteinangaben.");
        }
        draftReviewService.updateMilestone(projectId, milestoneId, currentUser.userId(), milestoneForm);
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

    @PostMapping("/projects/{projectId}/draft/sort-mode")
    public String updateSortMode(@PathVariable UUID projectId,
                                 @Valid @ModelAttribute DraftSortModeForm sortModeForm,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (bindingResult.hasErrors()) {
            throw new DomainValidationException("Der Sortiermodus ist ungültig.");
        }
        draftReviewService.updateSortMode(projectId, currentUser.userId(), sortModeForm);
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
}
