package de.melinadanhier.projectflow.plancontainer.project.controller;

import de.melinadanhier.projectflow.common.validation.UpdateValidation;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectService;
import de.melinadanhier.projectflow.plancontainer.project.service.DraftProjectPlanAccessException;
import de.melinadanhier.projectflow.planelement.dto.DeleteSectionForm;
import de.melinadanhier.projectflow.planelement.dto.SectionForm;
import de.melinadanhier.projectflow.planelement.service.SectionService;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class PlanController {

    private final ProjectService projectService;
    private final SectionService sectionService;

    @GetMapping("/projects/{projectId}/plan")
    public String plan(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model
    ) {
        model.addAttribute("sectionForm", new SectionForm());
        populatePlan(model, projectId, currentUser.userId());
        return "projects/plan";
    }

    @PostMapping("/projects/{projectId}/sections")
    public String createSection(
            @PathVariable UUID projectId,
            @Valid @ModelAttribute("sectionForm") SectionForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model,
        RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            populatePlan(model, projectId, currentUser.userId());
            return "projects/plan";
        }
        sectionService.createSection(projectId, form, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Projektbereich wurde angelegt.");
        return "redirect:/projects/" + projectId + "/plan";
    }

    @PostMapping("/projects/{projectId}/sections/{sectionId}")
    public String updateSection(
            @PathVariable UUID projectId,
            @PathVariable UUID sectionId,
            @Validated({jakarta.validation.groups.Default.class, UpdateValidation.class})
            @ModelAttribute("sectionForm") SectionForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editingSectionId", sectionId);
            populatePlan(model, projectId, currentUser.userId());
            return "projects/plan";
        }
        sectionService.updateSection(projectId, sectionId, form, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Projektbereich wurde aktualisiert.");
        return "redirect:/projects/" + projectId + "/plan";
    }

    @PostMapping("/projects/{projectId}/sections/{sectionId}/delete")
    public String deleteSection(
            @PathVariable UUID projectId,
            @PathVariable UUID sectionId,
            @Valid @ModelAttribute("deleteSectionForm") DeleteSectionForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("errorMessage", "Bitte wähle aus, was mit den Inhalten geschehen soll.");
            model.addAttribute("sectionForm", new SectionForm());
            populatePlan(model, projectId, currentUser.userId());
            return "projects/plan";
        }
        sectionService.deleteSection(projectId, sectionId, form, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Projektbereich wurde gelöscht.");
        return "redirect:/projects/" + projectId + "/plan";
    }

    private void populatePlan(Model model, UUID projectId, UUID userId) {
        model.addAttribute("plan", projectService.getProjectPlan(projectId, userId));
    }

    @ExceptionHandler(DraftProjectPlanAccessException.class)
    public String redirectDraftProject(DraftProjectPlanAccessException exception) {
        return "redirect:/projects/" + exception.getProjectId() + "/draft/review";
    }
}
