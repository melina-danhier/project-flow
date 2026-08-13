package com.melina.projectflow.planelement.repository;

import com.melina.projectflow.planelement.model.PlanElement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface PlanElementRepository extends JpaRepository<PlanElement, UUID> {

    Optional<PlanElement> findByIdAndPlanContainerId(UUID elementId, UUID projectId);
}
