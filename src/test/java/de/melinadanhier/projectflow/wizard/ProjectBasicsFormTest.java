package de.melinadanhier.projectflow.wizard;

import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import de.melinadanhier.projectflow.wizard.dto.ProjectBasicsForm;
import de.melinadanhier.projectflow.wizard.dto.ProjectTimeFrameType;
import de.melinadanhier.projectflow.wizard.model.ProjectWizardState;
import de.melinadanhier.projectflow.wizard.service.ProjectTimeFrameCalculator;
import de.melinadanhier.projectflow.wizard.service.ProjectWizardService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectBasicsFormTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaultsToOtherAndRejectsANullCategory() {
        ProjectBasicsForm form = new ProjectBasicsForm();

        assertThat(form.getCategory()).isEqualTo(TemplateCategory.OTHER);
        assertThat(form.getTimeFrameType()).isEqualTo(ProjectTimeFrameType.NONE);

        form.setTitle("Testprojekt");
        form.setCategory(null);

        assertThat(violatedProperties(form)).contains("category");
    }

    @Test
    void normalCategoryAllowsAnOptionalSubcategory() {
        ProjectBasicsForm withoutSubcategory = validForm();
        ProjectBasicsForm withSubcategory = validForm();
        withSubcategory.setSubcategory("Bachelorarbeit");

        assertThat(validator.validate(withoutSubcategory)).isEmpty();
        assertThat(validator.validate(withSubcategory)).isEmpty();
    }

    @Test
    void otherRequiresAProjectTypeDescription() {
        ProjectBasicsForm form = validForm();
        form.setCategory(TemplateCategory.OTHER);

        form.setOtherProjectTypeDescription("   ");
        assertThat(violatedProperties(form)).contains("otherProjectTypeDescription");

        form.setOtherProjectTypeDescription("Organisation eines privaten Flohmarkts");
        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void acceptsStartAndEnd() {
        ProjectBasicsForm form = validForm();
        form.setTimeFrameType(ProjectTimeFrameType.START_AND_END);
        form.setStartDate(LocalDate.of(2026, 9, 1));
        form.setEndDate(LocalDate.of(2026, 9, 20));

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void acceptsStartAndDuration() {
        ProjectBasicsForm form = validForm();
        form.setTimeFrameType(ProjectTimeFrameType.START_AND_DURATION);
        form.setStartDate(LocalDate.of(2026, 9, 1));
        form.setDurationDays(20);

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void acceptsEndAndDuration() {
        ProjectBasicsForm form = validForm();
        form.setTimeFrameType(ProjectTimeFrameType.END_AND_DURATION);
        form.setEndDate(LocalDate.of(2026, 9, 20));
        form.setDurationDays(20);

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void acceptsNoTimeInformation() {
        assertThat(validator.validate(validForm())).isEmpty();
    }

    @Test
    void rejectsDurationWithoutStartOrEnd() {
        ProjectBasicsForm form = validForm();
        form.setDurationDays(20);

        assertThat(violatedProperties(form)).contains("timeFrameType");
    }

    @Test
    void rejectsAnEndDateBeforeTheStartDateAtTheEndDateField() {
        ProjectBasicsForm form = validForm();
        form.setTimeFrameType(ProjectTimeFrameType.START_AND_END);
        form.setStartDate(LocalDate.of(2026, 9, 20));
        form.setEndDate(LocalDate.of(2026, 9, 1));

        assertThat(violatedProperties(form)).contains("endDate");
    }

    @Test
    void rejectsANonPositiveDurationAtTheDurationField() {
        ProjectBasicsForm form = validForm();
        form.setTimeFrameType(ProjectTimeFrameType.START_AND_DURATION);
        form.setStartDate(LocalDate.of(2026, 9, 1));
        form.setDurationDays(0);

        assertThat(violatedProperties(form)).contains("durationDays");
    }

    @Test
    void rejectsContradictoryTimeInformationAtTheSelectedMode() {
        ProjectBasicsForm form = validForm();
        form.setTimeFrameType(ProjectTimeFrameType.START_AND_DURATION);
        form.setStartDate(LocalDate.of(2026, 9, 1));
        form.setEndDate(LocalDate.of(2026, 9, 20));
        form.setDurationDays(20);

        assertThat(violatedProperties(form)).contains("timeFrameType");
    }

    @Test
    void keepsBasicsAndTimeSelectionInTheExistingSessionState() {
        UUID userId = UUID.randomUUID();
        ProjectWizardState state = new ProjectWizardState();
        state.setUserId(userId);
        state.setCreationType(CreationType.AI);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(ProjectWizardService.SESSION_ATTRIBUTE, state);

        ProjectBasicsForm form = validForm();
        form.setTitle("  Präsentation vorbereiten  ");
        form.setSubcategory(" Präsentation ");
        form.setTimeFrameType(ProjectTimeFrameType.START_AND_DURATION);
        form.setStartDate(LocalDate.of(2026, 9, 1));
        form.setDurationDays(3);

        ProjectWizardService service = new ProjectWizardService(new ProjectTimeFrameCalculator());
        service.saveBasics(form, userId, session);
        ProjectWizardState restored = service.requireOwned(userId, session);
        ProjectBasicsForm restoredForm = ProjectBasicsForm.from(restored);

        assertThat(restored.getTitle()).isEqualTo("Präsentation vorbereiten");
        assertThat(restored.getCategory()).isEqualTo(TemplateCategory.EDUCATION);
        assertThat(restored.getProjectType()).isEqualTo("Präsentation");
        assertThat(restored.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(restored.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(restoredForm.getTimeFrameType()).isEqualTo(ProjectTimeFrameType.START_AND_DURATION);
        assertThat(restoredForm.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(restoredForm.getEndDate()).isNull();
        assertThat(restoredForm.getDurationDays()).isEqualTo(3);
    }

    private ProjectBasicsForm validForm() {
        ProjectBasicsForm form = new ProjectBasicsForm();
        form.setTitle("Testprojekt");
        form.setCategory(TemplateCategory.EDUCATION);
        form.setCollaborationMode(CollaborationMode.INDIVIDUAL);
        return form;
    }

    private Set<String> violatedProperties(ProjectBasicsForm form) {
        return validator.validate(form).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }
}
