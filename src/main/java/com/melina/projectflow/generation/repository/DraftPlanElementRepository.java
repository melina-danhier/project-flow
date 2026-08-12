package com.melina.projectflow.generation.repository;

import com.melina.projectflow.generation.model.DraftPlanElement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DraftPlanElementRepository extends JpaRepository<DraftPlanElement, UUID> {
}
