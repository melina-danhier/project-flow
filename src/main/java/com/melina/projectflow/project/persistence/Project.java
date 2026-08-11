package com.melina.projectflow.project.persistence;

import com.melina.projectflow.ai.persistence.PlanDraft;
import com.melina.projectflow.project.domain.CreationType;
import com.melina.projectflow.project.domain.ProjectLocation;
import com.melina.projectflow.project.domain.ProjectStatus;
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
public class Project extends PlanContainer {

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

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
    private PlanDraft currentDraft;

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

    public void attachDraft(PlanDraft draft) {
        currentDraft = draft;
        if (draft != null) {
            draft.setProject(this);
        }
    }
}
