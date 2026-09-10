package de.melinadanhier.projectflow.planelement;

import de.melinadanhier.projectflow.ai.model.improvement.AiFeedbackType;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementElementType;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementContent;
import de.melinadanhier.projectflow.planelement.controller.AiElementImprovementController;
import de.melinadanhier.projectflow.planelement.dto.improvement.AiImprovementForm;
import de.melinadanhier.projectflow.planelement.dto.improvement.AiImprovementProposal;
import de.melinadanhier.projectflow.planelement.service.AiElementImprovementService;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiElementImprovementControllerTest {

    @Test
    @SuppressWarnings("unchecked")
    void formOffersOnlyActionsSupportedByElementType() {
        AiElementImprovementService service = mock(AiElementImprovementService.class);
        AiElementImprovementController controller = new AiElementImprovementController(service);
        UUID projectId = UUID.randomUUID();
        UUID elementId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.org", "hash", true);

        ExtendedModelMap sectionModel = new ExtendedModelMap();
        controller.form(projectId, AiImprovementElementType.SECTION, elementId, user, sectionModel);
        ExtendedModelMap taskModel = new ExtendedModelMap();
        controller.form(projectId, AiImprovementElementType.TASK, elementId, user, taskModel);

        assertThat((List<AiFeedbackType>) sectionModel.get("feedbackTypes"))
                .containsExactly(AiFeedbackType.IMPROVE, AiFeedbackType.EXPAND, AiFeedbackType.SIMPLIFY);
        assertThat((List<AiFeedbackType>) taskModel.get("feedbackTypes"))
                .containsExactly(AiFeedbackType.IMPROVE, AiFeedbackType.EXPAND, AiFeedbackType.SIMPLIFY,
                        AiFeedbackType.REPLAN, AiFeedbackType.ESTIMATE_EFFORT);
    }

    @Test
    void proposalPostRedirectsWithProposalIdAndReviewGetLoadsItFromSameSession() {
        AiElementImprovementService service = mock(AiElementImprovementService.class);
        AiElementImprovementController controller = new AiElementImprovementController(service);
        UUID projectId = UUID.randomUUID();
        UUID elementId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.org", "hash", true);
        AiImprovementForm form = new AiImprovementForm();
        form.setFeedbackType(AiFeedbackType.IMPROVE);
        AiImprovementContent content = new AiImprovementContent(
                AiImprovementElementType.SECTION, "Titel", "Beschreibung", null, null, null, null);
        AiImprovementProposal proposal = new AiImprovementProposal(
                proposalId, projectId, elementId, AiImprovementElementType.SECTION, 1,
                AiFeedbackType.IMPROVE, null, content, content);
        when(service.propose(projectId, AiImprovementElementType.SECTION, elementId, form, userId))
                .thenReturn(proposal);
        MockHttpSession session = new MockHttpSession();

        String postView = controller.propose(projectId, AiImprovementElementType.SECTION, elementId,
                form, new BeanPropertyBindingResult(form, "improvementForm"), user, session,
                new ExtendedModelMap());
        ExtendedModelMap reviewModel = new ExtendedModelMap();
        String getView = controller.review(projectId, proposalId, user, session, reviewModel);

        assertThat(postView).isEqualTo(
                "redirect:/projects/" + projectId + "/ai-improvements/" + proposalId);
        assertThat(getView).isEqualTo("projects/improvement/review");
        assertThat(reviewModel.get("proposal")).isSameAs(proposal);
        verify(service).requireImprovementAccess(projectId, userId);
    }

    @Test
    void discardingTemporaryProposalNeverConfirmsOrChangesPlanData() {
        AiElementImprovementService service = mock(AiElementImprovementService.class);
        AiElementImprovementController controller = new AiElementImprovementController(service);
        UUID projectId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AiImprovementProposal proposal = new AiImprovementProposal(
                proposalId, projectId, UUID.randomUUID(), AiImprovementElementType.SECTION, 1,
                AiFeedbackType.IMPROVE, null, null, null);
        MockHttpSession session = new MockHttpSession();
        var proposals = new LinkedHashMap<UUID, AiImprovementProposal>();
        proposals.put(proposalId, proposal);
        session.setAttribute("aiElementImprovementProposals", proposals);

        String view = controller.discard(projectId, proposalId,
                new AuthenticatedUser(userId, "user@example.org", "hash", true), session,
                new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/projects/" + projectId + "/plan");
        assertThat(proposals).isEmpty();
        verify(service).requireImprovementAccess(projectId, userId);
        verify(service, never()).confirm(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
