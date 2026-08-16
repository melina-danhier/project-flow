package de.melinadanhier.projectflow.planelement.controller;

import de.melinadanhier.projectflow.planelement.dto.MilestoneDetailsDto;
import de.melinadanhier.projectflow.planelement.dto.MilestoneForm;
import de.melinadanhier.projectflow.planelement.service.MilestoneService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class MilestoneController {

    private final MilestoneService milestoneService;

    @GetMapping("/projects/{projectId}/milestones/new")
    public String createForm(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model
    ) {
        model.addAttribute("milestoneForm", new MilestoneForm());
        populateFormModel(model, milestoneService.getMilestoneCreationContext(projectId, currentUser.userId()), false);
        return "projects/milestones/form";
    }

    @PostMapping("/projects/{projectId}/milestones")
    public String create(
            @PathVariable UUID projectId,
            @Valid @ModelAttribute("milestoneForm") MilestoneForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            populateFormModel(
                    model, milestoneService.getMilestoneCreationContext(projectId, currentUser.userId()), false);
            return "projects/milestones/form";
        }
        milestoneService.createMilestone(projectId, form, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Meilenstein wurde angelegt.");
        return planRedirect(projectId);
    }

    @GetMapping("/projects/{projectId}/milestones/{milestoneId}/edit")
    public String editForm(
            @PathVariable UUID projectId,
            @PathVariable UUID milestoneId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model
    ) {
        MilestoneDetailsDto milestone = milestoneService.getMilestoneForEditing(
                projectId, milestoneId, currentUser.userId());
        model.addAttribute("milestoneForm", toForm(milestone));
        populateFormModel(model, milestone, true);
        return "projects/milestones/form";
    }

    @PostMapping("/projects/{projectId}/milestones/{milestoneId}")
    public String update(
            @PathVariable UUID projectId,
            @PathVariable UUID milestoneId,
            @Valid @ModelAttribute("milestoneForm") MilestoneForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            populateFormModel(
                    model,
                    milestoneService.getMilestoneForEditing(projectId, milestoneId, currentUser.userId()),
                    true
            );
            return "projects/milestones/form";
        }
        milestoneService.updateMilestone(projectId, milestoneId, form, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Meilenstein wurde aktualisiert.");
        return planRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/milestones/{milestoneId}/delete")
    public String delete(
            @PathVariable UUID projectId,
            @PathVariable UUID milestoneId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            RedirectAttributes redirectAttributes
    ) {
        milestoneService.deleteMilestone(projectId, milestoneId, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Meilenstein wurde endgültig gelöscht.");
        return planRedirect(projectId);
    }

    private void populateFormModel(Model model, MilestoneDetailsDto context, boolean editing) {
        model.addAttribute("projectId", context.getPlanContainerId());
        model.addAttribute("milestoneId", context.getId());
        model.addAttribute("sections", context.getAvailableSections());
        model.addAttribute("editing", editing);
    }

    private MilestoneForm toForm(MilestoneDetailsDto milestone) {
        MilestoneForm form = new MilestoneForm();
        form.setPlanSectionId(milestone.getPlanSectionId());
        form.setTitle(milestone.getTitle());
        form.setDescription(milestone.getDescription());
        form.setSortOrder(milestone.getSortOrder());
        form.setDueDate(milestone.getDueDate());
        form.setCompleted(milestone.isCompleted());
        form.setLockVersion(milestone.getLockVersion());
        return form;
    }

    private String planRedirect(UUID projectId) {
        return "redirect:/projects/" + projectId + "/plan";
    }
}
