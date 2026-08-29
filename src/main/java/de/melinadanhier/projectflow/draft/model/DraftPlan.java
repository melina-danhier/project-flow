package de.melinadanhier.projectflow.draft.model;

import de.melinadanhier.projectflow.common.model.MutableEntity;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "plan_drafts")
@Getter
@Setter
@NoArgsConstructor
public class DraftPlan extends MutableEntity {

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DraftPlanStatus status = DraftPlanStatus.READY_FOR_REVIEW;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "sort_mode", nullable = false, length = 20)
    private SortMode sortMode = SortMode.DATE;

    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "draftPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<DraftSection> sections = new ArrayList<>();

    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "draftPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<DraftPlanElement> elements = new ArrayList<>();

    public void addSection(DraftSection section) {
        sections.add(section);
        section.setDraftPlan(this);
    }

    public void removeSection(DraftSection section) {
        sections.remove(section);
        if (section.getDraftPlan() == this) {
            section.setDraftPlan(null);
        }
    }

    public void addElement(DraftPlanElement element) {
        elements.add(element);
        element.setDraftPlan(this);
    }

    public void removeElement(DraftPlanElement element) {
        elements.remove(element);
        if (element.getDraftPlan() == this) {
            element.setDraftPlan(null);
        }
    }
}
