package de.melinadanhier.projectflow.planelement.repository;

import de.melinadanhier.projectflow.planelement.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    Optional<Task> findByIdAndPlanContainerId(UUID taskId, UUID projectId);

    @Query("""
            select distinct task from Task task
            left join fetch task.planSection
            left join fetch task.assignee assignee
            left join fetch assignee.user
            left join fetch task.prerequisites
            where task.planContainer.id = :projectId
            order by task.sortOrder asc
            """)
    List<Task> findPlanTasks(@Param("projectId") UUID projectId);

    @Query("""
            select distinct successor from Task successor
            join successor.prerequisites prerequisite
            where successor.planContainer.id = :projectId and prerequisite.id = :taskId
            order by successor.sortOrder asc
            """)
    List<Task> findSuccessors(@Param("projectId") UUID projectId, @Param("taskId") UUID taskId);

    @Modifying(flushAutomatically = true)
    @Query(value = "delete from task_prerequisites where successor_task_id = :taskId or prerequisite_task_id = :taskId", nativeQuery = true)
    int deleteDependencyLinksForTask(@Param("taskId") UUID taskId);

    @Modifying(flushAutomatically = true)
    @Query(value = "delete from task_prerequisites where successor_task_id in (select id from plan_elements where plan_container_id = :projectId) or prerequisite_task_id in (select id from plan_elements where plan_container_id = :projectId)", nativeQuery = true)
    int deleteDependencyLinksForProject(@Param("projectId") UUID projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Task task set task.assignee = null where task.assignee.id = :membershipId")
    int clearAssignee(@Param("membershipId") UUID membershipId);
}
