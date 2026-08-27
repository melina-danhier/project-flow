package de.melinadanhier.projectflow.draft.repository;

import de.melinadanhier.projectflow.draft.model.DraftPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.UUID;
import java.util.Optional;

public interface PlanDraftRepository extends JpaRepository<DraftPlan, UUID> {

    Optional<DraftPlan> findByIdAndProjectId(UUID draftId, UUID projectId);

    Optional<DraftPlan> findByProjectId(UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DraftPlan d where d.project.id = :projectId")
    Optional<DraftPlan> findForUpdateByProjectId(UUID projectId);
}
