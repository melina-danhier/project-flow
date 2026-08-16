package de.melinadanhier.projectflow.plancontainer.project.repository;

import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query("""
            select distinct project
            from Project project
            join project.memberships membership
            where membership.user.id = :userId and membership.active = true
              and project.location = :location
            order by project.updatedAt desc
            """)
    List<Project> findAllAccessibleByUserIdAndLocation(
            @Param("userId") UUID userId,
            @Param("location") ProjectLocation location
    );

    default List<Project> findAllAccessibleByUserId(UUID userId) {
        return findAllAccessibleByUserIdAndLocation(userId, ProjectLocation.OVERVIEW);
    }

    default List<Project> findAllDraftsAccessibleByUserId(UUID userId) {
        return findAllAccessibleByUserIdAndLocation(userId, ProjectLocation.DRAFT);
    }

    @EntityGraph(attributePaths = {"memberships", "memberships.user"})
    @Query("select project from Project project where project.id = :projectId")
    java.util.Optional<Project> findPlanProjectById(@Param("projectId") UUID projectId);
}
