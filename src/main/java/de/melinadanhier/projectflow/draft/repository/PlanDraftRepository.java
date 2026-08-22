package de.melinadanhier.projectflow.draft.repository;

import de.melinadanhier.projectflow.draft.model.DraftPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface PlanDraftRepository extends JpaRepository<DraftPlan, UUID> {

    Optional<DraftPlan> findByIdAndProjectId(UUID draftId, UUID projectId);

    Optional<DraftPlan> findByProjectId(UUID projectId);
}
