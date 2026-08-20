package de.melinadanhier.projectflow.generation.controller;

import de.melinadanhier.projectflow.generation.dto.response.AiWorkflowStatusDto;
import de.melinadanhier.projectflow.generation.service.AiWorkflowQueryService;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class AiWorkflowController {

    private final AiWorkflowQueryService workflowQueryService;

    @GetMapping("/projects/new/ai/status/{workflowId}")
    public String status(
            @PathVariable UUID workflowId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model
    ) {
        AiWorkflowStatusDto workflow = workflowQueryService.getOwnedStatus(
                workflowId, currentUser.userId());
        model.addAttribute("workflow", workflow);
        return "generation/ai-status";
    }
}
