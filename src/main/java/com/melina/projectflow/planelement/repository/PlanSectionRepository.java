package com.melina.projectflow.planelement.repository;

import com.melina.projectflow.planelement.model.PlanSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface PlanSectionRepository extends JpaRepository<PlanSection, UUID> {

    Optional<PlanSection> findByIdAndPlanContainerId(UUID sectionId, UUID projectId);
}
