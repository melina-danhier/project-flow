package de.melinadanhier.projectflow.planelement.repository;

import de.melinadanhier.projectflow.planelement.model.PlanElement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface PlanElementRepository extends JpaRepository<PlanElement, UUID> {

    Optional<PlanElement> findByIdAndPlanContainerId(UUID elementId, UUID projectId);

    List<PlanElement> findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
            UUID projectId,
            UUID sectionId
    );

    List<PlanElement> findAllByPlanContainerIdAndPlanSectionIsNullOrderBySortOrderAsc(UUID projectId);

    List<PlanElement> findAllByPlanContainerIdOrderBySortOrderAsc(UUID projectId);

    @Query("""
            select element from PlanElement element
            left join fetch element.planSection
            where element.planContainer.id = :projectId
            order by element.sortOrder asc
            """)
    List<PlanElement> findPlanElements(@Param("projectId") UUID projectId);
}
