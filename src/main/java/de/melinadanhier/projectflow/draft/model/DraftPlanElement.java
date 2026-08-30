package de.melinadanhier.projectflow.draft.model;

import de.melinadanhier.projectflow.ai.model.generation.GeneratedElementOrigin;
import de.melinadanhier.projectflow.common.model.MutableEntity;
import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "draft_plan_elements")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor
public abstract class DraftPlanElement extends MutableEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_draft_id", nullable = false)
    private DraftPlan draftPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draft_section_id")
    private DraftSection draftSection;

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
    @Column(name = "ai_origin", nullable = false, length = 20)
    private ElementOrigin origin = ElementOrigin.AI;

    public void markContentModified() {
        origin = origin.modifiedByUser();
    }

    /** Compatibility at the AI response boundary. */
    public void setAiOrigin(GeneratedElementOrigin generatedOrigin) {
        origin = generatedOrigin == GeneratedElementOrigin.USER_INPUT ? ElementOrigin.USER : ElementOrigin.AI;
    }

    /** Compatibility for callers that still inspect generated provenance. */
    public GeneratedElementOrigin getAiOrigin() {
        return origin == ElementOrigin.USER ? GeneratedElementOrigin.USER_INPUT : GeneratedElementOrigin.AI_INFERRED;
    }

    /** Kept as a derived compatibility property; modification is represented by origin. */
    public boolean isUserModified() {
        return origin == ElementOrigin.AI_MODIFIED || origin == ElementOrigin.TEMPLATE_MODIFIED;
    }
}
