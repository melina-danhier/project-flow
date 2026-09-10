package de.melinadanhier.projectflow.planelement;

import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementPlanContext;
import de.melinadanhier.projectflow.ai.model.planchange.*;
import de.melinadanhier.projectflow.ai.provider.AiClient;
import de.melinadanhier.projectflow.ai.validation.planchange.AiPlanChangeResponseValidator;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.membership.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.planelement.dto.planchange.PlanChangeProposal;
import de.melinadanhier.projectflow.planelement.model.*;
import de.melinadanhier.projectflow.planelement.repository.PlanElementRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import de.melinadanhier.projectflow.planelement.service.AiPlanChangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiPlanChangeConfirmationServiceTest {
    @Mock ProjectAuthorizationService authorization;
    @Mock PlanSectionRepository sections;
    @Mock PlanElementRepository elements;
    @Mock AiClient aiClient;
    @Mock ProjectMember membership;
    private final UUID projectId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private Project project;
    private PlanSection first;
    private PlanSection second;
    private Task task;
    private Milestone milestone;
    private AiPlanChangeService service;

    @BeforeEach void setUp() {
        project = identified(new Project(), projectId, 4);
        project.setTitle("Umzug"); project.setStartDate(LocalDate.of(2026, 9, 1));
        project.setEndDate(LocalDate.of(2026, 10, 31)); project.setSortMode(SortMode.MANUAL);
        first = section("Vorbereitung", 100, 2); second = section("Durchführung", 200, 3);
        task = identified(new Task(), UUID.randomUUID(), 5); task.setPlanContainer(project); task.setPlanSection(first);
        task.setTitle("Packen"); task.setDescription("Erhalten"); task.setPriority(TaskPriority.MEDIUM);
        task.setEstimatedHours(2); task.setOrigin(ElementOrigin.USER); task.setSortOrder(100);
        task.setStatus(TaskStatus.IN_PROGRESS);
        milestone = identified(new Milestone(), UUID.randomUUID(), 6); milestone.setPlanContainer(project);
        milestone.setPlanSection(second); milestone.setTitle("Übergabe"); milestone.setDescription("Alt");
        milestone.setDueDate(LocalDate.of(2026, 10, 20)); milestone.setCompleted(true);
        milestone.setOrigin(ElementOrigin.TEMPLATE); milestone.setSortOrder(100);
        when(membership.getProject()).thenReturn(project);
        when(authorization.requireEditableMemberForUpdate(projectId, userId)).thenReturn(membership);
        lenient().when(sections.findAllByPlanContainerIdOrderBySortOrderAsc(projectId))
                .thenReturn(new ArrayList<>(List.of(first, second)));
        lenient().when(elements.findPlanElements(projectId)).thenReturn(new ArrayList<>(List.of(task, milestone)));
        lenient().when(sections.save(any())).thenAnswer(invocation -> {
            PlanSection value = invocation.getArgument(0); if (value.getId() == null) ReflectionTestUtils.setField(value, "id", UUID.randomUUID()); return value;
        });
        lenient().when(elements.save(any())).thenAnswer(invocation -> {
            PlanElement value = invocation.getArgument(0); if (value.getId() == null) ReflectionTestUtils.setField(value, "id", UUID.randomUUID()); return value;
        });
        service = new AiPlanChangeService(authorization, sections, elements, aiClient,
                new AiPlanChangeResponseValidator());
    }

    @Test void appliesMixedChangesAndPreservesUnlistedTaskAndMilestoneState() {
        ProjectMember assignee = mock(ProjectMember.class); Task prerequisite = identified(new Task(), UUID.randomUUID(), 1);
        task.getAssignees().add(assignee); task.addPrerequisite(prerequisite);
        var changedTask = new AiTaskChange(AiPlanChangeOperation.MODIFIED, task.getId().toString(),
                second.getId().toString(), List.of("title", "section", "position"), "Kartons packen", null,
                null, null, null, null, new AiRelativePlacement(null, milestone.getId().toString()), "Ordnen");
        var changedMilestone = new AiMilestoneChange(AiPlanChangeOperation.MODIFIED,
                milestone.getId().toString(), second.getId().toString(), List.of("description"),
                null, "Neu", null, new AiRelativePlacement(null, null), null);
        PlanChangeProposal proposal = proposal(new AiPlanChangeResponse("Plan anpassen", List.of(),
                List.of(changedTask), List.of(changedMilestone)));

        service.confirm(projectId, proposal, userId);

        assertThat(task.getTitle()).isEqualTo("Kartons packen");
        assertThat(task.getPlanSection()).isSameAs(second); assertThat(task.getSortOrder()).isGreaterThan(milestone.getSortOrder());
        assertThat(task.getDescription()).isEqualTo("Erhalten"); assertThat(task.getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(task.getEstimatedHours()).isEqualTo(2); assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(task.getAssignees()).containsExactly(assignee); assertThat(task.getPrerequisites()).containsExactly(prerequisite);
        assertThat(task.getOrigin()).isEqualTo(ElementOrigin.AI_MODIFIED);
        assertThat(milestone.getDescription()).isEqualTo("Neu"); assertThat(milestone.isCompleted()).isTrue();
        assertThat(milestone.getOrigin()).isEqualTo(ElementOrigin.AI_MODIFIED);
        verify(elements).flush(); verify(sections).flush(); verifyNoInteractions(aiClient);
    }

    @Test void createsAiSectionTaskAndMilestoneWithoutCallingProvider() {
        var newSection = new AiSectionChange(AiPlanChangeOperation.NEW, null, "new-phase", List.of("title"),
                "Nachbereitung", null, null, null, null);
        var newTask = new AiTaskChange(AiPlanChangeOperation.NEW, null, "new-phase", List.of("title", "priority", "section"),
                "Abmelden", null, TaskPriority.HIGH, null, null, null, new AiRelativePlacement(null, null), null);
        var newMilestone = new AiMilestoneChange(AiPlanChangeOperation.NEW, null, "new-phase", List.of("title", "section"),
                "Abschluss", null, LocalDate.of(2026, 10, 25), new AiRelativePlacement(null, null), null);

        service.confirm(projectId, proposal(new AiPlanChangeResponse("Ergänzen", List.of(newSection),
                List.of(newTask), List.of(newMilestone))), userId);

        var sectionCaptor = org.mockito.ArgumentCaptor.forClass(PlanSection.class);
        verify(sections).save(sectionCaptor.capture()); assertThat(sectionCaptor.getValue().getOrigin()).isEqualTo(ElementOrigin.AI);
        var elementCaptor = org.mockito.ArgumentCaptor.forClass(PlanElement.class);
        verify(elements, times(2)).save(elementCaptor.capture());
        assertThat(elementCaptor.getAllValues()).allSatisfy(value -> {
            assertThat(value.getOrigin()).isEqualTo(ElementOrigin.AI);
            assertThat(value.getPlanSection()).isSameAs(sectionCaptor.getValue());
        });
        verifyNoInteractions(aiClient);
    }

    @Test void rejectsStaleSnapshotBeforeAnyMutation() {
        var change = new AiTaskChange(AiPlanChangeOperation.MODIFIED, task.getId().toString(),
                first.getId().toString(), List.of("title"), "Neu", null, null, null, null, null,
                new AiRelativePlacement(null, null), null);
        PlanChangeProposal stale = new PlanChangeProposal(UUID.randomUUID(), projectId, project.getTitle(), "Wunsch",
                Instant.now(), new AiImprovementPlanContext(List.of()), new AiPlanChangeResponse("Ändern", List.of(), List.of(change), List.of()),
                project.getLockVersion(), versions(first, second), Map.of(task.getId(), 4L, milestone.getId(), milestone.getLockVersion()));

        assertThatThrownBy(() -> service.confirm(projectId, stale, userId)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("seit dem KI-Vorschlag geändert");
        assertThat(task.getTitle()).isEqualTo("Packen");
        verify(elements, never()).save(any()); verify(elements, never()).flush();
    }

    private PlanChangeProposal proposal(AiPlanChangeResponse response) {
        return new PlanChangeProposal(UUID.randomUUID(), projectId, project.getTitle(), "Wunsch", Instant.now(),
                new AiImprovementPlanContext(List.of()), response, project.getLockVersion(), versions(first, second),
                Map.of(task.getId(), task.getLockVersion(), milestone.getId(), milestone.getLockVersion()));
    }
    private Map<UUID, Long> versions(PlanSection... values) {
        Map<UUID, Long> result = new LinkedHashMap<>(); for (PlanSection value : values) result.put(value.getId(), value.getLockVersion()); return result;
    }
    private PlanSection section(String title, int order, long version) {
        PlanSection result = identified(new PlanSection(), UUID.randomUUID(), version); result.setPlanContainer(project);
        result.setTitle(title); result.setOrigin(ElementOrigin.USER); result.setSortOrder(order); return result;
    }
    private <T> T identified(T value, UUID id, long version) {
        ReflectionTestUtils.setField(value, "id", id); ReflectionTestUtils.setField(value, "lockVersion", version); return value;
    }
}
