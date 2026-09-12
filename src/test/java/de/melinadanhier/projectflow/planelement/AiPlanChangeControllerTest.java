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
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
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
        assertThat(redirect.getFlashAttributes().get("successMessage")).isEqualTo("Die KI-Änderungen wurden übernommen.");
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
        assertThat(conflictModel.get("errorMessage")).isEqualTo(
                "Der KI-Vorschlag wurde bereits übernommen oder ist nicht mehr verfügbar.");
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
    }
    @Test void invalidFormNeverCallsAi() {
        AiPlanChangeService service = mock(AiPlanChangeService.class); var controller = new AiPlanChangeController(service);
        UUID project = UUID.randomUUID(), userId = UUID.randomUUID(); PlanChangeForm form = new PlanChangeForm();
        var binding = new BeanPropertyBindingResult(form, "planChangeForm"); binding.rejectValue("changeRequest", "NotBlank");
        assertThat(controller.propose(project, form, binding, user(userId), new MockHttpSession(), new ExtendedModelMap()))
                .isEqualTo("projects/plan-change/form");
        verify(service).requireAccess(project, userId); verify(service, never()).propose(any(), any(), any());
    }
    @Test void regenerateImmediatelyUsesSameRequestAndReplacesSessionProposal() {
        AiPlanChangeService service = mock(AiPlanChangeService.class); var controller = new AiPlanChangeController(service);
        UUID project = UUID.randomUUID(), userId = UUID.randomUUID(); PlanChangeProposal previous = proposal(project);
        PlanChangeProposal regenerated = new PlanChangeProposal(UUID.randomUUID(), project, "Projekt",
                previous.changeRequest(), Instant.now(), previous.originalPlan(), previous.changes(), 0, Map.of(), Map.of());
        Map<UUID, PlanChangeProposal> values = new LinkedHashMap<>(); values.put(previous.proposalId(), previous);
        MockHttpSession session = new MockHttpSession(); session.setAttribute("aiPlanChangeProposals", values);
        when(service.propose(eq(project), any(PlanChangeForm.class), eq(userId))).thenReturn(regenerated);

        String view = controller.regenerate(project, previous.proposalId(), user(userId), session,
                new RedirectAttributesModelMap(), new ExtendedModelMap(), new MockHttpServletResponse());

        assertThat(view).endsWith(regenerated.proposalId().toString());
        assertThat(values).containsOnlyKeys(regenerated.proposalId());
        var formCaptor = org.mockito.ArgumentCaptor.forClass(PlanChangeForm.class);
        verify(service).propose(eq(project), formCaptor.capture(), eq(userId));
        assertThat(formCaptor.getValue().getChangeRequest()).isEqualTo(previous.changeRequest());
    }
    @Test void regenerateAfterConsumptionUsesOnlyRememberedRequestAndCreatesNewProposal() {
        AiPlanChangeService service = mock(AiPlanChangeService.class); var controller = new AiPlanChangeController(service);
        UUID project = UUID.randomUUID(), userId = UUID.randomUUID(); PlanChangeProposal previous = proposal(project);
        PlanChangeProposal regenerated = new PlanChangeProposal(UUID.randomUUID(), project, "Projekt",
                previous.changeRequest(), Instant.now(), previous.originalPlan(), previous.changes(), 0, Map.of(), Map.of());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("aiPlanChangeLastRequests", new LinkedHashMap<>(Map.of(project, previous.changeRequest())));
        when(service.propose(eq(project), any(PlanChangeForm.class), eq(userId))).thenReturn(regenerated);

        String view = controller.regenerateLast(project, user(userId), session, new RedirectAttributesModelMap());

        assertThat(view).endsWith(regenerated.proposalId().toString());
        Map<?, ?> stored = (Map<?, ?>) session.getAttribute("aiPlanChangeProposals");
        assertThat(stored).hasSize(1);
        assertThat(stored.containsKey(regenerated.proposalId())).isTrue();
        verify(service, never()).confirm(any(), any(), any());
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
        assertThat(model.get("errorMessage")).isEqualTo("Der Projektplan wurde seit dem KI-Vorschlag geändert.");
    }
    @Test void proposalThatBecameInvalidUsesProjectSpecificConflictPage() {
        AiPlanChangeService service = mock(AiPlanChangeService.class); var controller = new AiPlanChangeController(service);
        UUID project = UUID.randomUUID(), userId = UUID.randomUUID(); PlanChangeProposal proposal = proposal(project);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("aiPlanChangeProposals", new LinkedHashMap<>(Map.of(proposal.proposalId(), proposal)));
        doThrow(new de.melinadanhier.projectflow.common.exception.DomainValidationException(
                "Der KI-Änderungsvorschlag ist nicht mehr gültig."))
                .when(service).confirm(project, proposal, userId);
        ExtendedModelMap model = new ExtendedModelMap(); MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(controller.confirm(project, proposal.proposalId(), user(userId), session,
                new RedirectAttributesModelMap(), model, response))
                .isEqualTo("projects/plan-change/conflict");
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(model.get("errorMessage")).isEqualTo("Der KI-Änderungsvorschlag ist nicht mehr gültig.");
    }
    @Test void proposalConflictTemplateHasProjectActionsAndNoUselessReload() throws Exception {
        String html = Files.readString(Path.of(
                "src/main/resources/templates/projects/plan-change/conflict.html"));

        assertThat(html)
                .contains("Zurück zum Projekt")
                .contains("Mit gleichem Wunsch neu generieren")
                .contains("/projects/{id}/plan")
                .doesNotContain("window.location.reload")
                .doesNotContain("Zur Projektübersicht");
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
        assertThat(model.get("errorMessage")).isEqualTo("Der Änderungswunsch passt nicht zum aktuellen Projektplan.");
        assertThat(session.getAttribute("aiPlanChangeProposals")).isNull();
    }
    private AuthenticatedUser user(UUID id) { return new AuthenticatedUser(id, "user@example.org", "hash", true); }
    private PlanChangeProposal proposal(UUID project) { return new PlanChangeProposal(UUID.randomUUID(), project, "Projekt", "Ändern", Instant.now(),
            new AiImprovementPlanContext(List.of()), new AiPlanChangeResponse("Kurz", List.of(), List.of(), List.of()), 0, Map.of(), Map.of()); }
}
