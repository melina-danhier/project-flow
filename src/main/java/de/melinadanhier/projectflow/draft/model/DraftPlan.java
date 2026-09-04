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

    @Column(name = "applied_at")
    private Instant appliedAt;

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
        if (section == null) throw new IllegalArgumentException("Entwurfsbereich darf nicht null sein.");
        if (section.getDraftPlan() != null && !sameEntity(section.getDraftPlan(), this)) {
            throw new IllegalArgumentException("Der Bereich gehört bereits zu einem anderen Entwurf.");
        }
        if (sections.stream().noneMatch(candidate -> sameEntity(candidate, section))) sections.add(section);
        section.setDraftPlan(this);
    }

    public void removeSection(DraftSection section) {
        new ArrayList<>(section.getElements()).forEach(section::removeElement);
        sections.removeIf(candidate -> sameEntity(candidate, section));
        if (sameEntity(section.getDraftPlan(), this)) {
            section.setDraftPlan(null);
        }
    }

    public void addElement(DraftPlanElement element) {
        if (element == null) throw new IllegalArgumentException("Entwurfselement darf nicht null sein.");
        if (element.getDraftPlan() != null && !sameEntity(element.getDraftPlan(), this)) {
            throw new IllegalArgumentException("Das Element gehört bereits zu einem anderen Entwurf.");
        }
        if (element.getDraftSection() != null && element.getDraftSection().getDraftPlan() != null
                && !sameEntity(element.getDraftSection().getDraftPlan(), this)) {
            throw new IllegalArgumentException("Element und Bereich gehören zu unterschiedlichen Entwürfen.");
        }
        if (elements.stream().noneMatch(candidate -> sameEntity(candidate, element))) elements.add(element);
        element.setDraftPlan(this);
    }

    public void removeElement(DraftPlanElement element) {
        if (element.getDraftSection() != null) element.getDraftSection().removeElement(element);
        elements.removeIf(candidate -> sameEntity(candidate, element));
        if (sameEntity(element.getDraftPlan(), this)) {
            element.setDraftPlan(null);
        }
    }

    public void clearContents() {
        new ArrayList<>(elements).forEach(this::removeElement);
        new ArrayList<>(sections).forEach(this::removeSection);
    }

    private boolean sameEntity(MutableEntity left, MutableEntity right) {
        if (left == right) return true;
        return left != null && right != null && left.getId() != null && left.getId().equals(right.getId());
    }
}
