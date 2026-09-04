package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.generation.controller.AiWorkflowController;
import de.melinadanhier.projectflow.generation.dto.AssumptionDecision;
import de.melinadanhier.projectflow.generation.dto.AssumptionDecisionRequest;
import de.melinadanhier.projectflow.generation.dto.AssumptionReviewDto;
import de.melinadanhier.projectflow.generation.dto.AssumptionReviewRequest;
import de.melinadanhier.projectflow.generation.dto.CriticalAssumptionReviewDto;
import de.melinadanhier.projectflow.generation.dto.response.AiWorkflowStatusDto;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.service.assumption.CriticalAssumptionReviewService;
import de.melinadanhier.projectflow.generation.service.precheck.AiPreCheckReviewService;
import de.melinadanhier.projectflow.generation.service.workflow.AiGenerationWorkflowService;
import de.melinadanhier.projectflow.generation.service.workflow.AiWorkflowQueryService;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CriticalAssumptionReviewControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AiWorkflowQueryService queries;
    @MockitoBean AiPreCheckReviewService preChecks;
    @MockitoBean AiGenerationWorkflowService generations;
    @MockitoBean CriticalAssumptionReviewService reviews;

    private final UUID workflowId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final AuthenticatedUser owner = new AuthenticatedUser(
            UUID.randomUUID(), "owner@example.org", "hash", true);

    @Test
    void showsOnlyGlobalAssumptionReviewBeforeDraft() throws Exception {
        when(reviews.getReview(workflowId, owner.userId())).thenReturn(new AssumptionReviewDto(
                workflowId, projectId, List.of(
                new CriticalAssumptionReviewDto(0, "Cloud-Dienste sind erlaubt.", false, null, null),
                new CriticalAssumptionReviewDto(1, "Zehn Stunden stehen bereit.", true, null, null)),
                null));

        mvc.perform(get("/projects/new/ai/assumptions/{id}", workflowId).with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(view().name("generation/assumption-review"))
                .andExpect(content().string(containsString("Kritische Annahmen prüfen")))
                .andExpect(content().string(containsString("Cloud-Dienste sind erlaubt.")))
                .andExpect(content().string(containsString("Zehn Stunden stehen bereit.")))
                .andExpect(content().string(containsString("Was trifft stattdessen zu?")))
                .andExpect(content().string(containsString("id=\"assumption-submit\"")))
                .andExpect(content().string(containsString("disabled")))
                .andExpect(content().string(not(containsString("Entwurf ausdrücklich übernehmen"))));
    }

    @Test
    void submitsAllDecisionsTogether() throws Exception {
        var expected = new AssumptionReviewRequest(List.of(
                new AssumptionDecisionRequest(0, AssumptionDecision.CONFIRMED, null),
                new AssumptionDecisionRequest(1, AssumptionDecision.REJECTED, "Vier Stunden.")));
        when(reviews.submit(workflowId, owner.userId(), expected)).thenReturn(true);

        mvc.perform(post("/projects/new/ai/assumptions/{id}", workflowId)
                        .with(user(owner)).with(csrf())
                        .param("decisions[0].assumptionIndex", "0")
                        .param("decisions[0].decision", "CONFIRMED")
                        .param("decisions[1].assumptionIndex", "1")
                        .param("decisions[1].decision", "REJECTED")
                        .param("decisions[1].correction", "Vier Stunden."))
                .andExpect(redirectedUrl("/projects/new/ai/status/" + workflowId));
        verify(reviews).submit(workflowId, owner.userId(), expected);
    }

    @Test
    void rejectsManipulatedAssumptionIndexBeforeBuildingTheRequest() throws Exception {
        mvc.perform(post("/projects/new/ai/assumptions/{id}", workflowId)
                        .with(user(owner)).with(csrf())
                        .param("decisions[0].assumptionIndex", "50")
                        .param("decisions[0].decision", "CONFIRMED"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reviews);
    }

    @Test
    void rejectsInvalidDecisionBeforeBuildingTheRequest() throws Exception {
        mvc.perform(post("/projects/new/ai/assumptions/{id}", workflowId)
                        .with(user(owner)).with(csrf())
                        .param("decisions[0].assumptionIndex", "0")
                        .param("decisions[0].decision", "MANIPULATED"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reviews);
    }

    @Test
    void routesFailedRegenerationWithSavedReviewBackToAssumptions() throws Exception {
        when(queries.getOwnedStatus(workflowId, owner.userId())).thenReturn(workflowStatus(
                AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE, true));

        mvc.perform(get("/projects/new/ai/status/{id}", workflowId).with(user(owner)))
                .andExpect(redirectedUrl("/projects/new/ai/assumptions/" + workflowId));
    }

    @Test
    void keepsPreCheckRoutingPrecedenceOverSavedAssumptionReview() throws Exception {
        when(queries.getOwnedStatus(workflowId, owner.userId())).thenReturn(workflowStatus(
                AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW, true));

        mvc.perform(get("/projects/new/ai/status/{id}", workflowId).with(user(owner)))
                .andExpect(redirectedUrl("/projects/new/ai/problems/" + workflowId));
    }

    @Test
    void retryDelegatesOwnershipAndStateValidationToGenerationService() throws Exception {
        mvc.perform(post("/projects/new/ai/status/{id}/retry", workflowId)
                        .with(user(owner)).with(csrf()))
                .andExpect(redirectedUrl("/projects/new/ai/status/" + workflowId));

        verify(generations).retry(workflowId, owner.userId());
        verifyNoInteractions(queries);
    }

    private AiWorkflowStatusDto workflowStatus(AiPlanGenerationWorkflowStatus status,
                                               boolean pendingAssumptionReview) {
        return new AiWorkflowStatusDto(
                workflowId, projectId, status, 0, 0, 0,
                null, null, null, pendingAssumptionReview);
    }
}
