package de.melinadanhier.projectflow.planelement.controller;

import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.improvement.AiFeedbackType;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementElementType;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.planelement.dto.improvement.AiImprovementForm;
import de.melinadanhier.projectflow.planelement.dto.improvement.AiImprovementProposal;
import de.melinadanhier.projectflow.planelement.dto.improvement.AiImprovementReview;
import de.melinadanhier.projectflow.planelement.service.AiElementImprovementService;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import de.melinadanhier.projectflow.feedback.domain.AiFeedbackContext;
import de.melinadanhier.projectflow.feedback.service.AiFeedbackOpportunity;
import de.melinadanhier.projectflow.study.domain.StudyEventType;
import de.melinadanhier.projectflow.study.service.StudyTrackingService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Arrays;

@Controller
@Slf4j
public class AiElementImprovementController {

    private static final String SESSION_PROPOSALS = "aiElementImprovementProposals";
    private static final int MAX_SESSION_PROPOSALS = 5;
    private final AiElementImprovementService improvementService;
    private final StudyTrackingService studyTrackingService;

    @org.springframework.beans.factory.annotation.Autowired
    public AiElementImprovementController(AiElementImprovementService improvementService,
                                          StudyTrackingService studyTrackingService) {
        this.improvementService = improvementService;
        this.studyTrackingService = studyTrackingService;
    }

    public AiElementImprovementController(AiElementImprovementService improvementService) {
        this(improvementService, null);
    }

    @GetMapping("/projects/{projectId}/plan-elements/{elementType}/{elementId}/improve")
    public String form(
            @PathVariable UUID projectId,
            @PathVariable AiImprovementElementType elementType,
            @PathVariable UUID elementId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            Model model
    ) {
        improvementService.requireImprovementAccess(projectId, elementType, elementId, currentUser.userId());
        model.addAttribute("improvementForm", new AiImprovementForm());
        populate(model, projectId, elementType, elementId);
        return "projects/improvement/form";
    }

    @PostMapping("/projects/{projectId}/plan-elements/{elementType}/{elementId}/improve")
    public String propose(
            @PathVariable UUID projectId,
            @PathVariable AiImprovementElementType elementType,
            @PathVariable UUID elementId,
            @Valid @ModelAttribute("improvementForm") AiImprovementForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            improvementService.requireImprovementAccess(projectId, currentUser.userId());
            populate(model, projectId, elementType, elementId);
            return "projects/improvement/form";
        }
        try {
            AiImprovementProposal proposal = improvementService.propose(
                    projectId, elementType, elementId, form, currentUser.userId());
            storeProposal(session, proposal);
            track(session, StudyEventType.AI_EDIT_STARTED);
            return reviewRedirect(proposal);
        } catch (AiTechnicalException exception) {
            if (exception instanceof AiOutputValidationException validationException) {
                log.warn("KI-Vorschlag abgelehnt projectId={} elementType={} elementId={} action={} "
                                + "errorCode={} reason={} validationIssues={}",
                        projectId, elementType, elementId, form.getFeedbackType(), exception.getErrorCode(),
                        exception.getMessage(), validationException.getValidationIssues());
            } else {
                log.warn("KI-Vorschlag fehlgeschlagen projectId={} elementType={} elementId={} action={} "
                                + "errorCode={} reason={}",
                        projectId, elementType, elementId, form.getFeedbackType(), exception.getErrorCode(),
                        exception.getMessage());
            }
            log.debug("Technische Details zum fehlgeschlagenen KI-Vorschlag", exception);
            model.addAttribute("errorMessage",
                    "Der KI-Vorschlag konnte nicht erstellt werden. Bitte versuche es später erneut.");
            populate(model, projectId, elementType, elementId);
            return "projects/improvement/form";
        }
    }

    @GetMapping("/projects/{projectId}/ai-improvements/{proposalId}")
    public String review(
            @PathVariable UUID projectId,
            @PathVariable UUID proposalId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            Model model
    ) {
        AiImprovementProposal proposal = requireProposal(session, projectId, proposalId);
        improvementService.requireImprovementAccess(projectId, currentUser.userId());
        model.addAttribute("proposal", proposal);
        model.addAttribute("review", AiImprovementReview.from(proposal));
        return "projects/improvement/review";
    }

    @PostMapping("/projects/{projectId}/ai-improvements/{proposalId}/confirm")
    public String confirm(
            @PathVariable UUID projectId,
            @PathVariable UUID proposalId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AiImprovementProposal proposal = requireProposal(session, projectId, proposalId);
        improvementService.confirm(proposal, currentUser.userId());
        proposals(session).remove(proposalId);
        track(session, StudyEventType.AI_EDIT_ADOPTED);
        offerFeedback(session, AiFeedbackContext.AI_EDIT_ADOPTED, proposalId, proposal);
        redirectAttributes.addFlashAttribute("successMessage", "Der KI-Vorschlag wurde übernommen.");
        return redirect(proposal);
    }

    @PostMapping("/projects/{projectId}/ai-improvements/{proposalId}/discard")
    public String discard(
            @PathVariable UUID projectId,
            @PathVariable UUID proposalId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AiImprovementProposal proposal = requireProposal(session, projectId, proposalId);
        improvementService.requireImprovementAccess(projectId, currentUser.userId());
        proposals(session).remove(proposalId);
        track(session, StudyEventType.AI_EDIT_REJECTED);
        offerFeedback(session, AiFeedbackContext.AI_EDIT_REJECTED, proposalId, proposal);
        redirectAttributes.addFlashAttribute("successMessage", "Der KI-Vorschlag wurde verworfen.");
        return redirect(proposal);
    }

    private void populate(Model model, UUID projectId, AiImprovementElementType type, UUID elementId) {
        model.addAttribute("projectId", projectId);
        model.addAttribute("elementId", elementId);
        model.addAttribute("elementType", type);
        model.addAttribute("feedbackTypes", Arrays.stream(AiFeedbackType.values())
                .filter(feedbackType -> feedbackType.supports(type))
                .toList());
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, AiImprovementProposal> proposals(HttpSession session) {
        Object current = session.getAttribute(SESSION_PROPOSALS);
        if (current instanceof Map<?, ?>) {
            return (Map<UUID, AiImprovementProposal>) current;
        }
        Map<UUID, AiImprovementProposal> proposals = new LinkedHashMap<>();
        session.setAttribute(SESSION_PROPOSALS, proposals);
        return proposals;
    }

    private AiImprovementProposal requireProposal(HttpSession session, UUID projectId, UUID proposalId) {
        AiImprovementProposal proposal = proposals(session).get(proposalId);
        if (proposal == null || !proposal.projectId().equals(projectId)) {
            throw new ConflictException("Der temporäre KI-Vorschlag ist nicht mehr verfügbar.");
        }
        return proposal;
    }

    private void storeProposal(HttpSession session, AiImprovementProposal proposal) {
        Map<UUID, AiImprovementProposal> stored = proposals(session);
        stored.put(proposal.proposalId(), proposal);
        while (stored.size() > MAX_SESSION_PROPOSALS) {
            stored.remove(stored.keySet().iterator().next());
        }
    }

    private String redirect(AiImprovementProposal proposal) {
        if (proposal.elementType() == AiImprovementElementType.TASK) {
            return "redirect:/projects/" + proposal.projectId() + "/tasks/" + proposal.elementId();
        }
        return "redirect:/projects/" + proposal.projectId() + "/plan";
    }

    private String reviewRedirect(AiImprovementProposal proposal) {
        return "redirect:/projects/" + proposal.projectId()
                + "/ai-improvements/" + proposal.proposalId();
    }

    private void offerFeedback(HttpSession session, AiFeedbackContext context, UUID actionId,
                               AiImprovementProposal proposal) {
        String returnUrl = proposal.elementType() == AiImprovementElementType.TASK
                ? "/projects/" + proposal.projectId() + "/tasks/" + proposal.elementId()
                : "/projects/" + proposal.projectId() + "/plan";
        session.setAttribute(AiFeedbackOpportunity.SESSION_ATTRIBUTE,
                new AiFeedbackOpportunity(context, actionId, returnUrl));
    }

    private void track(HttpSession session, StudyEventType type) {
        if (studyTrackingService != null) studyTrackingService.trackIfActive(session, type);
    }
}
