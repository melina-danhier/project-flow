package de.melinadanhier.projectflow.planelement;

import de.melinadanhier.projectflow.plancontainer.project.mapper.ProjectMapper;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectMemberRepository;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.planelement.dto.DeleteSectionForm;
import de.melinadanhier.projectflow.planelement.dto.MilestoneForm;
import de.melinadanhier.projectflow.planelement.dto.SectionForm;
import de.melinadanhier.projectflow.planelement.dto.TaskDependencyForm;
import de.melinadanhier.projectflow.planelement.mapper.PlanElementMapper;
import de.melinadanhier.projectflow.planelement.repository.MilestoneRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanElementRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import de.melinadanhier.projectflow.planelement.repository.TaskRepository;
import de.melinadanhier.projectflow.planelement.service.MilestoneService;
import de.melinadanhier.projectflow.planelement.service.SectionService;
import de.melinadanhier.projectflow.planelement.service.TaskDependencyService;
import de.melinadanhier.projectflow.planelement.service.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PlanStructureLockingTest {

    @Mock private ProjectAuthorizationService authorizationService;
    @Mock private PlanSectionRepository sectionRepository;
    @Mock private PlanElementRepository elementRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private MilestoneRepository milestoneRepository;
    @Mock private ProjectMemberRepository memberRepository;
    @Mock private PlanElementMapper elementMapper;
    @Mock private ProjectMapper projectMapper;

    private final UUID projectId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void sectionCreateReorderAndDeleteAcquireProjectLockBeforeRepositoryAccess() {
        RuntimeException lockReached = failAtProjectLock();
        SectionService service = new SectionService(
                sectionRepository, elementRepository, taskRepository, elementMapper, authorizationService);

        assertStopsAtLock(() -> service.createSection(projectId, new SectionForm(), userId), lockReached);
        assertStopsAtLock(() -> service.updateSection(
                projectId, UUID.randomUUID(), new SectionForm(), userId), lockReached);
        assertStopsAtLock(() -> service.deleteSection(
                projectId, UUID.randomUUID(), new DeleteSectionForm(), userId), lockReached);

        verify(authorizationService, times(3)).requireEditableMemberForUpdate(projectId, userId);
        verifyNoInteractions(sectionRepository, elementRepository, taskRepository);
    }

    @Test
    void milestoneCreateReorderAndDeleteAcquireProjectLockBeforeRepositoryAccess() {
        RuntimeException lockReached = failAtProjectLock();
        MilestoneService service = new MilestoneService(
                milestoneRepository, elementRepository, sectionRepository, authorizationService, elementMapper);

        assertStopsAtLock(() -> service.createMilestone(projectId, new MilestoneForm(), userId), lockReached);
        assertStopsAtLock(() -> service.updateMilestone(
                projectId, UUID.randomUUID(), new MilestoneForm(), userId), lockReached);
        assertStopsAtLock(() -> service.deleteMilestone(projectId, UUID.randomUUID(), userId), lockReached);

        verify(authorizationService, times(3)).requireEditableMemberForUpdate(projectId, userId);
        verifyNoInteractions(milestoneRepository, elementRepository, sectionRepository);
    }

    @Test
    void taskDeletionAcquiresProjectLockBeforeRenumbering() {
        RuntimeException lockReached = failAtProjectLock();
        TaskService service = new TaskService(
                taskRepository, elementRepository, sectionRepository, memberRepository,
                authorizationService, elementMapper, projectMapper);

        assertStopsAtLock(() -> service.deleteTask(projectId, UUID.randomUUID(), userId), lockReached);

        verify(authorizationService).requireEditableMemberForUpdate(projectId, userId);
        verifyNoInteractions(taskRepository, elementRepository, sectionRepository);
    }

    @Test
    void dependencyCreationAndDeletionAcquireProjectLockBeforeGraphAccess() {
        RuntimeException lockReached = failAtProjectLock();
        TaskDependencyService service = new TaskDependencyService(taskRepository, authorizationService);

        assertStopsAtLock(() -> service.createDependency(projectId, new TaskDependencyForm(), userId), lockReached);
        assertStopsAtLock(() -> service.deleteDependency(
                projectId, UUID.randomUUID(), UUID.randomUUID(), userId), lockReached);

        verify(authorizationService, times(2)).requireEditableMemberForUpdate(projectId, userId);
        verifyNoInteractions(taskRepository);
    }

    private RuntimeException failAtProjectLock() {
        RuntimeException marker = new RuntimeException("project lock reached");
        doThrow(marker).when(authorizationService).requireEditableMemberForUpdate(projectId, userId);
        return marker;
    }

    private void assertStopsAtLock(Runnable operation, RuntimeException marker) {
        assertThatThrownBy(operation::run).isSameAs(marker);
    }
}
