package de.melinadanhier.projectflow.generation.controller;

import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectCreationFlowState;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectCreationFlowService;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

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
        ProjectCreationFlowState state = creationFlowService.requireOwned(currentUser.userId(), session);
        if (state.getCreationType() != CreationType.AI) {
            throw new ResourceNotFoundException("KI-Erstellungsablauf wurde nicht gefunden.");
        }
        model.addAttribute("creationFlow", state);
        return "generation/ai-wizard";
    }
}
