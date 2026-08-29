package de.melinadanhier.projectflow.planelement.repository;

import de.melinadanhier.projectflow.planelement.model.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {

    @Query("""
            select comment from TaskComment comment
            join fetch comment.author author
            join fetch author.user
            where comment.task.id = :taskId
              and comment.task.planContainer.id = :projectId
            order by comment.createdAt asc
            """)
    List<TaskComment> findAllForTask(
            @Param("projectId") UUID projectId,
            @Param("taskId") UUID taskId
    );

    @Query("""
            select comment from TaskComment comment
            join fetch comment.author author
            where comment.id = :commentId
              and comment.task.id = :taskId
              and comment.task.planContainer.id = :projectId
            """)
    Optional<TaskComment> findForTask(
            @Param("projectId") UUID projectId,
            @Param("taskId") UUID taskId,
            @Param("commentId") UUID commentId
    );

}
