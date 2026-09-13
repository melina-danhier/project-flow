package de.melinadanhier.projectflow.planelement.repository;

import de.melinadanhier.projectflow.planelement.model.MilestoneComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MilestoneCommentRepository extends JpaRepository<MilestoneComment, UUID> {

    @Query("""
            select comment from MilestoneComment comment
            join fetch comment.author author
            join fetch author.user
            where comment.milestone.id = :milestoneId
              and comment.milestone.planContainer.id = :projectId
            order by comment.createdAt asc
            """)
    List<MilestoneComment> findAllForMilestone(
            @Param("projectId") UUID projectId,
            @Param("milestoneId") UUID milestoneId
    );

    @Query("""
            select comment from MilestoneComment comment
            join fetch comment.author author
            where comment.id = :commentId
              and comment.milestone.id = :milestoneId
              and comment.milestone.planContainer.id = :projectId
            """)
    Optional<MilestoneComment> findForMilestone(
            @Param("projectId") UUID projectId,
            @Param("milestoneId") UUID milestoneId,
            @Param("commentId") UUID commentId
    );
}
