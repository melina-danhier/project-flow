package de.melinadanhier.projectflow.draft;

import de.melinadanhier.projectflow.draft.model.DraftPlan;
import de.melinadanhier.projectflow.draft.model.DraftPlanStatus;
import de.melinadanhier.projectflow.draft.repository.DraftRepository;
import de.melinadanhier.projectflow.draft.service.*;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DraftDiscardServiceTest {
    @Test
    void discardsOnlyMatchingEditableDraftProject() {
        DraftRepository drafts = mock(DraftRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectAuthorizationService authorization = mock(ProjectAuthorizationService.class);
        DraftApplicationService service = new DraftApplicationService(
                drafts, projects, mock(AiPlanGenerationWorkflowRepository.class), authorization,
                mock(DraftValidationService.class), mock(DraftPlanAdoptionFactory.class), Clock.systemUTC());
        UUID projectId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Project project = new Project();
        ReflectionTestUtils.setField(project, "id", projectId);
        project.setLocation(ProjectLocation.DRAFT);
        DraftPlan draft = new DraftPlan();
        ReflectionTestUtils.setField(draft, "id", draftId);
        draft.setProject(project);
        draft.setStatus(DraftPlanStatus.READY_FOR_REVIEW);
        when(projects.findForUpdate(projectId)).thenReturn(Optional.of(projectId));
        when(drafts.findForUpdateByProjectId(projectId)).thenReturn(Optional.of(draft));

        assertThat(service.discard(projectId, draftId, userId, draft.getLockVersion())).isEqualTo(draftId);
        assertThat(project.getLocation()).isEqualTo(ProjectLocation.TRASH);
        verify(authorization).requireOwner(projectId, userId);
    }
}
