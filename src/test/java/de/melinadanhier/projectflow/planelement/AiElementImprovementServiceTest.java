package de.melinadanhier.projectflow.planelement;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.model.improvement.AiFeedbackType;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementContent;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementElementType;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementRequest;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementPlanContext;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementResponse;
import de.melinadanhier.projectflow.ai.model.improvement.AiReplanPlacementResponse;
import de.melinadanhier.projectflow.ai.provider.AiClient;
import de.melinadanhier.projectflow.ai.validation.improvement.AiImprovementResponseValidator;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import de.melinadanhier.projectflow.plancontainer.project.model.membership.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.planelement.dto.improvement.AiImprovementForm;
import de.melinadanhier.projectflow.planelement.dto.improvement.AiImprovementProposal;
import de.melinadanhier.projectflow.planelement.dto.improvement.AiReplanPlacementProposal;
import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import de.melinadanhier.projectflow.planelement.model.Milestone;
import de.melinadanhier.projectflow.planelement.model.PlanSection;
import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import de.melinadanhier.projectflow.planelement.model.TaskStatus;
import de.melinadanhier.projectflow.planelement.repository.MilestoneRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanElementRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import de.melinadanhier.projectflow.planelement.repository.TaskRepository;
import de.melinadanhier.projectflow.planelement.service.AiElementImprovementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiElementImprovementServiceTest {

    @Mock ProjectAuthorizationService authorizationService;
    @Mock PlanSectionRepository sectionRepository;
    @Mock TaskRepository taskRepository;
    @Mock MilestoneRepository milestoneRepository;
    @Mock PlanElementRepository planElementRepository;
    @Mock AiClient aiClient;
    @Mock ProjectMember membership;

    private final UUID projectId = UUID.randomUUID();
    private final UUID elementId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private Project project;
    private AiElementImprovementService service;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setTitle("Umzug");
        project.setDescription("Umzug in sechs Wochen");
        project.setStartDate(LocalDate.of(2026, 9, 10));
        project.setEndDate(LocalDate.of(2026, 10, 22));
        org.mockito.Mockito.lenient().when(membership.getProject()).thenReturn(project);
        org.mockito.Mockito.lenient().when(authorizationService.requireEditableMemberForUpdate(projectId, userId))
                .thenReturn(membership);
        service = new AiElementImprovementService(authorizationService, sectionRepository, taskRepository,
                milestoneRepository, planElementRepository, aiClient, new AiImprovementResponseValidator());
    }

    @Test
    void createsValidSectionProposalWithoutChangingActiveSection() {
        PlanSection section = section("Vorbereitung", "Alles vorbereiten", 3);
        when(authorizationService.requireEditableMember(projectId, userId)).thenReturn(membership);
        when(sectionRepository.findByIdAndPlanContainerId(elementId, projectId)).thenReturn(Optional.of(section));
        when(aiClient.improveElement(any())).thenReturn(response(AiImprovementElementType.SECTION,
                "Vorbereitung strukturieren", "Notwendige Vorbereitungen geordnet abschließen."));

        AiImprovementProposal proposal = service.propose(projectId, AiImprovementElementType.SECTION,
                elementId, form(AiFeedbackType.IMPROVE, null), userId);

        assertThat(proposal.proposed().title()).isEqualTo("Vorbereitung strukturieren");
        assertThat(proposal.elementVersion()).isEqualTo(3);
        assertThat(section.getTitle()).isEqualTo("Vorbereitung");
        verify(sectionRepository, never()).flush();
    }

    @Test
    void createsValidTaskProposalAndKeepsRelationshipsOutOfAiRequest() {
        Task task = task(4);
        PlanSection section = section("Phase", null, 1);
        ProjectMember assignee = org.mockito.Mockito.mock(ProjectMember.class);
        Task prerequisite = new Task();
        task.setPlanSection(section);
        task.setAssignee(assignee);
        task.addPrerequisite(prerequisite);
        when(authorizationService.requireEditableMember(projectId, userId)).thenReturn(membership);
        when(taskRepository.findByIdAndPlanContainerId(elementId, projectId)).thenReturn(Optional.of(task));
        when(aiClient.improveElement(any())).thenReturn(new AiImprovementResponse(
                AiImprovementElementType.TASK, "Kartons beschriften", "Raum und Inhalt notieren",
                TaskPriority.MEDIUM, 2, LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 12)));

        AiImprovementProposal proposal = service.propose(projectId, AiImprovementElementType.TASK,
                elementId, form(AiFeedbackType.EXPAND, "  Schwerpunkt auf Ergebnis  "), userId);

        assertThat(proposal.comment()).isEqualTo("Schwerpunkt auf Ergebnis");
        assertThat(proposal.proposed().priority()).isEqualTo(TaskPriority.MEDIUM);
        ArgumentCaptor<AiImprovementRequest> request = ArgumentCaptor.forClass(AiImprovementRequest.class);
        verify(aiClient).improveElement(request.capture());
        assertThat(request.getValue().feedbackType()).isEqualTo(AiFeedbackType.EXPAND);
        assertThat(request.getValue().comment()).isEqualTo("Schwerpunkt auf Ergebnis");
        assertThat(request.getValue().element()).hasNoNullFieldsOrPropertiesExcept("description");
        assertThat(task.getPlanSection()).isSameAs(section);
        assertThat(task.getAssignee()).isSameAs(assignee);
        assertThat(task.getPrerequisites()).containsExactly(prerequisite);
    }

    @Test
    void createsValidMilestoneProposal() {
        Milestone milestone = milestone(2);
        when(authorizationService.requireEditableMember(projectId, userId)).thenReturn(membership);
        when(milestoneRepository.findByIdAndPlanContainerId(elementId, projectId))
                .thenReturn(Optional.of(milestone));
        when(aiClient.improveElement(any())).thenReturn(new AiImprovementResponse(
                AiImprovementElementType.MILESTONE, "Wohnungsübergabe abgeschlossen", "Schlüssel übergeben",
                null, null, null, LocalDate.of(2026, 10, 20)));

        AiImprovementProposal proposal = service.propose(projectId, AiImprovementElementType.MILESTONE,
                elementId, form(AiFeedbackType.SIMPLIFY, " "), userId);

        assertThat(proposal.comment()).isNull();
        assertThat(proposal.proposed().dueDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(milestone.getTitle()).isEqualTo("Übergabe");
    }

    @ParameterizedTest
    @EnumSource(value = AiFeedbackType.class, names = {"IMPROVE", "EXPAND", "SIMPLIFY"})
    void passesEveryFeedbackTypeSeparatelyToProvider(AiFeedbackType feedbackType) {
        PlanSection section = section("Phase", null, 0);
        when(authorizationService.requireEditableMember(projectId, userId)).thenReturn(membership);
        when(sectionRepository.findByIdAndPlanContainerId(elementId, projectId)).thenReturn(Optional.of(section));
        when(aiClient.improveElement(any())).thenReturn(response(AiImprovementElementType.SECTION, "Phase", null));

        service.propose(projectId, AiImprovementElementType.SECTION, elementId,
                form(feedbackType, " Hinweis "), userId);

        ArgumentCaptor<AiImprovementRequest> request = ArgumentCaptor.forClass(AiImprovementRequest.class);
        verify(aiClient).improveElement(request.capture());
        assertThat(request.getValue().feedbackType()).isEqualTo(feedbackType);
        assertThat(request.getValue().comment()).isEqualTo("Hinweis");
    }

    @Test
    void rejectsOverlongTrimmedCommentBeforeProviderCall() {
        assertThatThrownBy(() -> service.propose(projectId, AiImprovementElementType.SECTION, elementId,
                form(AiFeedbackType.IMPROVE, "x".repeat(501)), userId))
                .isInstanceOf(DomainValidationException.class);
        verifyNoInteractions(aiClient, sectionRepository, taskRepository, milestoneRepository);
    }

    @Test
    void rejectsInvalidAiOutputAndDoesNotChangeData() {
        Task task = task(1);
        when(authorizationService.requireEditableMember(projectId, userId)).thenReturn(membership);
        when(taskRepository.findByIdAndPlanContainerId(elementId, projectId)).thenReturn(Optional.of(task));
        when(aiClient.improveElement(any())).thenReturn(new AiImprovementResponse(
                AiImprovementElementType.MILESTONE, "Falsch", null, null, null, null, null));

        assertThatThrownBy(() -> service.propose(projectId, AiImprovementElementType.TASK, elementId,
                form(AiFeedbackType.IMPROVE, null), userId)).isInstanceOf(AiOutputValidationException.class);
        assertThat(task.getTitle()).isEqualTo("Packen");
        verify(taskRepository, never()).flush();
    }

    @ParameterizedTest
    @EnumSource(value = AiFeedbackType.class, names = {"IMPROVE", "EXPAND", "SIMPLIFY"})
    void rejectsInventedTaskPlanningValuesForEveryFeedbackType(AiFeedbackType feedbackType) {
        Task task = task(1);
        task.setEstimatedHours(null);
        task.setStartDate(null);
        task.setDueDate(null);
        when(authorizationService.requireEditableMember(projectId, userId)).thenReturn(membership);
        when(taskRepository.findByIdAndPlanContainerId(elementId, projectId)).thenReturn(Optional.of(task));
        when(aiClient.improveElement(any())).thenReturn(new AiImprovementResponse(
                AiImprovementElementType.TASK, "Packen und beschriften", "Kartons nach Räumen sortieren",
                TaskPriority.MEDIUM, 8, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1)));

        assertThatThrownBy(() -> service.propose(projectId, AiImprovementElementType.TASK, elementId,
                form(feedbackType, null), userId)).isInstanceOf(AiOutputValidationException.class);

        assertThat(task.getEstimatedHours()).isNull();
        assertThat(task.getStartDate()).isNull();
        assertThat(task.getDueDate()).isNull();
        verify(taskRepository, never()).flush();
    }

    @Test
    void replanReceivesCompleteOrderedPlanContextWithoutChangingOtherElements() {
        Task selected = task(4);
        PlanSection section = section("Vorbereitung", "Alles vorbereiten", 1);
        UUID sectionId = UUID.randomUUID();
        ReflectionTestUtils.setField(section, "id", sectionId);
        selected.setPlanSection(section);
        selected.setSortOrder(200);

        Task prerequisite = new Task();
        UUID prerequisiteId = UUID.randomUUID();
        ReflectionTestUtils.setField(prerequisite, "id", prerequisiteId);
        prerequisite.setTitle("Material besorgen");
        prerequisite.setDescription("Kartons kaufen");
        prerequisite.setPlanSection(section);
        prerequisite.setSortOrder(100);
        prerequisite.setPriority(TaskPriority.LOW);
        prerequisite.setOrigin(ElementOrigin.USER);
        selected.addPrerequisite(prerequisite);

        Milestone milestone = milestone(1);
        UUID milestoneId = UUID.randomUUID();
        ReflectionTestUtils.setField(milestone, "id", milestoneId);
        milestone.setPlanSection(section);
        milestone.setSortOrder(300);

        when(authorizationService.requireEditableMember(projectId, userId)).thenReturn(membership);
        when(taskRepository.findByIdAndPlanContainerId(elementId, projectId)).thenReturn(Optional.of(selected));
        when(planElementRepository.findPlanElements(projectId)).thenReturn(List.of(prerequisite, selected, milestone));
        when(sectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(projectId)).thenReturn(List.of(section));
        when(planElementRepository.findByIdAndPlanContainerId(elementId, projectId)).thenReturn(Optional.of(selected));
        when(planElementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(projectId, sectionId))
                .thenReturn(List.of(prerequisite, selected, milestone));
        when(aiClient.improveElement(any())).thenReturn(new AiImprovementResponse(
                AiImprovementElementType.TASK, "Packen", null, TaskPriority.MEDIUM, 2,
                LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 15),
                AiReplanPlacementResponse.unchanged(),
                "Die Aufgabe liegt vor dem Transporttermin."));

        AiImprovementProposal proposal = service.propose(projectId, AiImprovementElementType.TASK, elementId,
                form(AiFeedbackType.REPLAN, null), userId);

        ArgumentCaptor<AiImprovementRequest> request = ArgumentCaptor.forClass(AiImprovementRequest.class);
        verify(aiClient).improveElement(request.capture());
        assertThat(request.getValue().plan().selectedElementReference()).isEqualTo(elementId.toString());
        assertThat(request.getValue().plan().sections()).hasSize(1);
        var planSection = request.getValue().plan().sections().getFirst();
        assertThat(planSection.elements()).extracting(AiImprovementPlanContext.Element::elementType)
                .containsExactly(AiImprovementElementType.TASK, AiImprovementElementType.TASK,
                        AiImprovementElementType.MILESTONE);
        assertThat(planSection.elements()).extracting(AiImprovementPlanContext.Element::title)
                .containsExactly("Material besorgen", "Packen", "Übergabe");
        assertThat(planSection.elements()).extracting(AiImprovementPlanContext.Element::position)
                .containsExactly(1, 2, 3);
        assertThat(planSection.elements().get(1).prerequisiteReferences()).containsExactly(prerequisiteId.toString());
        assertThat(planSection.reference()).isEqualTo(sectionId.toString());
        assertThat(prerequisite.getTitle()).isEqualTo("Material besorgen");
        assertThat(milestone.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(proposal.explanation()).isEqualTo("Die Aufgabe liegt vor dem Transporttermin.");
    }

    @Test
    void effortEstimationReceivesOnlyTaskSectionAndBasicProjectContext() {
        Task task = task(2);
        PlanSection section = section("Vorbereitung", "Alles vorbereiten", 1);
        task.setPlanSection(section);
        when(authorizationService.requireEditableMember(projectId, userId)).thenReturn(membership);
        when(taskRepository.findByIdAndPlanContainerId(elementId, projectId)).thenReturn(Optional.of(task));
        when(aiClient.improveElement(any())).thenReturn(new AiImprovementResponse(
                AiImprovementElementType.TASK, "Packen", null, TaskPriority.MEDIUM, 5,
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 12),
                "Der Umfang entspricht etwa fünf Arbeitsstunden."));

        service.propose(projectId, AiImprovementElementType.TASK, elementId,
                form(AiFeedbackType.ESTIMATE_EFFORT, null), userId);

        ArgumentCaptor<AiImprovementRequest> request = ArgumentCaptor.forClass(AiImprovementRequest.class);
        verify(aiClient).improveElement(request.capture());
        assertThat(request.getValue().section().title()).isEqualTo("Vorbereitung");
        assertThat(request.getValue().project().title()).isEqualTo("Umzug");
        assertThat(request.getValue().plan()).isNull();
    }

    @Test
    void rejectsActionThatIsNotAvailableForElementTypeBeforeProviderCall() {
        assertThatThrownBy(() -> service.propose(projectId, AiImprovementElementType.SECTION, elementId,
                form(AiFeedbackType.REPLAN, null), userId)).isInstanceOf(DomainValidationException.class);
        verifyNoInteractions(aiClient, sectionRepository, taskRepository, milestoneRepository);
    }

    @Test
    void technicalAiErrorDoesNotChangeData() {
        Task task = task(1);
        when(authorizationService.requireEditableMember(projectId, userId)).thenReturn(membership);
        when(taskRepository.findByIdAndPlanContainerId(elementId, projectId)).thenReturn(Optional.of(task));
        when(aiClient.improveElement(any())).thenThrow(
                new AiTechnicalException(AiTechnicalErrorCode.PROVIDER_UNAVAILABLE, "nicht erreichbar"));

        assertThatThrownBy(() -> service.propose(projectId, AiImprovementElementType.TASK, elementId,
                form(AiFeedbackType.IMPROVE, null), userId)).isInstanceOf(AiTechnicalException.class);
        assertThat(task.getTitle()).isEqualTo("Packen");
        verify(taskRepository, never()).flush();
    }

    @Test
    void confirmingReplanStoresOnlyDatesAndPreservesAllRelationshipsAndState() {
        Task task = task(5);
        PlanSection section = section("Phase", null, 1);
        ProjectMember assignee = org.mockito.Mockito.mock(ProjectMember.class);
        Task prerequisite = new Task();
        task.setPlanSection(section);
        task.setAssignee(assignee);
        task.addPrerequisite(prerequisite);
        task.setSortOrder(700);
        task.setStatus(TaskStatus.IN_PROGRESS);
        when(taskRepository.findByIdAndPlanContainerId(elementId, projectId)).thenReturn(Optional.of(task));
        AiImprovementProposal proposal = proposal(AiImprovementElementType.TASK, 5, AiFeedbackType.REPLAN,
                null,
                new AiImprovementResponse(AiImprovementElementType.TASK, "Packen", null,
                        TaskPriority.MEDIUM, 2, LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 16),
                        "Die neuen Termine passen zur Abhängigkeitsreihenfolge."));

        service.confirm(proposal, userId);

        assertThat(task.getTitle()).isEqualTo("Packen");
        assertThat(task.getDescription()).isNull();
        assertThat(task.getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(task.getEstimatedHours()).isEqualTo(2);
        assertThat(task.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 16));
        assertThat(task.getOrigin()).isEqualTo(ElementOrigin.AI_MODIFIED);
        assertThat(task.getPlanSection()).isSameAs(section);
        assertThat(task.getAssignee()).isSameAs(assignee);
        assertThat(task.getPrerequisites()).containsExactly(prerequisite);
        assertThat(task.getSortOrder()).isEqualTo(700);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        verify(taskRepository).flush();
        verifyNoInteractions(sectionRepository, milestoneRepository);
    }

    @Test
    void confirmingReplanMovesTaskToAnotherSectionAfterReferencedElement() {
        project.setSortMode(SortMode.MANUAL);
        Task selected = task(5);
        selected.setSortOrder(100);
        PlanSection originalSection = identifiedSection("Vorbereitung", UUID.randomUUID());
        PlanSection targetSection = identifiedSection("Transport", UUID.randomUUID());
        selected.setPlanSection(originalSection);
        Milestone reference = milestone(1);
        UUID referenceId = UUID.randomUUID();
        ReflectionTestUtils.setField(reference, "id", referenceId);
        reference.setPlanSection(targetSection);
        reference.setSortOrder(100);

        when(taskRepository.findByIdAndPlanContainerId(elementId, projectId)).thenReturn(Optional.of(selected));
        when(sectionRepository.findByIdAndPlanContainerId(targetSection.getId(), projectId))
                .thenReturn(Optional.of(targetSection));
        when(planElementRepository.findByIdAndPlanContainerId(referenceId, projectId))
                .thenReturn(Optional.of(reference));
        when(planElementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
                projectId, targetSection.getId())).thenReturn(List.of(reference));
        AiReplanPlacementProposal placement = placement(selected, targetSection,
                "nach \"Übergabe\"", null, referenceId);
        AiImprovementProposal proposal = replanProposal(selected, placement,
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 16));

        service.confirm(proposal, userId);

        assertThat(selected.getPlanSection()).isSameAs(targetSection);
        assertThat(selected.getSortOrder()).isGreaterThan(reference.getSortOrder());
        assertThat(selected.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(selected.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 16));
    }

    @Test
    void confirmingReplanMovesMilestoneWithoutChangingItsOtherFields() {
        project.setSortMode(SortMode.MANUAL);
        Milestone selected = milestone(5);
        selected.setCompleted(true);
        selected.setSortOrder(300);
        PlanSection target = identifiedSection("Transport", UUID.randomUUID());
        Task reference = task(1);
        UUID referenceId = UUID.randomUUID();
        ReflectionTestUtils.setField(reference, "id", referenceId);
        reference.setPlanSection(target);
        reference.setSortOrder(100);
        when(milestoneRepository.findByIdAndPlanContainerId(elementId, projectId)).thenReturn(Optional.of(selected));
        when(sectionRepository.findByIdAndPlanContainerId(target.getId(), projectId)).thenReturn(Optional.of(target));
        when(planElementRepository.findByIdAndPlanContainerId(referenceId, projectId))
                .thenReturn(Optional.of(reference));
        when(planElementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
                projectId, target.getId())).thenReturn(List.of(reference));
        AiReplanPlacementProposal placement = new AiReplanPlacementProposal(true, null, "Ohne Phase",
                "am Anfang", target.getId(), target.getTitle(), "nach \"Packen\"", null, referenceId,
                SortMode.MANUAL);
        AiImprovementContent original = new AiImprovementContent(AiImprovementElementType.MILESTONE,
                selected.getTitle(), selected.getDescription(), null, null, null, selected.getDueDate());
        AiImprovementContent proposed = new AiImprovementContent(AiImprovementElementType.MILESTONE,
                selected.getTitle(), selected.getDescription(), null, null, null, LocalDate.of(2026, 10, 18));
        AiImprovementProposal proposal = new AiImprovementProposal(UUID.randomUUID(), projectId, elementId,
                AiImprovementElementType.MILESTONE, selected.getLockVersion(), AiFeedbackType.REPLAN, null,
                "Der Meilenstein gehört in die Transportphase.", placement, original, proposed);

        service.confirm(proposal, userId);

        assertThat(selected.getPlanSection()).isSameAs(target);
        assertThat(selected.getSortOrder()).isGreaterThan(reference.getSortOrder());
        assertThat(selected.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(selected.getTitle()).isEqualTo("Übergabe");
        assertThat(selected.getDescription()).isEqualTo("Alte Beschreibung");
        assertThat(selected.isCompleted()).isTrue();
    }

    @Test
    void confirmingReplanChangesOnlyRelativeOrderInManualMode() {
        project.setSortMode(SortMode.MANUAL);
        PlanSection section = identifiedSection("Vorbereitung", UUID.randomUUID());
        Task selected = task(5);
        selected.setPlanSection(section);
        selected.setSortOrder(100);
        Task reference = task(1);
        UUID referenceId = UUID.randomUUID();
        ReflectionTestUtils.setField(reference, "id", referenceId);
        reference.setTitle("Transporter buchen");
        reference.setPlanSection(section);
        reference.setSortOrder(200);

        stubPlacementApplication(selected, section, reference, List.of(selected, reference));
        AiImprovementProposal proposal = replanProposal(selected,
                placement(selected, section, "nach \"Transporter buchen\"", null, referenceId),
                selected.getStartDate(), selected.getDueDate());

        service.confirm(proposal, userId);

        assertThat(selected.getSortOrder()).isGreaterThan(reference.getSortOrder());
        assertThat(selected.getPlanSection()).isSameAs(section);
        assertThat(selected.getTitle()).isEqualTo("Packen");
        assertThat(selected.getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(selected.getEstimatedHours()).isEqualTo(2);
        assertThat(selected.getStatus()).isEqualTo(TaskStatus.OPEN);
    }

    @Test
    void replanProposesStateCreatingTaskBeforeMilestoneAtSameDueDate() {
        project.setSortMode(SortMode.DATE);
        PlanSection section = identifiedSection("Wohnungsübergabe", UUID.randomUUID());
        Milestone milestone = milestone(1);
        UUID milestoneId = UUID.randomUUID();
        ReflectionTestUtils.setField(milestone, "id", milestoneId);
        milestone.setTitle("Wohnung ist leer und übergabefähig (sauber)");
        milestone.setPlanSection(section);
        milestone.setSortOrder(100);

        Task selected = task(5);
        selected.setTitle("Wohnung für Übergabe reinigen und räumen");
        selected.setDescription("Die Wohnung leeren und sauber für die Übergabe vorbereiten.");
        selected.setPlanSection(section);
        selected.setSortOrder(200);
        milestone.setDueDate(selected.getDueDate());

        when(authorizationService.requireEditableMember(projectId, userId)).thenReturn(membership);
        when(taskRepository.findByIdAndPlanContainerId(elementId, projectId)).thenReturn(Optional.of(selected));
        when(sectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(projectId)).thenReturn(List.of(section));
        when(sectionRepository.findByIdAndPlanContainerId(section.getId(), projectId))
                .thenReturn(Optional.of(section));
        when(planElementRepository.findPlanElements(projectId)).thenReturn(List.of(milestone, selected));
        when(planElementRepository.findByIdAndPlanContainerId(elementId, projectId))
                .thenReturn(Optional.of(selected));
        when(planElementRepository.findByIdAndPlanContainerId(milestoneId, projectId))
                .thenReturn(Optional.of(milestone));
        when(planElementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
                projectId, section.getId())).thenReturn(List.of(milestone, selected));
        when(aiClient.improveElement(any())).thenReturn(new AiImprovementResponse(
                AiImprovementElementType.TASK, selected.getTitle(), selected.getDescription(),
                selected.getPriority(), selected.getEstimatedHours(), selected.getStartDate(), selected.getDueDate(),
                new AiReplanPlacementResponse(true, section.getId().toString(), milestoneId.toString(), null),
                "Die Reinigung stellt den im Meilenstein beschriebenen Zustand erst her."));

        AiImprovementProposal proposal = service.propose(projectId, AiImprovementElementType.TASK, elementId,
                form(AiFeedbackType.REPLAN, null), userId);

        assertThat(proposal.placement().originalPositionLabel())
                .isEqualTo("nach \"Wohnung ist leer und übergabefähig (sauber)\"");
        assertThat(proposal.placement().proposedPositionLabel())
                .isEqualTo("vor \"Wohnung ist leer und übergabefähig (sauber)\"");
        assertThat(proposal.placement().beforeElementId()).isEqualTo(milestoneId);

        ArgumentCaptor<AiImprovementRequest> request = ArgumentCaptor.forClass(AiImprovementRequest.class);
        verify(aiClient).improveElement(request.capture());
        assertThat(request.getValue().plan().sections().getFirst().elements())
                .extracting(AiImprovementPlanContext.Element::elementType,
                        AiImprovementPlanContext.Element::title,
                        AiImprovementPlanContext.Element::position,
                        AiImprovementPlanContext.Element::dueDate)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(AiImprovementElementType.MILESTONE,
                                milestone.getTitle(), 1, milestone.getDueDate()),
                        org.assertj.core.groups.Tuple.tuple(AiImprovementElementType.TASK,
                                selected.getTitle(), 2, selected.getDueDate()));
    }

    @Test
    void replanValidatesUndatedTaskPlacementByLogicalOrderAlthoughDateModeIsConfigured() {
        project.setSortMode(SortMode.DATE);
        project.setStartDate(null);
        project.setEndDate(null);
        project.setDescription("Lerninhalte strukturieren");
        PlanSection section = identifiedSection("Vorbereitung", UUID.randomUUID());
        Milestone reference = milestone(1);
        UUID referenceId = UUID.randomUUID();
        ReflectionTestUtils.setField(reference, "id", referenceId);
        reference.setDueDate(null);
        reference.setPlanSection(section);
        reference.setSortOrder(100);
        Task selected = task(2);
        selected.setStartDate(null);
        selected.setDueDate(null);
        selected.setPlanSection(section);
        selected.setSortOrder(200);

        stubReplanProposal(selected, List.of(section), List.of(reference, selected));
        when(sectionRepository.findByIdAndPlanContainerId(section.getId(), projectId))
                .thenReturn(Optional.of(section));
        when(planElementRepository.findByIdAndPlanContainerId(referenceId, projectId))
                .thenReturn(Optional.of(reference));
        when(planElementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
                projectId, section.getId())).thenReturn(List.of(reference, selected));
        LocalDate proposedDueDate = LocalDate.of(2026, 10, 10);
        when(aiClient.improveElement(any())).thenReturn(new AiImprovementResponse(
                AiImprovementElementType.TASK, selected.getTitle(), selected.getDescription(),
                selected.getPriority(), selected.getEstimatedHours(), null, proposedDueDate,
                new AiReplanPlacementResponse(true, section.getId().toString(),
                        referenceId.toString(), null),
                "Die Aufgabe soll logisch vor dem Meilenstein eingeordnet werden."));

        AiImprovementProposal proposal = service.propose(projectId, AiImprovementElementType.TASK, elementId,
                form(AiFeedbackType.REPLAN, null), userId);

        assertThat(proposal.proposed().startDate()).isNull();
        assertThat(proposal.proposed().dueDate()).isNull();
        assertThat(proposal.placement().beforeElementId()).isEqualTo(referenceId);
        assertThat(proposal.placement().dateOrderingActive()).isFalse();
        ArgumentCaptor<AiImprovementRequest> request = ArgumentCaptor.forClass(AiImprovementRequest.class);
        verify(aiClient).improveElement(request.capture());
        assertThat(request.getValue().plan().sortMode()).isEqualTo(SortMode.MANUAL);

        service.confirm(proposal, userId);

        assertThat(selected.getStartDate()).isNull();
        assertThat(selected.getDueDate()).isNull();
        assertThat(selected.getSortOrder()).isLessThan(reference.getSortOrder());
    }

    @Test
    void replanValidatesUndatedMilestonePlacementByLogicalOrderAlthoughDateModeIsConfigured() {
        project.setSortMode(SortMode.DATE);
        project.setStartDate(null);
        project.setEndDate(null);
        project.setDescription("Lerninhalte strukturieren");
        PlanSection section = identifiedSection("Abschluss", UUID.randomUUID());
        Task reference = distinctTask("Abschluss vorbereiten", section, 100);
        reference.setStartDate(null);
        reference.setDueDate(null);
        Milestone selected = milestone(2);
        selected.setDueDate(null);
        selected.setPlanSection(section);
        selected.setSortOrder(200);

        when(authorizationService.requireEditableMember(projectId, userId)).thenReturn(membership);
        when(milestoneRepository.findByIdAndPlanContainerId(elementId, projectId))
                .thenReturn(Optional.of(selected));
        when(planElementRepository.findPlanElements(projectId)).thenReturn(List.of(reference, selected));
        when(sectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(projectId)).thenReturn(List.of(section));
        when(sectionRepository.findByIdAndPlanContainerId(section.getId(), projectId))
                .thenReturn(Optional.of(section));
        when(planElementRepository.findByIdAndPlanContainerId(elementId, projectId))
                .thenReturn(Optional.of(selected));
        when(planElementRepository.findByIdAndPlanContainerId(reference.getId(), projectId))
                .thenReturn(Optional.of(reference));
        when(planElementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
                projectId, section.getId())).thenReturn(List.of(reference, selected));
        LocalDate proposedDueDate = LocalDate.of(2026, 10, 12);
        when(aiClient.improveElement(any())).thenReturn(new AiImprovementResponse(
                AiImprovementElementType.MILESTONE, selected.getTitle(), selected.getDescription(),
                null, null, null, proposedDueDate,
                new AiReplanPlacementResponse(true, section.getId().toString(), null,
                        reference.getId().toString()),
                "Der Meilenstein folgt logisch auf die vorbereitende Aufgabe."));

        AiImprovementProposal proposal = service.propose(projectId, AiImprovementElementType.MILESTONE, elementId,
                form(AiFeedbackType.REPLAN, null), userId);

        assertThat(proposal.proposed().dueDate()).isNull();
        assertThat(proposal.placement().afterElementId()).isEqualTo(reference.getId());
        assertThat(proposal.placement().dateOrderingActive()).isFalse();
        ArgumentCaptor<AiImprovementRequest> request = ArgumentCaptor.forClass(AiImprovementRequest.class);
        verify(aiClient).improveElement(request.capture());
        assertThat(request.getValue().plan().sortMode()).isEqualTo(SortMode.MANUAL);

        service.confirm(proposal, userId);

        assertThat(selected.getDueDate()).isNull();
        assertThat(selected.getSortOrder()).isGreaterThan(reference.getSortOrder());
    }

    @Test
    void replanMayAddTaskDatesWhenAnotherPlanElementProvidesConcreteTemporalContext() {
        project.setSortMode(SortMode.DATE);
        project.setStartDate(null);
        project.setEndDate(null);
        project.setDescription("Lerninhalte strukturieren");
        PlanSection section = identifiedSection("Prüfungsvorbereitung", UUID.randomUUID());
        Milestone reference = milestone(1);
        UUID referenceId = UUID.randomUUID();
        ReflectionTestUtils.setField(reference, "id", referenceId);
        reference.setPlanSection(section);
        reference.setDueDate(LocalDate.of(2026, 10, 20));
        reference.setSortOrder(200);
        Task selected = task(2);
        selected.setStartDate(null);
        selected.setDueDate(null);
        selected.setPlanSection(section);
        selected.setSortOrder(100);

        stubReplanProposal(selected, List.of(section), List.of(selected, reference));
        when(sectionRepository.findByIdAndPlanContainerId(section.getId(), projectId))
                .thenReturn(Optional.of(section));
        when(planElementRepository.findByIdAndPlanContainerId(referenceId, projectId))
                .thenReturn(Optional.of(reference));
        when(planElementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
                projectId, section.getId())).thenReturn(List.of(selected, reference));
        LocalDate proposedStartDate = LocalDate.of(2026, 10, 17);
        when(aiClient.improveElement(any())).thenReturn(new AiImprovementResponse(
                AiImprovementElementType.TASK, selected.getTitle(), selected.getDescription(),
                selected.getPriority(), selected.getEstimatedHours(), proposedStartDate, reference.getDueDate(),
                new AiReplanPlacementResponse(true, section.getId().toString(),
                        referenceId.toString(), null),
                "Die Vorbereitung endet am bereits terminierten Meilenstein."));

        AiImprovementProposal proposal = service.propose(projectId, AiImprovementElementType.TASK, elementId,
                form(AiFeedbackType.REPLAN, null), userId);

        assertThat(proposal.proposed().startDate()).isEqualTo(proposedStartDate);
        assertThat(proposal.proposed().dueDate()).isEqualTo(reference.getDueDate());
        assertThat(proposal.placement().dateOrderingActive()).isTrue();
    }

    @Test
    void replanMayAddMilestoneDateWhenMixedPlanProvidesConcreteTemporalContext() {
        project.setSortMode(SortMode.DATE);
        project.setStartDate(null);
        project.setEndDate(null);
        project.setDescription("Lerninhalte strukturieren");
        PlanSection section = identifiedSection("Prüfungsvorbereitung", UUID.randomUUID());
        Task reference = distinctTask("Prüfungsstoff wiederholen", section, 100);
        reference.setStartDate(null);
        reference.setDueDate(LocalDate.of(2026, 10, 20));
        Milestone selected = milestone(2);
        selected.setDueDate(null);
        selected.setPlanSection(section);
        selected.setSortOrder(200);

        when(authorizationService.requireEditableMember(projectId, userId)).thenReturn(membership);
        when(milestoneRepository.findByIdAndPlanContainerId(elementId, projectId))
                .thenReturn(Optional.of(selected));
        when(planElementRepository.findPlanElements(projectId)).thenReturn(List.of(reference, selected));
        when(sectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(projectId)).thenReturn(List.of(section));
        when(sectionRepository.findByIdAndPlanContainerId(section.getId(), projectId))
                .thenReturn(Optional.of(section));
        when(planElementRepository.findByIdAndPlanContainerId(elementId, projectId))
                .thenReturn(Optional.of(selected));
        when(planElementRepository.findByIdAndPlanContainerId(reference.getId(), projectId))
                .thenReturn(Optional.of(reference));
        when(planElementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
                projectId, section.getId())).thenReturn(List.of(reference, selected));
        when(aiClient.improveElement(any())).thenReturn(new AiImprovementResponse(
                AiImprovementElementType.MILESTONE, selected.getTitle(), selected.getDescription(),
                null, null, null, reference.getDueDate(),
                new AiReplanPlacementResponse(true, section.getId().toString(), null,
                        reference.getId().toString()),
                "Der Meilenstein folgt auf die bereits terminierte Vorbereitung."));

        AiImprovementProposal proposal = service.propose(projectId, AiImprovementElementType.MILESTONE, elementId,
                form(AiFeedbackType.REPLAN, null), userId);

        assertThat(proposal.proposed().dueDate()).isEqualTo(reference.getDueDate());
        assertThat(proposal.placement().dateOrderingActive()).isTrue();
    }

    @Test
    void replanDescribesMovingFromBeforeToAfterTheSameReference() {
        project.setSortMode(SortMode.DATE);
        PlanSection section = identifiedSection("Transport", UUID.randomUUID());
        Task selected = task(5);
        selected.setPlanSection(section);
        selected.setSortOrder(100);
        Task reference = distinctTask("Transport durchführen", section, 200);

        stubReplanProposal(selected, List.of(section), List.of(selected, reference));
        when(sectionRepository.findByIdAndPlanContainerId(section.getId(), projectId)).thenReturn(Optional.of(section));
        when(planElementRepository.findByIdAndPlanContainerId(reference.getId(), projectId))
                .thenReturn(Optional.of(reference));
        when(planElementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
                projectId, section.getId())).thenReturn(List.of(selected, reference));
        when(aiClient.improveElement(any())).thenReturn(replanResponse(selected,
                new AiReplanPlacementResponse(true, section.getId().toString(), null,
                        reference.getId().toString())));

        AiImprovementProposal proposal = service.propose(projectId, AiImprovementElementType.TASK, elementId,
                form(AiFeedbackType.REPLAN, null), userId);

        assertThat(proposal.placement().originalPositionLabel()).isEqualTo("vor \"Transport durchführen\"");
        assertThat(proposal.placement().proposedPositionLabel()).isEqualTo("nach \"Transport durchführen\"");
        assertThat(proposal.placement().afterElementId()).isEqualTo(reference.getId());
    }

    @Test
    void replanCanSwitchToAnotherConcreteReferenceWithinTheSameSection() {
        project.setSortMode(SortMode.MANUAL);
        PlanSection section = identifiedSection("Vorbereitung", UUID.randomUUID());
        Task previous = distinctTask("Helfer klären", section, 100);
        Task selected = task(5);
        selected.setPlanSection(section);
        selected.setSortOrder(200);
        Task next = distinctTask("Material bereitstellen", section, 300);
        Task targetReference = distinctTask("Transport durchführen", section, 400);

        stubReplanProposal(selected, List.of(section), List.of(previous, selected, next, targetReference));
        when(sectionRepository.findByIdAndPlanContainerId(section.getId(), projectId)).thenReturn(Optional.of(section));
        when(planElementRepository.findByIdAndPlanContainerId(targetReference.getId(), projectId))
                .thenReturn(Optional.of(targetReference));
        when(planElementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
                projectId, section.getId())).thenReturn(List.of(previous, selected, next, targetReference));
        when(aiClient.improveElement(any())).thenReturn(replanResponse(selected,
                new AiReplanPlacementResponse(true, section.getId().toString(),
                        targetReference.getId().toString(), null)));

        AiImprovementProposal proposal = service.propose(projectId, AiImprovementElementType.TASK, elementId,
                form(AiFeedbackType.REPLAN, null), userId);

        assertThat(proposal.placement().originalPositionLabel()).isEqualTo("vor \"Material bereitstellen\"");
        assertThat(proposal.placement().proposedPositionLabel()).isEqualTo("vor \"Transport durchführen\"");
    }

    @Test
    void replanUsesSectionStartAsFallbackWhenTargetGroupHasNoElements() {
        project.setSortMode(SortMode.DATE);
        PlanSection current = identifiedSection("Vorbereitung", UUID.randomUUID());
        PlanSection target = identifiedSection("Transport", UUID.randomUUID());
        Task selected = task(5);
        selected.setPlanSection(current);
        selected.setSortOrder(100);

        when(authorizationService.requireEditableMember(projectId, userId)).thenReturn(membership);
        when(taskRepository.findByIdAndPlanContainerId(elementId, projectId)).thenReturn(Optional.of(selected));
        when(sectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(projectId)).thenReturn(List.of(current, target));
        when(sectionRepository.findByIdAndPlanContainerId(target.getId(), projectId)).thenReturn(Optional.of(target));
        when(planElementRepository.findByIdAndPlanContainerId(elementId, projectId)).thenReturn(Optional.of(selected));
        when(planElementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
                projectId, current.getId())).thenReturn(List.of(selected));
        when(aiClient.improveElement(any())).thenReturn(new AiImprovementResponse(
                AiImprovementElementType.TASK, selected.getTitle(), selected.getDescription(),
                selected.getPriority(), selected.getEstimatedHours(), selected.getStartDate(), selected.getDueDate(),
                new AiReplanPlacementResponse(true, target.getId().toString(), null, null),
                "Die Aufgabe passt in die Transportphase, aber die genaue Reihenfolge ist im Abschnitt selbst zu entscheiden."));

        AiImprovementProposal proposal = service.propose(projectId, AiImprovementElementType.TASK, elementId,
                form(AiFeedbackType.REPLAN, null), userId);

        assertThat(proposal.placement().targetSectionId()).isEqualTo(target.getId());
        assertThat(proposal.placement().proposedPositionLabel()).isEqualTo("an den Anfang der Section");
    }

    @Test
    void replanUsesSectionEndAsFallbackWhenNoReferenceWasProvidedForNonEmptyTarget() {
        project.setSortMode(SortMode.MANUAL);
        PlanSection current = identifiedSection("Vorbereitung", UUID.randomUUID());
        PlanSection target = identifiedSection("Transport", UUID.randomUUID());
        Task selected = task(5);
        selected.setPlanSection(current);
        selected.setSortOrder(100);
        Task existing = distinctTask("Transport durchführen", target, 100);

        stubReplanProposal(selected, List.of(current, target), List.of(selected, existing));
        when(sectionRepository.findByIdAndPlanContainerId(target.getId(), projectId)).thenReturn(Optional.of(target));
        when(planElementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
                projectId, current.getId())).thenReturn(List.of(selected));
        when(planElementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
                projectId, target.getId())).thenReturn(List.of(existing));
        when(aiClient.improveElement(any())).thenReturn(replanResponse(selected,
                new AiReplanPlacementResponse(true, target.getId().toString(), null, null)));

        AiImprovementProposal proposal = service.propose(projectId, AiImprovementElementType.TASK, elementId,
                form(AiFeedbackType.REPLAN, null), userId);

        assertThat(proposal.placement().proposedPositionLabel()).isEqualTo("ans Ende der Section");
    }

    @Test
    void confirmingReplanUsesRelativeOrderAsTieBreakerInDateMode() {
        project.setSortMode(SortMode.DATE);
        PlanSection section = identifiedSection("Vorbereitung", UUID.randomUUID());
        Task selected = task(5);
        selected.setPlanSection(section);
        selected.setSortOrder(200);
        Milestone reference = milestone(1);
        UUID referenceId = UUID.randomUUID();
        ReflectionTestUtils.setField(reference, "id", referenceId);
        reference.setPlanSection(section);
        reference.setDueDate(selected.getDueDate());
        reference.setSortOrder(100);

        stubPlacementApplication(selected, section, reference, List.of(reference, selected));
        AiImprovementProposal proposal = replanProposal(selected,
                placement(selected, section, "vor \"Übergabe\"", referenceId, null),
                selected.getStartDate(), selected.getDueDate());

        service.confirm(proposal, userId);

        assertThat(selected.getSortOrder()).isLessThan(reference.getSortOrder());
        assertThat(selected.getDueDate()).isEqualTo(reference.getDueDate());
    }

    @Test
    void proposeRejectsForeignSectionAndElementIds() {
        Task selected = task(2);
        UUID foreignSectionId = UUID.randomUUID();
        UUID foreignElementId = UUID.randomUUID();
        stubMinimalReplanProposal(selected);
        when(aiClient.improveElement(any())).thenReturn(replanResponse(selected,
                new AiReplanPlacementResponse(true, foreignSectionId.toString(), null, null)));

        assertThatThrownBy(() -> service.propose(projectId, AiImprovementElementType.TASK, elementId,
                form(AiFeedbackType.REPLAN, null), userId)).isInstanceOf(AiOutputValidationException.class);

        when(aiClient.improveElement(any())).thenReturn(replanResponse(selected,
                new AiReplanPlacementResponse(true, null, null, foreignElementId.toString())));
        assertThatThrownBy(() -> service.propose(projectId, AiImprovementElementType.TASK, elementId,
                form(AiFeedbackType.REPLAN, null), userId)).isInstanceOf(AiOutputValidationException.class);

        when(aiClient.improveElement(any())).thenReturn(replanResponse(selected,
                new AiReplanPlacementResponse(true, null, foreignElementId.toString(), null)));
        assertThatThrownBy(() -> service.propose(projectId, AiImprovementElementType.TASK, elementId,
                form(AiFeedbackType.REPLAN, null), userId)).isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void proposeRejectsReferenceInWrongSectionAndSelfReference() {
        Task selected = task(2);
        PlanSection current = identifiedSection("Vorbereitung", UUID.randomUUID());
        PlanSection target = identifiedSection("Transport", UUID.randomUUID());
        selected.setPlanSection(current);
        Task wrongReference = task(1);
        UUID referenceId = UUID.randomUUID();
        ReflectionTestUtils.setField(wrongReference, "id", referenceId);
        wrongReference.setPlanSection(current);
        stubReplanProposal(selected, List.of(current, target), List.of(selected, wrongReference));
        when(planElementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
                projectId, current.getId())).thenReturn(List.of(selected, wrongReference));
        when(sectionRepository.findByIdAndPlanContainerId(target.getId(), projectId)).thenReturn(Optional.of(target));
        when(planElementRepository.findByIdAndPlanContainerId(referenceId, projectId))
                .thenReturn(Optional.of(wrongReference));
        when(aiClient.improveElement(any())).thenReturn(replanResponse(selected,
                new AiReplanPlacementResponse(true, target.getId().toString(), referenceId.toString(), null)));

        assertThatThrownBy(() -> service.propose(projectId, AiImprovementElementType.TASK, elementId,
                form(AiFeedbackType.REPLAN, null), userId)).isInstanceOf(AiOutputValidationException.class);

        when(aiClient.improveElement(any())).thenReturn(replanResponse(selected,
                new AiReplanPlacementResponse(true, current.getId().toString(), elementId.toString(), null)));
        when(sectionRepository.findByIdAndPlanContainerId(current.getId(), projectId)).thenReturn(Optional.of(current));
        assertThatThrownBy(() -> service.propose(projectId, AiImprovementElementType.TASK, elementId,
                form(AiFeedbackType.REPLAN, null), userId)).isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void confirmingReplanRejectsTargetOrReferenceThatNoLongerExists() {
        Task selected = task(5);
        UUID targetId = UUID.randomUUID();
        AiReplanPlacementProposal missingTarget = new AiReplanPlacementProposal(true, null, "Ohne Phase",
                "am Anfang", targetId, "Transport", "innerhalb der neuen Section", null, null, project.getSortMode());
        when(taskRepository.findByIdAndPlanContainerId(elementId, projectId)).thenReturn(Optional.of(selected));
        when(sectionRepository.findByIdAndPlanContainerId(targetId, projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(replanProposal(selected, missingTarget,
                selected.getStartDate(), selected.getDueDate()), userId)).isInstanceOf(ConflictException.class);

        UUID referenceId = UUID.randomUUID();
        AiReplanPlacementProposal missingReference = new AiReplanPlacementProposal(true, null, "Ohne Phase",
                "am Anfang", null, "Ohne Phase", "vor \"Gelöscht\"", referenceId, null, project.getSortMode());
        when(planElementRepository.findByIdAndPlanContainerId(referenceId, projectId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.confirm(replanProposal(selected, missingReference,
                selected.getStartDate(), selected.getDueDate()), userId)).isInstanceOf(ConflictException.class);

        PlanSection otherSection = identifiedSection("Andere Phase", UUID.randomUUID());
        Task movedReference = task(1);
        ReflectionTestUtils.setField(movedReference, "id", referenceId);
        movedReference.setPlanSection(otherSection);
        when(planElementRepository.findByIdAndPlanContainerId(referenceId, projectId))
                .thenReturn(Optional.of(movedReference));
        assertThatThrownBy(() -> service.confirm(replanProposal(selected, missingReference,
                selected.getStartDate(), selected.getDueDate()), userId)).isInstanceOf(ConflictException.class);
    }

    @Test
    void confirmingReplanRejectsChangedSortMode() {
        Task selected = task(5);
        AiReplanPlacementProposal placement = new AiReplanPlacementProposal(false, null, "Ohne Phase",
                "am Anfang", null, "Ohne Phase", "Unverändert", null, null, SortMode.DATE);
        project.setSortMode(SortMode.MANUAL);

        assertThatThrownBy(() -> service.confirm(replanProposal(selected, placement,
                selected.getStartDate(), selected.getDueDate()), userId)).isInstanceOf(ConflictException.class);
        verify(taskRepository, never()).flush();
    }

    @Test
    void confirmingEffortEstimateChangesOnlyEffort() {
        Task task = task(3);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setSortOrder(400);
        when(taskRepository.findByIdAndPlanContainerId(elementId, projectId)).thenReturn(Optional.of(task));
        AiImprovementProposal proposal = proposal(AiImprovementElementType.TASK, 3,
                AiFeedbackType.ESTIMATE_EFFORT, null,
                new AiImprovementResponse(AiImprovementElementType.TASK, "Packen", null,
                        TaskPriority.MEDIUM, 6, LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 12),
                        "Sechs Stunden entsprechen dem beschriebenen Umfang."));

        service.confirm(proposal, userId);

        assertThat(task.getEstimatedHours()).isEqualTo(6);
        assertThat(task.getTitle()).isEqualTo("Packen");
        assertThat(task.getDescription()).isNull();
        assertThat(task.getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(task.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 11));
        assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(task.getSortOrder()).isEqualTo(400);
        assertThat(task.getOrigin()).isEqualTo(ElementOrigin.AI_MODIFIED);
    }

    @Test
    void staleProposalIsRejectedBeforeAnyFieldChanges() {
        Milestone milestone = milestone(8);
        when(milestoneRepository.findByIdAndPlanContainerId(elementId, projectId))
                .thenReturn(Optional.of(milestone));
        AiImprovementProposal proposal = proposal(AiImprovementElementType.MILESTONE, 7,
                new AiImprovementResponse(AiImprovementElementType.MILESTONE, "Neu", null,
                        null, null, null, LocalDate.of(2026, 10, 20)));

        assertThatThrownBy(() -> service.confirm(proposal, userId)).isInstanceOf(ConflictException.class);
        assertThat(milestone.getTitle()).isEqualTo("Übergabe");
        verify(milestoneRepository, never()).flush();
    }

    @Test
    void unauthorizedAccessStopsBeforeElementOrProviderAccess() {
        when(authorizationService.requireEditableMember(projectId, userId))
                .thenThrow(new ResourceNotFoundException("nicht gefunden"));
        assertThatThrownBy(() -> service.propose(projectId, AiImprovementElementType.TASK, elementId,
                form(AiFeedbackType.IMPROVE, null), userId)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(aiClient, sectionRepository, taskRepository, milestoneRepository);
    }

    private void stubMinimalReplanProposal(Task selected) {
        stubReplanProposal(selected, List.of(), List.of(selected));
        when(planElementRepository.findAllByPlanContainerIdAndPlanSectionIsNullOrderBySortOrderAsc(projectId))
                .thenReturn(List.of(selected));
    }

    private void stubReplanProposal(
            Task selected,
            List<PlanSection> sections,
            List<? extends de.melinadanhier.projectflow.planelement.model.PlanElement> elements) {
        when(authorizationService.requireEditableMember(projectId, userId)).thenReturn(membership);
        when(taskRepository.findByIdAndPlanContainerId(elementId, projectId)).thenReturn(Optional.of(selected));
        when(planElementRepository.findPlanElements(projectId)).thenReturn(
                new java.util.ArrayList<de.melinadanhier.projectflow.planelement.model.PlanElement>(elements));
        when(sectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(projectId)).thenReturn(sections);
        when(planElementRepository.findByIdAndPlanContainerId(elementId, projectId)).thenReturn(Optional.of(selected));
    }

    private void stubPlacementApplication(
            Task selected, PlanSection target, de.melinadanhier.projectflow.planelement.model.PlanElement reference,
            List<de.melinadanhier.projectflow.planelement.model.PlanElement> siblings) {
        when(taskRepository.findByIdAndPlanContainerId(elementId, projectId)).thenReturn(Optional.of(selected));
        when(sectionRepository.findByIdAndPlanContainerId(target.getId(), projectId)).thenReturn(Optional.of(target));
        when(planElementRepository.findByIdAndPlanContainerId(reference.getId(), projectId))
                .thenReturn(Optional.of(reference));
        when(planElementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
                projectId, target.getId())).thenReturn(siblings);
    }

    private PlanSection identifiedSection(String title, UUID id) {
        PlanSection section = section(title, null, 1);
        ReflectionTestUtils.setField(section, "id", id);
        return section;
    }

    private AiReplanPlacementProposal placement(
            Task selected, PlanSection target, String proposedLabel, UUID beforeId, UUID afterId) {
        PlanSection original = selected.getPlanSection();
        return new AiReplanPlacementProposal(true,
                original == null ? null : original.getId(), original == null ? "Ohne Phase" : original.getTitle(),
                "am Anfang", target == null ? null : target.getId(), target == null ? "Ohne Phase" : target.getTitle(),
                proposedLabel, beforeId, afterId, project.getSortMode());
    }

    private AiImprovementProposal replanProposal(
            Task selected, AiReplanPlacementProposal placement, LocalDate start, LocalDate due) {
        AiImprovementContent original = new AiImprovementContent(AiImprovementElementType.TASK,
                selected.getTitle(), selected.getDescription(), selected.getPriority(), selected.getEstimatedHours(),
                selected.getStartDate(), selected.getDueDate());
        AiImprovementContent proposed = new AiImprovementContent(AiImprovementElementType.TASK,
                selected.getTitle(), selected.getDescription(), selected.getPriority(), selected.getEstimatedHours(),
                start, due);
        return new AiImprovementProposal(UUID.randomUUID(), projectId, elementId,
                AiImprovementElementType.TASK, selected.getLockVersion(), AiFeedbackType.REPLAN, null,
                "Die Planung passt zum aktuellen Ablauf.", placement, original, proposed);
    }

    private AiImprovementResponse replanResponse(Task selected, AiReplanPlacementResponse placement) {
        return new AiImprovementResponse(AiImprovementElementType.TASK, selected.getTitle(),
                selected.getDescription(), selected.getPriority(), selected.getEstimatedHours(),
                selected.getStartDate(), selected.getDueDate(), placement,
                "Die Planung passt zum aktuellen Ablauf.");
    }

    private AiImprovementForm form(AiFeedbackType type, String comment) {
        AiImprovementForm form = new AiImprovementForm();
        form.setFeedbackType(type);
        form.setComment(comment);
        return form;
    }

    private PlanSection section(String title, String description, long version) {
        PlanSection section = new PlanSection();
        section.setTitle(title);
        section.setDescription(description);
        section.setOrigin(ElementOrigin.USER);
        ReflectionTestUtils.setField(section, "id", elementId);
        ReflectionTestUtils.setField(section, "lockVersion", version);
        return section;
    }

    private Task task(long version) {
        Task task = new Task();
        task.setTitle("Packen");
        task.setOrigin(ElementOrigin.USER);
        task.setPriority(TaskPriority.MEDIUM);
        task.setEstimatedHours(2);
        task.setStartDate(LocalDate.of(2026, 9, 11));
        task.setDueDate(LocalDate.of(2026, 9, 12));
        ReflectionTestUtils.setField(task, "id", elementId);
        ReflectionTestUtils.setField(task, "lockVersion", version);
        return task;
    }

    private Task distinctTask(String title, PlanSection section, int sortOrder) {
        Task task = task(1);
        ReflectionTestUtils.setField(task, "id", UUID.randomUUID());
        task.setTitle(title);
        task.setPlanSection(section);
        task.setSortOrder(sortOrder);
        return task;
    }

    private Milestone milestone(long version) {
        Milestone milestone = new Milestone();
        milestone.setTitle("Übergabe");
        milestone.setDescription("Alte Beschreibung");
        milestone.setOrigin(ElementOrigin.TEMPLATE);
        milestone.setDueDate(LocalDate.of(2026, 10, 20));
        ReflectionTestUtils.setField(milestone, "id", elementId);
        ReflectionTestUtils.setField(milestone, "lockVersion", version);
        return milestone;
    }

    private AiImprovementResponse response(AiImprovementElementType type, String title, String description) {
        return new AiImprovementResponse(type, title, description, null, null, null, null);
    }

    private AiImprovementProposal proposal(
            AiImprovementElementType type, long version, AiImprovementResponse response) {
        return proposal(type, version, null, response);
    }

    private AiImprovementProposal proposal(
            AiImprovementElementType type, long version, String comment, AiImprovementResponse response) {
        return proposal(type, version, AiFeedbackType.IMPROVE, comment, response);
    }

    private AiImprovementProposal proposal(
            AiImprovementElementType type, long version, AiFeedbackType action,
            String comment, AiImprovementResponse response) {
        return new AiImprovementProposal(UUID.randomUUID(), projectId, elementId, type, version,
                action, comment, response.explanation(),
                action == AiFeedbackType.REPLAN
                        ? new AiReplanPlacementProposal(false, null, "Ohne Phase", "am Anfang",
                                null, "Ohne Phase", "Unverändert", null, null, project.getSortMode())
                        : null,
                originalContent(type), response.toContent());
    }

    private AiImprovementContent originalContent(AiImprovementElementType type) {
        return switch (type) {
            case SECTION -> new AiImprovementContent(type, "Phase", null, null, null, null, null);
            case TASK -> new AiImprovementContent(type, "Packen", null, TaskPriority.MEDIUM, 2,
                    LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 12));
            case MILESTONE -> new AiImprovementContent(type, "Übergabe", "Alte Beschreibung", null, null, null,
                    LocalDate.of(2026, 10, 20));
        };
    }
}
