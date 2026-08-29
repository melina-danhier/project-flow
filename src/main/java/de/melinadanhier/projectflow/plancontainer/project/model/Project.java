package de.melinadanhier.projectflow.plancontainer.project.model;

import de.melinadanhier.projectflow.plancontainer.project.validation.ValidProjectClassification;
import de.melinadanhier.projectflow.draft.model.DraftPlan;
import de.melinadanhier.projectflow.plancontainer.model.PlanContainer;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "projects")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
@ValidProjectClassification(requireOtherDescription = false)
public class Project extends PlanContainer implements ProjectClassification {

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 50)
    private TemplateCategory category;

    @Size(max = 100)
    @Column(name = "other_project_type_description", length = 100)
    private String otherProjectTypeDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "subcategory", length = 100)
    private ProjectSubCategory subcategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "collaboration_mode", length = 20)
    private CollaborationMode collaborationMode;

    public boolean isGroupProject() {
        return collaborationMode == CollaborationMode.GROUP;
    }

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "creation_type", nullable = false, length = 20)
    private CreationType creationType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProjectStatus status;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "location", nullable = false, length = 20)
    private ProjectLocation location = ProjectLocation.OVERVIEW;

    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProjectMember> memberships = new LinkedHashSet<>();

    @OneToOne(mappedBy = "project", fetch = jakarta.persistence.FetchType.LAZY)
    private DraftPlan currentDraft;

    public void addMembership(ProjectMember membership) {
        memberships.add(membership);
        membership.setProject(this);
    }

    public void removeMembership(ProjectMember membership) {
        memberships.remove(membership);
        if (membership.getProject() == this) {
            membership.setProject(null);
        }
    }

    public void attachDraft(DraftPlan draft) {
        currentDraft = draft;
        if (draft != null) {
            draft.setProject(this);
        }
    }

    @AssertTrue(message = "Entwurfsstatus und Entwurfsbereich müssen gemeinsam gesetzt sein.")
    public boolean isDraftStateConsistent() {
        return (status == ProjectStatus.DRAFT) == (location == ProjectLocation.DRAFT);
    }
}
