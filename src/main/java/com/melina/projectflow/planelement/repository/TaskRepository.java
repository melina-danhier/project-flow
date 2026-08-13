package com.melina.projectflow.planelement.repository;

import com.melina.projectflow.planelement.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    Optional<Task> findByIdAndPlanContainerId(UUID taskId, UUID projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Task task set task.assignee = null where task.assignee.id = :membershipId")
    int clearAssignee(@Param("membershipId") UUID membershipId);
}
