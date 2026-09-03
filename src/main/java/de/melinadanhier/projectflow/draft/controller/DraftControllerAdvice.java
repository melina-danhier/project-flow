package de.melinadanhier.projectflow.draft.controller;

import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.draft.service.DraftApplicationPersistenceException;
import de.melinadanhier.projectflow.draft.service.DraftReviewService;
import de.melinadanhier.projectflow.draft.service.DraftVersionConflictException;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@ControllerAdvice(assignableTypes = {
        DraftReviewController.class,
        DraftEditingController.class,
        DraftApplicationController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DraftControllerAdvice {

    private final DraftReviewService draftReviewService;

    @ExceptionHandler(DraftVersionConflictException.class)
    public String handleVersionConflict(DraftVersionConflictException exception,
                                        @AuthenticationPrincipal AuthenticatedUser currentUser,
                                        HttpServletRequest request,
                                        HttpServletResponse response,
                                        Model model) throws IOException {
        UUID projectId = projectId(request);
        if (!exception.isReviewAvailable()) {
            response.sendError(HttpServletResponse.SC_CONFLICT, exception.getMessage());
            return null;
        }
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        model.addAttribute("errorMessage", exception.getMessage());
        model.addAttribute("draft", draftReviewService.review(projectId, currentUser.userId(), null));
        return "generation/draft-review";
    }

    @ExceptionHandler(DraftApplicationPersistenceException.class)
    public String handlePersistenceFailure(RedirectAttributes attributes,
                                           HttpServletRequest request) {
        attributes.addFlashAttribute("errorMessage",
                "Die Übernahme konnte nicht gespeichert werden. " +
                        "Der Entwurf blieb unverändert und kann erneut übernommen werden.");
        return reviewRedirect(projectId(request));
    }

    @ExceptionHandler({DomainValidationException.class, ConflictException.class})
    public String handleInvalidDraft(RuntimeException exception,
                                     RedirectAttributes attributes,
                                     HttpServletRequest request) {
        attributes.addFlashAttribute("errorMessage", exception.getMessage());
        return reviewRedirect(projectId(request));
    }

    private String reviewRedirect(UUID projectId) {
        return "redirect:/projects/" + projectId + "/draft/review";
    }

    @SuppressWarnings("unchecked")
    private UUID projectId(HttpServletRequest request) {
        var variables = (Map<String, String>) request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        if (variables == null || !variables.containsKey("projectId")) {
            throw new IllegalStateException("Request enthält keine projectId.");
        }

        return UUID.fromString(variables.get("projectId"));
    }

}
