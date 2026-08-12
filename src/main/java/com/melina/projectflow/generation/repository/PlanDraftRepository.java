package com.melina.projectflow.generation.repository;

import com.melina.projectflow.generation.model.PlanDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlanDraftRepository extends JpaRepository<PlanDraft, UUID> {
}
