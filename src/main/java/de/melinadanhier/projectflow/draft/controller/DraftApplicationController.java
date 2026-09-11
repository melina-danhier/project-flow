package de.melinadanhier.projectflow.draft.controller;

import de.melinadanhier.projectflow.common.model.MutableEntity;
import de.melinadanhier.projectflow.draft.dto.application.DraftApplyResult;
import de.melinadanhier.projectflow.draft.service.DraftApplicationPersistenceException;
import de.melinadanhier.projectflow.draft.service.DraftApplicationService;
import de.melinadanhier.projectflow.draft.repository.DraftRepository;
import de.melinadanhier.projectflow.generation.service.workflow.AiGenerationWorkflowService;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import de.melinadanhier.projectflow.feedback.domain.AiFeedbackContext;
import de.melinadanhier.projectflow.feedback.service.AiFeedbackOpportunity;
import de.melinadanhier.projectflow.study.domain.StudyEventType;
import de.melinadanhier.projectflow.study.service.StudyTrackingService;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectService;
import de.melinadanhier.projectflow.wizard.service.ProjectWizardService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;
import java.util.function.Supplier;

@Controller
@RequiredArgsConstructor
public class DraftApplicationController {

    private final DraftApplicationService draftApplicationService;
    private final AiGenerationWorkflowService generationWorkflowService;
    private final StudyTrackingService studyTrackingService;
    private final DraftRepository draftRepository;
    private final ProjectService projectService;
    private final ProjectWizardService wizardService;

    @PostMapping("/projects/{projectId}/draft/apply")
    public String apply(@PathVariable UUID projectId,
                        @AuthenticationPrincipal AuthenticatedUser currentUser,
                        RedirectAttributes redirectAttributes,
                        Model model, HttpSession session) {
        UUID draftId = draftRepository.findByProjectId(projectId)
                .map(MutableEntity::getId).orElse(projectId);
        DraftApplyResult result = executeApplication(
                () -> draftApplicationService.apply(projectId, currentUser.userId()));
        return switch (result.status()) {
            case APPLIED -> appliedRedirect(projectId, draftId, redirectAttributes, session);
            case PENDING_CONFIRMATION_REQUIRED, EMPTY_DRAFT_CONFIRMATION_REQUIRED -> {
                model.addAttribute("summary", result.summary());
                yield "generation/draft-pending-confirmation";
            }
        };
    }

    @PostMapping("/projects/{projectId}/draft/continue-with-pending")
    public String continueWithPending(@PathVariable UUID projectId,
                                      @RequestParam UUID draftId,
                                      @RequestParam long lockVersion,
                                      @AuthenticationPrincipal AuthenticatedUser currentUser,
                                      RedirectAttributes redirectAttributes, HttpSession session) {
        executeApplication(() -> draftApplicationService.continueWithPending(
                projectId, draftId, currentUser.userId(), lockVersion));
        return appliedRedirect(projectId, draftId, redirectAttributes, session);
    }

    @PostMapping("/projects/{projectId}/draft/confirm-empty")
    public String confirmEmpty(@PathVariable UUID projectId,
                               @RequestParam UUID draftId,
                               @RequestParam long lockVersion,
                               @AuthenticationPrincipal AuthenticatedUser currentUser,
                               RedirectAttributes redirectAttributes, HttpSession session) {
        executeApplication(() -> draftApplicationService.confirmEmpty(
                projectId, draftId, currentUser.userId(), lockVersion));
        return appliedRedirect(projectId, draftId, redirectAttributes, session);
    }

    @PostMapping("/projects/{projectId}/draft/regenerate")
    public String regenerate(@PathVariable UUID projectId,
                             @RequestParam UUID draftId,
                             @RequestParam long lockVersion,
                             @RequestParam String regenerationComment,
                             @AuthenticationPrincipal AuthenticatedUser currentUser, HttpSession session) {
        UUID workflowId = generationWorkflowService.regenerateDraft(
                projectId, draftId, currentUser.userId(), lockVersion, regenerationComment);
        studyTrackingService.trackIfActive(session, StudyEventType.PLAN_REGENERATED);
        return "redirect:/projects/new/ai/status/" + workflowId;
    }

    @PostMapping("/projects/{projectId}/draft/discard")
    public String discard(@PathVariable UUID projectId,
                          @RequestParam UUID draftId,
                          @RequestParam long lockVersion,
                          @AuthenticationPrincipal AuthenticatedUser currentUser,
                          HttpSession session) {
        draftApplicationService.discard(projectId, draftId, currentUser.userId(), lockVersion);
        projectService.deleteDraftProjectPermanently(projectId, currentUser.userId());
        wizardService.clearOwned(currentUser.userId(), session);
        session.setAttribute(AiFeedbackOpportunity.SESSION_ATTRIBUTE,
                new AiFeedbackOpportunity(AiFeedbackContext.DRAFT_DELETED, draftId, "/projects"));
        return "redirect:/projects";
    }

    private String appliedRedirect(UUID projectId, UUID draftId, RedirectAttributes redirectAttributes,
                                   HttpSession session) {
        studyTrackingService.trackIfActive(session, StudyEventType.PLAN_ADOPTED);
        session.setAttribute(AiFeedbackOpportunity.SESSION_ATTRIBUTE,
                new AiFeedbackOpportunity(AiFeedbackContext.PLAN_ADOPTED, draftId,
                        "/projects/" + projectId + "/plan"));
        redirectAttributes.addFlashAttribute("successMessage",
                "Der KI-Entwurf wurde übernommen.");
        return "redirect:/projects/" + projectId + "/plan";
    }

    private <T> T executeApplication(Supplier<T> application) {
        try {
            return application.get();
        } catch (DataAccessException exception) {
            throw new DraftApplicationPersistenceException(exception);
        }
    }
}
