package de.melinadanhier.projectflow.generation.model;

import de.melinadanhier.projectflow.common.model.MutableEntity;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
public class PlanDraft extends MutableEntity {

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PlanDraftStatus status = PlanDraftStatus.GENERATING;

    @PositiveOrZero
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Size(max = 2000)
    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Size(max = 100)
    @Column(name = "model_name", length = 100)
    private String modelName;

    @NotBlank
    @Size(max = 100)
    @Column(name = "prompt_version", nullable = false, length = 100)
    private String promptVersion;

    @NotBlank
    @Size(max = 100)
    @Column(name = "schema_version", nullable = false, length = 100)
    private String schemaVersion;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "planDraft", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<DraftSection> sections = new ArrayList<>();

    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "planDraft", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<DraftPlanElement> elements = new ArrayList<>();

    public void addSection(DraftSection section) {
        sections.add(section);
        section.setPlanDraft(this);
    }

    public void removeSection(DraftSection section) {
        sections.remove(section);
        if (section.getPlanDraft() == this) {
            section.setPlanDraft(null);
        }
    }

    public void addElement(DraftPlanElement element) {
        elements.add(element);
        element.setPlanDraft(this);
    }

    public void removeElement(DraftPlanElement element) {
        elements.remove(element);
        if (element.getPlanDraft() == this) {
            element.setPlanDraft(null);
        }
    }
}
