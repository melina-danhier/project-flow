package com.melina.projectflow.planelement.model;

import com.melina.projectflow.common.model.MutableEntity;
import com.melina.projectflow.plancontainer.model.PlanContainer;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "plan_sections")
@Getter
@Setter
@NoArgsConstructor
public class PlanSection extends MutableEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_container_id", nullable = false)
    private PlanContainer planContainer;

    @NotBlank
    @Size(max = 100)
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Size(max = 2000)
    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @PositiveOrZero
    @Column(name = "relative_start_day")
    private Integer relativeStartDay;

    @PositiveOrZero
    @Column(name = "relative_end_day")
    private Integer relativeEndDay;

    @PositiveOrZero
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20)
    private ElementOrigin origin;

    @Column(name = "has_critical_assumption", nullable = false)
    private boolean hasCriticalAssumption;

    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "planSection")
    @OrderBy("sortOrder ASC")
    private List<PlanElement> elements = new ArrayList<>();

    public void addElement(PlanElement element) {
        elements.add(element);
        element.setPlanSection(this);
    }

    public void removeElement(PlanElement element) {
        elements.remove(element);
        if (element.getPlanSection() == this) {
            element.setPlanSection(null);
        }
    }
}
