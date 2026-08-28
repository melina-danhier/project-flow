package de.melinadanhier.projectflow.draft;

import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.draft.model.DraftPlan;
import de.melinadanhier.projectflow.draft.model.DraftPlanStatus;
import de.melinadanhier.projectflow.draft.repository.PlanDraftRepository;
import de.melinadanhier.projectflow.draft.service.DraftApplicationService;
import de.melinadanhier.projectflow.draft.service.DraftReviewService;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMemberRole;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DraftServicesIntegrationTest {

    @Autowired
    private DraftReviewService reviewService;

    @Autowired
    private DraftApplicationService applicationService;

    @Autowired
    private PlanDraftRepository draftRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void reviewRequiresTheProjectOwner() {
        Project project = projectWithOwner("review-draft-owner@example.org");
        User outsider = user("review-draft-outsider@example.org");
        DraftPlan draft = draft(project, DraftPlanStatus.READY_FOR_REVIEW);
        draftRepository.saveAndFlush(draft);

        assertThat(reviewService.review(project.getId(), project.getMemberships().iterator().next().getUser().getId()))
                .extracting("id", "projectId")
                .containsExactly(draft.getId(), project.getId());
        assertThatThrownBy(() -> reviewService.review(project.getId(), outsider.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void applyingDraftIsRejectedWhenTheActivePlanAlreadyContainsElements() {
        Project project = projectWithOwner("occupied-plan-owner@example.org");
        DraftPlan draft = draft(project, DraftPlanStatus.READY_FOR_REVIEW);
        draftRepository.saveAndFlush(draft);
        Task existingTask = new Task();
        existingTask.setTitle("Bestehende Aufgabe");
        existingTask.setOrigin(ElementOrigin.USER);
        project.addElement(existingTask);
        projectRepository.saveAndFlush(project);

        assertThatThrownBy(() -> applicationService.apply(
                project.getId(), project.getMemberships().iterator().next().getUser().getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("bereits Inhalte");
    }

    private Project projectWithOwner(String email) {
        User owner = user(email);
        Project project = new Project();
        project.setTitle("KI-Projekt");
        project.setCreationType(CreationType.AI);
        project.setStatus(ProjectStatus.DRAFT);
        project.setLocation(ProjectLocation.DRAFT);
        ProjectMember membership = new ProjectMember();
        membership.setUser(owner);
        membership.setRole(ProjectMemberRole.OWNER);
        membership.setActive(true);
        project.addMembership(membership);
        return projectRepository.saveAndFlush(project);
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("Draft Service Test");
        user.setPasswordHash("$2a$12$test-hash");
        user.setEnabled(true);
        return userRepository.saveAndFlush(user);
    }

    private DraftPlan draft(Project project, DraftPlanStatus status) {
        DraftPlan draft = new DraftPlan();
        draft.setProject(project);
        draft.setStatus(status);
        project.attachDraft(draft);
        return draft;
    }

}
