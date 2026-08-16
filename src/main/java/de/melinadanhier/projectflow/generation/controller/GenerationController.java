package de.melinadanhier.projectflow.generation.controller;

import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectBasicsForm;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectCreationFlowState;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectCreationFlowService;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.validation.BindingResult;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class GenerationController {

    private final ProjectCreationFlowService creationFlowService;

    @GetMapping("/projects/new/ai")
    public String aiWizard(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model
    ) {
        ProjectCreationFlowState state = requireAiFlow(currentUser.userId(), session);
        model.addAttribute("creationFlow", state);
        model.addAttribute("projectBasicsForm", ProjectBasicsForm.from(state));
        return "generation/ai-wizard";
    }

    @PostMapping("/projects/new/ai")
    public String saveProjectBasics(
            @Valid @ModelAttribute("projectBasicsForm") ProjectBasicsForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model
    ) {
        ProjectCreationFlowState state = requireAiFlow(currentUser.userId(), session);
        if (bindingResult.hasErrors()) {
            model.addAttribute("creationFlow", state);
            return "generation/ai-wizard";
        }
        creationFlowService.updateBasics(form, currentUser.userId(), session);
        return "redirect:/projects/new/ai/details";
    }

    @GetMapping("/projects/new/ai/details")
    public String aiDetails(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model
    ) {
        model.addAttribute("creationFlow", requireAiFlow(currentUser.userId(), session));
        return "generation/ai-details";
    }

    private ProjectCreationFlowState requireAiFlow(UUID userId, HttpSession session) {
        ProjectCreationFlowState state = creationFlowService.requireOwned(userId, session);
        if (state.getCreationType() != CreationType.AI) {
            throw new ResourceNotFoundException("KI-Erstellungsablauf wurde nicht gefunden.");
        }
        return state;
    }
}
