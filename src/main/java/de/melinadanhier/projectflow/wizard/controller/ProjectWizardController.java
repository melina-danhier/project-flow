package de.melinadanhier.projectflow.wizard.controller;

import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectDetailsDto;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectService;
import de.melinadanhier.projectflow.plancontainer.template.service.TemplateService;
import de.melinadanhier.projectflow.generation.service.AiWizardCompletionService;
import de.melinadanhier.projectflow.generation.service.AiWorkflowCompletion;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import de.melinadanhier.projectflow.wizard.dto.AiProcessingConsentForm;
import de.melinadanhier.projectflow.wizard.dto.AiProjectDetailsForm;
import de.melinadanhier.projectflow.wizard.dto.ProjectBasicsForm;
import de.melinadanhier.projectflow.wizard.dto.ProjectCreationMethodForm;
import de.melinadanhier.projectflow.wizard.model.ProjectWizardState;
import de.melinadanhier.projectflow.wizard.service.ProjectWizardService;
import jakarta.servlet.http.HttpSession;
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
public class ProjectWizardController {

    private final ProjectWizardService wizardService;
    private final ProjectService projectService;
    private final TemplateService templateService;
    private final AiWizardCompletionService aiWizardCompletionService;

    @GetMapping("/projects/new")
    public String basics(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model
    ) {
        ProjectBasicsForm form = wizardService.findOwned(currentUser.userId(), session)
                .map(ProjectBasicsForm::from)
                .orElseGet(ProjectBasicsForm::new);
        model.addAttribute("projectBasicsForm", form);
        return "wizard/basics";
    }

    @PostMapping("/projects/new")
    public String saveBasics(
            @Valid @ModelAttribute("projectBasicsForm") ProjectBasicsForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session
    ) {
        if (bindingResult.hasErrors()) {
            return "wizard/basics";
        }
        wizardService.saveBasics(form, currentUser.userId(), session);
        return "redirect:/projects/new/method";
    }

    @GetMapping("/projects/new/method")
    public String method(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model
    ) {
        ProjectWizardState state = wizardService.requireOwned(currentUser.userId(), session);
        ProjectCreationMethodForm form = new ProjectCreationMethodForm();
        form.setCreationType(state.getCreationType());
        model.addAttribute("wizardState", state);
        model.addAttribute("creationMethodForm", form);
        return "wizard/method";
    }

    @PostMapping("/projects/new/method")
    public String selectMethod(
            @Valid @ModelAttribute("creationMethodForm") ProjectCreationMethodForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        ProjectWizardState state = wizardService.requireOwned(currentUser.userId(), session);
        if (bindingResult.hasErrors()) {
            model.addAttribute("wizardState", state);
            return "wizard/method";
        }
        wizardService.selectCreationType(form.getCreationType(), currentUser.userId(), session);
        return switch (form.getCreationType()) {
            case EMPTY -> createManualProject(currentUser.userId(), session, redirectAttributes);
            case TEMPLATE -> "redirect:/projects/new/template";
            case AI -> "redirect:/projects/new/ai/details";
        };
    }

    @GetMapping("/projects/new/template")
    public String templateCatalog(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model
    ) {
        ProjectWizardState state = wizardService.requireOwnedFor(
                CreationType.TEMPLATE, currentUser.userId(), session);
        model.addAttribute("wizardState", state);
        model.addAttribute("templates", templateService.getTemplates());
        templateService.findRecommendation(state.getCategory(), state.getProjectType())
                .ifPresent(template -> model.addAttribute("recommendedTemplateId", template.getId()));
        return "wizard/template-catalog";
    }

    @GetMapping("/projects/new/template/{templateId}")
    public String templatePreview(
            @PathVariable UUID templateId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model
    ) {
        model.addAttribute("wizardState", wizardService.requireOwnedFor(
                CreationType.TEMPLATE, currentUser.userId(), session));
        model.addAttribute("template", templateService.getTemplate(templateId));
        return "wizard/template-preview";
    }

    @PostMapping("/projects/new/template/{templateId}")
    public String useTemplate(
            @PathVariable UUID templateId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        wizardService.requireOwnedFor(CreationType.TEMPLATE, currentUser.userId(), session);
        ProjectDetailsDto project = projectService.createProjectFromTemplate(
                templateId, wizardService.projectData(currentUser.userId(), session), currentUser.userId());
        wizardService.clearOwned(currentUser.userId(), session);
        redirectAttributes.addFlashAttribute("successMessage", "Projekt wurde aus der Vorlage angelegt.");
        return "redirect:/projects/" + project.getId() + "/plan";
    }

    @GetMapping({"/projects/new/ai", "/projects/new/ai/details"})
    public String aiDetails(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model
    ) {
        ProjectWizardState state = wizardService.requireOwnedFor(
                CreationType.AI, currentUser.userId(), session);
        model.addAttribute("wizardState", state);
        model.addAttribute("aiProjectDetailsForm", AiProjectDetailsForm.from(state));
        return "generation/ai-details";
    }

    @PostMapping("/projects/new/ai/details")
    public String saveAiDetails(
            @Valid @ModelAttribute("aiProjectDetailsForm") AiProjectDetailsForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model
    ) {
        ProjectWizardState state = wizardService.requireOwnedFor(
                CreationType.AI, currentUser.userId(), session);
        if (bindingResult.hasErrors()) {
            model.addAttribute("wizardState", state);
            return "generation/ai-details";
        }
        wizardService.saveAiDetails(form, currentUser.userId(), session);
        return "redirect:/projects/new/ai/summary";
    }

    @GetMapping("/projects/new/ai/summary")
    public String aiSummary(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model
    ) {
        populateAiSummary(model, currentUser.userId(), session);
        if (!model.containsAttribute("aiProcessingConsentForm")) {
            AiProcessingConsentForm form = new AiProcessingConsentForm();
            form.setCompletionToken(wizardService.completionToken(currentUser.userId(), session));
            model.addAttribute("aiProcessingConsentForm", form);
        }
        return "generation/ai-summary";
    }

    @PostMapping("/projects/new/ai/confirm")
    public String confirmAiProcessing(
            @Valid @ModelAttribute("aiProcessingConsentForm") AiProcessingConsentForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            populateAiSummary(model, currentUser.userId(), session);
            return "generation/ai-summary";
        }
        AiWorkflowCompletion completion = aiWizardCompletionService.complete(
                form.getCompletionToken(),
                currentUser.userId(),
                () -> wizardService.confirmedSnapshot(
                        form.getCompletionToken(), currentUser.userId(), session)
        );
        wizardService.clearOwned(currentUser.userId(), session);
        return "redirect:/projects/new/ai/status/" + completion.workflowId();
    }

    @PostMapping("/projects/new/cancel")
    public String cancel(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        wizardService.clearOwned(currentUser.userId(), session);
        redirectAttributes.addFlashAttribute("successMessage", "Projekterstellung wurde abgebrochen.");
        return "redirect:/projects";
    }

    private String createManualProject(
            UUID userId,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        ProjectDetailsDto project = projectService.createProject(wizardService.projectData(userId, session), userId);
        wizardService.clearOwned(userId, session);
        redirectAttributes.addFlashAttribute("successMessage", "Projekt wurde angelegt.");
        return "redirect:/projects/" + project.getId() + "/plan";
    }

    private void populateAiSummary(Model model, UUID userId, HttpSession session) {
        model.addAttribute("summary", wizardService.aiSummary(userId, session));
        model.addAttribute("wizardState", wizardService.requireOwnedFor(CreationType.AI, userId, session));
    }
}
