package de.melinadanhier.projectflow.planelement.repository;

import de.melinadanhier.projectflow.planelement.model.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {

    Optional<Milestone> findByIdAndPlanContainerId(UUID milestoneId, UUID projectId);

    @Query("""
            select milestone from Milestone milestone
            left join fetch milestone.planSection
            where milestone.planContainer.id = :projectId
            order by milestone.sortOrder asc
            """)
    List<Milestone> findAllByPlanContainerIdOrderBySortOrderAsc(@Param("projectId") UUID projectId);
}
