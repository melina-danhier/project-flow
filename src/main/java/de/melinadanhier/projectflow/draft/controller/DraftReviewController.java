package de.melinadanhier.projectflow.draft.controller;

import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.draft.service.DraftReviewService;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import de.melinadanhier.projectflow.study.domain.StudyEventType;
import de.melinadanhier.projectflow.study.service.StudyTrackingService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class DraftReviewController {

    private static final String REVIEW_FILTERS_SESSION_ATTRIBUTE =
            DraftReviewController.class.getName() + ".reviewFilters";

    private final DraftReviewService draftReviewService;
    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final StudyTrackingService studyTrackingService;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final ProjectRepository projectRepository;

    @GetMapping({"/projects/{projectId}/draft", "/projects/{projectId}/draft/review"})
    public String review(@PathVariable UUID projectId,
                         @AuthenticationPrincipal AuthenticatedUser currentUser,
                         @RequestParam(required = false) String reviewStatus,
                         HttpSession session,
                         Model model) {
        projectAuthorizationService.requireMember(projectId, currentUser.userId());
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projekt oder Ressource wurde nicht gefunden."));
        if (project.getLocation() != ProjectLocation.DRAFT || project.getPlanConfirmedAt() != null) {
            return "redirect:/projects/" + projectId + "/plan";
        }
        var workflow = workflowRepository.findOwnedByProjectId(projectId, currentUser.userId()).orElse(null);
        if (workflow != null && workflow.getStatus() != AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED) {
            return "redirect:/projects/new/ai/status/" + workflow.getId();
        }
        String activeReviewStatus = resolveReviewStatus(session, projectId, reviewStatus);
        var draft = draftReviewService.review(projectId, currentUser.userId(), activeReviewStatus);
        model.addAttribute("draft", draft);
        return "generation/draft-review";
    }

    @PostMapping("/projects/{projectId}/draft/elements/{elementId}/accept")
    public String acceptElement(@PathVariable UUID projectId, @PathVariable UUID elementId,
                                @RequestParam long lockVersion,
                                @RequestParam(defaultValue = "false") boolean detail,
                                @AuthenticationPrincipal AuthenticatedUser currentUser, HttpSession session) {
        draftReviewService.acceptElement(projectId, elementId, currentUser.userId(), lockVersion);
        studyTrackingService.trackIfActive(session, StudyEventType.DRAFT_ITEM_ACCEPTED);
        return elementRedirect(projectId, elementId, detail, currentUser.userId());
    }

    @PostMapping("/projects/{projectId}/draft/sections/{sectionId}/accept")
    public String acceptSection(@PathVariable UUID projectId, @PathVariable UUID sectionId,
                                @RequestParam long lockVersion,
                                @AuthenticationPrincipal AuthenticatedUser currentUser, HttpSession session) {
        draftReviewService.acceptSection(projectId, sectionId, currentUser.userId(), lockVersion);
        studyTrackingService.trackIfActive(session, StudyEventType.DRAFT_ITEM_ACCEPTED);
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/elements/{elementId}/reject")
    public String rejectElement(@PathVariable UUID projectId, @PathVariable UUID elementId,
                                @RequestParam long lockVersion,
                                @RequestParam(defaultValue = "false") boolean detail,
                                @AuthenticationPrincipal AuthenticatedUser currentUser, HttpSession session) {
        draftReviewService.rejectElement(projectId, elementId, currentUser.userId(), lockVersion);
        studyTrackingService.trackIfActive(session, StudyEventType.DRAFT_ITEM_REJECTED);
        return elementRedirect(projectId, elementId, detail, currentUser.userId());
    }

    @PostMapping("/projects/{projectId}/draft/elements/{elementId}/reset")
    public String resetElement(@PathVariable UUID projectId, @PathVariable UUID elementId,
                               @RequestParam long lockVersion,
                               @RequestParam(defaultValue = "false") boolean detail,
                               @AuthenticationPrincipal AuthenticatedUser currentUser) {
        draftReviewService.resetElement(projectId, elementId, currentUser.userId(), lockVersion);
        return elementRedirect(projectId, elementId, detail, currentUser.userId());
    }

    @PostMapping("/projects/{projectId}/draft/sections/{sectionId}/reject")
    public String rejectSection(@PathVariable UUID projectId, @PathVariable UUID sectionId,
                                @RequestParam long lockVersion,
                                @AuthenticationPrincipal AuthenticatedUser currentUser, HttpSession session) {
        draftReviewService.rejectSection(projectId, sectionId, currentUser.userId(), lockVersion);
        studyTrackingService.trackIfActive(session, StudyEventType.DRAFT_ITEM_REJECTED);
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/sections/{sectionId}/reset")
    public String resetSection(@PathVariable UUID projectId, @PathVariable UUID sectionId,
                               @RequestParam long lockVersion,
                               @AuthenticationPrincipal AuthenticatedUser currentUser) {
        draftReviewService.resetSection(projectId, sectionId, currentUser.userId(), lockVersion);
        return reviewRedirect(projectId);
    }

    @PostMapping("/projects/{projectId}/draft/sort-mode")
    public String updateSortMode(@PathVariable UUID projectId,
                                 @RequestParam de.melinadanhier.projectflow.plancontainer.model.SortMode sortMode,
                                 @RequestParam long lockVersion,
                                 @AuthenticationPrincipal AuthenticatedUser currentUser) {
        draftReviewService.updateSortMode(projectId, currentUser.userId(), sortMode, lockVersion);
        return reviewRedirect(projectId);
    }

    private String reviewRedirect(UUID projectId) {
        return "redirect:/projects/" + projectId + "/draft/review";
    }

    @SuppressWarnings("unchecked")
    private String resolveReviewStatus(HttpSession session, UUID projectId, String requestedStatus) {
        Object current = session.getAttribute(REVIEW_FILTERS_SESSION_ATTRIBUTE);
        Map<UUID, String> filters;
        if (current instanceof Map<?, ?>) {
            filters = (Map<UUID, String>) current;
        } else {
            filters = new LinkedHashMap<>();
            session.setAttribute(REVIEW_FILTERS_SESSION_ATTRIBUTE, filters);
        }

        if (requestedStatus != null) {
            String normalized = requestedStatus.trim().toUpperCase();
            filters.put(projectId, normalized);
            return normalized;
        }
        return filters.getOrDefault(projectId, "");
    }

    private String elementRedirect(UUID projectId, UUID elementId, boolean detail, UUID userId) {
        if (!detail) return reviewRedirect(projectId);
        var element = draftReviewService.review(projectId, userId)
                .getElements().stream().filter(candidate -> candidate.getId().equals(elementId)).findFirst()
                .orElseThrow(() -> new de.melinadanhier.projectflow.common.exception.ResourceNotFoundException(
                        "Entwurfselement nicht gefunden."));
        String path = element.getType().equals("TASK") ? "tasks" : "milestones";
        return "redirect:/projects/" + projectId + "/draft/" + path + "/" + elementId;
    }
}
