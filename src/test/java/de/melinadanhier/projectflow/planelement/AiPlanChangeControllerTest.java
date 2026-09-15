package de.melinadanhier.projectflow.planelement;

import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementPlanContext;
import de.melinadanhier.projectflow.ai.model.planchange.AiPlanChangeResponse;
import de.melinadanhier.projectflow.planelement.controller.AiPlanChangeController;
import de.melinadanhier.projectflow.planelement.dto.planchange.*;
import de.melinadanhier.projectflow.planelement.service.AiPlanChangeService;
import de.melinadanhier.projectflow.planelement.service.PlanChangeNotApplicableException;
import de.melinadanhier.projectflow.feedback.domain.AiFeedbackContext;
import de.melinadanhier.projectflow.feedback.service.AiFeedbackOpportunity;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import de.melinadanhier.projectflow.study.service.StudyTrackingService;
import de.melinadanhier.projectflow.study.domain.StudyEventType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiPlanChangeControllerTest {
    @Test void formChecksEditableAccess() {
        AiPlanChangeService service = mock(AiPlanChangeService.class); var controller = new AiPlanChangeController(service);
        UUID project = UUID.randomUUID(), userId = UUID.randomUUID(); ExtendedModelMap model = new ExtendedModelMap();
        assertThat(controller.form(project, user(userId), model)).isEqualTo("projects/plan-change/form");
        verify(service).requireAccess(project, userId); assertThat(model).containsKey("planChangeForm");
    }
    @Test void proposalIsOnlyStoredInSessionAndReviewLoadsIt() {
        AiPlanChangeService service = mock(AiPlanChangeService.class); var controller = new AiPlanChangeController(service);
        UUID project = UUID.randomUUID(), userId = UUID.randomUUID(); PlanChangeForm form = new PlanChangeForm(); form.setChangeRequest("Ändern");
        PlanChangeProposal proposal = proposal(project); when(service.propose(project, form, userId)).thenReturn(proposal);
        var review = new PlanChangeReview("Kurz", List.of()); when(service.review(proposal)).thenReturn(review); MockHttpSession session = new MockHttpSession();
        String post = controller.propose(project, form, new BeanPropertyBindingResult(form, "planChangeForm"), user(userId), session, new ExtendedModelMap());
        ExtendedModelMap model = new ExtendedModelMap(); String get = controller.review(project, proposal.proposalId(), user(userId), session, model, new MockHttpServletResponse());
        assertThat(post).endsWith(proposal.proposalId().toString()); assertThat(get).isEqualTo("projects/plan-change/review");
        assertThat(model.get("review")).isSameAs(review);
    }
    @Test void discardRemovesOnlyTemporaryProposal() {
        AiPlanChangeService service = mock(AiPlanChangeService.class); var controller = new AiPlanChangeController(service);
        UUID project = UUID.randomUUID(), userId = UUID.randomUUID(); PlanChangeProposal proposal = proposal(project);
        Map<UUID, PlanChangeProposal> values = new LinkedHashMap<>(); values.put(proposal.proposalId(), proposal);
        MockHttpSession session = new MockHttpSession(); session.setAttribute("aiPlanChangeProposals", values);
        assertThat(controller.discard(project, proposal.proposalId(), user(userId), session, new RedirectAttributesModelMap()))
                .isEqualTo("redirect:/projects/" + project + "/plan");
        assertThat(values).isEmpty(); verify(service).requireAccess(project, userId); verifyNoMoreInteractions(service);
        assertThat(session.getAttribute(AiFeedbackOpportunity.SESSION_ATTRIBUTE))
                .isEqualTo(new AiFeedbackOpportunity(
                        AiFeedbackContext.AI_EDIT_REJECTED, proposal.proposalId(),
                        "/projects/" + project + "/plan"));
    }
    @Test void confirmAppliesStoredProposalOnceAndRemovesItFromSession() {
        AiPlanChangeService service = mock(AiPlanChangeService.class); var controller = new AiPlanChangeController(service);
        UUID project = UUID.randomUUID(), userId = UUID.randomUUID(); PlanChangeProposal proposal = proposal(project);
        Map<UUID, PlanChangeProposal> values = new LinkedHashMap<>(); values.put(proposal.proposalId(), proposal);
        MockHttpSession session = new MockHttpSession(); session.setAttribute("aiPlanChangeProposals", values);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        assertThat(controller.confirm(project, proposal.proposalId(), user(userId), session, redirect,
                new ExtendedModelMap(), new MockHttpServletResponse()))
                .isEqualTo("redirect:/projects/" + project + "/plan");

        verify(service).confirm(project, proposal, userId);
        assertThat(values).isEmpty();
        assertThat(redirect.getFlashAttributes().get("successMessage")).asString().isNotBlank();
        assertThat(session.getAttribute(AiFeedbackOpportunity.SESSION_ATTRIBUTE))
                .isEqualTo(new AiFeedbackOpportunity(
                        AiFeedbackContext.AI_EDIT_ADOPTED, proposal.proposalId(),
                        "/projects/" + project + "/plan"));
        ExtendedModelMap conflictModel = new ExtendedModelMap();
        MockHttpServletResponse conflictResponse = new MockHttpServletResponse();
        assertThat(controller.confirm(project, proposal.proposalId(), user(userId), session,
                new RedirectAttributesModelMap(), conflictModel, conflictResponse))
                .isEqualTo("projects/plan-change/conflict");
        assertThat(conflictResponse.getStatus()).isEqualTo(409);
        assertThat(conflictModel.get("errorMessage")).isNotNull();
        assertThat(conflictModel.get("canRegenerate")).isEqualTo(true);
        verify(service, times(1)).confirm(project, proposal, userId);
    }
    @Test void activeStudyDoesNotOfferRegularFeedbackAfterDiscard() {
        AiPlanChangeService service = mock(AiPlanChangeService.class);
        StudyTrackingService studyTrackingService = mock(StudyTrackingService.class);
        var controller = new AiPlanChangeController(service, studyTrackingService);
        UUID project = UUID.randomUUID(), userId = UUID.randomUUID();
        PlanChangeProposal proposal = proposal(project);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("aiPlanChangeProposals",
                new LinkedHashMap<>(Map.of(proposal.proposalId(), proposal)));
        session.setAttribute(AiFeedbackOpportunity.SESSION_ATTRIBUTE,
                new AiFeedbackOpportunity(AiFeedbackContext.AI_EDIT_ADOPTED,
                        UUID.randomUUID(), "/projects"));
        when(studyTrackingService.isActive(session)).thenReturn(true);

        controller.discard(project, proposal.proposalId(), user(userId), session,
                new RedirectAttributesModelMap());

        assertThat(session.getAttribute(AiFeedbackOpportunity.SESSION_ATTRIBUTE)).isNull();
        verify(studyTrackingService).trackIfActive(session, StudyEventType.PLAN_AI_CHANGE_REJECTED);
    }
    @Test void invalidFormNeverCallsAi() {
        AiPlanChangeService service = mock(AiPlanChangeService.class); var controller = new AiPlanChangeController(service);
        UUID project = UUID.randomUUID(), userId = UUID.randomUUID(); PlanChangeForm form = new PlanChangeForm();
        var binding = new BeanPropertyBindingResult(form, "planChangeForm"); binding.rejectValue("changeRequest", "NotBlank");
        assertThat(controller.propose(project, form, binding, user(userId), new MockHttpSession(), new ExtendedModelMap()))
                .isEqualTo("projects/plan-change/form");
        verify(service).requireAccess(project, userId); verify(service, never()).propose(any(), any(), any());
    }
    @Test void optimisticConflictOffersProjectReturnAndSafeRegeneration() {
        AiPlanChangeService service = mock(AiPlanChangeService.class); var controller = new AiPlanChangeController(service);
        UUID project = UUID.randomUUID(), userId = UUID.randomUUID(); PlanChangeProposal proposal = proposal(project);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("aiPlanChangeProposals", new LinkedHashMap<>(Map.of(proposal.proposalId(), proposal)));
        doThrow(new de.melinadanhier.projectflow.common.exception.ConflictException(
                "Der Projektplan wurde seit dem KI-Vorschlag geändert."))
                .when(service).confirm(project, proposal, userId);
        ExtendedModelMap model = new ExtendedModelMap(); MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.confirm(project, proposal.proposalId(), user(userId), session,
                new RedirectAttributesModelMap(), model, response);

        assertThat(view).isEqualTo("projects/plan-change/conflict");
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(model.get("projectId")).isEqualTo(project);
        assertThat(model.get("canRegenerate")).isEqualTo(true);
        assertThat(model.get("errorMessage")).isNotNull();
    }
    @Test void notApplicableIsShownAsBusinessMessageAndNothingIsStored() {
        AiPlanChangeService service = mock(AiPlanChangeService.class); var controller = new AiPlanChangeController(service);
        UUID project = UUID.randomUUID(), userId = UUID.randomUUID(); PlanChangeForm form = new PlanChangeForm();
        form.setChangeRequest("Schreibe einen Aufsatz über Klimawandel");
        when(service.propose(project, form, userId)).thenThrow(
                new PlanChangeNotApplicableException("Der Änderungswunsch passt nicht zum aktuellen Projektplan."));
        MockHttpSession session = new MockHttpSession(); ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.propose(project, form, new BeanPropertyBindingResult(form, "planChangeForm"),
                user(userId), session, model);

        assertThat(view).isEqualTo("projects/plan-change/form");
        assertThat(model.get("errorMessage")).isNotNull();
        assertThat(session.getAttribute("aiPlanChangeProposals")).isNull();
    }
    private AuthenticatedUser user(UUID id) { return new AuthenticatedUser(id, "user@example.org", "hash", true); }
    private PlanChangeProposal proposal(UUID project) { return new PlanChangeProposal(UUID.randomUUID(), project, "Projekt", "Ändern", Instant.now(),
            new AiImprovementPlanContext(List.of()), new AiPlanChangeResponse("Kurz", List.of(), List.of(), List.of()), 0, Map.of(), Map.of()); }
}
