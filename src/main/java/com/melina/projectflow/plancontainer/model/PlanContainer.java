package com.melina.projectflow.plancontainer.model;

import com.melina.projectflow.common.model.MutableEntity;
import com.melina.projectflow.planelement.model.PlanElement;
import com.melina.projectflow.planelement.model.PlanSection;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "plan_containers")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor
public abstract class PlanContainer extends MutableEntity {

    @NotBlank
    @Size(max = 100)
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Size(max = 2000)
    @Column(name = "description", length = 2000)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "structure_mode", nullable = false, length = 20)
    private StructureMode structureMode = StructureMode.TEMPORAL;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "sort_mode", nullable = false, length = 20)
    private SortMode sortMode = SortMode.DATE;

    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "planContainer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<PlanSection> sections = new ArrayList<>();

    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "planContainer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<PlanElement> elements = new ArrayList<>();

    public void addSection(PlanSection section) {
        sections.add(section);
        section.setPlanContainer(this);
    }

    public void removeSection(PlanSection section) {
        sections.remove(section);
        if (section.getPlanContainer() == this) {
            section.setPlanContainer(null);
        }
    }

    public void addElement(PlanElement element) {
        elements.add(element);
        element.setPlanContainer(this);
    }

    public void removeElement(PlanElement element) {
        elements.remove(element);
        if (element.getPlanContainer() == this) {
            element.setPlanContainer(null);
        }
    }
}
