package de.melinadanhier.projectflow.plancontainer.project.model.classification;

import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import java.util.List;

/** Shared typed classification for forms, session state, entities and view DTOs. */
public interface ProjectClassification {
    ProjectCategory getCategory();
    ProjectSubCategory getSubcategory();
    String getOtherProjectTypeDescription();

    default List<ProjectSubCategory> getSubcategoryOptions() {
        return ProjectSubCategory.forCategory(getCategory());
    }

    default boolean isOtherCategory() {
        return getCategory() == ProjectCategory.OTHER;
    }

    default String getProjectTypeLabel() {
        return isOtherCategory() ? getOtherProjectTypeDescription()
                : getSubcategory() == null ? null : getSubcategory().getLabel();
    }
}
