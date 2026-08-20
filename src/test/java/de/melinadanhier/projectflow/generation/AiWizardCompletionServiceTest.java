package de.melinadanhier.projectflow.generation;

import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.generation.service.AiWizardCompletionService;
import de.melinadanhier.projectflow.generation.service.AiWorkflowCompletion;
import de.melinadanhier.projectflow.generation.service.AiWorkflowPersistenceService;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiWizardCompletionServiceTest {

    @Mock
    private AiPlanGenerationWorkflowRepository workflowRepository;

    @Mock
    private AiWorkflowPersistenceService persistenceService;

    @Test
    void parallelCompletionResolvesUniqueConflictToExistingWorkflow() throws Exception {
        UUID token = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AiWorkflowCompletion expected = new AiWorkflowCompletion(UUID.randomUUID(), UUID.randomUUID());
        AiPlanGenerationWorkflow existingWorkflow = existingWorkflow(expected);
        AtomicInteger lookups = new AtomicInteger();
        when(workflowRepository.findOwnedByCompletionToken(token, userId)).thenAnswer(invocation ->
                lookups.incrementAndGet() <= 2 ? Optional.empty() : Optional.of(existingWorkflow));

        CyclicBarrier concurrentCreates = new CyclicBarrier(2);
        AtomicBoolean created = new AtomicBoolean();
        when(persistenceService.create(any(), any(), any())).thenAnswer(invocation -> {
            concurrentCreates.await();
            if (created.compareAndSet(false, true)) {
                return expected;
            }
            throw new DataIntegrityViolationException("completion token already exists");
        });

        AiWizardCompletionService service = new AiWizardCompletionService(
                workflowRepository, persistenceService);
        AiWizardSnapshot snapshot = mock(AiWizardSnapshot.class);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<AiWorkflowCompletion> first = executor.submit(
                    () -> service.complete(token, userId, () -> snapshot));
            Future<AiWorkflowCompletion> second = executor.submit(
                    () -> service.complete(token, userId, () -> snapshot));

            assertThat(first.get()).isEqualTo(expected);
            assertThat(second.get()).isEqualTo(expected);
        }

        verify(persistenceService, times(2)).create(snapshot, token, userId);
        assertThat(created).isTrue();
    }

    @Test
    void missingSessionSnapshotAfterParallelCompletionResolvesToExistingWorkflow() {
        UUID token = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AiWorkflowCompletion expected = new AiWorkflowCompletion(UUID.randomUUID(), UUID.randomUUID());
        AiPlanGenerationWorkflow existingWorkflow = existingWorkflow(expected);
        when(workflowRepository.findOwnedByCompletionToken(token, userId))
                .thenReturn(Optional.empty(), Optional.of(existingWorkflow));
        AiWizardCompletionService service = new AiWizardCompletionService(
                workflowRepository, persistenceService);

        AiWorkflowCompletion completion = service.complete(token, userId, () -> {
            throw new IllegalStateException("Wizard-Session wurde bereits entfernt");
        });

        assertThat(completion).isEqualTo(expected);
        verify(persistenceService, times(0)).create(any(), any(), any());
    }

    private AiPlanGenerationWorkflow existingWorkflow(AiWorkflowCompletion completion) {
        AiPlanGenerationWorkflow workflow = mock(AiPlanGenerationWorkflow.class);
        Project project = mock(Project.class);
        when(workflow.getId()).thenReturn(completion.workflowId());
        when(workflow.getProject()).thenReturn(project);
        when(project.getId()).thenReturn(completion.projectId());
        return workflow;
    }
}
