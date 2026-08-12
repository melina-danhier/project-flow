package com.melina.projectflow.planelement.repository;

import com.melina.projectflow.planelement.model.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {
}
