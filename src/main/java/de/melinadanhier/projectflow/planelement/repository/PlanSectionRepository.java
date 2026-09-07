package de.melinadanhier.projectflow.planelement.repository;

import de.melinadanhier.projectflow.planelement.model.PlanSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface PlanSectionRepository extends JpaRepository<PlanSection, UUID> {

    Optional<PlanSection> findByIdAndPlanContainerId(UUID sectionId, UUID projectId);

    @Query("""
            select section from PlanSection section where section.planContainer.id = :projectId
            order by section.sortOrder asc, section.id asc
            """)
    List<PlanSection> findAllByPlanContainerIdOrderBySortOrderAsc(@Param("projectId") UUID projectId);
}
