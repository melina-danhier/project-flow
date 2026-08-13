package com.melina.projectflow.plancontainer.project.repository;

import com.melina.projectflow.plancontainer.project.model.Project;
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
              and project.status = com.melina.projectflow.plancontainer.project.model.ProjectStatus.ACTIVE
              and project.location = com.melina.projectflow.plancontainer.project.model.ProjectLocation.OVERVIEW
            order by project.updatedAt desc
            """)
    List<Project> findAllAccessibleByUserId(@Param("userId") UUID userId);
}
