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

    @Query("""
            select element from PlanElement element
            where element.planContainer.id = :projectId and element.planSection.id = :sectionId
            order by element.sortOrder asc, element.id asc
            """)
    List<PlanElement> findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
            @Param("projectId") UUID projectId, @Param("sectionId") UUID sectionId);

    @Query("""
            select element from PlanElement element
            where element.planContainer.id = :projectId and element.planSection is null
            order by element.sortOrder asc, element.id asc
            """)
    List<PlanElement> findAllByPlanContainerIdAndPlanSectionIsNullOrderBySortOrderAsc(
            @Param("projectId") UUID projectId);

    @Query("""
            select element from PlanElement element where element.planContainer.id = :projectId
            order by element.sortOrder asc, element.id asc
            """)
    List<PlanElement> findAllByPlanContainerIdOrderBySortOrderAsc(@Param("projectId") UUID projectId);

    @Query("""
            select element from PlanElement element
            left join fetch element.planSection
            where element.planContainer.id = :projectId
            order by element.sortOrder asc, element.id asc
            """)
    List<PlanElement> findPlanElements(@Param("projectId") UUID projectId);
}
