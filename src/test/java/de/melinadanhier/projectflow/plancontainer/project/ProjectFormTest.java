package de.melinadanhier.projectflow.plancontainer.project;

import de.melinadanhier.projectflow.plancontainer.project.dto.form.ProjectCreateForm;
import de.melinadanhier.projectflow.plancontainer.project.dto.form.ProjectForm;
import de.melinadanhier.projectflow.plancontainer.project.dto.form.ProjectUpdateForm;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.CreationType;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectFormTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createAndUpdateFormsShareDateRangeValidation() {
        ProjectCreateForm createForm = validCreateForm();
        ProjectUpdateForm updateForm = validUpdateForm();
        createForm.setStartDate(LocalDate.of(2026, 9, 20));
        createForm.setEndDate(LocalDate.of(2026, 9, 1));
        updateForm.setStartDate(LocalDate.of(2026, 9, 20));
        updateForm.setEndDate(LocalDate.of(2026, 9, 1));

        assertThat(violatedProperties(createForm)).contains("dateRangeValid");
        assertThat(violatedProperties(updateForm)).contains("dateRangeValid");
    }

    @Test
    void createAndUpdateFormsShareCollaborationValidation() {
        ProjectCreateForm createForm = validCreateForm();
        ProjectUpdateForm updateForm = validUpdateForm();
        createForm.setCollaborationMode(CollaborationMode.BOTH);
        updateForm.setCollaborationMode(CollaborationMode.BOTH);

        assertThat(violatedProperties(createForm)).contains("projectCollaborationModeValid");
        assertThat(violatedProperties(updateForm)).contains("projectCollaborationModeValid");
    }

    @Test
    void createFormKeepsItsOtherCategoryDefaultAndOptionalTypeDescription() {
        ProjectCreateForm form = validCreateForm();
        form.setCategory(ProjectCategory.OTHER);
        form.setDescription("Das Projekt ist bereits ausreichend beschrieben.");

        assertThat(new ProjectCreateForm().getCategory()).isEqualTo(ProjectCategory.OTHER);
        assertThat(validator.validate(form)).isEmpty();
    }

    private ProjectCreateForm validCreateForm() {
        ProjectCreateForm form = new ProjectCreateForm();
        form.setTitle("Neues Projekt");
        form.setCategory(ProjectCategory.EDUCATION);
        form.setCollaborationMode(CollaborationMode.INDIVIDUAL);
        form.setCreationType(CreationType.EMPTY);
        return form;
    }

    private ProjectUpdateForm validUpdateForm() {
        ProjectUpdateForm form = new ProjectUpdateForm();
        form.setTitle("Bestehendes Projekt");
        form.setCategory(ProjectCategory.EDUCATION);
        form.setCollaborationMode(CollaborationMode.INDIVIDUAL);
        return form;
    }

    private Set<String> violatedProperties(ProjectForm form) {
        return validator.validate(form).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }
}
