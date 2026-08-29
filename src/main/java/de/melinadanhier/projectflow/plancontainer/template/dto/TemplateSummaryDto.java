package de.melinadanhier.projectflow.plancontainer.template.dto;

import de.melinadanhier.projectflow.plancontainer.project.model.ProjectClassification;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class TemplateSummaryDto implements ProjectClassification {

    private UUID id;
    private String title;
    private TemplateCategory category;
    private String otherProjectTypeDescription;

    private ProjectSubCategory subcategory;
    private Integer recommendedDurationDays;
    private CollaborationMode collaborationMode;
    private boolean active;
    private int version;
}
