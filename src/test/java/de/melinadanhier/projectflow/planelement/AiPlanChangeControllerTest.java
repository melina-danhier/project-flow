package de.melinadanhier.projectflow.planelement;

import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementPlanContext;
import de.melinadanhier.projectflow.ai.model.planchange.AiPlanChangeResponse;
import de.melinadanhier.projectflow.planelement.controller.AiPlanChangeController;
import de.melinadanhier.projectflow.planelement.dto.planchange.*;
import de.melinadanhier.projectflow.planelement.service.AiPlanChangeService;
import de.melinadanhier.projectflow.planelement.service.PlanChangeNotApplicableException;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
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
        ExtendedModelMap model = new ExtendedModelMap(); String get = controller.review(project, proposal.proposalId(), user(userId), session, model);
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
                previous.changeRequest(), Instant.now(), previous.originalPlan(), previous.changes());
        Map<UUID, PlanChangeProposal> values = new LinkedHashMap<>(); values.put(previous.proposalId(), previous);
        MockHttpSession session = new MockHttpSession(); session.setAttribute("aiPlanChangeProposals", values);
        when(service.propose(eq(project), any(PlanChangeForm.class), eq(userId))).thenReturn(regenerated);

        String view = controller.regenerate(project, previous.proposalId(), user(userId), session,
                new RedirectAttributesModelMap());

        assertThat(view).endsWith(regenerated.proposalId().toString());
        assertThat(values).containsOnlyKeys(regenerated.proposalId());
        var formCaptor = org.mockito.ArgumentCaptor.forClass(PlanChangeForm.class);
        verify(service).propose(eq(project), formCaptor.capture(), eq(userId));
        assertThat(formCaptor.getValue().getChangeRequest()).isEqualTo(previous.changeRequest());
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
            new AiImprovementPlanContext(List.of()), new AiPlanChangeResponse("Kurz", List.of(), List.of(), List.of())); }
}
