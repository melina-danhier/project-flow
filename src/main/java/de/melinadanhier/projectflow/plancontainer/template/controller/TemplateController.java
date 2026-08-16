package de.melinadanhier.projectflow.plancontainer.template.controller;

import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectCreationFlowState;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectCreateForm;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectCreationFlowService;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectService;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.service.TemplateService;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.validation.BindingResult;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final ProjectService projectService;
    private final ProjectCreationFlowService creationFlowService;

    @GetMapping("/templates")
    public String overview(Model model) {
        model.addAttribute("templates", templateService.getTemplates());
        return "templates/overview";
    }

    @GetMapping("/projects/new/template")
    public String creationEntry(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model
    ) {
        ProjectCreationFlowState state = creationFlowService.requireOwned(currentUser.userId(), session);
        if (state.getCreationType() != CreationType.TEMPLATE) {
            throw new ResourceNotFoundException("Vorlagen-Erstellungsablauf wurde nicht gefunden.");
        }
        model.addAttribute("creationFlow", state);
        model.addAttribute("templates", templateService.getTemplates());
        return "templates/overview";
    }

    @GetMapping("/templates/{templateId}")
    public String detail(@PathVariable UUID templateId, Model model) {
        var template = templateService.getTemplate(templateId);
        model.addAttribute("template", template);
        ProjectCreateForm form = new ProjectCreateForm();
        form.setCreationType(CreationType.TEMPLATE);
        form.setCategory(template.getCategory());
        form.setProjectType(template.getProjectType());
        form.setCollaborationMode(template.getCollaborationMode() == CollaborationMode.BOTH
                ? CollaborationMode.INDIVIDUAL
                : template.getCollaborationMode());
        model.addAttribute("projectForm", form);
        return "templates/detail";
    }

    @PostMapping("/templates/{templateId}/projects")
    public String createProject(
            @PathVariable UUID templateId,
            @Valid @ModelAttribute("projectForm") ProjectCreateForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        form.setCreationType(CreationType.TEMPLATE);
        if (bindingResult.hasErrors()) {
            model.addAttribute("template", templateService.getTemplate(templateId));
            return "templates/detail";
        }
        var project = projectService.createProjectFromTemplate(templateId, form, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Projekt wurde aus der Vorlage angelegt.");
        return "redirect:/projects/" + project.getId() + "/plan";
    }
}
