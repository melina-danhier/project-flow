package com.melina.projectflow.plancontainer.project.repository;

import com.melina.projectflow.plancontainer.project.model.ProjectMember;
import com.melina.projectflow.plancontainer.project.model.ProjectMemberRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    Optional<ProjectMember> findByProjectIdAndUserIdAndActiveTrue(UUID projectId, UUID userId);

    long countByProjectIdAndActiveTrue(UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select membership
            from ProjectMember membership
            where membership.project.id = :projectId
              and membership.role = :role
              and membership.active = true
            """)
    Optional<ProjectMember> findActiveOwnerForUpdate(
            @Param("projectId") UUID projectId,
            @Param("role") ProjectMemberRole role
    );
}
