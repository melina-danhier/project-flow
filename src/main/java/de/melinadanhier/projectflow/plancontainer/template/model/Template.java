package de.melinadanhier.projectflow.plancontainer.template.model;

import de.melinadanhier.projectflow.plancontainer.project.validation.ValidProjectClassification;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectClassification;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.model.PlanContainer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "plan_templates")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
@ValidProjectClassification(requireOtherDescription = false)
public class Template extends PlanContainer implements ProjectClassification {

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private TemplateCategory category;

    @Size(max = 100)
    @Column(name = "other_project_type_description", length = 100)
    private String otherProjectTypeDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "subcategory", length = 100)
    private ProjectSubCategory subcategory;

    @Positive
    @Column(name = "recommended_duration_days")
    private Integer recommendedDurationDays;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "collaboration_mode", nullable = false, length = 20)
    private CollaborationMode collaborationMode;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Min(1)
    @Column(name = "template_version", nullable = false)
    private int version = 1;
}
