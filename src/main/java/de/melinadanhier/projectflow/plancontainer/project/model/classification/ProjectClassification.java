package de.melinadanhier.projectflow.plancontainer.project.model.classification;

import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import java.util.List;

/** Shared typed classification for forms, session state, entities and view DTOs. */
public interface ProjectClassification {
    ProjectCategory getCategory();
    ProjectSubCategory getSubcategory();
    default String getOtherProjectTypeDescription() {
        return null;
    }

    default List<ProjectSubCategory> getSubcategoryOptions() {
        return ProjectSubCategory.forCategory(getCategory());
    }

    default List<ProjectSubCategory> getAllSubcategories() {
        return List.of(ProjectSubCategory.values());
    }

    default boolean isOtherCategory() {
        return getCategory() == ProjectCategory.OTHER;
    }

    default boolean hasSubcategories() {
        return !getSubcategoryOptions().isEmpty();
    }

    default boolean isSubcategoryRequired() {
        return false;
    }

    default String getProjectTypeLabel() {
        return getDisplayCategory();
    }

    default String getDisplayCategory() {
        if (getSubcategory() != null && !getSubcategory().isOther()) {
            return getSubcategory().getLabel();
        }
        return getCategory() == null ? null : getCategory().getLabel();
    }
}
