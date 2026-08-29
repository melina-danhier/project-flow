package de.melinadanhier.projectflow.plancontainer.project.model;

import de.melinadanhier.projectflow.plancontainer.project.model.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import java.util.List;

/** Shared typed classification for forms, session state, entities and view DTOs. */
public interface ProjectClassification {
    TemplateCategory getCategory();
    ProjectSubCategory getSubcategory();
    String getOtherProjectTypeDescription();

    default List<ProjectSubCategory> getSubcategoryOptions() {
        return ProjectSubCategory.forCategory(getCategory());
    }

    default boolean isOtherCategory() {
        return getCategory() == TemplateCategory.OTHER;
    }

    default String getProjectTypeLabel() {
        return isOtherCategory() ? getOtherProjectTypeDescription()
                : getSubcategory() == null ? null : getSubcategory().getLabel();
    }
}
