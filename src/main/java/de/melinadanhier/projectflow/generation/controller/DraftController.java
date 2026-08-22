package de.melinadanhier.projectflow.generation.controller;

import de.melinadanhier.projectflow.generation.service.DraftService;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class DraftController {

    private final DraftService draftService;

    @GetMapping("/projects/{projectId}/draft")
    public String review(@PathVariable UUID projectId,
                         @AuthenticationPrincipal AuthenticatedUser currentUser,
                         Model model) {
        model.addAttribute("draft", draftService.review(projectId, currentUser.userId()));
        return "generation/draft-review";
    }

    @PostMapping("/projects/{projectId}/draft/apply")
    public String apply(@PathVariable UUID projectId,
                        @AuthenticationPrincipal AuthenticatedUser currentUser,
                        RedirectAttributes redirectAttributes) {
        draftService.apply(projectId, currentUser.userId());
        redirectAttributes.addFlashAttribute("successMessage", "Der KI-Entwurf wurde übernommen.");
        return "redirect:/projects/" + projectId + "/plan";
    }
}
