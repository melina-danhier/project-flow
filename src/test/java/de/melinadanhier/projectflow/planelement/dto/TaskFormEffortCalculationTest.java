package de.melinadanhier.projectflow.planelement.dto;

import de.melinadanhier.projectflow.draft.dto.editing.DraftTaskForm;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskFormEffortCalculationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("2 Std. + 30 Min. ergeben 150 Minuten")
    void twoHoursThirtyMinutesYields150Minutes() {
        TaskForm form = new TaskForm();
        form.setEffortHours(2);
        form.setEffortMinutes(30);

        assertThat(form.getEstimatedMinutes()).isEqualTo(150);
    }

    @Test
    @DisplayName("0 Std. + 90 Min. ergeben 90 Minuten")
    void zeroHoursNinetyMinutesYields90Minutes() {
        TaskForm form = new TaskForm();
        form.setEffortHours(0);
        form.setEffortMinutes(90);

        assertThat(form.getEstimatedMinutes()).isEqualTo(90);
    }

    @Test
    @DisplayName("1 Std. + 90 Min. ergeben 150 Minuten")
    void oneHourNinetyMinutesYields150Minutes() {
        TaskForm form = new TaskForm();
        form.setEffortHours(1);
        form.setEffortMinutes(90);

        assertThat(form.getEstimatedMinutes()).isEqualTo(150);
    }

    @Test
    @DisplayName("150 Minuten laden wird zu 2 Std. und 30 Min. zerlegt")
    void loading150MinutesYields2HoursAnd30Minutes() {
        TaskForm form = new TaskForm();
        form.setEstimatedMinutes(150);

        assertThat(form.getEffortHours()).isEqualTo(2);
        assertThat(form.getEffortMinutes()).isEqualTo(30);
        assertThat(form.getEstimatedMinutes()).isEqualTo(150);
    }

    @Test
    @DisplayName("90 Minuten laden wird zu 1 Std. und 30 Min. zerlegt")
    void loading90MinutesYields1HourAnd30Minutes() {
        TaskForm form = new TaskForm();
        form.setEstimatedMinutes(90);

        assertThat(form.getEffortHours()).isEqualTo(1);
        assertThat(form.getEffortMinutes()).isEqualTo(30);
        assertThat(form.getEstimatedMinutes()).isEqualTo(90);
    }

    @Test
    @DisplayName("Beide Felder leer ergeben null")
    void emptyHoursAndMinutesYieldsNull() {
        TaskForm form = new TaskForm();
        form.setEffortHours(null);
        form.setEffortMinutes(null);

        assertThat(form.getEstimatedMinutes()).isNull();
    }

    @Test
    @DisplayName("0 Std. + 0 Min. ergeben null (kein Aufwand)")
    void zeroHoursAndZeroMinutesYieldsNull() {
        TaskForm form = new TaskForm();
        form.setEffortHours(0);
        form.setEffortMinutes(0);

        assertThat(form.getEstimatedMinutes()).isNull();
    }

    @Test
    @DisplayName("setEstimatedMinutes mit null oder 0 setzt beide Felder auf null")
    void loadingNullOrZeroMinutesResetsBothFieldsToNull() {
        TaskForm form = new TaskForm();
        form.setEstimatedMinutes(120);
        assertThat(form.getEffortHours()).isEqualTo(2);

        form.setEstimatedMinutes(null);
        assertThat(form.getEffortHours()).isNull();
        assertThat(form.getEffortMinutes()).isNull();
        assertThat(form.getEstimatedMinutes()).isNull();

        form.setEstimatedMinutes(0);
        assertThat(form.getEffortHours()).isNull();
        assertThat(form.getEffortMinutes()).isNull();
        assertThat(form.getEstimatedMinutes()).isNull();
    }

    @Test
    @DisplayName("DraftTaskForm verhält sich bei Berechnung und Zerlegung identisch")
    void draftTaskFormCalculatesAndDecomposesIdentically() {
        DraftTaskForm form = new DraftTaskForm();

        form.setEffortHours(1);
        form.setEffortMinutes(45);
        assertThat(form.getEstimatedMinutes()).isEqualTo(105);

        form.setEstimatedMinutes(195);
        assertThat(form.getEffortHours()).isEqualTo(3);
        assertThat(form.getEffortMinutes()).isEqualTo(15);
        assertThat(form.getEstimatedMinutes()).isEqualTo(195);

        form.setEffortHours(0);
        form.setEffortMinutes(0);
        assertThat(form.getEstimatedMinutes()).isNull();

        form.setEstimatedMinutes(null);
        assertThat(form.getEffortHours()).isNull();
        assertThat(form.getEffortMinutes()).isNull();
        assertThat(form.getEstimatedMinutes()).isNull();
    }

    @Test
    @DisplayName("Validierung weist negative Werte und Überschreitung des Maximalaufwands ab")
    void validationRejectsNegativeValuesAndExcessiveEffort() {
        TaskForm validForm = new TaskForm();
        validForm.setTitle("Aufgabe");
        validForm.setPriority(TaskPriority.MEDIUM);
        validForm.setEffortHours(5);
        validForm.setEffortMinutes(45);
        assertThat(validator.validate(validForm)).isEmpty();

        TaskForm negativeHours = new TaskForm();
        negativeHours.setTitle("Aufgabe");
        negativeHours.setPriority(TaskPriority.MEDIUM);
        negativeHours.setEffortHours(-1);
        assertThat(validator.validate(negativeHours))
                .anyMatch(v -> v.getPropertyPath().toString().equals("effortHours"));

        TaskForm negativeMinutes = new TaskForm();
        negativeMinutes.setTitle("Aufgabe");
        negativeMinutes.setPriority(TaskPriority.MEDIUM);
        negativeMinutes.setEffortMinutes(-5);
        assertThat(validator.validate(negativeMinutes))
                .anyMatch(v -> v.getPropertyPath().toString().equals("effortMinutes"));

        TaskForm excessiveEffort = new TaskForm();
        excessiveEffort.setTitle("Aufgabe");
        excessiveEffort.setPriority(TaskPriority.MEDIUM);
        excessiveEffort.setEffortHours(10_001); // 600.060 Minuten > 600.000
        assertThat(validator.validate(excessiveEffort))
                .anyMatch(v -> v.getPropertyPath().toString().equals("effortValid"));
    }
}
