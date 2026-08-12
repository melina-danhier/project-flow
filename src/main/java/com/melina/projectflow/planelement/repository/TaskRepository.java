package com.melina.projectflow.planelement.repository;

import com.melina.projectflow.planelement.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
}
