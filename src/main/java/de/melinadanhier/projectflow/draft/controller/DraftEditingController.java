package de.melinadanhier.projectflow.draft.controller;

import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.draft.dto.editing.DraftElementMoveForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftMilestoneForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftSectionForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftSectionMoveForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftTaskForm;
import de.melinadanhier.projectflow.draft.service.DraftReviewService;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import de.melinadanhier.projectflow.study.domain.StudyEventType;
import de.melinadanhier.projectflow.study.service.StudyTrackingService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.ui.Model;

import java.util.UUID;
import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class DraftEditingController {

    private final DraftReviewService draftReviewService;
    private final StudyTrackingService studyTrackingService;

    @GetMapping("/projects/{projectId}/draft/tasks/{taskId}")
    public String taskDetail(@PathVariable UUID projectId, @PathVariable UUID taskId,
                             @AuthenticationPrincipal AuthenticatedUser currentUser, Model model) {
        populateElementDetail(projectId, taskId, "TASK", currentUser.userId(), model);
        return "generation/draft-element-detail";
    }

    @GetMapping("/projects/{projectId}/draft/milestones/{milestoneId}")
    public String milestoneDetail(@PathVariable UUID projectId, @PathVariable UUID milestoneId,
                                  @AuthenticationPrincipal AuthenticatedUser currentUser, Model model) {
        populateElementDetail(projectId, milestoneId, "MILESTONE", currentUser.userId(), model);
        return "generation/draft-element-detail";
    }

    @GetMapping("/projects/{projectId}/draft/tasks/{taskId}/edit")
    public String taskEditForm(@PathVariable UUID projectId, @PathVariable UUID taskId,
                               @AuthenticationPrincipal AuthenticatedUser currentUser, Model model) {
        populateElementDetail(projectId, taskId, "TASK", currentUser.userId(), model);
        return "generation/draft-element-form";
    }

    @GetMapping("/projects/{projectId}/draft/milestones/{milestoneId}/edit")
    public String milestoneEditForm(@PathVariable UUID projectId, @PathVariable UUID milestoneId,
                                    @AuthenticationPrincipal AuthenticatedUser currentUser, Model model) {
        populateElementDetail(projectId, milestoneId, "MILESTONE", currentUser.userId(), model);
        return "generation/draft-element-form";
    }

    @PostMapping("/projects/{projectId}/draft/tasks/{taskId}/detail")
    public String updateTaskFromDetail(@PathVariable UUID projectId, @PathVariable UUID taskId,
                                       @Valid @ModelAttribute("taskForm") DraftTaskForm taskForm,
                                       BindingResult bindingResult,
                                       @AuthenticationPrincipal AuthenticatedUser currentUser,
                                       Model model, RedirectAttributes redirectAttributes, HttpSession session) {
        if (bindingResult.hasErrors()) {
            populateElementDetail(projectId, taskId, "TASK", currentUser.userId(), model);
            return "generation/draft-element-form";
        }
        try {
            draftReviewService.updateTask(projectId, taskId, currentUser.userId(), taskForm);
        } catch (DomainValidationException exception) {
            bindingResult.reject("draftTask", exception.getMessage());
            populateElementDetail(projectId, taskId, "TASK", currentUser.userId(), model);
            return "generation/draft-element-form";
        }
        studyTrackingService.trackIfActive(session, StudyEventType.DRAFT_ITEM_EDITED);
        redirectAttributes.addFlashAttribute("successMessage", "Die Aufgabe wurde aktualisiert.");
        return detailRedirect(projectId, "tasks", taskId);
    }

    @PostMapping("/projects/{projectId}/draft/milestones/{milestoneId}/detail")
    public String updateMilestoneFromDetail(@PathVariable UUID projectId, @PathVariable UUID milestoneId,
                                            @Valid @ModelAttribute("milestoneForm") DraftMilestoneForm milestoneForm,
                                            BindingResult bindingResult,
                                            @AuthenticationPrincipal AuthenticatedUser currentUser,
                                            Model model, RedirectAttributes redirectAttributes, HttpSession session) {
        if (bindingResult.hasErrors()) {
            populateElementDetail(projectId, milestoneId, "MILESTONE", currentUser.userId(), model);
            return "generation/draft-element-form";
        }
        try {
            draftReviewService.updateMilestone(projectId, milestoneId, currentUser.userId(), milestoneForm);
        } catch (DomainValidationException exception) {
            bindingResult.reject("draftMilestone", exception.getMessage());
            populateElementDetail(projectId, milestoneId, "MILESTONE", currentUser.userId(), model);
            return "generation/draft-element-form";
        }
        studyTrackingService.trackIfActive(session, StudyEventType.DRAFT_ITEM_EDITED);
        redirectAttributes.addFlashAttribute("successMessage", "Der Meilenstein wurde aktualisiert.");
        return detailRedirect(projectId, "milestones", milestoneId);
    }

    @PostMapping("/projects/{projectId}/draft/sections/{sectionId}")
    public String updateSection(@PathVariable UUID projectId,
                                @PathVariable UUID sectionId,
                                @Valid @ModelAttribute DraftSectionForm sectionForm,
                                BindingResult bindingResult,
                                @AuthenticationPrincipal AuthenticatedUser currentUser,
                                Model model,
                                RedirectAttributes redirectAttributes, HttpSession session) {
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
        studyTrackingService.trackIfActive(session, StudyEventType.DRAFT_ITEM_EDITED);
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/tasks/{taskId}")
    public String updateTask(@PathVariable UUID projectId, @PathVariable UUID taskId,
                             @Valid @ModelAttribute("taskForm") DraftTaskForm taskForm,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal AuthenticatedUser currentUser,
                             Model model, HttpSession session) {
        if (bindingResult.hasErrors()) {
            populateElementDetail(projectId, taskId, "TASK", currentUser.userId(), model);
            return "generation/draft-element-form";
        }
        try {
            draftReviewService.updateTask(projectId, taskId, currentUser.userId(), taskForm);
        } catch (DomainValidationException exception) {
            bindingResult.reject("draftTask", exception.getMessage());
            populateElementDetail(projectId, taskId, "TASK", currentUser.userId(), model);
            return "generation/draft-element-form";
        }
        studyTrackingService.trackIfActive(session, StudyEventType.DRAFT_ITEM_EDITED);
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/milestones/{milestoneId}")
    public String updateMilestone(@PathVariable UUID projectId, @PathVariable UUID milestoneId,
                                  @Valid @ModelAttribute("milestoneForm") DraftMilestoneForm milestoneForm,
                                  BindingResult bindingResult,
                                  @AuthenticationPrincipal AuthenticatedUser currentUser,
                                  Model model, HttpSession session) {
        if (bindingResult.hasErrors()) {
            populateElementDetail(projectId, milestoneId, "MILESTONE", currentUser.userId(), model);
            return "generation/draft-element-form";
        }
        try {
            draftReviewService.updateMilestone(projectId, milestoneId, currentUser.userId(), milestoneForm);
        } catch (DomainValidationException exception) {
            bindingResult.reject("draftMilestone", exception.getMessage());
            populateElementDetail(projectId, milestoneId, "MILESTONE", currentUser.userId(), model);
            return "generation/draft-element-form";
        }
        studyTrackingService.trackIfActive(session, StudyEventType.DRAFT_ITEM_EDITED);
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/elements/{elementId}/move")
    public String moveElement(@PathVariable UUID projectId, @PathVariable UUID elementId,
                              @Valid @ModelAttribute DraftElementMoveForm moveForm,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal AuthenticatedUser currentUser, HttpSession session) {
        if (bindingResult.hasErrors()) {
            throw new DomainValidationException("Die Zielposition ist ungültig.");
        }
        draftReviewService.moveElement(projectId, elementId, currentUser.userId(), moveForm);
        studyTrackingService.trackIfActive(session, StudyEventType.DRAFT_ITEM_EDITED);
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/sections/{sectionId}/move")
    public String moveSection(@PathVariable UUID projectId, @PathVariable UUID sectionId,
                              @Valid @ModelAttribute DraftSectionMoveForm moveForm,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal AuthenticatedUser currentUser, HttpSession session) {
        if (bindingResult.hasErrors()) {
            throw new DomainValidationException("Die Zielposition ist ungültig.");
        }
        draftReviewService.moveSection(projectId, sectionId, currentUser.userId(), moveForm);
        studyTrackingService.trackIfActive(session, StudyEventType.DRAFT_ITEM_EDITED);
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/tasks/{taskId}/delete")
    public String deleteTask(@PathVariable UUID projectId, @PathVariable UUID taskId,
                             @RequestParam long lockVersion,
                             @AuthenticationPrincipal AuthenticatedUser currentUser, HttpSession session) {
        draftReviewService.deleteTask(projectId, taskId, currentUser.userId(), lockVersion);
        studyTrackingService.trackIfActive(session, StudyEventType.DRAFT_ITEM_EDITED);
        return reviewRedirect(projectId);
    }

    private String reviewRedirect(UUID projectId) {
        return "redirect:/projects/" + projectId + "/draft/review";
    }

    @PostMapping("/projects/{projectId}/draft/elements/{elementId}/date")
    public String updateElementDate(@PathVariable UUID projectId, @PathVariable UUID elementId,
                                    @RequestParam LocalDate targetDate, @RequestParam long lockVersion,
                                    @AuthenticationPrincipal AuthenticatedUser currentUser, HttpSession session) {
        draftReviewService.updateElementDate(projectId, elementId, currentUser.userId(), targetDate, lockVersion);
        studyTrackingService.trackIfActive(session, StudyEventType.DRAFT_ITEM_EDITED);
        return reviewRedirect(projectId);
    }

    private String detailRedirect(UUID projectId, String typePath, UUID elementId) {
        return "redirect:/projects/" + projectId + "/draft/" + typePath + "/" + elementId;
    }

    private void populateElementDetail(UUID projectId, UUID elementId, String expectedType,
                                       UUID userId, Model model) {
        var draft = draftReviewService.review(projectId, userId, null);
        var element = draft.getElements().stream()
                .filter(candidate -> candidate.getId().equals(elementId) && candidate.getType().equals(expectedType))
                .findFirst()
                .orElseThrow(() -> new de.melinadanhier.projectflow.common.exception.ResourceNotFoundException(
                        "Entwurfselement nicht gefunden."));
        model.addAttribute("draft", draft);
        model.addAttribute("draftElement", element);
        model.addAttribute("sectionTitle", draft.getSections().stream()
                .filter(section -> section.getId().equals(element.getDraftSectionId()))
                .map(de.melinadanhier.projectflow.draft.dto.review.DraftSectionDto::getTitle)
                .findFirst().orElse("Ohne Bereich"));
        if (expectedType.equals("TASK") && !model.containsAttribute("taskForm")) {
            DraftTaskForm form = new DraftTaskForm();
            form.setLockVersion(draft.getLockVersion());
            form.setTitle(element.getTitle());
            form.setDescription(element.getDescription());
            form.setDraftSectionId(element.getDraftSectionId());
            form.setSectionSelectionPresent(true);
            form.setStartDate(element.getStartDate());
            form.setDueDate(element.getDueDate());
            form.setEstimatedHours(element.getEstimatedHours());
            form.setPriority(element.getPriority());
            model.addAttribute("taskForm", form);
        }
        if (expectedType.equals("MILESTONE") && !model.containsAttribute("milestoneForm")) {
            DraftMilestoneForm form = new DraftMilestoneForm();
            form.setLockVersion(draft.getLockVersion());
            form.setTitle(element.getTitle());
            form.setDescription(element.getDescription());
            form.setDraftSectionId(element.getDraftSectionId());
            form.setSectionSelectionPresent(true);
            form.setDueDate(element.getDueDate());
            model.addAttribute("milestoneForm", form);
        }
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
