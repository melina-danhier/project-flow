package com.melina.projectflow.planelement.repository;

import com.melina.projectflow.planelement.model.PlanSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlanSectionRepository extends JpaRepository<PlanSection, UUID> {
}
