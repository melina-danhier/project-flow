package de.melinadanhier.projectflow.draft.controller;

import de.melinadanhier.projectflow.draft.service.DraftApplicationService;
import de.melinadanhier.projectflow.draft.service.DraftReviewService;
import de.melinadanhier.projectflow.draft.dto.DraftSectionForm;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ExceptionHandler;
import de.melinadanhier.projectflow.draft.dto.DraftTaskForm;
import de.melinadanhier.projectflow.draft.dto.DraftMilestoneForm;
import de.melinadanhier.projectflow.draft.dto.DraftElementMoveForm;
import de.melinadanhier.projectflow.draft.dto.DraftSectionMoveForm;
import de.melinadanhier.projectflow.draft.dto.DraftSortModeForm;
import de.melinadanhier.projectflow.draft.service.CriticalAssumptionsConfirmationRequiredException;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.validation.BindingResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class DraftController {

    private final DraftReviewService draftReviewService;
    private final DraftApplicationService draftApplicationService;

    @GetMapping({"/projects/{projectId}/draft", "/projects/{projectId}/draft/review"})
    public String review(@PathVariable UUID projectId,
                         @AuthenticationPrincipal AuthenticatedUser currentUser,
                         Model model) {
        model.addAttribute(
                "draft",
                draftReviewService.review(projectId, currentUser.userId())
        );
        return "generation/draft-review";
    }

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

    @PostMapping("/projects/{projectId}/draft/apply")
    public String apply(@PathVariable UUID projectId,
                        @AuthenticationPrincipal AuthenticatedUser currentUser,
                        RedirectAttributes redirectAttributes) {
        draftApplicationService.apply(projectId, currentUser.userId());
        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Der KI-Entwurf wurde übernommen."
        );
        return "redirect:/projects/" + projectId + "/plan";
    }

    @PostMapping("/projects/{projectId}/draft/confirm-and-apply")
    public String confirmAndApply(@PathVariable UUID projectId, @RequestParam long lockVersion,
                                  @AuthenticationPrincipal AuthenticatedUser currentUser,
                                  RedirectAttributes redirectAttributes) {
        draftApplicationService.confirmAndApply(projectId, currentUser.userId(), lockVersion);
        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Der KI-Entwurf wurde übernommen."
        );
        return "redirect:/projects/" + projectId + "/plan";
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
        if (bindingResult.hasErrors()) throw new DomainValidationException("Die Zielposition ist ungültig.");
        draftReviewService.moveElement(projectId, elementId, currentUser.userId(), moveForm);
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/sections/{sectionId}/move")
    public String moveSection(@PathVariable UUID projectId, @PathVariable UUID sectionId,
                              @Valid @ModelAttribute DraftSectionMoveForm moveForm,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (bindingResult.hasErrors()) throw new DomainValidationException("Die Zielposition ist ungültig.");
        draftReviewService.moveSection(projectId, sectionId, currentUser.userId(), moveForm);
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/sort-mode")
    public String updateSortMode(@PathVariable UUID projectId,
                                 @Valid @ModelAttribute DraftSortModeForm sortModeForm,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (bindingResult.hasErrors()) throw new DomainValidationException("Der Sortiermodus ist ungültig.");
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

    @ExceptionHandler(CriticalAssumptionsConfirmationRequiredException.class)
    public String confirmation(CriticalAssumptionsConfirmationRequiredException exception, Model model) {
        model.addAttribute("draft", exception.getDraft());
        return "generation/draft-confirmation";
    }

    @ExceptionHandler({DomainValidationException.class, ConflictException.class})
    public String invalidDraft(RuntimeException exception,
                              RedirectAttributes attributes,
                              HttpServletRequest request) {
        var variables = (Map<?, ?>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        attributes.addFlashAttribute("errorMessage", exception.getMessage());
        return "redirect:/projects/" + variables.get("projectId") + "/draft/review";
    }

    private String reviewRedirect(UUID projectId) {
        return "redirect:/projects/" + projectId + "/draft/review";
    }
}
