package com.melina.projectflow.plancontainer.project.repository;

import com.melina.projectflow.plancontainer.project.model.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {
}
