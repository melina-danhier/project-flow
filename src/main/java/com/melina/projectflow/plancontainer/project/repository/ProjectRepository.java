package com.melina.projectflow.plancontainer.project.repository;

import com.melina.projectflow.plancontainer.project.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
}
