package de.melinadanhier.projectflow.plancontainer.template.model;

import de.melinadanhier.projectflow.plancontainer.model.PlanContainer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class Template extends PlanContainer {

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private TemplateCategory category;

    @NotBlank
    @Size(max = 100)
    @Column(name = "project_type", nullable = false, length = 100)
    private String projectType;

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
