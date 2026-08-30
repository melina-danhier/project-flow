package de.melinadanhier.projectflow.draft.model;

import de.melinadanhier.projectflow.common.model.MutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "draft_sections")
@Getter
@Setter
@NoArgsConstructor
public class DraftSection extends MutableEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_draft_id", nullable = false)
    private DraftPlan draftPlan;

    @NotBlank
    @Size(max = 100)
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Size(max = 2000)
    @Column(name = "description", length = 2000)
    private String description;

    @PositiveOrZero
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private DraftReviewStatus reviewStatus = DraftReviewStatus.PENDING;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20)
    private ElementOrigin origin = ElementOrigin.AI;

    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "draftSection")
    @OrderBy("sortOrder ASC")
    private List<DraftPlanElement> elements = new ArrayList<>();

    public void addElement(DraftPlanElement element) {
        elements.add(element);
        element.setDraftSection(this);
    }

    public void removeElement(DraftPlanElement element) {
        elements.remove(element);
        if (element.getDraftSection() == this) {
            element.setDraftSection(null);
        }
    }

    public void markContentModified() {
        origin = origin.modifiedByUser();
    }
}
