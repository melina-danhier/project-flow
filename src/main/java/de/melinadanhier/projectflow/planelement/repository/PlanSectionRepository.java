package de.melinadanhier.projectflow.planelement.repository;

import de.melinadanhier.projectflow.planelement.model.PlanSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface PlanSectionRepository extends JpaRepository<PlanSection, UUID> {

    Optional<PlanSection> findByIdAndPlanContainerId(UUID sectionId, UUID projectId);

    List<PlanSection> findAllByPlanContainerIdOrderBySortOrderAsc(UUID projectId);
}
