package de.melinadanhier.projectflow.wizard.controller;

import de.melinadanhier.projectflow.plancontainer.project.dto.view.ProjectDetailsDto;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectService;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectService.TemplateDateHandling;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import de.melinadanhier.projectflow.plancontainer.template.service.TemplateService;
import de.melinadanhier.projectflow.plancontainer.template.service.TemplateDateAssessment;
import de.melinadanhier.projectflow.wizard.service.AiWizardCompletionService;
import de.melinadanhier.projectflow.generation.model.workflow.AiWorkflowCompletion;
import de.melinadanhier.projectflow.generation.service.precheck.AiPreCheckReviewService;
import de.melinadanhier.projectflow.generation.service.workflow.AiWorkflowControlService;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import de.melinadanhier.projectflow.wizard.dto.AiProcessingConsentForm;
import de.melinadanhier.projectflow.wizard.dto.AiProjectDetailsForm;
import de.melinadanhier.projectflow.wizard.dto.ProjectBasicsForm;
import de.melinadanhier.projectflow.wizard.dto.ProjectCreationMethodForm;
import de.melinadanhier.projectflow.wizard.model.ProjectWizardState;
import de.melinadanhier.projectflow.wizard.service.ProjectWizardService;
import de.melinadanhier.projectflow.wizard.service.AiProjectQuestionCatalog;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ProjectWizardController {

    private final ProjectWizardService wizardService;
    private final ProjectService projectService;
    private final TemplateService templateService;
    private final AiWizardCompletionService aiWizardCompletionService;
    private final AiPreCheckReviewService aiPreCheckReviewService;
    private final AiWorkflowControlService aiWorkflowControlService;

    @GetMapping("/projects/new")
    public String basics(
            @RequestParam(required = false) UUID templateId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model
    ) {
        if (templateId != null) {
            templateService.getTemplate(templateId);
            wizardService.startWithTemplate(templateId, currentUser.userId(), session);
        }
        ProjectBasicsForm form = wizardService.findOwned(currentUser.userId(), session)
                .map(ProjectBasicsForm::from)
                .orElseGet(ProjectBasicsForm::new);
        model.addAttribute("projectBasicsForm", form);
        wizardService.findOwned(currentUser.userId(), session)
                .map(ProjectWizardState::getSelectedTemplateId)
                .filter(java.util.Objects::nonNull)
                .map(templateService::getTemplate)
                .ifPresent(template -> model.addAttribute("selectedTemplate", template));
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
            case TEMPLATE -> state.getSelectedTemplateId() == null
                    ? "redirect:/projects/new/template"
                    : continueWithSelectedTemplate(state, currentUser.userId(), session, redirectAttributes);
            case AI -> "redirect:/projects/new/ai/details";
        };
    }

    @GetMapping("/projects/new/template")
    public String templateCatalog(
            @RequestParam(required = false) ProjectCategory category,
            @RequestParam(required = false) String q,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model
    ) {
        ProjectWizardState state = wizardService.requireOwnedFor(
                CreationType.TEMPLATE, currentUser.userId(), session);
        ProjectCategory selected = category == null ? state.getCategory() : category;
        if (selected == null) {
            selected = ProjectCategory.EDUCATION;
        }
        boolean search = q != null;
        model.addAttribute("wizardState", state);
        model.addAttribute("categories", ProjectCategory.values());
        model.addAttribute("selectedCategory", search ? null : selected);
        model.addAttribute("templates", search ? templateService.search(q) : templateService.getTemplates(selected));
        model.addAttribute("recommendedTemplates", templateService.recommendations(
                state.getCategory(), state.getSubcategory()));
        model.addAttribute("searchPage", search);
        model.addAttribute("query", q == null ? "" : q);
        model.addAttribute("wizardContext", true);
        return "wizard/template-catalog";
    }

    @GetMapping("/projects/new/template/{templateId}")
    public String templatePreview(
            @PathVariable UUID templateId,
            @RequestParam(required = false) ProjectCategory category,
            @RequestParam(required = false) String q,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model
    ) {
        model.addAttribute("wizardState", wizardService.requireOwnedFor(
                CreationType.TEMPLATE, currentUser.userId(), session));
        model.addAttribute("template", templateService.getTemplate(templateId));
        model.addAttribute("wizardContext", true);
        model.addAttribute("backUrl", q == null ? "/projects/new/template"
                + (category == null ? "" : "?category=" + category.name())
                : org.springframework.web.util.UriComponentsBuilder.fromPath("/projects/new/template")
                        .queryParam("q", q).build().encode().toUriString());
        return "wizard/template-preview";
    }

    @PostMapping("/projects/new/template/{templateId}")
    public String useTemplate(
            @PathVariable UUID templateId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        templateService.getTemplate(templateId);
        ProjectWizardState state = wizardService.selectTemplate(templateId, currentUser.userId(), session);
        return continueWithSelectedTemplate(state, currentUser.userId(), session, redirectAttributes);
    }

    @GetMapping("/projects/new/template/confirm")
    public String confirmTemplate(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model
    ) {
        ProjectWizardState state = wizardService.requireOwnedFor(
                CreationType.TEMPLATE, currentUser.userId(), session);
        if (state.getSelectedTemplateId() == null) {
            return "redirect:/projects/new/template";
        }
        if (!dateAssessment(state).requiresConfirmation()) {
            return "redirect:/projects/new/template/" + state.getSelectedTemplateId();
        }
        populateTemplateConfirmation(model, state);
        return "wizard/template-confirm";
    }

    @PostMapping("/projects/new/template/confirm")
    public String materializeTemplate(
            @RequestParam(defaultValue = "false") boolean confirmed,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        ProjectWizardState state = wizardService.requireOwnedFor(
                CreationType.TEMPLATE, currentUser.userId(), session);
        if (state.getSelectedTemplateId() == null) {
            return "redirect:/projects/new/template";
        }
        if (!confirmed) {
            populateTemplateConfirmation(model, state);
            model.addAttribute("confirmationError", "Bitte bestätige die Übernahme der Vorlage.");
            return "wizard/template-confirm";
        }
        var assessment = dateAssessment(state);
        if (!assessment.requiresConfirmation()) {
            return materializeSelectedTemplate(
                    state, currentUser.userId(), session, redirectAttributes, TemplateDateHandling.CONVERT);
        }
        return materializeSelectedTemplate(
                state, currentUser.userId(), session, redirectAttributes, TemplateDateHandling.IGNORE);
    }

    private void populateTemplateConfirmation(Model model, ProjectWizardState state) {
        model.addAttribute("wizardState", state);
        model.addAttribute("template", templateService.getTemplate(state.getSelectedTemplateId()));
        model.addAttribute("dateAssessment", dateAssessment(state));
    }

    private String continueWithSelectedTemplate(
            ProjectWizardState state,
            UUID userId,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        var assessment = dateAssessment(state);
        if (assessment.requiresConfirmation()) {
            return "redirect:/projects/new/template/confirm";
        }
        return materializeSelectedTemplate(
                state, userId, session, redirectAttributes, TemplateDateHandling.CONVERT);
    }

    private String materializeSelectedTemplate(
            ProjectWizardState state,
            UUID userId,
            HttpSession session,
            RedirectAttributes redirectAttributes,
            TemplateDateHandling dateHandling
    ) {
        ProjectDetailsDto project = projectService.createProjectFromTemplate(
                state.getSelectedTemplateId(), wizardService.projectData(userId, session), userId, dateHandling);
        wizardService.clearOwned(userId, session);
        redirectAttributes.addFlashAttribute("successMessage", "Projekt wurde aus der Vorlage angelegt.");
        return "redirect:/projects/" + project.getId() + "/plan";
    }

    private TemplateDateAssessment dateAssessment(ProjectWizardState state) {
        return templateService.assessRelativeDates(
                state.getSelectedTemplateId(), state.getStartDate(), state.getEndDate());
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
        model.addAttribute("questions", AiProjectQuestionCatalog.questionsFor(
                state.getCategory(), state.getSubcategory()));
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
        var questions = AiProjectQuestionCatalog.questionsFor(state.getCategory(), state.getSubcategory());
        var submittedAnswers = form.getAnswers() == null ? java.util.Map.<String, String>of() : form.getAnswers();
        if (hasText(form.getProjectGoal()) || hasText(form.getConstraints())) {
            bindingResult.reject("ai.answers.obsolete",
                    "Die übermittelten Zusatzangaben verwenden nicht den aktuellen Fragenkatalog.");
        }
        if (form.getAvailableWorkingTime() != null && form.getAvailableWorkingTime().length() > 1000) {
            bindingResult.rejectValue("availableWorkingTime", "ai.availableWorkingTime.tooLong",
                    "Die verfügbare Arbeitszeit darf höchstens 1000 Zeichen lang sein.");
        }
        if (AiProjectQuestionCatalog.containsUnknownKey(
                state.getCategory(), state.getSubcategory(), submittedAnswers)) {
            bindingResult.reject("ai.answers.unknown",
                    "Die übermittelten Zusatzangaben passen nicht zur gewählten Projektart.");
        }
        questions.forEach(question -> {
            String value = submittedAnswers.get(question.key());
            if (value != null && value.length() > question.maxLength()) {
                bindingResult.reject("ai.answers.tooLong",
                        "Eine Zusatzangabe ist länger als erlaubt.");
            }
            if (question.required() && (value == null || value.isBlank())) {
                bindingResult.reject("ai.answers.required", "Bitte fülle alle Pflichtangaben aus.");
            }
        });
        if (bindingResult.hasErrors()) {
            model.addAttribute("wizardState", state);
            model.addAttribute("questions", questions);
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

    @PostMapping({"/projects/new/ai/problems/{workflowId}/edit", "/projects/new/ai/status/{workflowId}/edit"})
    public String editAiInputsAfterPreCheck(
            @PathVariable UUID workflowId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session
    ) {
        var snapshot = aiPreCheckReviewService.returnToWizard(workflowId, currentUser.userId());
        // Geänderte Wizard-Daten erhalten bei der nächsten Bestätigung einen neuen,
        // unveränderlichen Workflow statt den vorhandenen Snapshot umzuschreiben.
        wizardService.restoreFromSnapshot(snapshot, currentUser.userId(), session);
        return "redirect:/projects/new/ai/summary";
    }

    @PostMapping("/projects/new/ai/status/{workflowId}/cancel")
    public String cancelAiRun(@PathVariable UUID workflowId,
                              @AuthenticationPrincipal AuthenticatedUser currentUser,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        var cancellation = aiWorkflowControlService.cancel(workflowId, currentUser.userId());
        if (cancellation.operation() == de.melinadanhier.projectflow.ai.model.AiOperation.PRE_CHECK) {
            if (cancellation.snapshot() != null) {
                wizardService.restoreFromSnapshot(cancellation.snapshot(), currentUser.userId(), session);
            }
            redirectAttributes.addFlashAttribute("successMessage",
                    cancellation.changed() ? "Die KI-Vorprüfung wurde abgebrochen."
                            : "Die KI-Vorprüfung war bereits beendet.");
            return cancellation.snapshot() != null
                    ? "redirect:/projects/new/ai/summary"
                    : "redirect:/projects/new/ai/status/" + workflowId;
        }
        redirectAttributes.addFlashAttribute("successMessage",
                cancellation.changed() ? "Die Plangenerierung wurde abgebrochen. Das Ergebnis der Vorprüfung bleibt gültig."
                        : "Die Plangenerierung war bereits beendet.");
        return "redirect:/projects/new/ai/problems/" + workflowId;
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
