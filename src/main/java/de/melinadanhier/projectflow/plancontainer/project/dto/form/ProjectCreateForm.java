package de.melinadanhier.projectflow.plancontainer.project.dto.form;

import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.validation.ValidProjectClassification;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ValidProjectClassification(requireOtherDescription = false)
public class ProjectCreateForm extends ProjectForm {

    @NotNull
    private CreationType creationType;

    public ProjectCreateForm() {
        setCategory(TemplateCategory.OTHER);
    }
}
