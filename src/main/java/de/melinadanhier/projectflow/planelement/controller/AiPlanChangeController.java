package de.melinadanhier.projectflow.planelement.controller;

import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.planelement.dto.planchange.PlanChangeForm;
import de.melinadanhier.projectflow.planelement.dto.planchange.PlanChangeProposal;
import de.melinadanhier.projectflow.planelement.service.AiPlanChangeService;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import de.melinadanhier.projectflow.feedback.domain.AiFeedbackContext;
import de.melinadanhier.projectflow.feedback.service.AiFeedbackOpportunity;
import de.melinadanhier.projectflow.study.domain.StudyEventType;
import de.melinadanhier.projectflow.study.service.StudyTrackingService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;

@Controller @Slf4j
public class AiPlanChangeController {
    static final String SESSION_PROPOSALS = "aiPlanChangeProposals";
    static final String SESSION_LAST_REQUESTS = "aiPlanChangeLastRequests";
    private static final int MAX_SESSION_PROPOSALS = 5;
    private final AiPlanChangeService service;
    private final StudyTrackingService studyTrackingService;

    @org.springframework.beans.factory.annotation.Autowired
    public AiPlanChangeController(AiPlanChangeService service, StudyTrackingService studyTrackingService) {
        this.service = service;
        this.studyTrackingService = studyTrackingService;
    }

    public AiPlanChangeController(AiPlanChangeService service) {
        this(service, null);
    }

    @GetMapping("/projects/{projectId}/plan/ai-change")
    public String form(@PathVariable UUID projectId, @AuthenticationPrincipal AuthenticatedUser user, Model model) {
        service.requireAccess(projectId, user.userId());
        if (!model.containsAttribute("planChangeForm")) model.addAttribute("planChangeForm", new PlanChangeForm());
        model.addAttribute("projectId", projectId); return "projects/plan-change/form";
    }
    @PostMapping("/projects/{projectId}/plan/ai-change")
    public String propose(@PathVariable UUID projectId, @Valid @ModelAttribute PlanChangeForm planChangeForm,
                          BindingResult binding, @AuthenticationPrincipal AuthenticatedUser user,
                          HttpSession session, Model model) {
        if (binding.hasErrors()) { service.requireAccess(projectId, user.userId()); model.addAttribute("projectId", projectId); return "projects/plan-change/form"; }
        try {
            PlanChangeProposal proposal = service.propose(projectId, planChangeForm, user.userId()); store(session, proposal);
            track(session, StudyEventType.AI_EDIT_STARTED);
            return "redirect:/projects/" + projectId + "/plan/ai-change/" + proposal.proposalId();
        } catch (de.melinadanhier.projectflow.planelement.service.PlanChangeNotApplicableException exception) {
            model.addAttribute("projectId", projectId);
            model.addAttribute("errorMessage", exception.getMessage());
            return "projects/plan-change/form";
        } catch (AiTechnicalException exception) {
            logValidationFailure(projectId, exception);
            model.addAttribute("projectId", projectId); model.addAttribute("errorMessage", "Der KI-Änderungsvorschlag konnte nicht erstellt werden. Bitte versuche es erneut.");
            return "projects/plan-change/form";
        }
    }
    @GetMapping("/projects/{projectId}/plan/ai-change/{proposalId}")
    public String review(@PathVariable UUID projectId, @PathVariable UUID proposalId,
                         @AuthenticationPrincipal AuthenticatedUser user, HttpSession session, Model model,
                         HttpServletResponse response) {
        service.requireAccess(projectId, user.userId());
        PlanChangeProposal proposal = proposals(session).get(proposalId);
        if (proposal == null || !proposal.projectId().equals(projectId))
            return unavailable(projectId, session, model, response);
        model.addAttribute("proposal", proposal); model.addAttribute("review", service.review(proposal)); return "projects/plan-change/review";
    }
    @PostMapping("/projects/{projectId}/plan/ai-change/{proposalId}/discard")
    public String discard(@PathVariable UUID projectId, @PathVariable UUID proposalId,
                          @AuthenticationPrincipal AuthenticatedUser user, HttpSession session, RedirectAttributes redirect) {
        service.requireAccess(projectId, user.userId()); require(session, projectId, proposalId); proposals(session).remove(proposalId);
        track(session, StudyEventType.AI_EDIT_REJECTED);
        offerFeedback(session, AiFeedbackContext.AI_EDIT_REJECTED, proposalId, projectId);
        redirect.addFlashAttribute("successMessage", "Der KI-Änderungsvorschlag wurde verworfen."); return "redirect:/projects/" + projectId + "/plan";
    }
    @PostMapping("/projects/{projectId}/plan/ai-change/{proposalId}/confirm")
    public String confirm(@PathVariable UUID projectId, @PathVariable UUID proposalId,
                          @AuthenticationPrincipal AuthenticatedUser user, HttpSession session,
                          RedirectAttributes redirect, Model model, HttpServletResponse response) {
        service.requireAccess(projectId, user.userId());
        PlanChangeProposal proposal = proposals(session).get(proposalId);
        if (proposal == null || !proposal.projectId().equals(projectId))
            return unavailable(projectId, session, model, response);
        rememberRequest(session, proposal);
        try {
            service.confirm(projectId, proposal, user.userId());
        } catch (ConflictException | ResourceNotFoundException | DomainValidationException exception) {
            return conflict(projectId, exception.getMessage(), session, model, response);
        }
        proposals(session).remove(proposalId);
        track(session, StudyEventType.AI_EDIT_ADOPTED);
        offerFeedback(session, AiFeedbackContext.AI_EDIT_ADOPTED, proposalId, projectId);
        redirect.addFlashAttribute("successMessage", "Die KI-Änderungen wurden übernommen.");
        return "redirect:/projects/" + projectId + "/plan";
    }
    @PostMapping("/projects/{projectId}/plan/ai-change/{proposalId}/regenerate")
    public String regenerate(@PathVariable UUID projectId, @PathVariable UUID proposalId,
                             @AuthenticationPrincipal AuthenticatedUser user, HttpSession session,
                             RedirectAttributes redirect, Model model, HttpServletResponse response) {
        service.requireAccess(projectId, user.userId());
        PlanChangeProposal previous = proposals(session).get(proposalId);
        if (previous == null || !previous.projectId().equals(projectId))
            return unavailable(projectId, session, model, response);
        PlanChangeForm form = new PlanChangeForm(); form.setChangeRequest(previous.changeRequest());
        try {
            PlanChangeProposal regenerated = service.propose(projectId, form, user.userId());
            proposals(session).remove(proposalId); store(session, regenerated);
            return "redirect:/projects/" + projectId + "/plan/ai-change/" + regenerated.proposalId();
        } catch (de.melinadanhier.projectflow.planelement.service.PlanChangeNotApplicableException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/projects/" + projectId + "/plan/ai-change/" + proposalId;
        } catch (AiTechnicalException exception) {
            logValidationFailure(projectId, exception);
            redirect.addFlashAttribute("errorMessage",
                    "Der KI-Änderungsvorschlag konnte nicht neu erzeugt werden. Bitte versuche es erneut.");
            return "redirect:/projects/" + projectId + "/plan/ai-change/" + proposalId;
        }
    }

    @PostMapping("/projects/{projectId}/plan/ai-change/regenerate-last")
    public String regenerateLast(@PathVariable UUID projectId, @AuthenticationPrincipal AuthenticatedUser user,
                                 HttpSession session, RedirectAttributes redirect) {
        service.requireAccess(projectId, user.userId());
        String request = lastRequests(session).get(projectId);
        if (request == null || request.isBlank()) {
            redirect.addFlashAttribute("errorMessage", "Der ursprüngliche Änderungswunsch ist nicht mehr verfügbar.");
            return "redirect:/projects/" + projectId + "/plan";
        }
        PlanChangeForm form = new PlanChangeForm();
        form.setChangeRequest(request);
        try {
            PlanChangeProposal regenerated = service.propose(projectId, form, user.userId());
            store(session, regenerated);
            return "redirect:/projects/" + projectId + "/plan/ai-change/" + regenerated.proposalId();
        } catch (de.melinadanhier.projectflow.planelement.service.PlanChangeNotApplicableException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
        } catch (AiTechnicalException exception) {
            logValidationFailure(projectId, exception);
            redirect.addFlashAttribute("errorMessage", "Der KI-Änderungsvorschlag konnte nicht neu erzeugt werden.");
        }
        return "redirect:/projects/" + projectId + "/plan";
    }

    private void store(HttpSession session, PlanChangeProposal proposal) { Map<UUID, PlanChangeProposal> values = proposals(session); values.put(proposal.proposalId(), proposal); rememberRequest(session, proposal); while (values.size() > MAX_SESSION_PROPOSALS) values.remove(values.keySet().iterator().next()); }
    private PlanChangeProposal require(HttpSession session, UUID projectId, UUID proposalId) { PlanChangeProposal result = proposals(session).get(proposalId); if (result == null || !result.projectId().equals(projectId)) throw new ConflictException("Der temporäre KI-Vorschlag ist nicht mehr verfügbar."); return result; }
    @SuppressWarnings("unchecked") private Map<UUID, PlanChangeProposal> proposals(HttpSession session) { Object current = session.getAttribute(SESSION_PROPOSALS); if (current instanceof Map<?, ?>) return (Map<UUID, PlanChangeProposal>) current; Map<UUID, PlanChangeProposal> created = new LinkedHashMap<>(); session.setAttribute(SESSION_PROPOSALS, created); return created; }
    @SuppressWarnings("unchecked") private Map<UUID, String> lastRequests(HttpSession session) { Object current = session.getAttribute(SESSION_LAST_REQUESTS); if (current instanceof Map<?, ?>) return (Map<UUID, String>) current; Map<UUID, String> created = new LinkedHashMap<>(); session.setAttribute(SESSION_LAST_REQUESTS, created); return created; }
    private void rememberRequest(HttpSession session, PlanChangeProposal proposal) { Map<UUID, String> values = lastRequests(session); values.put(proposal.projectId(), proposal.changeRequest()); while (values.size() > MAX_SESSION_PROPOSALS) values.remove(values.keySet().iterator().next()); }
    private String unavailable(UUID projectId, HttpSession session, Model model, HttpServletResponse response) {
        return conflict(projectId, "Der KI-Vorschlag wurde bereits übernommen oder ist nicht mehr verfügbar.", session, model, response);
    }
    private String conflict(UUID projectId, String message, HttpSession session, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        model.addAttribute("projectId", projectId);
        model.addAttribute("errorMessage", message);
        model.addAttribute("canRegenerate", lastRequests(session).containsKey(projectId));
        return "projects/plan-change/conflict";
    }

    private void logValidationFailure(UUID projectId, AiTechnicalException exception) {
        if (exception instanceof AiOutputValidationException validationException) {
            log.warn("KI-Planänderung abgelehnt projectId={} errorCode={} reason={} validationIssues={}",
                    projectId, exception.getErrorCode(), exception.getMessage(), validationException.getValidationIssues());
        } else {
            log.warn("KI-Planänderung fehlgeschlagen projectId={} errorCode={} reason={}",
                    projectId, exception.getErrorCode(), exception.getMessage());
        }
        log.debug("Technische Details zur fehlgeschlagenen KI-Planänderung projectId={}", projectId, exception);
    }

    private void offerFeedback(HttpSession session, AiFeedbackContext context, UUID actionId, UUID projectId) {
        session.setAttribute(AiFeedbackOpportunity.SESSION_ATTRIBUTE,
                new AiFeedbackOpportunity(context, actionId, "/projects/" + projectId + "/plan"));
    }

    private void track(HttpSession session, StudyEventType type) {
        if (studyTrackingService != null) studyTrackingService.trackIfActive(session, type);
    }
}
