package de.melinadanhier.projectflow.plancontainer.project.repository;

import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMemberRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    Optional<ProjectMember> findByProjectIdAndUserIdAndActiveTrue(UUID projectId, UUID userId);

    Optional<ProjectMember> findByIdAndProjectIdAndActiveTrue(UUID membershipId, UUID projectId);

    @Query("""
            select membership from ProjectMember membership
            join fetch membership.user
            where membership.project.id = :projectId and membership.active = true
            order by membership.joinedAt asc
            """)
    List<ProjectMember> findActiveByProjectIdWithUser(@Param("projectId") UUID projectId);

    long countByProjectIdAndActiveTrue(UUID projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ProjectMember membership where membership.project.id = :projectId")
    int deleteAllByProjectId(@Param("projectId") UUID projectId);

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
