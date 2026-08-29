package de.melinadanhier.projectflow.wizard;

import de.melinadanhier.projectflow.plancontainer.project.model.ProjectSubCategory;
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
        withSubcategory.setSubcategory(ProjectSubCategory.THESIS);

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
        form.setSubcategory(ProjectSubCategory.PRESENTATION_OR_REPORT);
        form.setTimeFrameType(ProjectTimeFrameType.START_AND_DURATION);
        form.setStartDate(LocalDate.of(2026, 9, 1));
        form.setDurationDays(3);

        ProjectWizardService service = new ProjectWizardService(new ProjectTimeFrameCalculator());
        service.saveBasics(form, userId, session);
        ProjectWizardState restored = service.requireOwned(userId, session);
        ProjectBasicsForm restoredForm = ProjectBasicsForm.from(restored);

        assertThat(restored.getTitle()).isEqualTo("Präsentation vorbereiten");
        assertThat(restored.getCategory()).isEqualTo(TemplateCategory.EDUCATION);
        assertThat(restored.getSubcategory()).isEqualTo(ProjectSubCategory.PRESENTATION_OR_REPORT);
        assertThat(restored.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(restored.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(restoredForm.getTimeFrameType()).isEqualTo(ProjectTimeFrameType.START_AND_DURATION);
        assertThat(restoredForm.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(restoredForm.getEndDate()).isNull();
        assertThat(restoredForm.getDurationDays()).isEqualTo(3);
    }


    @Test
    void acceptsSubcategoryOnlyWithinItsCategory() {
        ProjectSubCategory subcategory = ProjectSubCategory.THESIS;
        ProjectBasicsForm form = validForm();
        form.setCategory(subcategory.getCategory());
        form.setSubcategory(subcategory);
        assertThat(validator.validate(form)).isEmpty();
        form.setCategory(TemplateCategory.OTHER);
        form.setOtherProjectTypeDescription("Anderes Vorhaben");
        assertThat(violatedProperties(form)).containsExactly("subcategory");
        form.setCategory(subcategory.getCategory() == TemplateCategory.HOME
                ? TemplateCategory.EDUCATION : TemplateCategory.HOME);
        assertThat(violatedProperties(form)).containsExactly("subcategory");
    }

    @Test
    void dropdownMappingIsCompleteOrderedAndHasNoOtherValues() {
        assertThat(ProjectSubCategory.forCategory(TemplateCategory.EDUCATION)).containsExactly(
                ProjectSubCategory.PRESENTATION_OR_REPORT,
                ProjectSubCategory.EXAM_PREPARATION,
                ProjectSubCategory.LEARNING_PLAN,
                ProjectSubCategory.TERM_PAPER,
                ProjectSubCategory.THESIS,
                ProjectSubCategory.OTHER_EDUCATION);
        assertThat(ProjectSubCategory.forCategory(TemplateCategory.SOFTWARE_TECHNOLOGY)).containsExactly(
                ProjectSubCategory.SOFTWARE_PROJECT,
                ProjectSubCategory.WEB_OR_MOBILE_APP,
                ProjectSubCategory.EXTEND_EXISTING_APPLICATION,
                ProjectSubCategory.WEBSITE,
                ProjectSubCategory.DATABASE_PROJECT,
                ProjectSubCategory.HARDWARE_OR_RASPBERRY_PI_PROJECT,
                ProjectSubCategory.OTHER_SOFTWARE_AND_TECHNOLOGY);
        assertThat(ProjectSubCategory.forCategory(TemplateCategory.EVENT)).containsExactly(
                ProjectSubCategory.PRIVATE_CELEBRATION,
                ProjectSubCategory.WORKSHOP_TRAINING_OR_INFORMATION_EVENT,
                ProjectSubCategory.CLUB_OR_COMMUNITY_EVENT,
                ProjectSubCategory.CONCERT_OR_PERFORMANCE,
                ProjectSubCategory.FLEA_MARKET_OR_SALES_EVENT,
                ProjectSubCategory.FUNDRAISING_EVENT,
                ProjectSubCategory.TOURNAMENT_OR_COMPETITION,
                ProjectSubCategory.STUDY_EVENT,
                ProjectSubCategory.OTHER_EVENT);
        assertThat(ProjectSubCategory.forCategory(TemplateCategory.HOME)).containsExactly(
                ProjectSubCategory.MOVING,
                ProjectSubCategory.RENOVATION_OR_HOME_PROJECT,
                ProjectSubCategory.DECLUTTERING_OR_HOUSEHOLD_ORGANIZATION,
                ProjectSubCategory.GARDEN_PROJECT,
                ProjectSubCategory.OTHER_HOME);
        assertThat(ProjectSubCategory.forCategory(TemplateCategory.CREATIVE)).containsExactly(
                ProjectSubCategory.WRITING_PROJECT,
                ProjectSubCategory.PODCAST,
                ProjectSubCategory.VIDEO_OR_SHORT_FILM_PROJECT,
                ProjectSubCategory.PHOTO_OR_GRAPHIC_PROJECT,
                ProjectSubCategory.MUSIC_PROJECT,
                ProjectSubCategory.EXHIBITION,
                ProjectSubCategory.BLOG_OR_SOCIAL_MEDIA_CAMPAIGN,
                ProjectSubCategory.BOARD_GAME_OR_CREATIVE_PROTOTYPE,
                ProjectSubCategory.OTHER_CREATIVE_PROJECT);
        assertThat(ProjectSubCategory.forCategory(TemplateCategory.CAREER)).containsExactly(
                ProjectSubCategory.JOB_SEARCH_AND_APPLICATION,
                ProjectSubCategory.CREATE_PORTFOLIO,
                ProjectSubCategory.TRAINING_OR_CERTIFICATION,
                ProjectSubCategory.ONBOARDING_PLAN,
                ProjectSubCategory.PROFESSIONAL_PRESENTATION,
                ProjectSubCategory.PROCESS_IMPROVEMENT,
                ProjectSubCategory.PRODUCT_OR_BUSINESS_IDEA,
                ProjectSubCategory.OTHER_CAREER);
        assertThat(ProjectSubCategory.forCategory(TemplateCategory.HEALTH_PERSONAL_DEVELOPMENT)).containsExactly(
                ProjectSubCategory.FITNESS_OR_RUNNING_GOAL,
                ProjectSubCategory.COMPETITION_PREPARATION,
                ProjectSubCategory.NUTRITION_PROJECT,
                ProjectSubCategory.HABIT_OR_PERSONAL_CHALLENGE,
                ProjectSubCategory.DIGITAL_DETOX_OR_DAILY_LIFE_CHANGE,
                ProjectSubCategory.OTHER_HEALTH_AND_PERSONAL_DEVELOPMENT);
        assertThat(ProjectSubCategory.forCategory(TemplateCategory.TRAVEL)).containsExactly(
                ProjectSubCategory.TRIP_OR_VACATION,
                ProjectSubCategory.ROAD_TRIP,
                ProjectSubCategory.FESTIVAL_OR_CONCERT_TRIP,
                ProjectSubCategory.CAMPING_TRIP,
                ProjectSubCategory.BICYCLE_TOUR,
                ProjectSubCategory.OTHER_TRAVEL);
        assertThat(ProjectSubCategory.values()).hasSize(56);
        assertThat(ProjectSubCategory.forCategory(TemplateCategory.OTHER)).isEmpty();
        assertThat(ProjectSubCategory.forCategory(null)).isEmpty();
        assertThat(ProjectSubCategory.values()).allSatisfy(value -> {
            assertThat(value.getLabel()).isNotBlank();
            assertThat(value.getCategory()).isNotEqualTo(TemplateCategory.OTHER);
        });
    }

    @Test
    void categoryChangeReplacesThePreviousSelectionAndInvalidSubmissionsDoNotMutateState() {
        var service = new ProjectWizardService(new ProjectTimeFrameCalculator());
        var session = new MockHttpSession();
        var userId = UUID.randomUUID();
        var original = validForm();
        original.setSubcategory(ProjectSubCategory.THESIS);
        service.saveBasics(original, userId, session);

        var changed = validForm();
        changed.setCategory(TemplateCategory.HOME);
        changed.setSubcategory(ProjectSubCategory.THESIS);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.saveBasics(changed, userId, session))
                .isInstanceOf(de.melinadanhier.projectflow.common.exception.DomainValidationException.class);
        assertThat(service.requireOwned(userId, session).getSubcategory()).isEqualTo(ProjectSubCategory.THESIS);

        changed.setSubcategory(null);
        service.saveBasics(changed, userId, session);
        assertThat(service.requireOwned(userId, session).getCategory()).isEqualTo(TemplateCategory.HOME);
        assertThat(ProjectBasicsForm.from(service.requireOwned(userId, session)).getSubcategory()).isNull();
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
