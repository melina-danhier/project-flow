package de.melinadanhier.projectflow.draft;

import de.melinadanhier.projectflow.ai.validation.generation.GenerationResponseValidator;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.draft.model.DraftPlan;
import de.melinadanhier.projectflow.draft.model.DraftSection;
import de.melinadanhier.projectflow.draft.model.DraftTask;
import de.melinadanhier.projectflow.draft.service.DraftValidationService;
import de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DraftValidationServiceTest {

    private DraftValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new DraftValidationService(
                mock(GenerationResponseValidator.class),
                Validation.buildDefaultValidatorFactory().getValidator(),
                mock(AiPlanGenerationWorkflowRepository.class),
                mock(AiWorkflowPayloadCodec.class));
    }

    @Test
    void rejectsPrerequisiteFromAnotherDraftEvenWhenRelationshipWasCreatedBeforeAttachment() {
        DraftTask successor = task("Nachfolger");
        DraftTask foreignPrerequisite = task("Fremde Voraussetzung");
        successor.addPrerequisite(foreignPrerequisite);

        DraftPlan first = draft(project());
        DraftPlan second = draft(project());
        first.addElement(successor);
        second.addElement(foreignPrerequisite);

        assertThatThrownBy(() -> validationService.validateForApplication(first))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void preventsAddingKnownCrossDraftPrerequisiteImmediately() {
        DraftTask successor = task("Nachfolger");
        DraftTask foreignPrerequisite = task("Fremde Voraussetzung");
        draft(project()).addElement(successor);
        draft(project()).addElement(foreignPrerequisite);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> successor.addPrerequisite(foreignPrerequisite))
                .withMessageContaining("anderen Entwurf");
    }

    @Test
    void preventsAssigningElementToSectionOfAnotherDraft() {
        DraftPlan first = draft(project());
        DraftPlan second = draft(project());
        DraftTask task = task("Aufgabe");
        DraftSection foreignSection = section("Fremder Bereich");
        first.addElement(task);
        second.addSection(foreignSection);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> foreignSection.addElement(task))
                .withMessageContaining("unterschiedlichen Entwürfen");
    }

    @Test
    void rejectsTaskStartAfterProjectEnd() {
        Project project = project();
        project.setStartDate(LocalDate.of(2026, 9, 1));
        project.setEndDate(LocalDate.of(2026, 9, 30));
        DraftPlan draft = draft(project);
        DraftTask task = task("Zu später Start");
        task.setStartDate(LocalDate.of(2026, 10, 1));
        draft.addElement(task);

        assertThatThrownBy(() -> validationService.validateForApplication(draft))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void acceptsBoundaryDatesAndOptionalTaskDates() {
        Project project = project();
        project.setStartDate(LocalDate.of(2026, 9, 1));
        project.setEndDate(LocalDate.of(2026, 9, 30));
        DraftPlan draft = draft(project);
        DraftTask bounded = task("Mit Zeitraum");
        bounded.setStartDate(project.getStartDate());
        bounded.setDueDate(project.getEndDate());
        draft.addElement(bounded);
        draft.addElement(task("Ohne Zeitraum"));

        assertThatNoException().isThrownBy(() -> validationService.validateForApplication(draft));
    }

    private DraftPlan draft(Project project) {
        DraftPlan draft = new DraftPlan();
        draft.setProject(project);
        return draft;
    }

    private Project project() {
        Project project = new Project();
        project.setTitle("Projekt");
        return project;
    }

    private DraftTask task(String title) {
        DraftTask task = new DraftTask();
        task.setTitle(title);
        return task;
    }

    private DraftSection section(String title) {
        DraftSection section = new DraftSection();
        section.setTitle(title);
        return section;
    }
}
