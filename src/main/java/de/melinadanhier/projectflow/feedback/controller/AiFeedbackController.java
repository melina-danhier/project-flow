package de.melinadanhier.projectflow.feedback.controller;

import de.melinadanhier.projectflow.feedback.dto.AiFeedbackForm;
import de.melinadanhier.projectflow.feedback.service.*;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/ai-feedback")
public class AiFeedbackController {
    private final AiFeedbackService feedbackService;

    @GetMapping
    public String form(HttpSession session) {
        AiFeedbackOpportunity opportunity = opportunity(session);
        if (opportunity == null) return "redirect:/projects";
        return "redirect:" + opportunity.returnUrl();
    }

    @PostMapping
    public String submit(@Valid @ModelAttribute AiFeedbackForm aiFeedbackForm,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal AuthenticatedUser currentUser,
                         HttpSession session, RedirectAttributes redirect) {
        AiFeedbackOpportunity opportunity = opportunity(session);
        if (opportunity == null) return "redirect:/projects";
        if (bindingResult.hasErrors()) {
            redirect.addFlashAttribute("errorMessage", "Bitte wähle eine Bewertung aus.");
            return "redirect:" + opportunity.returnUrl();
        }
        feedbackService.saveIfAbsent(currentUser.userId(), opportunity, aiFeedbackForm);
        session.removeAttribute(AiFeedbackOpportunity.SESSION_ATTRIBUTE);
        redirect.addFlashAttribute("successMessage", "Danke für dein Feedback.");
        return "redirect:" + opportunity.returnUrl();
    }

    @PostMapping("/skip")
    public String skip(HttpSession session) {
        AiFeedbackOpportunity opportunity = opportunity(session);
        session.removeAttribute(AiFeedbackOpportunity.SESSION_ATTRIBUTE);
        return "redirect:" + (opportunity == null ? "/projects" : opportunity.returnUrl());
    }

    private AiFeedbackOpportunity opportunity(HttpSession session) {
        Object value = session.getAttribute(AiFeedbackOpportunity.SESSION_ATTRIBUTE);
        return value instanceof AiFeedbackOpportunity opportunity ? opportunity : null;
    }
}
