package com.melina.projectflow.generation.repository;

import com.melina.projectflow.generation.model.PlanDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface PlanDraftRepository extends JpaRepository<PlanDraft, UUID> {

    Optional<PlanDraft> findByIdAndProjectId(UUID draftId, UUID projectId);
}
