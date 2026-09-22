package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.model.generation.*;
import de.melinadanhier.projectflow.ai.validation.generation.GenerationResponseValidator;
import de.melinadanhier.projectflow.ai.validation.generation.GenerationValidationCode;
import de.melinadanhier.projectflow.ai.validation.generation.GenerationValidationResult;
import de.melinadanhier.projectflow.draft.mapper.DraftMapper;
import de.melinadanhier.projectflow.draft.mapper.GeneratedPlanDraftMapper;
import de.melinadanhier.projectflow.draft.model.DraftPlan;
import de.melinadanhier.projectflow.draft.model.DraftPlanStatus;
import de.melinadanhier.projectflow.draft.model.DraftTask;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressionstest für kurzzeitige Projekte (z. B. 2-Stunden-Plan),
 * deren Gesamtaufwand höchstens 120 Minuten beträgt.
 */
class ShortPlanEffortRegressionTest {

    private GenerationResponseValidator validator;
    private GeneratedPlanDraftMapper draftMapper;

    @BeforeEach
    void setUp() {
        validator = new GenerationResponseValidator(
                Validation.buildDefaultValidatorFactory().getValidator()
        );
        draftMapper = new GeneratedPlanDraftMapper();
    }

    @Test
    void twoHourPlanWithMultipleTasksPassesValidationAndCalculatesTotalEffort() {
        // Plan mit 3 Aufgaben: 30 Min., 45 Min., 45 Min. -> Summe = 120 Min. (2 Stunden)
        GeneratedTask task1 = new GeneratedTask(
                "task-1", "Vorbereitung & Material bereitstellen",
                "Arbeitsplatz einrichten und Werkzeug bereitlegen",
                30, null, null, 1, List.of(), TaskPriority.MEDIUM
        );
        GeneratedTask task2 = new GeneratedTask(
                "task-2", "Hauptarbeitsschritt durchführen",
                "Schrittweise Umsetzung der Kernaufgabe",
                45, null, null, 2, List.of("task-1"), TaskPriority.HIGH
        );
        GeneratedTask task3 = new GeneratedTask(
                "task-3", "Qualitätsprüfung & Aufräumen",
                "Ergebnis begutachten und Arbeitsbereich säubern",
                45, null, null, 3, List.of("task-2"), TaskPriority.LOW
        );

        GeneratedSection section = new GeneratedSection(
                "sec-1", "Umsetzung", "Gesamter Ablauf in 2 Stunden",
                1, List.of(task1, task2, task3), List.of()
        );

        GeneratedPlanResponse response = new GeneratedPlanResponse(List.of(section));

        AiGenerationRequest request = new AiGenerationRequest(
                new AiWizardSnapshot(
                        "2-Stunden-Mini-Projekt",
                        "Schnelle Erledigung in maximal 2 Stunden",
                        LocalDate.of(2026, 9, 22),
                        LocalDate.of(2026, 9, 22),
                        CollaborationMode.INDIVIDUAL,
                        ProjectCategory.OTHER,
                        null,
                        null,
                        null,
                        "Zeitbudget: 2 Stunden (120 Minuten)",
                        null,
                        null
                ),
                List.of()
        );

        // 1. Validierung: muss ohne TASK_EFFORT_INVALID bestehen
        GenerationValidationResult result = validator.validate(response, request);
        assertThat(result.isValid()).isTrue();
        assertThat(result.issues()).noneMatch(i -> i.code() == GenerationValidationCode.TASK_EFFORT_INVALID);

        // 2. Mapping in DraftPlan
        var mappedDraft = draftMapper.map(response);
        assertThat(mappedDraft.elements()).hasSize(3);

        DraftPlan draftPlan = new DraftPlan();
        draftPlan.setStatus(DraftPlanStatus.READY_FOR_REVIEW);
        Project project = new Project();
        project.setTitle("2-Stunden-Mini-Projekt");
        project.setCreationType(CreationType.AI);
        project.setLocation(ProjectLocation.DRAFT);
        project.attachDraft(draftPlan);

        for (var sec : mappedDraft.sections()) {
            draftPlan.addSection(sec);
        }
        for (var el : mappedDraft.elements()) {
            draftPlan.addElement(el);
        }

        // 3. Review DTO Prüfung: Summe = 120 Min, formatiert als "2 Std."
        DraftMapper mapper = new de.melinadanhier.projectflow.draft.mapper.DraftMapperImpl();
        var reviewDto = mapper.toReviewDto(draftPlan);
        int totalEstimatedMinutes = draftPlan.getElements().stream()
                .filter(DraftTask.class::isInstance)
                .map(DraftTask.class::cast)
                .map(DraftTask::getEstimatedMinutes)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        reviewDto.setTotalEstimatedMinutes(totalEstimatedMinutes);

        assertThat(reviewDto.getTotalEstimatedMinutes()).isEqualTo(120);
        assertThat(reviewDto.getFormattedTotalEffort()).isEqualTo("2 Std.");

        // 4. Einzelne Aufgaben-Aufwände formatiert
        List<DraftTask> tasks = mappedDraft.elements().stream()
                .filter(DraftTask.class::isInstance)
                .map(DraftTask.class::cast)
                .toList();

        assertThat(tasks.get(0).getEstimatedMinutes()).isEqualTo(30);
        assertThat(de.melinadanhier.projectflow.common.util.EffortFormatter.formatMinutes(tasks.get(0).getEstimatedMinutes()))
                .isEqualTo("30 Min.");

        assertThat(tasks.get(1).getEstimatedMinutes()).isEqualTo(45);
        assertThat(de.melinadanhier.projectflow.common.util.EffortFormatter.formatMinutes(tasks.get(1).getEstimatedMinutes()))
                .isEqualTo("45 Min.");

        assertThat(tasks.get(2).getEstimatedMinutes()).isEqualTo(45);
        assertThat(de.melinadanhier.projectflow.common.util.EffortFormatter.formatMinutes(tasks.get(2).getEstimatedMinutes()))
                .isEqualTo("45 Min.");
    }

    @Test
    void rejectsInvalidEstimatedMinutesExceedingLimitOrNegative() {
        GeneratedTask invalidTask = new GeneratedTask(
                "task-1", "Ungültiger Aufwand", null,
                -10, null, null, 1, List.of(), TaskPriority.MEDIUM
        );
        GeneratedSection section = new GeneratedSection(
                "sec-1", "Bereich", null,
                1, List.of(invalidTask), List.of()
        );
        GeneratedPlanResponse response = new GeneratedPlanResponse(List.of(section));

        AiGenerationRequest request = new AiGenerationRequest(
                new AiWizardSnapshot(
                        "Test", null, null, null,
                        CollaborationMode.INDIVIDUAL, ProjectCategory.OTHER, null,
                        null, null, null, null, null
                ),
                List.of()
        );

        GenerationValidationResult result = validator.validate(response, request);
        assertThat(result.isValid()).isFalse();
        assertThat(result.issues()).anyMatch(i -> i.code() == GenerationValidationCode.TASK_EFFORT_INVALID);
    }
}
